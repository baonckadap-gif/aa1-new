package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.SequencerPlayMode
import com.example.domain.model.SequencerPlaybackState
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.midi.MidiMessageParser
import com.example.ui.theme.SoundPadTheme
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequencerSheet(
  playlists: List<SequencerPlaylist>,
  playbackState: SequencerPlaybackState,
  pads: List<SoundPad>,
  onSavePlaylist: (SequencerPlaylist) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  onPlayPlaylist: (SequencerPlaylist, Int) -> Unit,
  onTogglePlayPause: (SequencerPlaylist?) -> Unit,
  onNext: () -> Unit,
  onPrev: () -> Unit,
  onStop: () -> Unit,
  onArmMidiLearn: (SequencerPlaylist) -> Unit,
  onClearMidiTrigger: (SequencerPlaylist) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val spacing = SoundPadTheme.spacing
  var selectedId by remember(playlists) {
    mutableStateOf(playbackState.activePlaylistId ?: playlists.firstOrNull()?.id)
  }
  val activeDraft = playlists.firstOrNull { it.id == selectedId } ?: playlists.firstOrNull()
  var showCreateDialog by remember { mutableStateOf(false) }
  var showAddPadDialog by remember { mutableStateOf(false) }
  var showTriggerPadDialog by remember { mutableStateOf(false) }

  LaunchedEffect(playlists) {
    if (selectedId == null && playlists.isNotEmpty()) {
      selectedId = playlists.first().id
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.sm),
      verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs),
            modifier = Modifier.weight(1f)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Column {
              Text(stringResource(R.string.sequencer_title), style = MaterialTheme.typography.titleMedium)
              Text(
                text = stringResource(R.string.sequencer_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("close_sequencer_sheet")
          ) {
            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
          }
        }
      }

      // Active Transport Card
      if (playbackState.isPlaying || activeDraft != null) {
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
              1.dp,
              if (playbackState.isPlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
            )
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
              verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
              Text(
                text = if (playbackState.isPlaying) {
                  stringResource(
                    R.string.sequencer_running_header,
                    playbackState.activePlaylistName.orEmpty(),
                    playbackState.currentIndex + 1,
                    playbackState.queueSize
                  )
                } else {
                  stringResource(R.string.sequencer_ready_header, activeDraft?.name ?: "-")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
              )
              Text(
                text = when {
                  playbackState.isInGap -> stringResource(R.string.sequencer_gap_countdown, playbackState.remainingGapSec)
                  playbackState.currentPadName != null -> playbackState.currentPadName
                  else -> stringResource(R.string.sequencer_idle_prompt)
                },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically
              ) {
                IconButton(
                  onClick = onPrev,
                  enabled = playbackState.isPlaying,
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.cd_sequencer_prev))
                }
                Button(
                  onClick = { onTogglePlayPause(activeDraft) },
                  enabled = activeDraft != null && activeDraft.padIds.isNotEmpty(),
                  modifier = Modifier
                    .weight(1f)
                    .height(spacing.minTouchTarget)
                ) {
                  Icon(
                    if (playbackState.isPlaying && !playbackState.isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null
                  )
                  Spacer(modifier = Modifier.width(spacing.xs))
                  Text(
                    stringResource(
                      if (playbackState.isPlaying && !playbackState.isPaused) {
                        R.string.sequencer_action_pause
                      } else {
                        R.string.sequencer_action_play
                      }
                    )
                  )
                }
                IconButton(
                  onClick = onNext,
                  enabled = playbackState.isPlaying,
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.cd_sequencer_next))
                }
                IconButton(
                  onClick = onStop,
                  enabled = playbackState.isPlaying,
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.cd_sequencer_stop),
                    tint = MaterialTheme.colorScheme.error
                  )
                }
              }
            }
          }
        }
      }

      // Playlist tabs + New Playlist
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(spacing.sm),
          verticalAlignment = Alignment.CenterVertically
        ) {
          playlists.forEach { pl ->
            FilterChip(
              selected = activeDraft?.id == pl.id,
              onClick = { selectedId = pl.id },
              label = { Text("${pl.name} (${pl.padIds.size})") }
            )
          }
          FilledTonalButton(onClick = { showCreateDialog = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(spacing.xs))
            Text(stringResource(R.string.sequencer_new_playlist))
          }
        }
      }

      if (activeDraft == null) {
        item {
          Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Text(
              text = stringResource(R.string.sequencer_empty),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg)
            )
          }
        }
      } else {
        // Playlist Modes, Gap & Crossfade
        item {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
              verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(stringResource(R.string.sequencer_play_mode_label), style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = { onDeletePlaylist(activeDraft.id) }) {
                  Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(spacing.xs))
                  Text(stringResource(R.string.sequencer_delete_playlist), color = MaterialTheme.colorScheme.error)
                }
              }
              Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)
              ) {
                SequencerPlayMode.entries.forEach { m ->
                  FilterChip(
                    selected = activeDraft.mode == m,
                    onClick = { onSavePlaylist(activeDraft.copy(mode = m)) },
                    label = { Text(m.label) }
                  )
                }
              }

              Text(
                text = stringResource(R.string.sequencer_gap_label, activeDraft.gapSeconds),
                style = MaterialTheme.typography.labelSmall
              )
              Slider(
                value = activeDraft.gapSeconds,
                onValueChange = { onSavePlaylist(activeDraft.copy(gapSeconds = it)) },
                valueRange = 0f..15f
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = stringResource(R.string.sequencer_crossfade_label, activeDraft.crossfadeDurationMs),
                  style = MaterialTheme.typography.bodySmall
                )
                Switch(
                  checked = activeDraft.crossfadeEnabled,
                  onCheckedChange = { onSavePlaylist(activeDraft.copy(crossfadeEnabled = it)) }
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
              ) {
                OutlinedButton(
                  onClick = { showTriggerPadDialog = true },
                  modifier = Modifier
                    .weight(1f)
                    .height(spacing.minTouchTarget)
                ) {
                  val tName = pads.firstOrNull { it.id == activeDraft.triggerPadId }?.displayName
                  Text(
                    text = tName?.let { stringResource(R.string.sequencer_trigger_pad_selected, it) }
                      ?: stringResource(R.string.sequencer_trigger_pad_pick),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                OutlinedButton(
                  onClick = {
                    if (activeDraft.triggerMidiNote != null) {
                      onClearMidiTrigger(activeDraft)
                    } else {
                      onArmMidiLearn(activeDraft)
                    }
                  },
                  modifier = Modifier
                    .weight(1f)
                    .height(spacing.minTouchTarget)
                ) {
                  Icon(Icons.Filled.Piano, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(spacing.xs))
                  Text(
                    activeDraft.triggerMidiNote?.let {
                      "MIDI ${MidiMessageParser.formatNoteName(it)} ×"
                    } ?: stringResource(R.string.sequencer_midi_trigger_label),
                    maxLines = 1
                  )
                }
              }
            }
          }
        }

        // Queue items Header + Add Pad
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.sequencer_queue_header, activeDraft.padIds.size),
              style = MaterialTheme.typography.labelMedium
            )
            FilledTonalButton(onClick = { showAddPadDialog = true }, enabled = pads.isNotEmpty()) {
              Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(spacing.xs))
              Text(stringResource(R.string.sequencer_add_pad))
            }
          }
        }

        itemsIndexed(activeDraft.padIds, key = { idx, padId -> "${idx}_$padId" }) { index, padId ->
          val pad = pads.firstOrNull { it.id == padId }
          val isCurrent = playbackState.isPlaying &&
            playbackState.activePlaylistId == activeDraft.id &&
            playbackState.currentIndex == index
          var dragAccumY by remember { mutableFloatStateOf(0f) }

          Surface(
            modifier = Modifier.pointerInput(activeDraft.id, index, activeDraft.padIds.size) {
              detectDragGesturesAfterLongPress(
                onDragStart = { dragAccumY = 0f },
                onDrag = { change, dragAmount ->
                  change.consume()
                  dragAccumY += dragAmount.y
                  if (dragAccumY > 64f && index + 1 < activeDraft.padIds.size) {
                    dragAccumY = 0f
                    val mutable = activeDraft.padIds.toMutableList()
                    val moved = mutable.removeAt(index)
                    mutable.add(index + 1, moved)
                    onSavePlaylist(activeDraft.copy(padIds = mutable))
                  } else if (dragAccumY < -64f && index - 1 >= 0) {
                    dragAccumY = 0f
                    val mutable = activeDraft.padIds.toMutableList()
                    val moved = mutable.removeAt(index)
                    mutable.add(index - 1, moved)
                    onSavePlaylist(activeDraft.copy(padIds = mutable))
                  }
                }
              )
            },
            shape = MaterialTheme.shapes.small,
            color = if (isCurrent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm + spacing.xxs, vertical = spacing.xs),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
              Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.cd_sequencer_drag_handle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
              Text("${index + 1}.", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = pad?.displayName ?: stringResource(R.string.sequencer_missing_pad),
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              IconButton(
                onClick = { onPlayPlaylist(activeDraft, index) },
                enabled = pad != null,
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sequencer_play_item_$index")
              ) {
                Icon(
                  Icons.Filled.PlayArrow,
                  contentDescription = stringResource(R.string.cd_sequencer_play_here),
                  modifier = Modifier.size(18.dp)
                )
              }
              IconButton(
                enabled = index > 0,
                onClick = {
                  val mutable = activeDraft.padIds.toMutableList()
                  val moved = mutable.removeAt(index)
                  mutable.add(index - 1, moved)
                  onSavePlaylist(activeDraft.copy(padIds = mutable))
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sequencer_move_up_$index")
              ) {
                Icon(
                  Icons.Filled.ArrowUpward,
                  contentDescription = stringResource(R.string.cd_sequencer_move_up),
                  modifier = Modifier.size(16.dp)
                )
              }
              IconButton(
                enabled = index < activeDraft.padIds.lastIndex,
                onClick = {
                  val mutable = activeDraft.padIds.toMutableList()
                  val moved = mutable.removeAt(index)
                  mutable.add(index + 1, moved)
                  onSavePlaylist(activeDraft.copy(padIds = mutable))
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sequencer_move_down_$index")
              ) {
                Icon(
                  Icons.Filled.ArrowDownward,
                  contentDescription = stringResource(R.string.cd_sequencer_move_down),
                  modifier = Modifier.size(16.dp)
                )
              }
              IconButton(
                onClick = {
                  val mutable = activeDraft.padIds.toMutableList()
                  mutable.removeAt(index)
                  onSavePlaylist(activeDraft.copy(padIds = mutable))
                },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("sequencer_remove_item_$index")
              ) {
                Icon(
                  Icons.Filled.Delete,
                  contentDescription = stringResource(R.string.cd_sequencer_remove),
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(spacing.lg + spacing.xs)) }
    }
  }

  if (showCreateDialog) {
    val defaultSetlistName = stringResource(R.string.sequencer_default_setlist_name, playlists.size + 1)
    val fallbackSetlistName = stringResource(R.string.sequencer_fallback_setlist_name)
    var name by remember(defaultSetlistName) { mutableStateOf(defaultSetlistName) }
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text(stringResource(R.string.sequencer_create_dialog_title)) },
      text = {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.sequencer_playlist_name_label)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        Button(
          onClick = {
            val newPl = SequencerPlaylist(
              id = "seq_${UUID.randomUUID()}",
              name = name.ifBlank { fallbackSetlistName }
            )
            onSavePlaylist(newPl)
            selectedId = newPl.id
            showCreateDialog = false
          }
        ) { Text(stringResource(R.string.action_create)) }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(R.string.action_cancel)) }
      }
    )
  }

  if (showAddPadDialog && activeDraft != null) {
    AlertDialog(
      onDismissRequest = { showAddPadDialog = false },
      title = { Text(stringResource(R.string.sequencer_add_pad_dialog_title)) },
      text = {
        LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)) {
          itemsIndexed(pads) { _, pad ->
            Surface(
              onClick = {
                onSavePlaylist(activeDraft.copy(padIds = activeDraft.padIds + pad.id))
                showAddPadDialog = false
              },
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainer
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xxs),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(pad.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              }
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showAddPadDialog = false }) { Text(stringResource(R.string.action_close)) }
      }
    )
  }

  if (showTriggerPadDialog && activeDraft != null) {
    AlertDialog(
      onDismissRequest = { showTriggerPadDialog = false },
      title = { Text(stringResource(R.string.sequencer_trigger_pad_dialog_title)) },
      text = {
        LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs)) {
          item {
            Surface(
              onClick = {
                onSavePlaylist(activeDraft.copy(triggerPadId = null))
                showTriggerPadDialog = false
              },
              shape = MaterialTheme.shapes.small,
              color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
              Text(
                text = stringResource(R.string.sequencer_trigger_pad_none),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(spacing.md)
              )
            }
          }
          itemsIndexed(pads) { _, pad ->
            Surface(
              onClick = {
                onSavePlaylist(activeDraft.copy(triggerPadId = pad.id))
                showTriggerPadDialog = false
              },
              shape = MaterialTheme.shapes.small,
              color = if (activeDraft.triggerPadId == pad.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
            ) {
              Text(
                text = pad.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(spacing.md)
              )
            }
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = { showTriggerPadDialog = false }) { Text(stringResource(R.string.action_close)) }
      }
    )
  }
}
