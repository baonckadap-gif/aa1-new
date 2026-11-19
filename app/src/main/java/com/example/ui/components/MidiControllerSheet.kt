package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiDeviceSummary
import com.example.domain.model.MidiLearnTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.SoundPad
import com.example.midi.BleMidiCandidate
import com.example.midi.MidiMessageParser
import com.example.ui.BankPageUi
import com.example.ui.theme.SoundPadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidiControllerSheet(
  midiDevices: List<MidiDeviceSummary>,
  discoveredBleDevices: List<BleMidiCandidate>,
  isScanningBle: Boolean,
  lastActivityLabel: String?,
  pads: List<SoundPad>,
  ccMappings: List<MidiCcMapping>,
  isMidiLearnMode: Boolean,
  midiLearnTarget: MidiLearnTarget?,
  onToggleMidiLearnMode: () -> Unit,
  onArmPadLearn: (SoundPad) -> Unit,
  onClearPadNote: (String) -> Unit,
  onArmCcLearn: (MidiCcTarget) -> Unit,
  onClearCcMapping: (MidiCcTarget) -> Unit,
  onCancelLearn: () -> Unit,
  onRefreshDevices: () -> Unit,
  onStartBleScan: () -> Unit,
  onStopBleScan: () -> Unit,
  onConnectBleDevice: (String) -> Unit,
  bankPages: List<BankPageUi> = emptyList(),
  pcMappings: List<MidiProgramBankMapping> = emptyList(),
  onArmBankPcLearn: (Int, String) -> Unit = { _, _ -> },
  onClearBankPcMapping: (Int) -> Unit = {},
  focusModeMidiNote: Int? = null,
  onArmFocusModeLearn: () -> Unit = {},
  onClearFocusModeNote: () -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.Piano,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
            }
            Column {
              Text(
                text = "MIDI Controller",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "USB OTG & Bluetooth MIDI + MIDI Learn",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .minimumInteractiveComponentSize()
              .testTag("close_midi_sheet_button")
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close MIDI controller sheet")
          }
        }
      }

      // Live Learn Mode & Activity
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = 1.dp,
            color = if (isMidiLearnMode || midiLearnTarget != null) {
              MaterialTheme.colorScheme.secondary
            } else {
              MaterialTheme.colorScheme.outlineVariant
            }
          )
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Board MIDI Learn Mode",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Enable then tap any pad on board, then press a MIDI key",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = isMidiLearnMode,
                onCheckedChange = { onToggleMidiLearnMode() },
                modifier = Modifier.testTag("midi_learn_mode_toggle")
              )
            }

            if (midiLearnTarget != null) {
              val learnText = when (midiLearnTarget) {
                is MidiLearnTarget.PadNote -> "Waiting MIDI Note On for \"${midiLearnTarget.padName}\""
                is MidiLearnTarget.CcControl -> "Waiting MIDI CC fader/knob for \"${midiLearnTarget.target.label}\""
                is MidiLearnTarget.ProgramChangeBank -> "Waiting MIDI Program Change for Bank \"${midiLearnTarget.bankLabel}\""
                is MidiLearnTarget.SequencerPlaylistNote -> "Waiting MIDI Note On for Playlist \"${midiLearnTarget.playlistName}\""
                MidiLearnTarget.FocusModeNote -> "Waiting MIDI Note On untuk \"Toggle Fokus Mode\""
              }
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Sensors,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.secondary,
                      modifier = Modifier.size(18.dp)
                    )
                    Text(
                      text = learnText,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                  }
                  IconButton(
                    onClick = onCancelLearn,
                    modifier = Modifier
                      .size(36.dp)
                      .testTag("midi_cancel_learn_button")
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Close,
                      contentDescription = "Cancel MIDI Learn",
                      tint = MaterialTheme.colorScheme.onSecondaryContainer,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surface
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                      if (lastActivityLabel != null) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline
                    )
                )
                Text(
                  text = lastActivityLabel ?: "No MIDI signal received yet",
                  style = MaterialTheme.typography.labelSmall,
                  color = if (lastActivityLabel != null) {
                    MaterialTheme.colorScheme.primary
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

      // Connected Devices & BLE Scanner
      item {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Connected Devices (${midiDevices.size})",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "USB OTG auto-connects · Tap Bluetooth to scan BLE MIDI",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedIconButton(
                  onClick = onRefreshDevices,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("midi_refresh_button")
                ) {
                  Icon(Icons.Filled.Refresh, contentDescription = "Refresh MIDI devices")
                }

                if (isScanningBle) {
                  FilledIconButton(
                    onClick = onStopBleScan,
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("midi_ble_scan_button"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                      containerColor = MaterialTheme.colorScheme.secondary,
                      contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                  ) {
                    Icon(Icons.Filled.StopCircle, contentDescription = "Stop BLE MIDI scan")
                  }
                } else {
                  FilledTonalIconButton(
                    onClick = onStartBleScan,
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("midi_ble_scan_button")
                  ) {
                    Icon(Icons.Filled.BluetoothSearching, contentDescription = "Scan Bluetooth MIDI devices")
                  }
                }
              }
            }

            if (isScanningBle) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                  text = "Scanning nearby Bluetooth MIDI controllers...",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }

            if (midiDevices.isEmpty()) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                )
                Text(
                  text = "No USB/Bluetooth MIDI input ports connected",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            } else {
              midiDevices.forEach { device ->
                val statusColor = if (device.isConnected) {
                  SoundPadTheme.colors.ramEmerald
                } else {
                  MaterialTheme.colorScheme.error
                }
                val latencyBadge = if (device.typeLabel.contains("Bluetooth", ignoreCase = true) ||
                  device.typeLabel.contains("BLE", ignoreCase = true)
                ) {
                  "BLE ~18ms"
                } else {
                  "USB ~5ms"
                }
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(9.dp)
                      .clip(CircleShape)
                      .background(statusColor)
                  )
                  Icon(
                    imageVector = if (device.typeLabel == "Bluetooth") Icons.Filled.Bluetooth else Icons.Filled.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = device.name,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurface,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${device.typeLabel} · ${if (device.isConnected) "Connected" else "Disconnected"}",
                      style = MaterialTheme.typography.labelSmall,
                      color = statusColor
                    )
                  }
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                  ) {
                    Text(
                      text = latencyBadge,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }

            if (discoveredBleDevices.isNotEmpty()) {
              Text(
                text = "Discovered BLE MIDI",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              discoveredBleDevices.forEach { ble ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = ble.name,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = ble.address,
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  FilledTonalIconButton(
                    onClick = { onConnectBleDevice(ble.address) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                  ) {
                    Icon(Icons.Filled.Link, contentDescription = "Connect BLE MIDI device ${ble.name}")
                  }
                }
              }
            }
          }
        }
      }

      // MIDI CC Controls Mapping
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "CC Fader & Knob Mappings",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      items(MidiCcTarget.entries, key = { it.name }) { target ->
        val existing = ccMappings.firstOrNull { it.target == target }
        val isArmed = (midiLearnTarget as? MidiLearnTarget.CcControl)?.target == target

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = 1.dp,
            color = if (isArmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = target.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = when {
                  isArmed -> "Move MIDI fader or knob..."
                  existing != null -> "CC #${existing.ccNumber} · Ch ${if (existing.channel < 0) "All" else existing.channel + 1}"
                  else -> "Unassigned"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isArmed) {
                  MaterialTheme.colorScheme.secondary
                } else if (existing != null) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              if (existing != null) {
                IconButton(
                  onClick = { onClearCcMapping(target) },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_cc_${target.name.lowercase()}")
                ) {
                  Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = "Clear CC mapping for ${target.label}",
                    tint = MaterialTheme.colorScheme.error
                  )
                }
              }

              FilledTonalIconButton(
                onClick = { if (isArmed) onCancelLearn() else onArmCcLearn(target) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("learn_cc_${target.name.lowercase()}"),
                colors = if (isArmed) {
                  IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                  )
                } else {
                  IconButtonDefaults.filledTonalIconButtonColors()
                }
              ) {
                Icon(
                  imageVector = if (isArmed) Icons.Filled.Close else Icons.Filled.RadioButtonChecked,
                  contentDescription = if (isArmed) "Cancel CC Learn" else "Learn CC for ${target.label}"
                )
              }
            }
          }
        }
      }

      // MIDI Program Change -> Bank Switching
      if (bankPages.isNotEmpty()) {
        item {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Layers,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Column {
              Text(
                text = "Program Change -> Pindah Bank",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Default: PC #0 ke Bank 1, PC #1 ke Bank 2, dst. Atau ketuk Learn untuk custom.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        val distinctBanks = bankPages.distinctBy { it.bankKey }
        items(distinctBanks, key = { "pc_bank_${it.bankKey}" }) { bank ->
          val pagePosition = distinctBanks.indexOf(bank)
          val existing = pcMappings.firstOrNull { it.bankIndex == bank.bankKey }
          val isArmed = (midiLearnTarget as? MidiLearnTarget.ProgramChangeBank)?.bankIndex == bank.bankKey

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
              width = 1.dp,
              color = if (isArmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = bank.title,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = when {
                    isArmed -> "Kirim MIDI Program Change..."
                    existing != null -> "Custom PC #${existing.programNumber} · Ch ${if (existing.channel < 0) "All" else existing.channel + 1}"
                    else -> "Default PC #$pagePosition -> Bank ${pagePosition + 1}"
                  },
                  style = MaterialTheme.typography.labelSmall,
                  color = if (isArmed) {
                    MaterialTheme.colorScheme.secondary
                  } else if (existing != null) {
                    MaterialTheme.colorScheme.primary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  }
                )
              }

              Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existing != null) {
                  IconButton(
                    onClick = { onClearBankPcMapping(bank.bankKey) },
                    modifier = Modifier
                      .minimumInteractiveComponentSize()
                      .testTag("clear_pc_bank_${bank.bankKey}")
                  ) {
                    Icon(
                      imageVector = Icons.Filled.LinkOff,
                      contentDescription = "Clear Program Change mapping for ${bank.title}",
                      tint = MaterialTheme.colorScheme.error
                    )
                  }
                }

                FilledTonalIconButton(
                  onClick = {
                    if (isArmed) onCancelLearn() else onArmBankPcLearn(bank.bankKey, bank.title)
                  },
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("learn_pc_bank_${bank.bankKey}"),
                  colors = if (isArmed) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                      containerColor = MaterialTheme.colorScheme.secondary,
                      contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                  } else {
                    IconButtonDefaults.filledTonalIconButtonColors()
                  }
                ) {
                  Icon(
                    imageVector = if (isArmed) Icons.Filled.Close else Icons.Filled.RadioButtonChecked,
                    contentDescription = if (isArmed) "Cancel Program Change Learn" else "Learn Program Change for ${bank.title}"
                  )
                }
              }
            }
          }
        }
      }

      // Focus Mode MIDI Note Trigger
      item(key = "focus_mode_midi_note") {
        val isArmed = midiLearnTarget == MidiLearnTarget.FocusModeNote
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = 1.dp,
            color = if (isArmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Toggle Fokus Mode (MIDI Note)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = when {
                  isArmed -> "Tekan tuts / pad MIDI untuk toggle Mode Fokus..."
                  focusModeMidiNote != null -> "Note ${MidiMessageParser.formatNoteName(focusModeMidiNote)}"
                  else -> "Unassigned"
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                  isArmed -> MaterialTheme.colorScheme.secondary
                  focusModeMidiNote != null -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              if (focusModeMidiNote != null) {
                IconButton(
                  onClick = onClearFocusModeNote,
                  modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("clear_focus_mode_midi_note")
                ) {
                  Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = "Clear Focus Mode MIDI note",
                    tint = MaterialTheme.colorScheme.error
                  )
                }
              }

              FilledTonalIconButton(
                onClick = { if (isArmed) onCancelLearn() else onArmFocusModeLearn() },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("learn_focus_mode_midi_note"),
                colors = if (isArmed) {
                  IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                  )
                } else {
                  IconButtonDefaults.filledTonalIconButtonColors()
                }
              ) {
                Icon(
                  imageVector = if (isArmed) Icons.Filled.Close else Icons.Filled.Sensors,
                  contentDescription = if (isArmed) "Cancel Focus Mode MIDI Learn" else "Learn MIDI note for Focus Mode"
                )
              }
            }
          }
        }
      }

      // Pad Note On Mappings
      item {
        Text(
          text = "Pad Note Mappings (${pads.count { it.midiNote != null }}/${pads.size})",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      items(pads, key = { it.id }) { pad ->
        val isArmed = (midiLearnTarget as? MidiLearnTarget.PadNote)?.padId == pad.id
        val padColor = pad.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary

        Surface(
          shape = RoundedCornerShape(10.dp),
          color = if (isArmed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = 1.dp,
            color = if (isArmed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(padColor)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = pad.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = when {
                  isArmed -> "Press a key on MIDI controller..."
                  pad.midiNote != null -> "Note ${MidiMessageParser.formatNoteName(pad.midiNote)}"
                  else -> "Unassigned"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isArmed) {
                  MaterialTheme.colorScheme.secondary
                } else if (pad.midiNote != null) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                }
              )
            }

            if (pad.midiNote != null) {
              IconButton(
                onClick = { onClearPadNote(pad.id) },
                modifier = Modifier
                  .minimumInteractiveComponentSize()
                  .testTag("clear_pad_midi_${pad.positionIndex}")
              ) {
                Icon(
                  imageVector = Icons.Filled.LinkOff,
                  contentDescription = "Clear MIDI note for ${pad.displayName}",
                  tint = MaterialTheme.colorScheme.error
                )
              }
            }

            FilledTonalIconButton(
              onClick = { if (isArmed) onCancelLearn() else onArmPadLearn(pad) },
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("learn_pad_midi_${pad.positionIndex}"),
              colors = if (isArmed) {
                IconButtonDefaults.filledTonalIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondary,
                  contentColor = MaterialTheme.colorScheme.onSecondary
                )
              } else {
                IconButtonDefaults.filledTonalIconButtonColors()
              }
            ) {
              Icon(
                imageVector = if (isArmed) Icons.Filled.Close else Icons.Filled.Sensors,
                contentDescription = if (isArmed) "Cancel MIDI Learn" else "Learn MIDI note for ${pad.displayName}"
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }
}
