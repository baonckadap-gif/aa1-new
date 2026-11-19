package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.audio.SoundPadPlaybackService
import com.example.data.local.db.SoundPadDatabase
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
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
          } finally {
            pendingResult.finish()
          }
        }
      }

      PadSchedulerManager.ACTION_TRIGGER_SCHEDULE -> {
        val taskId = intent.getStringExtra(PadSchedulerManager.EXTRA_TASK_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val dao = SoundPadDatabase.getInstance(appCtx).soundPadDao()
            val entity = dao.getScheduledTaskById(taskId)
            if (entity != null && entity.enabled) {
              val task = entity.toDomain()
              SoundPadPlaybackService.startScheduledExecution(appCtx, task.id)
              if (task.autoStopMinutes > 0) {
                PadSchedulerManager.scheduleAutoStop(appCtx, task)
              }
              if (task.repeatMode == ScheduleRepeatMode.ONCE) {
                dao.upsertScheduledTask(entity.copy(enabled = false, nextTriggerAtMs = 0L))
              } else {
                val nextMs = PadSchedulerManager.scheduleTask(appCtx, task)
                dao.upsertScheduledTask(entity.copy(nextTriggerAtMs = nextMs))
              }
            }
          } finally {
            pendingResult.finish()
          }
        }
      }

      PadSchedulerManager.ACTION_STOP_SCHEDULED_PAD -> {
        val taskId = intent.getStringExtra(PadSchedulerManager.EXTRA_TASK_ID).orEmpty()
        val targetType = intent.getStringExtra(PadSchedulerManager.EXTRA_TARGET_TYPE)
          ?: ScheduleTargetType.PAD.name
        val targetId = intent.getStringExtra(PadSchedulerManager.EXTRA_TARGET_ID).orEmpty()
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
          } finally {
            pendingResult.finish()
          }
        }
      }
    }
  }
}
