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
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.SoundPadTheme
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
  val spacing = SoundPadTheme.spacing
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
        .padding(horizontal = spacing.lg + spacing.xs, vertical = spacing.md),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.voice_rec_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(R.string.voice_rec_subtitle),
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
          modifier = Modifier.minimumInteractiveComponentSize()
        ) {
          Icon(
            Icons.Filled.Close,
            contentDescription = stringResource(R.string.cd_close_voice_recorder)
          )
        }
      }

      // Countdown selector (when idle)
      if (!isRecording && activeCountdown == null) {
        val delayOptions = listOf(
          0 to stringResource(R.string.voice_rec_delay_instant),
          3 to stringResource(R.string.voice_rec_delay_3s),
          5 to stringResource(R.string.voice_rec_delay_5s)
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
          delayOptions.forEach { (sec, label) ->
            FilterChip(
              selected = countdownSec == sec,
              onClick = { countdownSec = sec },
              label = { Text(label, style = MaterialTheme.typography.labelSmall) },
              modifier = Modifier
                .weight(1f)
                .minimumInteractiveComponentSize()
            )
          }
        }
      }

      // Live VU Level Meter + Timer Display
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
          1.dp,
          if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
        )
      ) {
        Column(
          modifier = Modifier.padding(spacing.lg),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
          val statusTitle = when {
            activeCountdown != null -> stringResource(R.string.voice_rec_starting_in, activeCountdown ?: 0)
            isRecording -> formatRecTime(elapsedMs)
            else -> stringResource(R.string.voice_rec_idle_timer)
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
            val gap = spacing.xs.toPx()
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
      val isAlertActive = isRecording || activeCountdown != null
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
            containerColor = if (isAlertActive) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.primary
            },
            contentColor = if (isAlertActive) {
              MaterialTheme.colorScheme.onError
            } else {
              MaterialTheme.colorScheme.onPrimary
            }
          )
        ) {
          Icon(
            imageVector = when {
              isRecording -> Icons.Filled.Stop
              activeCountdown != null -> Icons.Filled.Close
              else -> Icons.Filled.Mic
            },
            contentDescription = stringResource(
              when {
                isRecording -> R.string.cd_stop_save_voice_pad
                activeCountdown != null -> R.string.cd_close_voice_recorder
                else -> R.string.cd_start_voice_recording
              }
            ),
            modifier = Modifier.size(34.dp)
          )
        }
      }

      if (isRecording) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)
        ) {
          Icon(
            imageVector = Icons.Filled.FiberManualRecord,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(12.dp)
          )
          Text(
            text = stringResource(R.string.voice_rec_recording_mic),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error
          )
        }
      }

      Spacer(modifier = Modifier.height(spacing.xs + spacing.xxs))
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
