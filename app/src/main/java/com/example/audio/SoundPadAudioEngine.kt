package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.Trace
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
import com.example.domain.model.LatencyTestReport
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadSettings
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PlayMode
import com.example.domain.model.RetriggerMode
import com.example.domain.model.SoundPad
import com.example.domain.model.TapLatencyMetrics
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

data class LoadControlDurations(
  val minBufferMs: Int,
  val maxBufferMs: Int,
  val bufferForPlaybackMs: Int,
  val bufferForPlaybackAfterRebufferMs: Int
)

object AudioBufferMath {
  fun computeLoadControlDurations(audioBufferSizeMs: Int): LoadControlDurations {
    val playbackMs = audioBufferSizeMs.coerceIn(20, 500)
    val afterRebufferMs = (playbackMs * 2).coerceAtLeast(100).coerceAtLeast(playbackMs)
    val minBufferMs = maxOf(1000, afterRebufferMs * 2)
    val maxBufferMs = maxOf(4000, minBufferMs * 2)
    return LoadControlDurations(
      minBufferMs = minBufferMs,
      maxBufferMs = maxBufferMs,
      bufferForPlaybackMs = playbackMs,
      bufferForPlaybackAfterRebufferMs = afterRebufferMs
    )
  }
}

object AudioMixMath {
  fun computeSlotGain(
    masterVolume: Float,
    padVolume: Float,
    envelopeGain: Float,
    normalizedGain: Float,
    autoNormalizeEnabled: Boolean,
    limiterEnabled: Boolean,
    activeSlotCount: Int,
    duckingMultiplier: Float,
    useRealtimeBusLimiter: Boolean = false
  ): Float {
    val normFactor = if (autoNormalizeEnabled) normalizedGain.coerceIn(0.35f, 1.60f) else 1.0f
    val raw = masterVolume.coerceIn(0f, 1f) *
      padVolume.coerceIn(0f, 1f) *
      envelopeGain.coerceIn(0f, 1f) *
      normFactor *
      duckingMultiplier.coerceIn(0.05f, 1f)

    val limiterScale = if (limiterEnabled && !useRealtimeBusLimiter && activeSlotCount > 1) {
      // Fallback soft-knee headroom scaling when real-time bus limiter is inactive
      (1.12f / sqrt(activeSlotCount.toFloat())).coerceIn(0.32f, 1.0f)
    } else {
      1.0f
    }
    return (raw * limiterScale).coerceIn(0f, 1f)
  }

  fun computeCueGain(padVolume: Float, cueVolume: Float): Float {
    return (padVolume.coerceIn(0f, 1f) * cueVolume.coerceIn(0f, 1f)).coerceIn(0f, 1f)
  }
}

data class ActiveVoiceSnapshot(
  val voiceId: String,
  val padId: String,
  val chokeGroup: Int,
  val isFadingOut: Boolean,
  val crossfadeEnabled: Boolean
)

object RetriggerVoicePlanner {
  fun resolveEffectiveRetriggerMode(padSettings: PadSettings, globalSettings: GlobalSettings): RetriggerMode {
    return padSettings.retriggerMode ?: globalSettings.retriggerMode
  }

  fun resolveEffectiveMaxVoices(globalSettings: GlobalSettings): Int {
    return globalSettings.maxVoices.coerceIn(1, SoundPadAudioEngine.MAX_PLAYER_POOL)
  }

  fun resolveEffectivePadVoiceCap(padSettings: PadSettings, globalSettings: GlobalSettings): Int {
    val globalCap = resolveEffectiveMaxVoices(globalSettings)
    return if (padSettings.maxVoices > 0) {
      minOf(padSettings.maxVoices.coerceIn(1, SoundPadAudioEngine.MAX_PLAYER_POOL), globalCap)
    } else {
      globalCap
    }
  }

  fun selectVoiceToSteal(voices: List<ActiveVoiceSnapshot>): ActiveVoiceSnapshot? {
    return voices.firstOrNull { it.isFadingOut } ?: voices.firstOrNull()
  }

  fun computeEvictionsForNewVoice(
    voices: List<ActiveVoiceSnapshot>,
    incomingPadId: String,
    perPadCap: Int,
    globalCap: Int
  ): List<String> {
    val remaining = voices.toMutableList()
    val evictions = mutableListOf<String>()
    val safePerPad = perPadCap.coerceIn(1, SoundPadAudioEngine.MAX_PLAYER_POOL)
    val safeGlobal = globalCap.coerceIn(1, SoundPadAudioEngine.MAX_PLAYER_POOL)

    while (remaining.count { it.padId == incomingPadId } >= safePerPad) {
      val samePadVoices = remaining.filter { it.padId == incomingPadId }
      val victim = selectVoiceToSteal(samePadVoices) ?: break
      remaining.remove(victim)
      evictions.add(victim.voiceId)
    }

    while (remaining.size >= safeGlobal) {
      val victim = selectVoiceToSteal(remaining) ?: break
      remaining.remove(victim)
      evictions.add(victim.voiceId)
    }
    return evictions
  }
}

class LatencyMetricsTracker {
  private var sampleCount = 0
  private var sumMs = 0.0
  private var minMs = Double.MAX_VALUE
  private var maxMs = 0.0
  private var lastMs = 0.0
  private var lastDispatchMs = 0.0

  fun record(
    tapDownNanos: Long,
    dispatchNanos: Long,
    soundStartedNanos: Long,
    warmSlotsReady: Int,
    idlePoolSize: Int,
    framesPerBurst: Int,
    nativeSampleRateHz: Int
  ): TapLatencyMetrics {
    val safeDispatch = ((dispatchNanos - tapDownNanos).coerceAtLeast(0L)) / 1_000_000.0
    val safeTotal = ((soundStartedNanos - tapDownNanos).coerceAtLeast(0L)) / 1_000_000.0
    sampleCount++
    sumMs += safeTotal
    lastMs = safeTotal
    lastDispatchMs = safeDispatch
    if (safeTotal < minMs) minMs = safeTotal
    if (safeTotal > maxMs) maxMs = safeTotal
    return snapshot(
      warmSlotsReady = warmSlotsReady,
      idlePoolSize = idlePoolSize,
      framesPerBurst = framesPerBurst,
      nativeSampleRateHz = nativeSampleRateHz
    )
  }

  fun reset(
    warmSlotsReady: Int,
    idlePoolSize: Int,
    framesPerBurst: Int,
    nativeSampleRateHz: Int
  ): TapLatencyMetrics {
    sampleCount = 0
    sumMs = 0.0
    minMs = Double.MAX_VALUE
    maxMs = 0.0
    lastMs = 0.0
    lastDispatchMs = 0.0
    return snapshot(warmSlotsReady, idlePoolSize, framesPerBurst, nativeSampleRateHz)
  }

  fun snapshot(
    warmSlotsReady: Int,
    idlePoolSize: Int,
    framesPerBurst: Int,
    nativeSampleRateHz: Int
  ): TapLatencyMetrics {
    val avg = if (sampleCount > 0) sumMs / sampleCount else 0.0
    val safeMin = if (sampleCount > 0) minMs else 0.0
    return TapLatencyMetrics(
      lastTapToSoundMs = lastMs,
      avgTapToSoundMs = avg,
      minTapToSoundMs = safeMin,
      maxTapToSoundMs = maxMs,
      lastDispatchMs = lastDispatchMs,
      sampleCount = sampleCount,
      warmSlotsReady = warmSlotsReady,
      idlePoolSize = idlePoolSize,
      framesPerBurst = framesPerBurst,
      nativeSampleRateHz = nativeSampleRateHz
    )
  }
}

class SoundPadAudioEngine(
  private val context: Context,
  private val ramCache: InMemoryAudioCache
) {

  companion object {
    const val MAX_PLAYER_POOL = 16
    private const val INITIAL_IDLE_PREWARM = 4
    private const val MAX_WARMED_PADS = 6
    private const val WARM_SHORT_CLIP_MAX_MS = 15_000L
    private const val ENVELOPE_TICK_MS = 16L // ~60fps smooth fade envelopes when fades are active
    private const val UI_PROGRESS_PUBLISH_MS = 33L // ~30fps UI progress updates ONLY for currently playing pads
  }

  var onPlaybackError: ((pad: SoundPad, errorType: PadErrorType, message: String, stackTrace: String) -> Unit)? = null

  @OptIn(UnstableApi::class)
  val limiterProcessor = MasterLimiterAudioProcessor()

  @OptIn(UnstableApi::class)
  private val mixerBus = MasterAudioMixerBus(context = context, limiterProcessor = limiterProcessor)

  @Volatile
  private var masterRecorder: MasterOutputRecorder? = null

  @OptIn(UnstableApi::class)
  fun attachMasterRecorder(recorder: MasterOutputRecorder) {
    masterRecorder = recorder
    mixerBus.attachMasterRecorder(recorder)
  }

  private class PooledPlayerSlot @OptIn(UnstableApi::class) constructor(
    val slotTag: String,
    val player: ExoPlayer,
    val eqProcessor: FiveBandEqAudioProcessor,
    val fxProcessor: ReverbEchoAudioProcessor,
    val panProcessor: StereoPanAudioProcessor,
    var tapProcessor: MasterBusSlotTapAudioProcessor?,
    var bufferSizeMs: Int,
    var currentListener: Player.Listener? = null
  )

  private data class WarmedPadSlot(
    val pad: SoundPad,
    val pooled: PooledPlayerSlot,
    val uriKey: String,
    val clipStartMs: Long,
    val clipEndMs: Long
  )

  private data class PlayerSlot @OptIn(UnstableApi::class) constructor(
    val voiceId: String,
    var pad: SoundPad,
    val pooled: PooledPlayerSlot,
    val player: ExoPlayer,
    val eqProcessor: FiveBandEqAudioProcessor,
    val fxProcessor: ReverbEchoAudioProcessor,
    val panProcessor: StereoPanAudioProcessor,
    val tapProcessor: MasterBusSlotTapAudioProcessor?,
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

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val mainHandler = Handler(Looper.getMainLooper())
  private val audioThread = HandlerThread("SoundPadAudioDispatcher", Process.THREAD_PRIORITY_AUDIO).apply {
    start()
  }
  private val audioHandler = Handler(audioThread.looper)
  private val commandQueue = ConcurrentLinkedQueue<() -> Unit>()
  private val drainScheduled = AtomicBoolean(false)
  private val releasedFlag = AtomicBoolean(false)

  // Keyed by unique voiceId ("<padId>#<seq>") so stacked polyphonic voices never collide
  private val activeSlots = LinkedHashMap<String, PlayerSlot>()
  private val idleSlotPool = ArrayDeque<PooledPlayerSlot>()
  private val warmedPadSlots = LinkedHashMap<String, WarmedPadSlot>(8, 0.75f, true)
  private val pendingReverseJobs = HashMap<String, MutableList<Job>>()
  private var tickerScheduled = false
  private var slotSequenceCounter = 0L
  private var voiceSequenceCounter = 0L

  private val _playbackStates = MutableStateFlow<Map<String, PadPlaybackState>>(emptyMap())
  val playbackStates: StateFlow<Map<String, PadPlaybackState>> = _playbackStates.asStateFlow()

  private val latencyTracker = LatencyMetricsTracker()
  private val _tapLatencyMetrics = MutableStateFlow(TapLatencyMetrics())
  val tapLatencyMetrics: StateFlow<TapLatencyMetrics> = _tapLatencyMetrics.asStateFlow()

  @Volatile
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
  private var activeCuePad: SoundPad? = null

  private var appliedBufferSizeMs: Int = currentSettings.audioBufferSizeMs
  private val _hasPendingBufferRebuild = MutableStateFlow(false)
  val hasPendingBufferRebuild: StateFlow<Boolean> = _hasPendingBufferRebuild.asStateFlow()

  private val _isRunningLatencyTest = MutableStateFlow(false)
  val isRunningLatencyTest: StateFlow<Boolean> = _isRunningLatencyTest.asStateFlow()

  private val _latencyTestReport = MutableStateFlow<LatencyTestReport?>(null)
  val latencyTestReport: StateFlow<LatencyTestReport?> = _latencyTestReport.asStateFlow()

  private var latencyTestPlayer: ExoPlayer? = null
  private var latencyTestTimeoutRunnable: Runnable? = null

  private val drainRunnable = object : Runnable {
    override fun run() {
      drainScheduled.set(false)
      while (!releasedFlag.get()) {
        val task = commandQueue.poll() ?: break
        runCatching { task() }
      }
      if (!releasedFlag.get() && !commandQueue.isEmpty() && drainScheduled.compareAndSet(false, true)) {
        audioHandler.post(this)
      }
    }
  }

  private val tickerRunnable = object : Runnable {
    override fun run() {
      tickerScheduled = false
      if (releasedFlag.get()) return
      if (activeSlots.isEmpty()) {
        publishPlaybackStates()
        return
      }
      processEnvelopesAndPublish()
      if (activeSlots.isNotEmpty()) {
        val interval = if (hasActiveEnvelopeFade()) ENVELOPE_TICK_MS else UI_PROGRESS_PUBLISH_MS
        tickerScheduled = true
        audioHandler.postDelayed(this, interval)
      } else {
        publishPlaybackStates()
      }
    }
  }

  private fun enqueueCommand(action: () -> Unit) {
    if (releasedFlag.get()) return
    commandQueue.offer(action)
    if (drainScheduled.compareAndSet(false, true)) {
      audioHandler.post(drainRunnable)
    }
  }

  internal fun flushDispatcherForTest(timeoutMs: Long = 1500L) {
    if (releasedFlag.get()) return
    if (Looper.myLooper() == audioThread.looper) {
      drainRunnable.run()
      return
    }
    val latch = CountDownLatch(1)
    enqueueCommand { latch.countDown() }
    latch.await(timeoutMs, TimeUnit.MILLISECONDS)
  }

  @OptIn(UnstableApi::class)
  private fun createLowLatencyLoadControl(bufferMs: Int = currentSettings.audioBufferSizeMs): DefaultLoadControl {
    val d = AudioBufferMath.computeLoadControlDurations(bufferMs)
    return DefaultLoadControl.Builder()
      .setBufferDurationsMs(
        d.minBufferMs,
        d.maxBufferMs,
        d.bufferForPlaybackMs,
        d.bufferForPlaybackAfterRebufferMs
      )
      .setPrioritizeTimeOverSizeThresholds(true)
      .build()
  }

  private val wakeLock: PowerManager.WakeLock by lazy {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundPadPro::AudioEngineWakeLock").apply {
      setReferenceCounted(false)
    }
  }

  init {
    SoundPadPlaybackService.onStopAllRequested = {
      stopAll()
    }
    enqueueCommand {
      mixerBus.refreshHardwareAudioSpec()
      publishLatencySnapshotLocked()
      preWarmIdlePoolSlotsLocked(INITIAL_IDLE_PREWARM)
    }
  }

  fun resetLatencyMetrics() {
    enqueueCommand {
      _tapLatencyMetrics.value = latencyTracker.reset(
        warmSlotsReady = warmedPadSlots.size,
        idlePoolSize = idleSlotPool.size,
        framesPerBurst = mixerBus.framesPerBurst,
        nativeSampleRateHz = mixerBus.nativeSampleRateHz
      )
    }
  }

  private fun publishLatencySnapshotLocked() {
    _tapLatencyMetrics.value = latencyTracker.snapshot(
      warmSlotsReady = warmedPadSlots.size,
      idlePoolSize = idleSlotPool.size,
      framesPerBurst = mixerBus.framesPerBurst,
      nativeSampleRateHz = mixerBus.nativeSampleRateHz
    )
  }

  @OptIn(UnstableApi::class)
  fun updateGlobalSettings(settings: GlobalSettings) {
    val bufferChanged = settings.audioBufferSizeMs != appliedBufferSizeMs
    currentSettings = settings
    if (bufferChanged) {
      appliedBufferSizeMs = settings.audioBufferSizeMs
    }
    limiterProcessor.setEnabled(settings.masterLimiterEnabled)
    mixerBus.setLimiterEnabled(settings.masterLimiterEnabled)
    enqueueCommand {
      if (bufferChanged) {
        _hasPendingBufferRebuild.value = activeSlots.isNotEmpty()
        rebuildIdleAndWarmedPoolForBufferChangeLocked()
      }
      if (settings.masterLimiterEnabled) {
        mixerBus.ensureStarted(settings.audioBufferSizeMs)
      } else {
        mixerBus.stop()
      }
      activeSlots.values.forEach { slot ->
        applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, slot.pad, settings)
      }
      warmedPadSlots.values.forEach { warmed ->
        applySlotDsp(
          warmed.pooled.eqProcessor,
          warmed.pooled.fxProcessor,
          warmed.pooled.panProcessor,
          warmed.pad,
          settings
        )
      }
      activeCuePad?.let { cuePad ->
        cuePlayer?.volume = AudioMixMath.computeCueGain(cuePad.settings.volume, settings.cueVolume)
      }
      refreshAllActiveSlotVolumes()
      publishLatencySnapshotLocked()
    }
  }

  @OptIn(UnstableApi::class)
  fun updatePlaylist(pads: List<SoundPad>) {
    currentPlaylist = pads
    enqueueCommand {
      val padMap = pads.associateBy { it.id }
      activeSlots.values.forEach { slot ->
        val updatedPad = padMap[slot.pad.id] ?: return@forEach
        slot.pad = updatedPad
        applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, updatedPad, currentSettings)
        applyPadParameters(slot.player, updatedPad, slot.currentEnvelopeGain, slot.tapProcessor)
      }
      refreshAllActiveSlotVolumes()
      preWarmPlaylistCandidatesLocked(pads)
      publishLatencySnapshotLocked()
    }
  }

  fun setRetriggerMode(mode: RetriggerMode) {
    currentSettings = currentSettings.copy(retriggerMode = mode)
  }

  fun setMaxVoices(maxVoices: Int) {
    currentSettings = currentSettings.copy(maxVoices = maxVoices.coerceIn(1, MAX_PLAYER_POOL))
  }

  fun setPlayMode(mode: PlayMode) {
    currentSettings = currentSettings.copy(playMode = mode)
  }

  fun setMasterVolume(volume: Float) {
    currentSettings = currentSettings.copy(masterVolume = volume.coerceIn(0f, 1f))
    enqueueCommand {
      refreshAllActiveSlotVolumes()
    }
  }

  fun setCueVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    currentSettings = currentSettings.copy(cueVolume = clamped)
    enqueueCommand {
      activeCuePad?.let { cuePad ->
        cuePlayer?.volume = AudioMixMath.computeCueGain(cuePad.settings.volume, clamped)
      }
    }
  }

  fun setAudioBufferSizeMs(bufferMs: Int) {
    val clamped = bufferMs.coerceIn(20, 500)
    if (clamped == appliedBufferSizeMs && currentSettings.audioBufferSizeMs == clamped) return
    currentSettings = currentSettings.copy(audioBufferSizeMs = clamped)
    appliedBufferSizeMs = clamped
    enqueueCommand {
      _hasPendingBufferRebuild.value = activeSlots.isNotEmpty()
      rebuildIdleAndWarmedPoolForBufferChangeLocked()
      publishLatencySnapshotLocked()
    }
  }

  @OptIn(UnstableApi::class)
  fun setMasterPan(pan: Float) {
    val clamped = pan.coerceIn(-1f, 1f)
    currentSettings = currentSettings.copy(masterPan = clamped)
    enqueueCommand {
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
    enqueueCommand {
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
    enqueueCommand {
      refreshAllActiveSlotVolumes()
    }
  }

  @OptIn(UnstableApi::class)
  fun setPreferredOutputDeviceId(deviceId: Int?) {
    preferredOutputDeviceId = deviceId
    enqueueCommand {
      val info = AudioOutputRouter.findOutputDeviceById(context, deviceId)
      cachedMainOutputDevice = info
      mixerBus.setPreferredOutputDevice(info)
      activeSlots.values.forEach { slot ->
        runCatching { slot.player.setPreferredAudioDevice(info) }
      }
      warmedPadSlots.values.forEach { warmed ->
        runCatching { warmed.pooled.player.setPreferredAudioDevice(info) }
      }
      idleSlotPool.forEach { pooled ->
        runCatching { pooled.player.setPreferredAudioDevice(info) }
      }
    }
  }

  @OptIn(UnstableApi::class)
  fun setCueOutputDeviceId(deviceId: Int?) {
    cueOutputDeviceId = deviceId
    enqueueCommand {
      val info = AudioOutputRouter.findOutputDeviceById(context, deviceId)
      cachedCueOutputDevice = info
      runCatching { cuePlayer?.setPreferredAudioDevice(info) }
      runCatching { previewPlayer?.setPreferredAudioDevice(info) }
    }
  }

  @OptIn(UnstableApi::class)
  fun runLatencyTest() {
    if (_isRunningLatencyTest.value) return
    scope.launch {
      _isRunningLatencyTest.value = true
      val configuredMs = currentSettings.audioBufferSizeMs.coerceIn(20, 500)
      try {
        val wavFile = withContext(Dispatchers.IO) { ensureLatencyTestPingWav() }
        enqueueCommand {
          releaseLatencyTestPlayer()
          val startElapsed = SystemClock.elapsedRealtime()
          val player = buildExoPlayerWithProcessors(
            eqProcessor = FiveBandEqAudioProcessor(),
            fxProcessor = ReverbEchoAudioProcessor(),
            panProcessor = StereoPanAudioProcessor(),
            includeAudioEffects = false,
            handleAudioFocus = false
          )
          cachedMainOutputDevice?.let { info ->
            runCatching { player.setPreferredAudioDevice(info) }
          }
          latencyTestPlayer = player
          var recordedLatency = false
          player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
              if (!recordedLatency && playbackState == Player.STATE_READY) {
                recordedLatency = true
                val readyElapsed = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(1L)
                _latencyTestReport.value = LatencyTestReport(
                  configuredBufferMs = configuredMs,
                  measuredReadyMs = readyElapsed
                )
              } else if (playbackState == Player.STATE_ENDED) {
                releaseLatencyTestPlayer()
                _isRunningLatencyTest.value = false
              }
            }

            override fun onPlayerError(error: PlaybackException) {
              if (!recordedLatency) {
                recordedLatency = true
                _latencyTestReport.value = LatencyTestReport(
                  configuredBufferMs = configuredMs,
                  measuredReadyMs = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(1L)
                )
              }
              releaseLatencyTestPlayer()
              _isRunningLatencyTest.value = false
            }
          })
          player.volume = (currentSettings.masterVolume * 0.45f).coerceIn(0.05f, 0.75f)
          player.setMediaItem(MediaItem.fromUri(Uri.fromFile(wavFile)))
          player.prepare()
          player.play()

          val timeoutRunnable = Runnable {
            if (_isRunningLatencyTest.value) {
              if (!recordedLatency) {
                _latencyTestReport.value = LatencyTestReport(
                  configuredBufferMs = configuredMs,
                  measuredReadyMs = (SystemClock.elapsedRealtime() - startElapsed).coerceAtLeast(1L)
                )
              }
              releaseLatencyTestPlayer()
              _isRunningLatencyTest.value = false
            }
          }
          latencyTestTimeoutRunnable = timeoutRunnable
          audioHandler.postDelayed(timeoutRunnable, 600L)
        }
      } catch (_: Exception) {
        _latencyTestReport.value = LatencyTestReport(
          configuredBufferMs = configuredMs,
          measuredReadyMs = configuredMs.toLong()
        )
        enqueueCommand { releaseLatencyTestPlayer() }
        _isRunningLatencyTest.value = false
      }
    }
  }

  private fun releaseLatencyTestPlayer() {
    latencyTestTimeoutRunnable?.let { audioHandler.removeCallbacks(it) }
    latencyTestTimeoutRunnable = null
    latencyTestPlayer?.let { p ->
      runCatching { p.stop() }
      runCatching { p.release() }
    }
    latencyTestPlayer = null
  }

  private fun ensureLatencyTestPingWav(): File {
    val file = File(context.cacheDir, "soundpad_latency_ping.wav")
    if (file.exists() && file.length() > 100L) return file
    val sampleRate = 44100
    val durationSamples = (sampleRate * 0.065f).toInt()
    val dataBytes = durationSamples * 2
    RandomAccessFile(file, "rw").use { raf ->
      raf.setLength(0)
      raf.writeBytes("RIFF")
      raf.write(intLe(36 + dataBytes))
      raf.writeBytes("WAVE")
      raf.writeBytes("fmt ")
      raf.write(intLe(16))
      raf.write(shortLe(1))
      raf.write(shortLe(1))
      raf.write(intLe(sampleRate))
      raf.write(intLe(sampleRate * 2))
      raf.write(shortLe(2))
      raf.write(shortLe(16))
      raf.writeBytes("data")
      raf.write(intLe(dataBytes))
      val buf = ByteArray(dataBytes)
      for (i in 0 until durationSamples) {
        val env = 1.0 - (i.toDouble() / durationSamples.toDouble())
        val sample = (sin(2.0 * PI * 880.0 * i / sampleRate) * env * 12000.0).toInt().coerceIn(-32767, 32767)
        buf[i * 2] = (sample and 0xFF).toByte()
        buf[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
      }
      raf.write(buf)
    }
    return file
  }

  private fun intLe(v: Int): ByteArray = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v ushr 8) and 0xFF).toByte(),
    ((v ushr 16) and 0xFF).toByte(),
    ((v ushr 24) and 0xFF).toByte()
  )

  private fun shortLe(v: Int): ByteArray = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v ushr 8) and 0xFF).toByte()
  )

  @OptIn(UnstableApi::class)
  fun updateActivePadParameters(pad: SoundPad) {
    enqueueCommand {
      var updatedAny = false
      activeSlots.values.forEach { slot ->
        if (slot.pad.id == pad.id) {
          slot.pad = pad
          applySlotDsp(slot.eqProcessor, slot.fxProcessor, slot.panProcessor, pad, currentSettings)
          applyPadParameters(slot.player, pad, slot.currentEnvelopeGain, slot.tapProcessor)
          updatedAny = true
        }
      }
      if (updatedAny) {
        refreshAllActiveSlotVolumes()
      }
    }
  }

  fun setActivePadsSpeed(speed: Float) {
    val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
    enqueueCommand {
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
    enqueueCommand {
      activeSlots.values.forEach { slot ->
        val updatedSettings = slot.pad.settings.copy(pitch = clampedPitch)
        slot.pad = slot.pad.copy(settings = updatedSettings)
        val currentSpeed = slot.pad.settings.speed.coerceIn(0.5f, 2.0f)
        slot.player.playbackParameters = PlaybackParameters(currentSpeed, clampedPitch)
      }
    }
  }

  private fun activeVoicesForPadLocked(padId: String): List<PlayerSlot> {
    return activeSlots.values.filter { it.pad.id == padId }
  }

  private fun nextVoiceIdLocked(padId: String): String {
    voiceSequenceCounter++
    return "${padId}#$voiceSequenceCounter"
  }

  private fun cancelPendingReverseJobsLocked(padId: String) {
    pendingReverseJobs.remove(padId)?.forEach { it.cancel() }
  }

  fun triggerPad(pad: SoundPad, tapDownNanos: Long = SystemClock.elapsedRealtimeNanos()) {
    enqueueCommand {
      Trace.beginSection("engine_trigger")
      try {
        val dispatchNanos = SystemClock.elapsedRealtimeNanos()
        val retriggerMode = RetriggerVoicePlanner.resolveEffectiveRetriggerMode(pad.settings, currentSettings)
        val existingVoices = activeVoicesForPadLocked(pad.id)

        if (existingVoices.isNotEmpty()) {
          when (pad.settings.triggerMode) {
            PadTriggerMode.ONE_SHOT, PadTriggerMode.HOLD -> {
              if (retriggerMode == RetriggerMode.RESTART) {
                releaseAllVoicesForPadLocked(pad.id, rearmLast = false)
              }
            }
            PadTriggerMode.TOGGLE, PadTriggerMode.LOOP -> {
              if (retriggerMode == RetriggerMode.RESTART) {
                if (existingVoices.all { it.isFadingOut }) {
                  releaseAllVoicesForPadLocked(pad.id, rearmLast = true)
                  syncStateAndService()
                } else {
                  val fadeOutMs = effectiveFadeOutMs(pad)
                  if (fadeOutMs > 0L) {
                    existingVoices.filter { !it.isFadingOut }.forEach { voice ->
                      beginFadeOut(voice, fadeOutMs, effectiveFadeCurve(pad))
                    }
                    syncStateAndService()
                  } else {
                    releaseAllVoicesForPadLocked(pad.id, rearmLast = true)
                    syncStateAndService()
                  }
                }
                return@enqueueCommand
              }
              // RetriggerMode.LAYER -> stack a new voice on top (up to maxVoices with stealing)
            }
          }
        }

        val didCrossfade = preparePoolForNewPad(pad)
        startSlot(
          pad = pad,
          viaCrossfade = didCrossfade,
          tapDownNanos = tapDownNanos,
          dispatchNanos = dispatchNanos
        )
      } finally {
        Trace.endSection()
      }
    }
  }

  fun triggerHoldStart(pad: SoundPad, tapDownNanos: Long = SystemClock.elapsedRealtimeNanos()) {
    enqueueCommand {
      Trace.beginSection("engine_trigger")
      try {
        val dispatchNanos = SystemClock.elapsedRealtimeNanos()
        val retriggerMode = RetriggerVoicePlanner.resolveEffectiveRetriggerMode(pad.settings, currentSettings)
        if (retriggerMode == RetriggerMode.RESTART && activeVoicesForPadLocked(pad.id).isNotEmpty()) {
          releaseAllVoicesForPadLocked(pad.id, rearmLast = false)
        }
        val didCrossfade = preparePoolForNewPad(pad)
        startSlot(
          pad = pad,
          viaCrossfade = didCrossfade,
          tapDownNanos = tapDownNanos,
          dispatchNanos = dispatchNanos
        )
      } finally {
        Trace.endSection()
      }
    }
  }

  fun triggerHoldRelease(padId: String) {
    enqueueCommand {
      val voices = activeVoicesForPadLocked(padId).filter { !it.isFadingOut }
      if (voices.isEmpty()) return@enqueueCommand
      var changed = false
      for (slot in voices) {
        val fadeOutMs = effectiveFadeOutMs(slot.pad)
        if (fadeOutMs > 0L) {
          beginFadeOut(slot, fadeOutMs, effectiveFadeCurve(slot.pad))
          changed = true
        } else {
          releaseVoiceLocked(slot.voiceId, rearmIfEligible = slot == voices.last())
          changed = true
        }
      }
      if (changed) {
        syncStateAndService()
      }
    }
  }

  fun stopPad(padId: String) {
    enqueueCommand {
      releaseAllVoicesForPadLocked(padId, rearmLast = true)
      syncStateAndService()
    }
  }

  fun stopAll() {
    enqueueCommand {
      stopAllInternal()
      syncStateAndService()
    }
  }

  fun startSequencerPad(
    pad: SoundPad,
    crossfadePreviousPadId: String?,
    crossfadeDurationMs: Long
  ) {
    enqueueCommand {
      if (crossfadePreviousPadId != null && crossfadeDurationMs > 0L) {
        activeVoicesForPadLocked(crossfadePreviousPadId).forEach { prev ->
          if (!prev.isFadingOut) {
            beginFadeOut(prev, crossfadeDurationMs, currentSettings.crossfadeCurve)
          }
        }
      }
      if (activeVoicesForPadLocked(pad.id).isNotEmpty()) {
        releaseAllVoicesForPadLocked(pad.id, rearmLast = false)
      }
      startSlot(
        pad = pad,
        viaCrossfade = crossfadePreviousPadId != null && crossfadeDurationMs > 0L,
        isSequencerSlot = true
      )
    }
  }

  fun pausePadForSequencer(padId: String) {
    enqueueCommand {
      activeVoicesForPadLocked(padId).forEach { it.player.pause() }
    }
  }

  fun resumeOrStartPadForSequencer(pad: SoundPad) {
    enqueueCommand {
      val voices = activeVoicesForPadLocked(pad.id)
      if (voices.isNotEmpty()) {
        voices.forEach { it.player.play() }
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
    enqueueCommand {
      if (activeVoicesForPadLocked(modified.id).isNotEmpty()) {
        releaseAllVoicesForPadLocked(modified.id, rearmLast = false)
      }
      startSlot(pad = modified, viaCrossfade = false)
    }
  }

  fun fadeOutAll(durationMs: Long = 1500L) {
    enqueueCommand {
      if (activeSlots.isEmpty()) return@enqueueCommand
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

        enqueueCommand {
          if (_cuePreviewPadId.value == pad.id && cuePlayer != null) {
            stopCuePreviewInternal()
            return@enqueueCommand
          }
          stopCuePreviewInternal()

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
          player.volume = AudioMixMath.computeCueGain(pad.settings.volume, currentSettings.cueVolume)
          player.setMediaItem(mediaItem)
          player.prepare()
          player.play()
          cuePlayer = player
          activeCuePad = pad
          _cuePreviewPadId.value = pad.id
        }
      } catch (e: Exception) {
        onPlaybackError?.invoke(
          pad,
          PadErrorType.PLAYBACK_ERROR,
          "Gagal memutar Cue Preview \"${pad.displayName}\": ${e.localizedMessage ?: e.javaClass.simpleName}",
          e.stackTraceToString().take(2000)
        )
        enqueueCommand { stopCuePreviewInternal() }
      }
    }
  }

  fun stopCuePreview() {
    enqueueCommand { stopCuePreviewInternal() }
  }

  private fun stopCuePreviewInternal() {
    cuePlayer?.let {
      runCatching { it.stop() }
      runCatching { it.release() }
    }
    cuePlayer = null
    activeCuePad = null
    _cuePreviewPadId.value = null
  }

  @OptIn(UnstableApi::class)
  fun startEditorPreview(uriString: String, trimStartMs: Long, trimEndMs: Long) {
    enqueueCommand {
      try {
        stopEditorPreviewInternal()
        val mediaSourceFactory = DefaultMediaSourceFactory(ramCache.createDataSourceFactory())
        val player = ExoPlayer.Builder(context)
          .setLooper(audioThread.looper)
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
            enqueueCommand {
              val pos = previewPlayer?.currentPosition?.coerceAtLeast(0L) ?: return@enqueueCommand
              _previewPositionMs.value = start + pos
            }
            delay(32L)
          }
        }
      } catch (_: Exception) {
        stopEditorPreviewInternal()
      }
    }
  }

  fun stopEditorPreview() {
    enqueueCommand { stopEditorPreviewInternal() }
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

  @OptIn(UnstableApi::class)
  fun release() {
    if (!releasedFlag.compareAndSet(false, true)) return
    SoundPadPlaybackService.onStopAllRequested = null
    val shutdownLatch = CountDownLatch(1)
    audioHandler.post {
      audioHandler.removeCallbacks(tickerRunnable)
      tickerScheduled = false
      releaseLatencyTestPlayer()
      stopCuePreviewInternal()
      stopEditorPreviewInternal()
      stopAllInternal()
      releaseAllPooledSlotsLocked()
      mixerBus.release()
      releaseWakeLock()
      shutdownLatch.countDown()
    }
    runCatching { shutdownLatch.await(500L, TimeUnit.MILLISECONDS) }
    mainHandler.post {
      SoundPadPlaybackService.updateForegroundState(context, 0, null)
    }
    runCatching { audioThread.quitSafely() }
    scope.cancel()
  }

  private fun totalPoolCountLocked(): Int {
    return activeSlots.size + warmedPadSlots.size + idleSlotPool.size
  }

  @OptIn(UnstableApi::class)
  private fun createPooledPlayerSlotLocked(): PooledPlayerSlot {
    slotSequenceCounter++
    val tag = "slot_$slotSequenceCounter"
    val eqProcessor = FiveBandEqAudioProcessor()
    val fxProcessor = ReverbEchoAudioProcessor()
    val panProcessor = StereoPanAudioProcessor()
    val tapProcessor = MasterBusSlotTapAudioProcessor(
      slotId = tag,
      mixerBus = mixerBus,
      recorder = masterRecorder
    )
    val bufferMs = appliedBufferSizeMs.coerceIn(20, 500)
    val player = buildExoPlayerWithProcessors(
      eqProcessor = eqProcessor,
      fxProcessor = fxProcessor,
      panProcessor = panProcessor,
      tapProcessor = tapProcessor,
      includeAudioEffects = true,
      handleAudioFocus = false,
      bufferMs = bufferMs
    )
    cachedMainOutputDevice?.let { info ->
      runCatching { player.setPreferredAudioDevice(info) }
    }
    return PooledPlayerSlot(
      slotTag = tag,
      player = player,
      eqProcessor = eqProcessor,
      fxProcessor = fxProcessor,
      panProcessor = panProcessor,
      tapProcessor = tapProcessor,
      bufferSizeMs = bufferMs
    )
  }

  private fun preWarmIdlePoolSlotsLocked(targetIdleCount: Int) {
    while (idleSlotPool.size < targetIdleCount && totalPoolCountLocked() < MAX_PLAYER_POOL) {
      idleSlotPool.addLast(createPooledPlayerSlotLocked())
    }
  }

  private fun acquirePooledSlotLocked(): PooledPlayerSlot {
    while (idleSlotPool.isNotEmpty()) {
      val candidate = idleSlotPool.removeFirst()
      if (candidate.bufferSizeMs == appliedBufferSizeMs) {
        return candidate
      }
      discardPooledSlotLocked(candidate)
    }
    if (totalPoolCountLocked() >= MAX_PLAYER_POOL && warmedPadSlots.isNotEmpty()) {
      val lruKey = warmedPadSlots.keys.firstOrNull()
      if (lruKey != null) {
        val evicted = warmedPadSlots.remove(lruKey)
        if (evicted != null) {
          evicted.pooled.currentListener?.let { l -> runCatching { evicted.pooled.player.removeListener(l) } }
          evicted.pooled.currentListener = null
          if (evicted.pooled.bufferSizeMs == appliedBufferSizeMs) {
            runCatching {
              evicted.pooled.player.stop()
              evicted.pooled.player.clearMediaItems()
            }
            return evicted.pooled
          } else {
            discardPooledSlotLocked(evicted.pooled)
          }
        }
      }
    }
    return createPooledPlayerSlotLocked()
  }

  private fun discardPooledSlotLocked(pooled: PooledPlayerSlot) {
    pooled.tapProcessor?.setRouteViaMixerBus(false)
    pooled.currentListener?.let { l -> runCatching { pooled.player.removeListener(l) } }
    pooled.currentListener = null
    runCatching { pooled.player.stop() }
    runCatching { pooled.player.release() }
  }

  private fun rebuildIdleAndWarmedPoolForBufferChangeLocked() {
    val iterator = idleSlotPool.iterator()
    while (iterator.hasNext()) {
      val slot = iterator.next()
      if (slot.bufferSizeMs != appliedBufferSizeMs) {
        iterator.remove()
        discardPooledSlotLocked(slot)
      }
    }
    val staleWarmed = warmedPadSlots.entries
      .filter { it.value.pooled.bufferSizeMs != appliedBufferSizeMs }
      .map { it.key }
    staleWarmed.forEach { key ->
      warmedPadSlots.remove(key)?.let { discardPooledSlotLocked(it.pooled) }
    }
    preWarmIdlePoolSlotsLocked(INITIAL_IDLE_PREWARM)
  }

  private fun releaseAllPooledSlotsLocked() {
    while (idleSlotPool.isNotEmpty()) {
      discardPooledSlotLocked(idleSlotPool.removeFirst())
    }
    warmedPadSlots.values.forEach { discardPooledSlotLocked(it.pooled) }
    warmedPadSlots.clear()
  }

  private fun isPadWarmEligible(pad: SoundPad): Boolean {
    if (pad.settings.isReversed || pad.errorType != null || pad.uriString.isBlank()) return false
    return pad.isPreloadedInRam || pad.durationMs in 1L..WARM_SHORT_CLIP_MAX_MS
  }

  private fun preWarmPlaylistCandidatesLocked(pads: List<SoundPad>) {
    if (pads.isEmpty()) return
    val candidates = pads.asSequence()
      .filter { pad ->
        isPadWarmEligible(pad) &&
          activeSlots.values.none { it.pad.id == pad.id } &&
          !warmedPadSlots.containsKey(pad.id)
      }
      .take(MAX_WARMED_PADS)
      .toList()
    for (pad in candidates) {
      if (warmedPadSlots.size >= MAX_WARMED_PADS) break
      if (totalPoolCountLocked() >= MAX_PLAYER_POOL && idleSlotPool.isEmpty()) break
      prepareWarmPadSlotLocked(pad)
    }
  }

  @OptIn(UnstableApi::class)
  private fun prepareWarmPadSlotLocked(pad: SoundPad) {
    if (!isPadWarmEligible(pad)) return
    if (activeSlots.values.any { it.pad.id == pad.id }) return
    val existing = warmedPadSlots[pad.id]
    val clipStart = pad.settings.trimStartMs.coerceAtLeast(0L)
    val clipEnd = pad.settings.trimEndMs
    if (existing != null &&
      existing.uriKey == pad.uriString &&
      existing.clipStartMs == clipStart &&
      existing.clipEndMs == clipEnd &&
      existing.pooled.bufferSizeMs == appliedBufferSizeMs
    ) {
      return
    }
    if (existing != null) {
      warmedPadSlots.remove(pad.id)
      returnOrDiscardPooledSlotLocked(existing.pooled)
    }
    if (warmedPadSlots.size >= MAX_WARMED_PADS) return
    val pooled = acquirePooledSlotLocked()
    applySlotDsp(pooled.eqProcessor, pooled.fxProcessor, pooled.panProcessor, pad, currentSettings)
    val uri = Uri.parse(pad.uriString)
    val mediaItem = buildClippedMediaItem(uri, clipStart, clipEnd)
    runCatching {
      pooled.player.playWhenReady = false
      pooled.player.setMediaItem(mediaItem)
      pooled.player.prepare()
      warmedPadSlots[pad.id] = WarmedPadSlot(
        pad = pad,
        pooled = pooled,
        uriKey = pad.uriString,
        clipStartMs = clipStart,
        clipEndMs = clipEnd
      )
    }.onFailure {
      returnOrDiscardPooledSlotLocked(pooled)
    }
  }

  private fun returnOrDiscardPooledSlotLocked(pooled: PooledPlayerSlot) {
    pooled.tapProcessor?.setRouteViaMixerBus(false)
    mixerBus.removeSlot(pooled.slotTag)
    masterRecorder?.removeSlot(pooled.slotTag)
    pooled.currentListener?.let { l -> runCatching { pooled.player.removeListener(l) } }
    pooled.currentListener = null
    if (pooled.bufferSizeMs == appliedBufferSizeMs && totalPoolCountLocked() < MAX_PLAYER_POOL) {
      runCatching {
        pooled.player.playWhenReady = false
        pooled.player.stop()
        pooled.player.clearMediaItems()
        idleSlotPool.addLast(pooled)
      }.onFailure {
        discardPooledSlotLocked(pooled)
      }
    } else {
      discardPooledSlotLocked(pooled)
    }
  }

  private fun preparePoolForNewPad(incomingPad: SoundPad): Boolean {
    val crossfadeEligibleIncoming = currentSettings.globalCrossfadeEnabled && incomingPad.settings.crossfadeEnabled
    var crossfadedAny = false

    if (currentSettings.playMode == PlayMode.SINGLE || currentSettings.playMode == PlayMode.CONTINUOUS) {
      val snapshots = activeSlots.values.toList()
      for (slot in snapshots) {
        if (slot.pad.id == incomingPad.id) {
          // Same-pad RESTART vs LAYER was already evaluated in triggerPad / triggerHoldStart
          continue
        }
        if (crossfadeEligibleIncoming && slot.pad.settings.crossfadeEnabled && !slot.isFadingOut) {
          beginFadeOut(slot, currentSettings.crossfadeDurationMs, currentSettings.crossfadeCurve)
          crossfadedAny = true
        } else if (!slot.isFadingOut) {
          val fadeOutMs = effectiveFadeOutMs(slot.pad)
          if (fadeOutMs > 0L) {
            beginFadeOut(slot, fadeOutMs, effectiveFadeCurve(slot.pad))
          } else {
            releaseVoiceLocked(slot.voiceId, rearmIfEligible = true)
          }
        }
      }
    } else {
      // MULTI Mode: Choke Group handling across different pads in the same group
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
            releaseVoiceLocked(slot.voiceId, rearmIfEligible = true)
          }
        }
      }
    }

    // E.2 / E.3: Enforce per-pad and global maxVoices limits (stealing fading-out first, then oldest)
    val perPadCap = RetriggerVoicePlanner.resolveEffectivePadVoiceCap(incomingPad.settings, currentSettings)
    val globalCap = RetriggerVoicePlanner.resolveEffectiveMaxVoices(currentSettings)
    val voiceSnapshots = activeSlots.values.map { slot ->
      ActiveVoiceSnapshot(
        voiceId = slot.voiceId,
        padId = slot.pad.id,
        chokeGroup = slot.pad.settings.chokeGroup,
        isFadingOut = slot.isFadingOut,
        crossfadeEnabled = slot.pad.settings.crossfadeEnabled
      )
    }
    val evictions = RetriggerVoicePlanner.computeEvictionsForNewVoice(
      voices = voiceSnapshots,
      incomingPadId = incomingPad.id,
      perPadCap = perPadCap,
      globalCap = globalCap
    )
    for (evictVoiceId in evictions) {
      releaseVoiceLocked(evictVoiceId, rearmIfEligible = false)
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
    tapProcessor: MasterBusSlotTapAudioProcessor? = null,
    includeAudioEffects: Boolean = true,
    handleAudioFocus: Boolean = false,
    bufferMs: Int = appliedBufferSizeMs
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
          .setEnableAudioOutputPlaybackParameters(enableAudioTrackPlaybackParams)
          .setAudioProcessors(processors)
          .build()
      }
    }
    return ExoPlayer.Builder(context, renderersFactory)
      .setLooper(audioThread.looper)
      .setMediaSourceFactory(mediaSourceFactory)
      .setLoadControl(createLowLatencyLoadControl(bufferMs))
      .setAudioAttributes(audioAttributes, handleAudioFocus)
      .build()
  }

  @OptIn(UnstableApi::class)
  private fun startSlot(
    pad: SoundPad,
    viaCrossfade: Boolean,
    isSequencerSlot: Boolean = false,
    tapDownNanos: Long? = null,
    dispatchNanos: Long? = null
  ) {
    if (RetriggerVoicePlanner.resolveEffectiveRetriggerMode(pad.settings, currentSettings) == RetriggerMode.RESTART) {
      cancelPendingReverseJobsLocked(pad.id)
    }
    if (!pad.settings.isReversed) {
      startSlotWithUri(
        pad = pad,
        viaCrossfade = viaCrossfade,
        playbackUri = Uri.parse(pad.uriString),
        clipStartMs = pad.settings.trimStartMs.coerceAtLeast(0L),
        clipEndMs = pad.settings.trimEndMs,
        isSequencerSlot = isSequencerSlot,
        tapDownNanos = tapDownNanos,
        dispatchNanos = dispatchNanos
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
        isSequencerSlot = isSequencerSlot,
        tapDownNanos = tapDownNanos,
        dispatchNanos = dispatchNanos
      )
      return
    }

    lateinit var reverseJob: Job
    reverseJob = scope.launch(Dispatchers.IO) {
      val result = ReverseAudioCacheManager.getOrBuildReversedWav(
        context = context,
        uriString = pad.uriString,
        trimStartMs = pad.settings.trimStartMs,
        trimEndMs = pad.settings.trimEndMs
      )
      enqueueCommand {
        val list = pendingReverseJobs[pad.id]
        list?.remove(reverseJob)
        if (list != null && list.isEmpty()) {
          pendingReverseJobs.remove(pad.id)
        }
        result.onSuccess { reversedFile ->
          preparePoolForNewPad(pad)
          startSlotWithUri(
            pad = pad,
            viaCrossfade = viaCrossfade,
            playbackUri = Uri.fromFile(reversedFile),
            clipStartMs = 0L,
            clipEndMs = 0L,
            isSequencerSlot = isSequencerSlot,
            tapDownNanos = tapDownNanos,
            dispatchNanos = dispatchNanos
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
    pendingReverseJobs.getOrPut(pad.id) { mutableListOf() }.add(reverseJob)
  }

  @OptIn(UnstableApi::class)
  private fun startSlotWithUri(
    pad: SoundPad,
    viaCrossfade: Boolean,
    playbackUri: Uri,
    clipStartMs: Long,
    clipEndMs: Long,
    isSequencerSlot: Boolean = false,
    tapDownNanos: Long? = null,
    dispatchNanos: Long? = null
  ) {
    Trace.beginSection("player_start")
    val voiceId = nextVoiceIdLocked(pad.id)
    var acquiredPooled: PooledPlayerSlot? = null
    try {
      val mainDevice = cachedMainOutputDevice ?: if (preferredOutputDeviceId != null) {
        AudioOutputRouter.findOutputDeviceById(context, preferredOutputDeviceId).also { cachedMainOutputDevice = it }
      } else null
      mixerBus.setPreferredOutputDevice(mainDevice)

      if (currentSettings.masterLimiterEnabled && !mixerBus.isActivelyHandlingOutput) {
        mixerBus.ensureStarted(currentSettings.audioBufferSizeMs)
      }
      val busActive = currentSettings.masterLimiterEnabled && mixerBus.isActivelyHandlingOutput

      val uriKey = playbackUri.toString()
      val warmedMatch = warmedPadSlots.remove(pad.id)
      val reusedWarmed = warmedMatch != null &&
        warmedMatch.uriKey == uriKey &&
        warmedMatch.clipStartMs == clipStartMs &&
        warmedMatch.clipEndMs == clipEndMs &&
        warmedMatch.pooled.bufferSizeMs == appliedBufferSizeMs

      val pooled = if (reusedWarmed) {
        warmedMatch!!.pooled
      } else {
        warmedMatch?.let { returnOrDiscardPooledSlotLocked(it.pooled) }
        acquirePooledSlotLocked()
      }
      acquiredPooled = pooled

      val player = pooled.player
      val eqProcessor = pooled.eqProcessor
      val fxProcessor = pooled.fxProcessor
      val panProcessor = pooled.panProcessor
      val tapProcessor = pooled.tapProcessor

      pooled.currentListener?.let { oldListener ->
        runCatching { player.removeListener(oldListener) }
      }
      tapProcessor?.setRouteViaMixerBus(busActive)
      applySlotDsp(eqProcessor, fxProcessor, panProcessor, pad, currentSettings)

      mainDevice?.let { info ->
        runCatching { player.setPreferredAudioDevice(info) }
      }

      val fadeInMs = if (viaCrossfade) {
        maxOf(effectiveFadeInMs(pad), currentSettings.crossfadeDurationMs)
      } else {
        effectiveFadeInMs(pad)
      }
      val fadeInCurve = if (viaCrossfade) currentSettings.crossfadeCurve else effectiveFadeCurve(pad)
      // D.5: If fadeInMs == 0, apply full unity gain immediately with zero envelope attenuation
      val initialGain = if (fadeInMs > 0L) 0f else 1f

      applyPadParameters(player, pad, initialGain, tapProcessor)

      val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
          if (playbackState == Player.STATE_ENDED) {
            onVoiceEnded(voiceId)
          }
        }

        override fun onPlayerError(error: PlaybackException) {
          val (type, msg) = mapPlaybackException(pad, error)
          onPlaybackError?.invoke(pad, type, msg, error.stackTraceToString().take(2000))
          releaseVoiceLocked(voiceId, rearmIfEligible = false)
          syncStateAndService()
        }
      }
      pooled.currentListener = listener
      player.addListener(listener)

      if (reusedWarmed && (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)) {
        if (player.currentPosition != 0L) {
          player.seekTo(0L)
        }
        player.play()
      } else {
        val mediaItem = buildClippedMediaItem(playbackUri, clipStartMs, clipEndMs)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
      }

      val soundStartedNanos = SystemClock.elapsedRealtimeNanos()

      activeSlots[voiceId] = PlayerSlot(
        voiceId = voiceId,
        pad = pad,
        pooled = pooled,
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

      if (tapDownNanos != null) {
        _tapLatencyMetrics.value = latencyTracker.record(
          tapDownNanos = tapDownNanos,
          dispatchNanos = dispatchNanos ?: soundStartedNanos,
          soundStartedNanos = soundStartedNanos,
          warmSlotsReady = warmedPadSlots.size,
          idlePoolSize = idleSlotPool.size,
          framesPerBurst = mixerBus.framesPerBurst,
          nativeSampleRateHz = mixerBus.nativeSampleRateHz
        )
      } else {
        publishLatencySnapshotLocked()
      }

      // B.3: Publish playback states immediately and defer Service/WakeLock binder IPC off the critical trigger path
      publishPlaybackStates()
      ensureTicker()
      schedulePostPlaySystemSyncLocked()
    } catch (e: Exception) {
      val type = if (e is SecurityException) PadErrorType.MISSING_PERMISSION else PadErrorType.PLAYBACK_ERROR
      val msg = "Gagal memutar \"${pad.displayName}\": ${e.localizedMessage ?: e.javaClass.simpleName}"
      onPlaybackError?.invoke(pad, type, msg, e.stackTraceToString().take(2000))
      if (activeSlots.containsKey(voiceId)) {
        releaseVoiceLocked(voiceId, rearmIfEligible = false)
      } else {
        acquiredPooled?.let { returnOrDiscardPooledSlotLocked(it) }
      }
      syncStateAndService()
    } finally {
      Trace.endSection()
    }
  }

  private fun schedulePostPlaySystemSyncLocked() {
    val activeCount = activeSlots.size
    val firstLabel = activeSlots.values.firstOrNull()?.pad?.displayName
    mainHandler.post {
      if (activeCount > 0) {
        acquireWakeLock()
        SoundPadPlaybackService.updateForegroundState(context, activeCount, firstLabel)
      } else {
        releaseWakeLock()
        SoundPadPlaybackService.updateForegroundState(context, 0, null)
      }
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
      releaseVoiceLocked(slot.voiceId, rearmIfEligible = true)
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
    val busActive = currentSettings.masterLimiterEnabled && mixerBus.isActivelyHandlingOutput
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
        duckingMultiplier = duckMult,
        useRealtimeBusLimiter = busActive && slot.tapProcessor != null
      )
      slot.tapProcessor?.setRouteViaMixerBus(busActive)
      slot.tapProcessor?.setGain(gain)
      slot.player.volume = if (busActive && slot.tapProcessor != null) 1.0f else gain
    }
  }

  @OptIn(UnstableApi::class)
  private fun applyPadParameters(
    player: ExoPlayer,
    pad: SoundPad,
    envelopeGain: Float,
    tapProcessor: MasterBusSlotTapAudioProcessor?
  ) {
    val activeCount = activeSlots.size.coerceAtLeast(1)
    val hasActiveDuckSource = activeSlots.values.any { it.pad.settings.isDuckingSource && !it.isFadingOut }
    val duckMult = if (currentSettings.duckingEnabled && (isVoiceDuckingActive || (hasActiveDuckSource && !pad.settings.isDuckingSource))) {
      currentSettings.duckingAttenuation
    } else {
      1.0f
    }
    val busActive = currentSettings.masterLimiterEnabled && mixerBus.isActivelyHandlingOutput && tapProcessor != null
    val gain = AudioMixMath.computeSlotGain(
      masterVolume = currentSettings.masterVolume,
      padVolume = pad.settings.volume,
      envelopeGain = envelopeGain,
      normalizedGain = pad.settings.normalizedGain,
      autoNormalizeEnabled = currentSettings.autoNormalizeEnabled,
      limiterEnabled = currentSettings.masterLimiterEnabled,
      activeSlotCount = activeCount,
      duckingMultiplier = duckMult,
      useRealtimeBusLimiter = busActive
    )
    tapProcessor?.setRouteViaMixerBus(busActive)
    tapProcessor?.setGain(gain)
    player.volume = if (busActive) 1.0f else gain
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

  private fun onVoiceEnded(voiceId: String) {
    val slot = activeSlots[voiceId]
    val padId = slot?.pad?.id
    val alreadyTriggeredNext = slot?.continuousTriggeredNext == true
    val isSeqSlot = slot?.isSequencerSlot == true
    val otherVoicesActiveForPad = padId != null && activeSlots.values.any {
      it.pad.id == padId && it.voiceId != voiceId && !it.isFadingOut
    }
    val shouldAdvance = currentSettings.playMode == PlayMode.CONTINUOUS &&
      !alreadyTriggeredNext &&
      !isSeqSlot &&
      !otherVoicesActiveForPad
    releaseVoiceLocked(voiceId, rearmIfEligible = true)

    if (shouldAdvance && padId != null && currentPlaylist.isNotEmpty()) {
      val currentIndex = currentPlaylist.indexOfFirst { it.id == padId }
      if (currentIndex >= 0 && currentIndex + 1 < currentPlaylist.size) {
        val nextPad = currentPlaylist[currentIndex + 1]
        startSlot(nextPad, viaCrossfade = false)
        return
      }
    }
    syncStateAndService()
  }

  @OptIn(UnstableApi::class)
  private fun stopAllInternal() {
    pendingReverseJobs.values.forEach { jobs -> jobs.forEach { it.cancel() } }
    pendingReverseJobs.clear()
    val voiceIds = activeSlots.keys.toList()
    for (voiceId in voiceIds) {
      releaseVoiceLocked(voiceId, rearmIfEligible = true)
    }
    mixerBus.clearAllSlots()
  }

  private fun releaseAllVoicesForPadLocked(padId: String, rearmLast: Boolean = true) {
    cancelPendingReverseJobsLocked(padId)
    val matchingVoiceIds = activeSlots.values
      .filter { it.pad.id == padId }
      .map { it.voiceId }
    matchingVoiceIds.forEachIndexed { idx, voiceId ->
      val isLast = idx == matchingVoiceIds.lastIndex
      releaseVoiceLocked(voiceId, rearmIfEligible = rearmLast && isLast)
    }
  }

  @OptIn(UnstableApi::class)
  private fun releaseVoiceLocked(voiceId: String, rearmIfEligible: Boolean = true) {
    val slot = activeSlots.remove(voiceId) ?: return
    val padId = slot.pad.id
    val pooled = slot.pooled
    pooled.tapProcessor?.setRouteViaMixerBus(false)
    mixerBus.removeSlot(pooled.slotTag)
    masterRecorder?.removeSlot(pooled.slotTag)
    pooled.currentListener?.let { l -> runCatching { slot.player.removeListener(l) } }
    pooled.currentListener = null

    val canRearm = rearmIfEligible &&
      !slot.isSequencerSlot &&
      pooled.bufferSizeMs == appliedBufferSizeMs &&
      isPadWarmEligible(slot.pad) &&
      warmedPadSlots.size < MAX_WARMED_PADS &&
      !warmedPadSlots.containsKey(padId)

    if (canRearm) {
      val rearmed = runCatching {
        slot.player.playWhenReady = false
        slot.player.pause()
        slot.player.seekTo(0L)
        warmedPadSlots[padId] = WarmedPadSlot(
          pad = slot.pad,
          pooled = pooled,
          uriKey = slot.pad.uriString,
          clipStartMs = slot.pad.settings.trimStartMs.coerceAtLeast(0L),
          clipEndMs = slot.pad.settings.trimEndMs
        )
        true
      }.getOrDefault(false)
      if (rearmed) {
        publishLatencySnapshotLocked()
        return
      }
    }

    returnOrDiscardPooledSlotLocked(pooled)
    publishLatencySnapshotLocked()
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
    if (tickerScheduled || releasedFlag.get()) return
    val interval = if (hasActiveEnvelopeFade()) ENVELOPE_TICK_MS else UI_PROGRESS_PUBLISH_MS
    tickerScheduled = true
    audioHandler.postDelayed(tickerRunnable, interval)
  }

  private fun processEnvelopesAndPublish() {
    val now = SystemClock.elapsedRealtime()
    val toRelease = mutableListOf<String>()
    var nextContinuousPad: SoundPad? = null
    var nextContinuousViaCrossfade = false

    for ((voiceId, slot) in activeSlots) {
      val padId = slot.pad.id
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

      // Proactive Continuous Crossfade near end of track (when last remaining voice for this pad finishes)
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
          if (candidateNext.settings.crossfadeEnabled && activeSlots.values.none { it.pad.id == candidateNext.id }) {
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
          toRelease.add(voiceId)
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
      toRelease.forEach { releaseVoiceLocked(it, rearmIfEligible = true) }
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
    if (activeSlots.isEmpty()) {
      audioHandler.removeCallbacks(tickerRunnable)
      tickerScheduled = false
      _hasPendingBufferRebuild.value = false
    }
    schedulePostPlaySystemSyncLocked()
  }

  private fun publishPlaybackStates() {
    lastUiPublishElapsedMs = SystemClock.elapsedRealtime()
    if (activeSlots.isEmpty()) {
      if (_playbackStates.value.isNotEmpty()) {
        _playbackStates.value = emptyMap()
      }
      return
    }
    val grouped = LinkedHashMap<String, MutableList<PlayerSlot>>()
    for (slot in activeSlots.values) {
      grouped.getOrPut(slot.pad.id) { mutableListOf() }.add(slot)
    }
    val nextMap = HashMap<String, PadPlaybackState>(grouped.size)
    for ((padId, voices) in grouped) {
      val displayVoice = voices.lastOrNull { !it.isFadingOut } ?: voices.last()
      val duration = displayVoice.player.duration.takeIf { it > 0L } ?: displayVoice.pad.durationMs
      val position = displayVoice.player.currentPosition.coerceAtLeast(0L)
      val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
      nextMap[padId] = PadPlaybackState(
        padId = padId,
        isPlaying = true,
        isFadingOut = voices.all { it.isFadingOut },
        positionMs = position,
        durationMs = duration,
        progress = progress,
        activeVoiceCount = voices.size
      )
    }
    _playbackStates.value = nextMap
  }

  private fun <T> runOnAudioThreadForTest(timeoutMs: Long = 1500L, block: () -> T): T {
    if (Looper.myLooper() == audioThread.looper || releasedFlag.get()) {
      return block()
    }
    var result: T? = null
    val latch = CountDownLatch(1)
    enqueueCommand {
      result = block()
      latch.countDown()
    }
    latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  internal fun getTotalActiveVoiceCountForTest(): Int =
    runOnAudioThreadForTest { activeSlots.size }

  internal fun getActiveVoiceCountForPadForTest(padId: String): Int =
    runOnAudioThreadForTest { activeSlots.values.count { it.pad.id == padId } }

  internal fun getTotalAllocatedPoolCountForTest(): Int =
    runOnAudioThreadForTest { totalPoolCountLocked() }

  internal fun getActiveVoiceIdsForPadForTest(padId: String): List<String> =
    runOnAudioThreadForTest { activeSlots.values.filter { it.pad.id == padId }.map { it.voiceId } }

  internal fun getActiveVoiceSpeedsForPadForTest(padId: String): List<Float> =
    runOnAudioThreadForTest { activeSlots.values.filter { it.pad.id == padId }.map { it.pad.settings.speed } }

  internal fun getActiveVoicePitchesForPadForTest(padId: String): List<Float> =
    runOnAudioThreadForTest { activeSlots.values.filter { it.pad.id == padId }.map { it.pad.settings.pitch } }

  internal fun getActiveVoiceLoopsForPadForTest(padId: String): List<Int> =
    runOnAudioThreadForTest { activeSlots.values.filter { it.pad.id == padId }.map { it.player.repeatMode } }

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
