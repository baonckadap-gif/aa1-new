package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.R
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PlayMode
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadProTheme
import com.example.ui.theme.SoundPadTheme
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
  isGlobalCueEnabled: Boolean = false,
  isLocked: Boolean = false,
  midiConnectedCount: Int = 0,
  isMidiLearnMode: Boolean = false,
  errorLogCount: Int = 0,
  isSequencerPlaying: Boolean = false,
  activeSchedulesCount: Int = 0,
  canRescanCurrentBank: Boolean = false,
  canReorderBanks: Boolean = false,
  onPickFolderClick: () -> Unit,
  onRescanClick: () -> Unit,
  onRescanCurrentBank: () -> Unit = onRescanClick,
  onOpenReorderBanks: () -> Unit = {},
  onOpenRenameCurrentBank: () -> Unit = {},
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
  val topBarTokens = SoundPadTheme.topBarTokens
  val extColors = SoundPadTheme.colors
  val spacing = SoundPadTheme.spacing
  var isOverflowExpanded by remember { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = spacing.xxs
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(
          horizontal = topBarTokens.horizontalPaddingDp,
          vertical = topBarTokens.verticalPaddingDp
        ),
      verticalArrangement = Arrangement.spacedBy(topBarTokens.contentSpacingDp)
    ) {
      // 1. Single-line title + dimmed session/grid info
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(topBarTokens.titleSpacingDp)
      ) {
        Text(
          text = folderName ?: stringResource(R.string.app_name),
          style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .alignByBaseline()
            .weight(1f, fill = false)
        )
        Text(
          text = "$activeProfileName · ${padCount}P · ${gridConfig.displayLabel}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.subtitleDimAlpha),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier
            .alignByBaseline()
            .weight(1f, fill = false)
        )
      }

      // 2. Grouped icon bar: Left (Folder, Bank) | Right (Search, Rescan, Cue, Menu ⋮)
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Left Group: Folder & Bank/Profiles
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(topBarTokens.iconGroupSpacingDp)
        ) {
          TopBarTooltipIconButton(
            tooltipText = stringResource(R.string.tooltip_folder),
            enabled = !isLocked,
            onClick = onPickFolderClick,
            modifier = Modifier.testTag("folder_picker_button")
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = stringResource(R.string.cd_select_folder),
              tint = if (isLocked) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.disabledAlpha)
              } else {
                MaterialTheme.colorScheme.primary
              },
              modifier = Modifier.size(topBarTokens.iconSizeDp)
            )
          }

          TopBarTooltipIconButton(
            tooltipText = stringResource(R.string.tooltip_bank_profiles),
            enabled = !isLocked,
            onClick = onOpenProfiles,
            modifier = Modifier.testTag("profiles_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Bookmarks,
              contentDescription = stringResource(R.string.cd_open_profiles),
              tint = if (isLocked) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.disabledAlpha)
              } else {
                MaterialTheme.colorScheme.onSurface
              },
              modifier = Modifier.size(topBarTokens.iconSizeDp)
            )
          }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Right Group: Search, Rescan, Cue, Menu (⋮)
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(topBarTokens.iconGroupSpacingDp)
        ) {
          TopBarTooltipIconButton(
            tooltipText = stringResource(R.string.tooltip_search),
            onClick = onToggleSearch,
            modifier = Modifier.testTag("search_toggle_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Search,
              contentDescription = stringResource(R.string.cd_search_pads),
              tint = if (isSearchVisible || searchQuery.isNotEmpty()) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurface
              },
              modifier = Modifier.size(topBarTokens.iconSizeDp)
            )
          }

          TopBarTooltipIconButton(
            tooltipText = stringResource(R.string.tooltip_rescan),
            enabled = folderName != null && !isScanning && !isLocked,
            onClick = onRescanClick,
            modifier = Modifier.testTag("rescan_button")
          ) {
            if (isScanning) {
              CircularProgressIndicator(
                modifier = Modifier.size(topBarTokens.spinnerSizeDp),
                strokeWidth = topBarTokens.spinnerStrokeDp,
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
                  tint = if (folderName != null && !isLocked) {
                    MaterialTheme.colorScheme.onSurface
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.disabledAlpha)
                  },
                  modifier = Modifier.size(topBarTokens.iconSizeDp)
                )
              }
            }
          }

          TopBarTooltipIconButton(
            tooltipText = stringResource(R.string.tooltip_cue),
            onClick = onOpenAudioOutput,
            modifier = Modifier.testTag("audio_output_button")
          ) {
            BadgedBox(
              badge = {
                if (isGlobalCueEnabled) {
                  Badge(
                    containerColor = extColors.cueMonitor,
                    modifier = Modifier.size(topBarTokens.badgeDotDp)
                  )
                }
              }
            ) {
              Icon(
                imageVector = Icons.Filled.Headphones,
                contentDescription = stringResource(R.string.cd_audio_output),
                tint = if (isGlobalCueEnabled) extColors.cueMonitor else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(topBarTokens.iconSizeDp)
              )
            }
          }

          Box {
            val hasOverflowAttention = errorLogCount > 0 || isLocked || isSequencerPlaying ||
              activeSchedulesCount > 0 || isMidiLearnMode
            TopBarTooltipIconButton(
              tooltipText = stringResource(R.string.tooltip_menu),
              onClick = { isOverflowExpanded = true },
              modifier = Modifier.testTag("overflow_menu_button")
            ) {
              BadgedBox(
                badge = {
                  if (errorLogCount > 0 || isLocked) {
                    Badge(
                      containerColor = MaterialTheme.colorScheme.error,
                      modifier = Modifier.size(topBarTokens.badgeDotDp)
                    )
                  } else if (hasOverflowAttention) {
                    Badge(
                      containerColor = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(topBarTokens.badgeDotDp)
                    )
                  }
                }
              ) {
                Icon(
                  imageVector = Icons.Filled.MoreVert,
                  contentDescription = stringResource(R.string.cd_overflow_menu),
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(topBarTokens.iconSizeDp)
                )
              }
            }

            DropdownMenu(
              expanded = isOverflowExpanded,
              onDismissRequest = { isOverflowExpanded = false },
              modifier = Modifier.testTag("top_overflow_dropdown")
            ) {
              // Bank & Folder actions (rarely used: Rescan Bank, Swap, Rename)
              if (canRescanCurrentBank) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.menu_rescan_bank)) },
                  leadingIcon = {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                  },
                  enabled = !isLocked,
                  onClick = {
                    isOverflowExpanded = false
                    onRescanCurrentBank()
                  },
                  modifier = Modifier.testTag("menu_rescan_bank_item")
                )
              } else if (folderName != null) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.menu_rescan_folder)) },
                  leadingIcon = {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                  },
                  enabled = !isLocked && !isScanning,
                  onClick = {
                    isOverflowExpanded = false
                    onRescanClick()
                  },
                  modifier = Modifier.testTag("menu_rescan_folder_item")
                )
              }

              if (canReorderBanks) {
                DropdownMenuItem(
                  text = { Text(stringResource(R.string.menu_swap_banks)) },
                  leadingIcon = {
                    Icon(Icons.Filled.SwapVert, contentDescription = null)
                  },
                  enabled = !isLocked,
                  onClick = {
                    isOverflowExpanded = false
                    onOpenReorderBanks()
                  },
                  modifier = Modifier.testTag("menu_swap_banks_item")
                )
              }

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_rename_bank)) },
                leadingIcon = {
                  Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onOpenRenameCurrentBank()
                },
                modifier = Modifier.testTag("menu_rename_bank_item")
              )

              HorizontalDivider()

              // Playlist / Sequencer, Scheduler, Crossfade, MIDI
              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_playlist_sequencer)) },
                leadingIcon = {
                  Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = if (isSequencerPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                },
                trailingIcon = {
                  if (isSequencerPlaying) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary)
                  }
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onOpenSequencer()
                },
                modifier = Modifier.testTag("sequencer_button")
              )

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_scheduler)) },
                leadingIcon = {
                  Icon(
                    Icons.Filled.Alarm,
                    contentDescription = null,
                    tint = if (activeSchedulesCount > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                  )
                },
                trailingIcon = {
                  if (activeSchedulesCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                      Text(text = "$activeSchedulesCount", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onOpenScheduler()
                },
                modifier = Modifier.testTag("scheduler_button")
              )

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_crossfade)) },
                leadingIcon = {
                  Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    contentDescription = null,
                    tint = if (isCrossfadeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                },
                trailingIcon = {
                  Text(
                    text = if (isCrossfadeEnabled) "ON" else "OFF",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCrossfadeEnabled) extColors.playModeActiveGreen else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onToggleCrossfade()
                },
                modifier = Modifier.testTag("crossfade_toggle_button")
              )

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_midi)) },
                leadingIcon = {
                  Icon(
                    Icons.Filled.Piano,
                    contentDescription = null,
                    tint = when {
                      isMidiLearnMode -> MaterialTheme.colorScheme.secondary
                      midiConnectedCount > 0 -> MaterialTheme.colorScheme.primary
                      else -> MaterialTheme.colorScheme.onSurface
                    }
                  )
                },
                trailingIcon = {
                  if (isMidiLearnMode) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondary) {
                      Text(text = "L", style = MaterialTheme.typography.labelSmall)
                    }
                  } else if (midiConnectedCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                      Text(text = "$midiConnectedCount", style = MaterialTheme.typography.labelSmall)
                    }
                  }
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onOpenMidiController()
                },
                modifier = Modifier.testTag("midi_controller_button")
              )

              HorizontalDivider()

              // Grid, Stage Lock, Fullscreen, Error Logs, MIUI Guide
              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_grid_settings)) },
                leadingIcon = {
                  Icon(Icons.Filled.GridView, contentDescription = null)
                },
                enabled = !isLocked,
                onClick = {
                  isOverflowExpanded = false
                  onOpenGridSettings()
                },
                modifier = Modifier.testTag("grid_settings_button")
              )

              DropdownMenuItem(
                text = {
                  Text(
                    stringResource(
                      if (isLocked) R.string.menu_stage_unlock_hint else R.string.menu_stage_lock
                    )
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    tint = if (isLocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                  )
                },
                onClick = {
                  isOverflowExpanded = false
                  if (isLocked) onLockUnlockHint() else onToggleLockMode()
                },
                modifier = Modifier.testTag("lock_mode_button")
              )

              DropdownMenuItem(
                text = {
                  Text(
                    stringResource(
                      if (isFullscreen) R.string.menu_exit_fullscreen else R.string.menu_fullscreen
                    )
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = null,
                    tint = if (isFullscreen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                  )
                },
                onClick = {
                  isOverflowExpanded = false
                  onToggleFullscreen()
                },
                modifier = Modifier.testTag("fullscreen_toggle_button")
              )

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_error_logs)) },
                leadingIcon = {
                  Icon(
                    Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = if (errorLogCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                  )
                },
                trailingIcon = {
                  if (errorLogCount > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                      Text(
                        text = if (errorLogCount > 99) "99+" else "$errorLogCount",
                        style = MaterialTheme.typography.labelSmall
                      )
                    }
                  }
                },
                onClick = {
                  isOverflowExpanded = false
                  onOpenErrorLogs()
                },
                modifier = Modifier.testTag("error_logs_button")
              )

              DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_miui_guide)) },
                leadingIcon = {
                  Icon(
                    Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                  )
                },
                onClick = {
                  isOverflowExpanded = false
                  onOpenMiuiGuide()
                },
                modifier = Modifier.testTag("miui_guide_button")
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = isSearchVisible) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = onSearchQueryChange,
          placeholder = { Text(stringResource(R.string.search_pads_placeholder)) },
          singleLine = true,
          leadingIcon = {
            Icon(
              Icons.Filled.Search,
              contentDescription = null,
              modifier = Modifier.size(topBarTokens.spinnerSizeDp)
            )
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(
                  Icons.Filled.Close,
                  contentDescription = stringResource(R.string.cd_clear_search),
                  modifier = Modifier.size(topBarTokens.spinnerSizeDp)
                )
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("search_pads_input")
        )
      }

      // 3. Play Mode Switcher (Material 3 SingleChoiceSegmentedButtonRow, 40.dp height, green active only)
      SingleChoiceSegmentedButtonRow(
        modifier = Modifier
          .fillMaxWidth()
          .height(topBarTokens.playModeHeightDp)
      ) {
        PlayMode.entries.forEachIndexed { index, mode ->
          val selected = mode == playMode
          val icon = when (mode) {
            PlayMode.SINGLE -> Icons.Filled.PlayArrow
            PlayMode.MULTI -> Icons.Filled.Layers
            PlayMode.CONTINUOUS -> Icons.AutoMirrored.Filled.PlaylistPlay
          }
          val shortLabel = when (mode) {
            PlayMode.SINGLE -> stringResource(R.string.play_mode_single_short)
            PlayMode.MULTI -> stringResource(R.string.play_mode_multi_short)
            PlayMode.CONTINUOUS -> stringResource(R.string.play_mode_cont_short)
          }

          SegmentedButton(
            selected = selected,
            onClick = { onPlayModeSelected(mode) },
            enabled = !isLocked,
            shape = SegmentedButtonDefaults.itemShape(
              index = index,
              count = PlayMode.entries.size
            ),
            colors = SegmentedButtonDefaults.colors(
              activeContainerColor = extColors.playModeActiveGreen,
              activeContentColor = extColors.onPlayModeActiveGreen,
              activeBorderColor = extColors.playModeActiveGreen,
              inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer,
              inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
              inactiveBorderColor = MaterialTheme.colorScheme.outlineVariant,
              disabledActiveContainerColor = extColors.playModeActiveGreen.copy(alpha = topBarTokens.disabledAlpha),
              disabledActiveContentColor = extColors.onPlayModeActiveGreen.copy(alpha = topBarTokens.disabledAlpha),
              disabledInactiveContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = topBarTokens.disabledAlpha),
              disabledInactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.disabledAlpha)
            ),
            icon = {
              Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(topBarTokens.playModeIconDp)
              )
            },
            label = {
              Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            },
            modifier = Modifier
              .height(topBarTokens.playModeHeightDp)
              .testTag("play_mode_${mode.name.lowercase()}")
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarTooltipIconButton(
  tooltipText: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit
) {
  val tooltipState = rememberTooltipState()
  TooltipBox(
    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
    tooltip = {
      PlainTooltip(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
      ) {
        Text(
          text = tooltipText,
          style = MaterialTheme.typography.labelSmall
        )
      }
    },
    state = tooltipState
  ) {
    IconButton(
      onClick = onClick,
      enabled = enabled,
      modifier = modifier.minimumInteractiveComponentSize(),
      content = content
    )
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
  val spacing = SoundPadTheme.spacing
  val topBarTokens = SoundPadTheme.topBarTokens
  val cd = if (isLocked) stringResource(R.string.cd_hold_to_unlock) else stringResource(R.string.cd_lock_mode)

  Box(
    modifier = modifier
      .minimumInteractiveComponentSize()
      .size(spacing.minTouchTarget)
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
        modifier = Modifier.size(topBarTokens.bankAddButtonDp),
        strokeWidth = topBarTokens.spinnerStrokeDp,
        color = tint,
        trackColor = tint.copy(alpha = 0.2f)
      )
    }
    Icon(
      imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
      contentDescription = null,
      tint = tint,
      modifier = Modifier.size(topBarTokens.iconSizeDp)
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TopControlBarPreview() {
  SoundPadProTheme(themeMode = ThemeMode.DARK) {
    TopControlBar(
      activeProfileName = "Default Session",
      folderName = "sample",
      padCount = 5,
      isScanning = false,
      newlyDetectedCount = 2,
      playMode = PlayMode.SINGLE,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      isSearchVisible = false,
      searchQuery = "",
      isFullscreen = false,
      isCrossfadeEnabled = true,
      isGlobalCueEnabled = true,
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
