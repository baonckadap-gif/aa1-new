package com.example

import android.app.Application
import com.example.audio.InMemoryAudioCache
import com.example.audio.MasterOutputRecorder
import com.example.audio.SequencerEngine
import com.example.audio.SoundPadAudioEngine
import com.example.audio.SoundPadPlaybackService
import com.example.audio.VoiceTrackRecorder
import com.example.audio.toDomain
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.saf.SafAudioScanner
import com.example.domain.model.ScheduleTargetType
import com.example.error.CrashLogManager
import com.example.midi.MidiControllerManager
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.toDomain
import com.example.ui.toDomainPad
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SoundPadApplication : Application() {

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    CrashLogManager.install(this)

    // Ensure scheduled tasks fire reliably even if the UI isn't open
    SoundPadPlaybackService.onScheduledTaskRequested = { taskId ->
      executeScheduledTaskInBackground(taskId)
    }

    // Re-register active alarms on process start as defensive guarantee
    appScope.launch {
      runCatching { PadSchedulerManager.rescheduleAllActive(this@SoundPadApplication) }
    }
  }

  val database: SoundPadDatabase by lazy { SoundPadDatabase.getInstance(this) }
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

  private fun executeScheduledTaskInBackground(taskId: String) {
    appScope.launch {
      val dao = database.soundPadDao()
      val taskEntity = dao.getScheduledTaskById(taskId) ?: return@launch
      val task = taskEntity.toDomain()
      val profiles = dao.getAllProfilesOnce()
      val activeProfile = profiles.firstOrNull { it.isActive } ?: profiles.firstOrNull() ?: return@launch
      val padEntities = dao.getPadsForProfileOnce(activeProfile.id)
      val settingsMap = dao.getPadSettingsForProfileOnce(activeProfile.id).associateBy { it.padId }
      val domainPads = padEntities.map { entity ->
        entity.toDomainPad(settingsMap[entity.id], ramCache.isCached(entity.uriString))
      }

      when (task.targetType) {
        ScheduleTargetType.PAD -> {
          val targetPad = domainPads.firstOrNull { it.id == task.targetId } ?: return@launch
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
          val playlistEntity = dao.getSequencerPlaylistById(task.targetId) ?: return@launch
          sequencerEngine.updateAvailablePads(domainPads)
          audioEngine.setMasterVolume(task.volume)
          sequencerEngine.startPlaylist(playlistEntity.toDomain())
        }
      }

      if (task.autoStopMinutes > 0) {
        delay(task.autoStopMinutes * 60_000L)
        sequencerEngine.stop()
        audioEngine.stopAll()
      }
    }
  }

  override fun onTerminate() {
    super.onTerminate()
    SoundPadPlaybackService.onScheduledTaskRequested = null
    midiController.release()
    masterRecorder.cancelRecording()
    voiceRecorder.cancelRecording()
    sequencerEngine.release()
    audioEngine.release()
    ramCache.clear()
  }
}
