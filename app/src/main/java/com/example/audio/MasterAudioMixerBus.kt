package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Shared low-latency stereo mixer bus routing all active main output pads through one summing
 * stage followed by [MasterLimiterAudioProcessor].
 *
 * Using Option (2) (unified mixer bus) ensures polyphonic pads are genuinely summed first and
 * then peak-limited cleanly before reaching Android AudioTrack, avoiding both per-session
 * DynamicsProcessing cross-slot clipping and AudioTrack HAL variance across OEMs.
 */
@UnstableApi
class MasterAudioMixerBus(
  private val context: Context,
  val limiterProcessor: MasterLimiterAudioProcessor = MasterLimiterAudioProcessor()
) {

  companion object {
    const val DEFAULT_BUS_SAMPLE_RATE = 48000
    private const val MIXER_BLOCK_MS = 10
    private const val BUS_RECORD_SLOT_ID = "__master_mixer_bus__"
  }

  private val slotBuffers = ConcurrentHashMap<String, SlotPcmRingBuffer>()
  private val runningFlag = AtomicBoolean(false)
  private val renderThreadLock = Any()

  @Volatile
  private var mixerThread: Thread? = null

  @Volatile
  private var audioTrack: AudioTrack? = null

  @Volatile
  private var busSampleRateHz: Int = DEFAULT_BUS_SAMPLE_RATE

  @Volatile
  private var preferredOutputDevice: AudioDeviceInfo? = null

  @Volatile
  private var masterRecorder: MasterOutputRecorder? = null

  @Volatile
  var isActivelyHandlingOutput: Boolean = false
    private set

  fun attachMasterRecorder(recorder: MasterOutputRecorder?) {
    masterRecorder = recorder
  }

  fun setLimiterEnabled(enabled: Boolean) {
    limiterProcessor.setEnabled(enabled)
  }

  fun setPreferredOutputDevice(deviceInfo: AudioDeviceInfo?) {
    preferredOutputDevice = deviceInfo
    runCatching {
      audioTrack?.preferredDevice = deviceInfo
    }
  }

  fun submitSlotAudio(slotId: String, stereoFloats: FloatArray, srcSampleRateHz: Int) {
    if (!isActivelyHandlingOutput || stereoFloats.isEmpty()) return
    val resampled = if (srcSampleRateHz == busSampleRateHz) {
      stereoFloats
    } else {
      MasterMixPcmMath.resampleStereo(stereoFloats, srcSampleRateHz, busSampleRateHz)
    }
    val ring = slotBuffers.getOrPut(slotId) { SlotPcmRingBuffer(busSampleRateHz * 2) }
    ring.push(resampled)
  }

  fun removeSlot(slotId: String) {
    slotBuffers.remove(slotId)?.clear()
  }

  fun clearAllSlots() {
    slotBuffers.keys.toList().forEach { key ->
      slotBuffers.remove(key)?.clear()
    }
    limiterProcessor.resetDspState()
    masterRecorder?.removeSlot(BUS_RECORD_SLOT_ID)
  }

  fun ensureStarted(requestedBufferMs: Int = 50): Boolean {
    if (isActivelyHandlingOutput && audioTrack != null && runningFlag.get()) {
      return true
    }
    synchronized(renderThreadLock) {
      if (isActivelyHandlingOutput && audioTrack != null && runningFlag.get()) {
        return true
      }
      val nativeRate = queryNativeOutputSampleRate()
      busSampleRateHz = nativeRate
      limiterProcessor.configureForFormat(nativeRate, 2)

      val minBufBytes = runCatching {
        AudioTrack.getMinBufferSize(
          nativeRate,
          AudioFormat.CHANNEL_OUT_STEREO,
          AudioFormat.ENCODING_PCM_16BIT
        )
      }.getOrDefault(-1)

      if (minBufBytes <= 0) {
        isActivelyHandlingOutput = false
        return false
      }

      val targetBytes = ((nativeRate * requestedBufferMs.coerceIn(20, 200)) / 1000) * 4
      val bufferBytes = maxOf(minBufBytes, targetBytes)

      val track = runCatching {
        val attrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
        val format = AudioFormat.Builder()
          .setSampleRate(nativeRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .build()
        val builder = AudioTrack.Builder()
          .setAudioAttributes(attrs)
          .setAudioFormat(format)
          .setTransferMode(AudioTrack.MODE_STREAM)
          .setBufferSizeInBytes(bufferBytes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }
        builder.build().apply {
          preferredOutputDevice?.let { dev ->
            runCatching { preferredDevice = dev }
          }
          play()
        }
      }.getOrNull()

      if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
        runCatching { track?.release() }
        isActivelyHandlingOutput = false
        return false
      }

      audioTrack = track
      runningFlag.set(true)
      isActivelyHandlingOutput = true
      mixerThread = thread(
        start = true,
        isDaemon = true,
        name = "SoundPadMasterMixerBus",
        priority = Thread.MAX_PRIORITY
      ) {
        runMixerLoop(track, nativeRate)
      }
      return true
    }
  }

  fun stop() {
    synchronized(renderThreadLock) {
      runningFlag.set(false)
      isActivelyHandlingOutput = false
      val t = mixerThread
      mixerThread = null
      runCatching { t?.join(250L) }
      audioTrack?.let { tr ->
        runCatching { tr.pause() }
        runCatching { tr.flush() }
        runCatching { tr.release() }
      }
      audioTrack = null
      clearAllSlots()
    }
  }

  fun release() {
    stop()
  }

  private fun queryNativeOutputSampleRate(): Int {
    return runCatching {
      val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      val raw = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
      if (raw == 44100 || raw == 48000) raw else DEFAULT_BUS_SAMPLE_RATE
    }.getOrDefault(DEFAULT_BUS_SAMPLE_RATE)
  }

  private fun runMixerLoop(track: AudioTrack, sampleRateHz: Int) {
    runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
    val framesPerBlock = ((sampleRateHz * MIXER_BLOCK_MS) / 1000).coerceAtLeast(256)
    val samplesPerBlock = framesPerBlock * 2
    val mixFloat = FloatArray(samplesPerBlock)
    val pcmShort = ShortArray(samplesPerBlock)

    while (runningFlag.get()) {
      mixFloat.fill(0.0f)
      var hasAudio = false

      val iterator = slotBuffers.entries.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        val chunk = entry.value.drainExactOrLess(samplesPerBlock)
        if (chunk != null && chunk.isNotEmpty()) {
          hasAudio = true
          val limit = minOf(chunk.size, samplesPerBlock)
          for (i in 0 until limit) {
            mixFloat[i] += chunk[i]
          }
        }
      }

      if (hasAudio) {
        limiterProcessor.processStereoInterleaved(mixFloat, framesPerBlock, sampleRateHz)
        val recorder = masterRecorder
        if (recorder != null && recorder.isActivelyRecording()) {
          recorder.submitSlotPcm(BUS_RECORD_SLOT_ID, mixFloat.copyOf(), sampleRateHz)
        }
        for (i in 0 until samplesPerBlock) {
          val s = (mixFloat[i] * 32767.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          pcmShort[i] = s.toShort()
        }
      } else {
        pcmShort.fill(0)
      }

      val written = runCatching {
        track.write(pcmShort, 0, samplesPerBlock, AudioTrack.WRITE_BLOCKING)
      }.getOrDefault(-1)

      if (written < 0 || !runningFlag.get()) {
        if (!hasAudio) {
          try {
            Thread.sleep(MIXER_BLOCK_MS.toLong())
          } catch (_: InterruptedException) {
            break
          }
        }
      }
    }
    isActivelyHandlingOutput = false
  }
}

/**
 * Tail Media3 [BaseAudioProcessor] installed on every main output [ExoPlayer] slot.
 *
 * When [MasterAudioMixerBus] is active and the real-time bus limiter is enabled, it scales the
 * post-EQ/FX/Pan PCM by the current slot gain, pushes samples to the shared mixer bus, and zeroes
 * the downstream sink buffer so ExoPlayer retains exact clock, clip, loop, and Sonic parameters
 * without double-playing through two AudioTracks.
 *
 * When the limiter / shared bus is disabled or unavailable (fallback mode), audio passes directly
 * to ExoPlayer's sink unmodified while optionally tapping to [MasterOutputRecorder].
 */
@UnstableApi
class MasterBusSlotTapAudioProcessor(
  private val slotId: String,
  private val mixerBus: MasterAudioMixerBus?,
  private val recorder: MasterOutputRecorder?
) : BaseAudioProcessor() {

  @Volatile
  private var currentSlotGain: Float = 1.0f

  @Volatile
  private var routeViaMixerBus: Boolean = false

  private var configuredSampleRate: Int = 44100
  private var configuredChannels: Int = 2

  fun setGain(gain: Float) {
    currentSlotGain = gain.coerceIn(0f, 2.0f)
  }

  fun setRouteViaMixerBus(enabled: Boolean) {
    if (routeViaMixerBus && !enabled) {
      mixerBus?.removeSlot(slotId)
    }
    routeViaMixerBus = enabled
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

    val bus = mixerBus
    val useBus = routeViaMixerBus && bus != null && bus.isActivelyHandlingOutput
    val rec = recorder
    val needsRecorderFallbackTap = !useBus && rec != null && rec.isActivelyRecording()

    if (useBus || needsRecorderFallbackTap) {
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
        if (useBus) {
          bus?.submitSlotAudio(slotId, stereoFloats, configuredSampleRate)
        } else if (needsRecorderFallbackTap) {
          rec?.submitSlotPcm(slotId, stereoFloats, configuredSampleRate)
        }
      }
    }

    val outBuffer = replaceOutputBuffer(remaining)
    if (useBus) {
      // Advance inputBuffer position and output zeroed PCM to prevent double playback
      inputBuffer.position(inputBuffer.limit())
      for (i in 0 until remaining) {
        outBuffer.put(0.toByte())
      }
    } else {
      outBuffer.put(inputBuffer)
    }
    outBuffer.flip()
  }

  override fun onReset() {
    mixerBus?.removeSlot(slotId)
    recorder?.removeSlot(slotId)
  }
}
