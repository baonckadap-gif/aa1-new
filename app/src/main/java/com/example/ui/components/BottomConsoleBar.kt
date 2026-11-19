package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.example.R
import com.example.audio.SoundPadAudioEngine
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadProTheme
import com.example.ui.theme.SoundPadTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomConsoleBar(
  activeVoices: Int,
  masterVolume: Float,
  isEditMode: Boolean,
  isLocked: Boolean = false,
  isGlobalCueEnabled: Boolean = false,
  isMasterRecording: Boolean = false,
  masterRecordElapsedMs: Long = 0L,
  hasPads: Boolean,
  onStopAll: () -> Unit,
  onFadeOutAll: () -> Unit = {},
  onMasterVolumeChange: (Float) -> Unit,
  onToggleGlobalCue: () -> Unit = {},
  onOpenMasterRecorder: () -> Unit = {},
  onOpenTrackCustomizer: () -> Unit,
  onOpenVoiceRecorder: () -> Unit,
  onToggleEditMode: () -> Unit,
  onHideBottomBar: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  val extColors = SoundPadTheme.colors
  val bottomTokens = SoundPadTheme.bottomBarTokens
  var isCollapsed by rememberSaveable { mutableStateOf(false) }

  val chevronRotation by animateFloatAsState(
    targetValue = if (isCollapsed) 180f else 0f,
    label = "bottom_bar_chevron_rotation"
  )

  val sliderColors = SliderDefaults.colors(
    thumbColor = MaterialTheme.colorScheme.primary,
    activeTrackColor = MaterialTheme.colorScheme.primary,
    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
  )
  val sliderCd = stringResource(R.string.cd_master_volume_slider)

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = bottomTokens.tonalElevationDp,
    border = BorderStroke(bottomTokens.borderThicknessDp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .heightIn(max = bottomTokens.maxContentHeightDp)
        .padding(
          horizontal = bottomTokens.horizontalPaddingDp,
          vertical = bottomTokens.verticalPaddingDp
        ),
      verticalArrangement = Arrangement.spacedBy(bottomTokens.rowSpacingDp)
    ) {
      // Baris 1: Volume icon + Standard round-thumb Slider + % text + Voice badge "0/16" + Chevron collapse toggle
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(bottomTokens.row1HeightDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(bottomTokens.row1ItemSpacingDp)
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.VolumeUp,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(bottomTokens.volumeIconDp)
        )

        Slider(
          value = masterVolume,
          onValueChange = onMasterVolumeChange,
          valueRange = 0f..1f,
          colors = sliderColors,
          thumb = {
            Box(
              modifier = Modifier
                .size(bottomTokens.sliderThumbDp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(
                  width = bottomTokens.sliderThumbBorderDp,
                  color = MaterialTheme.colorScheme.surface,
                  shape = CircleShape
                )
            )
          },
          track = { sliderState ->
            SliderDefaults.Track(
              sliderState = sliderState,
              colors = sliderColors,
              enabled = true,
              thumbTrackGapSize = bottomTokens.sliderTrackGapDp,
              drawStopIndicator = null,
              modifier = Modifier.height(bottomTokens.sliderTrackHeightDp)
            )
          },
          modifier = Modifier
            .weight(1f)
            .height(bottomTokens.sliderHeightDp)
            .semantics { contentDescription = sliderCd }
            .testTag("master_volume_slider")
        )

        Text(
          text = "${(masterVolume * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.End,
          maxLines = 1,
          modifier = Modifier.widthIn(min = bottomTokens.percentMinWidthDp)
        )

        Surface(
          shape = RoundedCornerShape(bottomTokens.badgeCornerDp),
          color = if (activeVoices > 0) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.surfaceContainer
          },
          border = BorderStroke(
            width = bottomTokens.borderThicknessDp,
            color = if (activeVoices > 0) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.outlineVariant
            }
          )
        ) {
          Text(
            text = "$activeVoices/${SoundPadAudioEngine.MAX_PLAYER_POOL}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (activeVoices > 0) {
              MaterialTheme.colorScheme.primary
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier.padding(
              horizontal = bottomTokens.badgeHorizontalPadDp,
              vertical = bottomTokens.badgeVerticalPadDp
            )
          )
        }

        IconButton(
          onClick = { isCollapsed = !isCollapsed },
          modifier = Modifier
            .size(bottomTokens.chevronButtonDp)
            .testTag("hide_bottom_bar_button")
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(
              if (isCollapsed) R.string.cd_expand_bottom_bar else R.string.cd_collapse_bottom_bar
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
              .size(bottomTokens.chevronIconDp)
              .graphicsLayer { rotationZ = chevronRotation }
          )
        }
      }

      // Baris 2: STOP (30% merah) + Tombol aksi sama besar (Fade Out, Cue, Rec Mic, REC Master, Warna, Edit Mode)
      AnimatedVisibility(visible = !isCollapsed) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(bottomTokens.row2HeightDp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(bottomTokens.row2ItemSpacingDp)
        ) {
          // STOP (merah, lebar 30%)
          FilledTonalButton(
            onClick = onStopAll,
            contentPadding = PaddingValues(horizontal = bottomTokens.horizontalPaddingDp),
            modifier = Modifier
              .weight(bottomTokens.stopWidthFraction)
              .height(bottomTokens.row2HeightDp)
              .testTag("stop_all_button"),
            shape = RoundedCornerShape(bottomTokens.stopCornerDp),
            colors = ButtonDefaults.filledTonalButtonColors(
              containerColor = if (activeVoices > 0) {
                extColors.panicRed
              } else {
                extColors.panicRed.copy(alpha = bottomTokens.stopIdleAlpha)
              },
              contentColor = extColors.onPanicRed
            )
          ) {
            Icon(
              imageVector = Icons.Filled.Stop,
              contentDescription = stringResource(R.string.cd_stop_all),
              modifier = Modifier.size(bottomTokens.stopIconDp)
            )
            Spacer(modifier = Modifier.width(bottomTokens.actionGroupSpacingDp))
            Text(
              text = stringResource(R.string.bottom_action_stop),
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = bottomTokens.stopLabelFontSp,
                fontWeight = FontWeight.Bold
              ),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          // Tombol aksi sama besar dalam sisa 70%
          Row(
            modifier = Modifier
              .weight(bottomTokens.actionsWidthFraction)
              .height(bottomTokens.row2HeightDp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(bottomTokens.actionGroupSpacingDp)
          ) {
            // 1. Fade Out
            BottomConsoleActionButton(
              icon = Icons.AutoMirrored.Filled.TrendingDown,
              label = stringResource(R.string.bottom_action_fade_out),
              contentDescription = stringResource(R.string.cd_fade_out_all),
              enabled = activeVoices > 0,
              isActive = activeVoices > 0,
              accentColor = MaterialTheme.colorScheme.secondary,
              onClick = onFadeOutAll,
              modifier = Modifier.testTag("fade_out_all_button")
            )

            // 2. Cue
            BottomConsoleActionButton(
              icon = Icons.Filled.Headphones,
              label = stringResource(R.string.bottom_action_cue),
              contentDescription = stringResource(R.string.cd_global_cue_toggle),
              enabled = true,
              isActive = isGlobalCueEnabled,
              accentColor = extColors.cueMonitor,
              onClick = onToggleGlobalCue,
              modifier = Modifier.testTag("global_cue_toggle_button")
            )

            // 3. Rec Mic
            BottomConsoleActionButton(
              icon = Icons.Filled.Mic,
              label = stringResource(R.string.bottom_action_rec_mic),
              contentDescription = stringResource(R.string.cd_voice_record),
              enabled = !isLocked,
              isActive = false,
              accentColor = extColors.panicRed,
              onClick = onOpenVoiceRecorder,
              modifier = Modifier.testTag("voice_record_button")
            )

            // 4. REC Master (dipindah dari baris 1, merah berkedip saat merekam + timer)
            MasterRecordActionButton(
              isRecording = isMasterRecording,
              elapsedMs = masterRecordElapsedMs,
              enabled = !isLocked || isMasterRecording,
              onClick = onOpenMasterRecorder,
              modifier = Modifier.testTag("master_record_button")
            )

            // 5. Warna / Tema
            BottomConsoleActionButton(
              icon = Icons.Filled.Palette,
              label = stringResource(R.string.bottom_action_theme),
              contentDescription = stringResource(R.string.cd_customize_tracks),
              enabled = hasPads && !isLocked,
              isActive = false,
              accentColor = MaterialTheme.colorScheme.primary,
              onClick = onOpenTrackCustomizer,
              modifier = Modifier.testTag("customize_tracks_button")
            )

            // 6. Edit Mode
            BottomConsoleActionButton(
              icon = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
              label = stringResource(
                if (isEditMode) R.string.bottom_action_done else R.string.bottom_action_edit
              ),
              contentDescription = stringResource(R.string.cd_toggle_edit_mode),
              enabled = !isLocked,
              isActive = isEditMode,
              accentColor = MaterialTheme.colorScheme.secondary,
              onClick = onToggleEditMode,
              modifier = Modifier.testTag("edit_mode_button")
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RowScope.BottomConsoleActionButton(
  icon: ImageVector,
  label: String,
  contentDescription: String,
  enabled: Boolean,
  isActive: Boolean,
  accentColor: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  iconAlpha: Float = 1f,
  useMonoLabel: Boolean = false
) {
  val bottomTokens = SoundPadTheme.bottomBarTokens
  val containerColor = if (isActive) {
    accentColor.copy(alpha = bottomTokens.actionActiveBgAlpha)
  } else {
    MaterialTheme.colorScheme.surfaceContainer
  }
  val borderColor = when {
    !enabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = bottomTokens.actionDisabledAlpha)
    isActive -> accentColor
    else -> MaterialTheme.colorScheme.outlineVariant
  }
  val contentTint = when {
    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = bottomTokens.actionDisabledAlpha)
    isActive -> accentColor
    else -> MaterialTheme.colorScheme.onSurface
  }
  val iconTint = if (!enabled) {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = bottomTokens.actionDisabledAlpha)
  } else {
    accentColor
  }

  Surface(
    shape = RoundedCornerShape(bottomTokens.actionCornerDp),
    color = containerColor,
    border = BorderStroke(
      width = if (isActive) bottomTokens.activeBorderDp else bottomTokens.inactiveBorderDp,
      color = borderColor
    ),
    modifier = modifier
      .weight(1f)
      .height(bottomTokens.row2HeightDp)
      .clip(RoundedCornerShape(bottomTokens.actionCornerDp))
      .semantics { this.contentDescription = contentDescription }
      .clickable(enabled = enabled, onClick = onClick)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = bottomTokens.rowSpacingDp / 2f, vertical = bottomTokens.verticalPaddingDp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier
          .size(bottomTokens.actionIconDp)
          .graphicsLayer { alpha = iconAlpha }
      )
      Spacer(modifier = Modifier.height(bottomTokens.verticalPaddingDp / 2f))
      val baseStyle = if (useMonoLabel) {
        MaterialTheme.typography.labelSmall
      } else {
        MaterialTheme.typography.bodySmall
      }
      Text(
        text = label,
        style = baseStyle.copy(
          fontSize = bottomTokens.actionLabelFontSp,
          lineHeight = bottomTokens.actionLabelLineHeightSp,
          fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
        ),
        color = contentTint,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun RowScope.MasterRecordActionButton(
  isRecording: Boolean,
  elapsedMs: Long,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val extColors = SoundPadTheme.colors
  val bottomTokens = SoundPadTheme.bottomBarTokens

  val blinkAlpha = if (isRecording) {
    val transition = rememberInfiniteTransition(label = "master_rec_blink")
    val animated by transition.animateFloat(
      initialValue = bottomTokens.blinkMaxAlpha,
      targetValue = bottomTokens.blinkMinAlpha,
      animationSpec = infiniteRepeatable(
        animation = tween(durationMillis = bottomTokens.blinkDurationMs),
        repeatMode = RepeatMode.Reverse
      ),
      label = "master_rec_dot_alpha"
    )
    animated
  } else {
    bottomTokens.blinkMaxAlpha
  }

  val recSec = (elapsedMs / 1000L).coerceAtLeast(0L)
  val label = if (isRecording) {
    "%02d:%02d".format(recSec / 60L, recSec % 60L)
  } else {
    stringResource(R.string.bottom_action_rec_master)
  }

  BottomConsoleActionButton(
    icon = Icons.Filled.FiberManualRecord,
    label = label,
    contentDescription = stringResource(R.string.cd_master_record),
    enabled = enabled,
    isActive = isRecording,
    accentColor = extColors.panicRed,
    onClick = onClick,
    iconAlpha = blinkAlpha,
    useMonoLabel = isRecording,
    modifier = modifier
  )
}

@Preview(showBackground = true)
@Composable
private fun BottomConsoleBarPreview() {
  SoundPadProTheme(themeMode = ThemeMode.DARK) {
    BottomConsoleBar(
      activeVoices = 3,
      masterVolume = 0.85f,
      isEditMode = false,
      isLocked = false,
      isGlobalCueEnabled = true,
      isMasterRecording = false,
      hasPads = true,
      onStopAll = {},
      onMasterVolumeChange = {},
      onOpenTrackCustomizer = {},
      onOpenVoiceRecorder = {},
      onToggleEditMode = {},
      onHideBottomBar = {}
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun BottomConsoleBarRecordingPreview() {
  SoundPadProTheme(themeMode = ThemeMode.DARK) {
    BottomConsoleBar(
      activeVoices = 0,
      masterVolume = 0.65f,
      isEditMode = true,
      isLocked = false,
      isGlobalCueEnabled = false,
      isMasterRecording = true,
      masterRecordElapsedMs = 42_000L,
      hasPads = true,
      onStopAll = {},
      onMasterVolumeChange = {},
      onOpenTrackCustomizer = {},
      onOpenVoiceRecorder = {},
      onToggleEditMode = {},
      onHideBottomBar = {}
    )
  }
}
