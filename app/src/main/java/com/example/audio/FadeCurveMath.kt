package com.example.audio

import com.example.domain.model.FadeCurve
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

object FadeCurveMath {

  private const val EXP_K = 3.0
  private val EXP_NORM = exp(EXP_K) - 1.0
  private const val LOG_K = 9.0
  private val LOG_NORM = ln(1.0 + LOG_K)

  fun gainIn(curve: FadeCurve, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f).toDouble()
    val result = when (curve) {
      FadeCurve.LINEAR -> t
      FadeCurve.EXPONENTIAL -> (exp(EXP_K * t) - 1.0) / EXP_NORM
      FadeCurve.LOGARITHMIC -> ln(1.0 + LOG_K * t) / LOG_NORM
      FadeCurve.S_CURVE -> t * t * (3.0 - 2.0 * t)
      FadeCurve.EQUAL_POWER -> sin(t * (PI / 2.0))
    }
    return result.toFloat().coerceIn(0f, 1f)
  }

  fun gainOut(curve: FadeCurve, progress: Float): Float {
    val t = progress.coerceIn(0f, 1f).toDouble()
    val result = when (curve) {
      FadeCurve.EQUAL_POWER -> cos(t * (PI / 2.0))
      FadeCurve.LINEAR -> 1.0 - t
      FadeCurve.EXPONENTIAL -> 1.0 - ((exp(EXP_K * t) - 1.0) / EXP_NORM)
      FadeCurve.LOGARITHMIC -> 1.0 - (ln(1.0 + LOG_K * t) / LOG_NORM)
      FadeCurve.S_CURVE -> 1.0 - (t * t * (3.0 - 2.0 * t))
    }
    return result.toFloat().coerceIn(0f, 1f)
  }

  fun equalPowerCrossfadePair(curve: FadeCurve, progress: Float): Pair<Float, Float> {
    return gainOut(curve, progress) to gainIn(curve, progress)
  }

  fun sqrtGain(value: Float): Float = sqrt(value.coerceIn(0f, 1f))
}
