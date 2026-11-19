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

  @Query("SELECT * FROM sound_pads WHERE profileId = :profileId ORDER BY positionIndex ASC")
  fun observePadsForProfile(profileId: String): Flow<List<SoundPadEntity>>

  @Query("SELECT * FROM sound_pads WHERE profileId = :profileId ORDER BY positionIndex ASC")
  suspend fun getPadsForProfileOnce(profileId: String): List<SoundPadEntity>

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
  suspend fun upsertPadSettingSafely(setting: PadSettingEntity): Boolean {
    if (!padExists(setting.padId)) return false
    upsertPadSetting(setting)
    return true
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
  suspend fun syncProfilePadsPreservingSettings(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>
  ) {
    upsertProfile(profile)
    if (pads.isEmpty()) {
      deletePadsForProfile(profile.id)
    } else {
      upsertPads(pads)
      deletePadsNotIn(profile.id, pads.map { it.id })
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
    if (pads.isEmpty()) {
      deletePadsForProfile(profile.id)
    } else {
      upsertPads(pads)
      deletePadsNotIn(profile.id, pads.map { it.id })
      val padIdSet = pads.mapTo(HashSet(pads.size)) { it.id }
      val validSettings = settings.filter { it.padId in padIdSet }
      if (validSettings.isNotEmpty()) {
        upsertPadSettings(validSettings)
      }
    }
  }

  @Transaction
  suspend fun insertClonedPadAndReindex(
    newPad: SoundPadEntity,
    newSetting: PadSettingEntity,
    reindexedPositions: List<Pair<String, Int>>
  ) {
    upsertPad(newPad)
    upsertPadSetting(newSetting)
    updateAllPadPositions(reindexedPositions)
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

  @Transaction
  suspend fun clearAllConfigTables() {
    deleteAllScheduledTasks()
    deleteAllSequencerPlaylists()
    deleteAllMidiCcMappings()
    deleteAllMidiPcMappings()
    deleteAllProfiles()
    clearErrorLogs()
  }
}
