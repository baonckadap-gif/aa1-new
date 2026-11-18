package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadShapeStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun padComposeShape(style: PadShapeStyle, cornerRadius: Dp = 12.dp): Shape {
  return when (style) {
    PadShapeStyle.SQUARE -> RoundedCornerShape(cornerRadius)
    PadShapeStyle.TRIANGLE -> GenericShape { size, _ ->
      val pts = trianglePoints(size)
      moveTo(pts[0].x, pts[0].y)
      lineTo(pts[1].x, pts[1].y)
      lineTo(pts[2].x, pts[2].y)
      close()
    }
    PadShapeStyle.STAR -> GenericShape { size, _ ->
      val pts = starPoints(size)
      moveTo(pts.first().x, pts.first().y)
      for (i in 1 until pts.size) {
        lineTo(pts[i].x, pts[i].y)
      }
      close()
    }
    PadShapeStyle.PRISM -> GenericShape { size, _ ->
      val pts = prismPoints(size)
      moveTo(pts.first().x, pts.first().y)
      for (i in 1 until pts.size) {
        lineTo(pts[i].x, pts[i].y)
      }
      close()
    }
  }
}

fun padCornerGlowVertices(style: PadShapeStyle, size: Size): List<Offset> {
  return when (style) {
    PadShapeStyle.SQUARE -> listOf(
      Offset(size.width * 0.08f, size.height * 0.08f),
      Offset(size.width * 0.92f, size.height * 0.08f),
      Offset(size.width * 0.92f, size.height * 0.92f),
      Offset(size.width * 0.08f, size.height * 0.92f)
    )
    PadShapeStyle.TRIANGLE -> trianglePoints(size)
    PadShapeStyle.STAR -> starOuterTipPoints(size)
    PadShapeStyle.PRISM -> prismPoints(size)
  }
}

fun buildPadShapePath(style: PadShapeStyle, size: Size): Path {
  val path = Path()
  when (style) {
    PadShapeStyle.SQUARE -> {
      val inset = 1f
      path.moveTo(inset, inset)
      path.lineTo(size.width - inset, inset)
      path.lineTo(size.width - inset, size.height - inset)
      path.lineTo(inset, size.height - inset)
      path.close()
    }
    PadShapeStyle.TRIANGLE -> {
      val pts = trianglePoints(size)
      path.moveTo(pts[0].x, pts[0].y)
      path.lineTo(pts[1].x, pts[1].y)
      path.lineTo(pts[2].x, pts[2].y)
      path.close()
    }
    PadShapeStyle.STAR -> {
      val pts = starPoints(size)
      path.moveTo(pts.first().x, pts.first().y)
      for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
      path.close()
    }
    PadShapeStyle.PRISM -> {
      val pts = prismPoints(size)
      path.moveTo(pts.first().x, pts.first().y)
      for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
      path.close()
    }
  }
  return path
}

private fun trianglePoints(size: Size): List<Offset> {
  val padX = size.width * 0.04f
  val padTop = size.height * 0.04f
  val padBottom = size.height * 0.96f
  return listOf(
    Offset(size.width * 0.5f, padTop),
    Offset(size.width - padX, padBottom),
    Offset(padX, padBottom)
  )
}

private fun starPoints(size: Size): List<Offset> {
  val cx = size.width * 0.5f
  val cy = size.height * 0.52f
  val outerR = min(size.width, size.height) * 0.48f
  val innerR = outerR * 0.48f
  val pts = ArrayList<Offset>(10)
  for (i in 0 until 10) {
    val angle = -PI / 2.0 + i * (PI / 5.0)
    val r = if (i % 2 == 0) outerR else innerR
    val scaleX = (size.width / min(size.width, size.height)).coerceAtMost(1.35f)
    val scaleY = (size.height / min(size.width, size.height)).coerceAtMost(1.35f)
    pts.add(
      Offset(
        x = cx + (cos(angle) * r * scaleX).toFloat().coerceIn(-cx * 0.96f, cx * 0.96f),
        y = cy + (sin(angle) * r * scaleY).toFloat().coerceIn(-cy * 0.96f, cy * 0.92f)
      )
    )
  }
  return pts
}

private fun starOuterTipPoints(size: Size): List<Offset> {
  val all = starPoints(size)
  return listOf(all[0], all[2], all[4], all[6], all[8])
}

private fun prismPoints(size: Size): List<Offset> {
  val w = size.width
  val h = size.height
  return listOf(
    Offset(w * 0.50f, h * 0.02f),
    Offset(w * 0.96f, h * 0.26f),
    Offset(w * 0.96f, h * 0.74f),
    Offset(w * 0.50f, h * 0.98f),
    Offset(w * 0.04f, h * 0.74f),
    Offset(w * 0.04f, h * 0.26f)
  )
}

@Composable
fun PadShapePreviewGlyph(
  style: PadShapeStyle,
  color: Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val path = buildPadShapePath(style, size)
    drawPath(
      path = path,
      color = color.copy(alpha = 0.25f)
    )
    drawPath(
      path = path,
      color = color,
      style = Stroke(width = 2.dp.toPx())
    )
  }
}

fun padBadgeVector(badge: PadBadgeIcon): ImageVector? {
  return when (badge) {
    PadBadgeIcon.NONE -> null
    PadBadgeIcon.MUSIC -> Icons.Filled.MusicNote
    PadBadgeIcon.BOLT -> Icons.Filled.Bolt
    PadBadgeIcon.FIRE -> Icons.Filled.LocalFireDepartment
    PadBadgeIcon.STAR -> Icons.Filled.Star
    PadBadgeIcon.MIC -> Icons.Filled.Mic
    PadBadgeIcon.DIAMOND -> Icons.Filled.Diamond
    PadBadgeIcon.WAVE -> Icons.Filled.GraphicEq
  }
}
