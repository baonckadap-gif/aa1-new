package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PlayMode
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadProTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TopControlBar(
  activeProfileName: String,
  folderName: String?,
  padCount: Int,
  isScanning: Boolean,
  newlyDetectedCount: Int,
  playMode: PlayMode,
  gridConfig: GridConfig,
  isSearchVisible: Boolean,
  searchQuery: String,
  isFullscreen: Boolean,
  isCrossfadeEnabled: Boolean,
  isLocked: Boolean = false,
  midiConnectedCount: Int = 0,
  isMidiLearnMode: Boolean = false,
  errorLogCount: Int = 0,
  isSequencerPlaying: Boolean = false,
  activeSchedulesCount: Int = 0,
  onPickFolderClick: () -> Unit,
  onRescanClick: () -> Unit,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onPlayModeSelected: (PlayMode) -> Unit,
  onOpenProfiles: () -> Unit,
  onOpenMidiController: () -> Unit = {},
  onOpenAudioOutput: () -> Unit = {},
  onOpenSequencer: () -> Unit = {},
  onOpenScheduler: () -> Unit = {},
  onToggleLockMode: () -> Unit = {},
  onLockUnlockHint: () -> Unit = {},
  onOpenGridSettings: () -> Unit,
  onToggleCrossfade: () -> Unit,
  onToggleFullscreen: () -> Unit,
  onOpenErrorLogs: () -> Unit = {},
  onOpenMiuiGuide: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Column(modifier = Modifier.widthIn(min = 90.dp, max = 152.dp)) {
          Text(
            text = folderName ?: stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "$activeProfileName · ${padCount}P · ${gridConfig.displayLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }

        Row(
          modifier = Modifier
            .weight(1f)
            .horizontalScroll(rememberScrollState()),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.End
        ) {
          IconButton(
            enabled = !isLocked,
            onClick = onPickFolderClick,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("folder_picker_button")
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = stringResource(R.string.cd_select_folder),
              tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary
            )
          }

          IconButton(
            enabled = !isLocked,
            onClick = onOpenProfiles,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("profiles_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Bookmarks,
              contentDescription = stringResource(R.string.cd_open_profiles),
              tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(
            onClick = onToggleSearch,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("search_toggle_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = stringResource(R.string.cd_search_pads),
              tint = if (isSearchVisible || searchQuery.isNotEmpty()) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface
              }
            )
          }

          IconButton(
            onClick = onRescanClick,
            enabled = folderName != null && !isScanning && !isLocked,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("rescan_button")
          ) {
            if (isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
              )
            } else {
              BadgedBox(
                badge = {
                  if (newlyDetectedCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                      Text(text = "+$newlyDetectedCount", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.Refresh,
                  contentDescription = stringResource(R.string.cd_rescan_folder),
                  tint = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }

          IconButton(
            enabled = !isLocked,
            onClick = onToggleCrossfade,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("crossfade_toggle_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.CompareArrows,
              contentDescription = stringResource(R.string.cd_toggle_crossfade),
              tint = when {
                isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                isCrossfadeEnabled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              }
            )
          }

          IconButton(
            onClick = onOpenAudioOutput,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("audio_output_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Headphones,
              contentDescription = stringResource(R.string.cd_audio_output),
              tint = MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(
            enabled = !isLocked,
            onClick = onOpenSequencer,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("sequencer_button")
          ) {
            BadgedBox(
              badge = {
                if (isSequencerPlaying) {
                  Badge(containerColor = MaterialTheme.colorScheme.primary)
                }
              }
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = stringResource(R.string.cd_sequencer),
                tint = when {
                  isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                  isSequencerPlaying -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.onSurface
                }
              )
            }
          }

          IconButton(
            enabled = !isLocked,
            onClick = onOpenScheduler,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("scheduler_button")
          ) {
            BadgedBox(
              badge = {
                if (activeSchedulesCount > 0) {
                  Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                    Text(text = "$activeSchedulesCount", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = stringResource(R.string.cd_scheduler),
                tint = when {
                  isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                  activeSchedulesCount > 0 -> MaterialTheme.colorScheme.secondary
                  else -> MaterialTheme.colorScheme.onSurface
                }
              )
            }
          }

          IconButton(
            enabled = !isLocked,
            onClick = onOpenMidiController,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("midi_controller_button")
          ) {
            BadgedBox(
              badge = {
                if (isMidiLearnMode) {
                  Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                    Text(text = "L", style = MaterialTheme.typography.labelSmall)
                  }
                } else if (midiConnectedCount > 0) {
                  Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(text = "$midiConnectedCount", style = MaterialTheme.typography.labelSmall)
                  }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.Piano,
                contentDescription = stringResource(R.string.cd_midi_controller),
                tint = when {
                  isLocked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                  isMidiLearnMode -> MaterialTheme.colorScheme.secondary
                  midiConnectedCount > 0 -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.onSurface
                }
              )
            }
          }

          HoldToUnlockControl(
            isLocked = isLocked,
            onLockNow = onToggleLockMode,
            onUnlockHoldComplete = onToggleLockMode,
            onShortTapWhileLocked = onLockUnlockHint,
            tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("lock_mode_button")
          )

          IconButton(
            enabled = !isLocked,
            onClick = onOpenGridSettings,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("grid_settings_button")
          ) {
            Icon(
              imageVector = Icons.Filled.GridView,
              contentDescription = stringResource(R.string.cd_grid_settings),
              tint = if (isLocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(
            onClick = onToggleFullscreen,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("fullscreen_toggle_button")
          ) {
            Icon(
              imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
              contentDescription = stringResource(R.string.cd_toggle_fullscreen),
              tint = if (isFullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
          }

          IconButton(
            onClick = onOpenErrorLogs,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("error_logs_button")
          ) {
            BadgedBox(
              badge = {
                if (errorLogCount > 0) {
                  Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(
                      text = if (errorLogCount > 99) "99+" else "$errorLogCount",
                      style = MaterialTheme.typography.labelSmall
                    )
                  }
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.BugReport,
                contentDescription = stringResource(R.string.cd_error_logs),
                tint = if (errorLogCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          IconButton(
            onClick = onOpenMiuiGuide,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("miui_guide_button")
          ) {
            Icon(
              imageVector = Icons.Filled.BatteryChargingFull,
              contentDescription = stringResource(R.string.cd_miui_help),
              tint = MaterialTheme.colorScheme.secondary
            )
          }
        }
      }

      AnimatedVisibility(visible = isSearchVisible) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text("Cari pad berdasarkan nama, tag, atau format...") },
          singleLine = true,
          leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("search_pads_input")
        )
      }

      // Live Segmented Play Mode Switcher (Icon + crisp short label)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(42.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceContainer)
          .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        PlayMode.entries.forEach { mode ->
          val selected = mode == playMode
          val targetBg = if (selected) {
            when (mode) {
              PlayMode.SINGLE -> MaterialTheme.colorScheme.primary
              PlayMode.MULTI -> MaterialTheme.colorScheme.tertiary
              PlayMode.CONTINUOUS -> MaterialTheme.colorScheme.secondary
            }
          } else {
            Color.Transparent
          }
          val chipBg by animateColorAsState(targetValue = targetBg, label = "play_mode_bg")
          val chipTint = if (selected) {
            when (mode) {
              PlayMode.SINGLE -> MaterialTheme.colorScheme.onPrimary
              PlayMode.MULTI -> MaterialTheme.colorScheme.onTertiary
              PlayMode.CONTINUOUS -> MaterialTheme.colorScheme.onSecondary
            }
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          }
          val icon = when (mode) {
            PlayMode.SINGLE -> Icons.Filled.PlayArrow
            PlayMode.MULTI -> Icons.Filled.Layers
            PlayMode.CONTINUOUS -> Icons.AutoMirrored.Filled.PlaylistPlay
          }
          val shortLabel = when (mode) {
            PlayMode.SINGLE -> "SINGLE"
            PlayMode.MULTI -> "MULTI"
            PlayMode.CONTINUOUS -> "CONT"
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .height(36.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(chipBg)
              .testTag("play_mode_${mode.name.lowercase()}")
              .clickable(enabled = !isLocked) { onPlayModeSelected(mode) },
            contentAlignment = Alignment.Center
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              if (selected) {
                Box(
                  modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(chipTint)
                )
              }
              Icon(
                imageVector = icon,
                contentDescription = mode.label,
                tint = chipTint,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontSize = 10.sp,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                color = chipTint,
                maxLines = 1
              )
            }
          }
        }
      }
    }
  }
}

/**
 * 1-tap to lock stage layout; requires continuous 2-second press & hold to unlock,
 * drawing a circular progress ring around the lock icon during the hold.
 */
@Composable
fun HoldToUnlockControl(
  isLocked: Boolean,
  onLockNow: () -> Unit,
  onUnlockHoldComplete: () -> Unit,
  onShortTapWhileLocked: () -> Unit,
  tint: Color,
  modifier: Modifier = Modifier,
  holdDurationMs: Long = 2000L
) {
  var holdProgress by remember { mutableFloatStateOf(0f) }
  val scope = rememberCoroutineScope()
  val cd = if (isLocked) stringResource(R.string.cd_hold_to_unlock) else stringResource(R.string.cd_lock_mode)

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .size(48.dp)
      .clip(CircleShape)
      .semantics { contentDescription = cd }
      .pointerInput(isLocked) {
        detectTapGestures(
          onPress = {
            if (!isLocked) {
              val released = tryAwaitRelease()
              if (released) onLockNow()
              return@detectTapGestures
            }
            var unlocked = false
            val job = scope.launch {
              val stepMs = 30L
              val totalSteps = (holdDurationMs / stepMs).coerceAtLeast(1L)
              for (step in 1..totalSteps) {
                delay(stepMs)
                holdProgress = (step.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
              }
              unlocked = true
              holdProgress = 0f
              onUnlockHoldComplete()
            }
            tryAwaitRelease()
            job.cancel()
            if (!unlocked) {
              if (holdProgress < 0.95f) {
                onShortTapWhileLocked()
              }
              holdProgress = 0f
            }
          }
        )
      },
    contentAlignment = Alignment.Center
  ) {
    if (holdProgress > 0.02f) {
      CircularProgressIndicator(
        progress = { holdProgress },
        modifier = Modifier.size(36.dp),
        strokeWidth = 2.5.dp,
        color = tint,
        trackColor = tint.copy(alpha = 0.2f)
      )
    }
    Icon(
      imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(20.dp)
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TopControlBarPreview() {
  SoundPadProTheme(themeMode = ThemeMode.DARK) {
    TopControlBar(
      activeProfileName = "Live Set A",
      folderName = "Stage Show Tracks",
      padCount = 16,
      isScanning = false,
      newlyDetectedCount = 2,
      playMode = PlayMode.MULTI,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      isSearchVisible = false,
      searchQuery = "",
      isFullscreen = false,
      isCrossfadeEnabled = true,
      isLocked = false,
      midiConnectedCount = 1,
      errorLogCount = 1,
      onPickFolderClick = {},
      onRescanClick = {},
      onToggleSearch = {},
      onSearchQueryChange = {},
      onPlayModeSelected = {},
      onOpenProfiles = {},
      onOpenGridSettings = {},
      onToggleCrossfade = {},
      onToggleFullscreen = {},
      onOpenMiuiGuide = {}
    )
  }
}
