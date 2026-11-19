package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.MasterRecordFormat
import com.example.domain.model.MasterRecordSampleRate
import com.example.domain.model.MasterRecordState
import com.example.ui.theme.SoundPadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterRecordSheet(
  recordState: MasterRecordState,
  onSelectFormat: (MasterRecordFormat) -> Unit = {},
  onSelectSampleRate: (MasterRecordSampleRate) -> Unit,
  onPickOutputFolder: () -> Unit,
  onToggleAutoAddPad: (Boolean) -> Unit,
  onStartRecording: () -> Unit,
  onStopAndSave: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val extColors = SoundPadTheme.colors

  val secTotal = (recordState.elapsedMs / 1000L).coerceAtLeast(0L)
  val formattedTime = "%02d:%02d".format(secTotal / 60L, secTotal % 60L)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(extColors.panicRed.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.FiberManualRecord,
              contentDescription = null,
              tint = extColors.panicRed
            )
          }
          Column {
            Text(
              text = stringResource(R.string.master_rec_title),
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.master_rec_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier.testTag("close_master_rec_sheet")
        ) {
          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
        }
      }

      // Live Timer & Level Meter Card
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
          width = 1.dp,
          color = if (recordState.isRecording) extColors.panicRed else MaterialTheme.colorScheme.outlineVariant
        )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
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
              Box(
                modifier = Modifier
                  .size(10.dp)
                  .clip(CircleShape)
                  .background(if (recordState.isRecording) extColors.panicRed else MaterialTheme.colorScheme.outline)
              )
              Text(
                text = if (recordState.isRecording) "SEDANG MEREKAM MIX" else "SIAP MEREKAM OUTPUT",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (recordState.isRecording) extColors.panicRed else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Text(
              text = formattedTime,
              style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onSurface
            )
          }

          LinearProgressIndicator(
            progress = { recordState.level.coerceIn(0f, 1f) },
            modifier = Modifier
              .fillMaxWidth()
              .height(10.dp)
              .clip(RoundedCornerShape(5.dp)),
            color = if (recordState.level > 0.85f) extColors.panicRed else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
          )

          Text(
            text = "Format: ${recordState.format.detailLabel} · ${if (recordState.sampleRateHz == 48000) "48.0 kHz" else "44.1 kHz"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Output Format Chips (WAV vs M4A AAC)
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.master_rec_format_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MasterRecordFormat.entries.forEach { fmt ->
            val selected = recordState.format == fmt
            FilterChip(
              selected = selected,
              enabled = !recordState.isRecording,
              onClick = { onSelectFormat(fmt) },
              label = { Text(fmt.shortLabel) },
              modifier = Modifier.testTag("master_rec_format_${fmt.extension}")
            )
          }
        }
      }

      // Sample Rate Chips
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = stringResource(R.string.master_rec_samplerate_label),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          MasterRecordSampleRate.entries.forEach { rate ->
            val selected = recordState.sampleRateHz == rate.hz
            FilterChip(
              selected = selected,
              enabled = !recordState.isRecording,
              onClick = { onSelectSampleRate(rate) },
              label = { Text(rate.label) },
              modifier = Modifier.testTag("master_rec_rate_${rate.hz}")
            )
          }
        }
      }

      // Destination SAF folder
      OutlinedButton(
        onClick = onPickOutputFolder,
        enabled = !recordState.isRecording,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("master_rec_pick_folder_button")
      ) {
        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = recordState.outputFolderName?.let { "Folder: $it" } ?: stringResource(R.string.master_rec_pick_folder),
          maxLines = 1
        )
      }

      // Auto add as Pad toggle
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = stringResource(R.string.master_rec_auto_add_pad),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
          )
          Switch(
            checked = recordState.autoAddAsPad,
            onCheckedChange = onToggleAutoAddPad,
            modifier = Modifier.testTag("master_rec_auto_add_switch")
          )
        }
      }

      recordState.lastSavedName?.let { lastName ->
        Text(
          text = "Rekaman terakhir: $lastName.${recordState.lastSavedExtension}",
          style = MaterialTheme.typography.labelSmall,
          color = extColors.ramEmerald
        )
      }

      if (recordState.isRecording) {
        Button(
          onClick = onStopAndSave,
          colors = ButtonDefaults.buttonColors(
            containerColor = extColors.panicRed,
            contentColor = MaterialTheme.colorScheme.onError
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("master_rec_stop_button")
        ) {
          Icon(Icons.Filled.Stop, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.master_rec_stop_format, recordState.format.shortLabel))
        }
      } else {
        Button(
          onClick = onStartRecording,
          colors = ButtonDefaults.buttonColors(
            containerColor = extColors.panicRed,
            contentColor = MaterialTheme.colorScheme.onError
          ),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("master_rec_start_button")
        ) {
          Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text(stringResource(R.string.master_rec_start))
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}
