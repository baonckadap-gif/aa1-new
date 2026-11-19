package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.backup.BackupInspectionSummary
import com.example.data.backup.BackupSyncStatus
import com.example.data.backup.PersistentBackupUiState
import com.example.data.backup.RestoreSelectionOptions
import com.example.data.backup.SnapshotFileInfo
import com.example.data.backup.SnapshotTrigger
import com.example.ui.theme.SoundPadTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupSettingsTabContent(
  backupState: PersistentBackupUiState,
  currentMusicFolderName: String?,
  onPickBackupFolderClick: () -> Unit,
  onSetAutoSyncEnabled: (Boolean) -> Unit,
  onSetIncludeWaveforms: (Boolean) -> Unit,
  onSyncNowClick: () -> Unit,
  onInspectRestoreFolderClick: () -> Unit,
  onOpenSnapshotPickerClick: () -> Unit,
  onExportZipClick: () -> Unit,
  onImportZipClick: () -> Unit,
  onShareZipClick: () -> Unit,
  onRequestDeleteAllConfig: () -> Unit
) {
  val spacing = SoundPadTheme.spacing
  val hasFolder = !backupState.folderUri.isNullOrBlank()
  val isError = backupState.status == BackupSyncStatus.ERROR_INACCESSIBLE
  val neverSyncedLabel = stringResource(R.string.backup_never_synced)
  val lastSavedLabel = remember(backupState.lastSyncAtMs, neverSyncedLabel) {
    if (backupState.lastSyncAtMs <= 0L) neverSyncedLabel
    else SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(backupState.lastSyncAtMs))
  }

  Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
    Text(
      text = stringResource(R.string.backup_tab_section_title),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary
    )

    // Kartu Status SAF + Mirror Folder Musik
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("backup_tab_saf_card"),
      shape = MaterialTheme.shapes.medium,
      color = if (isError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
      else MaterialTheme.colorScheme.surfaceContainerHigh,
      border = BorderStroke(
        width = 1.dp,
        color = if (isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.outlineVariant
      )
    ) {
      Column(
        modifier = Modifier.padding(spacing.md + spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(spacing.sm)
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)
        ) {
          Icon(
            imageVector = when (backupState.status) {
              BackupSyncStatus.ERROR_INACCESSIBLE -> Icons.Filled.CloudOff
              BackupSyncStatus.SAVING, BackupSyncStatus.PENDING -> Icons.Filled.CloudSync
              BackupSyncStatus.SAVED, BackupSyncStatus.IDLE -> Icons.Filled.CloudDone
              BackupSyncStatus.UNCONFIGURED -> Icons.Filled.FolderOpen
            },
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = backupState.folderName?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.backup_folder_unselected_placeholder),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = if (isError) {
                backupState.errorMessage ?: stringResource(R.string.backup_status_inaccessible)
              } else {
                stringResource(R.string.backup_last_sync_summary, lastSavedLabel, backupState.snapshotCount)
              },
              style = MaterialTheme.typography.bodySmall,
              color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (!currentMusicFolderName.isNullOrBlank()) {
          Text(
            text = stringResource(R.string.backup_mirror_active, currentMusicFolderName),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
          OutlinedButton(
            onClick = onPickBackupFolderClick,
            modifier = Modifier
              .weight(1f)
              .height(spacing.minTouchTarget)
              .testTag("backup_tab_pick_folder_btn")
          ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
            Text(
              text = if (hasFolder) stringResource(R.string.backup_tab_folder_change)
              else stringResource(R.string.backup_tab_folder_pick),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Button(
            onClick = onSyncNowClick,
            enabled = hasFolder,
            modifier = Modifier
              .weight(1f)
              .height(spacing.minTouchTarget)
              .testTag("backup_tab_save_now_btn")
          ) {
            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
            Text(stringResource(R.string.backup_tab_save_now), maxLines = 1)
          }
        }
      }
    }

    // Switch Sinkron Otomatis
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = spacing.md)) {
        Text(
          text = stringResource(R.string.backup_tab_auto_sync_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(R.string.backup_tab_auto_sync_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Switch(
        checked = backupState.autoSyncEnabled,
        onCheckedChange = onSetAutoSyncEnabled,
        modifier = Modifier.testTag("backup_tab_auto_sync_switch")
      )
    }

    // Switch Sertakan Cache Waveform
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f).padding(end = spacing.md)) {
        Text(
          text = stringResource(R.string.backup_tab_include_waveform_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = stringResource(R.string.backup_tab_include_waveform_subtitle),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Switch(
        checked = backupState.includeWaveforms,
        onCheckedChange = onSetIncludeWaveforms,
        modifier = Modifier.testTag("backup_tab_waveforms_switch")
      )
    }

    // Pulihkan dari Snapshot & Folder
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
      OutlinedButton(
        onClick = onOpenSnapshotPickerClick,
        modifier = Modifier
          .weight(1f)
          .height(spacing.minTouchTarget)
          .testTag("backup_tab_restore_snapshot_btn")
      ) {
        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
        Text(
          text = stringResource(R.string.backup_tab_restore_snapshot),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      OutlinedButton(
        onClick = onInspectRestoreFolderClick,
        modifier = Modifier
          .weight(1f)
          .height(spacing.minTouchTarget)
          .testTag("backup_tab_restore_folder_btn")
      ) {
        Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
        Text(
          text = stringResource(R.string.backup_tab_restore_from_folder),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    // Export / Import .zip + Bagikan Share Sheet
    Text(
      text = stringResource(R.string.backup_tab_zip_section_title),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
      OutlinedButton(
        onClick = onExportZipClick,
        modifier = Modifier
          .weight(1f)
          .height(spacing.minTouchTarget)
          .testTag("backup_tab_export_zip_btn")
      ) {
        Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
        Text(stringResource(R.string.backup_tab_export_zip), maxLines = 1)
      }
      OutlinedButton(
        onClick = onImportZipClick,
        modifier = Modifier
          .weight(1f)
          .height(spacing.minTouchTarget)
          .testTag("backup_tab_import_zip_btn")
      ) {
        Icon(Icons.Filled.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(spacing.xs + spacing.xxs))
        Text(stringResource(R.string.backup_tab_import_zip), maxLines = 1)
      }
    }

    Button(
      onClick = onShareZipClick,
      modifier = Modifier
        .fillMaxWidth()
        .height(spacing.minTouchTarget)
        .testTag("backup_tab_share_zip_btn")
    ) {
      Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(spacing.sm))
      Text(stringResource(R.string.backup_tab_share_zip))
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

    Text(
      text = stringResource(R.string.backup_tab_danger_zone),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.error
    )

    OutlinedButton(
      onClick = onRequestDeleteAllConfig,
      colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.65f)),
      modifier = Modifier
        .fillMaxWidth()
        .height(spacing.minTouchTarget)
        .testTag("backup_tab_delete_all_btn")
    ) {
      Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(spacing.sm))
      Text(stringResource(R.string.backup_tab_delete_all))
    }
  }
}

@Composable
fun RestorePreviewAndSelectDialog(
  inspection: BackupInspectionSummary,
  currentMusicFolderName: String?,
  onPickMusicFolderForRelink: () -> Unit,
  onRestoreAll: (RestoreSelectionOptions) -> Unit,
  onSkipDismiss: () -> Unit
) {
  val spacing = SoundPadTheme.spacing
  var isPartialMode by remember(inspection) { mutableStateOf(false) }
  var restoreSettings by remember(inspection) { mutableStateOf(true) }
  var restoreProfiles by remember(inspection) { mutableStateOf(true) }
  var restoreMidi by remember(inspection) { mutableStateOf(true) }
  var restoreSchedulerSeq by remember(inspection) { mutableStateOf(true) }
  var selectedProfiles by remember(inspection) {
    mutableStateOf(inspection.profilesPreview.map { it.id }.toSet())
  }

  val formattedDate = remember(inspection.backupDateMs) {
    SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(inspection.backupDateMs))
  }

  AlertDialog(
    onDismissRequest = onSkipDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    icon = {
      Icon(
        imageVector = Icons.Filled.Restore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = {
      Text(
        text = stringResource(R.string.restore_preview_title),
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 420.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)
      ) {
        if (!inspection.usedFallbackSnapshotName.isNullOrBlank()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
          ) {
            Row(
              modifier = Modifier.padding(spacing.sm + spacing.xxs),
              horizontalArrangement = Arrangement.spacedBy(spacing.sm),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = stringResource(
                  R.string.backup_fallback_snapshot_warning,
                  inspection.usedFallbackSnapshotName.orEmpty()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
              )
            }
          }
        }

        // Ringkasan Statistik Backup
        Surface(
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)
          ) {
            Text(
              text = "${inspection.sourceKind.label}: ${inspection.sourceLabel}",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = stringResource(R.string.backup_date_schema_summary, formattedDate, inspection.schemaVersion),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              SummaryMetricBadge(label = stringResource(R.string.backup_metric_profile), value = "${inspection.profileCount}")
              SummaryMetricBadge(label = stringResource(R.string.backup_metric_bank), value = "${inspection.bankCount}")
              SummaryMetricBadge(label = stringResource(R.string.backup_metric_pad), value = "${inspection.padCount}")
              SummaryMetricBadge(
                label = stringResource(R.string.backup_metric_sched_seq),
                value = "${inspection.schedulerCount + inspection.sequencerCount}"
              )
            }
          }
        }

        // Info folder musik untuk pencocokan ulang URI SAF setelah install ulang
        Surface(
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = spacing.sm + spacing.xxs, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
          ) {
            Icon(
              imageVector = Icons.Filled.LibraryMusic,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.backup_relink_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = when {
                  !inspection.detectedMusicFolderUri.isNullOrBlank() ->
                    stringResource(R.string.backup_relink_detected_folder)
                  !currentMusicFolderName.isNullOrBlank() ->
                    stringResource(R.string.backup_relink_current_folder, currentMusicFolderName)
                  else -> stringResource(R.string.backup_relink_pick_hint)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (inspection.detectedMusicFolderUri.isNullOrBlank()) {
              TextButton(
                onClick = onPickMusicFolderForRelink,
                modifier = Modifier.testTag("restore_pick_music_folder_btn")
              ) {
                Text(stringResource(R.string.backup_relink_pick_btn))
              }
            }
          }
        }

        if (isPartialMode) {
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Text(
            text = stringResource(R.string.backup_restore_components_header),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )

          RestoreCheckRow(
            label = stringResource(R.string.backup_opt_settings),
            checked = restoreSettings,
            onCheckedChange = { restoreSettings = it },
            testTag = "restore_opt_settings"
          )
          RestoreCheckRow(
            label = stringResource(R.string.backup_opt_profiles),
            checked = restoreProfiles,
            onCheckedChange = { restoreProfiles = it },
            testTag = "restore_opt_profiles"
          )
          if (restoreProfiles && inspection.profilesPreview.size > 1) {
            Column(
              modifier = Modifier.padding(start = spacing.lg + spacing.xxs),
              verticalArrangement = Arrangement.spacedBy(spacing.xxs)
            ) {
              inspection.profilesPreview.forEach { prof ->
                val picked = prof.id in selectedProfiles
                RestoreCheckRow(
                  label = stringResource(R.string.backup_opt_profile_item, prof.name, prof.padCount, prof.bankCount),
                  checked = picked,
                  onCheckedChange = { isChecked ->
                    selectedProfiles = if (isChecked) selectedProfiles + prof.id
                    else selectedProfiles - prof.id
                  },
                  testTag = "restore_profile_check_${prof.id}"
                )
              }
            }
          }
          RestoreCheckRow(
            label = stringResource(R.string.backup_opt_midi),
            checked = restoreMidi,
            onCheckedChange = { restoreMidi = it },
            testTag = "restore_opt_midi"
          )
          RestoreCheckRow(
            label = stringResource(R.string.backup_opt_scheduler_seq),
            checked = restoreSchedulerSeq,
            onCheckedChange = { restoreSchedulerSeq = it },
            testTag = "restore_opt_scheduler_seq"
          )
        }
      }
    },
    confirmButton = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)
      ) {
        if (!isPartialMode) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
          ) {
            OutlinedButton(
              onClick = { isPartialMode = true },
              modifier = Modifier
                .weight(1f)
                .height(spacing.minTouchTarget)
                .testTag("restore_partial_toggle_button")
            ) {
              Text(stringResource(R.string.restore_action_partial), maxLines = 1)
            }
            Button(
              onClick = {
                onRestoreAll(
                  RestoreSelectionOptions(
                    restoreSettings = true,
                    restoreProfilesAndPads = true,
                    selectedProfileIds = emptySet(),
                    restoreMidiMapping = true,
                    restoreSchedulerAndSequencer = true
                  )
                )
              },
              modifier = Modifier
                .weight(1f)
                .height(spacing.minTouchTarget)
                .testTag("restore_all_confirm_button")
            ) {
              Text(stringResource(R.string.restore_action_all), maxLines = 1)
            }
          }
        } else {
          val canApplyPartial = restoreSettings ||
            (restoreProfiles && selectedProfiles.isNotEmpty()) ||
            restoreMidi ||
            restoreSchedulerSeq
          Button(
            enabled = canApplyPartial,
            onClick = {
              onRestoreAll(
                RestoreSelectionOptions(
                  restoreSettings = restoreSettings,
                  restoreProfilesAndPads = restoreProfiles,
                  selectedProfileIds = selectedProfiles,
                  restoreMidiMapping = restoreMidi,
                  restoreSchedulerAndSequencer = restoreSchedulerSeq
                )
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(spacing.minTouchTarget)
              .testTag("restore_apply_partial_button")
          ) {
            Text(stringResource(R.string.backup_restore_apply_selected))
          }
        }

        TextButton(
          onClick = onSkipDismiss,
          modifier = Modifier
            .align(Alignment.End)
            .testTag("restore_skip_button")
        ) {
          Text(stringResource(R.string.restore_action_skip))
        }
      }
    }
  )
}

@Composable
private fun SummaryMetricBadge(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun RestoreCheckRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  testTag: String
) {
  val spacing = SoundPadTheme.spacing
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(MaterialTheme.shapes.small)
      .clickable { onCheckedChange(!checked) }
      .padding(vertical = spacing.xxs)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f)
    )
  }
}

@Composable
fun RestoreProgressDialog(
  progress: Float,
  stageText: String
) {
  val spacing = SoundPadTheme.spacing
  val clamped = progress.coerceIn(0f, 1f)
  AlertDialog(
    onDismissRequest = {},
    containerColor = MaterialTheme.colorScheme.surface,
    title = {
      Text(
        text = stringResource(R.string.restore_progress_title),
        style = MaterialTheme.typography.titleMedium
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("restore_progress_dialog"),
        verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)
      ) {
        LinearProgressIndicator(
          progress = { clamped },
          modifier = Modifier
            .fillMaxWidth()
            .height(spacing.sm)
            .clip(MaterialTheme.shapes.extraSmall)
            .testTag("restore_progress_bar")
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stageText.ifBlank { stringResource(R.string.backup_restore_processing) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
          )
          Text(
            text = "${(clamped * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    },
    confirmButton = {}
  )
}

@Composable
fun SnapshotPickerDialog(
  snapshots: List<SnapshotFileInfo>,
  hasBackupFolder: Boolean,
  onSelectSnapshot: (SnapshotFileInfo) -> Unit,
  onPickFolderInstead: () -> Unit,
  onDismiss: () -> Unit
) {
  val spacing = SoundPadTheme.spacing
  val fallbackLabel = stringResource(R.string.backup_snapshot_fallback_label)
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    icon = {
      Icon(
        imageVector = Icons.Filled.History,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = {
      Text(
        text = stringResource(R.string.snapshot_picker_title),
        style = MaterialTheme.typography.titleLarge
      )
    },
    text = {
      if (!hasBackupFolder) {
        Text(
          text = stringResource(R.string.backup_snapshot_no_folder_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else if (snapshots.isEmpty()) {
        Text(
          text = stringResource(R.string.snapshot_picker_empty),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
          items(snapshots, key = { it.fileName }) { snap ->
            val triggerLabel = remember(snap.trigger, fallbackLabel) {
              SnapshotTrigger.entries.firstOrNull { it.slug == snap.trigger }?.label ?: fallbackLabel
            }
            val dateText = remember(snap.lastModifiedMs) {
              SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                .format(Date(snap.lastModifiedMs.takeIf { it > 0L } ?: System.currentTimeMillis()))
            }
            val sizeKb = ((snap.sizeBytes + 1023L) / 1024L).coerceAtLeast(1L)

            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable { onSelectSnapshot(snap) }
                .testTag("snapshot_item_${snap.fileName}"),
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = triggerLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                  )
                  Text(
                    text = "$sizeKb KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Text(
                  text = dateText,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = snap.fileName,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      if (!hasBackupFolder) {
        Button(
          onClick = {
            onDismiss()
            onPickFolderInstead()
          },
          modifier = Modifier.height(spacing.minTouchTarget)
        ) {
          Text(stringResource(R.string.backup_tab_restore_from_folder))
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_close))
      }
    }
  )
}

@Composable
fun ResetAllConfigDialog(
  hasBackupFolder: Boolean,
  onConfirmReset: (alsoClearSafFolder: Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  val spacing = SoundPadTheme.spacing
  var alsoClearSaf by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface,
    icon = {
      Icon(
        imageVector = Icons.Filled.DeleteForever,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error
      )
    },
    title = {
      Text(
        text = stringResource(R.string.reset_all_config_title),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error
      )
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)) {
        Text(
          text = stringResource(R.string.reset_all_config_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasBackupFolder) {
          RestoreCheckRow(
            label = stringResource(R.string.reset_all_config_clear_saf),
            checked = alsoClearSaf,
            onCheckedChange = { alsoClearSaf = it },
            testTag = "reset_config_also_clear_saf"
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirmReset(alsoClearSaf) },
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error,
          contentColor = MaterialTheme.colorScheme.onError
        ),
        modifier = Modifier
          .height(spacing.minTouchTarget)
          .testTag("reset_all_config_confirm_btn")
      ) {
        Text(stringResource(R.string.reset_all_config_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}
