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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
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

  var padSearchQuery by remember { mutableStateOf("") }
  var selectedBankKey by remember { mutableStateOf<Int?>(null) }
  val bankFilters = remember(pads, bankPages) {
    buildSchedulerBankFilters(pads = pads, bankPages = bankPages)
  }
  val activeBankFilter = remember(bankFilters, selectedBankKey) {
    selectedBankKey?.let { key -> bankFilters.firstOrNull { it.bankKey == key } }
  }
  val filteredPads = remember(pads, padSearchQuery, activeBankFilter) {
    filterSchedulerPads(
      pads = pads,
      searchQuery = padSearchQuery,
      bankFilter = activeBankFilter
    )
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Atur Jadwal Pemutaran") },
    text = {
      LazyColumn(
        modifier = Modifier.height(420.dp),
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
          when (targetType) {
            ScheduleTargetType.PAD -> {
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                OutlinedTextField(
                  value = padSearchQuery,
                  onValueChange = { padSearchQuery = it },
                  placeholder = { Text(stringResource(R.string.scheduler_search_pads_label)) },
                  leadingIcon = {
                    Icon(
                      Icons.Filled.Search,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                  },
                  trailingIcon = {
                    if (padSearchQuery.isNotEmpty()) {
                      IconButton(
                        onClick = { padSearchQuery = "" },
                        modifier = Modifier.testTag("scheduler_pad_search_clear")
                      ) {
                        Icon(
                          Icons.Filled.Close,
                          contentDescription = stringResource(R.string.scheduler_clear_search),
                          modifier = Modifier.size(18.dp)
                        )
                      }
                    }
                  },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scheduler_pad_search_input")
                )

                if (bankFilters.isNotEmpty()) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    FilterChip(
                      selected = selectedBankKey == null,
                      onClick = { selectedBankKey = null },
                      label = { Text(stringResource(R.string.scheduler_all_banks)) },
                      modifier = Modifier.testTag("scheduler_bank_filter_all")
                    )
                    bankFilters.forEach { option ->
                      FilterChip(
                        selected = selectedBankKey == option.bankKey,
                        onClick = {
                          selectedBankKey = if (selectedBankKey == option.bankKey) null else option.bankKey
                        },
                        label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("scheduler_bank_filter_${option.bankKey}")
                      )
                    }
                  }
                }

                Text(
                  text = stringResource(R.string.scheduler_pad_count_shown, filteredPads.size),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                  if (filteredPads.isEmpty()) {
                    Box(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = stringResource(R.string.scheduler_no_matching_pads),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  } else {
                    LazyColumn(
                      modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 180.dp)
                        .testTag("scheduler_pad_picker_list"),
                      contentPadding = PaddingValues(6.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      items(filteredPads, key = { it.id }) { pad ->
                        val isSelected = targetId == pad.id
                        Surface(
                          shape = RoundedCornerShape(8.dp),
                          color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                          } else {
                            MaterialTheme.colorScheme.surface
                          },
                          border = if (isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                          } else {
                            null
                          },
                          modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { targetId = pad.id }
                            .testTag("scheduler_pad_option_${pad.id}")
                        ) {
                          Row(
                            modifier = Modifier
                              .fillMaxWidth()
                              .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                          ) {
                            Icon(
                              imageVector = if (isSelected) {
                                Icons.Filled.CheckCircle
                              } else {
                                Icons.Filled.RadioButtonUnchecked
                              },
                              contentDescription = null,
                              tint = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                              } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                              },
                              modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                              Text(
                                text = pad.displayName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) {
                                  MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                  MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                              )
                              val bankTag = pad.subfolderName.ifBlank {
                                stringResource(R.string.scheduler_bank_fallback, pad.bankIndex + 1)
                              }
                              Text(
                                text = "$bankTag · #${pad.positionIndex + 1} · ${pad.extension}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) {
                                  MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else {
                                  MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                              )
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            ScheduleTargetType.BANK -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                bankPages.distinctBy { it.bankKey }.forEach { bp ->
                  FilterChip(
                    selected = targetId == bp.bankKey.toString(),
                    onClick = { targetId = bp.bankKey.toString() },
                    label = { Text(bp.title) }
                  )
                }
              }
            }
            ScheduleTargetType.SEQUENCER -> {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                playlists.forEach { pl ->
                  FilterChip(
                    selected = targetId == pl.id,
                    onClick = { targetId = pl.id },
                    label = { Text(pl.name) }
                  )
                }
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

data class SchedulerBankFilterOption(
  val bankKey: Int,
  val label: String,
  val padIds: Set<String>? = null
)

fun buildSchedulerBankFilters(
  pads: List<SoundPad>,
  bankPages: List<BankPageUi>
): List<SchedulerBankFilterOption> {
  if (bankPages.isNotEmpty()) {
    val grouped = bankPages.groupBy { it.bankKey }
    val options = grouped.entries.mapNotNull { (bankKey, pages) ->
      val padIds = pages.flatMap { it.pads }.mapTo(mutableSetOf()) { it.id }
      if (padIds.isEmpty()) {
        null
      } else {
        val rawTitle = pages.first().title
        val cleanTitle = if (pages.size > 1) {
          rawTitle.replace(Regex("""\s*\(\d+\)$"""), "").ifBlank { rawTitle }
        } else {
          rawTitle
        }
        SchedulerBankFilterOption(
          bankKey = bankKey,
          label = cleanTitle,
          padIds = padIds
        )
      }
    }
    if (options.isNotEmpty()) return options
  }

  return pads.groupBy { it.bankIndex }
    .toSortedMap()
    .map { (bankIdx, bankPads) ->
      val label = bankPads.firstOrNull { it.subfolderName.isNotBlank() }?.subfolderName
        ?: "Bank ${bankIdx + 1}"
      SchedulerBankFilterOption(
        bankKey = bankIdx,
        label = label,
        padIds = null
      )
    }
}

fun filterSchedulerPads(
  pads: List<SoundPad>,
  searchQuery: String,
  bankFilter: SchedulerBankFilterOption?
): List<SoundPad> {
  val query = searchQuery.trim().lowercase()
  return pads.filter { pad ->
    val matchesBank = when {
      bankFilter == null -> true
      bankFilter.padIds != null -> pad.id in bankFilter.padIds
      else -> pad.bankIndex == bankFilter.bankKey
    }
    if (!matchesBank) return@filter false
    if (query.isEmpty()) return@filter true
    pad.displayName.lowercase().contains(query) ||
      pad.fileName.lowercase().contains(query) ||
      pad.subfolderName.lowercase().contains(query) ||
      pad.extension.lowercase().contains(query) ||
      pad.categoryTag.lowercase().contains(query)
  }
}
