package com.example.data.demo

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.domain.model.PadSettings
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.SoundPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

object DemoTrackManager {

  const val DEMO_FOLDER_URI = "app://soundpad_demo_tracks"
  const val DEMO_FOLDER_NAME = "Sample Test Tracks"

  private const val SAMPLE_RATE = 44100

  private data class DemoSpec(
    val fileName: String,
    val displayName: String,
    val durationSec: Double,
    val colorArgb: Long,
    val glowColorArgb: Long,
    val isLoopRecommended: Boolean,
    val synthesizer: (t: Double) -> Double
  )

  private val demoSpecs: List<DemoSpec> = listOf(
    DemoSpec(
      fileName = "01_Kick_Bass_Beat.wav",
      displayName = "Kick & Bass Beat",
      durationSec = 4.0,
      colorArgb = 0xFF00E5FFL,
      glowColorArgb = 0xFF00E5FFL,
      isLoopRecommended = true
    ) { t ->
      val beatTime = t % 0.5 // 120 BPM
      val kickEnv = exp(-beatTime * 18.0)
      val kickFreq = 48.0 + 110.0 * exp(-beatTime * 28.0)
      val kick = sin(2.0 * PI * kickFreq * beatTime) * kickEnv * 0.85

      val bassStep = ((t * 4.0).toInt()) % 4
      val bassNotes = doubleArrayOf(55.0, 55.0, 65.41, 73.42)
      val bassFreq = bassNotes[bassStep]
      val subBeat = t % 0.25
      val bassEnv = exp(-subBeat * 6.0)
      val bass = tanh(sin(2.0 * PI * bassFreq * t) * 1.8) * bassEnv * 0.45

      val hatTime = (t + 0.25) % 0.5
      val hatEnv = exp(-hatTime * 45.0)
      val hat = sin(2.0 * PI * 6800.0 * t + sin(2.0 * PI * 3100.0 * t)) * hatEnv * 0.16

      (kick + bass + hat) * 0.72
    },
    DemoSpec(
      fileName = "02_Neon_Synth_Chords.wav",
      displayName = "Neon Synth Pad",
      durationSec = 5.0,
      colorArgb = 0xFF8B5CF6L,
      glowColorArgb = 0xFFC084FCL,
      isLoopRecommended = true
    ) { t ->
      val chordIndex = ((t / 1.25).toInt()) % 4
      val roots = doubleArrayOf(220.0, 174.61, 261.63, 196.0) // Am - F - C - G
      val root = roots[chordIndex]
      val localT = t % 1.25
      val env = (1.0 - exp(-localT * 10.0)) * exp(-localT * 0.7)
      val voice1 = sin(2.0 * PI * root * t)
      val voice2 = sin(2.0 * PI * (root * 1.25) * t) * 0.7
      val voice3 = sin(2.0 * PI * (root * 1.5) * t) * 0.65
      val shimmer = sin(2.0 * PI * (root * 2.005) * t) * 0.35
      ((voice1 + voice2 + voice3 + shimmer) / 2.7) * env * 0.75
    },
    DemoSpec(
      fileName = "03_Funk_Arp_Lead.wav",
      displayName = "Funk Arp Riff",
      durationSec = 4.0,
      colorArgb = 0xFFFFB300L,
      glowColorArgb = 0xFFFFD54FL,
      isLoopRecommended = true
    ) { t ->
      val scale = doubleArrayOf(261.63, 311.13, 349.23, 392.00, 466.16, 523.25, 392.00, 311.13)
      val stepLen = 0.25
      val idx = ((t / stepLen).toInt()) % scale.size
      val freq = scale[idx]
      val noteT = t % stepLen
      val env = (1.0 - exp(-noteT * 50.0)) * exp(-noteT * 8.0)
      val s1 = sin(2.0 * PI * freq * t)
      val s2 = sin(2.0 * PI * freq * 2.0 * t) * 0.45
      val s3 = sin(2.0 * PI * freq * 3.0 * t) * 0.2
      ((s1 + s2 + s3) / 1.65) * env * 0.75
    },
    DemoSpec(
      fileName = "04_Trap_808_Drop.wav",
      displayName = "Trap 808 Drop",
      durationSec = 3.2,
      colorArgb = 0xFFFF4D6DL,
      glowColorArgb = 0xFFFF758FL,
      isLoopRecommended = false
    ) { t ->
      val local = t % 0.8
      val glideFreq = 92.0 * exp(-local * 5.0) + 42.0
      val sub = tanh(sin(2.0 * PI * glideFreq * local) * 2.2) * exp(-local * 2.2)
      val clapTime = (t - 0.4).let { if (it < 0) 99.0 else it % 0.8 }
      val clap = sin(2.0 * PI * 1850.0 * t + sin(2.0 * PI * 740.0 * t)) * exp(-clapTime * 28.0) * 0.45
      (sub * 0.75 + clap) * 0.78
    },
    DemoSpec(
      fileName = "05_Airhorn_Stinger.wav",
      displayName = "Airhorn FX",
      durationSec = 2.0,
      colorArgb = 0xFFEC4899L,
      glowColorArgb = 0xFFF472B6L,
      isLoopRecommended = false
    ) { t ->
      val env = when {
        t < 0.18 -> exp(-((t - 0.02).coerceAtLeast(0.0)) * 5.0)
        t < 0.32 -> 0.0
        t < 0.50 -> exp(-((t - 0.34).coerceAtLeast(0.0)) * 5.0)
        t < 0.62 -> 0.0
        else -> (1.0 - exp(-(t - 0.62) * 35.0)) * exp(-(t - 0.62) * 1.9)
      }
      val pitchSweep = 466.16 * (1.0 - 0.03 * sin(2.0 * PI * 4.5 * t))
      val h1 = tanh(sin(2.0 * PI * pitchSweep * t) * 2.0)
      val h2 = tanh(sin(2.0 * PI * (pitchSweep * 1.498) * t) * 1.8) * 0.6
      ((h1 + h2) / 1.6) * env * 0.68
    },
    DemoSpec(
      fileName = "06_Chill_Rhodes_Loop.wav",
      displayName = "Chill Rhodes Loop",
      durationSec = 5.0,
      colorArgb = 0xFF10B981L,
      glowColorArgb = 0xFF34D399L,
      isLoopRecommended = true
    ) { t ->
      val bar = ((t / 1.25).toInt()) % 4
      val chord = when (bar) {
        0 -> doubleArrayOf(261.63, 329.63, 392.00, 493.88) // Cmaj7
        1 -> doubleArrayOf(220.00, 261.63, 329.63, 392.00) // Am7
        2 -> doubleArrayOf(174.61, 220.00, 261.63, 329.63) // Fmaj7
        else -> doubleArrayOf(196.00, 246.94, 293.66, 349.23) // G7
      }
      val local = t % 1.25
      val env = (1.0 - exp(-local * 32.0)) * exp(-local * 1.4)
      val tremolo = 0.85 + 0.15 * sin(2.0 * PI * 4.0 * t)
      var sum = 0.0
      for (f in chord) {
        sum += sin(2.0 * PI * f * t) + 0.25 * sin(2.0 * PI * f * 2.0 * t) * exp(-local * 6.0)
      }
      (sum / 4.2) * env * tremolo * 0.78
    }
  )

  fun getDemoDirectory(context: Context): File {
    return File(context.filesDir, "demo_tracks").apply { mkdirs() }
  }

  suspend fun ensureDemoTracksInstalled(context: Context): List<SoundPad> = withContext(Dispatchers.IO) {
    val dir = getDemoDirectory(context)
    val pads = mutableListOf<SoundPad>()

    demoSpecs.forEachIndexed { idx, spec ->
      val outFile = File(dir, spec.fileName)
      if (!outFile.exists() || outFile.length() <= 44L) {
        writeSynthesizedWav(outFile, spec.durationSec, spec.synthesizer)
      }
      val uri = Uri.fromFile(outFile)
      val durationMs = extractDuration(context, uri).takeIf { it > 0L }
        ?: (spec.durationSec * 1000.0).toLong()

      pads.add(
        SoundPad(
          id = stablePadId(uri.toString()),
          uriString = uri.toString(),
          fileName = spec.fileName,
          displayName = spec.displayName,
          extension = "WAV",
          durationMs = durationMs,
          sizeBytes = outFile.length(),
          positionIndex = idx,
          colorArgb = spec.colorArgb,
          glowColorArgb = spec.glowColorArgb,
          shapeStyle = null,
          isClone = false,
          isPreloadedInRam = false,
          settings = PadSettings(
            volume = 0.9f,
            isLooping = spec.isLoopRecommended,
            triggerMode = if (spec.isLoopRecommended) PadTriggerMode.LOOP else PadTriggerMode.ONE_SHOT
          )
        )
      )
    }

    pads
  }

  /**
   * Copies bundled sample WAV files into public Music/SoundPad Demo on Android 10+ (zero permission required)
   * so users can also browse them using Xiaomi File Manager or the SAF folder picker.
   */
  suspend fun exportDemoTracksToPhoneMusicFolder(context: Context): Int = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext 0
    val dir = getDemoDirectory(context)
    ensureDemoTracksInstalled(context)

    var exportedCount = 0
    val resolver = context.contentResolver
    val relativePath = "${Environment.DIRECTORY_MUSIC}/SoundPad Demo"

    for (spec in demoSpecs) {
      val srcFile = File(dir, spec.fileName)
      if (!srcFile.exists()) continue

      val alreadyExists = runCatching {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        val selection = "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf(spec.fileName, "%SoundPad Demo%")
        resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, args, null)?.use { c ->
          c.moveToFirst()
        } ?: false
      }.getOrDefault(false)

      if (alreadyExists) {
        exportedCount++
        continue
      }

      val values = ContentValues().apply {
        put(MediaStore.Audio.Media.DISPLAY_NAME, spec.fileName)
        put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
        put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
        put(MediaStore.Audio.Media.IS_MUSIC, 1)
        put(MediaStore.Audio.Media.IS_PENDING, 1)
      }

      val itemUri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: continue
      runCatching {
        resolver.openOutputStream(itemUri)?.use { out ->
          srcFile.inputStream().use { input -> input.copyTo(out) }
        }
        val doneValues = ContentValues().apply {
          put(MediaStore.Audio.Media.IS_PENDING, 0)
        }
        resolver.update(itemUri, doneValues, null, null)
        exportedCount++
      }.onFailure {
        runCatching { resolver.delete(itemUri, null, null) }
      }
    }

    exportedCount
  }

  private fun writeSynthesizedWav(
    outFile: File,
    durationSec: Double,
    synthesizer: (t: Double) -> Double
  ) {
    val totalSamples = (SAMPLE_RATE * durationSec).toInt().coerceAtLeast(1)
    val dataByteCount = totalSamples * 2 // 16-bit mono
    val totalFileLength = 36 + dataByteCount

    BufferedOutputStream(FileOutputStream(outFile)).use { out ->
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(totalFileLength)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16) // PCM chunk size
        putShort(1.toShort()) // AudioFormat = 1 (PCM)
        putShort(1.toShort()) // Channels = 1 (Mono)
        putInt(SAMPLE_RATE)
        putInt(SAMPLE_RATE * 2) // ByteRate
        putShort(2.toShort()) // BlockAlign
        putShort(16.toShort()) // BitsPerSample
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(dataByteCount)
      }
      out.write(header.array())

      val chunkSamples = 2048
      val byteBuffer = ByteArray(chunkSamples * 2)
      var sampleIndex = 0
      while (sampleIndex < totalSamples) {
        val count = minOf(chunkSamples, totalSamples - sampleIndex)
        for (i in 0 until count) {
          val globalIdx = sampleIndex + i
          val t = globalIdx.toDouble() / SAMPLE_RATE.toDouble()
          // Smooth 8ms fade at start and end to avoid clicks
          val edgeFade = minOf(
            (globalIdx / (SAMPLE_RATE * 0.008)).coerceIn(0.0, 1.0),
            ((totalSamples - 1 - globalIdx) / (SAMPLE_RATE * 0.008)).coerceIn(0.0, 1.0)
          )
          val raw = (synthesizer(t) * edgeFade).coerceIn(-0.98, 0.98)
          val pcm = (raw * 32767.0).toInt().toShort()
          val bIdx = i * 2
          byteBuffer[bIdx] = (pcm.toInt() and 0xFF).toByte()
          byteBuffer[bIdx + 1] = ((pcm.toInt() ushr 8) and 0xFF).toByte()
        }
        out.write(byteBuffer, 0, count * 2)
        sampleIndex += count
      }
    }
  }

  private fun extractDuration(context: Context, uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
      0L
    } finally {
      runCatching { retriever.release() }
    }
  }

  fun stablePadId(uriString: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray())
    return bytes.take(12).joinToString("") { "%02x".format(it) }
  }
}
