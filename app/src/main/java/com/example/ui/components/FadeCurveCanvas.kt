package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.audio.FadeCurveMath
import com.example.domain.model.FadeCurve

@Composable
fun FadeCurveCanvas(
  curve: FadeCurve,
  showCrossfadePair: Boolean,
  modifier: Modifier = Modifier
) {
  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryColor = MaterialTheme.colorScheme.secondary
  val gridColor = MaterialTheme.colorScheme.outlineVariant
  val bgColor = MaterialTheme.colorScheme.surfaceContainer

  Canvas(
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .clip(RoundedCornerShape(10.dp))
      .background(bgColor)
      .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
  ) {
    val padX = 10.dp.toPx()
    val padY = 8.dp.toPx()
    val w = (size.width - padX * 2f).coerceAtLeast(1f)
    val h = (size.height - padY * 2f).coerceAtLeast(1f)

    // Subtle reference grid lines
    drawLine(
      color = gridColor.copy(alpha = 0.55f),
      start = Offset(padX, padY + h * 0.5f),
      end = Offset(padX + w, padY + h * 0.5f),
      strokeWidth = 1f
    )
    drawLine(
      color = gridColor.copy(alpha = 0.55f),
      start = Offset(padX + w * 0.5f, padY),
      end = Offset(padX + w * 0.5f, padY + h),
      strokeWidth = 1f
    )

    val steps = 40
    val fadeInPath = Path()
    for (i in 0..steps) {
      val t = i.toFloat() / steps.toFloat()
      val gain = FadeCurveMath.gainIn(curve, t)
      val x = padX + t * w
      val y = padY + (1f - gain) * h
      if (i == 0) fadeInPath.moveTo(x, y) else fadeInPath.lineTo(x, y)
    }
    drawPath(
      path = fadeInPath,
      color = primaryColor,
      style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
    )

    if (showCrossfadePair) {
      val fadeOutPath = Path()
      for (i in 0..steps) {
        val t = i.toFloat() / steps.toFloat()
        val gain = FadeCurveMath.gainOut(curve, t)
        val x = padX + t * w
        val y = padY + (1f - gain) * h
        if (i == 0) fadeOutPath.moveTo(x, y) else fadeOutPath.lineTo(x, y)
      }
      drawPath(
        path = fadeOutPath,
        color = secondaryColor,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
      )
    }
  }
}
