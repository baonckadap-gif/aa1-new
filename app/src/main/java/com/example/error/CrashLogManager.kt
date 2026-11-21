package com.example.error

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.local.db.ErrorLogEntity
import com.example.data.local.db.SoundPadDao
import com.example.data.local.db.SoundPadDatabase
import com.example.domain.model.AppErrorLog
import com.example.domain.model.PadErrorType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object CrashLogManager {

  private const val TAG = "SoundPadCrashLog"
  private const val PENDING_CRASH_FILE = "pending_crash_log.json"
  private val logScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  @Volatile
  private var installed = false

  fun install(context: Context) {
    if (installed) return
    installed = true
    val appContext = context.applicationContext
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      runCatching {
        persistPendingCrash(appContext, thread.name, throwable)
      }
      previousHandler?.uncaughtException(thread, throwable)
    }
  }

  fun persistPendingCrash(context: Context, threadName: String, throwable: Throwable) {
    val stackTrace = throwable.stackTraceToString().take(4000)
    val payload = JSONObject().apply {
      put("id", "crash_${UUID.randomUUID()}")
      put("timestampMs", System.currentTimeMillis())
      put("threadName", threadName)
      put("exceptionClass", throwable.javaClass.name)
      put("message", throwable.localizedMessage ?: throwable.message ?: throwable.javaClass.simpleName)
      put("stackTrace", stackTrace)
    }
    val file = File(context.filesDir, PENDING_CRASH_FILE)
    file.writeText(payload.toString(), Charsets.UTF_8)
  }

  fun consumePendingCrash(context: Context): AppErrorLog? {
    val file = File(context.filesDir, PENDING_CRASH_FILE)
    if (!file.exists()) return null
    return runCatching {
      val json = JSONObject(file.readText(Charsets.UTF_8))
      file.delete()
      val excName = json.optString("exceptionClass", "Exception").substringAfterLast('.')
      val rawMsg = json.optString("message", "Crash tidak diketahui")
      val thread = json.optString("threadName", "main")
      AppErrorLog(
        id = json.optString("id", "crash_${UUID.randomUUID()}"),
        timestampMs = json.optLong("timestampMs", System.currentTimeMillis()),
        fileName = "Thread: $thread ($excName)",
        uriString = null,
        errorType = PadErrorType.APP_CRASH,
        message = "Crash sesi sebelumnya [$excName]: $rawMsg",
        suggestion = PadErrorType.APP_CRASH.defaultSuggestion,
        stackTrace = json.optString("stackTrace", "")
      )
    }.getOrElse {
      runCatching { file.delete() }
      null
    }
  }

  fun recordNonFatal(
    context: Context,
    source: String,
    errorType: PadErrorType = PadErrorType.PLAYBACK_ERROR,
    throwable: Throwable? = null,
    message: String = throwable?.localizedMessage ?: throwable?.message ?: errorType.label,
    suggestion: String = errorType.defaultSuggestion,
    padId: String? = null,
    uriString: String? = null
  ) {
    if (throwable is CancellationException) throw throwable
    Log.e(TAG, "[$source] $message", throwable)
    val appCtx = context.applicationContext
    val stack = throwable?.stackTraceToString()?.take(4000) ?: ""
    val entity = ErrorLogEntity(
      id = "err_${UUID.randomUUID()}",
      timestampMs = System.currentTimeMillis(),
      fileName = source,
      uriString = uriString,
      errorType = errorType.name,
      message = message,
      suggestion = suggestion,
      stackTrace = stack,
      padId = padId
    )
    logScope.launch {
      try {
        SoundPadDatabase.getInstance(appCtx).soundPadDao().insertErrorLog(entity)
      } catch (e: Exception) {
        Log.e(TAG, "Gagal menulis ErrorLogEntity ke Room: ${e.message}")
      }
    }
  }

  suspend fun recordNonFatalToDao(
    dao: SoundPadDao,
    source: String,
    errorType: PadErrorType = PadErrorType.PLAYBACK_ERROR,
    throwable: Throwable? = null,
    message: String = throwable?.localizedMessage ?: throwable?.message ?: errorType.label,
    suggestion: String = errorType.defaultSuggestion,
    padId: String? = null,
    uriString: String? = null
  ) {
    if (throwable is CancellationException) throw throwable
    Log.e(TAG, "[$source] $message", throwable)
    val stack = throwable?.stackTraceToString()?.take(4000) ?: ""
    val entity = ErrorLogEntity(
      id = "err_${UUID.randomUUID()}",
      timestampMs = System.currentTimeMillis(),
      fileName = source,
      uriString = uriString,
      errorType = errorType.name,
      message = message,
      suggestion = suggestion,
      stackTrace = stack,
      padId = padId
    )
    try {
      dao.insertErrorLog(entity)
    } catch (ce: CancellationException) {
      throw ce
    } catch (e: Exception) {
      Log.e(TAG, "Gagal menulis ErrorLogEntity ke Room: ${e.message}")
    }
  }

  fun formatErrorLogsAsTxt(logs: List<AppErrorLog>): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return buildString {
      appendLine("==============================================")
      appendLine("SoundPad Pro - Error & Crash Diagnostic Log")
      appendLine("Generated: ${sdf.format(Date())}")
      appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
      appendLine("Total Entries: ${logs.size}")
      appendLine("==============================================")
      appendLine()
      if (logs.isEmpty()) {
        appendLine("Tidak ada error tercatat.")
      } else {
        logs.forEachIndexed { idx, log ->
          appendLine("[#${idx + 1}] ${sdf.format(Date(log.timestampMs))} | ${log.errorType.label}")
          appendLine("File / Target : ${log.fileName}")
          if (!log.uriString.isNullOrBlank()) {
            appendLine("URI           : ${log.uriString}")
          }
          appendLine("Pesan Error   : ${log.message}")
          appendLine("Saran Perbaikan: ${log.suggestion}")
          if (log.stackTrace.isNotBlank()) {
            appendLine("Stacktrace    :")
            appendLine(log.stackTrace.trim())
          }
          appendLine("----------------------------------------------")
        }
      }
    }
  }
}
