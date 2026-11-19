package com.example.ui

import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.SoundPad
import kotlin.math.abs

/**
 * Pure sparse-slot layout engine for SoundPad grids:
 * - Preserves empty slots (`EmptyPadWell`) between occupied pads
 * - Automatically paginates overflow slots (`(maxSlotIndex / perBank) + 1`)
 * - Supports Swap / Shift drag & drop, explicit "Move to slot", bulk offset move,
 *   compact/sort/restore-initial grid organization, and legacy compacted-index migration.
 */
object PadGridSlotManager {

  fun resolveBankSlotCollisions(pads: List<SoundPad>): List<SoundPad> {
    if (pads.isEmpty()) return emptyList()
    val occupiedByBank = mutableMapOf<Int, MutableSet<Int>>()
    return pads.map { pad ->
      val bank = pad.bankIndex.coerceAtLeast(0)
      val preferredSlot = pad.positionIndex.coerceAtLeast(0)
      val occupied = occupiedByBank.getOrPut(bank) { mutableSetOf() }
      val assignedSlot = if (occupied.add(preferredSlot)) {
        preferredSlot
      } else {
        val free = findFirstEmptySlot(occupied, startFrom = 0)
        occupied.add(free)
        free
      }
      if (assignedSlot == pad.positionIndex && bank == pad.bankIndex) {
        pad
      } else {
        pad.copy(bankIndex = bank, positionIndex = assignedSlot)
      }
    }
  }

  /**
   * Migrates legacy boards where `SafAudioScanner` or ViewModel compacted global `positionIndex`
   * across multiple banks (`0..N-1`) into per-bank 0-based `positionIndex` while preserving order.
   */
  fun migrateLegacyGlobalPositionsIfNeeded(pads: List<SoundPad>): List<SoundPad> {
    if (pads.isEmpty()) return emptyList()
    val distinctBanks = pads.map { it.bankIndex.coerceAtLeast(0) }.distinct()
    val needsRebase = if (distinctBanks.size > 1) {
      val sortedGlobalPositions = pads.map { it.positionIndex }.sorted()
      val isGlobalSequence = sortedGlobalPositions == (0 until pads.size).toList()
      val laterBanksStartPastZero = pads
        .filter { it.bankIndex > 0 }
        .groupBy { it.bankIndex }
        .values
        .all { group -> group.minOf { it.positionIndex } > 0 }
      isGlobalSequence && laterBanksStartPastZero
    } else {
      false
    }

    val rebased = if (needsRebase) {
      val perBankCounters = mutableMapOf<Int, Int>()
      val byBankSorted = pads.sortedWith(compareBy<SoundPad> { it.bankIndex }.thenBy { it.positionIndex })
      val rebasedById = HashMap<String, SoundPad>(pads.size)
      for (pad in byBankSorted) {
        val bIdx = pad.bankIndex.coerceAtLeast(0)
        val localSlot = perBankCounters.getOrDefault(bIdx, 0)
        perBankCounters[bIdx] = localSlot + 1
        rebasedById[pad.id] = pad.copy(bankIndex = bIdx, positionIndex = localSlot)
      }
      pads.map { rebasedById[it.id] ?: it }
    } else {
      pads
    }

    return resolveBankSlotCollisions(rebased)
  }

  fun promoteImplicitBanks(pads: List<SoundPad>, perBank: Int): List<SoundPad> {
    val safePerBank = perBank.coerceAtLeast(1)
    return pads.map { pad ->
      val rawPos = pad.positionIndex.coerceAtLeast(0)
      val targetBank = rawPos / safePerBank
      val targetPos = rawPos % safePerBank
      pad.copy(bankIndex = targetBank, positionIndex = targetPos)
    }
  }

  fun findFirstEmptySlot(occupiedSlots: Set<Int>, startFrom: Int = 0): Int {
    var candidate = startFrom.coerceAtLeast(0)
    while (candidate in occupiedSlots) {
      candidate++
    }
    return candidate
  }

  fun findFirstEmptySlot(padsInBank: Collection<SoundPad>, startFrom: Int = 0): Int {
    val occupied = padsInBank.mapTo(HashSet(padsInBank.size)) { it.positionIndex.coerceAtLeast(0) }
    return findFirstEmptySlot(occupied, startFrom)
  }

  fun findNearestEmptySlot(occupiedSlots: Set<Int>, nearSlot: Int): Int {
    val base = nearSlot.coerceAtLeast(0)
    if (base + 1 !in occupiedSlots) return base + 1
    var offset = 1
    while (true) {
      val forward = base + 1 + offset
      if (forward !in occupiedSlots) return forward
      val backward = base - offset
      if (backward >= 0 && backward !in occupiedSlots) return backward
      offset++
    }
  }

  fun findNearestEmptySlot(padsInBank: Collection<SoundPad>, nearSlot: Int): Int {
    val occupied = padsInBank.mapTo(HashSet(padsInBank.size)) { it.positionIndex.coerceAtLeast(0) }
    return findNearestEmptySlot(occupied, nearSlot)
  }

  fun buildBankPages(
    pads: List<SoundPad>,
    perBank: Int,
    activeBankNames: Map<Int, String>,
    activeBankOrder: List<Int>,
    activeBankFolderUris: Map<Int, String>,
    isBankExplicitMode: Boolean,
    isFiltered: Boolean
  ): List<BankPageUi> {
    val safePerBank = perBank.coerceAtLeast(1)
    if (!isBankExplicitMode) {
      if (pads.isEmpty()) return emptyList()
      val safePads = resolveBankSlotCollisions(pads)
      val maxSlot = safePads.maxOfOrNull { it.positionIndex.coerceAtLeast(0) } ?: 0
      val totalSubPages = (maxSlot / safePerBank) + 1
      val groupedSubPages = safePads.groupBy { it.positionIndex.coerceAtLeast(0) / safePerBank }
      return (0 until totalSubPages).mapNotNull { subIdx ->
        val subPads = groupedSubPages[subIdx].orEmpty().sortedBy { it.positionIndex }
        if (isFiltered && subPads.isEmpty()) {
          null
        } else {
          val offset = subIdx * safePerBank
          val slotMap = subPads.associateBy { (it.positionIndex - offset).coerceIn(0, safePerBank - 1) }
          val title = activeBankNames[subIdx]?.takeIf { it.isNotBlank() } ?: "B${subIdx + 1}"
          BankPageUi(
            bankKey = subIdx,
            title = title,
            pads = subPads,
            slotMap = slotMap,
            canRescanSubfolder = false,
            subPageIndex = subIdx,
            totalSubPages = totalSubPages,
            slotOffset = offset,
            targetPadBankIndex = 0
          )
        }
      }
    }

    val safePads = resolveBankSlotCollisions(pads)
    val grouped = safePads.groupBy { it.bankIndex }
    val allKeys = buildList {
      activeBankOrder.forEach { if (it !in this) add(it) }
      (grouped.keys + activeBankFolderUris.keys + activeBankNames.keys).sorted().forEach {
        if (it !in this) add(it)
      }
    }.filter { key ->
      if (isFiltered) {
        grouped[key]?.isNotEmpty() == true
      } else {
        grouped[key]?.isNotEmpty() == true || activeBankFolderUris.containsKey(key) || key in activeBankOrder
      }
    }

    val pages = mutableListOf<BankPageUi>()
    allKeys.forEach { key ->
      val padsInBank = grouped[key].orEmpty()
      val baseTitle = activeBankNames[key]?.takeIf { it.isNotBlank() }
        ?: padsInBank.firstOrNull { it.subfolderName.isNotBlank() }?.subfolderName
        ?: "B${key + 1}"
      val canRescan = activeBankFolderUris.containsKey(key)
      if (padsInBank.isEmpty()) {
        pages.add(
          BankPageUi(
            bankKey = key,
            title = baseTitle,
            pads = emptyList(),
            slotMap = emptyMap(),
            canRescanSubfolder = canRescan,
            subPageIndex = 0,
            totalSubPages = 1,
            slotOffset = 0,
            targetPadBankIndex = key
          )
        )
      } else {
        val maxSlot = padsInBank.maxOf { it.positionIndex.coerceAtLeast(0) }
        val totalSubPages = (maxSlot / safePerBank) + 1
        val subGrouped = padsInBank.groupBy { it.positionIndex.coerceAtLeast(0) / safePerBank }
        for (subIdx in 0 until totalSubPages) {
          val subPads = subGrouped[subIdx].orEmpty().sortedBy { it.positionIndex }
          if (isFiltered && subPads.isEmpty()) continue
          val offset = subIdx * safePerBank
          val slotMap = subPads.associateBy { (it.positionIndex - offset).coerceIn(0, safePerBank - 1) }
          val pageTitle = if (totalSubPages > 1) "$baseTitle (${subIdx + 1})" else baseTitle
          pages.add(
            BankPageUi(
              bankKey = key,
              title = pageTitle,
              pads = subPads,
              slotMap = slotMap,
              canRescanSubfolder = canRescan,
              subPageIndex = subIdx,
              totalSubPages = totalSubPages,
              slotOffset = offset,
              targetPadBankIndex = key
            )
          )
        }
      }
    }
    return pages
  }

  fun movePadToSlot(
    pads: List<SoundPad>,
    padId: String,
    targetBankIndex: Int,
    targetSlotIndex: Int,
    targetSubfolderName: String?,
    conflictMode: DragDropConflictMode
  ): List<SoundPad> {
    val movingPad = pads.firstOrNull { it.id == padId } ?: return pads
    val safeTargetBank = targetBankIndex.coerceAtLeast(0)
    val safeTargetSlot = targetSlotIndex.coerceAtLeast(0)
    if (movingPad.bankIndex == safeTargetBank && movingPad.positionIndex == safeTargetSlot) {
      return pads
    }

    val resolvedSubName = targetSubfolderName ?: movingPad.subfolderName
    val occupant = pads.firstOrNull {
      it.id != padId && it.bankIndex == safeTargetBank && it.positionIndex == safeTargetSlot
    }

    if (occupant == null) {
      return pads.map { p ->
        if (p.id == padId) {
          p.copy(bankIndex = safeTargetBank, positionIndex = safeTargetSlot, subfolderName = resolvedSubName)
        } else {
          p
        }
      }
    }

    if (conflictMode == DragDropConflictMode.SWAP) {
      return pads.map { p ->
        when (p.id) {
          padId -> p.copy(
            bankIndex = safeTargetBank,
            positionIndex = safeTargetSlot,
            subfolderName = resolvedSubName
          )
          occupant.id -> p.copy(
            bankIndex = movingPad.bankIndex,
            positionIndex = movingPad.positionIndex,
            subfolderName = movingPad.subfolderName
          )
          else -> p
        }
      }
    }

    // DragDropConflictMode.SHIFT
    val occupiedInTargetBank = pads
      .filter { it.id != padId && it.bankIndex == safeTargetBank }
      .associateBy { it.positionIndex }

    val oldSlot = movingPad.positionIndex
    if (movingPad.bankIndex == safeTargetBank && safeTargetSlot > oldSlot) {
      // Check if contiguous range (oldSlot + 1)..safeTargetSlot has all slots filled -> classic left-shift by -1
      val allOccupiedBetween = ((oldSlot + 1)..safeTargetSlot).all { it in occupiedInTargetBank }
      if (allOccupiedBetween) {
        val shiftedIds = ((oldSlot + 1)..safeTargetSlot).mapNotNull { occupiedInTargetBank[it]?.id }.toSet()
        return pads.map { p ->
          when {
            p.id == padId -> p.copy(
              bankIndex = safeTargetBank,
              positionIndex = safeTargetSlot,
              subfolderName = resolvedSubName
            )
            p.id in shiftedIds -> p.copy(positionIndex = p.positionIndex - 1)
            else -> p
          }
        }
      }
    }

    // Otherwise shift contiguous occupied chain starting at safeTargetSlot forward by +1
    val chainToShift = mutableListOf<SoundPad>()
    var cursor = safeTargetSlot
    while (true) {
      val atCursor = occupiedInTargetBank[cursor] ?: break
      chainToShift.add(atCursor)
      cursor++
    }
    val shiftMap = chainToShift.associate { it.id to (it.positionIndex + 1) }

    return pads.map { p ->
      when {
        p.id == padId -> p.copy(
          bankIndex = safeTargetBank,
          positionIndex = safeTargetSlot,
          subfolderName = resolvedSubName
        )
        p.id in shiftMap -> p.copy(positionIndex = shiftMap.getValue(p.id))
        else -> p
      }
    }
  }

  /**
   * Moves all [selectedIds] to [targetBankIndex] starting at [baseTargetSlotIndex],
   * preserving their relative position offsets (`pad.positionIndex - minSelectedPos`).
   */
  fun bulkMovePadsToSlot(
    pads: List<SoundPad>,
    selectedIds: Set<String>,
    targetBankIndex: Int,
    baseTargetSlotIndex: Int,
    targetSubfolderName: String?,
    conflictMode: DragDropConflictMode
  ): List<SoundPad> {
    val selected = pads
      .filter { it.id in selectedIds }
      .sortedWith(compareBy<SoundPad> { it.bankIndex }.thenBy { it.positionIndex })
    if (selected.isEmpty()) return pads
    if (selected.size == 1) {
      return movePadToSlot(
        pads = pads,
        padId = selected.first().id,
        targetBankIndex = targetBankIndex,
        targetSlotIndex = baseTargetSlotIndex,
        targetSubfolderName = targetSubfolderName,
        conflictMode = conflictMode
      )
    }

    val safeTargetBank = targetBankIndex.coerceAtLeast(0)
    val safeBaseSlot = baseTargetSlotIndex.coerceAtLeast(0)
    val minPos = selected.minOf { it.positionIndex.coerceAtLeast(0) }
    val resolvedSubName = targetSubfolderName ?: selected.first().subfolderName

    // Assign desired target slots preserving exact relative offset; bump only if multi-bank duplicates collide
    val reservedTargets = HashSet<Int>(selected.size)
    val desiredSlots = linkedMapOf<String, Int>()
    for (p in selected) {
      var target = safeBaseSlot + (p.positionIndex.coerceAtLeast(0) - minPos)
      while (target in reservedTargets) {
        target++
      }
      reservedTargets.add(target)
      desiredSlots[p.id] = target
    }

    val remainingPads = pads.filterNot { it.id in selectedIds }
    val otherBankPads = remainingPads.filter { it.bankIndex != safeTargetBank }
    val nonSelectedInTargetBank = remainingPads
      .filter { it.bankIndex == safeTargetBank }
      .associateBy { it.positionIndex }
      .toMutableMap()

    val updatedOtherBankPads = otherBankPads.toMutableList()

    if (conflictMode == DragDropConflictMode.SWAP) {
      // Collect vacated slots in the target bank (or original banks) for displaced occupants
      val vacatedSameBankSlots = ArrayDeque(
        selected.filter { it.bankIndex == safeTargetBank && it.positionIndex !in reservedTargets }
          .map { it.positionIndex }
          .sorted()
      )
      val occupiedAfterSelected = HashSet<Int>(reservedTargets).apply {
        addAll(nonSelectedInTargetBank.filterKeys { it !in reservedTargets }.keys)
      }
      val updatedTargetOccupants = mutableListOf<SoundPad>()
      for ((slot, occupant) in nonSelectedInTargetBank.toSortedMap()) {
        if (slot !in reservedTargets) {
          updatedTargetOccupants.add(occupant)
        } else {
          val swapSlot = if (vacatedSameBankSlots.isNotEmpty()) {
            vacatedSameBankSlots.removeFirst()
          } else {
            findFirstEmptySlot(occupiedAfterSelected, 0)
          }
          occupiedAfterSelected.add(swapSlot)
          updatedTargetOccupants.add(occupant.copy(positionIndex = swapSlot))
        }
      }
      val movedSelected = selected.map { p ->
        p.copy(
          bankIndex = safeTargetBank,
          positionIndex = desiredSlots.getValue(p.id),
          subfolderName = resolvedSubName
        )
      }
      val byId = (updatedOtherBankPads + updatedTargetOccupants + movedSelected).associateBy { it.id }
      return pads.map { byId.getValue(it.id) }
    } else {
      // SHIFT: push non-selected occupants in target bank forward past reserved targets
      val updatedOccupants = nonSelectedInTargetBank.values.sortedBy { it.positionIndex }.toMutableList()
      val fixedSlots = HashSet<Int>(reservedTargets)
      val finalOccupants = mutableListOf<SoundPad>()
      for (occ in updatedOccupants) {
        var pos = occ.positionIndex
        if (pos in reservedTargets || pos in fixedSlots) {
          while (pos in fixedSlots) {
            pos++
          }
        }
        fixedSlots.add(pos)
        finalOccupants.add(if (pos == occ.positionIndex) occ else occ.copy(positionIndex = pos))
      }
      val movedSelected = selected.map { p ->
        p.copy(
          bankIndex = safeTargetBank,
          positionIndex = desiredSlots.getValue(p.id),
          subfolderName = resolvedSubName
        )
      }
      val byId = (updatedOtherBankPads + finalOccupants + movedSelected).associateBy { it.id }
      return pads.map { byId.getValue(it.id) }
    }
  }

  fun compactBankSlots(pads: List<SoundPad>, targetBankIndex: Int?): List<SoundPad> {
    if (pads.isEmpty()) return emptyList()
    val inScope = if (targetBankIndex == null) {
      pads
    } else {
      pads.filter { it.bankIndex == targetBankIndex }
    }
    if (inScope.isEmpty()) return pads
    val compactedIds = inScope.sortedBy { it.positionIndex }
      .mapIndexed { idx, p -> p.id to idx }
      .toMap()
    return pads.map { p ->
      val newSlot = compactedIds[p.id]
      if (newSlot != null) p.copy(positionIndex = newSlot) else p
    }
  }

  fun sortBankAlphabetically(pads: List<SoundPad>, targetBankIndex: Int?): List<SoundPad> {
    if (pads.isEmpty()) return emptyList()
    val inScope = if (targetBankIndex == null) {
      pads
    } else {
      pads.filter { it.bankIndex == targetBankIndex }
    }
    if (inScope.isEmpty()) return pads
    val sortedIds = inScope.sortedWith(
      compareBy<SoundPad> { it.displayName.lowercase() }.thenBy { it.positionIndex }
    ).mapIndexed { idx, p -> p.id to idx }.toMap()
    return pads.map { p ->
      val newSlot = sortedIds[p.id]
      if (newSlot != null) p.copy(positionIndex = newSlot) else p
    }
  }

  fun restoreInitialBankPositions(
    pads: List<SoundPad>,
    initialSlotsByPadId: Map<String, Pair<Int, Int>>,
    targetBankIndex: Int? = null,
    bankNames: Map<Int, String> = emptyMap()
  ): List<SoundPad> {
    if (pads.isEmpty() || initialSlotsByPadId.isEmpty()) return pads
    val inScope = pads.map { pad ->
      val initial = initialSlotsByPadId[pad.id]
      if (initial != null && (
          targetBankIndex == null ||
            pad.bankIndex == targetBankIndex ||
            initial.first == targetBankIndex
          )
      ) {
        val restoredBank = initial.first.coerceAtLeast(0)
        val restoredSlot = initial.second.coerceAtLeast(0)
        val subName = bankNames[restoredBank] ?: pad.subfolderName
        pad.copy(bankIndex = restoredBank, positionIndex = restoredSlot, subfolderName = subName)
      } else {
        pad
      }
    }
    return resolveBankSlotCollisions(inScope)
  }

  fun resolveBankNamesAfterScan(
    hasSubfolderBanks: Boolean,
    scannedBankNames: Map<Int, String>,
    existingBankNames: Map<Int, String>,
    padBankKeys: Set<Int>,
    previousSubfolderKeys: Set<Int>
  ): Map<Int, String> {
    val manualBankKeys = existingBankNames.keys.filterTo(mutableSetOf()) { it !in previousSubfolderKeys }
    val activeBankKeys = scannedBankNames.keys + padBankKeys + manualBankKeys
    return if (hasSubfolderBanks) {
      val merged = scannedBankNames.toMutableMap()
      existingBankNames.forEach { (k, v) ->
        if (v.isNotBlank() && (k in scannedBankNames || k in manualBankKeys)) {
          merged[k] = v
        }
      }
      merged
    } else {
      existingBankNames.filterKeys { it in activeBankKeys }
    }
  }

  fun resolveBankOrderAfterScan(
    existingOrder: List<Int>,
    hasSubfolderBanks: Boolean,
    scannedBankKeys: Set<Int>,
    padBankKeys: Set<Int>,
    existingBankNames: Map<Int, String>,
    previousSubfolderKeys: Set<Int>
  ): List<Int> {
    val manualBankKeys = existingBankNames.keys.filterTo(mutableSetOf()) { it !in previousSubfolderKeys }
    val activeBankKeys = scannedBankKeys + padBankKeys + manualBankKeys
    if (!hasSubfolderBanks &&
      existingOrder.isEmpty() &&
      manualBankKeys.isEmpty() &&
      activeBankKeys.all { it == 0 }
    ) {
      return emptyList()
    }
    val kept = existingOrder.filter { it in activeBankKeys }
    val added = (activeBankKeys - kept.toSet()).sorted()
    return (kept + added).distinct()
  }

  fun distanceSquaredToSlot(a: Int, b: Int): Int = abs(a - b)
}
