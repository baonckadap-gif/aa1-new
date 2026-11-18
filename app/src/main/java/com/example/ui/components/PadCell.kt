package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.StereoPanMath
import com.example.domain.model.HapticIntensity
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadProTheme
import com.example.ui.theme.SoundPadTheme
import com.example.ui.theme.padMetaStyleForGrid
import com.example.ui.theme.padTitleStyleForGrid
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class PadDensity {
  STANDARD,
  COMPACT,
  MICRO
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
  miniWaveformEnabled: Boolean = true,
  isLoading: Boolean = false,
  isEnabled: Boolean = true,
  gridRows: Int = 4,
  gridCols: Int = 4,
  reduceMotion: Boolean = false,
  hapticIntensity: HapticIntensity = HapticIntensity.SOFT,
  swipePadVolumeEnabled: Boolean = false,
  doubleTapToStopEnabled: Boolean = true,
  onClick: (SoundPad) -> Unit,
  onDoubleTapStop: (SoundPad) -> Unit = {},
  onSwipeVolumeChange: (SoundPad, Float, Boolean) -> Unit = { _, _, _ -> },
  onCustomizeClick: (SoundPad) -> Unit = {},
  onHoldStart: (SoundPad) -> Unit,
  onHoldRelease: (SoundPad) -> Unit,
  modifier: Modifier = Modifier
) {
  if (pad == null) {
    EmptyPadWell(density = density, style = defaultPadShape, modifier = modifier)
    return
  }

  val hasError = pad.errorMessage != null
  val isPlaying = playbackState?.isPlaying == true && !hasError && isEnabled
  val isFadingOut = playbackState?.isFadingOut == true
  val progress = playbackState?.progress ?: 0f
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val haptic = LocalHapticFeedback.current
  val extColors = SoundPadTheme.colors

  var isSwipingVolume by remember(pad.id) { mutableStateOf(false) }
  var swipedVolume by remember(pad.id, pad.settings.volume) { mutableFloatStateOf(pad.settings.volume) }

  val customTint = pad.colorArgb?.let { Color(it.toInt()) }
  val accentColor = customTint ?: MaterialTheme.colorScheme.primary
  val cornerGlowColor = pad.glowColorArgb?.let { Color(it.toInt()) } ?: defaultCornerGlowColor
  val glowFactor = if (isEnabled) pad.glowIntensity.coerceIn(0f, 1.8f) else 0f
  val activeShapeStyle = pad.shapeStyle ?: defaultPadShape
  val customBorderTint = pad.borderColorArgb?.let { Color(it.toInt()) }
  val customTextColor = pad.textColorArgb?.let { Color(it.toInt()) }

  val targetScale = when {
    !isEnabled || reduceMotion -> 1f
    isDragging -> 1.04f
    isPressed -> 0.94f
    else -> 1f
  }
  val scale by animateFloatAsState(
    targetValue = targetScale,
    animationSpec = spring(stiffness = Spring.StiffnessHigh),
    label = "pad_scale"
  )

  val shouldWiggle = isEditMode && !reduceMotion && !isDragging && !isMultiSelectMode && density != PadDensity.MICRO
  val wiggleRotation = if (shouldWiggle) {
    val wiggleTransition = rememberInfiniteTransition(label = "pad_edit_wiggle")
    val phaseShift = if (slotNumber % 2 == 0) 1 else -1
    val wiggle by wiggleTransition.animateFloat(
      initialValue = -1.1f * phaseShift,
      targetValue = 1.1f * phaseShift,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = 190),
        repeatMode = RepeatMode.Reverse
      ),
      label = "pad_wiggle_deg"
    )
    wiggle
  } else {
    0f
  }

  val borderColor by animateColorAsState(
    targetValue = when {
      !isEnabled -> MaterialTheme.colorScheme.outlineVariant
      hasError -> MaterialTheme.colorScheme.error
      isMultiSelectMode && isSelected -> MaterialTheme.colorScheme.primary
      isLoading -> MaterialTheme.colorScheme.primary
      isMidiLearnArmed -> MaterialTheme.colorScheme.secondary
      isCuePlaying -> extColors.cueMonitor
      isDropTarget -> MaterialTheme.colorScheme.secondary
      isFadingOut -> MaterialTheme.colorScheme.secondary
      isPlaying -> cornerGlowColor
      isEditMode || isMidiLearnMode -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f)
      customBorderTint != null -> customBorderTint
      customTint != null -> customTint.copy(alpha = 0.82f)
      else -> cornerGlowColor.copy(alpha = 0.58f)
    },
    label = "pad_border"
  )

  val idleContainer = when {
    !isEnabled -> extColors.padDisabledContainer
    hasError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
    isMultiSelectMode && isSelected -> lerp(MaterialTheme.colorScheme.primaryContainer, customTint ?: MaterialTheme.colorScheme.primary, 0.32f)
    customTint != null -> lerp(MaterialTheme.colorScheme.surfaceVariant, customTint, 0.36f)
    else -> MaterialTheme.colorScheme.surfaceVariant
  }

  val activeContainer = when {
    !isEnabled -> extColors.padDisabledContainer
    customTint != null -> lerp(MaterialTheme.colorScheme.primaryContainer, customTint, 0.58f)
    else -> MaterialTheme.colorScheme.primaryContainer
  }

  val containerColor by animateColorAsState(
    targetValue = if (isPlaying) activeContainer else idleContainer,
    label = "pad_bg"
  )

  val cornerRadius: Dp = when (density) {
    PadDensity.STANDARD -> pad.cornerRadiusDp.coerceIn(2f, 28f).dp
    PadDensity.COMPACT -> (pad.cornerRadiusDp * 0.58f).coerceIn(2f, 16f).dp
    PadDensity.MICRO -> 4.dp
  }
  val shape = padComposeShape(activeShapeStyle, cornerRadius)

  val padDescription = when {
    !isEnabled -> "Pad $slotNumber, ${pad.displayName}, disabled"
    hasError -> "Pad $slotNumber, ${pad.displayName}, error"
    isMultiSelectMode && isSelected -> "Pad $slotNumber, ${pad.displayName}, selected"
    isLoading -> "Pad $slotNumber, ${pad.displayName}, loading"
    isPlaying -> "Pad $slotNumber, ${pad.displayName}, playing"
    else -> "Pad $slotNumber, ${pad.displayName}"
  }

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
        var latestVol = pad.settings.volume
        val slop = viewConfiguration.touchSlop
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == down.id } ?: break
          if (!change.pressed) {
            if (draggingVertical) {
              isSwipingVolume = false
              onSwipeVolumeChange(pad, latestVol, true)
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
              latestVol = pad.settings.volume
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
            onSwipeVolumeChange(pad, latestVol, false)
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
      Modifier.clickable(
        interactionSource = interactionSource,
        indication = ripple(color = if (hasError) MaterialTheme.colorScheme.error else accentColor)
      ) {
        performPadHaptic(haptic, hapticIntensity)
        onClick(pad)
      }
    }
    pad.settings.triggerMode == PadTriggerMode.HOLD -> {
      Modifier.pointerInput(pad.id, hapticIntensity) {
        detectTapGestures(
          onPress = {
            performPadHaptic(haptic, hapticIntensity)
            onHoldStart(pad)
            tryAwaitRelease()
            onHoldRelease(pad)
          }
        )
      }
    }
    else -> {
      Modifier.combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(color = accentColor),
        onClick = {
          performPadHaptic(haptic, hapticIntensity)
          onClick(pad)
        },
        onDoubleClick = if (doubleTapToStopEnabled) {
          {
            if (isPlaying) {
              performPadHaptic(haptic, hapticIntensity)
              onDoubleTapStop(pad)
            } else {
              performPadHaptic(haptic, hapticIntensity)
              onClick(pad)
            }
          }
        } else null,
        onLongClick = {
          performPadHaptic(haptic, hapticIntensity)
          onCustomizeClick(pad)
        }
      )
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
    isPlaying -> (baseBorderWidth + 0.85f).coerceAtLeast(1.9f).dp
    isEditMode || isMidiLearnMode -> (baseBorderWidth + 0.35f).coerceAtLeast(1.4f).dp
    else -> baseBorderWidth.dp
  }

  val surfaceSheenBrush = when (pad.surfaceFinish) {
    PadSurfaceFinish.CLASSIC -> Brush.verticalGradient(
      colors = listOf(
        Color.White.copy(alpha = if (isPlaying) 0.14f else 0.06f),
        Color.Transparent,
        Color.Black.copy(alpha = if (extColors.isDark) 0.24f else 0.08f)
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
        Color.White.copy(alpha = if (isPlaying) 0.28f else 0.18f),
        Color.White.copy(alpha = 0.04f),
        cornerGlowColor.copy(alpha = if (isPlaying) 0.22f else 0.10f)
      )
    )
    PadSurfaceFinish.MATTE -> Brush.verticalGradient(
      colors = listOf(
        Color.Black.copy(alpha = 0.04f),
        Color.Black.copy(alpha = if (extColors.isDark) 0.12f else 0.04f)
      )
    )
  }

  Box(
    modifier = modifier
      .scale(scale)
      .graphicsLayer { rotationZ = wiggleRotation }
      .alpha(if (isEnabled) 1f else 0.46f)
      .drawBehind {
        if (!reduceMotion && isEnabled && !hasError && density != PadDensity.MICRO && glowFactor > 0.02f) {
          val vertices = padCornerGlowVertices(activeShapeStyle, size)
          val radiusScale = if (isPlaying) 0.44f else 0.28f
          val haloRadius = (minOf(size.width, size.height) * radiusScale * glowFactor.coerceAtLeast(0.4f)).coerceAtLeast(8f)
          val haloAlpha = ((if (isPlaying) 0.50f else 0.22f) * glowFactor).coerceIn(0f, 0.90f)
          vertices.forEach { v ->
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(
                  cornerGlowColor.copy(alpha = haloAlpha),
                  cornerGlowColor.copy(alpha = haloAlpha * 0.35f),
                  Color.Transparent
                ),
                center = v,
                radius = haloRadius
              ),
              radius = haloRadius,
              center = v
            )
          }
        }
      }
      .clip(shape)
      .testTag("pad_item_${pad.positionIndex}")
      .semantics { contentDescription = padDescription }
      .then(verticalVolumeSwipeMod)
      .then(interactionModifier)
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      shape = shape,
      color = containerColor,
      tonalElevation = if (isPlaying || isDragging) 6.dp else 1.dp
    ) {
      Box(modifier = Modifier.fillMaxSize()) {
        if (isEnabled) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(surfaceSheenBrush)
          )
        }

        if (isPlaying && progress > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth(progress.coerceIn(0f, 1f))
              .fillMaxHeight()
              .background(accentColor.copy(alpha = if (isFadingOut) 0.14f else 0.24f))
          )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
          val outline = shape.createOutline(size, layoutDirection, this)
          if (!reduceMotion && isEnabled && !hasError && density != PadDensity.MICRO && glowFactor > 0.02f) {
            val vertices = padCornerGlowVertices(activeShapeStyle, size)
            val cornerRadiusPx = (minOf(size.width, size.height) * (if (isPlaying) 0.44f else 0.30f) * glowFactor.coerceAtLeast(0.4f)).coerceAtLeast(8f)
            val cornerAlpha = ((if (isPlaying) 0.62f else 0.32f) * glowFactor).coerceIn(0f, 0.92f)
            vertices.forEach { vertex ->
              drawCircle(
                brush = Brush.radialGradient(
                  colors = listOf(
                    cornerGlowColor.copy(alpha = cornerAlpha),
                    cornerGlowColor.copy(alpha = cornerAlpha * 0.24f),
                    Color.Transparent
                  ),
                  center = vertex,
                  radius = cornerRadiusPx
                ),
                radius = cornerRadiusPx,
                center = vertex
              )
            }
          }

          if (borderWidthDp.value > 0.05f) {
            drawOutline(
              outline = outline,
              color = borderColor,
              style = Stroke(
                width = borderWidthDp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
              )
            )
          }
        }

        val contentPadding = when (activeShapeStyle) {
          PadShapeStyle.TRIANGLE -> Modifier.padding(top = 12.dp, bottom = 2.dp, start = 10.dp, end = 10.dp)
          PadShapeStyle.STAR -> Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
          PadShapeStyle.PRISM -> Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
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
              accentColor = cornerGlowColor,
              customTextColor = customTextColor,
              playbackState = playbackState
            )
            PadDensity.COMPACT -> CompactPadContent(
              pad = pad,
              slotNumber = slotNumber,
              isPlaying = isPlaying,
              isLoading = isLoading,
              gridRows = gridRows,
              gridCols = gridCols,
              accentColor = cornerGlowColor,
              customTextColor = customTextColor
            )
            PadDensity.MICRO -> MicroPadContent(
              slotNumber = slotNumber,
              isPlaying = isPlaying,
              isLoading = isLoading,
              hasError = hasError,
              gridRows = gridRows,
              gridCols = gridCols,
              accentColor = cornerGlowColor
            )
          }
        }

        if (isMultiSelectMode) {
          Box(
            modifier = Modifier
              .align(Alignment.TopEnd)
              .padding(4.dp)
              .size(if (density == PadDensity.MICRO) 14.dp else 20.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
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
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
          ) {
            Text(
              text = "VOL ${(swipedVolume * 100f).roundToInt()}%",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        if (isPlaying) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .fillMaxWidth()
              .height(if (density == PadDensity.MICRO) 2.dp else 3.dp)
              .background(Color.Black.copy(alpha = 0.35f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .background(if (isFadingOut) MaterialTheme.colorScheme.secondary else cornerGlowColor)
            )
          }
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
  playbackState: PadPlaybackState?
) {
  val extColors = SoundPadTheme.colors
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
  val secondaryTextColor = if (!isEnabled) {
    extColors.padDisabledContent
  } else {
    customTextColor?.copy(alpha = 0.78f) ?: MaterialTheme.colorScheme.onSurfaceVariant
  }
  val badgeVector = padBadgeVector(pad.badgeIcon)
  val titleStyle = padTitleStyleForGrid(gridRows, gridCols, isPlaying)
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = SoundPadTheme.spacing.sm, vertical = 6.dp),
    verticalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(
              when {
                !isEnabled -> extColors.padDisabledContent
                hasError -> MaterialTheme.colorScheme.error
                isLoading -> MaterialTheme.colorScheme.primary
                isCuePlaying -> extColors.cueMonitor
                isFadingOut -> MaterialTheme.colorScheme.secondary
                isPlaying -> accentColor
                else -> accentColor.copy(alpha = 0.55f)
              }
            )
        )
        if (pad.showSlotNumber) {
          Text(
            text = String.format(Locale.US, "%02d", slotNumber),
            style = metaStyle,
            color = if (isPlaying) accentColor else secondaryTextColor
          )
        }
        if (pad.isFavorite) {
          Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(R.string.cd_favorite_pad),
            tint = extColors.favoriteGold,
            modifier = Modifier.size(11.dp)
          )
        }
        if (pad.isClone) {
          Text(
            text = "DUP",
            style = metaStyle.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.secondary
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        if (hasError) {
          Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = "File bermasalah",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp)
          )
        } else if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(12.dp),
            strokeWidth = 1.8.dp,
            color = MaterialTheme.colorScheme.primary
          )
        } else if (isEditMode) {
          Icon(
            imageVector = Icons.Filled.DragIndicator,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(13.dp)
          )
        } else {
          if (isCuePlaying) {
            Icon(
              imageVector = Icons.Filled.Headphones,
              contentDescription = "Cue headphone preview active",
              tint = extColors.cueMonitor,
              modifier = Modifier.size(12.dp)
            )
          }
          if (badgeVector != null) {
            Icon(
              imageVector = badgeVector,
              contentDescription = pad.badgeIcon.label,
              tint = customTextColor ?: accentColor,
              modifier = Modifier.size(12.dp)
            )
          }
          if (pad.settings.isDuckingSource) {
            Icon(
              imageVector = Icons.Filled.RecordVoiceOver,
              contentDescription = "Ducking source",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(11.dp)
            )
          }
          if (isLooping) {
            Icon(
              imageVector = Icons.Filled.Repeat,
              contentDescription = stringResource(R.string.cd_loop_enabled),
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(11.dp)
            )
          }
          if (isTrimmed) {
            Icon(
              imageVector = Icons.Filled.ContentCut,
              contentDescription = "Non-destructive cut active",
              tint = accentColor,
              modifier = Modifier.size(11.dp)
            )
          }
          if (hasFade) {
            Icon(
              imageVector = Icons.Filled.AutoGraph,
              contentDescription = "Fade envelope active",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(11.dp)
            )
          }
          if (showCrossfadeIcon) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.CompareArrows,
              contentDescription = "Crossfade enabled",
              tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
              modifier = Modifier.size(11.dp)
            )
          }
          if (pad.settings.speed != 1.0f) {
            Text(
              text = String.format(Locale.US, "%.1fx", pad.settings.speed),
              style = metaStyle.copy(fontSize = 8.sp),
              color = MaterialTheme.colorScheme.secondary
            )
          }
          if (isPanned) {
            Text(
              text = StereoPanMath.formatPanLabel(pad.settings.pan).replace(" ", ""),
              style = metaStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = extColors.cueMonitor
            )
          }
          if (pad.settings.isReversed) {
            Text(
              text = "REV",
              style = metaStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.secondary
            )
          }
          if (pad.settings.eqEnabled) {
            Text(
              text = "EQ",
              style = metaStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary
            )
          }
          if (pad.settings.echoEnabled || pad.settings.reverbEnabled) {
            Text(
              text = "FX",
              style = metaStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = extColors.cueMonitor
            )
          }
          if (pad.midiNote != null) {
            Text(
              text = "N${pad.midiNote}",
              style = metaStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
              color = accentColor
            )
          }
          if (pad.isPreloadedInRam) {
            Icon(
              imageVector = Icons.Filled.Bolt,
              contentDescription = "RAM Preloaded",
              tint = extColors.ramEmerald,
              modifier = Modifier.size(11.dp)
            )
          }
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = pad.displayName,
        style = titleStyle,
        color = if (hasError) MaterialTheme.colorScheme.error else titleColor,
        maxLines = if (showWaveform) 1 else 2,
        overflow = TextOverflow.Ellipsis
      )

      if (showWaveform) {
        MiniWaveformStrip(
          bars = pad.miniWaveform,
          progress = if (isPlaying) (playbackState?.progress ?: 0f) else 0f,
          isPlaying = isPlaying,
          activeColor = accentColor,
          idleColor = (customTextColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.36f),
          playheadColor = extColors.playheadWhite,
          modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
              text = "Memuat...",
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
                style = metaStyle,
                color = if (isPlaying) accentColor else secondaryTextColor
              )
            }
            if (pad.categoryTag.isNotBlank()) {
              Text(
                text = pad.categoryTag,
                style = metaStyle.copy(fontSize = 8.sp),
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
          Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
            CircularProgressIndicator(
              progress = { playbackState.progress.coerceIn(0f, 1f) },
              modifier = Modifier.fillMaxSize(),
              strokeWidth = 2.dp,
              color = accentColor,
              trackColor = accentColor.copy(alpha = 0.22f)
            )
            Icon(
              imageVector = Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(9.dp)
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
              else -> secondaryTextColor.copy(alpha = 0.65f)
            },
            modifier = Modifier.size(14.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun MiniWaveformStrip(
  bars: List<Float>,
  progress: Float,
  isPlaying: Boolean,
  activeColor: Color,
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
    val clampedProgress = progress.coerceIn(0f, 1f)
    val playheadX = size.width * clampedProgress

    for (i in 0 until count) {
      val amp = bars[i].coerceIn(0.10f, 1.0f)
      val barH = (size.height * amp).coerceAtLeast(2f)
      val x = i * slotW + gap
      val y = (size.height - barH) * 0.5f
      val barProgress = (i + 0.5f) / count.toFloat()
      val barColor = if (isPlaying && barProgress <= clampedProgress) activeColor else idleColor
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
        color = playheadColor.copy(alpha = 0.90f),
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
  isLoading: Boolean,
  gridRows: Int,
  gridCols: Int,
  accentColor: Color,
  customTextColor: Color?
) {
  val hasError = pad.errorMessage != null
  val titleColor = if (hasError) MaterialTheme.colorScheme.error else (customTextColor ?: MaterialTheme.colorScheme.onSurface)
  val secondaryColor = customTextColor?.copy(alpha = 0.78f) ?: MaterialTheme.colorScheme.onSurfaceVariant
  val badgeVector = padBadgeVector(pad.badgeIcon)
  val titleStyle = padTitleStyleForGrid(gridRows, gridCols, isPlaying)
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 4.dp, vertical = 3.dp),
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
            contentDescription = "File bermasalah",
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
                if (isPlaying) accentColor else accentColor.copy(alpha = 0.45f)
              )
          )
        }
      }
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
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
    Text(
      text = pad.displayName,
      style = titleStyle,
      color = titleColor,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis
    )
    Spacer(modifier = Modifier.height(2.dp))
  }
}

@Composable
private fun MicroPadContent(
  slotNumber: Int,
  isPlaying: Boolean,
  isLoading: Boolean,
  hasError: Boolean,
  gridRows: Int,
  gridCols: Int,
  accentColor: Color
) {
  val metaStyle = padMetaStyleForGrid(gridRows, gridCols)
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
      Text(
        text = if (hasError) "!" else slotNumber.toString(),
        style = metaStyle.copy(fontWeight = if (isPlaying || hasError) FontWeight.Bold else FontWeight.Medium),
        color = when {
          hasError -> MaterialTheme.colorScheme.error
          isPlaying -> accentColor
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
      )
    }
  }
}

@Composable
private fun EmptyPadWell(
  density: PadDensity,
  style: PadShapeStyle,
  modifier: Modifier = Modifier
) {
  val cornerRadius = when (density) {
    PadDensity.STANDARD -> 14.dp
    PadDensity.COMPACT -> 8.dp
    PadDensity.MICRO -> 4.dp
  }
  val shape = padComposeShape(style, cornerRadius)
  Box(
    modifier = modifier
      .clip(shape)
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
      .border(
        width = 0.75.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        shape = shape
      )
  )
}

fun formatDuration(durationMs: Long): String {
  if (durationMs <= 0L) return "0:00"
  val totalSeconds = durationMs / 1000L
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PadCellStatesPreview() {
  SoundPadProTheme(themeMode = ThemeMode.AMOLED) {
    val sampleIdle = SoundPad(
      id = "p1",
      uriString = "file:///kick.wav",
      fileName = "kick.wav",
      displayName = "Kick & Sub",
      extension = "WAV",
      durationMs = 18_000L,
      sizeBytes = 500_000L,
      positionIndex = 0,
      colorArgb = 0xFF00E5FFL,
      isFavorite = true,
      miniWaveform = listOf(0.2f, 0.8f, 0.5f, 0.9f, 0.4f, 0.7f, 0.3f, 0.6f),
      settings = PadSettings(isLooping = true, eqEnabled = true)
    )
    val samplePlay = sampleIdle.copy(
      id = "p2",
      displayName = "Vocal Chorus Hook",
      colorArgb = 0xFFFFB300L,
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
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
          defaultCornerGlowColor = Color(0xFF00E5FF),
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(116.dp, 96.dp)
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
          defaultCornerGlowColor = Color(0xFFFFB300),
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(116.dp, 96.dp)
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
          defaultCornerGlowColor = Color(0xFF00E5FF),
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(116.dp, 96.dp)
        )
        PadCell(
          pad = sampleIdle,
          slotNumber = 4,
          playbackState = null,
          density = PadDensity.STANDARD,
          isEditMode = false,
          isDragging = false,
          isDropTarget = false,
          globalCrossfadeEnabled = false,
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = Color(0xFF00E5FF),
          isLoading = true,
          isEnabled = false,
          onClick = {},
          onHoldStart = {},
          onHoldRelease = {},
          modifier = Modifier.size(116.dp, 96.dp)
        )
      }
    }
  }
}
