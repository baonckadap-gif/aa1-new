package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.EqPreset
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun EqFiveBandPanel(
  title: String,
  subtitle: String,
  enabled: Boolean,
  preset: EqPreset,
  bandsDb: List<Float>,
  testTagPrefix: String,
  onEnabledChange: (Boolean) -> Unit,
  onPresetSelected: (EqPreset) -> Unit,
  onBandChange: (bandIndex: Int, gainDb: Float) -> Unit,
  onReset: () -> Unit,
  modifier: Modifier = Modifier
) {
  val sanitized = EqPreset.sanitizeBands(bandsDb)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      width = if (enabled) 1.5.dp else 1.dp,
      color = if (enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Equalizer,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
          Column {
            Text(
              text = title,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(
            onClick = onReset,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("${testTagPrefix}_eq_reset_button")
          ) {
            Icon(
              imageVector = Icons.Filled.RestartAlt,
              contentDescription = "Reset $title",
              modifier = Modifier.size(20.dp)
            )
          }
          Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag("${testTagPrefix}_eq_switch")
          )
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(EqPreset.entries, key = { it.name }) { item ->
          FilterChip(
            selected = preset == item,
            onClick = { onPresetSelected(item) },
            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier.testTag("${testTagPrefix}_eq_preset_${item.name.lowercase()}")
          )
        }
      }

      EqResponsePreviewCanvas(
        bandsDb = sanitized,
        enabled = enabled,
        lineColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        gridColor = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier
          .fillMaxWidth()
          .height(64.dp)
      )

      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        EqPreset.FREQUENCY_LABELS.forEachIndexed { index, freqLabel ->
          val gain = sanitized[index]
          val formatted = if (gain >= 0f) {
            String.format(Locale.US, "+%.1f dB", gain)
          } else {
            String.format(Locale.US, "%.1f dB", gain)
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .pointerInput(index, testTagPrefix) {
                detectTapGestures(
                  onDoubleTap = { onBandChange(index, 0f) }
                )
              },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = freqLabel,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.width(46.dp)
            )
            Slider(
              value = gain,
              onValueChange = { raw ->
                val snapped = ((raw * 2f).roundToInt() / 2f).coerceIn(-12f, 12f)
                onBandChange(index, snapped)
              },
              valueRange = -12f..12f,
              modifier = Modifier
                .weight(1f)
                .testTag("${testTagPrefix}_eq_band_$index")
            )
            Text(
              text = formatted,
              style = MaterialTheme.typography.labelSmall,
              color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                gain > 0.4f -> MaterialTheme.colorScheme.primary
                gain < -0.4f -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              },
              modifier = Modifier.width(56.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EqResponsePreviewCanvas(
  bandsDb: List<Float>,
  enabled: Boolean,
  lineColor: Color,
  gridColor: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, gridColor.copy(alpha = 0.65f))
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
      val w = size.width
      val h = size.height
      if (w <= 0f || h <= 0f) return@Canvas

      val zeroY = h * 0.5f
      drawLine(
        color = gridColor.copy(alpha = 0.65f),
        start = Offset(0f, zeroY),
        end = Offset(w, zeroY),
        strokeWidth = 1.5f
      )

      val points = bandsDb.mapIndexed { idx, db ->
        val x = (idx.toFloat() / 4f) * w
        val norm = (db.coerceIn(-12f, 12f) / 12f)
        val y = zeroY - (norm * (h * 0.44f))
        Offset(x, y)
      }

      val linePath = Path()
      val fillPath = Path()
      if (points.isNotEmpty()) {
        linePath.moveTo(points[0].x, points[0].y)
        fillPath.moveTo(points[0].x, h)
        fillPath.lineTo(points[0].x, points[0].y)

        for (i in 0 until points.lastIndex) {
          val p0 = points[i]
          val p1 = points[i + 1]
          val midX = (p0.x + p1.x) * 0.5f
          linePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
          fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
        }
        fillPath.lineTo(points.last().x, h)
        fillPath.close()
      }

      drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
          colors = listOf(
            lineColor.copy(alpha = if (enabled) 0.28f else 0.10f),
            Color.Transparent
          )
        )
      )
      drawPath(
        path = linePath,
        color = lineColor.copy(alpha = if (enabled) 1f else 0.45f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
      )

      points.forEach { pt ->
        drawCircle(
          color = lineColor.copy(alpha = if (enabled) 1f else 0.5f),
          radius = 3.5.dp.toPx(),
          center = pt
        )
      }
    }
  }
}
