package com.example.audio

import android.app.ForegroundServiceStartNotAllowedException
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
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.domain.model.PadErrorType
import com.example.error.CrashLogManager
import java.lang.ref.WeakReference

class SoundPadPlaybackService : Service() {

  companion object {
    private const val TAG = "SoundPadPlaybackService"
    const val CHANNEL_ID = "soundpad_playback_channel"
    const val RECORD_CHANNEL_ID = "soundpad_master_record_channel"
    const val NOTIFICATION_ID = 4101
    const val RECORD_NOTIFICATION_ID = 4102
    const val SCHEDULE_FALLBACK_NOTIFICATION_ID = 4105
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

    @Volatile
    private var isServiceInstanceAlive = false

    @Volatile
    private var activeServiceRef: WeakReference<SoundPadPlaybackService>? = null

    private val stateLock = Any()

    fun updateForegroundState(context: Context, activeCount: Int, activeLabel: String?) {
      val normalizedLabel = activeLabel ?: ""
      synchronized(stateLock) {
        if (activeCount <= 0 && !lastRecordingActive) {
          val wasActive = isForegroundPlaybackActive || lastActiveCount > 0
          lastActiveCount = 0
          lastActiveLabel = ""
          isForegroundPlaybackActive = false
          if (wasActive || isServiceInstanceAlive) {
            stopServiceIfIdleLocked(context)
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
        val started = try {
          context.startForegroundService(intent)
          true
        } catch (e: Exception) {
          CrashLogManager.recordNonFatal(
            context = context,
            source = "SoundPadPlaybackService.updateForegroundState",
            errorType = PadErrorType.PLAYBACK_ERROR,
            throwable = e,
            message = "Gagal memulai foreground playback service: ${e.localizedMessage ?: e.javaClass.simpleName}"
          )
          false
        }
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
            stopServiceIfIdleLocked(context)
          }
          return
        }
        val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
          action = ACTION_UPDATE_MASTER_RECORDING
          putExtra(EXTRA_IS_RECORDING, true)
          putExtra(EXTRA_RECORD_ELAPSED, elapsedLabel ?: "00:00")
        }
        val started = try {
          context.startForegroundService(intent)
          true
        } catch (e: Exception) {
          CrashLogManager.recordNonFatal(
            context = context,
            source = "SoundPadPlaybackService.updateMasterRecordingNotification",
            errorType = PadErrorType.PLAYBACK_ERROR,
            throwable = e,
            message = "Gagal memperbarui notifikasi rekaman master: ${e.localizedMessage ?: e.javaClass.simpleName}"
          )
          false
        }
        lastRecordingActive = started
      }
    }

    fun startScheduledExecution(context: Context, taskId: String): Boolean {
      val intent = Intent(context, SoundPadPlaybackService::class.java).apply {
        action = ACTION_TRIGGER_SCHEDULED_TASK
        putExtra(EXTRA_SCHEDULE_TASK_ID, taskId)
      }
      return try {
        context.startForegroundService(intent)
        true
      } catch (e: Exception) {
        val isFgsDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
          e is ForegroundServiceStartNotAllowedException
        val message = if (isFgsDenied) {
          "Izin foreground service dari latar ditolak sistem saat memicu jadwal $taskId"
        } else {
          "Gagal memulai service untuk jadwal $taskId: ${e.localizedMessage ?: e.javaClass.simpleName}"
        }
        CrashLogManager.recordNonFatal(
          context = context,
          source = "SoundPadPlaybackService.startScheduledExecution",
          errorType = PadErrorType.APP_CRASH,
          throwable = e,
          message = message,
          suggestion = "Aktifkan whitelist baterai (HyperOS/MIUI) serta izin Alarm Tepat Waktu & Notifikasi agar jadwal berjalan andal."
        )
        postScheduledFallbackNotification(context, taskId)
        false
      }
    }

    fun stopScheduledExecution(
      context: Context,
      taskId: String,
      targetType: String,
      targetId: String
    ) {
      // Jangan pernah panggil startForegroundService hanya untuk menghentikan jadwal!
      // Hentikan langsung via callback engine/dispatcher, dan gunakan startService biasa
      // hanya jika service masih hidup dan sudah idle.
      onStopScheduledTaskRequested?.invoke(taskId, targetType, targetId)
      synchronized(stateLock) {
        if (isServiceInstanceAlive && lastActiveCount <= 0 && !lastRecordingActive) {
          stopServiceIfIdleLocked(context)
        }
      }
    }

    fun stopServiceIfIdle(context: Context) {
      synchronized(stateLock) {
        if (lastActiveCount <= 0 && !lastRecordingActive && (isForegroundPlaybackActive || isServiceInstanceAlive)) {
          isForegroundPlaybackActive = false
          stopServiceIfIdleLocked(context)
        }
      }
    }

    private fun stopServiceIfIdleLocked(context: Context) {
      val runningService = activeServiceRef?.get()
      if (runningService != null) {
        runningService.requestStopIfIdleOnMainThread()
        return
      }
      if (!isServiceInstanceAlive) return
      val stopIntent = Intent(context, SoundPadPlaybackService::class.java).apply {
        action = ACTION_STOP_SERVICE
      }
      try {
        context.startService(stopIntent)
      } catch (e: Exception) {
        Log.w(TAG, "startService(ACTION_STOP_SERVICE) diabaikan karena service/background idle: ${e.message}")
      }
    }

    fun ensureNotificationChannels(context: Context) {
      val manager = context.getSystemService(NotificationManager::class.java) ?: return
      if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          context.getString(R.string.notification_channel_playback),
          NotificationManager.IMPORTANCE_LOW
        ).apply {
          description = context.getString(R.string.notification_playback_text)
          setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
      }
      if (manager.getNotificationChannel(RECORD_CHANNEL_ID) == null) {
        val recChannel = NotificationChannel(
          RECORD_CHANNEL_ID,
          context.getString(R.string.notification_recording_title),
          NotificationManager.IMPORTANCE_LOW
        ).apply {
          description = context.getString(R.string.notification_recording_text)
          setShowBadge(false)
        }
        manager.createNotificationChannel(recChannel)
      }
    }

    private fun postScheduledFallbackNotification(context: Context, taskId: String) {
      try {
        ensureNotificationChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
          putExtra(EXTRA_SCHEDULE_TASK_ID, taskId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
          context,
          SCHEDULE_FALLBACK_NOTIFICATION_ID,
          openAppIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_notification_soundpad)
          .setContentTitle(context.getString(R.string.notification_schedule_fallback_title))
          .setContentText(context.getString(R.string.notification_schedule_fallback_text))
          .setAutoCancel(true)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .setContentIntent(contentPendingIntent)
          .build()
        manager.notify(SCHEDULE_FALLBACK_NOTIFICATION_ID, notification)
      } catch (se: SecurityException) {
        Log.w(TAG, "Izin POST_NOTIFICATIONS belum diberikan untuk fallback notifikasi jadwal: ${se.message}")
      } catch (e: Exception) {
        Log.e(TAG, "Gagal menampilkan fallback notifikasi jadwal: ${e.message}")
      }
    }
  }

  @Volatile
  private var foregroundPostedInInstance = false

  override fun onCreate() {
    super.onCreate()
    isServiceInstanceAlive = true
    activeServiceRef = WeakReference(this)
    createNotificationChannels()
  }

  override fun onDestroy() {
    foregroundPostedInInstance = false
    isServiceInstanceAlive = false
    synchronized(stateLock) {
      isForegroundPlaybackActive = false
    }
    if (activeServiceRef?.get() === this) {
      activeServiceRef = null
    }
    super.onDestroy()
  }

  private fun requestStopIfIdleOnMainThread() {
    mainExecutor.execute {
      if (!lastRecordingActive && lastActiveCount <= 0) {
        foregroundPostedInInstance = false
        synchronized(stateLock) {
          isForegroundPlaybackActive = false
        }
        try {
          stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
          Log.w(TAG, "stopForeground saat idle gagal: ${e.message}")
        }
        try {
          stopSelf()
        } catch (e: Exception) {
          Log.w(TAG, "stopSelf saat idle gagal: ${e.message}")
        }
      }
    }
  }

  private fun postForegroundSafely(
    notificationId: Int,
    notification: Notification,
    startId: Int
  ): Boolean {
    createNotificationChannels()
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          notificationId,
          notification,
          ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
      } else {
        startForeground(notificationId, notification)
      }
      foregroundPostedInInstance = true
      true
    } catch (e: Exception) {
      foregroundPostedInInstance = false
      synchronized(stateLock) {
        isForegroundPlaybackActive = false
        lastRecordingActive = false
        lastActiveCount = 0
        lastActiveLabel = ""
      }
      CrashLogManager.recordNonFatal(
        context = applicationContext,
        source = "SoundPadPlaybackService.postForegroundSafely",
        errorType = PadErrorType.APP_CRASH,
        throwable = e,
        message = "Gagal memanggil startForeground(id=$notificationId): ${e.localizedMessage ?: e.javaClass.simpleName}",
        suggestion = "Periksa izin FOREGROUND_SERVICE_MEDIA_PLAYBACK, POST_NOTIFICATIONS, dan status latar perangkat."
      )
      try {
        if (startId > 0) {
          stopSelfResult(startId)
        } else {
          stopSelf()
        }
      } catch (stopErr: Exception) {
        Log.e(TAG, "Gagal stopSelf setelah startForeground gagal: ${stopErr.message}")
      }
      false
    }
  }

  private fun stopForegroundAndSelfSafely(startId: Int) {
    synchronized(stateLock) {
      isForegroundPlaybackActive = false
    }
    foregroundPostedInInstance = false
    try {
      stopForeground(STOP_FOREGROUND_REMOVE)
    } catch (e: Exception) {
      CrashLogManager.recordNonFatal(
        context = applicationContext,
        source = "SoundPadPlaybackService.stopForegroundAndSelfSafely",
        errorType = PadErrorType.PLAYBACK_ERROR,
        throwable = e,
        message = "stopForeground gagal: ${e.localizedMessage ?: e.javaClass.simpleName}"
      )
    }
    try {
      stopSelfResult(startId)
    } catch (e: Exception) {
      Log.w(TAG, "stopSelfResult($startId) gagal: ${e.message}")
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // Wajib panggil startForeground() PERTAMA di SEMUA jalur (action apa pun, intent null, restart, else)
    when (intent?.action) {
      ACTION_STOP_ALL -> {
        val posted = postForegroundSafely(
          NOTIFICATION_ID,
          buildNotification(
            activeCount = lastActiveCount.coerceAtLeast(1),
            activeLabel = lastActiveLabel
          ),
          startId
        )
        if (!posted) return START_NOT_STICKY
        onStopAllRequested?.invoke()
        synchronized(stateLock) {
          lastActiveCount = 0
          lastActiveLabel = ""
        }
        if (!lastRecordingActive) {
          stopForegroundAndSelfSafely(startId)
        }
      }
      ACTION_STOP_MASTER_RECORDING -> {
        val posted = if (lastActiveCount > 0) {
          postForegroundSafely(
            NOTIFICATION_ID,
            buildNotification(lastActiveCount.coerceAtLeast(1), lastActiveLabel),
            startId
          )
        } else {
          postForegroundSafely(
            RECORD_NOTIFICATION_ID,
            buildRecordingNotification("00:00"),
            startId
          )
        }
        if (!posted) return START_NOT_STICKY
        onStopMasterRecordingRequested?.invoke()
        synchronized(stateLock) {
          lastRecordingActive = false
        }
        try {
          getSystemService(NotificationManager::class.java)?.cancel(RECORD_NOTIFICATION_ID)
        } catch (e: Exception) {
          Log.w(TAG, "Gagal membatalkan notifikasi rekam master: ${e.message}")
        }
        if (lastActiveCount <= 0) {
          stopForegroundAndSelfSafely(startId)
        }
      }
      ACTION_STOP_SERVICE -> {
        val posted = postForegroundSafely(
          NOTIFICATION_ID,
          buildNotification(
            activeCount = lastActiveCount.coerceAtLeast(1),
            activeLabel = lastActiveLabel
          ),
          startId
        )
        if (!posted) return START_NOT_STICKY
        if (!lastRecordingActive && lastActiveCount <= 0) {
          stopForegroundAndSelfSafely(startId)
        }
      }
      ACTION_UPDATE_MASTER_RECORDING -> {
        val elapsed = intent.getStringExtra(EXTRA_RECORD_ELAPSED) ?: "00:00"
        val recNotification = buildRecordingNotification(elapsed)
        postForegroundSafely(RECORD_NOTIFICATION_ID, recNotification, startId)
      }
      ACTION_TRIGGER_SCHEDULED_TASK -> {
        val notification = buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = getString(R.string.notification_playback_title)
        )
        val posted = postForegroundSafely(NOTIFICATION_ID, notification, startId)
        if (!posted) return START_NOT_STICKY
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
        val posted = postForegroundSafely(NOTIFICATION_ID, notification, startId)
        if (!posted) return START_NOT_STICKY
        val taskId = intent.getStringExtra(EXTRA_SCHEDULE_TASK_ID).orEmpty()
        val targetType = intent.getStringExtra(EXTRA_SCHEDULE_TARGET_TYPE).orEmpty()
        val targetId = intent.getStringExtra(EXTRA_SCHEDULE_TARGET_ID).orEmpty()
        onStopScheduledTaskRequested?.invoke(taskId, targetType, targetId)
        if (!lastRecordingActive && lastActiveCount <= 0) {
          stopForegroundAndSelfSafely(startId)
        }
      }
      ACTION_START_OR_UPDATE -> {
        val activeCount = intent.getIntExtra(EXTRA_ACTIVE_COUNT, 1).coerceAtLeast(1)
        val activeLabel = intent.getStringExtra(EXTRA_ACTIVE_LABEL).orEmpty()
        val notification = buildNotification(activeCount, activeLabel)
        postForegroundSafely(NOTIFICATION_ID, notification, startId)
      }
      null -> {
        val notification = buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = lastActiveLabel
        )
        val posted = postForegroundSafely(NOTIFICATION_ID, notification, startId)
        if (posted && !lastRecordingActive && lastActiveCount <= 0) {
          stopForegroundAndSelfSafely(startId)
        }
      }
      else -> {
        val notification = buildNotification(
          activeCount = lastActiveCount.coerceAtLeast(1),
          activeLabel = lastActiveLabel
        )
        val posted = postForegroundSafely(NOTIFICATION_ID, notification, startId)
        if (posted && !lastRecordingActive && lastActiveCount <= 0) {
          stopForegroundAndSelfSafely(startId)
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun createNotificationChannels() {
    ensureNotificationChannels(this)
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
