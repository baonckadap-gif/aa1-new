package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.PadProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagerSheet(
  profiles: List<PadProfile>,
  activeProfileId: String,
  onSaveNewProfile: (String) -> Unit,
  onLoadProfile: (String) -> Unit,
  onDuplicateProfile: (String) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onExportProfileJson: (PadProfile) -> Unit,
  onImportProfileJson: () -> Unit,
  onBackupAllData: () -> Unit = {},
  onRestoreAllData: () -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var newProfileName by remember { mutableStateOf("") }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Profiles & JSON Presets",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "${profiles.size} saved profile(s)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedIconButton(
            onClick = onBackupAllData,
            modifier = Modifier
              .size(48.dp)
              .testTag("backup_all_button")
          ) {
            Icon(
              imageVector = Icons.Filled.CloudDownload,
              contentDescription = stringResource(R.string.cd_backup_all),
              tint = MaterialTheme.colorScheme.secondary
            )
          }

          OutlinedIconButton(
            onClick = onRestoreAllData,
            modifier = Modifier
              .size(48.dp)
              .testTag("restore_all_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Restore,
              contentDescription = stringResource(R.string.cd_restore_all),
              tint = MaterialTheme.colorScheme.tertiary
            )
          }

          // Import single profile JSON icon button
          OutlinedIconButton(
            onClick = onImportProfileJson,
            modifier = Modifier
              .size(48.dp)
              .testTag("import_profile_json_button")
          ) {
            Icon(
              imageVector = Icons.Filled.FileOpen,
              contentDescription = "Import profile from JSON",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }
      }

      // Save current layout as new profile
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        OutlinedTextField(
          value = newProfileName,
          onValueChange = { newProfileName = it },
          placeholder = { Text("New profile name...") },
          singleLine = true,
          modifier = Modifier
            .weight(1f)
            .testTag("new_profile_name_input")
        )
        FilledIconButton(
          onClick = {
            val trimmed = newProfileName.trim().ifEmpty { "Set ${profiles.size + 1}" }
            onSaveNewProfile(trimmed)
            newProfileName = ""
          },
          modifier = Modifier
            .size(48.dp)
            .testTag("save_new_profile_button")
        ) {
          Icon(
            imageVector = Icons.Filled.Save,
            contentDescription = "Save current setup as new profile"
          )
        }
      }

      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .height(296.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(profiles, key = { it.id }) { profile ->
          val isActive = profile.id == activeProfileId
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { onLoadProfile(profile.id) },
            shape = RoundedCornerShape(12.dp),
            color = if (isActive) {
              MaterialTheme.colorScheme.primaryContainer
            } else {
              MaterialTheme.colorScheme.surfaceContainer
            },
            border = BorderStroke(
              width = if (isActive) 1.5.dp else 1.dp,
              color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                ProfileMiniGridPreview(
                  colors = profile.previewColorArgbs,
                  padCount = profile.padCount
                )
                Column(modifier = Modifier.weight(1f)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    if (isActive) {
                      Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Active profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                    Text(
                      text = profile.name,
                      style = MaterialTheme.typography.titleSmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Text(
                    text = "${profile.gridConfig.displayLabel} · ${profile.padCount} pads",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                  onClick = { onDuplicateProfile(profile.id) },
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = "Duplicate profile",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                  )
                }
                IconButton(
                  onClick = { onExportProfileJson(profile) },
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Export profile to JSON",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                }
                if (profiles.size > 1) {
                  IconButton(
                    onClick = { onDeleteProfile(profile.id) },
                    modifier = Modifier.size(44.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Delete,
                      contentDescription = "Delete profile",
                      tint = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@Composable
private fun ProfileMiniGridPreview(
  colors: List<Long?>,
  padCount: Int
) {
  Column(
    modifier = Modifier
      .size(40.dp)
      .clip(RoundedCornerShape(8.dp))
      .background(MaterialTheme.colorScheme.surface)
      .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
      .padding(3.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp)
  ) {
    for (r in 0 until 4) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        for (c in 0 until 4) {
          val idx = r * 4 + c
          val hasPad = idx < padCount
          val tileColor = when {
            !hasPad -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            idx < colors.size && colors[idx] != null -> Color(colors[idx]!!.toInt()).copy(alpha = 0.88f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
          }
          Box(
            modifier = Modifier
              .weight(1f)
              .height(6.dp)
              .clip(RoundedCornerShape(1.5.dp))
              .background(tileColor)
          )
        }
      }
    }
  }
}
