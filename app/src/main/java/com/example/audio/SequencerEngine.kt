package com.example.audio

import com.example.data.local.db.SequencerPlaylistEntity
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PlayMode
import com.example.domain.model.SequencerPlayMode
import com.example.domain.model.SequencerPlaybackState
import com.example.domain.model.SequencerPlaylist
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
import org.json.JSONArray
import kotlin.random.Random

object SequencerQueueMath {
  fun resolveNextIndex(
    currentIndex: Int,
    queueSize: Int,
    mode: SequencerPlayMode,
    random: Random = Random.Default
  ): Int? {
    if (queueSize <= 0) return null
    return when (mode) {
      SequencerPlayMode.REPEAT_ONE -> currentIndex.coerceIn(0, queueSize - 1)
      SequencerPlayMode.SEQUENTIAL -> if (currentIndex + 1 < queueSize) currentIndex + 1 else null
      SequencerPlayMode.REPEAT_ALL -> (currentIndex + 1) % queueSize
      SequencerPlayMode.SHUFFLE -> {
        if (queueSize == 1) 0
        else {
          var next = random.nextInt(queueSize)
          if (next == currentIndex) next = (currentIndex + 1) % queueSize
          next
        }
      }
    }
  }

  fun resolvePrevIndex(currentIndex: Int, queueSize: Int, mode: SequencerPlayMode): Int {
    if (queueSize <= 0) return 0
    return if (currentIndex - 1 >= 0) {
      currentIndex - 1
    } else if (mode == SequencerPlayMode.REPEAT_ALL || mode == SequencerPlayMode.SHUFFLE) {
      queueSize - 1
    } else {
      0
    }
  }
}

class SequencerEngine(
  private val audioEngine: SoundPadAudioEngine
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _state = MutableStateFlow(SequencerPlaybackState())
  val state: StateFlow<SequencerPlaybackState> = _state.asStateFlow()

  private var availablePadsById: Map<String, SoundPad> = emptyMap()
  private var activePlaylist: SequencerPlaylist? = null
  private var activeQueue: List<SoundPad> = emptyList()
  private var currentIndex: Int = 0
  private var isPaused: Boolean = false
  private var monitorJob: Job? = null
  private var gapJob: Job? = null
  private var crossfadedForCurrentItem: Boolean = false

  fun updateAvailablePads(pads: List<SoundPad>) {
    availablePadsById = pads.associateBy { it.id }
  }

  fun syncPlaylists(playlists: List<SequencerPlaylist>) {
    val activeId = activePlaylist?.id ?: return
    val updated = playlists.firstOrNull { it.id == activeId } ?: return
    activePlaylist = updated
    if (_state.value.isPlaying) {
      activeQueue = updated.padIds.mapNotNull { availablePadsById[it] }
      _state.value = _state.value.copy(
        activePlaylistName = updated.name,
        queueSize = activeQueue.size
      )
    }
  }

  fun startPlaylist(playlist: SequencerPlaylist, startAtIndex: Int = 0) {
    val queue = playlist.padIds.mapNotNull { availablePadsById[it] }
    if (queue.isEmpty()) return

    gapJob?.cancel()
    monitorJob?.cancel()
    activePlaylist = playlist
    activeQueue = queue
    currentIndex = startAtIndex.coerceIn(0, queue.lastIndex)
    isPaused = false
    crossfadedForCurrentItem = false

    startCurrentItem(viaCrossfade = false)
    startMonitor()
  }

  fun togglePlayPause(playlist: SequencerPlaylist? = activePlaylist) {
    val curState = _state.value
    if (curState.isPlaying && (playlist == null || playlist.id == curState.activePlaylistId)) {
      if (isPaused) {
        isPaused = false
        val pad = activeQueue.getOrNull(currentIndex)
        if (pad != null) {
          audioEngine.resumeOrStartPadForSequencer(pad)
        }
        publishState()
      } else {
        isPaused = true
        val pad = activeQueue.getOrNull(currentIndex)
        if (pad != null) {
          audioEngine.pausePadForSequencer(pad.id)
        }
        publishState()
      }
      return
    }
    if (playlist != null) {
      startPlaylist(playlist)
    }
  }

  fun nextItem() {
    val playlist = activePlaylist ?: return
    if (activeQueue.isEmpty()) return
    gapJob?.cancel()
    isPaused = false
    val nextIdx = SequencerQueueMath.resolveNextIndex(
      currentIndex = currentIndex,
      queueSize = activeQueue.size,
      mode = if (playlist.mode == SequencerPlayMode.REPEAT_ONE) SequencerPlayMode.REPEAT_ALL else playlist.mode
    ) ?: run {
      stop()
      return
    }
    val oldPadId = activeQueue.getOrNull(currentIndex)?.id
    currentIndex = nextIdx
    crossfadedForCurrentItem = false
    if (oldPadId != null && !playlist.crossfadeEnabled) {
      audioEngine.stopPad(oldPadId)
    }
    startCurrentItem(viaCrossfade = playlist.crossfadeEnabled)
  }

  fun prevItem() {
    val playlist = activePlaylist ?: return
    if (activeQueue.isEmpty()) return
    gapJob?.cancel()
    isPaused = false
    val oldPadId = activeQueue.getOrNull(currentIndex)?.id
    currentIndex = SequencerQueueMath.resolvePrevIndex(currentIndex, activeQueue.size, playlist.mode)
    crossfadedForCurrentItem = false
    if (oldPadId != null && !playlist.crossfadeEnabled) {
      audioEngine.stopPad(oldPadId)
    }
    startCurrentItem(viaCrossfade = playlist.crossfadeEnabled)
  }

  fun stop() {
    gapJob?.cancel()
    gapJob = null
    monitorJob?.cancel()
    monitorJob = null
    val padId = activeQueue.getOrNull(currentIndex)?.id
    if (padId != null) {
      audioEngine.stopPad(padId)
    }
    activePlaylist = null
    activeQueue = emptyList()
    currentIndex = 0
    isPaused = false
    crossfadedForCurrentItem = false
    _state.value = SequencerPlaybackState()
  }

  private fun startCurrentItem(viaCrossfade: Boolean) {
    val playlist = activePlaylist ?: return
    val pad = activeQueue.getOrNull(currentIndex) ?: run {
      stop()
      return
    }
    val preparedPad = pad.copy(
      settings = pad.settings.copy(
        isLooping = false,
        crossfadeEnabled = playlist.crossfadeEnabled,
        fadeInMs = if (viaCrossfade && playlist.crossfadeEnabled) {
          maxOf(pad.settings.fadeInMs, playlist.crossfadeDurationMs)
        } else {
          pad.settings.fadeInMs
        }
      )
    )
    // Play in multi mode cleanly without auto-advancing SoundPadAudioEngine's grid CONTINUOUS list
    audioEngine.startSequencerPad(
      pad = preparedPad,
      crossfadePreviousPadId = if (viaCrossfade && playlist.crossfadeEnabled) _state.value.currentPadId else null,
      crossfadeDurationMs = if (playlist.crossfadeEnabled) playlist.crossfadeDurationMs else 0L
    )
    crossfadedForCurrentItem = false
    publishState(isInGap = false, remainingGap = 0f)
  }

  private fun startMonitor() {
    monitorJob?.cancel()
    monitorJob = scope.launch {
      var sawCurrentPlaying = false
      while (isActive && activePlaylist != null) {
        val playlist = activePlaylist ?: break
        val pad = activeQueue.getOrNull(currentIndex) ?: break
        val playbackMap: Map<String, PadPlaybackState> = audioEngine.playbackStates.value
        val activePlayback = playbackMap[pad.id]

        if (activePlayback != null && activePlayback.isPlaying) {
          sawCurrentPlaying = true
          // Trigger proactive crossfade if gap is 0 and crossfade enabled
          if (!isPaused &&
            !crossfadedForCurrentItem &&
            playlist.crossfadeEnabled &&
            playlist.gapSeconds <= 0.05f &&
            activePlayback.durationMs > playlist.crossfadeDurationMs &&
            (activePlayback.durationMs - activePlayback.positionMs) in 1..playlist.crossfadeDurationMs
          ) {
            val nextIdx = SequencerQueueMath.resolveNextIndex(currentIndex, activeQueue.size, playlist.mode)
            if (nextIdx != null) {
              crossfadedForCurrentItem = true
              sawCurrentPlaying = false
              currentIndex = nextIdx
              startCurrentItem(viaCrossfade = true)
            }
          }
        } else if (sawCurrentPlaying && !isPaused && gapJob?.isActive != true) {
          sawCurrentPlaying = false
          val nextIdx = SequencerQueueMath.resolveNextIndex(currentIndex, activeQueue.size, playlist.mode)
          if (nextIdx == null) {
            stop()
            break
          }
          val gapMs = (playlist.gapSeconds * 1000f).toLong().coerceAtLeast(0L)
          if (gapMs > 0L) {
            gapJob = scope.launch {
              var remaining = gapMs
              while (remaining > 0L && isActive) {
                publishState(isInGap = true, remainingGap = remaining / 1000f)
                delay(100L)
                if (!isPaused) remaining -= 100L
              }
              currentIndex = nextIdx
              startCurrentItem(viaCrossfade = playlist.crossfadeEnabled)
            }
          } else {
            currentIndex = nextIdx
            startCurrentItem(viaCrossfade = playlist.crossfadeEnabled)
          }
        }
        delay(80L)
      }
    }
  }

  private fun publishState(isInGap: Boolean = false, remainingGap: Float = 0f) {
    val playlist = activePlaylist
    val pad = activeQueue.getOrNull(currentIndex)
    _state.value = if (playlist == null) {
      SequencerPlaybackState()
    } else {
      SequencerPlaybackState(
        activePlaylistId = playlist.id,
        activePlaylistName = playlist.name,
        currentIndex = currentIndex,
        currentPadId = pad?.id,
        currentPadName = pad?.displayName,
        queueSize = activeQueue.size,
        isPlaying = true,
        isPaused = isPaused,
        isInGap = isInGap,
        remainingGapSec = remainingGap
      )
    }
  }

  fun release() {
    stop()
    scope.cancel()
  }
}

fun SequencerPlaylistEntity.toDomain(): SequencerPlaylist {
  val ids = runCatching {
    val arr = JSONArray(padIdsJson)
    List(arr.length()) { idx -> arr.optString(idx) }.filter { it.isNotBlank() }
  }.getOrDefault(emptyList())
  return SequencerPlaylist(
    id = id,
    name = name,
    padIds = ids,
    mode = runCatching { SequencerPlayMode.valueOf(mode) }.getOrDefault(SequencerPlayMode.SEQUENTIAL),
    gapSeconds = gapSeconds,
    crossfadeEnabled = crossfadeEnabled,
    crossfadeDurationMs = crossfadeDurationMs,
    triggerPadId = triggerPadId,
    triggerMidiNote = triggerMidiNote,
    updatedAtMs = updatedAtMs
  )
}

fun SequencerPlaylist.toEntity(): SequencerPlaylistEntity {
  val arr = JSONArray().apply {
    padIds.forEach { put(it) }
  }
  return SequencerPlaylistEntity(
    id = id,
    name = name,
    padIdsJson = arr.toString(),
    mode = mode.name,
    gapSeconds = gapSeconds,
    crossfadeEnabled = crossfadeEnabled,
    crossfadeDurationMs = crossfadeDurationMs,
    triggerPadId = triggerPadId,
    triggerMidiNote = triggerMidiNote,
    updatedAtMs = updatedAtMs
  )
}
