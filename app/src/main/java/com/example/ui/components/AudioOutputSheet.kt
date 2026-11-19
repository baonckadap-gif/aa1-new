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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import com.example.domain.model.LatencyTestReport
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
  hasPendingBufferRebuild: Boolean = false,
  isRunningLatencyTest: Boolean = false,
  latencyTestReport: LatencyTestReport? = null,
  onRefreshDevices: () -> Unit,
  onSelectMainDevice: (Int?) -> Unit,
  onSelectCueDevice: (Int?) -> Unit,
  onCueVolumeChange: (Float) -> Unit = {},
  onAudioBufferSizeChange: (Int) -> Unit = {},
  onRunLatencyTest: () -> Unit = {},
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

      // Audio Buffer Size & Latency Test
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = stringResource(R.string.audio_buffer_section_title),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              Text(
                text = "${settings.audioBufferSizeMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = stringResource(R.string.audio_buffer_slider_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "20–500 ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Slider(
              value = settings.audioBufferSizeMs.toFloat().coerceIn(20f, 500f),
              onValueChange = { value ->
                val snapped = ((value / 10f).roundToInt() * 10).coerceIn(20, 500)
                onAudioBufferSizeChange(snapped)
              },
              valueRange = 20f..500f,
              modifier = Modifier.testTag("audio_buffer_size_slider")
            )

            Text(
              text = stringResource(R.string.audio_buffer_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasPendingBufferRebuild) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
              ) {
                Text(
                  text = stringResource(R.string.audio_buffer_pending_rebuild_warning),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSecondaryContainer,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              FilledTonalButton(
                enabled = !isRunningLatencyTest,
                onClick = onRunLatencyTest,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("latency_test_button")
              ) {
                if (isRunningLatencyTest) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                  )
                  Spacer(modifier = Modifier.size(8.dp))
                  Text(stringResource(R.string.latency_test_running))
                } else {
                  Icon(
                    imageVector = Icons.Filled.Speed,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.size(6.dp))
                  Text(stringResource(R.string.latency_test_button))
                }
              }

              if (latencyTestReport != null) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ) {
                  Text(
                    text = stringResource(
                      R.string.latency_test_result,
                      latencyTestReport.measuredReadyMs,
                      latencyTestReport.configuredBufferMs
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                      .padding(horizontal = 10.dp, vertical = 6.dp)
                      .testTag("latency_test_result_label")
                  )
                }
              }
            }
          }
        }
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

      // Cue / Headphone Preview Device & Separate Cue Volume
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "CUE / HEADPHONE MONITOR DEVICE",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary
        )
      }

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
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = stringResource(R.string.cue_volume_title),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
              )
              Text(
                text = "${(settings.cueVolume * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
              )
            }
            Slider(
              value = settings.cueVolume.coerceIn(0f, 1f),
              onValueChange = onCueVolumeChange,
              valueRange = 0f..1f,
              modifier = Modifier.testTag("cue_volume_slider")
            )
            Text(
              text = stringResource(R.string.cue_volume_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
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
