package com.example.audio

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.domain.model.PadErrorType
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

data class AudioValidationResult(
  val isValid: Boolean,
  val errorType: PadErrorType? = null,
  val message: String? = null,
  val suggestion: String? = null
)

object AudioFileValidator {

  fun validateHeaderBytes(header: ByteArray, totalSizeBytes: Long, extension: String): AudioValidationResult {
    val ext = extension.lowercase().trim()
    if (totalSizeBytes in 1L..43L || header.size < 12) {
      return AudioValidationResult(
        isValid = false,
        errorType = PadErrorType.CORRUPT_HEADER,
        message = "File terlalu kecil atau header .$ext terpotong (${totalSizeBytes} byte).",
        suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
      )
    }

    return when (ext) {
      "wav" -> {
        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val wave = String(header, 8, 4, Charsets.US_ASCII)
        if (riff != "RIFF" || wave != "WAVE") {
          AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.CORRUPT_HEADER,
            message = "Header WAV tidak valid (diharapkan RIFF/WAVE, ditemukan '$riff/$wave').",
            suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
          )
        } else {
          AudioValidationResult(isValid = true)
        }
      }
      "ogg" -> {
        val ogg = String(header, 0, 4, Charsets.US_ASCII)
        if (ogg != "OggS") {
          AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.CORRUPT_HEADER,
            message = "Header OGG tidak valid (diharapkan OggS, ditemukan '$ogg').",
            suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
          )
        } else {
          AudioValidationResult(isValid = true)
        }
      }
      "flac" -> {
        val flac = String(header, 0, 4, Charsets.US_ASCII)
        if (flac != "fLaC") {
          AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.CORRUPT_HEADER,
            message = "Header FLAC tidak valid (diharapkan fLaC, ditemukan '$flac').",
            suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
          )
        } else {
          AudioValidationResult(isValid = true)
        }
      }
      "m4a", "mp4", "aac" -> {
        val box = if (header.size >= 8) String(header, 4, 4, Charsets.US_ASCII) else ""
        if (box != "ftyp") {
          AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.CORRUPT_HEADER,
            message = "Container M4A/MP4 tidak valid (signature 'ftyp' tidak ditemukan).",
            suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
          )
        } else {
          AudioValidationResult(isValid = true)
        }
      }
      "mp3" -> {
        val hasId3 = header.size >= 3 && String(header, 0, 3, Charsets.US_ASCII) == "ID3"
        val hasMpegSync = hasMpegAudioFrameSync(header)
        if (!hasId3 && !hasMpegSync) {
          AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.CORRUPT_HEADER,
            message = "Header MP3 tidak valid (tidak ditemukan tag ID3 maupun frame sync MPEG).",
            suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
          )
        } else {
          AudioValidationResult(isValid = true)
        }
      }
      else -> AudioValidationResult(
        isValid = false,
        errorType = PadErrorType.UNSUPPORTED_FORMAT,
        message = "Format .$ext tidak didukung oleh SoundPad Pro.",
        suggestion = PadErrorType.UNSUPPORTED_FORMAT.defaultSuggestion
      )
    }
  }

  private fun hasMpegAudioFrameSync(bytes: ByteArray): Boolean {
    val limit = (bytes.size - 1).coerceAtMost(2048)
    for (i in 0 until limit) {
      val b0 = bytes[i].toInt() and 0xFF
      val b1 = bytes[i + 1].toInt() and 0xFF
      if (b0 == 0xFF && (b1 and 0xE0) == 0xE0) {
        return true
      }
    }
    return false
  }

  fun validateAudioUri(
    context: Context,
    uri: Uri,
    fileName: String,
    extension: String,
    knownSizeBytes: Long = 0L
  ): AudioValidationResult {
    // 1. Read header bytes safely & check permissions
    val headerResult = readHeaderSafely(context, uri, knownSizeBytes)
    if (headerResult.error != null) {
      return headerResult.error
    }

    val headerCheck = validateHeaderBytes(
      header = headerResult.header,
      totalSizeBytes = headerResult.sizeBytes,
      extension = extension
    )
    if (!headerCheck.isValid) {
      return headerCheck
    }

    // 2. Probe MediaExtractor for DRM / encryption & valid audio track
    val extractor = MediaExtractor()
    var pfd: ParcelFileDescriptor? = null
    return try {
      if (uri.scheme == "file") {
        val path = uri.path ?: return AudioValidationResult(
          isValid = false,
          errorType = PadErrorType.MISSING_PERMISSION,
          message = "Path file lokal tidak valid untuk \"$fileName\".",
          suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
        )
        extractor.setDataSource(path)
      } else {
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
          ?: return AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.MISSING_PERMISSION,
            message = "Tidak dapat membuka file \"$fileName\" melalui SAF.",
            suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
          )
        extractor.setDataSource(pfd.fileDescriptor)
      }

      // Check PSSH DRM metadata if present
      val psshInfo = runCatching { extractor.psshInfo }.getOrNull()
      if (psshInfo != null && psshInfo.isNotEmpty()) {
        return AudioValidationResult(
          isValid = false,
          errorType = PadErrorType.DRM_PROTECTED,
          message = "File \"$fileName\" mengandung proteksi DRM (PSSH).",
          suggestion = PadErrorType.DRM_PROTECTED.defaultSuggestion
        )
      }

      var audioTrackIdx = -1
      for (i in 0 until extractor.trackCount) {
        val fmt = extractor.getTrackFormat(i)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
        if (mime.startsWith("audio/") || mime.contains("drm", ignoreCase = true)) {
          audioTrackIdx = i
          val isSampleEncrypted = runCatching {
            extractor.selectTrack(i)
            (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0
          }.getOrDefault(false)
          val hasCrypto = fmt.containsKey("crypto-mode") ||
            fmt.containsKey("crypto-key") ||
            isSampleEncrypted ||
            mime.contains("drm", ignoreCase = true) ||
            mime.contains("encrypted", ignoreCase = true)
          if (hasCrypto) {
            return AudioValidationResult(
              isValid = false,
              errorType = PadErrorType.DRM_PROTECTED,
              message = "Track audio pada \"$fileName\" terenkripsi / dilindungi DRM.",
              suggestion = PadErrorType.DRM_PROTECTED.defaultSuggestion
            )
          }
          break
        }
      }

      if (audioTrackIdx < 0) {
        return AudioValidationResult(
          isValid = false,
          errorType = PadErrorType.UNSUPPORTED_FORMAT,
          message = "Tidak ada stream audio yang valid di dalam \"$fileName\".",
          suggestion = PadErrorType.UNSUPPORTED_FORMAT.defaultSuggestion
        )
      }

      AudioValidationResult(isValid = true)
    } catch (sec: SecurityException) {
      AudioValidationResult(
        isValid = false,
        errorType = PadErrorType.MISSING_PERMISSION,
        message = "Izin baca SAF ditolak untuk \"$fileName\": ${sec.localizedMessage ?: "Permission denied"}",
        suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
      )
    } catch (e: Exception) {
      AudioValidationResult(
        isValid = false,
        errorType = PadErrorType.CORRUPT_HEADER,
        message = "Gagal membaca struktur audio \"$fileName\": ${e.localizedMessage ?: e.javaClass.simpleName}",
        suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
      )
    } finally {
      runCatching { extractor.release() }
      runCatching { pfd?.close() }
    }
  }

  private data class HeaderReadResult(
    val header: ByteArray = ByteArray(0),
    val sizeBytes: Long = 0L,
    val error: AudioValidationResult? = null
  )

  private fun readHeaderSafely(context: Context, uri: Uri, knownSizeBytes: Long): HeaderReadResult {
    return try {
      val stream: InputStream?
      val resolvedSize: Long
      if (uri.scheme == "file") {
        val file = File(uri.path ?: "")
        if (!file.exists()) {
          return HeaderReadResult(
            error = AudioValidationResult(
              isValid = false,
              errorType = PadErrorType.MISSING_PERMISSION,
              message = "File tidak ditemukan di penyimpanan aplikasi.",
              suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
            )
          )
        }
        resolvedSize = file.length()
        stream = file.inputStream()
      } else {
        resolvedSize = knownSizeBytes
        stream = context.contentResolver.openInputStream(uri)
      }

      if (stream == null) {
        return HeaderReadResult(
          error = AudioValidationResult(
            isValid = false,
            errorType = PadErrorType.MISSING_PERMISSION,
            message = "Stream file tidak dapat dibuka (izin SAF mungkin hilang).",
            suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
          )
        )
      }

      val buf = ByteArray(2048)
      val bytesRead = stream.use { input ->
        var total = 0
        while (total < buf.size) {
          val n = input.read(buf, total, buf.size - total)
          if (n <= 0) break
          total += n
        }
        total
      }
      HeaderReadResult(
        header = buf.copyOf(bytesRead),
        sizeBytes = if (resolvedSize > 0L) resolvedSize else bytesRead.toLong()
      )
    } catch (sec: SecurityException) {
      HeaderReadResult(
        error = AudioValidationResult(
          isValid = false,
          errorType = PadErrorType.MISSING_PERMISSION,
          message = "Izin SAF hilang atau kedaluwarsa: ${sec.localizedMessage ?: "SecurityException"}",
          suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
        )
      )
    } catch (e: Exception) {
      HeaderReadResult(
        error = AudioValidationResult(
          isValid = false,
          errorType = PadErrorType.CORRUPT_HEADER,
          message = "Tidak dapat membaca header file: ${e.localizedMessage ?: e.javaClass.simpleName}",
          suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion
        )
      )
    }
  }
}
