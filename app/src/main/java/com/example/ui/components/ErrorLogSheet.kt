package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppErrorLog
import com.example.domain.model.PadErrorType
import com.example.ui.theme.SoundPadTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun errorTypeIcon(type: PadErrorType): ImageVector = when (type) {
  PadErrorType.CORRUPT_HEADER -> Icons.Filled.BrokenImage
  PadErrorType.DRM_PROTECTED -> Icons.Filled.Lock
  PadErrorType.UNSUPPORTED_FORMAT -> Icons.Filled.AudioFile
  PadErrorType.MISSING_PERMISSION -> Icons.Filled.FolderOff
  PadErrorType.PLAYBACK_ERROR -> Icons.Filled.Warning
  PadErrorType.APP_CRASH -> Icons.Filled.BugReport
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorLogSheet(
  errorLogs: List<AppErrorLog>,
  onClearLogs: () -> Unit,
  onExportTxt: () -> Unit,
  onDeleteLog: (String) -> Unit = {},
  onFixLog: (AppErrorLog) -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val spacing = SoundPadTheme.spacing
  var selectedFilter by remember { mutableStateOf<PadErrorType?>(null) }
  val filteredLogs = remember(errorLogs, selectedFilter) {
    if (selectedFilter == null) errorLogs else errorLogs.filter { it.errorType == selectedFilter }
  }
  val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = spacing.lg + spacing.xs, vertical = spacing.sm + spacing.xxs),
      verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.error_log_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = stringResource(R.string.error_log_count, errorLogs.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
          OutlinedIconButton(
            enabled = errorLogs.isNotEmpty(),
            onClick = onExportTxt,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("export_error_logs_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Download,
              contentDescription = stringResource(R.string.cd_export_error_logs),
              tint = MaterialTheme.colorScheme.primary
            )
          }
          OutlinedIconButton(
            enabled = errorLogs.isNotEmpty(),
            onClick = onClearLogs,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("clear_error_logs_button")
          ) {
            Icon(
              imageVector = Icons.Filled.DeleteSweep,
              contentDescription = stringResource(R.string.cd_clear_error_logs),
              tint = MaterialTheme.colorScheme.error
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.minimumInteractiveComponentSize()
          ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
          }
        }
      }

      LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)) {
        item {
          FilterChip(
            selected = selectedFilter == null,
            onClick = { selectedFilter = null },
            label = {
              Text(
                stringResource(R.string.error_log_filter_all, errorLogs.size),
                style = MaterialTheme.typography.labelSmall
              )
            },
            modifier = Modifier.testTag("error_filter_all")
          )
        }
        items(PadErrorType.entries) { type ->
          val count = errorLogs.count { it.errorType == type }
          FilterChip(
            selected = selectedFilter == type,
            onClick = { selectedFilter = if (selectedFilter == type) null else type },
            leadingIcon = {
              Icon(
                imageVector = errorTypeIcon(type),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
              )
            },
            label = {
              Text(
                text = if (count > 0) "${type.label} ($count)" else type.label,
                style = MaterialTheme.typography.labelSmall
              )
            },
            modifier = Modifier.testTag("error_filter_${type.name.lowercase()}")
          )
        }
      }

      if (filteredLogs.isEmpty()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
          ) {
            Icon(
              imageVector = Icons.Filled.CheckCircle,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp)
            )
            Text(
              text = stringResource(R.string.error_log_empty_title),
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = stringResource(R.string.error_log_empty_desc),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)
        ) {
          items(filteredLogs, key = { it.id }) { log ->
            ErrorLogCard(
              log = log,
              formattedTime = dateFormatter.format(Date(log.timestampMs)),
              onFixLog = { onFixLog(log) },
              onDeleteLog = { onDeleteLog(log.id) }
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(spacing.xs + spacing.xxs))
    }
  }
}

@Composable
private fun ErrorLogCard(
  log: AppErrorLog,
  formattedTime: String,
  onFixLog: () -> Unit,
  onDeleteLog: () -> Unit
) {
  val spacing = SoundPadTheme.spacing
  val context = LocalContext.current
  var expanded by remember(log.id) { mutableStateOf(false) }
  var copiedFeedback by remember(log.id) { mutableStateOf(false) }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.medium,
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(spacing.md),
      verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = MaterialTheme.shapes.extraSmall,
          color = MaterialTheme.colorScheme.errorContainer
        ) {
          Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
          ) {
            Icon(
              imageVector = errorTypeIcon(log.errorType),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(13.dp)
            )
            Text(
              text = log.errorType.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
        Text(
          text = formattedTime,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(
        text = log.fileName,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )

      Text(
        text = log.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
      )

      if (log.suggestion.isNotBlank()) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.small,
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
          Text(
            text = stringResource(R.string.error_log_suggestion, log.suggestion),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(spacing.sm)
          )
        }
      }

      if (log.stackTrace.isNotBlank()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = spacing.minTouchTarget)
            .clickable { expanded = !expanded }
            .padding(vertical = spacing.xxs),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(
              if (expanded) R.string.error_log_hide_stacktrace else R.string.error_log_show_stacktrace
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
          Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
          )
        }
        if (expanded) {
          Box(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = log.stackTrace,
              style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 14,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      val suggestionFormatted = if (log.suggestion.isNotBlank()) {
        stringResource(R.string.error_log_suggestion, log.suggestion)
      } else ""
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onFixLog,
          modifier = Modifier.testTag("error_log_fix_${log.id}")
        ) {
          Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(15.dp))
          Text(" ${stringResource(R.string.action_fix)}", style = MaterialTheme.typography.labelSmall)
        }
        TextButton(
          onClick = {
            val detail = buildString {
              appendLine("[${log.errorType.label}] ${log.fileName}")
              appendLine(log.message)
              if (suggestionFormatted.isNotBlank()) appendLine(suggestionFormatted)
              if (log.stackTrace.isNotBlank()) appendLine(log.stackTrace)
            }
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("SoundPad Error Log", detail))
            copiedFeedback = true
          },
          modifier = Modifier.testTag("error_log_copy_${log.id}")
        ) {
          Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
          Text(
            text = if (copiedFeedback) " ${stringResource(R.string.error_log_copied)}" else " ${stringResource(R.string.action_copy)}",
            style = MaterialTheme.typography.labelSmall
          )
        }
        TextButton(
          onClick = onDeleteLog,
          modifier = Modifier.testTag("error_log_delete_${log.id}")
        ) {
          Icon(
            Icons.Filled.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(15.dp)
          )
          Text(
            text = " ${stringResource(R.string.action_delete)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }
    }
  }
}
