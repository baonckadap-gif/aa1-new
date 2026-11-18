package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.scheduler.PadSchedulerManager
import com.example.ui.BankPageUi
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerSheet(
  tasks: List<ScheduledPadTask>,
  pads: List<SoundPad>,
  bankPages: List<BankPageUi>,
  playlists: List<SequencerPlaylist>,
  canScheduleExactAlarms: Boolean,
  onRequestExactAlarmPermission: () -> Unit,
  onSaveTask: (ScheduledPadTask) -> Unit,
  onToggleTaskEnabled: (ScheduledPadTask, Boolean) -> Unit,
  onDeleteTask: (String) -> Unit,
  onTestTriggerTask: (ScheduledPadTask) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var editingTask by remember { mutableStateOf<ScheduledPadTask?>(null) }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
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
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column {
              Text(stringResource(R.string.scheduler_title), style = MaterialTheme.typography.titleMedium)
              Text(
                text = "AlarmManager Exact + Boot Restore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          IconButton(onClick = onDismiss, modifier = Modifier.testTag("close_scheduler_sheet")) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
          }
        }
      }

      if (!canScheduleExactAlarms) {
        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Text(
                text = stringResource(R.string.scheduler_exact_alarm_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
              )
              FilledTonalButton(onClick = onRequestExactAlarmPermission) {
                Text(stringResource(R.string.scheduler_grant_exact_alarm))
              }
            }
          }
        }
      }

      item {
        Button(
          onClick = {
            val defaultTargetId = pads.firstOrNull()?.id ?: ""
            editingTask = ScheduledPadTask(
              id = "sched_${UUID.randomUUID()}",
              title = "Jadwal ${tasks.size + 1}",
              targetType = ScheduleTargetType.PAD,
              targetId = defaultTargetId,
              hour = 9,
              minute = 0
            )
          },
          enabled = pads.isNotEmpty() || bankPages.isNotEmpty() || playlists.isNotEmpty(),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("scheduler_add_button")
        ) {
          Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.scheduler_add))
        }
      }

      if (tasks.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
          ) {
            Text(
              text = stringResource(R.string.scheduler_empty),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            )
          }
        }
      } else {
        items(tasks, key = { it.id }) { task ->
          val targetLabel = when (task.targetType) {
            ScheduleTargetType.PAD -> pads.firstOrNull { it.id == task.targetId }?.displayName ?: "Pad tidak ditemukan"
            ScheduleTargetType.BANK -> "Bank ${(task.targetId.toIntOrNull() ?: 0) + 1}"
            ScheduleTargetType.SEQUENCER -> playlists.firstOrNull { it.id == task.targetId }?.name ?: "Playlist"
          }
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
              1.dp,
              if (task.enabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
            )
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "%02d:%02d · %s".format(task.hour, task.minute, task.title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${task.targetType.label}: $targetLabel · ${PadSchedulerManager.formatDaysSummary(task.repeatMode, task.daysOfWeekMask)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "Vol ${(task.volume * 100).roundToInt()}% · Fade ${task.fadeInMs}ms" +
                      (if (task.autoStopMinutes > 0) " · Auto-Stop ${task.autoStopMinutes}m" else "") +
                      (if (task.loopPlayback) " · Loop" else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = task.enabled,
                  onCheckedChange = { onToggleTaskEnabled(task, it) }
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
              ) {
                TextButton(onClick = { onTestTriggerTask(task) }) {
                  Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Uji")
                }
                TextButton(onClick = { editingTask = task }) {
                  Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Edit")
                }
                TextButton(onClick = { onDeleteTask(task.id) }) {
                  Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(20.dp)) }
    }
  }

  editingTask?.let { draft ->
    ScheduleEditorDialog(
      initial = draft,
      pads = pads,
      bankPages = bankPages,
      playlists = playlists,
      onSave = { saved ->
        onSaveTask(saved)
        editingTask = null
      },
      onDismiss = { editingTask = null }
    )
  }
}

@Composable
private fun ScheduleEditorDialog(
  initial: ScheduledPadTask,
  pads: List<SoundPad>,
  bankPages: List<BankPageUi>,
  playlists: List<SequencerPlaylist>,
  onSave: (ScheduledPadTask) -> Unit,
  onDismiss: () -> Unit
) {
  var title by remember { mutableStateOf(initial.title) }
  var targetType by remember { mutableStateOf(initial.targetType) }
  var targetId by remember { mutableStateOf(initial.targetId) }
  var hour by remember { mutableIntStateOf(initial.hour) }
  var minute by remember { mutableIntStateOf(initial.minute) }
  var repeatMode by remember { mutableStateOf(initial.repeatMode) }
  var daysMask by remember { mutableIntStateOf(initial.daysOfWeekMask) }
  var volume by remember { mutableFloatStateOf(initial.volume) }
  var fadeInSec by remember { mutableFloatStateOf(initial.fadeInMs / 1000f) }
  var autoStopMin by remember { mutableFloatStateOf(initial.autoStopMinutes.toFloat()) }
  var loopPlayback by remember { mutableStateOf(initial.loopPlayback) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Atur Jadwal Pemutaran") },
    text = {
      LazyColumn(
        modifier = Modifier.height(380.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Nama Jadwal") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }

        item {
          Text("Target Pemutaran", style = MaterialTheme.typography.labelMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ScheduleTargetType.entries.forEach { t ->
              FilterChip(
                selected = targetType == t,
                onClick = {
                  targetType = t
                  targetId = when (t) {
                    ScheduleTargetType.PAD -> pads.firstOrNull()?.id ?: ""
                    ScheduleTargetType.BANK -> (bankPages.firstOrNull()?.bankKey ?: 0).toString()
                    ScheduleTargetType.SEQUENCER -> playlists.firstOrNull()?.id ?: ""
                  }
                },
                label = { Text(t.label) }
              )
            }
          }
        }

        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            when (targetType) {
              ScheduleTargetType.PAD -> pads.take(24).forEach { pad ->
                FilterChip(
                  selected = targetId == pad.id,
                  onClick = { targetId = pad.id },
                  label = { Text(pad.displayName, maxLines = 1) }
                )
              }
              ScheduleTargetType.BANK -> bankPages.distinctBy { it.bankKey }.forEach { bp ->
                FilterChip(
                  selected = targetId == bp.bankKey.toString(),
                  onClick = { targetId = bp.bankKey.toString() },
                  label = { Text(bp.title) }
                )
              }
              ScheduleTargetType.SEQUENCER -> playlists.forEach { pl ->
                FilterChip(
                  selected = targetId == pl.id,
                  onClick = { targetId = pl.id },
                  label = { Text(pl.name) }
                )
              }
            }
          }
        }

        item {
          Text("Waktu (%02d:%02d)".format(hour, minute), style = MaterialTheme.typography.labelMedium)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Jam: $hour", style = MaterialTheme.typography.labelSmall)
              Slider(value = hour.toFloat(), onValueChange = { hour = it.roundToInt().coerceIn(0, 23) }, valueRange = 0f..23f)
            }
            Column(modifier = Modifier.weight(1f)) {
              Text("Menit: $minute", style = MaterialTheme.typography.labelSmall)
              Slider(value = minute.toFloat(), onValueChange = { minute = it.roundToInt().coerceIn(0, 59) }, valueRange = 0f..59f)
            }
          }
        }

        item {
          Text("Pengulangan", style = MaterialTheme.typography.labelMedium)
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            ScheduleRepeatMode.entries.forEach { rm ->
              FilterChip(
                selected = repeatMode == rm,
                onClick = { repeatMode = rm },
                label = { Text(rm.label) }
              )
            }
          }
          if (repeatMode == ScheduleRepeatMode.CUSTOM_DAYS) {
            val dayNames = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
            Row(
              modifier = Modifier.horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              dayNames.forEachIndexed { idx, label ->
                val checked = (daysMask and (1 shl idx)) != 0
                FilterChip(
                  selected = checked,
                  onClick = {
                    daysMask = if (checked) daysMask and (1 shl idx).inv() else daysMask or (1 shl idx)
                  },
                  label = { Text(label) }
                )
              }
            }
          }
        }

        item {
          Text("Volume: ${(volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
          Slider(value = volume, onValueChange = { volume = it }, valueRange = 0.05f..1f)

          Text("Fade In: ${(fadeInSec * 1000).roundToInt()} ms", style = MaterialTheme.typography.labelSmall)
          Slider(value = fadeInSec, onValueChange = { fadeInSec = it }, valueRange = 0f..5f)

          Text(
            text = if (autoStopMin < 0.5f) "Auto Stop: Mati" else "Auto Stop: ${autoStopMin.roundToInt()} menit",
            style = MaterialTheme.typography.labelSmall
          )
          Slider(value = autoStopMin, onValueChange = { autoStopMin = it }, valueRange = 0f..60f)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Ulangi (Loop) saat berjalan", style = MaterialTheme.typography.bodySmall)
            Switch(checked = loopPlayback, onCheckedChange = { loopPlayback = it })
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onSave(
            initial.copy(
              title = title.ifBlank { "Jadwal Otomatis" },
              targetType = targetType,
              targetId = targetId,
              hour = hour,
              minute = minute,
              repeatMode = repeatMode,
              daysOfWeekMask = daysMask,
              enabled = true,
              volume = volume,
              fadeInMs = (fadeInSec * 1000f).toLong(),
              autoStopMinutes = autoStopMin.roundToInt(),
              loopPlayback = loopPlayback
            )
          )
        }
      ) {
        Text("Simpan")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Batal") }
    }
  )
}
