package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AudioOutputDeviceUi
import com.example.domain.model.EqPreset
import com.example.domain.model.GlobalSettings
import com.example.ui.theme.SoundPadTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioOutputSheet(
  settings: GlobalSettings,
  outputDevices: List<AudioOutputDeviceUi>,
  selectedMainDeviceId: Int?,
  selectedCueDeviceId: Int?,
  isNormalizingAll: Boolean,
  onRefreshDevices: () -> Unit,
  onSelectMainDevice: (Int?) -> Unit,
  onSelectCueDevice: (Int?) -> Unit,
  onAudioEffectsToggle: (Boolean) -> Unit = {},
  onMasterLimiterToggle: (Boolean) -> Unit,
  onDuckingToggle: (Boolean) -> Unit,
  onDuckingAttenuationChange: (Float) -> Unit,
  onAutoNormalizeToggle: (Boolean) -> Unit,
  onAnalyzeLoudnessAll: () -> Unit,
  onMasterEqEnabledChange: (Boolean) -> Unit = {},
  onMasterEqPresetSelect: (EqPreset) -> Unit = {},
  onMasterEqBandChange: (Int, Float) -> Unit = { _, _ -> },
  onResetMasterEq: () -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Mixer, Limiter & Audio Output",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Anti-clipping limiter, auto-ducking, loudness & headphone cue",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedIconButton(
              onClick = onRefreshDevices,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("refresh_audio_devices_button")
            ) {
              Icon(Icons.Filled.Refresh, contentDescription = "Refresh audio output devices")
            }
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(Icons.Filled.Close, contentDescription = "Close audio output sheet")
            }
          }
        }
      }

      // Master Limiter + Auto-Ducking + Loudness Normalization
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                  Text(stringResource(R.string.settings_audio_effects_title), style = MaterialTheme.typography.titleSmall)
                  Text(
                    stringResource(R.string.settings_audio_effects_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Switch(
                checked = settings.audioEffectsEnabled,
                onCheckedChange = onAudioEffectsToggle,
                modifier = Modifier.testTag("mixer_audio_effects_switch")
              )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                  Text("Master Limiter", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Prevents audio clipping when stacking multiple pads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Switch(
                checked = settings.masterLimiterEnabled,
                onCheckedChange = onMasterLimiterToggle,
                modifier = Modifier.testTag("master_limiter_switch")
              )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Column {
                  Text("Auto-Ducking", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Lowers background pads during Voice Recording or Duck Source pads",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Switch(
                checked = settings.duckingEnabled,
                onCheckedChange = onDuckingToggle,
                modifier = Modifier.testTag("auto_ducking_switch")
              )
            }

            if (settings.duckingEnabled) {
              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(
                    "DUCKING BACKGROUND LEVEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    "${(settings.duckingAttenuation * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                  )
                }
                Slider(
                  value = settings.duckingAttenuation,
                  onValueChange = onDuckingAttenuationChange,
                  valueRange = 0.05f..0.90f,
                  modifier = Modifier.testTag("ducking_attenuation_slider")
                )
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column {
                  Text("Loudness Normalization", style = MaterialTheme.typography.titleSmall)
                  Text(
                    "Balances quiet & loud files automatically per pad",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                FilledTonalIconButton(
                  enabled = !isNormalizingAll,
                  onClick = onAnalyzeLoudnessAll,
                  modifier = Modifier
                    .size(40.dp)
                    .testTag("analyze_loudness_all_button")
                ) {
                  if (isNormalizingAll) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                  } else {
                    Icon(
                      Icons.Filled.AutoFixHigh,
                      contentDescription = "Analyze loudness for all pads",
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
                Switch(
                  checked = settings.autoNormalizeEnabled,
                  onCheckedChange = onAutoNormalizeToggle,
                  modifier = Modifier.testTag("auto_normalize_switch")
                )
              }
            }
          }
        }
      }

      // Master 5-Band Equalizer
      item {
        EqFiveBandPanel(
          title = "Master 5-Band Equalizer",
          subtitle = "Applies globally across all pads (-12dB to +12dB)",
          enabled = settings.masterEqEnabled,
          preset = settings.masterEqPreset,
          bandsDb = settings.masterEqBandsDb,
          testTagPrefix = "master",
          onEnabledChange = onMasterEqEnabledChange,
          onPresetSelected = onMasterEqPresetSelect,
          onBandChange = onMasterEqBandChange,
          onReset = onResetMasterEq
        )
      }

      // Main PA Output Device
      item {
        Text(
          text = "MAIN OUTPUT DEVICE",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }

      item {
        OutputDeviceOptionRow(
          label = "System Default Output",
          subtitle = "Automatic Android routing",
          isHeadphone = false,
          typeLabel = "Default",
          isSelected = selectedMainDeviceId == null,
          onClick = { onSelectMainDevice(null) }
        )
      }

      items(outputDevices, key = { "main_${it.id}" }) { device ->
        OutputDeviceOptionRow(
          label = device.name,
          subtitle = device.typeLabel,
          isHeadphone = device.isHeadphoneLike,
          typeLabel = device.typeLabel,
          isSelected = selectedMainDeviceId == device.id,
          onClick = { onSelectMainDevice(device.id) }
        )
      }

      // Cue / Headphone Preview Device
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "CUE / HEADPHONE MONITOR DEVICE",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary
        )
      }

      item {
        OutputDeviceOptionRow(
          label = "Follow Main / Auto Headphones",
          subtitle = "Cue previews play to default monitor output",
          isHeadphone = true,
          typeLabel = "Headphone",
          isSelected = selectedCueDeviceId == null,
          onClick = { onSelectCueDevice(null) }
        )
      }

      items(outputDevices, key = { "cue_${it.id}" }) { device ->
        OutputDeviceOptionRow(
          label = device.name,
          subtitle = "Private cue monitor on ${device.typeLabel}",
          isHeadphone = device.isHeadphoneLike,
          typeLabel = device.typeLabel,
          isSelected = selectedCueDeviceId == device.id,
          onClick = { onSelectCueDevice(device.id) }
        )
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}

@Composable
private fun OutputDeviceOptionRow(
  label: String,
  subtitle: String,
  isHeadphone: Boolean,
  typeLabel: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  val icon = when {
    typeLabel.contains("Bluetooth", ignoreCase = true) || typeLabel.contains("BLE", ignoreCase = true) ->
      Icons.Filled.BluetoothAudio
    typeLabel.contains("USB", ignoreCase = true) -> Icons.Filled.Usb
    isHeadphone -> Icons.Filled.Headphones
    else -> Icons.Filled.Speaker
  }
  val latencyTag = when {
    typeLabel.contains("USB", ignoreCase = true) -> "USB ~8ms"
    typeLabel.contains("Bluetooth", ignoreCase = true) -> "BT ~120ms"
    isHeadphone -> "Wired Low-Lat"
    typeLabel.equals("Default", ignoreCase = true) -> "Auto Route"
    else -> "Internal"
  }
  val statusDotColor = if (isSelected) {
    SoundPadTheme.colors.ramEmerald
  } else {
    MaterialTheme.colorScheme.outline
  }
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(10.dp),
    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(
      width = if (isSelected) 1.5.dp else 1.dp,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(statusDotColor)
      )
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = label,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Text(
          text = latencyTag,
          style = MaterialTheme.typography.labelSmall,
          color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
      }
      if (isSelected) {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = "Selected output device",
          tint = SoundPadTheme.colors.ramEmerald,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}
