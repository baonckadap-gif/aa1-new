package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.SoundPadApplication
import com.example.audio.SoundPadPlaybackService
import com.example.data.local.db.SoundPadDatabase
import com.example.domain.model.PadErrorType
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.error.CrashLogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PadSchedulerReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent?) {
    val action = intent?.action ?: return
    val appCtx = context.applicationContext

    when (action) {
      Intent.ACTION_BOOT_COMPLETED,
      "android.intent.action.QUICKBOOT_POWERON" -> {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            PadSchedulerManager.rescheduleAllActive(appCtx)
          } catch (ce: CancellationException) {
            throw ce
          } catch (e: Exception) {
            CrashLogManager.recordNonFatal(
              context = appCtx,
              source = "PadSchedulerReceiver.BOOT_COMPLETED",
              errorType = PadErrorType.PLAYBACK_ERROR,
              throwable = e,
              message = "Gagal menjadwal ulang alarm setelah boot: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
          } finally {
            pendingResult.finish()
          }
        }
      }

      PadSchedulerManager.ACTION_TRIGGER_SCHEDULE -> {
        val taskId = intent.getStringExtra(PadSchedulerManager.EXTRA_TASK_ID) ?: return
        // Catat bahwa receiver baru saja memicu taskId agar race ONCE (enabled=false) tidak membatalkan playback
        (appCtx as? SoundPadApplication)?.markScheduledTaskJustTriggered(taskId)
        // Panggil startForegroundService SECEPAT MUNGKIN di onReceive() sebelum goAsync()/IO coroutine
        val fgsStarted = SoundPadPlaybackService.startScheduledExecution(appCtx, taskId)
        if (!fgsStarted) {
          // Fallback notifikasi + Error Log sudah dicatat oleh startScheduledExecution; coba eksekusi langsung bila proses hidup
          (appCtx as? SoundPadApplication)?.executeScheduledTaskDirect(taskId)
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val dao = SoundPadDatabase.getInstance(appCtx).soundPadDao()
            val entity = dao.getScheduledTaskById(taskId)
            if (entity != null && entity.enabled) {
              val task = entity.toDomain()
              if (task.autoStopMinutes > 0) {
                PadSchedulerManager.scheduleAutoStop(appCtx, task)
              }
              if (task.repeatMode == ScheduleRepeatMode.ONCE) {
                dao.upsertScheduledTask(entity.copy(enabled = false, nextTriggerAtMs = 0L))
              } else {
                val nextMs = PadSchedulerManager.scheduleTask(appCtx, task)
                dao.upsertScheduledTask(entity.copy(nextTriggerAtMs = nextMs))
              }
            } else {
              SoundPadPlaybackService.stopServiceIfIdle(appCtx)
            }
          } catch (ce: CancellationException) {
            throw ce
          } catch (e: Exception) {
            CrashLogManager.recordNonFatal(
              context = appCtx,
              source = "PadSchedulerReceiver.ACTION_TRIGGER_SCHEDULE",
              errorType = PadErrorType.PLAYBACK_ERROR,
              throwable = e,
              message = "Gagal memproses jadwal $taskId: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
          } finally {
            pendingResult?.finish()
          }
        }
      }

      PadSchedulerManager.ACTION_STOP_SCHEDULED_PAD -> {
        val taskId = intent.getStringExtra(PadSchedulerManager.EXTRA_TASK_ID).orEmpty()
        val targetType = intent.getStringExtra(PadSchedulerManager.EXTRA_TARGET_TYPE)
          ?: ScheduleTargetType.PAD.name
        val targetId = intent.getStringExtra(PadSchedulerManager.EXTRA_TARGET_ID).orEmpty()
        // Hentikan langsung tanpa startForegroundService (aman di background)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val resolvedTask = if (taskId.isNotBlank()) {
              SoundPadDatabase.getInstance(appCtx).soundPadDao().getScheduledTaskById(taskId)?.toDomain()
            } else null
            val finalType = resolvedTask?.targetType?.name ?: targetType
            val finalTargetId = resolvedTask?.targetId ?: targetId
            SoundPadPlaybackService.stopScheduledExecution(
              context = appCtx,
              taskId = taskId,
              targetType = finalType,
              targetId = finalTargetId
            )
          } catch (ce: CancellationException) {
            throw ce
          } catch (e: Exception) {
            CrashLogManager.recordNonFatal(
              context = appCtx,
              source = "PadSchedulerReceiver.ACTION_STOP_SCHEDULED_PAD",
              errorType = PadErrorType.PLAYBACK_ERROR,
              throwable = e,
              message = "Gagal menghentikan jadwal $taskId: ${e.localizedMessage ?: e.javaClass.simpleName}"
            )
          } finally {
            pendingResult?.finish()
          }
        }
      }
    }
  }
}
