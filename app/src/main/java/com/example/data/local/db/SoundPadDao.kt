package com.example.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundPadDao {
  @Query("SELECT * FROM pad_profiles ORDER BY updatedAtMs DESC")
  fun observeProfiles(): Flow<List<PadProfileEntity>>

  @Query("SELECT * FROM pad_profiles ORDER BY updatedAtMs DESC")
  suspend fun getAllProfilesOnce(): List<PadProfileEntity>

  @Query("SELECT * FROM pad_profiles WHERE id = :profileId LIMIT 1")
  suspend fun getProfileById(profileId: String): PadProfileEntity?

  @Upsert
  suspend fun upsertProfile(profile: PadProfileEntity)

  @Query("UPDATE pad_profiles SET isActive = 0")
  suspend fun clearActiveFlags()

  @Query("UPDATE pad_profiles SET isActive = 1 WHERE id = :profileId")
  suspend fun markProfileActive(profileId: String)

  @Transaction
  suspend fun setActiveProfile(profileId: String) {
    clearActiveFlags()
    markProfileActive(profileId)
  }

  @Query("UPDATE pad_profiles SET focusModeMidiNote = :midiNote, updatedAtMs = :updatedAtMs WHERE id = :profileId")
  suspend fun updateProfileFocusModeMidiNote(
    profileId: String,
    midiNote: Int?,
    updatedAtMs: Long = System.currentTimeMillis()
  )

  @Query("DELETE FROM pad_profiles WHERE id = :profileId")
  suspend fun deleteProfileById(profileId: String)

  @Query("SELECT EXISTS(SELECT 1 FROM pad_profiles WHERE id = :profileId)")
  suspend fun profileExists(profileId: String): Boolean

  @Query("SELECT * FROM sound_pads WHERE profileId = :profileId ORDER BY positionIndex ASC")
  fun observePadsForProfile(profileId: String): Flow<List<SoundPadEntity>>

  @Query("SELECT * FROM sound_pads WHERE profileId = :profileId ORDER BY positionIndex ASC")
  suspend fun getPadsForProfileOnce(profileId: String): List<SoundPadEntity>

  @Query("SELECT * FROM sound_pads WHERE id = :padId LIMIT 1")
  suspend fun getPadById(padId: String): SoundPadEntity?

  @Query("SELECT COUNT(*) FROM sound_pads WHERE profileId = :profileId")
  suspend fun getPadCountForProfile(profileId: String): Int

  @Query("SELECT EXISTS(SELECT 1 FROM sound_pads WHERE id = :padId)")
  suspend fun padExists(padId: String): Boolean

  @Query("SELECT id FROM sound_pads WHERE id IN (:padIds)")
  suspend fun findExistingPadIds(padIds: List<String>): List<String>

  @Upsert
  suspend fun upsertPad(pad: SoundPadEntity)

  @Upsert
  suspend fun upsertPads(pads: List<SoundPadEntity>)

  @Query("DELETE FROM sound_pads WHERE profileId = :profileId AND id NOT IN (:keepIds)")
  suspend fun deletePadsNotIn(profileId: String, keepIds: List<String>)

  @Query("DELETE FROM sound_pads WHERE profileId = :profileId")
  suspend fun deletePadsForProfile(profileId: String)

  @Query("DELETE FROM sound_pads WHERE id = :padId")
  suspend fun deletePadById(padId: String)

  @Transaction
  suspend fun deletePadsByIds(padIds: List<String>) {
    padIds.forEach { deletePadById(it) }
  }

  @Query(
    """
    UPDATE sound_pads SET
      displayName = :displayName,
      colorArgb = :colorArgb,
      glowColorArgb = :glowColorArgb,
      glowIntensity = :glowIntensity,
      shapeStyle = :shapeStyle,
      borderColorArgb = :borderColorArgb,
      borderWidthDp = :borderWidthDp,
      cornerRadiusDp = :cornerRadiusDp,
      textColorArgb = :textColorArgb,
      surfaceFinish = :surfaceFinish,
      badgeIcon = :badgeIcon,
      showSlotNumber = :showSlotNumber,
      showDurationLabel = :showDurationLabel
    WHERE id = :padId
    """
  )
  suspend fun updatePadAppearance(
    padId: String,
    displayName: String,
    colorArgb: Long?,
    glowColorArgb: Long?,
    glowIntensity: Float,
    shapeStyle: String?,
    borderColorArgb: Long?,
    borderWidthDp: Float,
    cornerRadiusDp: Float,
    textColorArgb: Long?,
    surfaceFinish: String,
    badgeIcon: String,
    showSlotNumber: Boolean,
    showDurationLabel: Boolean
  )

  @Query(
    """
    UPDATE sound_pads SET
      colorArgb = :colorArgb,
      glowColorArgb = :glowColorArgb,
      glowIntensity = :glowIntensity,
      shapeStyle = :shapeStyle,
      borderColorArgb = :borderColorArgb,
      borderWidthDp = :borderWidthDp,
      cornerRadiusDp = :cornerRadiusDp,
      textColorArgb = :textColorArgb,
      surfaceFinish = :surfaceFinish,
      showSlotNumber = :showSlotNumber,
      showDurationLabel = :showDurationLabel
    WHERE profileId = :profileId
    """
  )
  suspend fun applyVisualStyleToAllInProfile(
    profileId: String,
    colorArgb: Long?,
    glowColorArgb: Long?,
    glowIntensity: Float,
    shapeStyle: String?,
    borderColorArgb: Long?,
    borderWidthDp: Float,
    cornerRadiusDp: Float,
    textColorArgb: Long?,
    surfaceFinish: String,
    showSlotNumber: Boolean,
    showDurationLabel: Boolean
  )

  @Query("UPDATE sound_pads SET positionIndex = :positionIndex WHERE id = :padId")
  suspend fun updatePadPosition(padId: String, positionIndex: Int)

  @Transaction
  suspend fun updateAllPadPositions(updates: List<Pair<String, Int>>) {
    for ((padId, pos) in updates) {
      updatePadPosition(padId, pos)
    }
  }

  @Upsert
  suspend fun upsertPadSetting(setting: PadSettingEntity)

  @Upsert
  suspend fun upsertPadSettings(settings: List<PadSettingEntity>)

  @Transaction
  suspend fun upsertPadWithSetting(
    pad: SoundPadEntity,
    setting: PadSettingEntity
  ): Boolean {
    if (!profileExists(pad.profileId)) return false
    upsertPad(pad)
    if (!padExists(pad.id)) return false
    upsertPadSetting(if (setting.padId == pad.id) setting else setting.copy(padId = pad.id))
    return true
  }

  @Transaction
  suspend fun upsertPadWithSettingEnsuringProfile(
    pad: SoundPadEntity,
    setting: PadSettingEntity,
    fallbackProfile: PadProfileEntity
  ): Boolean {
    if (!profileExists(pad.profileId)) {
      if (fallbackProfile.id != pad.profileId) return false
      upsertProfile(fallbackProfile)
    }
    upsertPad(pad)
    if (!padExists(pad.id)) return false
    upsertPadSetting(if (setting.padId == pad.id) setting else setting.copy(padId = pad.id))
    return true
  }

  @Transaction
  suspend fun persistPadSettingSafelyTransaction(setting: PadSettingEntity): Boolean {
    val parentPad = getPadById(setting.padId) ?: return false
    if (!profileExists(parentPad.profileId)) return false
    upsertPadSetting(setting)
    return true
  }

  @Transaction
  suspend fun upsertPadSettingSafely(setting: PadSettingEntity): Boolean {
    return persistPadSettingSafelyTransaction(setting)
  }

  @Transaction
  suspend fun upsertPadSettingsSafely(settings: List<PadSettingEntity>) {
    if (settings.isEmpty()) return
    val existingIds = settings
      .map { it.padId }
      .distinct()
      .chunked(500)
      .flatMap { batch -> findExistingPadIds(batch) }
      .toHashSet()
    val validSettings = settings.filter { it.padId in existingIds }
    if (validSettings.isNotEmpty()) {
      upsertPadSettings(validSettings)
    }
  }

  @Query("SELECT * FROM pad_settings WHERE padId = :padId LIMIT 1")
  suspend fun getPadSetting(padId: String): PadSettingEntity?

  @Query(
    """
    SELECT ps.* FROM pad_settings ps
    INNER JOIN sound_pads sp ON ps.padId = sp.id
    WHERE sp.profileId = :profileId
    """
  )
  suspend fun getPadSettingsForProfileOnce(profileId: String): List<PadSettingEntity>

  @Transaction
  suspend fun upsertPadsIfProfileExists(
    profileId: String,
    pads: List<SoundPadEntity>
  ): Boolean {
    if (!profileExists(profileId)) return false
    val validPads = pads.filter { it.profileId == profileId }
    if (validPads.isNotEmpty()) {
      upsertPads(validPads)
    }
    return true
  }

  @Transaction
  suspend fun syncProfilePadsPreservingSettings(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>
  ) {
    upsertProfile(profile)
    val validPads = pads.filter { it.profileId == profile.id }
    if (validPads.isEmpty()) {
      deletePadsForProfile(profile.id)
    } else {
      upsertPads(validPads)
      deletePadsNotIn(profile.id, validPads.map { it.id })
    }
  }

  @Transaction
  suspend fun saveCompleteProfile(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>,
    settings: List<PadSettingEntity>
  ) {
    clearActiveFlags()
    upsertProfile(profile.copy(isActive = true))
    val validPads = pads.filter { it.profileId == profile.id }
    if (validPads.isEmpty()) {
      deletePadsForProfile(profile.id)
    } else {
      upsertPads(validPads)
      deletePadsNotIn(profile.id, validPads.map { it.id })
      val existingPadIds = validPads
        .map { it.id }
        .distinct()
        .chunked(500)
        .flatMap { batch -> findExistingPadIds(batch) }
        .toHashSet()
      val validSettings = settings.filter { it.padId in existingPadIds }
      if (validSettings.isNotEmpty()) {
        upsertPadSettings(validSettings)
      }
    }
  }

  @Transaction
  suspend fun syncSnapshotWithSettings(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>,
    settings: List<PadSettingEntity>
  ) {
    upsertProfile(profile)
    val validPads = pads.filter { it.profileId == profile.id }
    if (validPads.isEmpty()) {
      deletePadsForProfile(profile.id)
    } else {
      upsertPads(validPads)
      deletePadsNotIn(profile.id, validPads.map { it.id })
      val existingPadIds = validPads
        .map { it.id }
        .distinct()
        .chunked(500)
        .flatMap { batch -> findExistingPadIds(batch) }
        .toHashSet()
      val validSettings = settings.filter { it.padId in existingPadIds }
      if (validSettings.isNotEmpty()) {
        upsertPadSettings(validSettings)
      }
    }
  }

  @Transaction
  suspend fun bulkDuplicatePadsTransaction(
    profileId: String,
    clonedPads: List<SoundPadEntity>,
    clonedSettings: List<PadSettingEntity>
  ): Boolean {
    if (!profileExists(profileId)) return false
    val validPads = clonedPads.filter { it.profileId == profileId }
    if (validPads.isEmpty()) return true
    upsertPads(validPads)
    val existingIds = validPads
      .map { it.id }
      .distinct()
      .chunked(500)
      .flatMap { batch -> findExistingPadIds(batch) }
      .toHashSet()
    val validSettings = clonedSettings.filter { it.padId in existingIds }
    if (validSettings.isNotEmpty()) {
      upsertPadSettings(validSettings)
    }
    return true
  }

  @Transaction
  suspend fun duplicatePadToBankTransaction(
    profileId: String,
    padsToPersist: List<SoundPadEntity>,
    clonedPadId: String,
    clonedSetting: PadSettingEntity,
    bankNamesJson: String?,
    bankOrderJson: String?,
    bankFolderUrisJson: String?
  ): Boolean {
    if (!profileExists(profileId)) return false
    if (bankNamesJson != null && bankOrderJson != null && bankFolderUrisJson != null) {
      updateProfileBankConfig(
        profileId = profileId,
        bankNamesJson = bankNamesJson,
        bankOrderJson = bankOrderJson,
        bankFolderUrisJson = bankFolderUrisJson
      )
    }
    val validPads = padsToPersist.filter { it.profileId == profileId }
    if (validPads.isNotEmpty()) {
      upsertPads(validPads)
    }
    if (!padExists(clonedPadId)) return false
    upsertPadSetting(if (clonedSetting.padId == clonedPadId) clonedSetting else clonedSetting.copy(padId = clonedPadId))
    return true
  }

  @Transaction
  suspend fun insertClonedPadAndReindex(
    newPad: SoundPadEntity,
    newSetting: PadSettingEntity,
    reindexedPositions: List<Pair<String, Int>>
  ): Boolean {
    if (!profileExists(newPad.profileId)) return false
    upsertPad(newPad)
    if (!padExists(newPad.id)) return false
    upsertPadSetting(if (newSetting.padId == newPad.id) newSetting else newSetting.copy(padId = newPad.id))
    for ((padId, pos) in reindexedPositions) {
      if (padExists(padId)) {
        updatePadPosition(padId, pos)
      }
    }
    return true
  }

  @Query("UPDATE sound_pads SET midiNote = :midiNote WHERE id = :padId")
  suspend fun updatePadMidiNote(padId: String, midiNote: Int?)

  @Query("SELECT * FROM midi_cc_mappings WHERE profileId = :profileId")
  fun observeMidiCcMappings(profileId: String): Flow<List<MidiCcMappingEntity>>

  @Query("SELECT * FROM midi_cc_mappings WHERE profileId = :profileId")
  suspend fun getMidiCcMappingsOnce(profileId: String): List<MidiCcMappingEntity>

  @Upsert
  suspend fun upsertMidiCcMapping(mapping: MidiCcMappingEntity)

  @Upsert
  suspend fun upsertMidiCcMappings(mappings: List<MidiCcMappingEntity>)

  @Query("DELETE FROM midi_cc_mappings WHERE profileId = :profileId AND target = :target")
  suspend fun deleteMidiCcMappingByTarget(profileId: String, target: String)

  @Query("DELETE FROM midi_cc_mappings WHERE profileId = :profileId")
  suspend fun deleteMidiCcMappingsForProfile(profileId: String)

  @Query("UPDATE pad_profiles SET bankNamesJson = :bankNamesJson WHERE id = :profileId")
  suspend fun updateProfileBankNames(profileId: String, bankNamesJson: String)

  @Query("UPDATE sound_pads SET isFavorite = :isFavorite, categoryTag = :categoryTag WHERE id = :padId")
  suspend fun updatePadFavoriteAndTag(padId: String, isFavorite: Boolean, categoryTag: String)

  @Query("UPDATE pad_settings SET normalizedGain = :normalizedGain WHERE padId = :padId")
  suspend fun updatePadNormalizedGain(padId: String, normalizedGain: Float)

  @Query("UPDATE sound_pads SET miniWaveformJson = :miniWaveformJson WHERE uriString = :uriString")
  suspend fun updatePadMiniWaveformByUri(uriString: String, miniWaveformJson: String)

  @Query("UPDATE sound_pads SET errorType = :errorType, errorMessage = :errorMessage WHERE id = :padId")
  suspend fun updatePadErrorState(padId: String, errorType: String?, errorMessage: String?)

  @Query("UPDATE sound_pads SET bankIndex = :bankIndex, subfolderName = :subfolderName WHERE id = :padId")
  suspend fun updatePadBankIndex(padId: String, bankIndex: Int, subfolderName: String)

  @Query(
    """
    UPDATE pad_profiles SET
      bankNamesJson = :bankNamesJson,
      bankOrderJson = :bankOrderJson,
      bankFolderUrisJson = :bankFolderUrisJson
    WHERE id = :profileId
    """
  )
  suspend fun updateProfileBankConfig(
    profileId: String,
    bankNamesJson: String,
    bankOrderJson: String,
    bankFolderUrisJson: String
  )

  @Query(
    """
    UPDATE pad_profiles SET
      masterEqEnabled = :enabled,
      masterEqPreset = :preset,
      masterEqBandsJson = :bandsJson
    WHERE id = :profileId
    """
  )
  suspend fun updateProfileMasterEq(
    profileId: String,
    enabled: Boolean,
    preset: String,
    bandsJson: String
  )

  @Query("SELECT * FROM error_logs ORDER BY timestampMs DESC LIMIT 300")
  fun observeErrorLogs(): Flow<List<ErrorLogEntity>>

  @Query("SELECT * FROM error_logs ORDER BY timestampMs DESC LIMIT 300")
  suspend fun getErrorLogsOnce(): List<ErrorLogEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertErrorLog(log: ErrorLogEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertErrorLogs(logs: List<ErrorLogEntity>)

  @Query("DELETE FROM error_logs WHERE id = :id")
  suspend fun deleteErrorLogById(id: String)

  @Query("DELETE FROM error_logs")
  suspend fun clearErrorLogs()

  // ---- Scheduler Tasks ----
  @Query("SELECT * FROM scheduled_tasks ORDER BY hour ASC, minute ASC, createdAtMs DESC")
  fun observeScheduledTasks(): Flow<List<ScheduledTaskEntity>>

  @Query("SELECT * FROM scheduled_tasks ORDER BY hour ASC, minute ASC")
  suspend fun getAllScheduledTasksOnce(): List<ScheduledTaskEntity>

  @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
  suspend fun getActiveScheduledTasksOnce(): List<ScheduledTaskEntity>

  @Query("SELECT * FROM scheduled_tasks WHERE id = :id LIMIT 1")
  suspend fun getScheduledTaskById(id: String): ScheduledTaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertScheduledTask(task: ScheduledTaskEntity)

  @Query("SELECT * FROM scheduler_id_counter WHERE id = 1 LIMIT 1")
  suspend fun getSchedulerIdCounterOnce(): SchedulerIdCounterEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSchedulerIdCounter(counter: SchedulerIdCounterEntity)

  @Transaction
  suspend fun allocateNextSchedulerRequestCode(): Int {
    val current = (getSchedulerIdCounterOnce()?.nextRequestCode ?: 100).coerceAtLeast(100)
    val next = current + 1
    upsertSchedulerIdCounter(SchedulerIdCounterEntity(id = 1, nextRequestCode = next))
    return current
  }

  @Transaction
  suspend fun upsertScheduledTaskAssigningRequestCode(task: ScheduledTaskEntity): ScheduledTaskEntity {
    val existing = getScheduledTaskById(task.id)
    val assignedCode = when {
      existing != null && existing.requestCode > 0 -> existing.requestCode
      task.requestCode > 0 -> task.requestCode
      else -> allocateNextSchedulerRequestCode()
    }
    val counter = getSchedulerIdCounterOnce()
    if (counter == null || counter.nextRequestCode <= assignedCode) {
      upsertSchedulerIdCounter(
        SchedulerIdCounterEntity(id = 1, nextRequestCode = (assignedCode + 1).coerceAtLeast(100))
      )
    }
    val finalized = task.copy(requestCode = assignedCode)
    upsertScheduledTask(finalized)
    return finalized
  }

  @Query("DELETE FROM scheduled_tasks WHERE id = :id")
  suspend fun deleteScheduledTaskById(id: String)

  // ---- Sequencer / Playlist Pad ----
  @Query("SELECT * FROM sequencer_playlists ORDER BY updatedAtMs DESC")
  fun observeSequencerPlaylists(): Flow<List<SequencerPlaylistEntity>>

  @Query("SELECT * FROM sequencer_playlists ORDER BY updatedAtMs DESC")
  suspend fun getAllSequencerPlaylistsOnce(): List<SequencerPlaylistEntity>

  @Query("SELECT * FROM sequencer_playlists WHERE id = :id LIMIT 1")
  suspend fun getSequencerPlaylistById(id: String): SequencerPlaylistEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSequencerPlaylist(playlist: SequencerPlaylistEntity)

  @Query("DELETE FROM sequencer_playlists WHERE id = :id")
  suspend fun deleteSequencerPlaylistById(id: String)

  // ---- MIDI Program Change -> Bank Mappings ----
  @Query("SELECT * FROM midi_pc_bank_mappings WHERE profileId = :profileId ORDER BY bankIndex ASC")
  fun observeMidiPcMappings(profileId: String): Flow<List<MidiPcBankMappingEntity>>

  @Query("SELECT * FROM midi_pc_bank_mappings WHERE profileId = :profileId ORDER BY bankIndex ASC")
  suspend fun getMidiPcMappingsOnce(profileId: String): List<MidiPcBankMappingEntity>

  @Upsert
  suspend fun upsertMidiPcMapping(mapping: MidiPcBankMappingEntity)

  @Query("DELETE FROM midi_pc_bank_mappings WHERE profileId = :profileId AND bankIndex = :bankIndex")
  suspend fun deleteMidiPcMappingByBank(profileId: String, bankIndex: Int)

  @Query("DELETE FROM midi_pc_bank_mappings WHERE profileId = :profileId")
  suspend fun deleteMidiPcMappingsForProfile(profileId: String)

  @Query("DELETE FROM pad_profiles")
  suspend fun deleteAllProfiles()

  @Query("DELETE FROM scheduled_tasks")
  suspend fun deleteAllScheduledTasks()

  @Query("DELETE FROM sequencer_playlists")
  suspend fun deleteAllSequencerPlaylists()

  @Query("DELETE FROM midi_cc_mappings")
  suspend fun deleteAllMidiCcMappings()

  @Query("DELETE FROM midi_pc_bank_mappings")
  suspend fun deleteAllMidiPcMappings()

  // ---- Multi-Layout Grid Configs ----
  @Query("SELECT * FROM pad_layouts WHERE profileId = :profileId ORDER BY isDefault DESC, updatedAtMs DESC")
  fun observeLayoutsForProfile(profileId: String): Flow<List<PadLayoutEntity>>

  @Query("SELECT * FROM pad_layouts WHERE profileId = :profileId ORDER BY isDefault DESC, updatedAtMs DESC")
  suspend fun getLayoutsForProfileOnce(profileId: String): List<PadLayoutEntity>

  @Query("SELECT * FROM pad_layouts ORDER BY updatedAtMs DESC")
  suspend fun getAllLayoutsOnce(): List<PadLayoutEntity>

  @Upsert
  suspend fun upsertLayout(layout: PadLayoutEntity)

  @Transaction
  suspend fun upsertLayoutSafely(layout: PadLayoutEntity): Boolean {
    if (!profileExists(layout.profileId)) return false
    upsertLayout(layout)
    return true
  }

  @Query("DELETE FROM pad_layouts WHERE id = :layoutId")
  suspend fun deleteLayoutById(layoutId: String)

  @Query("DELETE FROM pad_layouts WHERE profileId = :profileId")
  suspend fun deleteLayoutsForProfile(profileId: String)

  @Query("DELETE FROM pad_layouts")
  suspend fun deleteAllLayouts()

  @Transaction
  suspend fun clearAllConfigTables() {
    deleteAllScheduledTasks()
    deleteAllSequencerPlaylists()
    deleteAllMidiCcMappings()
    deleteAllMidiPcMappings()
    deleteAllLayouts()
    deleteAllProfiles()
    clearErrorLogs()
  }
}
