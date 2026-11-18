package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.domain.model.EqPreset
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

data class BiquadCoeffs(
  val b0: Double = 1.0,
  val b1: Double = 0.0,
  val b2: Double = 0.0,
  val a1: Double = 0.0,
  val a2: Double = 0.0,
  val isBypass: Boolean = true
)

object FiveBandEqMath {
  val BAND_FREQUENCIES_HZ = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
  private const val DEFAULT_Q = 1.15

  fun combineEqBands(
    masterEnabled: Boolean,
    masterBandsDb: List<Float>,
    padEnabled: Boolean,
    padBandsDb: List<Float>
  ): FloatArray {
    val result = FloatArray(5)
    for (i in 0 until 5) {
      val m = if (masterEnabled) masterBandsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f) else 0f
      val p = if (padEnabled) padBandsDb.getOrElse(i) { 0f }.coerceIn(-12f, 12f) else 0f
      result[i] = (m + p).coerceIn(-18f, 18f)
    }
    return result
  }

  /**
   * Computes Robert Bristow-Johnson Peaking EQ biquad coefficients normalized by a0.
   */
  fun computePeakingBiquad(
    sampleRate: Int,
    centerFreqHz: Float,
    gainDb: Float,
    q: Double = DEFAULT_Q
  ): BiquadCoeffs {
    if (abs(gainDb) < 0.05f || sampleRate <= 0) {
      return BiquadCoeffs(isBypass = true)
    }
    val nyquistSafe = (sampleRate * 0.45).coerceAtLeast(100.0)
    val freq = centerFreqHz.toDouble().coerceIn(20.0, nyquistSafe)
    val a = 10.0.pow(gainDb.toDouble() / 40.0)
    val w0 = 2.0 * PI * freq / sampleRate.toDouble()
    val cosW0 = cos(w0)
    val sinW0 = sin(w0)
    val alpha = sinW0 / (2.0 * q)

    val b0 = 1.0 + alpha * a
    val b1 = -2.0 * cosW0
    val b2 = 1.0 - alpha * a
    val a0 = 1.0 + alpha / a
    val a1 = -2.0 * cosW0
    val a2 = 1.0 - alpha / a

    if (abs(a0) < 1e-9) return BiquadCoeffs(isBypass = true)
    return BiquadCoeffs(
      b0 = b0 / a0,
      b1 = b1 / a0,
      b2 = b2 / a0,
      a1 = a1 / a0,
      a2 = a2 / a0,
      isBypass = false
    )
  }

  fun resolveBandLabel(index: Int): String {
    return EqPreset.FREQUENCY_LABELS.getOrElse(index) { "${BAND_FREQUENCIES_HZ.getOrElse(index) { 0f }.toInt()}Hz" }
  }
}

@UnstableApi
class FiveBandEqAudioProcessor : BaseAudioProcessor() {

  @Volatile
  private var bandsDb: FloatArray = FloatArray(5)

  @Volatile
  private var isActiveBypass: Boolean = true

  @Volatile
  private var coeffs: Array<BiquadCoeffs> = Array(5) { BiquadCoeffs() }

  private var configuredSampleRate: Int = 44100
  private var configuredChannels: Int = 2

  // Direct Form II Transposed filter states: [band][channel]
  private val z1 = Array(5) { DoubleArray(2) }
  private val z2 = Array(5) { DoubleArray(2) }

  fun setBandsDb(gainsDb: FloatArray) {
    val sanitized = FloatArray(5) { idx ->
      gainsDb.getOrElse(idx) { 0f }.coerceIn(-18f, 18f)
    }
    bandsDb = sanitized
    recomputeCoefficients(configuredSampleRate)
  }

  fun setCombinedEq(
    masterEnabled: Boolean,
    masterBandsDb: List<Float>,
    padEnabled: Boolean,
    padBandsDb: List<Float>
  ) {
    setBandsDb(FiveBandEqMath.combineEqBands(masterEnabled, masterBandsDb, padEnabled, padBandsDb))
  }

  private fun recomputeCoefficients(sampleRate: Int) {
    val gains = bandsDb
    var allFlat = true
    val nextCoeffs = Array(5) { idx ->
      val g = gains[idx]
      if (abs(g) >= 0.05f) allFlat = false
      FiveBandEqMath.computePeakingBiquad(
        sampleRate = sampleRate,
        centerFreqHz = FiveBandEqMath.BAND_FREQUENCIES_HZ[idx],
        gainDb = g
      )
    }
    coeffs = nextCoeffs
    isActiveBypass = allFlat
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
    recomputeCoefficients(configuredSampleRate)
    clearFilterState()
    return inputAudioFormat
  }

  override fun onFlush() {
    clearFilterState()
  }

  override fun onReset() {
    clearFilterState()
  }

  private fun clearFilterState() {
    for (b in 0 until 5) {
      z1[b][0] = 0.0
      z1[b][1] = 0.0
      z2[b][0] = 0.0
      z2[b][1] = 0.0
    }
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    if (isActiveBypass) {
      val outBuffer = replaceOutputBuffer(remaining)
      outBuffer.put(inputBuffer)
      outBuffer.flip()
      return
    }

    val channels = configuredChannels
    val encoding = inputAudioFormat.encoding
    val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
    val frameCount = remaining / (channels * bytesPerSample)
    if (frameCount <= 0) {
      inputBuffer.position(inputBuffer.limit())
      return
    }

    val outBuffer = replaceOutputBuffer(frameCount * channels * bytesPerSample)
    val localCoeffs = coeffs

    if (encoding == C.ENCODING_PCM_16BIT) {
      for (frame in 0 until frameCount) {
        for (ch in 0 until channels) {
          var sample = inputBuffer.short.toDouble() / 32768.0
          for (b in 0 until 5) {
            val c = localCoeffs[b]
            if (!c.isBypass) {
              val y = c.b0 * sample + z1[b][ch]
              z1[b][ch] = c.b1 * sample - c.a1 * y + z2[b][ch]
              z2[b][ch] = c.b2 * sample - c.a2 * y
              sample = y
            }
          }
          val soft = softLimit(sample)
          val outShort = (soft * 32767.0)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
          outBuffer.putShort(outShort)
        }
      }
    } else {
      for (frame in 0 until frameCount) {
        for (ch in 0 until channels) {
          var sample = inputBuffer.float.toDouble()
          for (b in 0 until 5) {
            val c = localCoeffs[b]
            if (!c.isBypass) {
              val y = c.b0 * sample + z1[b][ch]
              z1[b][ch] = c.b1 * sample - c.a1 * y + z2[b][ch]
              z2[b][ch] = c.b2 * sample - c.a2 * y
              sample = y
            }
          }
          outBuffer.putFloat(softLimit(sample).toFloat())
        }
      }
    }

    outBuffer.flip()
  }

  private fun softLimit(x: Double): Double {
    return if (x > 0.92 || x < -0.92) {
      tanh(x * 0.95)
    } else {
      x
    }
  }
}
