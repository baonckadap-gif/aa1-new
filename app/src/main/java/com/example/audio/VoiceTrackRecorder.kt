package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

data class VoiceRecordingResult(
  val file: File,
  val durationMs: Long
)

class VoiceTrackRecorder(private val context: Context) {

  companion object {
    private const val SAMPLE_RATE = 44100
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var recordJob: Job? = null
  private var activeAudioRecord: AudioRecord? = null
  private var activeFile: File? = null
  private var startTimeMs: Long = 0L

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _level = MutableStateFlow(0f)
  val level: StateFlow<Float> = _level.asStateFlow()

  private val _elapsedMs = MutableStateFlow(0L)
  val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

  fun getVoiceDirectory(): File {
    return File(context.filesDir, "voice_tracks").apply { mkdirs() }
  }

  @SuppressLint("MissingPermission")
  fun startRecording(): Result<File> {
    if (_isRecording.value) {
      return activeFile?.let { Result.success(it) } ?: Result.failure(IllegalStateException("Already recording"))
    }
    return runCatching {
      val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        .coerceAtLeast(4096)
      val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        minBuf * 2
      )
      check(recorder.state == AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        "Microphone initialization failed"
      }

      val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val outFile = File(getVoiceDirectory(), "Voice_$stamp.wav")
      // Write placeholder 44-byte WAV header
      outFile.outputStream().use { it.write(ByteArray(44)) }

      activeAudioRecord = recorder
      activeFile = outFile
      startTimeMs = System.currentTimeMillis()
      _elapsedMs.value = 0L
      _level.value = 0f
      _isRecording.value = true

      recorder.startRecording()

      recordJob = scope.launch {
        val shortBuf = ShortArray(minBuf / 2)
        val byteBuf = ByteArray(shortBuf.size * 2)
        var totalPcmBytes = 0

        RandomAccessFile(outFile, "rw").use { raf ->
          raf.seek(44L)
          while (isActive && _isRecording.value) {
            val read = recorder.read(shortBuf, 0, shortBuf.size)
            if (read > 0) {
              var sumSq = 0.0
              for (i in 0 until read) {
                val s = shortBuf[i].toInt()
                sumSq += (s * s).toDouble()
                byteBuf[i * 2] = (s and 0xFF).toByte()
                byteBuf[i * 2 + 1] = ((s ushr 8) and 0xFF).toByte()
              }
              raf.write(byteBuf, 0, read * 2)
              totalPcmBytes += read * 2

              val rms = sqrt(sumSq / read.toDouble()) / 32768.0
              val scaledLevel = (rms * 3.8).toFloat().coerceIn(0f, 1f)
              _level.value = _level.value * 0.35f + scaledLevel * 0.65f
              _elapsedMs.value = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
            }
          }
          raf.seek(0L)
          raf.write(createWavHeader(totalPcmBytes))
        }
      }

      outFile
    }
  }

  suspend fun stopRecording(): VoiceRecordingResult? = withContext(Dispatchers.IO) {
    if (!_isRecording.value) return@withContext null
    _isRecording.value = false
    runCatching { activeAudioRecord?.stop() }
    recordJob?.join()
    runCatching { activeAudioRecord?.release() }
    activeAudioRecord = null
    recordJob = null
    _level.value = 0f

    val file = activeFile ?: return@withContext null
    activeFile = null
    val duration = (file.length() - 44L).coerceAtLeast(0L) * 1000L / (SAMPLE_RATE * 2L)
    if (file.exists() && file.length() > 44L) {
      VoiceRecordingResult(file = file, durationMs = duration.coerceAtLeast(_elapsedMs.value))
    } else {
      runCatching { file.delete() }
      null
    }
  }

  fun cancelRecording() {
    _isRecording.value = false
    runCatching { activeAudioRecord?.stop() }
    recordJob?.cancel()
    runCatching { activeAudioRecord?.release() }
    activeAudioRecord = null
    recordJob = null
    activeFile?.let { runCatching { it.delete() } }
    activeFile = null
    _level.value = 0f
    _elapsedMs.value = 0L
  }

  private fun createWavHeader(pcmBytes: Int): ByteArray {
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(36 + pcmBytes)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16)
      putShort(1.toShort())
      putShort(1.toShort())
      putInt(SAMPLE_RATE)
      putInt(SAMPLE_RATE * 2)
      putShort(2.toShort())
      putShort(16.toShort())
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(pcmBytes)
    }.array()
  }
}
