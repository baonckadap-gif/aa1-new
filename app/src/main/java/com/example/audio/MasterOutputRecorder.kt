package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.domain.model.MasterRecordFormat
import com.example.domain.model.MasterRecordSampleRate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Pure PCM mixing & RIFF WAV encoding helpers for internal master mix recording.
 */
object MasterMixPcmMath {
  fun createWavHeader(dataSizeBytes: Int, sampleRateHz: Int, channels: Int = 2, bitsPerSample: Int = 16): ByteArray {
    val byteRate = sampleRateHz * channels * (bitsPerSample / 8)
    val blockAlign = channels * (bitsPerSample / 8)
    val totalDataLen = dataSizeBytes + 36
    return ByteArray(44).apply {
      this[0] = 'R'.code.toByte(); this[1] = 'I'.code.toByte(); this[2] = 'F'.code.toByte(); this[3] = 'F'.code.toByte()
      this[4] = (totalDataLen and 0xff).toByte()
      this[5] = ((totalDataLen shr 8) and 0xff).toByte()
      this[6] = ((totalDataLen shr 16) and 0xff).toByte()
      this[7] = ((totalDataLen shr 24) and 0xff).toByte()
      this[8] = 'W'.code.toByte(); this[9] = 'A'.code.toByte(); this[10] = 'V'.code.toByte(); this[11] = 'E'.code.toByte()
      this[12] = 'f'.code.toByte(); this[13] = 'm'.code.toByte(); this[14] = 't'.code.toByte(); this[15] = ' '.code.toByte()
      this[16] = 16; this[17] = 0; this[18] = 0; this[19] = 0
      this[20] = 1; this[21] = 0
      this[22] = channels.toByte(); this[23] = 0
      this[24] = (sampleRateHz and 0xff).toByte()
      this[25] = ((sampleRateHz shr 8) and 0xff).toByte()
      this[26] = ((sampleRateHz shr 16) and 0xff).toByte()
      this[27] = ((sampleRateHz shr 24) and 0xff).toByte()
      this[28] = (byteRate and 0xff).toByte()
      this[29] = ((byteRate shr 8) and 0xff).toByte()
      this[30] = ((byteRate shr 16) and 0xff).toByte()
      this[31] = ((byteRate shr 24) and 0xff).toByte()
      this[32] = blockAlign.toByte(); this[33] = 0
      this[34] = bitsPerSample.toByte(); this[35] = 0
      this[36] = 'd'.code.toByte(); this[37] = 'a'.code.toByte(); this[38] = 't'.code.toByte(); this[39] = 'a'.code.toByte()
      this[40] = (dataSizeBytes and 0xff).toByte()
      this[41] = ((dataSizeBytes shr 8) and 0xff).toByte()
      this[42] = ((dataSizeBytes shr 16) and 0xff).toByte()
      this[43] = ((dataSizeBytes shr 24) and 0xff).toByte()
    }
  }

  /**
   * Resamples interleaved stereo float samples from [srcRate] to [dstRate] using linear interpolation.
   */
  fun resampleStereo(interleavedStereo: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
    val srcFrames = interleavedStereo.size / 2
    if (srcFrames <= 0) return FloatArray(0)
    if (srcRate == dstRate || srcRate <= 0 || dstRate <= 0) return interleavedStereo

    val ratio = srcRate.toDouble() / dstRate.toDouble()
    val dstFrames = ((srcFrames.toDouble() / ratio).toInt()).coerceAtLeast(1)
    val out = FloatArray(dstFrames * 2)
    for (df in 0 until dstFrames) {
      val srcPos = df * ratio
      val idx0 = srcPos.toInt().coerceIn(0, srcFrames - 1)
      val idx1 = (idx0 + 1).coerceIn(0, srcFrames - 1)
      val frac = (srcPos - idx0).toFloat()
      val l0 = interleavedStereo[idx0 * 2]
      val r0 = interleavedStereo[idx0 * 2 + 1]
      val l1 = interleavedStereo[idx1 * 2]
      val r1 = interleavedStereo[idx1 * 2 + 1]
      out[df * 2] = l0 + (l1 - l0) * frac
      out[df * 2 + 1] = r0 + (r1 - r0) * frac
    }
    return out
  }

  fun mixAndSoftLimitToPcm16(
    slotChunks: List<FloatArray>,
    framesToWrite: Int,
    outBytes: ByteArray
  ): Float {
    var sumSquares = 0.0
    val sampleCount = framesToWrite * 2
    for (i in 0 until sampleCount) {
      var mixed = 0.0
      for (chunk in slotChunks) {
        if (i < chunk.size) {
          mixed += chunk[i].toDouble()
        }
      }
      val limited = if (abs(mixed) > 0.88) tanh(mixed * 0.95) else mixed
      sumSquares += limited * limited
      val s16 = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
      val byteIdx = i * 2
      outBytes[byteIdx] = (s16 and 0xFF).toByte()
      outBytes[byteIdx + 1] = ((s16 shr 8) and 0xFF).toByte()
    }
    val rms = sqrt((sumSquares / sampleCount.coerceAtLeast(1)).coerceAtLeast(0.0))
    return (rms.toFloat() * 2.4f).coerceIn(0f, 1f)
  }
}

/**
 * Thread-safe bounded ring buffer holding resampled stereo floats for a single active player slot.
 */
internal class SlotPcmRingBuffer(capacitySamples: Int = 48_000 * 2) {
  private val buffer = FloatArray(capacitySamples)
  private var readIdx = 0
  private var writeIdx = 0
  private var size = 0

  @Synchronized
  fun push(samples: FloatArray) {
    if (samples.isEmpty()) return
    for (s in samples) {
      if (size == buffer.size) {
        readIdx = (readIdx + 1) % buffer.size
        size--
      }
      buffer[writeIdx] = s
      writeIdx = (writeIdx + 1) % buffer.size
      size++
    }
  }

  @Synchronized
  fun drainExactOrLess(requestedSamples: Int): FloatArray? {
    if (size <= 0) return null
    val take = requestedSamples.coerceAtMost(size)
    val out = FloatArray(take)
    for (i in 0 until take) {
      out[i] = buffer[readIdx]
      readIdx = (readIdx + 1) % buffer.size
    }
    size -= take
    return out
  }

  @Synchronized
  fun clear() {
    readIdx = 0
    writeIdx = 0
    size = 0
  }
}

/**
 * Transparent Media3 AudioProcessor attached at the tail of each ExoPlayer sink chain.
 * Copies post-EQ/FX/Pan audio scaled by slot gain into [MasterOutputRecorder] without altering playback.
 */
@UnstableApi
class MasterTapAudioProcessor(
  private val slotId: String,
  private val recorder: MasterOutputRecorder
) : BaseAudioProcessor() {

  @Volatile
  private var currentSlotGain: Float = 1.0f

  private var configuredSampleRate: Int = 44100
  private var configuredChannels: Int = 2

  fun setGain(gain: Float) {
    currentSlotGain = gain.coerceIn(0f, 1.5f)
  }

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
      inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
    val supportedChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
    if (!supportedEncoding || !supportedChannels) {
      return AudioProcessor.AudioFormat.NOT_SET
    }
    configuredSampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
    configuredChannels = inputAudioFormat.channelCount
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    if (recorder.isActivelyRecording()) {
      val dup = inputBuffer.asReadOnlyBuffer().order(inputBuffer.order())
      val encoding = inputAudioFormat.encoding
      val channels = configuredChannels
      val gain = currentSlotGain
      val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
      val frameCount = remaining / (channels * bytesPerSample)
      if (frameCount > 0) {
        val stereoFloats = FloatArray(frameCount * 2)
        if (encoding == C.ENCODING_PCM_16BIT) {
          for (f in 0 until frameCount) {
            val l = (dup.short.toFloat() / 32768f) * gain
            val r = if (channels == 2) (dup.short.toFloat() / 32768f) * gain else l
            stereoFloats[f * 2] = l
            stereoFloats[f * 2 + 1] = r
          }
        } else {
          for (f in 0 until frameCount) {
            val l = dup.float * gain
            val r = if (channels == 2) dup.float * gain else l
            stereoFloats[f * 2] = l
            stereoFloats[f * 2 + 1] = r
          }
        }
        recorder.submitSlotPcm(slotId, stereoFloats, configuredSampleRate)
      }
    }

    val outBuffer = replaceOutputBuffer(remaining)
    outBuffer.put(inputBuffer)
    outBuffer.flip()
  }

  override fun onReset() {
    recorder.removeSlot(slotId)
  }
}

data class RecordedMasterOutput(
  val uriString: String,
  val displayName: String,
  val fileName: String,
  val durationMs: Long,
  val sizeBytes: Long,
  val format: MasterRecordFormat = MasterRecordFormat.WAV
)

/**
 * Lightweight MediaCodec AAC-LC -> MediaMuxer (.m4a) streaming encoder with no third-party libraries.
 */
internal class AacM4aStreamEncoder(
  private val outputFile: File,
  private val sampleRateHz: Int,
  private val channelCount: Int = 2,
  private val bitRate: Int = 192_000
) {
  private val codec: MediaCodec
  private val muxer: MediaMuxer
  private val bufferInfo = MediaCodec.BufferInfo()
  private var trackIndex = -1
  private var muxerStarted = false
  private var totalFramesWritten = 0L
  private var finished = false

  init {
    val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRateHz, channelCount).apply {
      setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
      setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
      setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
    }
    codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    codec.start()
    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
  }

  @Synchronized
  fun writePcm16(pcmBytes: ByteArray, length: Int = pcmBytes.size) {
    if (finished || length <= 0) return
    val bytesPerFrame = channelCount * 2
    var offset = 0
    while (offset < length) {
      drainOutput(endOfStream = false)
      val inputIndex = codec.dequeueInputBuffer(10_000L)
      if (inputIndex < 0) continue
      val inputBuf = codec.getInputBuffer(inputIndex) ?: continue
      inputBuf.clear()
      val chunkLen = minOf(length - offset, inputBuf.capacity())
      inputBuf.put(pcmBytes, offset, chunkLen)
      val ptsUs = (totalFramesWritten * 1_000_000L) / sampleRateHz.toLong()
      codec.queueInputBuffer(inputIndex, 0, chunkLen, ptsUs, 0)
      totalFramesWritten += (chunkLen / bytesPerFrame).toLong()
      offset += chunkLen
    }
    drainOutput(endOfStream = false)
  }

  @Synchronized
  fun finish() {
    if (finished) return
    finished = true
    runCatching {
      val inputIndex = codec.dequeueInputBuffer(10_000L)
      if (inputIndex >= 0) {
        val ptsUs = (totalFramesWritten * 1_000_000L) / sampleRateHz.toLong()
        codec.queueInputBuffer(inputIndex, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
      }
      drainOutput(endOfStream = true)
    }
    releaseResources()
  }

  @Synchronized
  fun abort() {
    if (finished) return
    finished = true
    releaseResources()
  }

  private fun releaseResources() {
    runCatching { codec.stop() }
    runCatching { codec.release() }
    if (muxerStarted) {
      runCatching { muxer.stop() }
    }
    runCatching { muxer.release() }
  }

  private fun drainOutput(endOfStream: Boolean) {
    val timeoutUs = if (endOfStream) 10_000L else 0L
    while (true) {
      val outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
      when {
        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
          if (!endOfStream) return
        }
        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          if (!muxerStarted) {
            trackIndex = muxer.addTrack(codec.outputFormat)
            muxer.start()
            muxerStarted = true
          }
        }
        outIndex >= 0 -> {
          val outBuf = codec.getOutputBuffer(outIndex)
          if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0
          }
          if (outBuf != null && bufferInfo.size > 0 && muxerStarted && trackIndex >= 0) {
            outBuf.position(bufferInfo.offset)
            outBuf.limit(bufferInfo.offset + bufferInfo.size)
            muxer.writeSampleData(trackIndex, outBuf, bufferInfo)
          }
          val sawEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
          codec.releaseOutputBuffer(outIndex, false)
          if (sawEos) return
        }
        else -> if (!endOfStream) return
      }
    }
  }
}

/**
 * Records the internal polyphonic master output mix (from MasterAudioMixerBus or ExoPlayer AudioProcessor sinks,
 * never microphone) into a 16-bit 44.1kHz/48kHz stereo WAV or AAC (.m4a) file.
 */
class MasterOutputRecorder(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val slotBuffers = ConcurrentHashMap<String, SlotPcmRingBuffer>()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _level = MutableStateFlow(0f)
  val level: StateFlow<Float> = _level.asStateFlow()

  private val _elapsedMs = MutableStateFlow(0L)
  val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

  @Volatile
  private var activeFlag = false

  @Volatile
  private var targetSampleRateHz = MasterRecordSampleRate.RATE_44100.hz

  @Volatile
  private var targetFormat = MasterRecordFormat.WAV

  private var mixJob: Job? = null
  private var tempOutputFile: File? = null
  private var activeAacEncoder: AacM4aStreamEncoder? = null
  private var startedElapsedMs = 0L
  private var targetSafFolderUri: String? = null

  fun isActivelyRecording(): Boolean = activeFlag

  fun submitSlotPcm(slotId: String, stereoFloats: FloatArray, srcSampleRateHz: Int) {
    if (!activeFlag || stereoFloats.isEmpty()) return
    val resampled = MasterMixPcmMath.resampleStereo(stereoFloats, srcSampleRateHz, targetSampleRateHz)
    val ring = slotBuffers.getOrPut(slotId) { SlotPcmRingBuffer(targetSampleRateHz * 2) }
    ring.push(resampled)
  }

  fun removeSlot(slotId: String) {
    slotBuffers.remove(slotId)?.clear()
  }

  fun startRecording(
    sampleRateHz: Int = MasterRecordSampleRate.RATE_44100.hz,
    safFolderUri: String? = null,
    format: MasterRecordFormat = MasterRecordFormat.WAV
  ): Result<Unit> {
    if (activeFlag) return Result.success(Unit)
    return runCatching {
      slotBuffers.clear()
      val chosenRate = if (sampleRateHz == 48000) 48000 else 44100
      targetSampleRateHz = chosenRate
      targetFormat = format
      targetSafFolderUri = safFolderUri

      val dir = File(context.filesDir, "master_recordings").apply { mkdirs() }
      val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val outFile = File(dir, "MasterMix_$stamp.${format.extension}")
      tempOutputFile = outFile

      val aacEncoder = if (format == MasterRecordFormat.M4A) {
        AacM4aStreamEncoder(outputFile = outFile, sampleRateHz = chosenRate, channelCount = 2)
      } else {
        FileOutputStream(outFile).use { fos ->
          fos.write(MasterMixPcmMath.createWavHeader(0, chosenRate, channels = 2, bitsPerSample = 16))
        }
        null
      }
      activeAacEncoder = aacEncoder

      startedElapsedMs = SystemClock.elapsedRealtime()
      activeFlag = true
      _isRecording.value = true
      _elapsedMs.value = 0L
      _level.value = 0f

      SoundPadPlaybackService.updateMasterRecordingNotification(context, isRecording = true, elapsedLabel = "00:00")

      val tickMs = 20L
      val framesPerTick = (chosenRate * tickMs / 1000L).toInt().coerceAtLeast(256)
      val samplesPerTick = framesPerTick * 2

      mixJob = scope.launch {
        val pcmChunkBytes = ByteArray(samplesPerTick * 2)
        var lastNotifSec = -1L
        val wavStream = if (aacEncoder == null) FileOutputStream(outFile, true) else null
        try {
          while (isActive && activeFlag) {
            val slotChunks = ArrayList<FloatArray>()
            val iterator = slotBuffers.entries.iterator()
            while (iterator.hasNext()) {
              val entry = iterator.next()
              val chunk = entry.value.drainExactOrLess(samplesPerTick)
              if (chunk != null) {
                slotChunks.add(chunk)
              }
            }

            val rms = MasterMixPcmMath.mixAndSoftLimitToPcm16(slotChunks, framesPerTick, pcmChunkBytes)
            if (aacEncoder != null) {
              aacEncoder.writePcm16(pcmChunkBytes, pcmChunkBytes.size)
            } else {
              wavStream?.write(pcmChunkBytes)
            }

            val elapsed = (SystemClock.elapsedRealtime() - startedElapsedMs).coerceAtLeast(0L)
            _level.value = rms
            _elapsedMs.value = elapsed

            val elapsedSec = elapsed / 1000L
            if (elapsedSec != lastNotifSec) {
              lastNotifSec = elapsedSec
              val min = elapsedSec / 60L
              val sec = elapsedSec % 60L
              SoundPadPlaybackService.updateMasterRecordingNotification(
                context,
                isRecording = true,
                elapsedLabel = "%02d:%02d".format(min, sec)
              )
            }
            delay(tickMs)
          }
          wavStream?.flush()
        } finally {
          runCatching { wavStream?.close() }
        }
      }
    }
  }

  suspend fun stopRecordingAndSave(): RecordedMasterOutput? = withContext(Dispatchers.IO) {
    if (!activeFlag) return@withContext null
    activeFlag = false
    runCatching { mixJob?.join() }
    mixJob = null
    slotBuffers.clear()

    activeAacEncoder?.finish()
    activeAacEncoder = null

    val durationMs = (_elapsedMs.value).coerceAtLeast(0L)
    _isRecording.value = false
    _level.value = 0f
    _elapsedMs.value = 0L
    SoundPadPlaybackService.updateMasterRecordingNotification(context, isRecording = false, elapsedLabel = null)

    val outFile = tempOutputFile ?: return@withContext null
    tempOutputFile = null
    val recordedFormat = targetFormat

    val minValidSize = if (recordedFormat == MasterRecordFormat.WAV) 44L else 64L
    if (!outFile.exists() || outFile.length() <= minValidSize) {
      outFile.delete()
      return@withContext null
    }

    if (recordedFormat == MasterRecordFormat.WAV) {
      val pcmDataBytes = (outFile.length() - 44L).toInt().coerceAtLeast(0)
      RandomAccessFile(outFile, "rw").use { raf ->
        raf.seek(0L)
        raf.write(MasterMixPcmMath.createWavHeader(pcmDataBytes, targetSampleRateHz, channels = 2, bitsPerSample = 16))
      }
    }

    val fileName = outFile.name
    val cleanDisplay = fileName.substringBeforeLast('.')

    // Copy to user-selected SAF folder if granted
    val folderUriStr = targetSafFolderUri
    if (!folderUriStr.isNullOrBlank()) {
      val safDocUri = runCatching {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(folderUriStr))
        val targetDoc = tree?.createFile(recordedFormat.mimeType, fileName)
        if (targetDoc != null) {
          context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
            outFile.inputStream().use { input -> input.copyTo(out) }
          }
          targetDoc.uri
        } else {
          null
        }
      }.getOrNull()

      if (safDocUri != null) {
        val size = outFile.length()
        outFile.delete()
        return@withContext RecordedMasterOutput(
          uriString = safDocUri.toString(),
          displayName = cleanDisplay,
          fileName = fileName,
          durationMs = durationMs,
          sizeBytes = size,
          format = recordedFormat
        )
      }
    }

    return@withContext RecordedMasterOutput(
      uriString = Uri.fromFile(outFile).toString(),
      displayName = cleanDisplay,
      fileName = fileName,
      durationMs = durationMs,
      sizeBytes = outFile.length(),
      format = recordedFormat
    )
  }

  fun cancelRecording() {
    activeFlag = false
    mixJob?.cancel()
    mixJob = null
    activeAacEncoder?.abort()
    activeAacEncoder = null
    slotBuffers.clear()
    _isRecording.value = false
    _level.value = 0f
    _elapsedMs.value = 0L
    SoundPadPlaybackService.updateMasterRecordingNotification(context, isRecording = false, elapsedLabel = null)
    tempOutputFile?.delete()
    tempOutputFile = null
  }
}
