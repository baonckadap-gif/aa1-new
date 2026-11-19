package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.domain.model.FadeCurve
import com.example.domain.model.GlobalSettings
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PlayMode
import com.example.domain.model.SoundPad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

object AudioMixMath {
  fun computeSlotGain(
    masterVolume: Float,
    padVolume: Float,
    envelopeGain: Float,
    normalizedGain: Float,
    autoNormalizeEnabled: Boolean,
    limiterEnabled: Boolean,
    activeSlotCount: Int,
    duckingMultiplier: Float
  ): Float {
    val normFactor = if (autoNormalizeEnabled) normalizedGain.coerceIn(0.35f, 1.60f) else 1.0f
    val raw = masterVolume.coerceIn(0f, 1f) *
      padVolume.coerceIn(0f, 1f) *
      envelopeGain.coerceIn(0f, 1f) *
      normFactor *
      duckingMultiplier.coerceIn(0.05f, 1f)

    val limiterScale = if (limiterEnabled && activeSlotCount > 1) {
      // Soft-knee headroom scaling prevents polyphony clipping on Android mixer
      (1.12f / sqrt(activeSlotCount.toFloat())).coerceIn(0.32f, 1.0f)
    } else {
      1.0f
    }
    return (raw * limiterScale).coerceIn(0f, 1f)
  }
}

class SoundPadAudioEngine(
  private val context: Context,
  private val ramCache: InMemoryAudioCache
) {

  companion object {
    const val MAX_PLAYER_POOL = 16
    private const val ENVELOPE_TICK_MS = 16L // ~60fps smooth fade envelopes when fades are active
    private const val UI_PROGRESS_PUBLISH_MS = 33L // ~30fps UI progress updates ONLY for currently playing pads
  }

  var onPlaybackError: ((pad: SoundPad, errorType: PadErrorType, message: String, stackTrace: String) -> Unit)? = null

  @Volatile
  private var masterRecorder: MasterOutputRecorder? = null

  fun attachMasterRecorder(recorder: MasterOutputRecorder) {
    masterRecorder = recorder
  }

  private data class PlayerSlot @OptIn(UnstableApi::class) constructor(
    var pad: SoundPad,
    val player: ExoPlayer,
    val eqProcessor: FiveBandEqAudioProcessor,
    val fxProcessor: ReverbEchoAudioProcessor,
    val panProcessor: StereoPanAudioProcessor,
    val tapProcessor: MasterTapAudioProcessor?,
    val listener: Player.Listener,
    val fadeInStartElapsedMs: Long,
    val fadeInDurationMs: Long,
    val fadeInCurve: FadeCurve,
    var currentEnvelopeGain: Float = 1.0f,
    var fadeOutStartElapsedMs: Long? = null,
    var fadeOutDurationMs: Long = 0L,
    var fadeOutCurve: FadeCurve = FadeCurve.EQUAL_POWER,
    var fadeOutStartGain: Float = 1.0f,
    var continuousTriggeredNext: Boolean = false,
    val isSequencerSlot: Boolean = false
  ) {
    val isFadingOut: Boolean
      get() = fadeOutStartElapsedMs != null
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val activeSlots = LinkedHashMap<String, PlayerSlot>()
  private val pendingReverseJobs = HashMap<String, Job>()
  private var tickerJob: Job? = null

  private val _playbackStates = MutableStateFlow<Map<String, PadPlaybackState>>(emptyMap())
  val playbackStates: StateFlow<Map<String, PadPlaybackState>> = _playbackStates.asStateFlow()

  private var currentSettings: GlobalSettings = GlobalSettings()
  private var currentPlaylist: List<SoundPad> = emptyList()
  private var previewPlayer: ExoPlayer? = null
  private val _previewPositionMs = MutableStateFlow<Long?>(null)
  val previewPositionMs: StateFlow<Long?> = _previewPositionMs.asStateFlow()
  private var previewTickerJob: Job? = null

  private var preferredOutputDeviceId: Int? = null
  private var cueOutputDeviceId: Int? = null
  private var cachedMainOutputDevice: AudioDeviceInfo? = null
  private var cachedCueOutputDevice: AudioDeviceInfo? = null
  private var lastUiPublishElapsedMs: Long = 0L
  private var isVoiceDuckingActive: Boolean = false
  private val _cuePreviewPadId = MutableStateFlow<String?>(null)
  val cuePreviewPadId: StateFlow<String?> = _cuePreviewPadId.asStateFlow()
  private var cuePlayer: ExoPlayer? = null

  @OptIn(UnstableApi::class)
  private fun createLowLatencyLoadControl(): DefaultLoadControl =
    DefaultLoadControl.Builder()
      .setBufferDurationsMs(1000, 4000, 50, 150)
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()

  private val wakeLock: PowerManager.WakeLock by lazy {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundPadPro::AudioEngineWakeLock").apply {
      setReferenceCounted(false)
    }
  }

  init {
    SoundPadPlaybackService.onStopAllRequested = {
      scope.launch { stopAll() }
    }
  }

  @OptIn(UnstableApi::class)
  fun updateGlobalSettings(settings: GlobalSettings) {
    currentSettings = settings
    scope.launch {
      activeSlots.values.forEach { slot ->
        applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, slot.pad, settings)
      }
      refreshAllActiveSlotVolumes()
    }
  }

  @OptIn(UnstableApi::class)
  fun updatePlaylist(pads: List<SoundPad>) {
    currentPlaylist = pads
    scope.launch {
      pads.forEach { pad ->
        activeSlots[pad.id]?.let { slot ->
          slot.pad = pad
          applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, pad, currentSettings)
          applyPadParameters(slot.player, pad, slot.currentEnvelopeGain)
        }
      }
      refreshAllActiveSlotVolumes()
    }
  }

  fun setPlayMode(mode: PlayMode) {
    currentSettings = currentSettings.copy(playMode = mode)
  }

  fun setMasterVolume(volume: Float) {
    currentSettings = currentSettings.copy(masterVolume = volume.coerceIn(0f, 1f))
    scope.launch {
      refreshAllActiveSlotVolumes()
    }
  }

  @OptIn(UnstableApi::class)
  fun setMasterPan(pan: Float) {
    val clamped = pan.coerceIn(-1f, 1f)
    currentSettings = currentSettings.copy(masterPan = clamped)
    scope.launch {
      activeSlots.values.forEach { slot ->
        slot.panProcessor.setPan(StereoPanMath.combinePan(clamped, slot.pad.settings.pan))
      }
    }
  }

  @OptIn(UnstableApi::class)
  fun setMasterEq(enabled: Boolean, bandsDb: List<Float>) {
    currentSettings = currentSettings.copy(
      masterEqEnabled = enabled,
      masterEqBandsDb = bandsDb
    )
    scope.launch {
      val fxActive = currentSettings.audioEffectsEnabled
      activeSlots.values.forEach { slot ->
        slot.eqProcessor.setCombinedEq(
          masterEnabled = fxActive && enabled,
          masterBandsDb = bandsDb,
          padEnabled = fxActive && slot.pad.settings.eqEnabled,
          padBandsDb = slot.pad.settings.eqBandsDb
        )
      }
    }
  }

  fun setVoiceDuckingActive(active: Boolean) {
    isVoiceDuckingActive = active
    scope.launch {
      refreshAllActiveSlotVolumes()
    }
  }

  @OptIn(UnstableApi::class)
  fun setPreferredOutputDeviceId(deviceId: Int?) {
    preferredOutputDeviceId = deviceId
    scope.launch {
      val info = AudioOutputRouter.findOutputDeviceById(context, deviceId)
      cachedMainOutputDevice = info
      activeSlots.values.forEach { slot ->
        runCatching { slot.player.setPreferredAudioDevice(info) }
      }
    }
  }

  @OptIn(UnstableApi::class)
  fun setCueOutputDeviceId(deviceId: Int?) {
    cueOutputDeviceId = deviceId
    scope.launch {
      val info = AudioOutputRouter.findOutputDeviceById(context, deviceId)
      cachedCueOutputDevice = info
      runCatching { cuePlayer?.setPreferredAudioDevice(info) }
      runCatching { previewPlayer?.setPreferredAudioDevice(info) }
    }
  }

  @OptIn(UnstableApi::class)
  fun updateActivePadParameters(pad: SoundPad) {
    scope.launch {
      val slot = activeSlots[pad.id] ?: return@launch
      slot.pad = pad
      applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, pad, currentSettings)
      applyPadParameters(slot.player, pad, slot.currentEnvelopeGain)
      refreshAllActiveSlotVolumes()
    }
  }

  fun setActivePadsSpeed(speed: Float) {
    val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
    scope.launch {
      activeSlots.values.forEach { slot ->
        val updatedSettings = slot.pad.settings.copy(speed = clampedSpeed)
        slot.pad = slot.pad.copy(settings = updatedSettings)
        val currentPitch = slot.pad.settings.pitch.coerceIn(0.5f, 2.0f)
        slot.player.playbackParameters = PlaybackParameters(clampedSpeed, currentPitch)
      }
    }
  }

  fun setActivePadsPitch(pitch: Float) {
    val clampedPitch = pitch.coerceIn(0.5f, 2.0f)
    scope.launch {
      activeSlots.values.forEach { slot ->
        val updatedSettings = slot.pad.settings.copy(pitch = clampedPitch)
        slot.pad = slot.pad.copy(settings = updatedSettings)
        val currentSpeed = slot.pad.settings.speed.coerceIn(0.5f, 2.0f)
        slot.player.playbackParameters = PlaybackParameters(currentSpeed, clampedPitch)
      }
    }
  }

  fun triggerPad(pad: SoundPad) {
    scope.launch {
      val existingSlot = activeSlots[pad.id]
      if (existingSlot != null) {
        if (pad.settings.triggerMode == PadTriggerMode.ONE_SHOT) {
          releaseSlot(pad.id)
        } else if (existingSlot.isFadingOut) {
          releaseSlot(pad.id)
          syncStateAndService()
          return@launch
        } else {
          val fadeOutMs = effectiveFadeOutMs(pad)
          if (fadeOutMs > 0L) {
            beginFadeOut(existingSlot, fadeOutMs, effectiveFadeCurve(pad))
            syncStateAndService()
          } else {
            releaseSlot(pad.id)
            syncStateAndService()
          }
          return@launch
        }
      }

      val didCrossfade = preparePoolForNewPad(pad)
      startSlot(pad, viaCrossfade = didCrossfade)
    }
  }

  fun triggerHoldStart(pad: SoundPad) {
    scope.launch {
      if (activeSlots.containsKey(pad.id)) {
        releaseSlot(pad.id)
      }
      val didCrossfade = preparePoolForNewPad(pad)
      startSlot(pad, viaCrossfade = didCrossfade)
    }
  }

  fun triggerHoldRelease(padId: String) {
    scope.launch {
      val slot = activeSlots[padId] ?: return@launch
      val fadeOutMs = effectiveFadeOutMs(slot.pad)
      if (fadeOutMs > 0L) {
        beginFadeOut(slot, fadeOutMs, effectiveFadeCurve(slot.pad))
        syncStateAndService()
      } else {
        releaseSlot(padId)
        syncStateAndService()
      }
    }
  }

  fun stopPad(padId: String) {
    scope.launch {
      releaseSlot(padId)
      syncStateAndService()
    }
  }

  fun stopAll() {
    scope.launch {
      stopAllInternal()
      syncStateAndService()
    }
  }

  fun startSequencerPad(
    pad: SoundPad,
    crossfadePreviousPadId: String?,
    crossfadeDurationMs: Long
  ) {
    scope.launch {
      if (crossfadePreviousPadId != null && crossfadeDurationMs > 0L) {
        activeSlots[crossfadePreviousPadId]?.let { prev ->
          if (!prev.isFadingOut) {
            beginFadeOut(prev, crossfadeDurationMs, currentSettings.crossfadeCurve)
          }
        }
      }
      if (activeSlots.containsKey(pad.id)) {
        releaseSlot(pad.id)
      }
      startSlot(
        pad = pad,
        viaCrossfade = crossfadePreviousPadId != null && crossfadeDurationMs > 0L,
        isSequencerSlot = true
      )
    }
  }

  fun pausePadForSequencer(padId: String) {
    scope.launch {
      activeSlots[padId]?.player?.pause()
    }
  }

  fun resumeOrStartPadForSequencer(pad: SoundPad) {
    scope.launch {
      val slot = activeSlots[pad.id]
      if (slot != null) {
        slot.player.play()
      } else {
        startSlot(pad = pad, viaCrossfade = false, isSequencerSlot = true)
      }
    }
  }

  fun triggerScheduledPad(
    pad: SoundPad,
    volumeOverride: Float,
    fadeInMsOverride: Long,
    loopOverride: Boolean
  ) {
    val modified = pad.copy(
      settings = pad.settings.copy(
        volume = volumeOverride.coerceIn(0f, 1f),
        fadeInMs = fadeInMsOverride.coerceAtLeast(0L),
        isLooping = loopOverride || pad.settings.isLooping
      )
    )
    scope.launch {
      if (activeSlots.containsKey(modified.id)) {
        releaseSlot(modified.id)
      }
      startSlot(pad = modified, viaCrossfade = false)
    }
  }

  fun fadeOutAll(durationMs: Long = 1500L) {
    scope.launch {
      if (activeSlots.isEmpty()) return@launch
      val safeDuration = durationMs.coerceIn(250L, 6000L)
      activeSlots.values.forEach { slot ->
        if (!slot.isFadingOut) {
          beginFadeOut(slot, safeDuration, currentSettings.globalFadeCurve)
        }
      }
      ensureTicker()
      syncStateAndService()
    }
  }

  @OptIn(UnstableApi::class)
  fun startCuePreview(pad: SoundPad) {
    scope.launch {
      try {
        if (_cuePreviewPadId.value == pad.id && cuePlayer != null) {
          stopCuePreviewInternal()
          return@launch
        }
        stopCuePreviewInternal()

        val targetUri: Uri
        val clipStartMs: Long
        val clipEndMs: Long
        if (pad.settings.isReversed) {
          val cached = ReverseAudioCacheManager.getCachedReversedFileIfReady(
            context = context,
            uriString = pad.uriString,
            trimStartMs = pad.settings.trimStartMs,
            trimEndMs = pad.settings.trimEndMs
          )
          val reversedFile = cached ?: ReverseAudioCacheManager.getOrBuildReversedWav(
            context = context,
            uriString = pad.uriString,
            trimStartMs = pad.settings.trimStartMs,
            trimEndMs = pad.settings.trimEndMs
          ).getOrThrow()
          targetUri = Uri.fromFile(reversedFile)
          clipStartMs = 0L
          clipEndMs = 0L
        } else {
          targetUri = Uri.parse(pad.uriString)
          clipStartMs = pad.settings.trimStartMs.coerceAtLeast(0L)
          clipEndMs = pad.settings.trimEndMs
        }

        val eqProcessor = FiveBandEqAudioProcessor()
        val fxProcessor = ReverbEchoAudioProcessor()
        val panProcessor = StereoPanAudioProcessor()
        applySlotDsp(eqProcessor, fxProcessor, panProcessor, pad, currentSettings)

        val player = buildExoPlayerWithProcessors(
          eqProcessor = eqProcessor,
          fxProcessor = fxProcessor,
          panProcessor = panProcessor,
          includeAudioEffects = currentSettings.audioEffectsEnabled,
          handleAudioFocus = false
        )

        val cueDevice = cachedCueOutputDevice ?: if (cueOutputDeviceId != null) {
          AudioOutputRouter.findOutputDeviceById(context, cueOutputDeviceId).also { cachedCueOutputDevice = it }
        } else null
        cueDevice?.let { info ->
          runCatching { player.setPreferredAudioDevice(info) }
        }

        val mediaItem = buildClippedMediaItem(targetUri, clipStartMs, clipEndMs)

        player.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
              stopCuePreviewInternal()
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            val (type, msg) = mapPlaybackException(pad, error)
            onPlaybackError?.invoke(pad, type, msg, error.stackTraceToString().take(2000))
            stopCuePreviewInternal()
          }
        })
        player.playbackParameters = PlaybackParameters(
          pad.settings.speed.coerceIn(0.5f, 2.0f),
          pad.settings.pitch.coerceIn(0.5f, 2.0f)
        )
        player.volume = (pad.settings.volume * 0.85f).coerceIn(0.05f, 1f)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        cuePlayer = player
        _cuePreviewPadId.value = pad.id
      } catch (e: Exception) {
        onPlaybackError?.invoke(
          pad,
          PadErrorType.PLAYBACK_ERROR,
          "Gagal memutar Cue Preview \"${pad.displayName}\": ${e.localizedMessage ?: e.javaClass.simpleName}",
          e.stackTraceToString().take(2000)
        )
        stopCuePreviewInternal()
      }
    }
  }

  fun stopCuePreview() {
    scope.launch { stopCuePreviewInternal() }
  }

  private fun stopCuePreviewInternal() {
    cuePlayer?.let {
      runCatching { it.stop() }
      runCatching { it.release() }
    }
    cuePlayer = null
    _cuePreviewPadId.value = null
  }

  @OptIn(UnstableApi::class)
  fun startEditorPreview(uriString: String, trimStartMs: Long, trimEndMs: Long) {
    scope.launch {
      try {
        stopEditorPreviewInternal()
        val mediaSourceFactory = DefaultMediaSourceFactory(ramCache.createDataSourceFactory())
        val player = ExoPlayer.Builder(context)
          .setMediaSourceFactory(mediaSourceFactory)
          .setLoadControl(createLowLatencyLoadControl())
          .build()

        val previewDevice = cachedCueOutputDevice ?: cachedMainOutputDevice ?: run {
          val targetId = cueOutputDeviceId ?: preferredOutputDeviceId
          if (targetId != null) AudioOutputRouter.findOutputDeviceById(context, targetId) else null
        }
        previewDevice?.let { info ->
          runCatching { player.setPreferredAudioDevice(info) }
        }

        val start = trimStartMs.coerceAtLeast(0L)
        val clipping = MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(start)
          .apply {
            if (trimEndMs > start) {
              setEndPositionMs(trimEndMs)
            }
          }
          .build()
        val mediaItem = MediaItem.Builder()
          .setUri(Uri.parse(uriString))
          .setClippingConfiguration(clipping)
          .build()

        player.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
              stopEditorPreviewInternal()
            }
          }

          override fun onPlayerError(error: PlaybackException) {
            stopEditorPreviewInternal()
          }
        })
        player.volume = currentSettings.masterVolume.coerceIn(0f, 1f)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        previewPlayer = player

        previewTickerJob = scope.launch {
          while (isActive && previewPlayer != null) {
            val pos = previewPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
            _previewPositionMs.value = start + pos
            delay(32L)
          }
        }
      } catch (_: Exception) {
        stopEditorPreviewInternal()
      }
    }
  }

  fun stopEditorPreview() {
    scope.launch { stopEditorPreviewInternal() }
  }

  private fun stopEditorPreviewInternal() {
    previewTickerJob?.cancel()
    previewTickerJob = null
    previewPlayer?.let {
      runCatching { it.stop() }
      runCatching { it.release() }
    }
    previewPlayer = null
    _previewPositionMs.value = null
  }

  fun release() {
    SoundPadPlaybackService.onStopAllRequested = null
    stopCuePreviewInternal()
    stopEditorPreviewInternal()
    stopAllInternal()
    releaseWakeLock()
    SoundPadPlaybackService.updateForegroundState(context, 0, null)
    scope.cancel()
  }

  private fun preparePoolForNewPad(incomingPad: SoundPad): Boolean {
    val crossfadeEligibleIncoming = currentSettings.globalCrossfadeEnabled && incomingPad.settings.crossfadeEnabled
    var crossfadedAny = false

    if (currentSettings.playMode == PlayMode.SINGLE || currentSettings.playMode == PlayMode.CONTINUOUS) {
      val keys = activeSlots.keys.toList()
      for (id in keys) {
        if (id == incomingPad.id) {
          releaseSlot(id)
          continue
        }
        val slot = activeSlots[id] ?: continue
        if (crossfadeEligibleIncoming && slot.pad.settings.crossfadeEnabled && !slot.isFadingOut) {
          beginFadeOut(slot, currentSettings.crossfadeDurationMs, currentSettings.crossfadeCurve)
          crossfadedAny = true
        } else if (!slot.isFadingOut) {
          val fadeOutMs = effectiveFadeOutMs(slot.pad)
          if (fadeOutMs > 0L) {
            beginFadeOut(slot, fadeOutMs, effectiveFadeCurve(slot.pad))
          } else {
            releaseSlot(id)
          }
        }
      }
    } else {
      // MULTI Mode: Choke Group handling
      val chokeGroup = incomingPad.settings.chokeGroup
      if (chokeGroup > 0) {
        val sameGroupSlots = activeSlots.values
          .filter { it.pad.settings.chokeGroup == chokeGroup && it.pad.id != incomingPad.id }
          .toList()
        for (slot in sameGroupSlots) {
          if (crossfadeEligibleIncoming && slot.pad.settings.crossfadeEnabled && !slot.isFadingOut) {
            beginFadeOut(slot, currentSettings.crossfadeDurationMs, currentSettings.crossfadeCurve)
            crossfadedAny = true
          } else {
            releaseSlot(slot.pad.id)
          }
        }
      }
    }

    // Ensure pool stays under MAX_PLAYER_POOL (evict fading-out slots first, then oldest)
    while (activeSlots.size >= MAX_PLAYER_POOL) {
      val evictId = activeSlots.values.firstOrNull { it.isFadingOut }?.pad?.id
        ?: activeSlots.keys.firstOrNull()
        ?: break
      releaseSlot(evictId)
    }

    return crossfadedAny
  }

  @OptIn(UnstableApi::class)
  private fun applySlotDsp(
    eqProcessor: FiveBandEqAudioProcessor,
    fxProcessor: ReverbEchoAudioProcessor,
    panProcessor: StereoPanAudioProcessor,
    pad: SoundPad,
    settings: GlobalSettings
  ) {
    val fxActive = settings.audioEffectsEnabled
    eqProcessor.setCombinedEq(
      masterEnabled = fxActive && settings.masterEqEnabled,
      masterBandsDb = settings.masterEqBandsDb,
      padEnabled = fxActive && pad.settings.eqEnabled,
      padBandsDb = pad.settings.eqBandsDb
    )
    fxProcessor.setParams(
      if (fxActive) {
        ReverbEchoParams(
          echoEnabled = pad.settings.echoEnabled,
          echoDelayMs = pad.settings.echoDelayMs,
          echoFeedback = pad.settings.echoFeedback,
          echoMix = pad.settings.echoMix,
          reverbEnabled = pad.settings.reverbEnabled,
          reverbRoomSize = pad.settings.reverbRoomSize,
          reverbDamping = pad.settings.reverbDamping,
          reverbMix = pad.settings.reverbMix
        )
      } else {
        ReverbEchoParams()
      }
    )
    panProcessor.setPan(StereoPanMath.combinePan(settings.masterPan, pad.settings.pan))
  }

  private fun buildClippedMediaItem(uri: Uri, startMs: Long, endMs: Long): MediaItem {
    val safeStart = startMs.coerceAtLeast(0L)
    return if (safeStart > 0L || endMs > safeStart) {
      val clipping = MediaItem.ClippingConfiguration.Builder()
        .setStartPositionMs(safeStart)
        .apply {
          if (endMs > safeStart) {
            setEndPositionMs(endMs)
          }
        }
        .build()
      MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(clipping)
        .build()
    } else {
      MediaItem.fromUri(uri)
    }
  }

  @OptIn(UnstableApi::class)
  private fun buildExoPlayerWithProcessors(
    eqProcessor: FiveBandEqAudioProcessor,
    fxProcessor: ReverbEchoAudioProcessor,
    panProcessor: StereoPanAudioProcessor,
    tapProcessor: MasterTapAudioProcessor? = null,
    includeAudioEffects: Boolean = true,
    handleAudioFocus: Boolean = false
  ): ExoPlayer {
    val mediaSourceFactory = DefaultMediaSourceFactory(ramCache.createDataSourceFactory())
    val audioAttributes = AudioAttributes.Builder()
      .setUsage(C.USAGE_MEDIA)
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .build()
    val processorList = buildList<AudioProcessor> {
      if (includeAudioEffects) {
        add(eqProcessor)
        add(fxProcessor)
      }
      add(panProcessor)
      if (tapProcessor != null) {
        add(tapProcessor)
      }
    }
    val processors = processorList.toTypedArray()
    val renderersFactory = object : DefaultRenderersFactory(context) {
      override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
      ): AudioSink {
        return DefaultAudioSink.Builder(context)
          .setEnableFloatOutput(enableFloatOutput)
          .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
          .setAudioProcessors(processors)
          .build()
      }
    }
    return ExoPlayer.Builder(context, renderersFactory)
      .setMediaSourceFactory(mediaSourceFactory)
      .setLoadControl(createLowLatencyLoadControl())
      .setAudioAttributes(audioAttributes, handleAudioFocus)
      .build()
  }

  @OptIn(UnstableApi::class)
  private fun startSlot(pad: SoundPad, viaCrossfade: Boolean, isSequencerSlot: Boolean = false) {
    pendingReverseJobs.remove(pad.id)?.cancel()
    if (!pad.settings.isReversed) {
      startSlotWithUri(
        pad = pad,
        viaCrossfade = viaCrossfade,
        playbackUri = Uri.parse(pad.uriString),
        clipStartMs = pad.settings.trimStartMs.coerceAtLeast(0L),
        clipEndMs = pad.settings.trimEndMs,
        isSequencerSlot = isSequencerSlot
      )
      return
    }

    val cached = ReverseAudioCacheManager.getCachedReversedFileIfReady(
      context = context,
      uriString = pad.uriString,
      trimStartMs = pad.settings.trimStartMs,
      trimEndMs = pad.settings.trimEndMs
    )
    if (cached != null) {
      startSlotWithUri(
        pad = pad,
        viaCrossfade = viaCrossfade,
        playbackUri = Uri.fromFile(cached),
        clipStartMs = 0L,
        clipEndMs = 0L,
        isSequencerSlot = isSequencerSlot
      )
      return
    }

    pendingReverseJobs[pad.id] = scope.launch {
      val result = ReverseAudioCacheManager.getOrBuildReversedWav(
        context = context,
        uriString = pad.uriString,
        trimStartMs = pad.settings.trimStartMs,
        trimEndMs = pad.settings.trimEndMs
      )
      pendingReverseJobs.remove(pad.id)
      result.onSuccess { reversedFile ->
        startSlotWithUri(
          pad = pad,
          viaCrossfade = viaCrossfade,
          playbackUri = Uri.fromFile(reversedFile),
          clipStartMs = 0L,
          clipEndMs = 0L,
          isSequencerSlot = isSequencerSlot
        )
      }.onFailure { err ->
        onPlaybackError?.invoke(
          pad,
          PadErrorType.PLAYBACK_ERROR,
          "Gagal membalik audio \"${pad.displayName}\": ${err.localizedMessage ?: err.javaClass.simpleName}",
          err.stackTraceToString().take(2000)
        )
        syncStateAndService()
      }
    }
  }

  @OptIn(UnstableApi::class)
  private fun startSlotWithUri(
    pad: SoundPad,
    viaCrossfade: Boolean,
    playbackUri: Uri,
    clipStartMs: Long,
    clipEndMs: Long,
    isSequencerSlot: Boolean = false
  ) {
    try {
      val eqProcessor = FiveBandEqAudioProcessor()
      val fxProcessor = ReverbEchoAudioProcessor()
      val panProcessor = StereoPanAudioProcessor()
      val tapProcessor = masterRecorder?.let { rec ->
        MasterTapAudioProcessor(slotId = "${pad.id}_${SystemClock.elapsedRealtimeNanos()}", recorder = rec)
      }
      applySlotDsp(eqProcessor, fxProcessor, panProcessor, pad, currentSettings)

      val player = buildExoPlayerWithProcessors(
        eqProcessor = eqProcessor,
        fxProcessor = fxProcessor,
        panProcessor = panProcessor,
        tapProcessor = tapProcessor,
        includeAudioEffects = currentSettings.audioEffectsEnabled,
        handleAudioFocus = false
      )

      val mainDevice = cachedMainOutputDevice ?: if (preferredOutputDeviceId != null) {
        AudioOutputRouter.findOutputDeviceById(context, preferredOutputDeviceId).also { cachedMainOutputDevice = it }
      } else null
      mainDevice?.let { info ->
        runCatching { player.setPreferredAudioDevice(info) }
      }

      val fadeInMs = if (viaCrossfade) {
        maxOf(effectiveFadeInMs(pad), currentSettings.crossfadeDurationMs)
      } else {
        effectiveFadeInMs(pad)
      }
      val fadeInCurve = if (viaCrossfade) currentSettings.crossfadeCurve else effectiveFadeCurve(pad)
      val initialGain = if (fadeInMs > 0L) 0f else 1f

      applyPadParameters(player, pad, initialGain, tapProcessor)

      val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) {
            onPadEnded(pad)
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          val (type, msg) = mapPlaybackException(pad, error)
          onPlaybackError?.invoke(pad, type, msg, error.stackTraceToString().take(2000))
          releaseSlot(pad.id)
          syncStateAndService()
        }
      }

      player.addListener(listener)
      val mediaItem = buildClippedMediaItem(playbackUri, clipStartMs, clipEndMs)
      player.setMediaItem(mediaItem)
      player.prepare()
      player.play()

      activeSlots[pad.id] = PlayerSlot(
        pad = pad,
        player = player,
        eqProcessor = eqProcessor,
        fxProcessor = fxProcessor,
        panProcessor = panProcessor,
        tapProcessor = tapProcessor,
        listener = listener,
        fadeInStartElapsedMs = SystemClock.elapsedRealtime(),
        fadeInDurationMs = fadeInMs,
        fadeInCurve = fadeInCurve,
        currentEnvelopeGain = initialGain,
        isSequencerSlot = isSequencerSlot
      )

      acquireWakeLock()
      ensureTicker()
      syncStateAndService()
    } catch (e: Exception) {
      val type = if (e is SecurityException) PadErrorType.MISSING_PERMISSION else PadErrorType.PLAYBACK_ERROR
      val msg = "Gagal memutar \"${pad.displayName}\": ${e.localizedMessage ?: e.javaClass.simpleName}"
      onPlaybackError?.invoke(pad, type, msg, e.stackTraceToString().take(2000))
      releaseSlot(pad.id)
      syncStateAndService()
    }
  }

  private fun mapPlaybackException(pad: SoundPad, error: PlaybackException): Pair<PadErrorType, String> {
    val code = error.errorCode
    return when {
      code in PlaybackException.ERROR_CODE_DRM_UNSPECIFIED..PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
        PadErrorType.DRM_PROTECTED to "File \"${pad.fileName}\" dilindungi DRM (${error.errorCodeName})."
      code == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
        PadErrorType.MISSING_PERMISSION to "Izin akses file \"${pad.fileName}\" ditolak sistem."
      code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
        PadErrorType.MISSING_PERMISSION to "File \"${pad.fileName}\" tidak ditemukan atau sudah dipindahkan."
      code in PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED..PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
        PadErrorType.CORRUPT_HEADER to "Header atau struktur audio \"${pad.fileName}\" rusak (${error.errorCodeName})."
      code in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
        PadErrorType.UNSUPPORTED_FORMAT to "Codec audio \"${pad.fileName}\" tidak didukung perangkat (${error.errorCodeName})."
      else ->
        PadErrorType.PLAYBACK_ERROR to "Error pemutaran \"${pad.fileName}\": ${error.localizedMessage ?: error.errorCodeName}"
    }
  }

  private fun beginFadeOut(slot: PlayerSlot, durationMs: Long, curve: FadeCurve) {
    if (durationMs <= 0L) {
      releaseSlot(slot.pad.id)
      return
    }
    slot.fadeOutStartElapsedMs = SystemClock.elapsedRealtime()
    slot.fadeOutDurationMs = durationMs
    slot.fadeOutCurve = curve
    slot.fadeOutStartGain = slot.currentEnvelopeGain.coerceIn(0.01f, 1f)
  }

  private fun computeDuckingMultiplierForSlot(slot: PlayerSlot, hasActiveDuckSource: Boolean): Float {
    if (!currentSettings.duckingEnabled) return 1.0f
    if (isVoiceDuckingActive) return currentSettings.duckingAttenuation
    if (hasActiveDuckSource && !slot.pad.settings.isDuckingSource) {
      return currentSettings.duckingAttenuation
    }
    return 1.0f
  }

  @OptIn(UnstableApi::class)
  private fun refreshAllActiveSlotVolumes() {
    val activeCount = activeSlots.size.coerceAtLeast(1)
    val hasActiveDuckSource = activeSlots.values.any { it.pad.settings.isDuckingSource && !it.isFadingOut }
    activeSlots.values.forEach { slot ->
      val duckMult = computeDuckingMultiplierForSlot(slot, hasActiveDuckSource)
      val gain = AudioMixMath.computeSlotGain(
        masterVolume = currentSettings.masterVolume,
        padVolume = slot.pad.settings.volume,
        envelopeGain = slot.currentEnvelopeGain,
        normalizedGain = slot.pad.settings.normalizedGain,
        autoNormalizeEnabled = currentSettings.autoNormalizeEnabled,
        limiterEnabled = currentSettings.masterLimiterEnabled,
        activeSlotCount = activeCount,
        duckingMultiplier = duckMult
      )
      slot.player.volume = gain
      slot.tapProcessor?.setGain(gain)
    }
  }

  @OptIn(UnstableApi::class)
  private fun applyPadParameters(
    player: ExoPlayer,
    pad: SoundPad,
    envelopeGain: Float,
    tapProcessor: MasterTapAudioProcessor? = activeSlots[pad.id]?.tapProcessor
  ) {
    val activeCount = activeSlots.size.coerceAtLeast(1)
    val hasActiveDuckSource = activeSlots.values.any { it.pad.settings.isDuckingSource && !it.isFadingOut }
    val duckMult = if (currentSettings.duckingEnabled && (isVoiceDuckingActive || (hasActiveDuckSource && !pad.settings.isDuckingSource))) {
      currentSettings.duckingAttenuation
    } else {
      1.0f
    }
    val gain = AudioMixMath.computeSlotGain(
      masterVolume = currentSettings.masterVolume,
      padVolume = pad.settings.volume,
      envelopeGain = envelopeGain,
      normalizedGain = pad.settings.normalizedGain,
      autoNormalizeEnabled = currentSettings.autoNormalizeEnabled,
      limiterEnabled = currentSettings.masterLimiterEnabled,
      activeSlotCount = activeCount,
      duckingMultiplier = duckMult
    )
    player.volume = gain
    tapProcessor?.setGain(gain)
    val speed = pad.settings.speed.coerceIn(0.5f, 2.0f)
    val pitch = pad.settings.pitch.coerceIn(0.5f, 2.0f)
    player.playbackParameters = PlaybackParameters(speed, pitch)
    val shouldLoop = pad.settings.isLooping || pad.settings.triggerMode == PadTriggerMode.LOOP
    player.repeatMode = if (shouldLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
  }

  private fun effectiveFadeInMs(pad: SoundPad): Long {
    return if (pad.settings.fadeInMs > 0L) pad.settings.fadeInMs else currentSettings.globalFadeInMs
  }

  private fun effectiveFadeOutMs(pad: SoundPad): Long {
    return if (pad.settings.fadeOutMs > 0L) pad.settings.fadeOutMs else currentSettings.globalFadeOutMs
  }

  private fun effectiveFadeCurve(pad: SoundPad): FadeCurve {
    return if (pad.settings.fadeInMs > 0L || pad.settings.fadeOutMs > 0L) {
      pad.settings.fadeCurve
    } else {
      currentSettings.globalFadeCurve
    }
  }

  private fun onPadEnded(pad: SoundPad) {
    val slot = activeSlots[pad.id]
    val alreadyTriggeredNext = slot?.continuousTriggeredNext == true
    val isSeqSlot = slot?.isSequencerSlot == true
    val shouldAdvance = currentSettings.playMode == PlayMode.CONTINUOUS && !alreadyTriggeredNext && !isSeqSlot
    releaseSlot(pad.id)

    if (shouldAdvance && currentPlaylist.isNotEmpty()) {
      val currentIndex = currentPlaylist.indexOfFirst { it.id == pad.id }
      if (currentIndex >= 0 && currentIndex + 1 < currentPlaylist.size) {
        val nextPad = currentPlaylist[currentIndex + 1]
        startSlot(nextPad, viaCrossfade = false)
        return
      }
    }
    syncStateAndService()
  }

  private fun stopAllInternal() {
    pendingReverseJobs.values.forEach { it.cancel() }
    pendingReverseJobs.clear()
    val ids = activeSlots.keys.toList()
    for (id in ids) {
      releaseSlot(id)
    }
  }

  private fun releaseSlot(padId: String) {
    pendingReverseJobs.remove(padId)?.cancel()
    val slot = activeSlots.remove(padId) ?: return
    runCatching { slot.player.removeListener(slot.listener) }
    runCatching { slot.player.stop() }
    runCatching { slot.player.release() }
  }

  private fun hasActiveEnvelopeFade(): Boolean {
    val now = SystemClock.elapsedRealtime()
    for (slot in activeSlots.values) {
      if (slot.isFadingOut) return true
      if (slot.fadeInDurationMs > 0L && (now - slot.fadeInStartElapsedMs) < slot.fadeInDurationMs) return true
      if (effectiveFadeOutMs(slot.pad) > 0L) return true
      if (currentSettings.playMode == PlayMode.CONTINUOUS && currentSettings.globalCrossfadeEnabled) return true
    }
    return false
  }

  private fun ensureTicker() {
    if (tickerJob?.isActive == true) return
    tickerJob = scope.launch {
      while (isActive && activeSlots.isNotEmpty()) {
        processEnvelopesAndPublish()
        val tickInterval = if (hasActiveEnvelopeFade()) ENVELOPE_TICK_MS else UI_PROGRESS_PUBLISH_MS
        delay(tickInterval)
      }
      publishPlaybackStates()
    }
  }

  private fun processEnvelopesAndPublish() {
    val now = SystemClock.elapsedRealtime()
    val toRelease = mutableListOf<String>()
    var nextContinuousPad: SoundPad? = null
    var nextContinuousViaCrossfade = false

    for ((padId, slot) in activeSlots) {
      val fadeInGain = if (slot.fadeInDurationMs > 0L) {
        val t = ((now - slot.fadeInStartElapsedMs).toFloat() / slot.fadeInDurationMs.toFloat()).coerceIn(0f, 1f)
        FadeCurveMath.gainIn(slot.fadeInCurve, t)
      } else {
        1f
      }

      val duration = slot.player.duration.takeIf { it > 0L } ?: slot.pad.durationMs
      val position = slot.player.currentPosition.coerceAtLeast(0L)
      val remainingMs = if (duration > 0L) (duration - position).coerceAtLeast(0L) else Long.MAX_VALUE
      val isLooping = slot.pad.settings.isLooping || slot.pad.settings.triggerMode == PadTriggerMode.LOOP

      // Proactive Continuous Crossfade near end of track
      if (currentSettings.playMode == PlayMode.CONTINUOUS &&
        !slot.isFadingOut &&
        !slot.continuousTriggeredNext &&
        !isLooping &&
        currentSettings.globalCrossfadeEnabled &&
        slot.pad.settings.crossfadeEnabled &&
        duration > currentSettings.crossfadeDurationMs &&
        remainingMs in 1..currentSettings.crossfadeDurationMs
      ) {
        val currentIdx = currentPlaylist.indexOfFirst { it.id == padId }
        if (currentIdx >= 0 && currentIdx + 1 < currentPlaylist.size) {
          val candidateNext = currentPlaylist[currentIdx + 1]
          if (candidateNext.settings.crossfadeEnabled && !activeSlots.containsKey(candidateNext.id)) {
            slot.continuousTriggeredNext = true
            beginFadeOut(slot, currentSettings.crossfadeDurationMs, currentSettings.crossfadeCurve)
            nextContinuousPad = candidateNext
            nextContinuousViaCrossfade = true
          }
        }
      }

      val fadeOutGain = if (slot.fadeOutStartElapsedMs != null) {
        val elapsed = (now - slot.fadeOutStartElapsedMs!!).toFloat()
        val t = (elapsed / slot.fadeOutDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        if (t >= 1f) {
          toRelease.add(padId)
          0f
        } else {
          slot.fadeOutStartGain * FadeCurveMath.gainOut(slot.fadeOutCurve, t)
        }
      } else if (!isLooping && duration > 0L) {
        val endFadeMs = effectiveFadeOutMs(slot.pad)
        if (endFadeMs > 0L && remainingMs in 0..endFadeMs) {
          val t = (1f - (remainingMs.toFloat() / endFadeMs.toFloat())).coerceIn(0f, 1f)
          FadeCurveMath.gainOut(effectiveFadeCurve(slot.pad), t)
        } else {
          1f
        }
      } else {
        1f
      }

      val envelopeGain = if (slot.isFadingOut) {
        fadeOutGain
      } else {
        (fadeInGain * fadeOutGain).coerceIn(0f, 1f)
      }

      slot.currentEnvelopeGain = envelopeGain
    }
    refreshAllActiveSlotVolumes()

    if (toRelease.isNotEmpty()) {
      toRelease.forEach { releaseSlot(it) }
      if (nextContinuousPad == null) {
        syncStateAndService()
      }
    }

    if (nextContinuousPad != null) {
      startSlot(nextContinuousPad, viaCrossfade = nextContinuousViaCrossfade)
      return
    }

    if ((now - lastUiPublishElapsedMs) >= UI_PROGRESS_PUBLISH_MS) {
      publishPlaybackStates()
    }
  }

  private fun syncStateAndService() {
    publishPlaybackStates()
    val count = activeSlots.size
    if (count > 0) {
      val firstLabel = activeSlots.values.firstOrNull()?.pad?.displayName
      SoundPadPlaybackService.updateForegroundState(context, count, firstLabel)
      acquireWakeLock()
    } else {
      tickerJob?.cancel()
      tickerJob = null
      releaseWakeLock()
      SoundPadPlaybackService.updateForegroundState(context, 0, null)
    }
  }

  private fun publishPlaybackStates() {
    lastUiPublishElapsedMs = SystemClock.elapsedRealtime()
    if (activeSlots.isEmpty()) {
      if (_playbackStates.value.isNotEmpty()) {
        _playbackStates.value = emptyMap()
      }
      return
    }
    val nextMap = HashMap<String, PadPlaybackState>(activeSlots.size)
    for ((padId, slot) in activeSlots) {
      val duration = slot.player.duration.takeIf { it > 0L } ?: slot.pad.durationMs
      val position = slot.player.currentPosition.coerceAtLeast(0L)
      val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
      nextMap[padId] = PadPlaybackState(
        padId = padId,
        isPlaying = true,
        isFadingOut = slot.isFadingOut,
        positionMs = position,
        durationMs = duration,
        progress = progress
      )
    }
    _playbackStates.value = nextMap
  }

  private fun acquireWakeLock() {
    runCatching {
      if (!wakeLock.isHeld) {
        wakeLock.acquire(60L * 60L * 1000L)
      }
    }
  }

  private fun releaseWakeLock() {
    runCatching {
      if (wakeLock.isHeld) {
        wakeLock.release()
      }
    }
  }
}
