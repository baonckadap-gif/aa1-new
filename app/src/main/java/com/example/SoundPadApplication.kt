package com.example

import android.app.Application
import com.example.audio.InMemoryAudioCache
import com.example.audio.MasterOutputRecorder
import com.example.audio.SequencerEngine
import com.example.audio.SoundPadAudioEngine
import com.example.audio.SoundPadPlaybackService
import com.example.audio.VoiceTrackRecorder
import com.example.audio.toDomain
import com.example.data.backup.PersistentConfigSyncManager
import com.example.data.local.db.PadDatabaseRepository
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.saf.SafAudioScanner
import com.example.domain.model.PadErrorType
import com.example.domain.model.ScheduleTargetType
import com.example.error.CrashLogManager
import com.example.midi.MidiControllerManager
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.toDomain
import com.example.ui.toDomainPad
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure dispatcher helper so target-specific scheduled auto-stop behavior can be unit-tested
 * cleanly without triggering stopAll() on unrelated playing pads.
 */
object ScheduledAutoStopDispatcher {
  fun dispatchStopTarget(
    targetType: ScheduleTargetType,
    targetId: String,
    activePlaylistId: String?,
    padsProvider: () -> List<Pair<String, Int>>, // padId to bankIndex
    onStopPad: (String) -> Unit,
    onStopSequencer: () -> Unit
  ) {
    when (targetType) {
      ScheduleTargetType.PAD -> {
        if (targetId.isNotBlank()) {
          onStopPad(targetId)
        }
      }
      ScheduleTargetType.BANK -> {
        val bankIdx = targetId.toIntOrNull() ?: return
        padsProvider()
          .filter { (_, padBank) -> padBank == bankIdx }
          .forEach { (padId, _) -> onStopPad(padId) }
      }
      ScheduleTargetType.SEQUENCER -> {
        if (targetId.isBlank() || activePlaylistId == targetId) {
          onStopSequencer()
        }
      }
    }
  }
}

class SoundPadApplication : Application() {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val recentTriggeredTasks = ConcurrentHashMap<String, Long>()

  override fun onCreate() {
    super.onCreate()
    CrashLogManager.install(this)

    // Ensure scheduled tasks fire reliably even if the UI isn't open
    SoundPadPlaybackService.onScheduledTaskRequested = { taskId ->
      executeScheduledTaskInBackground(taskId)
    }
    SoundPadPlaybackService.onStopScheduledTaskRequested = { taskId, targetTypeRaw, targetId ->
      stopScheduledTaskInBackground(taskId, targetTypeRaw, targetId)
    }

    // Re-register active alarms on process start as defensive guarantee
    appScope.launch {
      runCatching { PadSchedulerManager.rescheduleAllActive(this@SoundPadApplication) }
    }
  }

  val database: SoundPadDatabase by lazy { SoundPadDatabase.getInstance(this) }
  val padRepository: PadDatabaseRepository by lazy { PadDatabaseRepository(this, database.soundPadDao()) }
  val settingsRepository: GlobalSettingsRepository by lazy { GlobalSettingsRepository(this) }
  val safScanner: SafAudioScanner by lazy { SafAudioScanner(this) }
  val ramCache: InMemoryAudioCache by lazy { InMemoryAudioCache(this) }
  val masterRecorder: MasterOutputRecorder by lazy { MasterOutputRecorder(this) }
  val audioEngine: SoundPadAudioEngine by lazy {
    SoundPadAudioEngine(this, ramCache).also { engine ->
      engine.attachMasterRecorder(masterRecorder)
    }
  }
  val sequencerEngine: SequencerEngine by lazy { SequencerEngine(audioEngine) }
  val voiceRecorder: VoiceTrackRecorder by lazy { VoiceTrackRecorder(this) }
  val midiController: MidiControllerManager by lazy { MidiControllerManager(this) }
  val persistentConfigManager: PersistentConfigSyncManager by lazy {
    PersistentConfigSyncManager(
      context = this,
      dao = database.soundPadDao(),
      settingsRepo = settingsRepository,
      appScope = appScope
    )
  }

  fun markScheduledTaskJustTriggered(taskId: String) {
    recentTriggeredTasks[taskId] = System.currentTimeMillis()
  }

  fun executeScheduledTaskDirect(taskId: String) {
    executeScheduledTaskInBackground(taskId)
  }

  private fun executeScheduledTaskInBackground(taskId: String) {
    appScope.launch {
      try {
        val dao = database.soundPadDao()
        val taskEntity = dao.getScheduledTaskById(taskId)
        val triggeredAt = recentTriggeredTasks[taskId]
        val recentlyTriggered = triggeredAt != null && (System.currentTimeMillis() - triggeredAt) < 15_000L
        recentTriggeredTasks.remove(taskId)

        if (taskEntity == null || (!taskEntity.enabled && !recentlyTriggered)) {
          SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
          return@launch
        }
        val task = taskEntity.toDomain()
        val profiles = dao.getAllProfilesOnce()
        val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull()
        if (activeProfile == null) {
          SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
          return@launch
        }
        val padEntities = dao.getPadsForProfileOnce(activeProfile.id)
        val settingsMap = dao.getPadSettingsForProfileOnce(activeProfile.id).associateBy { it.padId }
        val domainPads = padEntities.map { entity ->
          entity.toDomainPad(settingsMap[entity.id], ramCache.isCached(entity.uriString))
        }

        when (task.targetType) {
          ScheduleTargetType.PAD -> {
            val targetPad = domainPads.firstOrNull { it.id == task.targetId }
            if (targetPad == null) {
              SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
              return@launch
            }
            audioEngine.triggerScheduledPad(
              pad = targetPad,
              volumeOverride = task.volume,
              fadeInMsOverride = task.fadeInMs,
              loopOverride = task.loopPlayback
            )
          }
          ScheduleTargetType.BANK -> {
            val bankIdx = task.targetId.toIntOrNull() ?: 0
            val bankPads = domainPads.filter { it.bankIndex == bankIdx }
            if (bankPads.isEmpty()) {
              SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
              return@launch
            }
            bankPads.forEach { pad ->
              audioEngine.triggerScheduledPad(
                pad = pad,
                volumeOverride = task.volume,
                fadeInMsOverride = task.fadeInMs,
                loopOverride = task.loopPlayback
              )
            }
          }
          ScheduleTargetType.SEQUENCER -> {
            val playlistEntity = dao.getSequencerPlaylistById(task.targetId)
            if (playlistEntity == null) {
              SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
              return@launch
            }
            sequencerEngine.updateAvailablePads(domainPads)
            audioEngine.setMasterVolume(task.volume)
            sequencerEngine.startPlaylist(playlistEntity.toDomain())
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        CrashLogManager.recordNonFatal(
          context = this@SoundPadApplication,
          source = "SoundPadApplication.executeScheduledTaskInBackground",
          errorType = PadErrorType.PLAYBACK_ERROR,
          throwable = e,
          message = "Gagal mengeksekusi jadwal $taskId: ${e.localizedMessage ?: e.javaClass.simpleName}"
        )
        SoundPadPlaybackService.stopServiceIfIdle(this@SoundPadApplication)
      }
    }
  }

  private fun stopScheduledTaskInBackground(
    taskId: String,
    targetTypeRaw: String,
    targetId: String
  ) {
    appScope.launch {
      try {
        val targetType = runCatching { ScheduleTargetType.valueOf(targetTypeRaw) }
          .getOrDefault(ScheduleTargetType.PAD)
        val resolvedTargetId = if (targetId.isNotBlank()) {
          targetId
        } else if (taskId.isNotBlank()) {
          database.soundPadDao().getScheduledTaskById(taskId)?.targetId.orEmpty()
        } else {
          ""
        }

        ScheduledAutoStopDispatcher.dispatchStopTarget(
          targetType = targetType,
          targetId = resolvedTargetId,
          activePlaylistId = sequencerEngine.state.value.activePlaylistId,
          padsProvider = { emptyList() },
          onStopPad = { padId -> audioEngine.stopPad(padId) },
          onStopSequencer = { sequencerEngine.stop() }
        ).let {
          if (targetType == ScheduleTargetType.BANK) {
            val bankIdx = resolvedTargetId.toIntOrNull() ?: return@let
            val dao = database.soundPadDao()
            val profiles = dao.getAllProfilesOnce()
            val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull() ?: return@let
            dao.getPadsForProfileOnce(activeProfile.id)
              .filter { it.bankIndex == bankIdx }
              .forEach { entity -> audioEngine.stopPad(entity.id) }
          }
        }
      } catch (ce: CancellationException) {
        throw ce
      } catch (e: Exception) {
        CrashLogManager.recordNonFatal(
          context = this@SoundPadApplication,
          source = "SoundPadApplication.stopScheduledTaskInBackground",
          errorType = PadErrorType.PLAYBACK_ERROR,
          throwable = e,
          message = "Gagal menghentikan jadwal $taskId: ${e.localizedMessage ?: e.javaClass.simpleName}"
        )
      }
    }
  }

  override fun onTerminate() {
    super.onTerminate()
    persistentConfigManager.onAppExiting()
    SoundPadPlaybackService.onScheduledTaskRequested = null
    SoundPadPlaybackService.onStopScheduledTaskRequested = null
    midiController.release()
    masterRecorder.cancelRecording()
    voiceRecorder.cancelRecording()
    sequencerEngine.release()
    audioEngine.release()
    ramCache.clear()
  }
}
