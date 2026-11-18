package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordSheet(
  isRecording: Boolean,
  level: Float,
  elapsedMs: Long,
  onStartRecording: () -> Unit,
  onStopAndSaveRecording: () -> Unit,
  onCancelRecording: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var countdownSec by remember { mutableIntStateOf(3) }
  var activeCountdown by remember { mutableStateOf<Int?>(null) }

  val animatedLevel by animateFloatAsState(
    targetValue = level.coerceIn(0f, 1f),
    label = "voice_level_meter"
  )

  LaunchedEffect(activeCountdown) {
    val current = activeCountdown ?: return@LaunchedEffect
    if (current > 0) {
      delay(1000L)
      activeCountdown = current - 1
    } else {
      activeCountdown = null
      onStartRecording()
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      if (isRecording) {
        onCancelRecording()
      }
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      activeCountdown = null
      if (isRecording) onCancelRecording()
      onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Voice Track Recorder",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "Saves WAV automatically as a new pad",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(
          onClick = {
            activeCountdown = null
            if (isRecording) onCancelRecording()
            onDismiss()
          },
          modifier = Modifier.size(48.dp)
        ) {
          Icon(Icons.Filled.Close, contentDescription = "Close recorder")
        }
      }

      // Countdown selector (when idle)
      if (!isRecording && activeCountdown == null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(0 to "Instant", 3 to "3s Delay", 5 to "5s Delay").forEach { (sec, label) ->
            FilterChip(
              selected = countdownSec == sec,
              onClick = { countdownSec = sec },
              label = { Text(label, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier.weight(1f)
            )
          }
        }
      }

      // Live VU Level Meter + Timer Display
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
          1.dp,
          if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          val statusTitle = when {
            activeCountdown != null -> "STARTING IN ${activeCountdown}"
            isRecording -> formatRecTime(elapsedMs)
            else -> "00:00.0"
          }
          Text(
            text = statusTitle,
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = when {
              activeCountdown != null -> MaterialTheme.colorScheme.secondary
              isRecording -> MaterialTheme.colorScheme.error
              else -> MaterialTheme.colorScheme.onSurface
            }
          )

          // 24-segment LED studio level meter
          val primaryClr = MaterialTheme.colorScheme.primary
          val amberClr = MaterialTheme.colorScheme.secondary
          val redClr = MaterialTheme.colorScheme.error
          val trackBg = MaterialTheme.colorScheme.outlineVariant
          Canvas(
            modifier = Modifier
              .fillMaxWidth()
              .height(26.dp)
          ) {
            val segments = 24
            val gap = 4.dp.toPx()
            val segW = (size.width - gap * (segments - 1)) / segments.toFloat()
            val litCount = (animatedLevel * segments).toInt().coerceIn(0, segments)
            for (i in 0 until segments) {
              val segColor = when {
                i >= litCount -> trackBg
                i >= (segments * 0.8f).toInt() -> redClr
                i >= (segments * 0.55f).toInt() -> amberClr
                else -> primaryClr
              }
              drawRoundRect(
                color = segColor,
                topLeft = Offset(i * (segW + gap), 0f),
                size = Size(segW, size.height),
                cornerRadius = CornerRadius(4f, 4f)
              )
            }
          }
        }
      }

      // Primary Record / Stop Button (icon-only)
      Box(contentAlignment = Alignment.Center) {
        FilledIconButton(
          onClick = {
            when {
              isRecording -> onStopAndSaveRecording()
              activeCountdown != null -> activeCountdown = null
              countdownSec > 0 -> activeCountdown = countdownSec
              else -> onStartRecording()
            }
          },
          modifier = Modifier
            .size(72.dp)
            .testTag("record_action_button"),
          shape = CircleShape,
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRecording || activeCountdown != null) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.primary
            },
            contentColor = Color.White
          )
        ) {
          Icon(
            imageVector = when {
              isRecording -> Icons.Filled.Stop
              activeCountdown != null -> Icons.Filled.Close
              else -> Icons.Filled.Mic
            },
            contentDescription = if (isRecording) "Stop and save voice pad" else "Start voice recording",
            modifier = Modifier.size(34.dp)
          )
        }
      }

      if (isRecording) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.FiberManualRecord,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(12.dp)
          )
          Text(
            text = "Recording mic input...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

private fun formatRecTime(ms: Long): String {
  val safe = ms.coerceAtLeast(0L)
  val totalSec = safe / 1000L
  val min = totalSec / 60L
  val sec = totalSec % 60L
  val tenths = (safe % 1000L) / 100L
  return String.format(Locale.US, "%02d:%02d.%d", min, sec, tenths)
}
