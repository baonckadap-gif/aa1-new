package com.example.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object ReverseAudioCacheManager {

  private const val CACHE_DIR_NAME = "reverse_cache"
  private const val MAX_CACHE_BYTES = 150L * 1024L * 1024L

  fun getCacheDirectory(context: Context): File {
    return File(context.filesDir, CACHE_DIR_NAME).apply { mkdirs() }
  }

  fun buildCacheKey(uriString: String, trimStartMs: Long, trimEndMs: Long): String {
    val raw = "$uriString|$trimStartMs|$trimEndMs"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(32)
  }

  fun getCachedReversedFileIfReady(
    context: Context,
    uriString: String,
    trimStartMs: Long,
    trimEndMs: Long
  ): File? {
    val key = buildCacheKey(uriString, trimStartMs, trimEndMs)
    val file = File(getCacheDirectory(context), "rev_$key.wav")
    return if (file.exists() && file.length() > 44L) file else null
  }

  /**
   * Pure PCM-16 frame reversal utility (preserves L/R channel order per frame).
   */
  fun reversePcm16Frames(pcmBytes: ByteArray, channels: Int): ByteArray {
    val safeChannels = channels.coerceAtLeast(1)
    val frameSize = safeChannels * 2
    val totalFrames = pcmBytes.size / frameSize
    if (totalFrames <= 1) return pcmBytes.copyOf()

    val out = ByteArray(totalFrames * frameSize)
    for (f in 0 until totalFrames) {
      val srcOffset = (totalFrames - 1 - f) * frameSize
      val dstOffset = f * frameSize
      System.arraycopy(pcmBytes, srcOffset, out, dstOffset, frameSize)
    }
    return out
  }

  suspend fun getOrBuildReversedWav(
    context: Context,
    uriString: String,
    trimStartMs: Long,
    trimEndMs: Long
  ): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val existing = getCachedReversedFileIfReady(context, uriString, trimStartMs, trimEndMs)
      if (existing != null) {
        existing.setLastModified(System.currentTimeMillis())
        return@runCatching existing
      }

      val cacheDir = getCacheDirectory(context)
      val key = buildCacheKey(uriString, trimStartMs, trimEndMs)
      val tmpPcmFile = File(cacheDir, "tmp_$key.pcm")
      val partWavFile = File(cacheDir, "rev_$key.wav.part")
      val finalWavFile = File(cacheDir, "rev_$key.wav")

      try {
        val info = decodeUriToTempPcm16(
          context = context,
          sourceUri = Uri.parse(uriString),
          trimStartMs = trimStartMs,
          trimEndMs = trimEndMs,
          tempPcmFile = tmpPcmFile
        )

        val pcmLength = tmpPcmFile.length()
        require(pcmLength > 0L) { "Decoded audio buffer is empty" }

        val frameSize = info.channels.coerceAtLeast(1) * 2
        val alignedTotalBytes = (pcmLength / frameSize) * frameSize
        require(alignedTotalBytes > 0L) { "No valid PCM audio frames found" }

        writeReversedWavFromTempPcm(
          tempPcmFile = tmpPcmFile,
          alignedPcmBytes = alignedTotalBytes,
          sampleRate = info.sampleRate,
          channels = info.channels,
          targetWavFile = partWavFile
        )

        if (finalWavFile.exists()) {
          finalWavFile.delete()
        }
        check(partWavFile.renameTo(finalWavFile)) { "Failed to finalize reversed WAV cache file" }
        pruneOldCacheFiles(cacheDir, protectFile = finalWavFile)
        finalWavFile
      } finally {
        runCatching { if (tmpPcmFile.exists()) tmpPcmFile.delete() }
        runCatching { if (partWavFile.exists()) partWavFile.delete() }
      }
    }
  }

  private data class DecodedPcmInfo(val sampleRate: Int, val channels: Int)

  private fun decodeUriToTempPcm16(
    context: Context,
    sourceUri: Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    tempPcmFile: File
  ): DecodedPcmInfo {
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    var codec: MediaCodec? = null

    return try {
      if (sourceUri.scheme == "file" && sourceUri.path != null) {
        extractor.setDataSource(sourceUri.path!!)
      } else {
        pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
        if (pfd != null) {
          extractor.setDataSource(pfd.fileDescriptor)
        } else {
          extractor.setDataSource(context, sourceUri, null)
        }
      }

      var trackIdx = -1
      for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
        if (mime != null && mime.startsWith("audio/")) {
          trackIdx = i
          break
        }
      }
      require(trackIdx >= 0) { "No audio track found in file" }

      extractor.selectTrack(trackIdx)
      val inputFormat = extractor.getTrackFormat(trackIdx)
      val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio MIME")
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

      BufferedOutputStream(FileOutputStream(tempPcmFile), 65536).use { out ->
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
                  writeAsPcm16(outBuf, pcmEncoding, out)
                }
              }
              codec.releaseOutputBuffer(outIdx, false)
              if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                outputDone = true
              }
            }
          }
        }
        out.flush()
      }

      DecodedPcmInfo(sampleRate = sampleRate, channels = channels)
    } finally {
      runCatching { codec?.stop() }
      runCatching { codec?.release() }
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  private fun writeAsPcm16(buf: ByteBuffer, encoding: Int, out: BufferedOutputStream) {
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

  private fun writeReversedWavFromTempPcm(
    tempPcmFile: File,
    alignedPcmBytes: Long,
    sampleRate: Int,
    channels: Int,
    targetWavFile: File
  ) {
    val safeChannels = channels.coerceAtLeast(1)
    val frameSize = safeChannels * 2
    val blockFrames = 16384
    val blockBytes = blockFrames * frameSize
    val readBuf = ByteArray(blockBytes)
    val writeBuf = ByteArray(blockBytes)

    RandomAccessFile(tempPcmFile, "r").use { raf ->
      BufferedOutputStream(FileOutputStream(targetWavFile), 65536).use { out ->
        val header = buildWavHeader(alignedPcmBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), sampleRate, safeChannels)
        out.write(header)

        var cursor = alignedPcmBytes
        while (cursor > 0L) {
          val toRead = minOf(blockBytes.toLong(), cursor).toInt()
          val alignedToRead = (toRead / frameSize) * frameSize
          if (alignedToRead <= 0) break
          cursor -= alignedToRead.toLong()
          raf.seek(cursor)
          raf.readFully(readBuf, 0, alignedToRead)

          val framesInChunk = alignedToRead / frameSize
          for (f in 0 until framesInChunk) {
            val srcPos = (framesInChunk - 1 - f) * frameSize
            val dstPos = f * frameSize
            System.arraycopy(readBuf, srcPos, writeBuf, dstPos, frameSize)
          }
          out.write(writeBuf, 0, alignedToRead)
        }
        out.flush()
      }
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

  private fun pruneOldCacheFiles(cacheDir: File, protectFile: File) {
    val wavFiles = cacheDir.listFiles { f -> f.isFile && f.name.endsWith(".wav") }?.toList().orEmpty()
    var totalSize = wavFiles.sumOf { it.length() }
    if (totalSize <= MAX_CACHE_BYTES) return

    val sortedOldestFirst = wavFiles.filter { it.absolutePath != protectFile.absolutePath }
      .sortedBy { it.lastModified() }
    for (file in sortedOldestFirst) {
      if (totalSize <= MAX_CACHE_BYTES) break
      val len = file.length()
      if (file.delete()) {
        totalSize -= len
      }
    }
  }
}
