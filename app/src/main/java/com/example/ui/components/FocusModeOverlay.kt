package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.HapticIntensity
import com.example.ui.theme.SoundPadTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FocusModeOverlay(
  currentBankTitle: String,
  activeVoices: Int,
  masterVolume: Float,
  focusCornerPanicEnabled: Boolean,
  hapticIntensity: HapticIntensity,
  onMasterVolumeChange: (Float) -> Unit,
  onStopAll: () -> Unit,
  onExitFocusMode: () -> Unit,
  modifier: Modifier = Modifier
) {
  val extColors = SoundPadTheme.colors
  val haptic = LocalHapticFeedback.current
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  var controlsVisible by remember { mutableStateOf(true) }
  var interactionTick by remember { mutableIntStateOf(0) }
  var isAdjustingSlider by remember { mutableStateOf(false) }
  var showEntryHint by remember { mutableStateOf(true) }

  fun revealControls() {
    controlsVisible = true
    interactionTick++
  }

  LaunchedEffect(controlsVisible, interactionTick, isAdjustingSlider) {
    if (controlsVisible && !isAdjustingSlider) {
      delay(3000L)
      controlsVisible = false
    }
  }

  LaunchedEffect(Unit) {
    delay(3200L)
    showEntryHint = false
  }

  Box(modifier = modifier.fillMaxSize()) {
    // 1. Top-edge swipe-down exit gesture strip
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    var triggeredSwipeExit by remember { mutableStateOf(false) }
    var accumulatedDownY by remember { mutableFloatStateOf(0f) }

    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .statusBarsPadding()
        .height(44.dp)
        .testTag("focus_top_swipe_exit_zone")
        .pointerInput(Unit) {
          detectVerticalDragGestures(
            onDragStart = {
              accumulatedDownY = 0f
              triggeredSwipeExit = false
            },
            onDragEnd = {
              accumulatedDownY = 0f
              triggeredSwipeExit = false
            },
            onDragCancel = {
              accumulatedDownY = 0f
              triggeredSwipeExit = false
            },
            onVerticalDrag = { _, dragAmount ->
              if (dragAmount > 0f) {
                accumulatedDownY += dragAmount
                if (!triggeredSwipeExit && accumulatedDownY >= swipeThresholdPx) {
                  triggeredSwipeExit = true
                  performPadHaptic(haptic, hapticIntensity)
                  onExitFocusMode()
                }
              }
            }
          )
        },
      contentAlignment = Alignment.TopCenter
    ) {
      Box(
        modifier = Modifier
          .padding(top = 6.dp)
          .width(42.dp)
          .height(4.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f))
      )
    }

    // 2. Brief entry hint pill
    AnimatedVisibility(
      visible = showEntryHint,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .padding(top = 20.dp, start = 16.dp, end = 16.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(16.dp),
        color = extColors.focusOverlayBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Text(
          text = stringResource(R.string.focus_mode_entry_hint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
      }
    }

    // 3. Bottom-edge touch trigger zone (when mini overlay is auto-hidden)
    if (!controlsVisible) {
      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(end = if (focusCornerPanicEnabled) 76.dp else 0.dp)
          .height(48.dp)
          .testTag("focus_bottom_edge_trigger")
          .pointerInput(Unit) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false)
              revealControls()
            }
          },
        contentAlignment = Alignment.BottomCenter
      ) {
        Box(
          modifier = Modifier
            .padding(bottom = 8.dp)
            .width(52.dp)
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f))
        )
      }
    }

    // 4. Mini translucent bottom overlay (STOP, Master Volume, Exit Fokus) auto-hides after 3s
    AnimatedVisibility(
      visible = controlsVisible,
      enter = fadeIn() + slideInVertically { it / 2 },
      exit = fadeOut() + slideOutVertically { it / 2 },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(
          start = 12.dp,
          end = if (focusCornerPanicEnabled) 80.dp else 12.dp,
          bottom = 10.dp
        )
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 560.dp)
          .testTag("focus_mini_controls_bar"),
        shape = RoundedCornerShape(20.dp),
        color = extColors.focusOverlayBg,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 6.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilledTonalButton(
            onClick = {
              performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
              revealControls()
              onStopAll()
            },
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = extColors.panicRed,
              contentColor = extColors.onPanicRed
            ),
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .height(48.dp)
              .testTag("focus_mini_stop_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Stop,
              contentDescription = stringResource(R.string.cd_stop_all),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.bottom_action_stop),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              maxLines = 1
            )
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = if (activeVoices > 0) "$currentBankTitle · ${activeVoices}A" else currentBankTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = "${(masterVolume * 100f).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
              )
            }
            Slider(
              value = masterVolume,
              onValueChange = { vol ->
                isAdjustingSlider = true
                onMasterVolumeChange(vol.coerceIn(0f, 1f))
              },
              onValueChangeFinished = {
                isAdjustingSlider = false
                revealControls()
              },
              valueRange = 0f..1f,
              colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag("focus_mini_volume_slider")
            )
          }

          OutlinedButton(
            onClick = {
              performPadHaptic(haptic, hapticIntensity)
              onExitFocusMode()
            },
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .height(48.dp)
              .testTag("focus_mini_exit_button")
          ) {
            Icon(
              imageVector = Icons.Filled.FullscreenExit,
              contentDescription = stringResource(R.string.cd_focus_mode_exit),
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = stringResource(R.string.focus_mode_label),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              maxLines = 1
            )
          }
        }
      }
    }

    // 5. Optional corner 2-second long-press STOP/PANIC trigger
    if (focusCornerPanicEnabled) {
      var panicHoldProgress by remember { mutableFloatStateOf(0f) }
      val panicDesc = stringResource(R.string.settings_focus_panic_title)

      Box(
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .navigationBarsPadding()
          .padding(end = 10.dp, bottom = 10.dp)
          .size(60.dp)
          .clip(CircleShape)
          .background(
            if (panicHoldProgress > 0.02f) {
              extColors.panicRed.copy(alpha = 0.28f)
            } else {
              extColors.focusEdgeHintBg
            }
          )
          .semantics { contentDescription = panicDesc }
          .testTag("focus_corner_panic_zone")
          .pointerInput(hapticIntensity) {
            detectTapGestures(
              onPress = {
                var triggered = false
                val holdMs = 2000L
                val stepMs = 30L
                val steps = (holdMs / stepMs).coerceAtLeast(1L)
                val job = scope.launch {
                  for (step in 1..steps) {
                    delay(stepMs)
                    panicHoldProgress = (step.toFloat() / steps.toFloat()).coerceIn(0f, 1f)
                  }
                  triggered = true
                  panicHoldProgress = 0f
                  performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
                  onStopAll()
                }
                tryAwaitRelease()
                job.cancel()
                if (!triggered) {
                  panicHoldProgress = 0f
                }
              }
            )
          },
        contentAlignment = Alignment.Center
      ) {
        if (panicHoldProgress > 0.02f) {
          CircularProgressIndicator(
            progress = { panicHoldProgress },
            modifier = Modifier.size(54.dp),
            strokeWidth = 3.dp,
            color = extColors.panicRed,
            trackColor = extColors.panicRed.copy(alpha = 0.22f)
          )
        }
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = null,
            tint = extColors.panicRed.copy(alpha = if (panicHoldProgress > 0.02f) 1f else 0.72f),
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = stringResource(R.string.focus_corner_panic_hint),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = extColors.panicRed.copy(alpha = if (panicHoldProgress > 0.02f) 1f else 0.72f)
          )
        }
      }
    }
  }
}
