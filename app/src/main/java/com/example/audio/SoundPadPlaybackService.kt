package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

class SoundPadPlaybackService : Service() {

  companion object {
    const val CHANNEL_ID = "soundpad_playback_channel"
    const val RECORD_CHANNEL_ID = "soundpad_master_record_channel"
    const val NOTIFICATION_ID = 4101
    const val RECORD_NOTIFICATION_ID = 4102
    const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE"
    const val ACTION_STOP_ALL = "com.example.action.STOP_ALL"
    const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
    const val ACTION_UPDATE_MASTER_RECORDING = "com.example.action.UPDATE_MASTER_RECORDING"
    const val ACTION_STOP_MASTER_RECORDING = "com.example.action.STOP_MASTER_RECORDING"
    const val ACTION_TRIGGER_SCHEDULED_TASK = "com.example.action.SERVICE_TRIGGER_SCHEDULED_TASK"
    const val ACTION_STOP_SCHEDULED_TASK = "com.example.action.SERVICE_STOP_SCHEDULED_TASK"

    const val EXTRA_ACTIVE_COUNT = "extra_active_count"
    const val EXTRA_ACTIVE_LABEL = "extra_active_label"
    const val EXTRA_IS_RECORDING = "extra_is_recording"
    const val EXTRA_RECORD_ELAPSED = "extra_record_elapsed"
    const val EXTRA_SCHEDULE_TASK_ID = "extra_schedule_task_id"
    const val EXTRA_SCHEDULE_TARGET_TYPE = "extra_schedule_target_type"
    const val EXTRA_SCHEDULE_TARGET_ID = "extra_schedule_target_id"

    @Volatile
    var onStopAllRequested: (() -> Unit)? = null

    @Volatile
    var onStopMasterRecordingRequested: (() -> Unit)? = null

    @Volatile
    var onScheduledTaskRequested: ((String) -> Unit)? = null

    @Volatile
    var onStopScheduledTaskRequested: ((taskId: String, targetType: String, targetId: String) -> Unit)? = null

    @Volatile
    private var lastActiveCount = 0

    @Volatile
    private var lastActiveLabel = ""

    @Volatile
    private var lastRecordingActive = false

    @Volatile
    private var isForegroundPlaybackActive = false

    private val stateLock = Any()

    fun updateForegroundState(context: Context, activeCount: Int, activeLabel: String?) {
      val normalizedLabel = activeLabel ?: ""
      synchronized(stateLock) {
        if (activeCount <= 0 && !lastRecordingActive) {
          val wasActive = isForegroundPlaybackActive || lastActiveCount > 0
          lastActiveCount = 0
          lastActiveLabel = ""
          isForegroundPlaybackActive = false
          if (wasActive) {
            val stopIntent = Intent(context, SoundPadPlaybackService::class.java).apply {
              action = ACTION_STOP_SERVICE
            }
            runCatching { context.startService(stopIntent) }
          }
          return
        }
        if (activeCount <= 0 && lastRecordingActive) {
          lastActiveCount = 0
          lastActiveLabel = ""
          return
        }
        if (isForegroundPlaybackActive && lastActiveCount == activeCount && lastActiveLabel == normalizedLabel) {
          return
        }
        val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
          action = ACTION_START_OR_UPDATE
          putExtra(EXTRA_ACTIVE_COUNT, activeCount)
          putExtra(EXTRA_ACTIVE_LABEL, normalizedLabel)
        }
        val started = runCatching {
          context.startForegroundService(intent)
        }.isSuccess
        if (started) {
          lastActiveCount = activeCount
          lastActiveLabel = normalizedLabel
          isForegroundPlaybackActive = true
        } else {
          lastActiveCount = 0
          lastActiveLabel = ""
          isForegroundPlaybackActive = false
        }
      }
    }

    fun updateMasterRecordingNotification(context: Context, isRecording: Boolean, elapsedLabel: String?) {
      synchronized(stateLock) {
        if (!isRecording) {
          val wasRecording = lastRecordingActive
          lastRecordingActive = false
          val mgr = context.getSystemService(NotificationManager::class.java)
          mgr?.cancel(RECORD_NOTIFICATION_ID)
          if (wasRecording && lastActiveCount <= 0) {
            isForegroundPlaybackActive = false
            val stopIntent = Intent(context, SoundPadPlaybackService::class.java).apply {
              action = ACTION_STOP_SERVICE
            }
            runCatching { context.startService(stopIntent) }
          }
          return
        }
        val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
          action = ACTION_UPDATE_MASTER_RECORDING
          putExtra(EXTRA_IS_RECORDING, true)
          putExtra(EXTRA_RECORD_ELAPSED, elapsedLabel ?: "00:00")
        }
        val started = runCatching { context.startForegroundService(intent) }.isSuccess
        lastRecordingActive = started
      }
    }

    fun startScheduledExecution(context: Context, taskId: String) {
      val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
        action = ACTION_TRIGGER_SCHEDULED_TASK
        putExtra(EXTRA_SCHEDULE_TASK_ID, taskId)
      }
      runCatching { context.startForegroundService(intent) }
    }

    fun stopScheduledExecution(
      context: Context,
      taskId: String,
      targetType: String,
      targetId: String
    ) {
      val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
        action = ACTION_STOP_SCHEDULED_TASK
        putExtra(EXTRA_SCHEDULE_TASK_ID, taskId)
        putExtra(EXTRA_SCHEDULE_TARGET_TYPE, targetType)
        putExtra(EXTRA_SCHEDULE_TARGET_ID, targetId)
      }
      runCatching { context.startForegroundService(intent) }
    }
  }

  @Volatile
  private var foregroundPostedInInstance = false

  override fun onCreate() {
    super.onCreate()
    createNotificationChannels()
  }

  override fun onDestroy() {
    foregroundPostedInInstance = false
    super.onDestroy()
  }

  private fun postForegroundSafely(notificationId: Int, notification: Notification) {
    val result = runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          notificationId,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        startForeground(notificationId, notification)
      }
    }
    if (result.isSuccess) {
      foregroundPostedInInstance = true
    }
  }

  private fun ensureForegroundPostedIfNeeded() {
    if (!foregroundPostedInInstance) {
      postForegroundSafely(
        NOTIFICATION_ID,
        buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = lastActiveLabel
        )
      )
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_STOP_ALL -> {
        ensureForegroundPostedIfNeeded()
        onStopAllRequested?.invoke()
        if (!lastRecordingActive) {
          isForegroundPlaybackActive = false
          foregroundPostedInInstance = false
          runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
          stopSelfResult(startId)
        }
      }
      ACTION_STOP_MASTER_RECORDING -> {
        ensureForegroundPostedIfNeeded()
        onStopMasterRecordingRequested?.invoke()
        lastRecordingActive = false
        getSystemService(NotificationManager::class.java)?.cancel(RECORD_NOTIFICATION_ID)
        if (lastActiveCount <= 0) {
          isForegroundPlaybackActive = false
          foregroundPostedInInstance = false
          runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
          stopSelfResult(startId)
        }
      }
      ACTION_STOP_SERVICE -> {
        ensureForegroundPostedIfNeeded()
        if (!lastRecordingActive && lastActiveCount <= 0) {
          isForegroundPlaybackActive = false
          foregroundPostedInInstance = false
          runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
          stopSelfResult(startId)
        }
      }
      ACTION_UPDATE_MASTER_RECORDING -> {
        val elapsed = intent.getStringExtra(EXTRA_RECORD_ELAPSED) ?: "00:00"
        val recNotification = buildRecordingNotification(elapsed)
        postForegroundSafely(RECORD_NOTIFICATION_ID, recNotification)
      }
      ACTION_TRIGGER_SCHEDULED_TASK -> {
        val notification = buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = getString(R.string.notification_playback_title)
        )
        postForegroundSafely(NOTIFICATION_ID, notification)
        val taskId = intent.getStringExtra(EXTRA_SCHEDULE_TASK_ID)
        if (!taskId.isNullOrBlank()) {
          onScheduledTaskRequested?.invoke(taskId)
        }
      }
      ACTION_STOP_SCHEDULED_TASK -> {
        val notification = buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = lastActiveLabel
        )
        postForegroundSafely(NOTIFICATION_ID, notification)
        val taskId = intent.getStringExtra(EXTRA_SCHEDULE_TASK_ID).orEmpty()
        val targetType = intent.getStringExtra(EXTRA_SCHEDULE_TARGET_TYPE).orEmpty()
        val targetId = intent.getStringExtra(EXTRA_SCHEDULE_TARGET_ID).orEmpty()
        onStopScheduledTaskRequested?.invoke(taskId, targetType, targetId)
        if (!lastRecordingActive && lastActiveCount <= 0) {
          isForegroundPlaybackActive = false
          foregroundPostedInInstance = false
          runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
          stopSelfResult(startId)
        }
      }
      ACTION_START_OR_UPDATE, null -> {
        val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1) ?: 1
        val activeLabel = intent?.getStringExtra(EXTRA_ACTIVE_LABEL).orEmpty()
        val notification = buildNotification(activeCount, activeLabel)
        postForegroundSafely(NOTIFICATION_ID, notification)
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannels() {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        getString(R.string.notification_channel_playback),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.notification_playback_text)
        setShowBadge(false)
      }
      manager.createNotificationChannel(channel)
    }
    if (manager.getNotificationChannel(RECORD_CHANNEL_ID) == null) {
      val recChannel = NotificationChannel(
        RECORD_CHANNEL_ID,
        getString(R.string.notification_recording_title),
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = getString(R.string.notification_recording_text)
        setShowBadge(false)
      }
      manager.createNotificationChannel(recChannel)
    }
  }

  private fun buildNotification(activeCount: Int, activeLabel: String): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      0,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val stopAllIntent = Intent(this, SoundPadPlaybackService::class.java).apply {
      action = ACTION_STOP_ALL
    }
    val stopPendingIntent = PendingIntent.getService(
      this,
      1,
      stopAllIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val contentText = if (activeCount == 1 && activeLabel.isNotBlank()) {
      "Playing: $activeLabel"
    } else {
      "Playing $activeCount active pads"
    }

    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_soundpad)
      .setContentTitle(getString(R.string.notification_playback_title))
      .setContentText(contentText)
      .setOngoing(true)
      .setSilent(true)
      .setContentIntent(contentPendingIntent)
      .addAction(
        android.R.drawable.ic_media_pause,
        getString(R.string.notification_action_stop_all),
        stopPendingIntent
      )
      .build()
  }

  private fun buildRecordingNotification(elapsedLabel: String): Notification {
    val openAppIntent = Intent(this, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val contentPendingIntent = PendingIntent.getActivity(
      this,
      10,
      openAppIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val stopRecIntent = Intent(this, SoundPadPlaybackService::class.java).apply {
      action = ACTION_STOP_MASTER_RECORDING
    }
    val stopRecPending = PendingIntent.getService(
      this,
      11,
      stopRecIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    return NotificationCompat.Builder(this, RECORD_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_notification_soundpad)
      .setContentTitle("${getString(R.string.notification_recording_title)} ($elapsedLabel)")
      .setContentText(getString(R.string.notification_recording_text))
      .setOngoing(true)
      .setSilent(true)
      .setContentIntent(contentPendingIntent)
      .addAction(
        android.R.drawable.ic_media_pause,
        getString(R.string.notification_action_stop_recording),
        stopRecPending
      )
      .build()
  }
}
