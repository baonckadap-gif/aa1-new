package com.example

import com.example.data.backup.FullBackupSerializer
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PadProfile
import com.example.domain.model.SoundPad
import com.example.ui.PadGridSlotManager
import com.example.ui.SoundPadUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PadSlotGridUnitTest {

  private fun makePad(
    id: String,
    name: String = "Pad $id",
    bankIndex: Int = 0,
    positionIndex: Int = 0,
    subfolderName: String = ""
  ): SoundPad {
    return SoundPad(
      id = id,
      uriString = "content://test/$id",
      fileName = "$id.wav",
      displayName = name,
      extension = "WAV",
      durationMs = 1500L,
      sizeBytes = 4096L,
      positionIndex = positionIndex,
      bankIndex = bankIndex,
      subfolderName = subfolderName
    )
  }

  @Test
  fun `sparse slotMap preserves empty gaps and paginates overflow slots automatically`() {
    val pads = listOf(
      makePad("p0", bankIndex = 0, positionIndex = 0),
      makePad("p5", bankIndex = 0, positionIndex = 5),
      makePad("p40", bankIndex = 0, positionIndex = 40)
    )
    val state = SoundPadUiState(
      pads = pads,
      settings = GlobalSettings(gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4))
    )

    // maxSlot = 40, perBank = 16 -> ceil(41 / 16) = 3 sub-pages
    assertEquals(3, state.bankPages.size)
    assertEquals(0, state.bankPages[0].slotOffset)
    assertEquals("p0", state.bankPages[0].slotMap[0]?.id)
    assertNull(state.bankPages[0].slotMap[1])
    assertEquals("p5", state.bankPages[0].slotMap[5]?.id)
    assertEquals(16, state.bankPages[1].slotOffset)
    assertTrue(state.bankPages[1].slotMap.isEmpty())
    assertEquals("p40", state.bankPages[2].slotMap[40 - 32]?.id)
    assertEquals(32, state.bankPages[2].slotOffset)
  }

  @Test
  fun `explicit bank with overflow pads splits into multiple subpages`() {
    val pads = (0 until 20).map { i ->
      makePad("k_$i", bankIndex = 0, positionIndex = i, subfolderName = "Drums")
    } + listOf(
      makePad("fx_1", bankIndex = 1, positionIndex = 18, subfolderName = "FX")
    )

    val pages = PadGridSlotManager.buildBankPages(
      pads = pads,
      perBank = 16,
      activeBankNames = mapOf(0 to "Drums", 1 to "FX"),
      activeBankOrder = listOf(0, 1),
      activeBankFolderUris = emptyMap(),
      isBankExplicitMode = true,
      isFiltered = false
    )

    // Drums: 20 pads -> 2 subpages (0..15, 16..19); FX: maxSlot 18 -> 2 subpages (empty 0..15, fx_1 at local 2)
    assertEquals(4, pages.size)
    assertEquals("Drums (1)", pages[0].title)
    assertEquals(16, pages[0].pads.size)
    assertEquals("Drums (2)", pages[1].title)
    assertEquals(4, pages[1].pads.size)
    assertEquals("FX (1)", pages[2].title)
    assertTrue(pages[2].slotMap.isEmpty())
    assertEquals("FX (2)", pages[3].title)
    assertEquals("fx_1", pages[3].slotMap[2]?.id)
  }

  @Test
  fun `move pad to empty slot leaves source slot empty and keeps other pads intact`() {
    val pads = listOf(
      makePad("a", positionIndex = 0),
      makePad("b", positionIndex = 3),
      makePad("c", positionIndex = 7)
    )

    val moved = PadGridSlotManager.movePadToSlot(
      pads = pads,
      padId = "a",
      targetBankIndex = 0,
      targetSlotIndex = 12,
      targetSubfolderName = null,
      conflictMode = DragDropConflictMode.SWAP
    )

    val map = moved.associateBy { it.id }
    assertEquals(12, map.getValue("a").positionIndex)
    assertEquals(3, map.getValue("b").positionIndex)
    assertEquals(7, map.getValue("c").positionIndex)
  }

  @Test
  fun `move pad to occupied slot in SWAP mode exchanges slots across banks and pages`() {
    val pads = listOf(
      makePad("a", bankIndex = 0, positionIndex = 2, subfolderName = "Bank A"),
      makePad("b", bankIndex = 1, positionIndex = 19, subfolderName = "Bank B")
    )

    val swapped = PadGridSlotManager.movePadToSlot(
      pads = pads,
      padId = "a",
      targetBankIndex = 1,
      targetSlotIndex = 19,
      targetSubfolderName = "Bank B",
      conflictMode = DragDropConflictMode.SWAP
    )

    val map = swapped.associateBy { it.id }
    assertEquals(1, map.getValue("a").bankIndex)
    assertEquals(19, map.getValue("a").positionIndex)
    assertEquals("Bank B", map.getValue("a").subfolderName)

    assertEquals(0, map.getValue("b").bankIndex)
    assertEquals(2, map.getValue("b").positionIndex)
    assertEquals("Bank A", map.getValue("b").subfolderName)
  }

  @Test
  fun `move pad to occupied slot in SHIFT mode shifts contiguous neighbors cleanly`() {
    val initial = listOf(
      makePad("a", positionIndex = 0),
      makePad("b", positionIndex = 1),
      makePad("c", positionIndex = 2),
      makePad("d", positionIndex = 5)
    )
    val forward = PadGridSlotManager.movePadToSlot(
      pads = initial,
      padId = "a",
      targetBankIndex = 0,
      targetSlotIndex = 2,
      targetSubfolderName = null,
      conflictMode = DragDropConflictMode.SHIFT
    ).associateBy { it.id }

    assertEquals(0, forward.getValue("b").positionIndex)
    assertEquals(1, forward.getValue("c").positionIndex)
    assertEquals(2, forward.getValue("a").positionIndex)
    assertEquals(5, forward.getValue("d").positionIndex)

    val backward = PadGridSlotManager.movePadToSlot(
      pads = initial,
      padId = "c",
      targetBankIndex = 0,
      targetSlotIndex = 0,
      targetSubfolderName = null,
      conflictMode = DragDropConflictMode.SHIFT
    ).associateBy { it.id }

    assertEquals(0, backward.getValue("c").positionIndex)
    assertEquals(1, backward.getValue("a").positionIndex)
    assertEquals(2, backward.getValue("b").positionIndex)
    assertEquals(5, backward.getValue("d").positionIndex)
  }

  @Test
  fun `rescan simulation keeps sparse positions and inserts new pads into first empty slots`() {
    val existingPads = listOf(
      makePad("old_2", bankIndex = 0, positionIndex = 2),
      makePad("old_5", bankIndex = 0, positionIndex = 5)
    )
    val occupied = existingPads.mapTo(HashSet()) { it.positionIndex }

    val newSlot1 = PadGridSlotManager.findFirstEmptySlot(occupied)
    occupied.add(newSlot1)
    val newSlot2 = PadGridSlotManager.findFirstEmptySlot(occupied)
    occupied.add(newSlot2)
    val newSlot3 = PadGridSlotManager.findFirstEmptySlot(occupied)

    assertEquals(0, newSlot1)
    assertEquals(1, newSlot2)
    assertEquals(3, newSlot3)

    val nearestTo2 = PadGridSlotManager.findNearestEmptySlot(
      existingPads,
      nearSlot = 2
    )
    assertEquals(3, nearestTo2)
  }

  @Test
  fun `bulkMovePadsToSlot preserves relative offsets in SWAP and SHIFT modes`() {
    val pads = listOf(
      makePad("s1", positionIndex = 1),
      makePad("s2", positionIndex = 4),
      makePad("s3", positionIndex = 5),
      makePad("occ", positionIndex = 13),
      makePad("occNext", positionIndex = 14)
    )

    val shiftResult = PadGridSlotManager.bulkMovePadsToSlot(
      pads = pads,
      selectedIds = setOf("s1", "s2", "s3"),
      targetBankIndex = 0,
      baseTargetSlotIndex = 10,
      targetSubfolderName = null,
      conflictMode = DragDropConflictMode.SHIFT
    ).associateBy { it.id }

    assertEquals(10, shiftResult.getValue("s1").positionIndex)
    assertEquals(13, shiftResult.getValue("s2").positionIndex)
    assertEquals(14, shiftResult.getValue("s3").positionIndex)
    assertTrue(shiftResult.getValue("occ").positionIndex >= 15)
    assertTrue(shiftResult.getValue("occNext").positionIndex >= 15)
    assertEquals(5, shiftResult.values.map { it.positionIndex }.distinct().size)

    val swapResult = PadGridSlotManager.bulkMovePadsToSlot(
      pads = pads,
      selectedIds = setOf("s1", "s2", "s3"),
      targetBankIndex = 0,
      baseTargetSlotIndex = 10,
      targetSubfolderName = null,
      conflictMode = DragDropConflictMode.SWAP
    ).associateBy { it.id }

    assertEquals(10, swapResult.getValue("s1").positionIndex)
    assertEquals(13, swapResult.getValue("s2").positionIndex)
    assertEquals(14, swapResult.getValue("s3").positionIndex)
    assertTrue(swapResult.getValue("occ").positionIndex in setOf(1, 4, 5))
    assertTrue(swapResult.getValue("occNext").positionIndex in setOf(1, 4, 5))
  }

  @Test
  fun `compact sort and restore initial positions work per bank without losing pads`() {
    val initialPads = listOf(
      makePad("z", name = "Zeta", bankIndex = 0, positionIndex = 1),
      makePad("a", name = "Alpha", bankIndex = 0, positionIndex = 4),
      makePad("m", name = "Mid", bankIndex = 0, positionIndex = 8),
      makePad("b1", name = "Other Bank", bankIndex = 1, positionIndex = 6)
    )
    val snapshot = initialPads.associate { it.id to (it.bankIndex to it.positionIndex) }

    val compacted = PadGridSlotManager.compactBankSlots(initialPads, targetBankIndex = 0).associateBy { it.id }
    assertEquals(0, compacted.getValue("z").positionIndex)
    assertEquals(1, compacted.getValue("a").positionIndex)
    assertEquals(2, compacted.getValue("m").positionIndex)
    assertEquals(6, compacted.getValue("b1").positionIndex)

    val sorted = PadGridSlotManager.sortBankAlphabetically(initialPads, targetBankIndex = 0).associateBy { it.id }
    assertEquals(0, sorted.getValue("a").positionIndex)
    assertEquals(1, sorted.getValue("m").positionIndex)
    assertEquals(2, sorted.getValue("z").positionIndex)
    assertEquals(6, sorted.getValue("b1").positionIndex)

    val restored = PadGridSlotManager.restoreInitialBankPositions(
      pads = sorted.values.toList(),
      initialSlotsByPadId = snapshot,
      targetBankIndex = 0
    ).associateBy { it.id }
    assertEquals(1, restored.getValue("z").positionIndex)
    assertEquals(4, restored.getValue("a").positionIndex)
    assertEquals(8, restored.getValue("m").positionIndex)
    assertEquals(6, restored.getValue("b1").positionIndex)
  }

  @Test
  fun `migrateLegacyGlobalPositionsIfNeeded rebases multi-bank compacted slots cleanly`() {
    val legacyPads = listOf(
      makePad("b0_0", bankIndex = 0, positionIndex = 0),
      makePad("b0_1", bankIndex = 0, positionIndex = 1),
      makePad("b1_0", bankIndex = 1, positionIndex = 2),
      makePad("b1_1", bankIndex = 1, positionIndex = 3),
      makePad("b2_0", bankIndex = 2, positionIndex = 4)
    )

    val migrated = PadGridSlotManager.migrateLegacyGlobalPositionsIfNeeded(legacyPads).associateBy { it.id }
    assertEquals(5, migrated.size)
    assertEquals(0, migrated.getValue("b0_0").positionIndex)
    assertEquals(1, migrated.getValue("b0_1").positionIndex)
    assertEquals(0, migrated.getValue("b1_0").positionIndex)
    assertEquals(1, migrated.getValue("b1_1").positionIndex)
    assertEquals(0, migrated.getValue("b2_0").positionIndex)

    val sparsePads = listOf(
      makePad("s0", bankIndex = 0, positionIndex = 3),
      makePad("s1", bankIndex = 1, positionIndex = 7)
    )
    val preserved = PadGridSlotManager.migrateLegacyGlobalPositionsIfNeeded(sparsePads).associateBy { it.id }
    assertEquals(3, preserved.getValue("s0").positionIndex)
    assertEquals(7, preserved.getValue("s1").positionIndex)
  }

  @Test
  fun `ProfileJsonSerializer and FullBackupSerializer preserve sparse slots and DragDropConflictMode`() {
    val profile = PadProfile(
      id = "p_sparse",
      name = "Sparse Profile",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 3,
      createdAtMs = 1L,
      updatedAtMs = 1L,
      isActive = true
    )
    val pads = listOf(
      makePad("p0", bankIndex = 0, positionIndex = 0),
      makePad("p5", bankIndex = 0, positionIndex = 5),
      makePad("p19", bankIndex = 1, positionIndex = 19)
    )

    val json = ProfileJsonSerializer.exportToJson(profile, pads)
    val imported = ProfileJsonSerializer.importFromJson(json)
    val byName = imported.pads.associateBy { it.displayName }

    assertEquals(0, byName.getValue("Pad p0").positionIndex)
    assertEquals(5, byName.getValue("Pad p5").positionIndex)
    assertEquals(19, byName.getValue("Pad p19").positionIndex)
    assertEquals(1, byName.getValue("Pad p19").bankIndex)

    val settings = GlobalSettings(dragDropConflictMode = DragDropConflictMode.SHIFT)
    val fullJson = FullBackupSerializer.exportBackupJson(
      settings = settings,
      profiles = listOf(ExportedProfileBundle(profile = profile, pads = pads))
    )
    val decodedBackup = FullBackupSerializer.importBackupJson(fullJson)
    assertEquals(DragDropConflictMode.SHIFT, decodedBackup.globalSettings.dragDropConflictMode)
    val backupPads = decodedBackup.profiles.first().pads.associateBy { it.fileName }
    assertEquals(5, backupPads.getValue("p5.wav").positionIndex)
    assertEquals(19, backupPads.getValue("p19.wav").positionIndex)
  }
}
