package com.example.ui.components

import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.ThemeMode

@Composable
fun OnboardingDialog(
  currentGridConfig: GridConfig,
  currentThemeMode: ThemeMode,
  onPickFolder: () -> Unit,
  onLoadDemoTracks: () -> Unit,
  onSelectGridConfig: (GridConfig) -> Unit,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onRequestMicPermission: () -> Unit,
  onRequestBlePermissions: () -> Unit,
  onOpenMiuiGuide: () -> Unit,
  onRestoreOldConfig: () -> Unit = {},
  onFinishOnboarding: () -> Unit,
  onDismissSkip: () -> Unit
) {
  var step by remember { mutableIntStateOf(0) } // 0..2

  AlertDialog(
    onDismissRequest = onDismissSkip,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
            }
            Text(
              text = stringResource(R.string.app_name),
              style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }
          Text(
            text = stringResource(R.string.onboarding_step_indicator, step + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }

        // Step indicator dots
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          for (i in 0..2) {
            Box(
              modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .clip(CircleShape)
                .background(
                  if (i <= step) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outlineVariant
                )
            )
          }
        }
      }
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        when (step) {
          0 -> {
            Text(
              text = stringResource(R.string.onboarding_title_step1),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.onboarding_desc_step1),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
              onClick = {
                onPickFolder()
                onFinishOnboarding()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_wizard_pick_folder")
            ) {
              Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.onboarding_pick_folder))
            }

            OutlinedButton(
              onClick = {
                onLoadDemoTracks()
                step = 1
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_wizard_load_demo")
            ) {
              Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.onboarding_load_demo))
            }

            OutlinedButton(
              onClick = onRestoreOldConfig,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_wizard_restore_config")
            ) {
              Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.onboarding_restore_old_config))
            }
            Text(
              text = stringResource(R.string.onboarding_restore_old_config_desc),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          1 -> {
            Text(
              text = stringResource(R.string.onboarding_title_step2),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.onboarding_desc_step2),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf(GridPreset.GRID_4X4, GridPreset.GRID_10X10, GridPreset.GRID_15X15).forEach { preset ->
                val selected = currentGridConfig.preset == preset
                Surface(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .testTag("onboarding_grid_${preset.name.lowercase()}")
                    .clickable {
                      onSelectGridConfig(GridConfig(preset = preset, rows = preset.rows, cols = preset.cols))
                    },
                  shape = RoundedCornerShape(10.dp),
                  color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                  border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                  )
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.GridView,
                      contentDescription = null,
                      tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                    Text(
                      text = preset.label,
                      style = MaterialTheme.typography.labelLarge,
                      color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf(ThemeMode.DARK, ThemeMode.AMOLED, ThemeMode.LIGHT).forEach { mode ->
                FilterChip(
                  selected = currentThemeMode == mode,
                  onClick = { onSelectThemeMode(mode) },
                  label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("onboarding_theme_${mode.name.lowercase()}")
                )
              }
            }
          }

          else -> {
            Text(
              text = stringResource(R.string.onboarding_title_step3),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.onboarding_desc_step3),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
              onClick = onRequestMicPermission,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_perm_mic")
            ) {
              Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.onboarding_grant_mic_perm))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              OutlinedButton(
                onClick = onRequestBlePermissions,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("onboarding_perm_ble")
              ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_grant_midi_perm))
              }
            }

            OutlinedButton(
              onClick = onOpenMiuiGuide,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("onboarding_miui_guide_button")
            ) {
              Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.settings_open_miui_guide))
            }
          }
        }
      }
    },
    confirmButton = {
      if (step < 2) {
        Button(
          onClick = { step++ },
          modifier = Modifier
            .height(48.dp)
            .testTag("onboarding_next_button")
        ) {
          Text(stringResource(R.string.onboarding_next))
          Spacer(modifier = Modifier.width(4.dp))
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
      } else {
        Button(
          onClick = onFinishOnboarding,
          modifier = Modifier
            .height(48.dp)
            .testTag("onboarding_finish_button")
        ) {
          Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(stringResource(R.string.onboarding_finish))
        }
      }
    },
    dismissButton = {
      Row {
        if (step > 0) {
          TextButton(
            onClick = { step-- },
            modifier = Modifier.testTag("onboarding_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.onboarding_back))
          }
        }
        TextButton(
          onClick = onDismissSkip,
          modifier = Modifier.testTag("onboarding_skip_button")
        ) {
          Text(stringResource(R.string.onboarding_skip))
        }
      }
    }
  )
}
