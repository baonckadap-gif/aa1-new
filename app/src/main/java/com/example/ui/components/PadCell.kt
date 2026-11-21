package com.example.ui.components

import android.os.SystemClock
import android.os.Trace
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.StereoPanMath
import com.example.domain.model.AnimationMode
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.HapticIntensity
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.RetriggerMode
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadColorPalette
import com.example.ui.theme.SoundPadProTheme
import com.example.ui.theme.SoundPadTheme
import com.example.ui.theme.padMetaStyleForGrid
import com.example.ui.theme.padTitleMinFontSizeSp
import com.example.ui.theme.padTitleStyleForGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PadDensity {
  STANDARD,
  COMPACT,
  MICRO
}

@Stable
data class PadUiState(
  val pad: SoundPad?,
  val slotNumber: Int,
  val playbackState: PadPlaybackState? = null,
  val density: PadDensity = PadDensity.STANDARD,
  val isEditMode: Boolean = false,
  val isMultiSelectMode: Boolean = false,
  val isSelected: Boolean = false,
  val isMidiLearnMode: Boolean = false,
  val isMidiLearnArmed: Boolean = false,
  val isCuePlaying: Boolean = false,
  val isDragging: Boolean = false,
  val isDropTarget: Boolean = false,
  val isLoading: Boolean = false,
  val isEnabled: Boolean = true
)

private sealed interface PadStatusBadge {
  data class VectorIcon(
    val icon: ImageVector,
    val description: String,
    val tint: Color
  ) : PadStatusBadge

  data class ShortTag(
    val label: String,
    val tint: Color
  ) : PadStatusBadge
}

fun performPadHaptic(
  haptic: HapticFeedback,
  intensity: HapticIntensity,
  strongType: HapticFeedbackType = HapticFeedbackType.LongPress
) {
  when (intensity) {
    HapticIntensity.OFF -> Unit
    HapticIntensity.SOFT -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    HapticIntensity.STRONG -> haptic.performHapticFeedback(strongType)
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PadCell(
  pad: SoundPad?,
  slotNumber: Int,
  playbackState: PadPlaybackState?,
  density: PadDensity,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean = false,
  isSelected: Boolean = false,
  isMidiLearnMode: Boolean = false,
  isMidiLearnArmed: Boolean = false,
  isCuePlaying: Boolean = false,
  isDragging: Boolean,
  isDropTarget: Boolean,
  globalCrossfadeEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  defaultPadColorMode: DefaultPadColorMode = DefaultPadColorMode.GRAY,
  defaultCustomPadColorArgb: Long = SoundPadColorPalette.DEFAULT_CUSTOM_PAD_COLOR_ARGB,
  miniWaveformEnabled: Boolean = true,
  isLoading: Boolean = false,
  isEnabled: Boolean = true,
  gridRows: Int = 4,
  gridCols: Int = 4,
  reduceMotion: Boolean = false,
  visualEffectsEnabled: Boolean = true,
  animationMode: AnimationMode = AnimationMode.FULL,
  progressProvider: (() -> Float)? = null,
  hapticIntensity: HapticIntensity = HapticIntensity.SOFT,
  swipePadVolumeEnabled: Boolean = false,
  doubleTapToStopEnabled: Boolean = true,
  globalRetriggerMode: RetriggerMode = RetriggerMode.RESTART,
  onClick: (SoundPad) -> Unit,
  onClickWithTime: ((SoundPad, Long) -> Unit)? = null,
  onEmptyClick: () -> Unit = {},
  onEmptyDoubleClick: (() -> Unit)? = null,
  onDoubleTapStop: (SoundPad) -> Unit = {},
  onSwipeVolumeChange: (SoundPad, Float, Boolean) -> Unit = { _, _, _ -> },
  onCustomizeClick: (SoundPad) -> Unit = {},
  onHoldStart: (SoundPad) -> Unit,
  onHoldStartWithTime: ((SoundPad, Long) -> Unit)? = null,
  onHoldRelease: (SoundPad) -> Unit,
  modifier: Modifier = Modifier
) {
  if (pad == null) {
    EmptyPadWell(
      slotNumber = slotNumber,
      density = density,
      style = defaultPadShape,
      isDropTarget = isDropTarget,
      hapticIntensity = hapticIntensity,
      onClick = onEmptyClick,
      onDoubleClick = onEmptyDoubleClick,
      modifier = modifier
    )
    return
  }

  val padTokens = SoundPadTheme.padTokens
  val spacing = SoundPadTheme.spacing
  val extColors = SoundPadTheme.colors
  val effectiveAnimationMode = when {
    !visualEffectsEnabled || reduceMotion -> AnimationMode.OFF
    else -> animationMode
  }
  val effectsActive = effectiveAnimationMode == AnimationMode.FULL
  val hasError = pad.errorMessage != null
  val isPlaying = playbackState?.isPlaying == true && !hasError && isEnabled
  val isFadingOut = playbackState?.isFadingOut == true
  val activeVoiceCount = if (isPlaying) (playbackState?.activeVoiceCount ?: 1).coerceAtLeast(1) else 0
  val effectiveLayerMode = (pad.settings.retriggerMode ?: globalRetriggerMode) == RetriggerMode.LAYER
  val latestPlaybackState by rememberUpdatedState(playbackState)
  val activeProgressProvider = progressProvider ?: { latestPlaybackState?.progress ?: 0f }
  val interactionSource = remember { MutableInteractionSource() }
  val haptic = LocalHapticFeedback.current
  val viewConfig = LocalViewConfiguration.current
  val coroutineScope = rememberCoroutineScope()

  var isPressedState by remember(pad.id) { mutableStateOf(false) }
  var isSwipingVolume by remember(pad.id) { mutableStateOf(false) }
  var swipedVolume by remember(pad.id, pad.settings.volume) { mutableFloatStateOf(pad.settings.volume) }
  var lastTapElapsedMs by remember(pad.id) { mutableLongStateOf(0L) }
  var wasPlayingAtLastTap by remember(pad.id) { mutableStateOf(false) }

  val manualTint = pad.colorArgb?.let { Color(it.toInt()) }
  val effectiveTint: Color? = when {
    manualTint != null -> manualTint
    defaultPadColorMode == DefaultPadColorMode.AUTO -> {
      val presets = SoundPadColorPalette.PAD_PRESETS_12
      val idx = ((slotNumber - 1).coerceAtLeast(0)) % presets.size
      Color(presets[idx].argb.toInt())
    }
    defaultPadColorMode == DefaultPadColorMode.CUSTOM -> Color(defaultCustomPadColorArgb.toInt())
    else -> null
  }
  val isNeutralGrayDefault = manualTint == null && defaultPadColorMode == DefaultPadColorMode.GRAY
  val accentColor = effectiveTint ?: MaterialTheme.colorScheme.primary
  val cornerGlowColor = pad.glowColorArgb?.let { Color(it.toInt()) }
    ?: if (isNeutralGrayDefault) MaterialTheme.colorScheme.primary else defaultCornerGlowColor
  val allowCornerGlow = !isNeutralGrayDefault || pad.glowColorArgb != null
  val glowFactor = if (isEnabled && effectsActive && allowCornerGlow) pad.glowIntensity.coerceIn(0f, 1.8f) else 0f
  val activeShapeStyle = pad.shapeStyle ?: defaultPadShape
  val customBorderTint = pad.borderColorArgb?.let { Color(it.toInt()) }
  val customTextColor = pad.textColorArgb?.let { Color(it.toInt()) }
  val contentAccent = if (isNeutralGrayDefault && !isPlaying) extColors.padDefaultGrayBorder else cornerGlowColor

  val targetScale = when {
    !isEnabled || effectiveAnimationMode != AnimationMode.FULL -> 1f
    isDragging -> 1.04f
    isPressedState -> 0.94f
    else -> 1f
  }
  val scale = if (effectiveAnimationMode == AnimationMode.FULL) {
    val animatedScale by animateFloatAsState(
      targetValue = targetScale,
      animationSpec = spring(stiffness = Spring.StiffnessHigh),
      label = "pad_scale"
    )
    animatedScale
  } else {
    1f
  }

  val targetBorderColor = when {
    !isEnabled -> MaterialTheme.colorScheme.outlineVariant
    hasError -> MaterialTheme.colorScheme.error
    isMultiSelectMode && isSelected -> MaterialTheme.colorScheme.primary
    isLoading -> MaterialTheme.colorScheme.primary
    isMidiLearnArmed -> MaterialTheme.colorScheme.secondary
    isCuePlaying -> extColors.cueMonitor
    isDropTarget -> MaterialTheme.colorScheme.secondary
    isFadingOut -> MaterialTheme.colorScheme.secondary
    isPlaying -> cornerGlowColor
    isEditMode || isMidiLearnMode -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
    customBorderTint != null -> customBorderTint
    effectiveTint != null -> effectiveTint.copy(alpha = 0.84f)
    isNeutralGrayDefault -> extColors.padDefaultGrayBorder
    else -> cornerGlowColor.copy(alpha = 0.60f)
  }
  val borderColor = when (effectiveAnimationMode) {
    AnimationMode.FULL -> {
      val animatedBorder by animateColorAsState(targetValue = targetBorderColor, label = "pad_border")
      animatedBorder
    }
    AnimationMode.LIGHT -> {
      val animatedBorder by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 65),
        label = "pad_border_light"
      )
      animatedBorder
    }
    AnimationMode.OFF -> targetBorderColor
  }

  val idleContainer = when {
    !isEnabled -> extColors.padDisabledContainer
    hasError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    isMultiSelectMode && isSelected -> lerp(MaterialTheme.colorScheme.primaryContainer, effectiveTint ?: MaterialTheme.colorScheme.primary, 0.32f)
    effectiveTint != null -> lerp(MaterialTheme.colorScheme.surfaceVariant, effectiveTint, 0.36f)
    isNeutralGrayDefault -> extColors.padDefaultGray
    else -> MaterialTheme.colorScheme.surfaceVariant
  }

  val activeContainer = when {
    !isEnabled -> extColors.padDisabledContainer
    effectiveTint != null -> lerp(MaterialTheme.colorScheme.primaryContainer, effectiveTint, 0.58f)
    isNeutralGrayDefault -> lerp(extColors.padDefaultGray, MaterialTheme.colorScheme.primaryContainer, 0.55f)
    else -> MaterialTheme.colorScheme.primaryContainer
  }

  val targetContainerColor = if (isPlaying) activeContainer else idleContainer
  val containerColor = when (effectiveAnimationMode) {
    AnimationMode.FULL -> {
      val animatedBg by animateColorAsState(targetValue = targetContainerColor, label = "pad_bg")
      animatedBg
    }
    AnimationMode.LIGHT -> {
      val animatedBg by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 65),
        label = "pad_bg_light"
      )
      animatedBg
    }
    AnimationMode.OFF -> targetContainerColor
  }

  val cornerRadius: Dp = when (density) {
    PadDensity.STANDARD -> pad.cornerRadiusDp.coerceIn(padTokens.minCornerDp, padTokens.maxCornerDp).dp
    PadDensity.COMPACT -> (pad.cornerRadiusDp * padTokens.compactCornerScale)
      .coerceIn(padTokens.minCornerDp, padTokens.compactCornerDp.value * 2f).dp
    PadDensity.MICRO -> padTokens.microCornerDp
  }
  val shape = remember(activeShapeStyle, cornerRadius) {
    padComposeShape(activeShapeStyle, cornerRadius)
  }

  val padDescription = when {
    !isEnabled -> "Pad $slotNumber, ${pad.displayName}, disabled"
    hasError -> "Pad $slotNumber, ${pad.displayName}, error"
    isMultiSelectMode && isSelected -> "Pad $slotNumber, ${pad.displayName}, selected"
    isLoading -> "Pad $slotNumber, ${pad.displayName}, loading"
    isPlaying && activeVoiceCount > 1 -> "Pad $slotNumber, ${pad.displayName}, playing, $activeVoiceCount voices"
    isPlaying -> "Pad $slotNumber, ${pad.displayName}, playing"
    else -> "Pad $slotNumber, ${pad.displayName}"
  }

  val latestPad by rememberUpdatedState(pad)
  val latestIsPlaying by rememberUpdatedState(isPlaying)
  val latestDoubleTapStop by rememberUpdatedState(doubleTapToStopEnabled)
  val latestEffectiveLayerMode by rememberUpdatedState(effectiveLayerMode)
  val latestHapticIntensity by rememberUpdatedState(hapticIntensity)
  val latestOnClick by rememberUpdatedState(onClick)
  val latestOnClickWithTime by rememberUpdatedState(onClickWithTime)
  val latestOnHoldStart by rememberUpdatedState(onHoldStart)
  val latestOnHoldStartWithTime by rememberUpdatedState(onHoldStartWithTime)
  val latestOnHoldRelease by rememberUpdatedState(onHoldRelease)
  val latestOnDoubleTapStop by rememberUpdatedState(onDoubleTapStop)
  val latestOnCustomizeClick by rememberUpdatedState(onCustomizeClick)

  val allowVerticalVolumeSwipe = swipePadVolumeEnabled &&
    isEnabled &&
    !hasError &&
    !isEditMode &&
    !isMidiLearnMode &&
    pad.settings.triggerMode != PadTriggerMode.HOLD

  val verticalVolumeSwipeMod = if (allowVerticalVolumeSwipe) {
    Modifier.pointerInput(pad.id, allowVerticalVolumeSwipe) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var accDx = 0f
        var accDy = 0f
        var draggingVertical = false
        var latestVol = latestPad.settings.volume
        val slop = viewConfiguration.touchSlop
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (!change.pressed) {
            if (draggingVertical) {
              isSwipingVolume = false
              onSwipeVolumeChange(latestPad, latestVol, true)
            }
            break
          }
          val delta = change.position - change.previousPosition
          accDx += delta.x
          accDy += delta.y
          if (!draggingVertical) {
            if (abs(accDy) > slop && abs(accDy) > abs(accDx) * 1.25f) {
              draggingVertical = true
              isSwipingVolume = true
              latestVol = latestPad.settings.volume
              swipedVolume = latestVol
            } else if (abs(accDx) > slop * 1.4f && abs(accDx) > abs(accDy)) {
              break
            }
          }
          if (draggingVertical) {
            change.consume()
            val normDelta = -delta.y / size.height.coerceAtLeast(100).toFloat()
            latestVol = (latestVol + normDelta * 1.2f).coerceIn(0f, 1f)
            swipedVolume = latestVol
            onSwipeVolumeChange(latestPad, latestVol, false)
          }
        }
      }
    }
  } else {
    Modifier
  }

  val interactionModifier = when {
    !isEnabled -> Modifier
    hasError || isEditMode || isMidiLearnMode -> {
      Modifier.pointerInput(pad.id, hasError, isEditMode, isMidiLearnMode) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          isPressedState = true
          val pressInteraction = PressInteraction.Press(down.position)
          coroutineScope.launch { interactionSource.emit(pressInteraction) }
          val downMs = SystemClock.elapsedRealtime()
          var movedTooFar = false
          var released = false
          val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - down.position
            if ((delta.x * delta.x + delta.y * delta.y) > slopSq) {
              movedTooFar = true
            }
            if (!change.pressed) {
              isPressedState = false
              released = true
              val heldShort = (SystemClock.elapsedRealtime() - downMs) < viewConfiguration.longPressTimeoutMillis
              if (!movedTooFar && heldShort) {
                coroutineScope.launch { performPadHaptic(haptic, latestHapticIntensity) }
                latestOnClick(latestPad)
              }
              coroutineScope.launch { interactionSource.emit(PressInteraction.Release(pressInteraction)) }
              break
            }
          }
          isPressedState = false
          if (!released) {
            coroutineScope.launch { interactionSource.emit(PressInteraction.Cancel(pressInteraction)) }
          }
        }
      }
    }
    pad.settings.triggerMode == PadTriggerMode.HOLD -> {
      Modifier.pointerInput(pad.id) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val downNanos = SystemClock.elapsedRealtimeNanos()
          isPressedState = true
          val pressInteraction = PressInteraction.Press(down.position)
          coroutineScope.launch { interactionSource.emit(pressInteraction) }
          Trace.beginSection("pad_tap")
          try {
            latestOnHoldStartWithTime?.invoke(latestPad, downNanos) ?: latestOnHoldStart(latestPad)
          } finally {
            Trace.endSection()
          }
          coroutineScope.launch { performPadHaptic(haptic, latestHapticIntensity) }
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
          }
          isPressedState = false
          latestOnHoldRelease(latestPad)
          coroutineScope.launch { interactionSource.emit(PressInteraction.Release(pressInteraction)) }
        }
      }
    }
    else -> {
      // Custom pointerInput triggers immediately at ACTION_DOWN without combinedClickable/double-tap latency.
      Modifier.pointerInput(pad.id) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val downNanos = SystemClock.elapsedRealtimeNanos()
          val nowMs = SystemClock.elapsedRealtime()
          isPressedState = true
          val pressInteraction = PressInteraction.Press(down.position)
          coroutineScope.launch { interactionSource.emit(pressInteraction) }

          val targetPad = latestPad
          val playingNow = latestIsPlaying
          val withinTimeout = lastTapElapsedMs > 0L && (nowMs - lastTapElapsedMs) <= viewConfig.doubleTapTimeoutMillis
          val isDoubleTapStop = latestDoubleTapStop && !latestEffectiveLayerMode && playingNow && wasPlayingAtLastTap && withinTimeout

          Trace.beginSection("pad_tap")
          try {
            if (isDoubleTapStop) {
              lastTapElapsedMs = 0L
              wasPlayingAtLastTap = false
              latestOnDoubleTapStop(targetPad)
            } else {
              lastTapElapsedMs = nowMs
              wasPlayingAtLastTap = playingNow
              latestOnClickWithTime?.invoke(targetPad, downNanos) ?: latestOnClick(targetPad)
            }
          } finally {
            Trace.endSection()
          }
          coroutineScope.launch { performPadHaptic(haptic, latestHapticIntensity) }

          var gestureEnded = false
          val longPressJob = coroutineScope.launch {
            delay(viewConfig.longPressTimeoutMillis)
            if (!gestureEnded) {
              gestureEnded = true
              isPressedState = false
              performPadHaptic(haptic, latestHapticIntensity)
              latestOnCustomizeClick(latestPad)
            }
          }

          val slopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val delta = change.position - down.position
            if ((delta.x * delta.x + delta.y * delta.y) > slopSq || !change.pressed) {
              gestureEnded = true
              longPressJob.cancel()
              break
            }
          }
          gestureEnded = true
          longPressJob.cancel()
          isPressedState = false
          coroutineScope.launch { interactionSource.emit(PressInteraction.Release(pressInteraction)) }
        }
      }
    }
  }

  val baseBorderWidth = pad.borderWidthDp.coerceIn(0f, 4f)
  val borderWidthDp = when {
    !isEnabled -> 0.8.dp
    hasError -> 2.0.dp
    isMultiSelectMode && isSelected -> 2.6.dp
    isLoading -> 1.8.dp
    isMidiLearnArmed -> (baseBorderWidth + 1.4f).coerceAtLeast(2.6f).dp
    isDropTarget -> 2.4.dp
    isPlaying -> (baseBorderWidth + padTokens.playingBorderExtraDp).coerceAtLeast(padTokens.playingBorderMinDp).dp
    isEditMode || isMidiLearnMode -> (baseBorderWidth + 0.35f).coerceAtLeast(1.4f).dp
    else -> baseBorderWidth.dp
  }

  val sheenHighlight = extColors.padSheenHighlight
  val sheenShadow = extColors.padSheenShadow
  val surfaceSheenBrush = remember(
    effectsActive,
    pad.surfaceFinish,
    accentColor,
    cornerGlowColor,
    isPlaying,
    extColors.isDark,
    sheenHighlight,
    sheenShadow
  ) {
    if (!effectsActive) null else when (pad.surfaceFinish) {
      PadSurfaceFinish.CLASSIC -> Brush.verticalGradient(
        colors = listOf(
          sheenHighlight.copy(alpha = if (isPlaying) 0.14f else 0.06f),
          Color.Transparent,
          sheenShadow.copy(alpha = if (extColors.isDark) 0.24f else 0.08f)
        )
      )
      PadSurfaceFinish.NEON_GRADIENT -> Brush.linearGradient(
        colors = listOf(
          accentColor.copy(alpha = if (isPlaying) 0.42f else 0.22f),
          Color.Transparent,
          cornerGlowColor.copy(alpha = if (isPlaying) 0.48f else 0.26f)
        )
      )
      PadSurfaceFinish.GLASS -> Brush.linearGradient(
        colors = listOf(
          sheenHighlight.copy(alpha = if (isPlaying) 0.28f else 0.18f),
          sheenHighlight.copy(alpha = 0.04f),
          cornerGlowColor.copy(alpha = if (isPlaying) 0.22f else 0.10f)
        )
      )
      PadSurfaceFinish.MATTE -> Brush.verticalGradient(
        colors = listOf(
          sheenShadow.copy(alpha = 0.04f),
          sheenShadow.copy(alpha = if (extColors.isDark) 0.12f else 0.04f)
        )
      )
    }
  }

  val drawCornerGlow = effectsActive &&
    isEnabled &&
    !hasError &&
    glowFactor > 0.02f &&
    (density == PadDensity.STANDARD || (density == PadDensity.COMPACT && isPlaying))

  val sweepFillColor = remember(accentColor, isFadingOut) {
    accentColor.copy(alpha = if (isFadingOut) 0.14f else 0.22f)
  }

  Box(
    modifier = modifier
      .defaultMinSize(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
      .graphicsLayer {
        if (effectsActive && scale != 1f) {
          scaleX = scale
          scaleY = scale
        }
        alpha = if (isEnabled) 1f else 0.46f
      }
      .clip(shape)
      .testTag("pad_item_${pad.positionIndex}")
      .semantics {
        contentDescription = padDescription
        if (isEnabled) {
          onClick(label = pad.displayName) {
            val downNanos = SystemClock.elapsedRealtimeNanos()
            when {
              hasError || isEditMode || isMidiLearnMode -> latestOnClick(latestPad)
              pad.settings.triggerMode == PadTriggerMode.HOLD -> {
                latestOnHoldStartWithTime?.invoke(latestPad, downNanos) ?: latestOnHoldStart(latestPad)
              }
              else -> {
                latestOnClickWithTime?.invoke(latestPad, downNanos) ?: latestOnClick(latestPad)
              }
            }
            true
          }
          if (!hasError && !isEditMode && !isMidiLearnMode && pad.settings.triggerMode != PadTriggerMode.HOLD) {
            onLongClick(label = pad.displayName) {
              latestOnCustomizeClick(latestPad)
              true
            }
          }
        }
      }
      .indication(
        interactionSource = interactionSource,
        indication = ripple(color = if (hasError) MaterialTheme.colorScheme.error else accentColor)
      )
      .then(verticalVolumeSwipeMod)
      .then(interactionModifier)
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = shape,
      color = containerColor,
      tonalElevation = 0.dp
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val cornerGlowDraws: List<Pair<Offset, Brush>> = if (drawCornerGlow) {
              val vertices = padCornerGlowVertices(activeShapeStyle, size)
              val radiusScale = if (isPlaying) 0.46f else 0.30f
              val cornerRadiusPx = (minOf(size.width, size.height) * radiusScale * glowFactor.coerceAtLeast(0.4f)).coerceAtLeast(8f)
              val baseAlpha = if (isPlaying) 0.56f else 0.30f
              val cornerAlpha = (baseAlpha * glowFactor).coerceIn(0f, 0.92f)
              vertices.map { vertex ->
                vertex to Brush.radialGradient(
                  colors = listOf(
                    cornerGlowColor.copy(alpha = cornerAlpha),
                    cornerGlowColor.copy(alpha = cornerAlpha * 0.24f),
                    Color.Transparent
                  ),
                  center = vertex,
                  radius = cornerRadiusPx
                )
              }
            } else {
              emptyList()
            }
            val glowRadiusPx = if (drawCornerGlow) {
              val radiusScale = if (isPlaying) 0.46f else 0.30f
              (minOf(size.width, size.height) * radiusScale * glowFactor.coerceAtLeast(0.4f)).coerceAtLeast(8f)
            } else 0f
            val haloStroke = if (isPlaying && effectsActive) {
              Stroke(
                width = (borderWidthDp + padTokens.playingGlowHaloStrokeDp).toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
              )
            } else null
            val borderStroke = if (borderWidthDp.value > 0.05f) {
              Stroke(
                width = borderWidthDp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
              )
            } else null

            onDrawBehind {
              if (surfaceSheenBrush != null && isEnabled) {
                drawRect(brush = surfaceSheenBrush)
              }
              if (isPlaying) {
                val p = activeProgressProvider().coerceIn(0f, 1f)
                if (p > 0f) {
                  drawRect(
                    color = sweepFillColor,
                    size = Size(width = size.width * p, height = size.height)
                  )
                }
              }
              if (cornerGlowDraws.isNotEmpty()) {
                for (i in cornerGlowDraws.indices) {
                  val (vertex, brush) = cornerGlowDraws[i]
                  drawCircle(brush = brush, radius = glowRadiusPx, center = vertex)
                }
              }
              if (haloStroke != null) {
                drawOutline(
                  outline = outline,
                  color = borderColor.copy(alpha = padTokens.playingGlowHaloAlpha),
                  style = haloStroke
                )
              }
              if (borderStroke != null) {
                drawOutline(
                  outline = outline,
                  color = borderColor,
                  style = borderStroke
                )
              }
            }
          }
      ) {
        val contentPadding = when (activeShapeStyle) {
          PadShapeStyle.TRIANGLE -> Modifier.padding(top = spacing.md, bottom = spacing.xxs, start = spacing.sm, end = spacing.sm)
          PadShapeStyle.STAR -> Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm)
          PadShapeStyle.PRISM -> Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
          PadShapeStyle.SQUARE -> Modifier
        }

        Box(modifier = Modifier.fillMaxSize().then(contentPadding)) {
          when (density) {
            PadDensity.STANDARD -> StandardPadContent(
              pad = pad,
              slotNumber = slotNumber,
              isPlaying = isPlaying,
              isFadingOut = isFadingOut,
              isCuePlaying = isCuePlaying,
              isLoading = isLoading,
              isEnabled = isEnabled,
              isEditMode = isEditMode,
              globalCrossfadeEnabled = globalCrossfadeEnabled,
              miniWaveformEnabled = miniWaveformEnabled,
              gridRows = gridRows,
              gridCols = gridCols,
              accentColor = contentAccent,
              customTextColor = customTextColor,
              playbackState = playbackState,
              progressProvider = activeProgressProvider
            )
            PadDensity.COMPACT -> CompactPadContent(
              pad = pad,
              slotNumber = slotNumber,
              isPlaying = isPlaying,
              activeVoiceCount = activeVoiceCount,
              isLoading = isLoading,
              gridRows = gridRows,
              gridCols = gridCols,
              accentColor = contentAccent,
              customTextColor = customTextColor
            )
            PadDensity.MICRO -> MicroPadContent(
              padId = pad.id,
              slotNumber = slotNumber,
              isPlaying = isPlaying,
              activeVoiceCount = activeVoiceCount,
              isLoading = isLoading,
              hasError = hasError,
              gridRows = gridRows,
              gridCols = gridCols,
              accentColor = contentAccent
            )
          }
        }

        if (isMultiSelectMode) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(spacing.xs)
              .size(if (density == PadDensity.MICRO) 14.dp else 20.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = padTokens.metaMinAlpha)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
              contentDescription = if (isSelected) "Selected" else "Not selected",
              tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(if (density == PadDensity.MICRO) 12.dp else 18.dp)
            )
          }
        }

        if (isSwipingVolume) {
          Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
          ) {
            Text(
              text = "VOL ${(swipedVolume * 100f).roundToInt()}%",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs)
            )
          }
        }

        if (isPlaying) {
          val trackColor = extColors.padProgressTrack
          val fillColor = if (isFadingOut) MaterialTheme.colorScheme.secondary else cornerGlowColor
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .fillMaxWidth()
              .height(if (density == PadDensity.MICRO) padTokens.bottomProgressMicroDp else padTokens.bottomProgressStandardDp)
              .drawBehind {
                drawRect(color = trackColor)
                val p = activeProgressProvider().coerceIn(0.03f, 1f)
                drawRect(color = fillColor, size = Size(width = size.width * p, height = size.height))
              }
          )
        }
      }
    }
  }
}

@Composable
private fun StandardPadContent(
  pad: SoundPad,
  slotNumber: Int,
  isPlaying: Boolean,
  isFadingOut: Boolean,
  isCuePlaying: Boolean,
  isLoading: Boolean,
  isEnabled: Boolean,
  isEditMode: Boolean,
  globalCrossfadeEnabled: Boolean,
  miniWaveformEnabled: Boolean,
  gridRows: Int,
  gridCols: Int,
  accentColor: Color,
  customTextColor: Color?,
  playbackState: PadPlaybackState?,
  progressProvider: () -> Float
) {
  val extColors = SoundPadTheme.colors
  val spacing = SoundPadTheme.spacing
  val padTokens = SoundPadTheme.padTokens
  val hasError = pad.errorMessage != null
  val isLooping = pad.settings.isLooping || pad.settings.triggerMode == PadTriggerMode.LOOP
  val hasFade = pad.settings.fadeInMs > 0L || pad.settings.fadeOutMs > 0L
  val isTrimmed = pad.settings.trimStartMs > 0L || pad.settings.trimEndMs > 0L
  val showCrossfadeIcon = globalCrossfadeEnabled && pad.settings.crossfadeEnabled
  val isPanned = abs(pad.settings.pan) >= 0.04f
  val showWaveform = miniWaveformEnabled && pad.miniWaveform.isNotEmpty() && !hasError

  val titleColor = if (!isEnabled) {
    extColors.padDisabledContent
  } else {
    customTextColor ?: MaterialTheme.colorScheme.onSurface
  }
  val metaTextColor = if (!isEnabled) {
    extColors.padDisabledContent
  } else {
    (customTextColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = padTokens.metaMinAlpha)
  }

  val badgeVector = padBadgeVector(pad.badgeIcon)
  val titleStyle = padTitleStyleForGrid(gridRows, gridCols, isPlaying)
  val minTitleFontSizeSp = padTitleMinFontSizeSp(gridRows, gridCols)
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)

  val loopContentDesc = stringResource(R.string.cd_loop_enabled)
  val cueContentDesc = stringResource(R.string.cd_cue_preview)

  val secondaryColor = MaterialTheme.colorScheme.secondary
  val primaryColor = MaterialTheme.colorScheme.primary
  val activeStatusBadges = remember(
    isCuePlaying,
    isLooping,
    pad.settings.isReversed,
    hasFade,
    pad.settings.eqEnabled,
    badgeVector,
    pad.settings.isDuckingSource,
    isTrimmed,
    showCrossfadeIcon,
    pad.settings.speed,
    isPanned,
    pad.settings.pan,
    pad.settings.echoEnabled,
    pad.settings.reverbEnabled,
    pad.midiNote,
    pad.isPreloadedInRam,
    accentColor,
    customTextColor,
    secondaryColor,
    primaryColor,
    extColors
  ) {
    buildList {
      if (isLooping) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.Repeat, loopContentDesc, secondaryColor))
      }
      if (pad.settings.isReversed) {
        add(PadStatusBadge.ShortTag("REV", secondaryColor))
      }
      if (hasFade) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.AutoGraph, "Fade envelope active", primaryColor))
      }
      if (pad.settings.eqEnabled) {
        add(PadStatusBadge.ShortTag("EQ", primaryColor))
      }
      if (isCuePlaying) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.Headphones, cueContentDesc, extColors.cueMonitor))
      }
      if (badgeVector != null) {
        add(PadStatusBadge.VectorIcon(badgeVector, pad.badgeIcon.label, customTextColor ?: accentColor))
      }
      if (pad.settings.echoEnabled || pad.settings.reverbEnabled) {
        add(PadStatusBadge.ShortTag("FX", extColors.cueMonitor))
      }
      if (isTrimmed) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.ContentCut, "Non-destructive cut active", accentColor))
      }
      if (pad.settings.isDuckingSource) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.RecordVoiceOver, "Ducking source", secondaryColor))
      }
      if (showCrossfadeIcon) {
        add(PadStatusBadge.VectorIcon(Icons.AutoMirrored.Filled.CompareArrows, "Crossfade enabled", primaryColor.copy(alpha = padTokens.metaMinAlpha)))
      }
      if (pad.settings.speed != 1.0f) {
        add(PadStatusBadge.ShortTag(String.format(Locale.US, "%.1fx", pad.settings.speed), secondaryColor))
      }
      if (isPanned) {
        add(PadStatusBadge.ShortTag(StereoPanMath.formatPanLabel(pad.settings.pan).replace(" ", ""), extColors.cueMonitor))
      }
      if (pad.midiNote != null) {
        add(PadStatusBadge.ShortTag("N${pad.midiNote}", accentColor))
      }
      if (pad.isPreloadedInRam) {
        add(PadStatusBadge.VectorIcon(Icons.Filled.Bolt, "RAM Preloaded", extColors.ramEmerald))
      }
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = padTokens.padHorizontalPaddingDp, vertical = padTokens.padVerticalPaddingDp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
      ) {
        Box(
          modifier = Modifier
            .size(padTokens.statusDotDp)
            .clip(CircleShape)
            .background(
              when {
                !isEnabled -> extColors.padDisabledContent
                hasError -> MaterialTheme.colorScheme.error
                isLoading -> MaterialTheme.colorScheme.primary
                isCuePlaying -> extColors.cueMonitor
                isFadingOut -> MaterialTheme.colorScheme.secondary
                isPlaying -> accentColor
                else -> accentColor.copy(alpha = 0.65f)
              }
            )
        )
        if (pad.showSlotNumber) {
          Text(
            text = String.format(Locale.US, "%02d", slotNumber),
            style = metaStyle,
            color = if (isPlaying) accentColor else metaTextColor
          )
        }
        if (pad.isFavorite) {
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.cd_favorite_pad),
            tint = extColors.favoriteGold,
            modifier = Modifier.size(padTokens.statusIconDp)
          )
        }
        if (pad.isClone) {
          Text(
            text = "DUP",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
      ) {
        when {
          hasError -> {
            Icon(
              imageVector = Icons.Filled.Warning,
              contentDescription = "Error",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(padTokens.actionIconDp)
            )
          }
          isLoading -> {
            CircularProgressIndicator(
              modifier = Modifier.size(padTokens.statusSpinnerDp),
              strokeWidth = 1.8.dp,
              color = MaterialTheme.colorScheme.primary
            )
          }
          isEditMode -> {
            Icon(
              imageVector = Icons.Filled.DragIndicator,
              contentDescription = "Drag to reorder",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(padTokens.actionIconDp)
            )
          }
          else -> {
            val activeVoices = if (isPlaying) (playbackState?.activeVoiceCount ?: 1) else 0
            if (activeVoices > 1) {
              val voiceDesc = stringResource(R.string.cd_pad_voice_count_badge, activeVoices)
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(padTokens.statusBadgeRadiusDp))
                  .background(accentColor.copy(alpha = 0.22f))
                  .padding(horizontal = spacing.xs, vertical = 1.dp)
                  .semantics { contentDescription = voiceDesc }
                  .testTag("pad_voice_count_badge_${pad.id}"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "×$activeVoices",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                  color = accentColor,
                  maxLines = 1
                )
              }
            }
            val visibleBadges = activeStatusBadges.take(padTokens.maxStatusIcons)
            val overflowCount = (activeStatusBadges.size - visibleBadges.size).coerceAtLeast(0)
            visibleBadges.forEach { badge ->
              when (badge) {
                is PadStatusBadge.VectorIcon -> {
                  Icon(
                    imageVector = badge.icon,
                    contentDescription = badge.description,
                    tint = badge.tint,
                    modifier = Modifier.size(padTokens.statusIconDp)
                  )
                }
                is PadStatusBadge.ShortTag -> {
                  Text(
                    text = badge.label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badge.tint
                  )
                }
              }
            }
            if (overflowCount > 0) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(padTokens.statusBadgeRadiusDp))
                  .background(extColors.padStatusChipBg)
                  .padding(horizontal = spacing.xxs, vertical = 1.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = "+$overflowCount",
                  style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                  color = metaTextColor
                )
              }
            }
          }
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
      AutoSizePadTitle(
        text = pad.displayName,
        style = titleStyle,
        minFontSizeSp = minTitleFontSizeSp,
        color = if (hasError) MaterialTheme.colorScheme.error else titleColor,
        maxLines = 2,
        modifier = Modifier.fillMaxWidth()
      )

      if (showWaveform) {
        val baseIdleWave = lerp(
          customTextColor ?: MaterialTheme.colorScheme.onSurface,
          accentColor,
          0.25f
        ).copy(alpha = padTokens.waveformIdleAlpha)

        MiniWaveformStrip(
          bars = pad.miniWaveform,
          progressProvider = if (isPlaying) progressProvider else { { 0f } },
          isPlaying = isPlaying,
          playedColor = accentColor.copy(alpha = padTokens.waveformPlayedAlpha),
          unplayedPlayingColor = accentColor.copy(alpha = padTokens.waveformDimAlpha),
          idleColor = baseIdleWave,
          playheadColor = extColors.playheadWhite.copy(alpha = padTokens.playheadAlpha),
          modifier = Modifier
            .fillMaxWidth()
            .height(padTokens.waveformHeightDp)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
      ) {
        when {
          hasError -> {
            Text(
              text = pad.errorType?.label ?: "Error",
              style = metaStyle.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.error,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          isLoading -> {
            Text(
              text = stringResource(R.string.pad_loading_label),
              style = metaStyle,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 1
            )
          }
          else -> {
            if (pad.showDurationLabel) {
              val effectiveDur = if (pad.settings.trimEndMs > pad.settings.trimStartMs) {
                pad.settings.trimEndMs - pad.settings.trimStartMs
              } else {
                (pad.durationMs - pad.settings.trimStartMs).coerceAtLeast(0L)
              }
              val displayTime = if (isPlaying && playbackState != null && playbackState.durationMs > 0L) {
                val remaining = (playbackState.durationMs - playbackState.positionMs).coerceAtLeast(0L)
                "-${formatDuration(remaining)}"
              } else {
                formatDuration(effectiveDur)
              }
              Text(
                text = displayTime,
                style = if (isPlaying) metaStyle.copy(fontWeight = FontWeight.Bold) else metaStyle,
                color = if (isPlaying) accentColor else metaTextColor,
                maxLines = 1
              )
            }
            if (pad.categoryTag.isNotBlank()) {
              Text(
                text = pad.categoryTag,
                style = metaStyle,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }
      }

      when {
        isPlaying && playbackState != null -> {
          val trackColor = accentColor.copy(alpha = padTokens.waveformDimAlpha)
          Box(contentAlignment = Alignment.Center, modifier = Modifier.size(padTokens.playingEqBoxDp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val strokePx = 2.dp.toPx()
              drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
              )
              val p = progressProvider().coerceIn(0f, 1f)
              if (p > 0f) {
                drawArc(
                  color = accentColor,
                  startAngle = -90f,
                  sweepAngle = p * 360f,
                  useCenter = false,
                  style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
              }
            }
            Icon(
              imageVector = Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(padTokens.playingEqIconDp)
            )
          }
        }
        else -> {
          Icon(
            imageVector = when {
              hasError -> Icons.Filled.Warning
              isCuePlaying -> Icons.Filled.Headphones
              else -> Icons.Filled.PlayArrow
            },
            contentDescription = null,
            tint = when {
              hasError -> MaterialTheme.colorScheme.error
              isCuePlaying -> extColors.cueMonitor
              else -> metaTextColor
            },
            modifier = Modifier.size(padTokens.actionIconDp)
          )
        }
      }
    }
  }
}

@Composable
private fun AutoSizePadTitle(
  text: String,
  style: TextStyle,
  minFontSizeSp: Float,
  color: Color,
  maxLines: Int,
  modifier: Modifier = Modifier
) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current

  BoxWithConstraints(modifier = modifier) {
    val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
    val startSizeSp = style.fontSize.value
    val resolvedStyle = remember(text, style, minFontSizeSp, maxLines, maxWidthPx) {
      var candidateSp = startSizeSp
      var matchedStyle = style
      while (candidateSp > minFontSizeSp) {
        val lineHeightSp = (candidateSp * 1.18f).sp
        val testStyle = style.copy(fontSize = candidateSp.sp, lineHeight = lineHeightSp)
        val result = textMeasurer.measure(
          text = text,
          style = testStyle,
          overflow = TextOverflow.Clip,
          softWrap = true,
          maxLines = maxLines,
          constraints = Constraints(maxWidth = maxWidthPx)
        )
        if (!result.hasVisualOverflow && result.lineCount <= maxLines) {
          matchedStyle = testStyle
          break
        }
        candidateSp = (candidateSp - 0.5f).coerceAtLeast(minFontSizeSp)
        matchedStyle = style.copy(
          fontSize = candidateSp.sp,
          lineHeight = (candidateSp * 1.18f).sp
        )
      }
      matchedStyle
    }

    Text(
      text = text,
      style = resolvedStyle,
      color = color,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
private fun MiniWaveformStrip(
  bars: List<Float>,
  progressProvider: () -> Float,
  isPlaying: Boolean,
  playedColor: Color,
  unplayedPlayingColor: Color,
  idleColor: Color,
  playheadColor: Color,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    if (bars.isEmpty()) return@Canvas
    val count = bars.size
    val slotW = size.width / count
    val barW = (slotW * 0.64f).coerceAtLeast(1.5f)
    val gap = (slotW - barW) * 0.5f
    val clampedProgress = progressProvider().coerceIn(0f, 1f)
    val playheadX = size.width * clampedProgress

    for (i in 0 until count) {
      val amp = bars[i].coerceIn(0.10f, 1.0f)
      val barH = (size.height * amp).coerceAtLeast(2f)
      val x = i * slotW + gap
      val y = (size.height - barH) * 0.5f
      val barProgress = (i + 0.5f) / count.toFloat()
      val barColor = when {
        !isPlaying -> idleColor
        barProgress <= clampedProgress -> playedColor
        else -> unplayedPlayingColor
      }
      drawLine(
        color = barColor,
        start = Offset(x + barW * 0.5f, y),
        end = Offset(x + barW * 0.5f, y + barH),
        strokeWidth = barW,
        cap = StrokeCap.Round
      )
    }

    if (isPlaying) {
      drawLine(
        color = playheadColor,
        start = Offset(playheadX, 0f),
        end = Offset(playheadX, size.height),
        strokeWidth = 2f,
        cap = StrokeCap.Round
      )
    }
  }
}

@Composable
private fun CompactPadContent(
  pad: SoundPad,
  slotNumber: Int,
  isPlaying: Boolean,
  activeVoiceCount: Int,
  isLoading: Boolean,
  gridRows: Int,
  gridCols: Int,
  accentColor: Color,
  customTextColor: Color?
) {
  val spacing = SoundPadTheme.spacing
  val padTokens = SoundPadTheme.padTokens
  val hasError = pad.errorMessage != null
  val titleColor = if (hasError) MaterialTheme.colorScheme.error else (customTextColor ?: MaterialTheme.colorScheme.onSurface)
  val secondaryColor = (customTextColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = padTokens.metaMinAlpha)
  val badgeVector = padBadgeVector(pad.badgeIcon)
  val titleStyle = padTitleStyleForGrid(gridRows, gridCols, isPlaying)
  val minTitleFontSizeSp = padTitleMinFontSizeSp(gridRows, gridCols)
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = spacing.xs, vertical = spacing.xs),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      when {
        hasError -> {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(9.dp)
          )
        }
        isLoading -> {
          CircularProgressIndicator(
            modifier = Modifier.size(8.dp),
            strokeWidth = 1.4.dp,
            color = MaterialTheme.colorScheme.primary
          )
        }
        else -> {
          Box(
            modifier = Modifier
              .size(5.dp)
              .clip(CircleShape)
              .background(
                if (isPlaying) accentColor else accentColor.copy(alpha = 0.55f)
              )
          )
        }
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
      ) {
        if (isPlaying && activeVoiceCount > 1) {
          val voiceDesc = stringResource(R.string.cd_pad_voice_count_badge, activeVoiceCount)
          Text(
            text = "×$activeVoiceCount",
            style = metaStyle.copy(fontWeight = FontWeight.ExtraBold),
            color = accentColor,
            modifier = Modifier
              .semantics { contentDescription = voiceDesc }
              .testTag("pad_voice_count_badge_${pad.id}")
          )
        }
        if (badgeVector != null && !hasError) {
          Icon(
            imageVector = badgeVector,
            contentDescription = pad.badgeIcon.label,
            tint = customTextColor ?: accentColor,
            modifier = Modifier.size(9.dp)
          )
        }
        if (pad.showSlotNumber) {
          Text(
            text = slotNumber.toString(),
            style = metaStyle,
            color = when {
              hasError -> MaterialTheme.colorScheme.error
              isPlaying -> accentColor
              else -> secondaryColor
            }
          )
        }
      }
    }
    AutoSizePadTitle(
      text = pad.displayName,
      style = titleStyle,
      minFontSizeSp = minTitleFontSizeSp,
      color = titleColor,
      maxLines = 2,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(spacing.xxs))
  }
}

@Composable
private fun MicroPadContent(
  padId: String,
  slotNumber: Int,
  isPlaying: Boolean,
  activeVoiceCount: Int,
  isLoading: Boolean,
  hasError: Boolean,
  gridRows: Int,
  gridCols: Int,
  accentColor: Color
) {
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)
  val padTokens = SoundPadTheme.padTokens
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier.size(10.dp),
        strokeWidth = 1.5.dp,
        color = MaterialTheme.colorScheme.primary
      )
    } else {
      val label = when {
        hasError -> "!"
        isPlaying && activeVoiceCount > 1 -> "×$activeVoiceCount"
        else -> slotNumber.toString()
      }
      val voiceDesc = if (isPlaying && activeVoiceCount > 1) {
        stringResource(R.string.cd_pad_voice_count_badge, activeVoiceCount)
      } else {
        null
      }
      Text(
        text = label,
        style = metaStyle.copy(fontWeight = if (isPlaying || hasError) FontWeight.Bold else FontWeight.Medium),
        color = when {
          hasError -> MaterialTheme.colorScheme.error
          isPlaying -> accentColor
          else -> MaterialTheme.colorScheme.onSurface.copy(alpha = padTokens.metaMinAlpha)
        },
        modifier = if (isPlaying && activeVoiceCount > 1) {
          Modifier
            .semantics { if (voiceDesc != null) contentDescription = voiceDesc }
            .testTag("pad_voice_count_badge_$padId")
        } else {
          Modifier
        }
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmptyPadWell(
  slotNumber: Int,
  density: PadDensity,
  style: PadShapeStyle,
  isDropTarget: Boolean = false,
  hapticIntensity: HapticIntensity,
  onClick: () -> Unit,
  onDoubleClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val padTokens = SoundPadTheme.padTokens
  val spacing = SoundPadTheme.spacing
  val extColors = SoundPadTheme.colors
  val haptic = LocalHapticFeedback.current
  val viewConfig = LocalViewConfiguration.current
  val interactionSource = remember { MutableInteractionSource() }
  val coroutineScope = rememberCoroutineScope()
  var lastTapElapsedMs by remember(slotNumber) { mutableLongStateOf(0L) }
  val latestOnClick by rememberUpdatedState(onClick)
  val latestOnDoubleClick by rememberUpdatedState(onDoubleClick)

  val cornerRadius = when (density) {
    PadDensity.STANDARD -> padTokens.standardCornerDp
    PadDensity.COMPACT -> padTokens.compactCornerDp
    PadDensity.MICRO -> padTokens.microCornerDp
  }
  val shape = remember(style, cornerRadius) { padComposeShape(style, cornerRadius) }
  val outlineColor = if (isDropTarget) {
    MaterialTheme.colorScheme.secondary
  } else {
    extColors.padEmptyWellBorder.copy(alpha = padTokens.metaMinAlpha)
  }
  val containerColor = if (isDropTarget) {
    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
  } else {
    extColors.padEmptyWellContainer
  }
  val outlineStrokeWidth = when {
    isDropTarget -> 2.5.dp
    density == PadDensity.STANDARD -> padTokens.emptyWellBorderDp
    else -> padTokens.emptyWellBorderCompactDp
  }
  val plusIconSize = when (density) {
    PadDensity.STANDARD -> padTokens.emptyIconStandardDp
    PadDensity.COMPACT -> padTokens.emptyIconCompactDp
    PadDensity.MICRO -> padTokens.emptyIconMicroDp
  }
  val addDescription = stringResource(R.string.cd_empty_pad_add)

  val clickMod = if (onDoubleClick != null) {
    Modifier
      .indication(interactionSource = interactionSource, indication = ripple(color = MaterialTheme.colorScheme.primary))
      .pointerInput(slotNumber, hapticIntensity) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          val pressInteraction = PressInteraction.Press(down.position)
          coroutineScope.launch { interactionSource.emit(pressInteraction) }
          val nowMs = SystemClock.elapsedRealtime()
          val isDoubleTap = lastTapElapsedMs > 0L && (nowMs - lastTapElapsedMs) <= viewConfig.doubleTapTimeoutMillis
          if (isDoubleTap) {
            lastTapElapsedMs = 0L
            coroutineScope.launch { performPadHaptic(haptic, hapticIntensity) }
            latestOnDoubleClick?.invoke()
          } else {
            lastTapElapsedMs = nowMs
            latestOnClick()
          }
          while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
          }
          coroutineScope.launch { interactionSource.emit(PressInteraction.Release(pressInteraction)) }
        }
      }
  } else {
    Modifier.clickable(
      interactionSource = interactionSource,
      indication = ripple(color = MaterialTheme.colorScheme.primary)
    ) {
      performPadHaptic(haptic, hapticIntensity)
      onClick()
    }
  }

  Box(
    modifier = modifier
      .defaultMinSize(minWidth = spacing.minTouchTarget, minHeight = spacing.minTouchTarget)
      .clip(shape)
      .background(containerColor)
      .testTag("empty_pad_well_$slotNumber")
      .semantics {
        contentDescription = "$addDescription ($slotNumber)"
        onClick(label = addDescription) {
          latestOnClick()
          true
        }
      }
      .then(clickMod),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val dashLenPx = padTokens.dashLengthDp.toPx()
      val dashGapPx = padTokens.dashGapDp.toPx()
      val outline = shape.createOutline(size, layoutDirection, this)
      drawOutline(
        outline = outline,
        color = outlineColor,
        style = Stroke(
          width = outlineStrokeWidth.toPx(),
          cap = StrokeCap.Round,
          join = StrokeJoin.Round,
          pathEffect = if (isDropTarget) null else PathEffect.dashPathEffect(floatArrayOf(dashLenPx, dashGapPx), 0f)
        )
      )
    }

    Icon(
      imageVector = Icons.Filled.Add,
      contentDescription = addDescription,
      tint = if (isDropTarget) {
        MaterialTheme.colorScheme.secondary
      } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = padTokens.metaMinAlpha)
      },
      modifier = Modifier.size(plusIconSize)
    )
  }
}

fun formatDuration(durationMs: Long): String {
  if (durationMs <= 0L) return "0:00"
  val totalSeconds = durationMs / 1000L
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun PadCellStatesPreview() {
  SoundPadProTheme(themeMode = ThemeMode.AMOLED) {
    val spacing = SoundPadTheme.spacing
    val cyanPreset = SoundPadColorPalette.PAD_PRESETS_12[0].argb
    val amberPreset = SoundPadColorPalette.PAD_PRESETS_12[1].argb
    val sampleIdle = SoundPad(
      id = "p1",
      uriString = "file:///kick.wav",
      fileName = "kick.wav",
      displayName = "Kick & Sub Bass Layer Ultra Long Track Title",
      extension = "WAV",
      durationMs = 18_000L,
      sizeBytes = 500_000L,
      positionIndex = 0,
      colorArgb = cyanPreset,
      isFavorite = true,
      miniWaveform = listOf(0.2f, 0.8f, 0.5f, 0.9f, 0.4f, 0.7f, 0.3f, 0.6f),
      settings = PadSettings(isLooping = true, eqEnabled = true, isReversed = true, fadeInMs = 300L, echoEnabled = true)
    )
    val samplePlay = sampleIdle.copy(
      id = "p2",
      displayName = "Vocal Chorus Hook",
      colorArgb = amberPreset,
      isClone = true,
      positionIndex = 1,
      settings = PadSettings(isReversed = true, reverbEnabled = true, fadeInMs = 300L)
    )
    val sampleError = sampleIdle.copy(
      id = "p3",
      displayName = "Corrupt Audio",
      errorType = PadErrorType.CORRUPT_HEADER,
      errorMessage = "Header rusak",
      positionIndex = 2
    )
    Column(modifier = Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        PadCell(
          pad = sampleIdle,
          slotNumber = 1,
          playbackState = null,
          density = PadDensity.STANDARD,
          isEditMode = false,
          isDragging = false,
          isDropTarget = false,
          globalCrossfadeEnabled = true,
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = MaterialTheme.colorScheme.primary,
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(104.dp).aspectRatio(1f)
        )
        PadCell(
          pad = samplePlay,
          slotNumber = 2,
          playbackState = PadPlaybackState("p2", isPlaying = true, positionMs = 7000L, durationMs = 18000L, progress = 0.62f),
          density = PadDensity.STANDARD,
          isEditMode = false,
          isDragging = false,
          isDropTarget = false,
          globalCrossfadeEnabled = false,
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = Color(amberPreset.toInt()),
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(104.dp).aspectRatio(1f)
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        PadCell(
          pad = sampleError,
          slotNumber = 3,
          playbackState = null,
          density = PadDensity.STANDARD,
          isEditMode = false,
          isDragging = false,
          isDropTarget = false,
          globalCrossfadeEnabled = false,
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = MaterialTheme.colorScheme.primary,
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(104.dp).aspectRatio(1f)
        )
        PadCell(
          pad = null,
          slotNumber = 4,
          playbackState = null,
          density = PadDensity.STANDARD,
          isEditMode = false,
          isDragging = false,
          isDropTarget = false,
          globalCrossfadeEnabled = false,
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = MaterialTheme.colorScheme.primary,
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(104.dp).aspectRatio(1f)
        )
      }
    }
  }
}
