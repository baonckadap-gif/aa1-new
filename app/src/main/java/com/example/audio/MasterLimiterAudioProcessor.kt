package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

data class MasterLimiterParams(
  val enabled: Boolean = true,
  val thresholdDb: Float = -1.0f,
  val lookaheadMs: Float = 3.0f,
  val attackMs: Float = 0.5f,
  val releaseMs: Float = 80.0f,
  val kneeWidthDb: Float = 3.0f
)

/**
 * Pure DSP formulas for soft-knee peak limiting & lookahead gain computation.
 * Isolated here for deterministic JVM unit tests without hardware audio dependencies.
 */
object MasterLimiterMath {
  const val DEFAULT_THRESHOLD_DB = -1.0f
  const val DEFAULT_LOOKAHEAD_MS = 3.0f
  const val DEFAULT_ATTACK_MS = 0.5f
  const val DEFAULT_RELEASE_MS = 80.0f
  const val DEFAULT_KNEE_WIDTH_DB = 3.0f

  fun dbToLinear(db: Float): Float = 10.0f.pow(db / 20.0f)

  fun linearToDb(linear: Float): Float {
    val safe = max(linear, 1e-6f)
    return 20.0f * log10(safe)
  }

  /**
   * Computes target linear gain `[0f, 1f]` for a stereo-linked peak magnitude [peakLinear]
   * given [thresholdDb] and [kneeWidthDb].
   *
   * - Below `(thresholdDb - kneeWidthDb / 2)`: gain = 1.0 (0 dB attenuation)
   * - Inside soft knee: smooth quadratic transition
   * - Above `(thresholdDb + kneeWidthDb / 2)`: exact brickwall clamping to [thresholdDb]
   */
  fun computeSoftKneeTargetGain(
    peakLinear: Float,
    thresholdDb: Float = DEFAULT_THRESHOLD_DB,
    kneeWidthDb: Float = DEFAULT_KNEE_WIDTH_DB
  ): Float {
    val safePeak = abs(peakLinear)
    if (safePeak <= 1e-6f) return 1.0f
    val inputDb = linearToDb(safePeak)
    val knee = kneeWidthDb.coerceIn(0f, 12f)
    val halfKnee = knee * 0.5f
    val overDb = inputDb - thresholdDb

    val outputDb = when {
      knee <= 0.001f -> {
        if (inputDb > thresholdDb) thresholdDb else inputDb
      }
      overDb <= -halfKnee -> {
        inputDb
      }
      overDb >= halfKnee -> {
        thresholdDb
      }
      else -> {
        val delta = overDb + halfKnee
        inputDb - (delta * delta) / (2.0f * knee)
      }
    }
    val gainDb = (outputDb - inputDb).coerceAtMost(0f)
    return dbToLinear(gainDb).coerceIn(0f, 1f)
  }
}

/**
 * Real-time stereo-linked lookahead soft-knee master peak limiter.
 *
 * Designed to operate either directly inside a Media3 sink chain ([BaseAudioProcessor]) OR as the
 * terminal DSP processor of [MasterAudioMixerBus] via [processStereoInterleaved].
 *
 * Characteristics:
 * - Lookahead circular buffer: 2–5 ms (default 3.0 ms)
 * - Ceiling threshold: -1.0 dBFS (~0.89125 linear)
 * - Soft-knee onset: 3.0 dB around threshold
 * - Fast attack (~0.5 ms) + smooth release (~80 ms)
 */
@UnstableApi
class MasterLimiterAudioProcessor(
  initialParams: MasterLimiterParams = MasterLimiterParams()
) : BaseAudioProcessor() {

  @Volatile
  private var params: MasterLimiterParams = sanitizeParams(initialParams)

  private var configuredSampleRate: Int = 48000
  private var configuredChannels: Int = 2

  private var delayFrames: Int = 144
  private var delayL = FloatArray(144)
  private var delayR = FloatArray(144)
  private var delayPeak = FloatArray(144)
  private var delayWriteIdx: Int = 0
  private var envelopeGain: Float = 1.0f

  private var attackCoeff: Float = 0.0f
  private var releaseCoeff: Float = 0.0f
  private var hardCeilingLinear: Float = MasterLimiterMath.dbToLinear(MasterLimiterMath.DEFAULT_THRESHOLD_DB)

  @Volatile
  var lastGainReductionDb: Float = 0.0f
    private set

  @Volatile
  var currentOutputPeakDb: Float = -96.0f
    private set

  init {
    recalculateCoefficients(configuredSampleRate)
  }

  private fun sanitizeParams(raw: MasterLimiterParams): MasterLimiterParams {
    return raw.copy(
      thresholdDb = raw.thresholdDb.coerceIn(-12.0f, 0.0f),
      lookaheadMs = raw.lookaheadMs.coerceIn(2.0f, 5.0f),
      attackMs = raw.attackMs.coerceIn(0.1f, 10.0f),
      releaseMs = raw.releaseMs.coerceIn(10.0f, 500.0f),
      kneeWidthDb = raw.kneeWidthDb.coerceIn(0.0f, 6.0f)
    )
  }

  fun setParams(next: MasterLimiterParams) {
    val sanitized = sanitizeParams(next)
    val lookaheadChanged = sanitized.lookaheadMs != params.lookaheadMs
    params = sanitized
    hardCeilingLinear = MasterLimiterMath.dbToLinear(sanitized.thresholdDb)
    recalculateCoefficients(configuredSampleRate)
    if (lookaheadChanged) {
      allocateDelayBuffer(configuredSampleRate, sanitized.lookaheadMs)
    }
  }

  fun setEnabled(enabled: Boolean) {
    if (params.enabled != enabled) {
      params = params.copy(enabled = enabled)
    }
  }

  fun getParams(): MasterLimiterParams = params

  fun getLookaheadFrames(): Int = delayFrames

  fun configureForFormat(sampleRateHz: Int, channelCount: Int = 2) {
    val safeRate = sampleRateHz.coerceIn(8000, 192000)
    val safeCh = channelCount.coerceIn(1, 2)
    val needsRealloc = safeRate != configuredSampleRate || delayL.isEmpty()
    configuredSampleRate = safeRate
    configuredChannels = safeCh
    recalculateCoefficients(safeRate)
    if (needsRealloc) {
      allocateDelayBuffer(safeRate, params.lookaheadMs)
    }
  }

  private fun recalculateCoefficients(sampleRateHz: Int) {
    val sr = sampleRateHz.coerceAtLeast(8000).toDouble()
    val effectiveAttackSec = (params.attackMs.coerceAtLeast(0.1f) / 1000.0)
    val effectiveReleaseSec = (params.releaseMs.coerceAtLeast(10.0f) / 1000.0)
    attackCoeff = exp(-1.0 / (effectiveAttackSec * sr)).toFloat().coerceIn(0.0f, 0.9999f)
    releaseCoeff = exp(-1.0 / (effectiveReleaseSec * sr)).toFloat().coerceIn(0.0f, 0.99995f)
    hardCeilingLinear = MasterLimiterMath.dbToLinear(params.thresholdDb)
  }

  private fun allocateDelayBuffer(sampleRateHz: Int, lookaheadMs: Float) {
    val frames = ((sampleRateHz * lookaheadMs) / 1000.0f).toInt().coerceIn(16, 2048)
    delayFrames = frames
    delayL = FloatArray(frames)
    delayR = FloatArray(frames)
    delayPeak = FloatArray(frames)
    delayWriteIdx = 0
    envelopeGain = 1.0f
  }

  fun resetDspState() {
    delayL.fill(0f)
    delayR.fill(0f)
    delayPeak.fill(0f)
    delayWriteIdx = 0
    envelopeGain = 1.0f
    lastGainReductionDb = 0.0f
    currentOutputPeakDb = -96.0f
  }

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
      inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
    val supportedChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
    if (!supportedEncoding || !supportedChannels) {
      return AudioProcessor.AudioFormat.NOT_SET
    }
    configureForFormat(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    return inputAudioFormat
  }

  override fun onFlush() {
    resetDspState()
  }

  override fun onReset() {
    resetDspState()
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    val activeParams = params
    if (!activeParams.enabled) {
      val outBuffer = replaceOutputBuffer(remaining)
      outBuffer.put(inputBuffer)
      outBuffer.flip()
      return
    }

    val encoding = inputAudioFormat.encoding
    val channels = configuredChannels
    val frameCount = if (encoding == C.ENCODING_PCM_FLOAT) {
      remaining / (channels * 4)
    } else {
      remaining / (channels * 2)
    }
    if (frameCount <= 0) return

    val stereo = FloatArray(frameCount * 2)
    if (encoding == C.ENCODING_PCM_FLOAT) {
      for (f in 0 until frameCount) {
        val l = inputBuffer.float
        val r = if (channels == 2) inputBuffer.float else l
        stereo[f * 2] = l
        stereo[f * 2 + 1] = r
      }
    } else {
      for (f in 0 until frameCount) {
        val l = inputBuffer.short.toFloat() / 32768.0f
        val r = if (channels == 2) inputBuffer.short.toFloat() / 32768.0f else l
        stereo[f * 2] = l
        stereo[f * 2 + 1] = r
      }
    }

    processStereoInterleaved(stereo, frameCount, configuredSampleRate)

    val outBuffer = replaceOutputBuffer(remaining)
    if (encoding == C.ENCODING_PCM_FLOAT) {
      for (f in 0 until frameCount) {
        outBuffer.putFloat(stereo[f * 2])
        if (channels == 2) outBuffer.putFloat(stereo[f * 2 + 1])
      }
    } else {
      for (f in 0 until frameCount) {
        val sL = (stereo[f * 2] * 32767.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        outBuffer.putShort(sL.toShort())
        if (channels == 2) {
          val sR = (stereo[f * 2 + 1] * 32767.0f).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
          outBuffer.putShort(sR.toShort())
        }
      }
    }
    outBuffer.flip()
  }

  /**
   * Processes interleaved stereo floats `[L0, R0, L1, R1, ...]` in-place through the lookahead
   * soft-knee peak limiter.
   *
   * Because audio travels through [delayFrames] lookahead frames (`2–5 ms`), an incoming peak is
   * already detected across the delay window and [envelopeGain] ramps down BEFORE that sample
   * reaches the output.
   */
  @Synchronized
  fun processStereoInterleaved(
    interleaved: FloatArray,
    frameCount: Int,
    sampleRateHz: Int = configuredSampleRate
  ) {
    if (frameCount <= 0 || interleaved.isEmpty()) return
    if (sampleRateHz != configuredSampleRate || delayL.isEmpty()) {
      configureForFormat(sampleRateHz, 2)
    }

    val activeParams = params
    if (!activeParams.enabled) {
      var peak = 0f
      val limit = minOf(frameCount * 2, interleaved.size)
      for (i in 0 until limit) {
        val a = abs(interleaved[i])
        if (a > peak) peak = a
      }
      lastGainReductionDb = 0.0f
      currentOutputPeakDb = if (peak > 1e-6f) MasterLimiterMath.linearToDb(peak) else -96.0f
      return
    }

    val safeFrames = minOf(frameCount, interleaved.size / 2)
    val len = delayFrames.coerceAtLeast(1)
    val thresholdDb = activeParams.thresholdDb
    val kneeDb = activeParams.kneeWidthDb
    val ceiling = hardCeilingLinear
    var minGainSeen = 1.0f
    var maxOutputSeen = 0.0f

    for (f in 0 until safeFrames) {
      val idx = f * 2
      val inL = interleaved[idx]
      val inR = interleaved[idx + 1]
      val inPeak = max(abs(inL), abs(inR))

      // Oldest delayed frame about to exit the lookahead buffer
      val delayedL = delayL[delayWriteIdx]
      val delayedR = delayR[delayWriteIdx]

      // Push current frame into lookahead ring
      delayL[delayWriteIdx] = inL
      delayR[delayWriteIdx] = inR
      delayPeak[delayWriteIdx] = inPeak
      delayWriteIdx = (delayWriteIdx + 1) % len

      // Inspect maximum stereo peak across the entire lookahead window
      var windowPeak = 0.0f
      for (k in 0 until len) {
        val p = delayPeak[k]
        if (p > windowPeak) windowPeak = p
      }

      val targetGain = MasterLimiterMath.computeSoftKneeTargetGain(
        peakLinear = windowPeak,
        thresholdDb = thresholdDb,
        kneeWidthDb = kneeDb
      )

      envelopeGain = if (targetGain < envelopeGain) {
        attackCoeff * envelopeGain + (1.0f - attackCoeff) * targetGain
      } else {
        releaseCoeff * envelopeGain + (1.0f - releaseCoeff) * targetGain
      }.coerceIn(0.0f, 1.0f)

      if (envelopeGain < minGainSeen) {
        minGainSeen = envelopeGain
      }

      var outL = delayedL * envelopeGain
      var outR = delayedR * envelopeGain

      // Hard brickwall safety guard at -1 dBFS ceiling so delayed spikes cannot exceed threshold
      val outPeakFrame = max(abs(outL), abs(outR))
      if (outPeakFrame > ceiling && outPeakFrame > 1e-6f) {
        val trimScale = ceiling / outPeakFrame
        outL *= trimScale
        outR *= trimScale
      }

      val clampedPeak = max(abs(outL), abs(outR))
      if (clampedPeak > maxOutputSeen) {
        maxOutputSeen = clampedPeak
      }

      interleaved[idx] = outL
      interleaved[idx + 1] = outR
    }

    lastGainReductionDb = if (minGainSeen < 0.9999f) {
      -MasterLimiterMath.linearToDb(minGainSeen)
    } else {
      0.0f
    }
    currentOutputPeakDb = if (maxOutputSeen > 1e-6f) {
      MasterLimiterMath.linearToDb(maxOutputSeen)
    } else {
      -96.0f
    }
  }
}
