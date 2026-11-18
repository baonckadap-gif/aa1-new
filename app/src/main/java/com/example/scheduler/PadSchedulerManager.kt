package com.example.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.data.local.db.ScheduledTaskEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.ScheduledPadTask
import java.util.Calendar

object PadSchedulerManager {
  const val ACTION_TRIGGER_SCHEDULE = "com.example.action.TRIGGER_SCHEDULED_PAD"
  const val EXTRA_TASK_ID = "extra_task_id"

  fun canScheduleExactAlarms(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
    return am.canScheduleExactAlarms()
  }

  fun buildExactAlarmPermissionIntent(context: Context): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  /**
   * Computes the next trigger epoch millis for [hour]:[minute] given [repeatMode] and
   * [daysOfWeekMask] (bit 0 = Monday .. bit 6 = Sunday).
   */
  fun computeNextTriggerTimeMs(
    hour: Int,
    minute: Int,
    repeatMode: ScheduleRepeatMode,
    daysOfWeekMask: Int,
    nowMs: Long = System.currentTimeMillis()
  ): Long {
    val safeHour = hour.coerceIn(0, 23)
    val safeMinute = minute.coerceIn(0, 59)
    val cal = Calendar.getInstance().apply {
      timeInMillis = nowMs
      set(Calendar.HOUR_OF_DAY, safeHour)
      set(Calendar.MINUTE, safeMinute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
    }

    if (repeatMode != ScheduleRepeatMode.CUSTOM_DAYS || daysOfWeekMask == 0) {
      if (cal.timeInMillis <= nowMs) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
      }
      return cal.timeInMillis
    }

    for (offset in 0..7) {
      val candidate = Calendar.getInstance().apply {
        timeInMillis = cal.timeInMillis
        add(Calendar.DAY_OF_YEAR, offset)
      }
      if (candidate.timeInMillis <= nowMs) continue
      val bitIdx = calendarDayToMondayZeroIndex(candidate.get(Calendar.DAY_OF_WEEK))
      if ((daysOfWeekMask and (1 shl bitIdx)) != 0) {
        return candidate.timeInMillis
      }
    }
    if (cal.timeInMillis <= nowMs) cal.add(Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
  }

  private fun calendarDayToMondayZeroIndex(dayOfWeek: Int): Int {
    return when (dayOfWeek) {
      Calendar.MONDAY -> 0
      Calendar.TUESDAY -> 1
      Calendar.WEDNESDAY -> 2
      Calendar.THURSDAY -> 3
      Calendar.FRIDAY -> 4
      Calendar.SATURDAY -> 5
      Calendar.SUNDAY -> 6
      else -> 0
    }
  }

  fun formatDaysSummary(repeatMode: ScheduleRepeatMode, daysMask: Int): String {
    return when (repeatMode) {
      ScheduleRepeatMode.ONCE -> "Sekali"
      ScheduleRepeatMode.DAILY -> "Setiap Hari"
      ScheduleRepeatMode.CUSTOM_DAYS -> {
        val labels = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
        val selected = labels.filterIndexed { idx, _ -> (daysMask and (1 shl idx)) != 0 }
        if (selected.isEmpty()) "Sekali" else selected.joinToString(", ")
      }
    }
  }

  fun scheduleTask(context: Context, task: ScheduledPadTask): Long {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return 0L
    if (!task.enabled) {
      cancelTask(context, task.id)
      return 0L
    }
    val triggerAtMs = computeNextTriggerTimeMs(
      hour = task.hour,
      minute = task.minute,
      repeatMode = task.repeatMode,
      daysOfWeekMask = task.daysOfWeekMask
    )
    val pi = buildPendingIntent(context, task.id)
    runCatching {
      if (canScheduleExactAlarms(context)) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
      } else {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
      }
    }
    return triggerAtMs
  }

  fun cancelTask(context: Context, taskId: String) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
    runCatching {
      alarmManager.cancel(buildPendingIntent(context, taskId))
    }
  }

  suspend fun rescheduleAllActive(context: Context) {
    val dao = SoundPadDatabase.getInstance(context).soundPadDao()
    val active = dao.getActiveScheduledTasksOnce()
    active.forEach { entity ->
      val domain = entity.toDomain()
      val nextMs = scheduleTask(context, domain)
      if (nextMs > 0L && nextMs != entity.nextTriggerAtMs) {
        dao.upsertScheduledTask(entity.copy(nextTriggerAtMs = nextMs))
      }
    }
  }

  private fun buildPendingIntent(context: Context, taskId: String): PendingIntent {
    val intent = Intent(context, PadSchedulerReceiver::class.java).apply {
      action = ACTION_TRIGGER_SCHEDULE
      putExtra(EXTRA_TASK_ID, taskId)
    }
    return PendingIntent.getBroadcast(
      context,
      taskId.hashCode(),
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }
}

fun ScheduledTaskEntity.toDomain(): ScheduledPadTask = ScheduledPadTask(
  id = id,
  title = title,
  targetType = runCatching { ScheduleTargetType.valueOf(targetType) }.getOrDefault(ScheduleTargetType.PAD),
  targetId = targetId,
  hour = hour,
  minute = minute,
  repeatMode = runCatching { ScheduleRepeatMode.valueOf(repeatMode) }.getOrDefault(ScheduleRepeatMode.ONCE),
  daysOfWeekMask = daysOfWeekMask,
  enabled = enabled,
  volume = volume,
  fadeInMs = fadeInMs,
  autoStopMinutes = autoStopMinutes,
  loopPlayback = loopPlayback,
  nextTriggerAtMs = nextTriggerAtMs,
  createdAtMs = createdAtMs
)

fun ScheduledPadTask.toEntity(): ScheduledTaskEntity = ScheduledTaskEntity(
  id = id,
  title = title,
  targetType = targetType.name,
  targetId = targetId,
  hour = hour,
  minute = minute,
  repeatMode = repeatMode.name,
  daysOfWeekMask = daysOfWeekMask,
  enabled = enabled,
  volume = volume,
  fadeInMs = fadeInMs,
  autoStopMinutes = autoStopMinutes,
  loopPlayback = loopPlayback,
  nextTriggerAtMs = nextTriggerAtMs,
  createdAtMs = createdAtMs
)
