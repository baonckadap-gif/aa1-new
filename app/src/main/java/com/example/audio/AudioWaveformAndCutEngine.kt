package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.domain.model.WaveformData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

object AudioWaveformAndCutEngine {

  suspend fun extractWaveform(
    context: Context,
    uriString: String,
    barCount: Int = 110
  ): WaveformData = withContext(Dispatchers.IO) {
    val uri = Uri.parse(uriString)
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    var codec: MediaCodec? = null

    try {
      pfd = setExtractorDataSource(context, extractor, uri)
      val trackIdx = findAudioTrackIndex(extractor)
      if (trackIdx < 0) return@withContext fallbackWaveform(barCount, 0L)

      extractor.selectTrack(trackIdx)
      val format = extractor.getTrackFormat(trackIdx)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext fallbackWaveform(barCount, 0L)
      val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
        format.getLong(MediaFormat.KEY_DURATION)
      } else {
        0L
      }
      val durationMs = (durationUs / 1000L).coerceAtLeast(0L)

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val bins = FloatArray(barCount.coerceAtLeast(16))
      var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
      var inputDone = false
      var outputDone = false
      val bufferInfo = MediaCodec.BufferInfo()
      val safeDurationUs = durationUs.coerceAtLeast(1_000_000L)

      while (!outputDone) {
        if (!inputDone) {
          val inIdx = codec.dequeueInputBuffer(10_000)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
            if (sampleSize < 0) {
              codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              val pts = extractor.sampleTime.coerceAtLeast(0L)
              codec.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
              extractor.advance()
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
        when {
          outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = codec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
          }
          outIdx >= 0 -> {
            val outBuf = codec.getOutputBuffer(outIdx)
            if (outBuf != null && bufferInfo.size > 0) {
              outBuf.position(bufferInfo.offset)
              outBuf.limit(bufferInfo.offset + bufferInfo.size)
              val peak = computeBufferPeak(outBuf, pcmEncoding)
              val binIdx = (((bufferInfo.presentationTimeUs.toDouble() / safeDurationUs.toDouble()) * bins.size).toInt())
                .coerceIn(0, bins.lastIndex)
              if (peak > bins[binIdx]) {
                bins[binIdx] = peak
              }
            }
            codec.releaseOutputBuffer(outIdx, false)
            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              outputDone = true
            }
          }
        }
      }

      val maxPeak = bins.maxOrNull()?.takeIf { it > 0.001f } ?: 1f
      val normalized = bins.map { (it / maxPeak).coerceIn(0.06f, 1.0f) }
      WaveformData(amplitudes = normalized, durationMs = durationMs)
    } catch (_: Exception) {
      fallbackWaveform(barCount, 0L)
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  /**
   * Fast mini-waveform extractor optimized for pad cell previews (24 bars).
   * Uses seek-sampling on longer tracks so scanning dozens of songs stays snappy.
   */
  suspend fun extractMiniWaveform(
    context: Context,
    uriString: String,
    barCount: Int = 24
  ): List<Float> = withContext(Dispatchers.IO) {
    val uri = Uri.parse(uriString)
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    var codec: MediaCodec? = null
    val safeBars = barCount.coerceIn(12, 36)

    try {
      pfd = setExtractorDataSource(context, extractor, uri)
      val trackIdx = findAudioTrackIndex(extractor)
      if (trackIdx < 0) return@withContext emptyList()

      extractor.selectTrack(trackIdx)
      val format = extractor.getTrackFormat(trackIdx)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
      val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
        format.getLong(MediaFormat.KEY_DURATION)
      } else {
        0L
      }

      if (durationUs <= 8_000_000L) {
        return@withContext extractWaveform(context, uriString, safeBars).amplitudes
      }

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      val bins = FloatArray(safeBars)
      var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
      val info = MediaCodec.BufferInfo()

      for (b in 0 until safeBars) {
        val targetUs = ((b.toDouble() + 0.5) / safeBars.toDouble() * durationUs.toDouble()).toLong()
        extractor.seekTo(targetUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        runCatching { codec.flush() }

        var reads = 0
        var gotOutput = false
        while (reads < 6 && !gotOutput) {
          val inIdx = codec.dequeueInputBuffer(5_000)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
            if (sampleSize < 0) {
              codec.queueInputBuffer(inIdx, 0, 0, 0L, 0)
              break
            } else {
              codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime.coerceAtLeast(0L), 0)
              extractor.advance()
              reads++
            }
          }
          val outIdx = codec.dequeueOutputBuffer(info, 5_000)
          when {
            outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
              val outFmt = codec.outputFormat
              if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
              }
            }
            outIdx >= 0 -> {
              val outBuf = codec.getOutputBuffer(outIdx)
              if (outBuf != null && info.size > 0) {
                outBuf.position(info.offset)
                outBuf.limit(info.offset + info.size)
                val peak = computeBufferPeak(outBuf, pcmEncoding)
                if (peak > bins[b]) bins[b] = peak
                gotOutput = true
              }
              codec.releaseOutputBuffer(outIdx, false)
            }
          }
        }
      }

      val maxPeak = bins.maxOrNull()?.takeIf { it > 0.001f } ?: return@withContext emptyList()
      bins.map { (it / maxPeak).coerceIn(0.08f, 1.0f) }
    } catch (_: Exception) {
      emptyList()
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  suspend fun computeLoudnessGain(
    context: Context,
    uriString: String
  ): Float = withContext(Dispatchers.IO) {
    val uri = Uri.parse(uriString)
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    var codec: MediaCodec? = null

    try {
      pfd = setExtractorDataSource(context, extractor, uri)
      val trackIdx = findAudioTrackIndex(extractor)
      if (trackIdx < 0) return@withContext 1.0f

      extractor.selectTrack(trackIdx)
      val format = extractor.getTrackFormat(trackIdx)
      val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext 1.0f

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(format, null, null, 0)
      codec.start()

      var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
      var inputDone = false
      var outputDone = false
      val info = MediaCodec.BufferInfo()
      var sumSquares = 0.0
      var sampleCount = 0L
      var peak = 0f
      val maxAnalyzeSamples = 44100L * 20L // Analyze up to ~20s for fast normalization

      while (!outputDone && sampleCount < maxAnalyzeSamples) {
        if (!inputDone) {
          val inIdx = codec.dequeueInputBuffer(10_000)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
            if (size < 0) {
              codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
              extractor.advance()
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
        when {
          outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = codec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
          }
          outIdx >= 0 -> {
            val outBuf = codec.getOutputBuffer(outIdx)
            if (outBuf != null && info.size > 0) {
              outBuf.position(info.offset)
              outBuf.limit(info.offset + info.size)
              val dup = outBuf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
              if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                val floats = dup.asFloatBuffer()
                while (floats.hasRemaining()) {
                  val s = floats.get().coerceIn(-1f, 1f)
                  sumSquares += s * s
                  sampleCount++
                  val a = abs(s)
                  if (a > peak) peak = a
                }
              } else {
                val shorts = dup.asShortBuffer()
                while (shorts.hasRemaining()) {
                  val s = shorts.get().toFloat() / 32768f
                  sumSquares += s * s
                  sampleCount++
                  val a = abs(s)
                  if (a > peak) peak = a
                }
              }
            }
            codec.releaseOutputBuffer(outIdx, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              outputDone = true
            }
          }
        }
      }

      if (sampleCount == 0L || sumSquares <= 0.0) return@withContext 1.0f
      val rms = kotlin.math.sqrt(sumSquares / sampleCount.toDouble()).toFloat()
      if (rms < 0.002f) return@withContext 1.0f

      val targetRms = 0.22f // ~ -13 dBFS target perceived loudness
      val desiredGain = (targetRms / rms).coerceIn(0.40f, 1.60f)
      val ceilingGain = if (peak > 0.05f) (0.96f / peak).coerceAtMost(1.60f) else 1.60f
      minOf(desiredGain, ceilingGain).coerceIn(0.40f, 1.60f)
    } catch (_: Exception) {
      1.0f
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  suspend fun exportTrimmedToFile(
    context: Context,
    sourceUriString: String,
    trimStartMs: Long,
    trimEndMs: Long,
    targetFile: File
  ): Result<Long> = withContext(Dispatchers.IO) {
    runCatching {
      FileOutputStream(targetFile).use { out ->
        decodeTrimmedToWavStream(context, Uri.parse(sourceUriString), trimStartMs, trimEndMs, out)
      }
    }
  }

  suspend fun exportTrimmedToUri(
    context: Context,
    sourceUriString: String,
    trimStartMs: Long,
    trimEndMs: Long,
    targetUri: Uri
  ): Result<Long> = withContext(Dispatchers.IO) {
    runCatching {
      val stream = context.contentResolver.openOutputStream(targetUri)
        ?: error("Unable to open output stream for export")
      stream.use { out ->
        decodeTrimmedToWavStream(context, Uri.parse(sourceUriString), trimStartMs, trimEndMs, out)
      }
    }
  }

  private fun decodeTrimmedToWavStream(
    context: Context,
    sourceUri: Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    output: OutputStream
  ): Long {
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    var codec: MediaCodec? = null

    return try {
      pfd = setExtractorDataSource(context, extractor, sourceUri)
      val trackIdx = findAudioTrackIndex(extractor)
      require(trackIdx >= 0) { "No audio track found in source file" }

      extractor.selectTrack(trackIdx)
      val inputFormat = extractor.getTrackFormat(trackIdx)
      val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME type")
      val sourceDurationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
        inputFormat.getLong(MediaFormat.KEY_DURATION)
      } else {
        0L
      }

      val startUs = (trimStartMs * 1000L).coerceAtLeast(0L)
      val endUs = if (trimEndMs > trimStartMs) {
        trimEndMs * 1000L
      } else if (sourceDurationUs > 0L) {
        sourceDurationUs
      } else {
        Long.MAX_VALUE
      }

      if (startUs > 0L) {
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      }

      var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
        inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
      } else {
        44100
      }
      var channels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
      } else {
        1
      }
      var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

      codec = MediaCodec.createDecoderByType(mime)
      codec.configure(inputFormat, null, null, 0)
      codec.start()

      val pcmOut = ByteArrayOutputStream()
      val info = MediaCodec.BufferInfo()
      var inputDone = false
      var outputDone = false

      while (!outputDone) {
        if (!inputDone) {
          val inIdx = codec.dequeueInputBuffer(10_000)
          if (inIdx >= 0) {
            val inBuf = codec.getInputBuffer(inIdx)
            val sampleSize = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
            val sampleTime = extractor.sampleTime
            if (sampleSize < 0 || (endUs < Long.MAX_VALUE && sampleTime > endUs + 150_000L)) {
              codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputDone = true
            } else {
              codec.queueInputBuffer(inIdx, 0, sampleSize, sampleTime.coerceAtLeast(0L), 0)
              extractor.advance()
            }
          }
        }

        val outIdx = codec.dequeueOutputBuffer(info, 10_000)
        when {
          outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
            val outFmt = codec.outputFormat
            if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
              sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }
            if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
              channels = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
              pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
            }
          }
          outIdx >= 0 -> {
            val outBuf = codec.getOutputBuffer(outIdx)
            if (outBuf != null && info.size > 0) {
              val pts = info.presentationTimeUs
              if (pts in startUs..endUs) {
                outBuf.position(info.offset)
                outBuf.limit(info.offset + info.size)
                appendAsPcm16(outBuf, pcmEncoding, pcmOut)
              }
            }
            codec.releaseOutputBuffer(outIdx, false)
            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
              outputDone = true
            }
          }
        }
      }

      val pcmBytes = pcmOut.toByteArray()
      val header = buildWavHeader(pcmBytes.size, sampleRate, channels)
      output.write(header)
      output.write(pcmBytes)
      output.flush()

      val bytesPerSec = sampleRate * channels * 2L
      if (bytesPerSec > 0L) (pcmBytes.size * 1000L) / bytesPerSec else 0L
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  private fun setExtractorDataSource(
    context: Context,
    extractor: MediaExtractor,
    uri: Uri
  ): ParcelFileDescriptor? {
    if (uri.scheme == "file" && uri.path != null) {
      extractor.setDataSource(uri.path!!)
      return null
    }
    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
    if (pfd != null) {
      extractor.setDataSource(pfd.fileDescriptor)
      return pfd
    }
    extractor.setDataSource(context, uri, null)
    return null
  }

  private fun findAudioTrackIndex(extractor: MediaExtractor): Int {
    for (i in 0 until extractor.trackCount) {
      val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
      if (mime != null && mime.startsWith("audio/")) return i
    }
    return -1
  }

  private fun computeBufferPeak(buf: ByteBuffer, encoding: Int): Float {
    val dup = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    var maxAmp = 0f
    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
      val floats = dup.asFloatBuffer()
      while (floats.hasRemaining()) {
        val v = abs(floats.get()).coerceIn(0f, 1f)
        if (v > maxAmp) maxAmp = v
      }
    } else {
      val shorts = dup.asShortBuffer()
      while (shorts.hasRemaining()) {
        val v = abs(shorts.get().toInt()) / 32768f
        if (v > maxAmp) maxAmp = v
      }
    }
    return maxAmp
  }

  private fun appendAsPcm16(buf: ByteBuffer, encoding: Int, out: ByteArrayOutputStream) {
    val dup = buf.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
      val floats = dup.asFloatBuffer()
      while (floats.hasRemaining()) {
        val sample = (floats.get().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
        out.write(sample.toInt() and 0xFF)
        out.write((sample.toInt() ushr 8) and 0xFF)
      }
    } else {
      val chunk = ByteArray(dup.remaining())
      dup.get(chunk)
      out.write(chunk)
    }
  }

  private fun buildWavHeader(pcmDataSize: Int, sampleRate: Int, channels: Int): ByteArray {
    val byteRate = sampleRate * channels * 2
    val blockAlign = (channels * 2).toShort()
    return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
      put("RIFF".toByteArray(Charsets.US_ASCII))
      putInt(36 + pcmDataSize)
      put("WAVE".toByteArray(Charsets.US_ASCII))
      put("fmt ".toByteArray(Charsets.US_ASCII))
      putInt(16)
      putShort(1.toShort())
      putShort(channels.toShort())
      putInt(sampleRate)
      putInt(byteRate)
      putShort(blockAlign)
      putShort(16.toShort())
      put("data".toByteArray(Charsets.US_ASCII))
      putInt(pcmDataSize)
    }.array()
  }

  private fun fallbackWaveform(barCount: Int, durationMs: Long): WaveformData {
    val list = List(barCount.coerceAtLeast(16)) { idx ->
      val phase = idx / 6.0
      (0.28f + 0.45f * abs(kotlin.math.sin(phase)).toFloat()).coerceIn(0.1f, 0.95f)
    }
    return WaveformData(amplitudes = list, durationMs = durationMs)
  }
}
