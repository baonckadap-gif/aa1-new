package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.SoundPad
import com.example.ui.BankPageUi
import com.example.ui.PadGridSlotManager

private const val MAX_SLOT_NUMBER = 999

@Composable
fun MovePadToSlotDialog(
  pad: SoundPad,
  allPads: List<SoundPad>,
  bankPages: List<BankPageUi>,
  isBankExplicitMode: Boolean,
  padsPerBank: Int,
  conflictMode: DragDropConflictMode,
  onConfirmMove: (targetBankIndex: Int, targetSlotIndex: Int) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages, isBankExplicitMode) {
    if (!isBankExplicitMode) {
      listOf(0 to (bankPages.firstOrNull()?.title?.takeIf { it.isNotBlank() } ?: "Board"))
    } else {
      bankPages.distinctBy { it.targetPadBankIndex }
        .map { page ->
          val cleanTitle = page.title.replace(Regex(" \\(\\d+\\)$"), "")
          page.targetPadBankIndex to cleanTitle
        }
        .ifEmpty { listOf(pad.bankIndex to "Bank ${pad.bankIndex + 1}") }
    }
  }

  var selectedBankIndex by remember(pad.id) { mutableIntStateOf(pad.bankIndex) }
  var slotText by remember(pad.id) { mutableStateOf((pad.positionIndex + 1).toString()) }

  val parsedSlotNumber = slotText.trim().toIntOrNull()
  val isValidSlot = parsedSlotNumber != null && parsedSlotNumber in 1..MAX_SLOT_NUMBER
  val targetSlotZeroBased = (parsedSlotNumber ?: 1) - 1

  val targetBankPads = remember(allPads, selectedBankIndex, pad.id) {
    if (selectedBankIndex == -1) emptyList()
    else allPads.filter { it.id != pad.id && it.bankIndex == selectedBankIndex }
  }
  val occupiedSlots = remember(targetBankPads) {
    targetBankPads.map { it.positionIndex.coerceAtLeast(0) }.toSet()
  }
  val suggestedEmptySlots = remember(occupiedSlots) {
    buildList {
      var cursor = 0
      while (size < 4 && cursor < MAX_SLOT_NUMBER) {
        if (cursor !in occupiedSlots) add(cursor)
        cursor++
      }
    }
  }
  val occupantAtTarget = remember(targetBankPads, targetSlotZeroBased, isValidSlot) {
    if (!isValidSlot) null
    else targetBankPads.firstOrNull { it.positionIndex == targetSlotZeroBased }
  }
  val safePerBank = padsPerBank.coerceAtLeast(1)
  val resultingSubPage = (targetSlotZeroBased / safePerBank) + 1

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.SwapHoriz,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = { Text(stringResource(R.string.move_slot_dialog_title)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Pad: \"${pad.displayName}\" (saat ini Slot ${pad.positionIndex + 1})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        if (isBankExplicitMode || distinctBanks.size > 1) {
          Text(
            text = "Bank Tujuan",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            distinctBanks.forEach { (bIdx, label) ->
              FilterChip(
                selected = selectedBankIndex == bIdx,
                onClick = {
                  selectedBankIndex = bIdx
                  if (bIdx != pad.bankIndex) {
                    val free = PadGridSlotManager.findFirstEmptySlot(
                      allPads.filter { it.bankIndex == bIdx }
                    )
                    slotText = (free + 1).toString()
                  }
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.testTag("move_slot_bank_chip_$bIdx")
              )
            }
            FilterChip(
              selected = selectedBankIndex == -1,
              onClick = {
                selectedBankIndex = -1
                slotText = "1"
              },
              leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
              label = { Text("Bank Baru", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
              modifier = Modifier.testTag("move_slot_new_bank_chip")
            )
          }
        }

        OutlinedTextField(
          value = slotText,
          onValueChange = { input ->
            if (input.length <= 4 && input.all { it.isDigit() }) {
              slotText = input
            }
          },
          label = { Text(stringResource(R.string.move_slot_input_label, MAX_SLOT_NUMBER)) },
          isError = slotText.isNotEmpty() && !isValidSlot,
          supportingText = {
            when {
              !isValidSlot -> Text(
                text = stringResource(R.string.move_slot_invalid_error, MAX_SLOT_NUMBER),
                color = MaterialTheme.colorScheme.error
              )
              occupantAtTarget != null -> Text(
                text = "Slot terisi \"${occupantAtTarget.displayName}\" → Mode ${conflictMode.label} (Hal. $resultingSubPage)",
                color = MaterialTheme.colorScheme.tertiary
              )
              else -> Text(
                text = "Slot kosong tersedia (Halaman $resultingSubPage)",
                color = MaterialTheme.colorScheme.primary
              )
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("move_slot_number_input")
        )

        if (suggestedEmptySlots.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = "Slot Kosong Terdekat:",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              suggestedEmptySlots.forEach { emptyZeroBased ->
                val oneBased = emptyZeroBased + 1
                Surface(
                  modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .testTag("move_slot_quick_empty_$oneBased")
                    .clickable { slotText = oneBased.toString() },
                  shape = RoundedCornerShape(8.dp),
                  color = if (parsedSlotNumber == oneBased) {
                    MaterialTheme.colorScheme.primaryContainer
                  } else {
                    MaterialTheme.colorScheme.surfaceContainer
                  },
                  border = BorderStroke(
                    1.dp,
                    if (parsedSlotNumber == oneBased) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                  )
                ) {
                  Text(
                    text = "#$oneBased",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        enabled = isValidSlot,
        onClick = {
          val zeroBased = (parsedSlotNumber ?: 1) - 1
          onConfirmMove(selectedBankIndex, zeroBased)
        },
        modifier = Modifier.testTag("confirm_move_slot_button")
      ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(" Pindah", maxLines = 1)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}

@Composable
fun BulkMoveSlotsDialog(
  selectedPads: List<SoundPad>,
  bankPages: List<BankPageUi>,
  isBankExplicitMode: Boolean,
  conflictMode: DragDropConflictMode,
  onConfirmBulkMove: (targetBankIndex: Int, baseTargetSlotIndex: Int) -> Unit,
  onDismiss: () -> Unit
) {
  val sortedSelected = remember(selectedPads) {
    selectedPads.sortedWith(compareBy<SoundPad> { it.bankIndex }.thenBy { it.positionIndex })
  }
  val distinctBanks = remember(bankPages, isBankExplicitMode) {
    if (!isBankExplicitMode) {
      listOf(0 to (bankPages.firstOrNull()?.title?.takeIf { it.isNotBlank() } ?: "Board"))
    } else {
      bankPages.distinctBy { it.targetPadBankIndex }
        .map { page ->
          val cleanTitle = page.title.replace(Regex(" \\(\\d+\\)$"), "")
          page.targetPadBankIndex to cleanTitle
        }
        .ifEmpty { listOf(0 to "Bank 1") }
    }
  }

  val initialBank = sortedSelected.firstOrNull()?.bankIndex ?: 0
  val minSelectedPos = remember(sortedSelected) {
    sortedSelected.minOfOrNull { it.positionIndex.coerceAtLeast(0) } ?: 0
  }

  var selectedBankIndex by remember { mutableIntStateOf(initialBank) }
  var startSlotText by remember { mutableStateOf((minSelectedPos + 1).toString()) }

  val parsedStart = startSlotText.trim().toIntOrNull()
  val isValidStart = parsedStart != null && parsedStart in 1..MAX_SLOT_NUMBER

  val previewTargets = remember(sortedSelected, parsedStart, isValidStart) {
    if (!isValidStart || sortedSelected.isEmpty()) {
      emptyList()
    } else {
      val baseZero = (parsedStart ?: 1) - 1
      val used = mutableSetOf<Int>()
      sortedSelected.map { pad ->
        var target = baseZero + (pad.positionIndex.coerceAtLeast(0) - minSelectedPos)
        while (target in used) target++
        used.add(target)
        pad.positionIndex + 1 to (target + 1)
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.SwapHoriz,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary
      )
    },
    title = { Text(stringResource(R.string.bulk_move_slot_dialog_title, sortedSelected.size)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Jarak relatif antar pad terpilih dipertahankan (Mode: ${conflictMode.label}).",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (isBankExplicitMode || distinctBanks.size > 1) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            distinctBanks.forEach { (bIdx, label) ->
              FilterChip(
                selected = selectedBankIndex == bIdx,
                onClick = { selectedBankIndex = bIdx },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.testTag("bulk_move_slot_bank_chip_$bIdx")
              )
            }
            FilterChip(
              selected = selectedBankIndex == -1,
              onClick = { selectedBankIndex = -1 },
              leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
              label = { Text("Bank Baru", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
              modifier = Modifier.testTag("bulk_move_slot_new_bank_chip")
            )
          }
        }

        OutlinedTextField(
          value = startSlotText,
          onValueChange = { input ->
            if (input.length <= 4 && input.all { it.isDigit() }) {
              startSlotText = input
            }
          },
          label = { Text("Mulai di Nomor Slot (1–$MAX_SLOT_NUMBER)") },
          isError = startSlotText.isNotEmpty() && !isValidStart,
          supportingText = {
            if (!isValidStart) {
              Text(
                text = stringResource(R.string.move_slot_invalid_error, MAX_SLOT_NUMBER),
                color = MaterialTheme.colorScheme.error
              )
            } else if (previewTargets.isNotEmpty()) {
              val previewStr = previewTargets.take(5).joinToString(", ") { (from, to) -> "#$from→#$to" } +
                if (previewTargets.size > 5) "…" else ""
              Text(text = "Pemetaan offset: $previewStr", color = MaterialTheme.colorScheme.primary)
            }
          },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("bulk_move_slot_number_input")
        )
      }
    },
    confirmButton = {
      Button(
        enabled = isValidStart && sortedSelected.isNotEmpty(),
        onClick = {
          val baseZero = (parsedStart ?: 1) - 1
          onConfirmBulkMove(selectedBankIndex, baseZero)
        },
        modifier = Modifier.testTag("confirm_bulk_move_slot_button")
      ) {
        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(" Pindahkan", maxLines = 1)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.action_cancel))
      }
    }
  )
}
