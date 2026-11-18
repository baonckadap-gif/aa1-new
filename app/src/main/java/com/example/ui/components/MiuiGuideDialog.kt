package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.util.MiuiOptimizationHelper

@Composable
fun MiuiGuideDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val isXiaomi = MiuiOptimizationHelper.isXiaomiDevice()
  val osLabel = MiuiOptimizationHelper.detectOsLabel()
  val isBatteryUnrestricted = MiuiOptimizationHelper.isIgnoringBatteryOptimizations(context)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = if (isXiaomi) "HyperOS / MIUI Whitelist" else "Background Audio Reliability",
          style = MaterialTheme.typography.headlineSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = osLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isBatteryUnrestricted) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
          border = BorderStroke(
            width = 1.dp,
            color = if (isBatteryUnrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = if (isBatteryUnrestricted) Icons.Filled.CheckCircle else Icons.Filled.WarningAmber,
              contentDescription = null,
              tint = if (isBatteryUnrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = if (isBatteryUnrestricted) {
                "Battery Saver: Unrestricted (Recommended)"
              } else {
                "Battery Saver: Restricted — tap Battery icon below & pick \"No restrictions\""
              },
              style = MaterialTheme.typography.labelMedium,
              color = if (isBatteryUnrestricted) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onErrorContainer
              }
            )
          }
        }

        Text(
          text = "1. Autostart (Rocket icon): Aktifkan Background Autostart untuk SoundPad Pro.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "2. Battery Saver (Battery icon): Ubah ke \"No restrictions\" / Tidak ada pembatasan.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "3. Other Permissions (Shield icon): Izinkan tampil di Lockscreen & Background Pop-ups.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "4. Recents Lock: Buka Recent Apps, tekan tahan SoundPad Pro lalu ketuk ikon gembok.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    confirmButton = {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.End)
      ) {
        OutlinedIconButton(
          onClick = { MiuiOptimizationHelper.openAutostartSettings(context) },
          modifier = Modifier
            .size(48.dp)
            .testTag("miui_autostart_button")
        ) {
          Icon(
            imageVector = Icons.Filled.RocketLaunch,
            contentDescription = "Open Autostart settings"
          )
        }
        OutlinedIconButton(
          onClick = { MiuiOptimizationHelper.openOtherPermissionsSettings(context) },
          modifier = Modifier
            .size(48.dp)
            .testTag("miui_permissions_button")
        ) {
          Icon(
            imageVector = Icons.Filled.AdminPanelSettings,
            contentDescription = "Open HyperOS/MIUI other permissions"
          )
        }
        FilledIconButton(
          onClick = { MiuiOptimizationHelper.openBatterySaverSettings(context) },
          modifier = Modifier
            .size(48.dp)
            .testTag("miui_battery_button")
        ) {
          Icon(
            imageVector = Icons.Filled.BatteryChargingFull,
            contentDescription = "Open Battery Saver settings"
          )
        }
      }
    },
    dismissButton = {
      IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Close dialog"
        )
      }
    }
  )
}
