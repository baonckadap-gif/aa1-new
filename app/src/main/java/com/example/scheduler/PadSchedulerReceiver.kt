package com.example.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.audio.SoundPadPlaybackService
import com.example.data.local.db.SoundPadDatabase
import com.example.domain.model.ScheduleRepeatMode
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
    }
  }
}
