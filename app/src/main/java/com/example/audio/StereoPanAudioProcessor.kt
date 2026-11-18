package com.example.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object StereoPanMath {
  /**
   * Computes equal-power L/R gain factors for a combined pan in [-1f (Full Left), +1f (Full Right)].
   * At center (0f), both channels stay at unity (1.0f, 1.0f) so unpanned playback has zero level loss.
   * For any pan away from center, applies smooth equal-power cosine/sine weighting scaled by sqrt(2)
   * clamped cleanly to 1.0f on the dominant channel to avoid digital clipping while keeping perceived energy smooth.
   */
  fun computeChannelGains(combinedPan: Float): Pair<Float, Float> {
    val p = combinedPan.coerceIn(-1f, 1f)
    if (abs(p) < 0.005f) return 1.0f to 1.0f
    val t = (p + 1f) * 0.5f // 0 = hard left, 0.5 = center, 1 = hard right
    val angle = t * (PI / 2.0)
    val scale = sqrt(2.0)
    val left = (cos(angle) * scale).toFloat().coerceIn(0f, 1f)
    val right = (sin(angle) * scale).toFloat().coerceIn(0f, 1f)
    return left to right
  }

  fun combinePan(masterPan: Float, padPan: Float): Float {
    return (masterPan + padPan).coerceIn(-1f, 1f)
  }

  fun formatPanLabel(pan: Float): String {
    val pct = (pan.coerceIn(-1f, 1f) * 100f).toInt()
    return when {
      pct <= -3 -> "L ${-pct}"
      pct >= 3 -> "R $pct"
      else -> "Center"
    }
  }
}

@UnstableApi
class StereoPanAudioProcessor : BaseAudioProcessor() {

  @Volatile
  private var leftGain: Float = 1.0f

  @Volatile
  private var rightGain: Float = 1.0f

  @Volatile
  private var activePan: Float = 0.0f

  fun setPan(combinedPan: Float) {
    val clamped = combinedPan.coerceIn(-1f, 1f)
    activePan = clamped
    val (l, r) = StereoPanMath.computeChannelGains(clamped)
    leftGain = l
    rightGain = r
  }

  override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
      inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
    val supportedChannels = inputAudioFormat.channelCount == 1 || inputAudioFormat.channelCount == 2
    if (!supportedEncoding || !supportedChannels) {
      return AudioProcessor.AudioFormat.NOT_SET
    }
    return AudioProcessor.AudioFormat(
      inputAudioFormat.sampleRate,
      2,
      inputAudioFormat.encoding
    )
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    val inChannels = inputAudioFormat.channelCount
    val encoding = inputAudioFormat.encoding
    val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
    val frameCount = remaining / (inChannels * bytesPerSample)
    if (frameCount <= 0) {
      inputBuffer.position(inputBuffer.limit())
      return
    }

    val outBytes = frameCount * 2 * bytesPerSample
    val outBuffer = replaceOutputBuffer(outBytes)
    val lGain = leftGain
    val rGain = rightGain

    if (encoding == C.ENCODING_PCM_16BIT) {
      for (i in 0 until frameCount) {
        val rawL: Short
        val rawR: Short
        if (inChannels == 1) {
          rawL = inputBuffer.short
          rawR = rawL
        } else {
          rawL = inputBuffer.short
          rawR = inputBuffer.short
        }
        val outL = (rawL * lGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        val outR = (rawR * rGain).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        outBuffer.putShort(outL)
        outBuffer.putShort(outR)
      }
    } else if (encoding == C.ENCODING_PCM_FLOAT) {
      for (i in 0 until frameCount) {
        val rawL: Float
        val rawR: Float
        if (inChannels == 1) {
          rawL = inputBuffer.float
          rawR = rawL
        } else {
          rawL = inputBuffer.float
          rawR = inputBuffer.float
        }
        outBuffer.putFloat((rawL * lGain).coerceIn(-1f, 1f))
        outBuffer.putFloat((rawR * rGain).coerceIn(-1f, 1f))
      }
    }

    outBuffer.flip()
  }
}
