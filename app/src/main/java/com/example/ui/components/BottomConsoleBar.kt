package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SoundPadAudioEngine
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadProTheme
import com.example.ui.theme.SoundPadTheme
import kotlin.math.roundToInt

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
  onHideBottomBar: () -> Unit,
  modifier: Modifier = Modifier
) {
  val extColors = SoundPadTheme.colors

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 4.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
      // Master Volume Fader Strip + Active Voice Readout + Hide Button
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
          Text(
            text = "${(masterVolume * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
          )
        }

        Slider(
          value = masterVolume,
          onValueChange = onMasterVolumeChange,
          valueRange = 0f..1f,
          modifier = Modifier
            .weight(1f)
            .height(28.dp)
            .testTag("master_volume_slider"),
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outline
          )
        )

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (activeVoices > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = 1.dp,
            color = if (activeVoices > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Text(
            text = "$activeVoices/${SoundPadAudioEngine.MAX_PLAYER_POOL}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (activeVoices > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
          )
        }

        val masterRecCd = stringResource(R.string.cd_master_record)
        val recSec = (masterRecordElapsedMs / 1000L).coerceAtLeast(0L)
        val recLabel = if (isMasterRecording) {
          "%02d:%02d".format(recSec / 60L, recSec % 60L)
        } else {
          "M.REC"
        }
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (isMasterRecording) {
            extColors.panicRed.copy(alpha = 0.22f)
          } else {
            MaterialTheme.colorScheme.surfaceContainer
          },
          border = BorderStroke(
            width = if (isMasterRecording) 1.5.dp else 1.dp,
            color = if (isMasterRecording) extColors.panicRed else MaterialTheme.colorScheme.outlineVariant
          ),
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .semantics { contentDescription = masterRecCd }
            .testTag("master_record_button")
            .clickable(enabled = !isLocked || isMasterRecording, onClick = onOpenMasterRecorder)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.FiberManualRecord,
              contentDescription = null,
              tint = extColors.panicRed,
              modifier = Modifier.size(10.dp)
            )
            Text(
              text = recLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
              ),
              color = if (isMasterRecording) extColors.panicRed else MaterialTheme.colorScheme.onSurface
            )
          }
        }

        IconButton(
          onClick = onHideBottomBar,
          modifier = Modifier
            .size(36.dp)
            .testTag("hide_bottom_bar_button")
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_hide_bottom_bar),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      // Live Performance Action Transport Row (Always-visible red PANIC button + Cue + REC + Edit)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        // Prominent Red PANIC / STOP ALL
        FilledTonalButton(
          onClick = onStopAll,
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("stop_all_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (activeVoices > 0) {
              extColors.panicRed
            } else {
              MaterialTheme.colorScheme.errorContainer
            },
            contentColor = if (activeVoices > 0) {
              MaterialTheme.colorScheme.onError
            } else {
              MaterialTheme.colorScheme.onErrorContainer
            }
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.cd_stop_all),
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "STOP",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            maxLines = 1
          )
        }

        // Fade Out All
        OutlinedIconButton(
          enabled = activeVoices > 0,
          onClick = onFadeOutAll,
          modifier = Modifier
            .size(48.dp)
            .testTag("fade_out_all_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(
            width = 1.dp,
            color = if (activeVoices > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
          ),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.secondary
          )
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
            contentDescription = stringResource(R.string.cd_fade_out_all),
            modifier = Modifier.size(20.dp)
          )
        }

        // Cue On/Off Quick Toggle
        OutlinedIconButton(
          onClick = onToggleGlobalCue,
          modifier = Modifier
            .size(48.dp)
            .testTag("global_cue_toggle_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(
            width = if (isGlobalCueEnabled) 1.5.dp else 1.dp,
            color = if (isGlobalCueEnabled) extColors.cueMonitor else MaterialTheme.colorScheme.outline
          ),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (isGlobalCueEnabled) {
              extColors.cueMonitor.copy(alpha = 0.18f)
            } else {
              MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (isGlobalCueEnabled) {
              extColors.cueMonitor
            } else {
              MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Headphones,
            contentDescription = stringResource(R.string.cd_global_cue_toggle),
            modifier = Modifier.size(20.dp)
          )
        }

        // REC (Voice / Recording trigger)
        OutlinedIconButton(
          enabled = !isLocked,
          onClick = onOpenVoiceRecorder,
          modifier = Modifier
            .size(48.dp)
            .testTag("voice_record_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = extColors.panicRed
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.cd_voice_record),
            modifier = Modifier.size(20.dp)
          )
        }

        // Style Customizer
        OutlinedIconButton(
          enabled = hasPads && !isLocked,
          onClick = onOpenTrackCustomizer,
          modifier = Modifier
            .size(48.dp)
            .testTag("customize_tracks_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = Icons.Filled.Palette,
            contentDescription = stringResource(R.string.cd_customize_tracks),
            modifier = Modifier.size(20.dp)
          )
        }

        // Edit Mode Toggle
        OutlinedIconButton(
          enabled = !isLocked,
          onClick = onToggleEditMode,
          modifier = Modifier
            .size(48.dp)
            .testTag("edit_mode_button"),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(
            width = if (isEditMode) 1.5.dp else 1.dp,
            color = if (isEditMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
          ),
          colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (isEditMode) {
              MaterialTheme.colorScheme.secondaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainer
            },
            contentColor = if (isEditMode) {
              MaterialTheme.colorScheme.secondary
            } else {
              MaterialTheme.colorScheme.onSurface
            }
          )
        ) {
          Icon(
            imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
            contentDescription = stringResource(R.string.cd_toggle_edit_mode),
            modifier = Modifier.size(20.dp)
          )
        }
      }
    }
  }
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
