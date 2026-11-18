package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.tanh

data class ReverbEchoParams(
  val echoEnabled: Boolean = false,
  val echoDelayMs: Float = 220f,
  val echoFeedback: Float = 0.30f,
  val echoMix: Float = 0.25f,
  val reverbEnabled: Boolean = false,
  val reverbRoomSize: Float = 0.35f,
  val reverbDamping: Float = 0.45f,
  val reverbMix: Float = 0.25f
) {
  val isBypassed: Boolean
    get() = (!echoEnabled || echoMix <= 0.005f) && (!reverbEnabled || reverbMix <= 0.005f)
}

@UnstableApi
class ReverbEchoAudioProcessor : BaseAudioProcessor() {

  companion object {
    private const val MAX_ECHO_MS = 1000
    private val BASE_COMB_LENGTHS_44K = intArrayOf(1116, 1188, 1277, 1356)
    private val BASE_ALLPASS_LENGTHS_44K = intArrayOf(556, 441)
    private const val STEREO_SPREAD_44K = 23
  }

  @Volatile
  private var params = ReverbEchoParams()

  private var sampleRate = 44100
  private var channels = 2

  // Echo stereo delay lines
  private var echoBufL = FloatArray(1)
  private var echoBufR = FloatArray(1)
  private var echoIdx = 0

  // Reverb 4 parallel damped comb filters + 2 series allpass diffusers per channel
  private var combL = Array(4) { FloatArray(1) }
  private var combR = Array(4) { FloatArray(1) }
  private var combIdxL = IntArray(4)
  private var combIdxR = IntArray(4)
  private var combFilterStateL = FloatArray(4)
  private var combFilterStateR = FloatArray(4)

  private var allpassL = Array(2) { FloatArray(1) }
  private var allpassR = Array(2) { FloatArray(1) }
  private var allpassIdxL = IntArray(2)
  private var allpassIdxR = IntArray(2)

  fun setParams(next: ReverbEchoParams) {
    params = next.copy(
      echoDelayMs = next.echoDelayMs.coerceIn(60f, 800f),
      echoFeedback = next.echoFeedback.coerceIn(0f, 0.75f),
      echoMix = next.echoMix.coerceIn(0f, 0.85f),
      reverbRoomSize = next.reverbRoomSize.coerceIn(0f, 1f),
      reverbDamping = next.reverbDamping.coerceIn(0f, 1f),
      reverbMix = next.reverbMix.coerceIn(0f, 0.85f)
    )
  }

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
      inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
    val supportedChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
    if (!supportedEncoding || !supportedChannels) {
      return AudioProcessor.AudioFormat.NOT_SET
    }
    sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
    channels = inputAudioFormat.channelCount
    allocateBuffers(sampleRate)
    return inputAudioFormat
  }

  private fun allocateBuffers(sr: Int) {
    val maxEchoSamples = ((sr * MAX_ECHO_MS) / 1000).coerceAtLeast(1024)
    echoBufL = FloatArray(maxEchoSamples)
    echoBufR = FloatArray(maxEchoSamples)
    echoIdx = 0

    val scale = sr.toDouble() / 44100.0
    val spread = (STEREO_SPREAD_44K * scale).toInt().coerceAtLeast(4)
    for (i in 0 until 4) {
      val lenL = (BASE_COMB_LENGTHS_44K[i] * scale).toInt().coerceAtLeast(64)
      val lenR = lenL + spread
      combL[i] = FloatArray(lenL)
      combR[i] = FloatArray(lenR)
      combIdxL[i] = 0
      combIdxR[i] = 0
      combFilterStateL[i] = 0f
      combFilterStateR[i] = 0f
    }
    for (i in 0 until 2) {
      val lenL = (BASE_ALLPASS_LENGTHS_44K[i] * scale).toInt().coerceAtLeast(32)
      val lenR = lenL + spread
      allpassL[i] = FloatArray(lenL)
      allpassR[i] = FloatArray(lenR)
      allpassIdxL[i] = 0
      allpassIdxR[i] = 0
    }
  }

  override fun onFlush() {
    clearBuffers()
  }

  override fun onReset() {
    clearBuffers()
  }

  private fun clearBuffers() {
    echoBufL.fill(0f)
    echoBufR.fill(0f)
    echoIdx = 0
    for (i in 0 until 4) {
      combL[i].fill(0f)
      combR[i].fill(0f)
      combIdxL[i] = 0
      combIdxR[i] = 0
      combFilterStateL[i] = 0f
      combFilterStateR[i] = 0f
    }
    for (i in 0 until 2) {
      allpassL[i].fill(0f)
      allpassR[i].fill(0f)
      allpassIdxL[i] = 0
      allpassIdxR[i] = 0
    }
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    val p = params
    if (p.isBypassed) {
      val outBuffer = replaceOutputBuffer(remaining)
      outBuffer.put(inputBuffer)
      outBuffer.flip()
      return
    }

    val encoding = inputAudioFormat.encoding
    val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
    val frameCount = remaining / (channels * bytesPerSample)
    if (frameCount <= 0) {
      inputBuffer.position(inputBuffer.limit())
      return
    }

    val outBuffer = replaceOutputBuffer(frameCount * channels * bytesPerSample)

    val useEcho = p.echoEnabled && p.echoMix > 0.005f
    val echoBufSize = echoBufL.size.coerceAtLeast(1)
    val delaySamplesL = ((p.echoDelayMs * sampleRate) / 1000f).toInt().coerceIn(1, echoBufSize - 1)
    val delaySamplesR = (delaySamplesL * 0.94f).toInt().coerceIn(1, echoBufSize - 1)
    val echoFb = p.echoFeedback
    val echoMix = p.echoMix

    val useReverb = p.reverbEnabled && p.reverbMix > 0.005f
    val roomFeedback = 0.68f + (p.reverbRoomSize * 0.27f)
    val damp = p.reverbDamping * 0.65f
    val invDamp = 1f - damp
    val reverbMix = p.reverbMix

    for (frame in 0 until frameCount) {
      val inL: Float
      val inR: Float
      if (encoding == C.ENCODING_PCM_16BIT) {
        inL = inputBuffer.short.toFloat() / 32768f
        inR = if (channels == 2) inputBuffer.short.toFloat() / 32768f else inL
      } else {
        inL = inputBuffer.float
        inR = if (channels == 2) inputBuffer.float else inL
      }

      var curL = inL
      var curR = inR

      if (useEcho) {
        val readIdxL = (echoIdx - delaySamplesL + echoBufSize) % echoBufSize
        val readIdxR = (echoIdx - delaySamplesR + echoBufSize) % echoBufSize
        val delayedL = echoBufL[readIdxL]
        val delayedR = echoBufR[readIdxR]
        // Subtle cross-feed stereo echo
        echoBufL[echoIdx] = (inL + delayedR * echoFb).coerceIn(-1.5f, 1.5f)
        echoBufR[echoIdx] = (inR + delayedL * echoFb).coerceIn(-1.5f, 1.5f)
        echoIdx = (echoIdx + 1) % echoBufSize

        val dryEcho = 1f - (echoMix * 0.35f)
        curL = curL * dryEcho + delayedL * echoMix
        curR = curR * dryEcho + delayedR * echoMix
      }

      if (useReverb) {
        val monoInput = (curL + curR) * 0.14f
        var wetL = 0f
        var wetR = 0f

        for (c in 0 until 4) {
          val bufL = combL[c]
          val idxL = combIdxL[c]
          val outC = bufL[idxL]
          combFilterStateL[c] = outC * invDamp + combFilterStateL[c] * damp
          bufL[idxL] = monoInput + combFilterStateL[c] * roomFeedback
          combIdxL[c] = (idxL + 1) % bufL.size
          wetL += outC

          val bufR = combR[c]
          val idxR = combIdxR[c]
          val outCR = bufR[idxR]
          combFilterStateR[c] = outCR * invDamp + combFilterStateR[c] * damp
          bufR[idxR] = monoInput + combFilterStateR[c] * roomFeedback
          combIdxR[c] = (idxR + 1) % bufR.size
          wetR += outCR
        }

        for (a in 0 until 2) {
          val bufL = allpassL[a]
          val idxL = allpassIdxL[a]
          val bufferedL = bufL[idxL]
          val outAllL = -wetL + bufferedL
          bufL[idxL] = wetL + bufferedL * 0.5f
          allpassIdxL[a] = (idxL + 1) % bufL.size
          wetL = outAllL

          val bufR = allpassR[a]
          val idxR = allpassIdxR[a]
          val bufferedR = bufR[idxR]
          val outAllR = -wetR + bufferedR
          bufR[idxR] = wetR + bufferedR * 0.5f
          allpassIdxR[a] = (idxR + 1) % bufR.size
          wetR = outAllR
        }

        val dryRev = 1f - (reverbMix * 0.30f)
        curL = curL * dryRev + wetL * reverbMix
        curR = curR * dryRev + wetR * reverbMix
      }

      val finalL = if (curL > 0.94f || curL < -0.94f) tanh(curL.toDouble()).toFloat() else curL
      val finalR = if (curR > 0.94f || curR < -0.94f) tanh(curR.toDouble()).toFloat() else curR

      if (encoding == C.ENCODING_PCM_16BIT) {
        val sL = (finalL * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        outBuffer.putShort(sL)
        if (channels == 2) {
          val sR = (finalR * 32767f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
          outBuffer.putShort(sR)
        }
      } else {
        outBuffer.putFloat(finalL.coerceIn(-1f, 1f))
        if (channels == 2) {
          outBuffer.putFloat(finalR.coerceIn(-1f, 1f))
        }
      }
    }

    outBuffer.flip()
  }
}
