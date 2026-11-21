package com.example.data.local.db

import android.content.Context
import android.util.Log
import com.example.domain.model.PadErrorType
import com.example.error.CrashLogManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository terpusat untuk semua mutasi Room terkait profil, pad, setting,
 * scan, restore, hapus, dan duplikat.
 *
 * Seluruh mutasi diserialisasikan oleh satu [Mutex] bersama agar tidak terjadi
 * race condition FOREIGN KEY (SQLiteConstraintException) antara UI/slider,
 * rescan folder, duplicate, delete, dan restore backup.
 */
class PadDatabaseRepository(
  context: Context?,
  private val dao: SoundPadDao,
  private val mutex: Mutex = sharedMutationMutex
) {
  private val appContext = context?.applicationContext

  companion object {
    private const val TAG = "PadDatabaseRepository"

    /** Mutex tunggal di layer repository untuk seluruh mutasi profil/pad/setting. */
    val sharedMutationMutex = Mutex()
  }

  suspend fun <T> withDbMutation(
    operation: String,
    fallback: T,
    padId: String? = null,
    block: suspend (SoundPadDao) -> T
  ): T {
    return mutex.withLock {
      try {
        block(dao)
      } catch (ce: CancellationException) {
        throw ce
      } catch (t: Throwable) {
        recordDaoFailure(operation = operation, throwable = t, padId = padId)
        fallback
      }
    }
  }

  suspend fun <T> withSafeDbCall(
    operation: String,
    fallback: T,
    padId: String? = null,
    block: suspend (SoundPadDao) -> T
  ): T {
    return try {
      block(dao)
    } catch (ce: CancellationException) {
      throw ce
    } catch (t: Throwable) {
      recordDaoFailure(operation = operation, throwable = t, padId = padId)
      fallback
    }
  }

  private suspend fun recordDaoFailure(
    operation: String,
    throwable: Throwable,
    padId: String? = null
  ) {
    val msg = "Operasi DB '$operation' gagal: ${throwable.localizedMessage ?: throwable.javaClass.simpleName}"
    Log.e(TAG, msg, throwable)
    if (appContext != null) {
      CrashLogManager.recordNonFatal(
        context = appContext,
        source = "PadDatabaseRepository.$operation",
        errorType = PadErrorType.PLAYBACK_ERROR,
        throwable = throwable,
        message = msg,
        suggestion = "Coba ulangi aksi atau pulihkan backup konfigurasi jika database mengalami konflik.",
        padId = padId
      )
    } else {
      CrashLogManager.recordNonFatalToDao(
        dao = dao,
        source = "PadDatabaseRepository.$operation",
        errorType = PadErrorType.PLAYBACK_ERROR,
        throwable = throwable,
        message = msg,
        suggestion = "Coba ulangi aksi atau pulihkan backup konfigurasi jika database mengalami konflik.",
        padId = padId
      )
    }
  }

  suspend fun upsertPadWithSetting(
    pad: SoundPadEntity,
    setting: PadSettingEntity,
    fallbackProfile: PadProfileEntity? = null
  ): Boolean {
    return withDbMutation(
      operation = "upsertPadWithSetting",
      fallback = false,
      padId = pad.id
    ) { dbDao ->
      val saved = if (fallbackProfile != null) {
        dbDao.upsertPadWithSettingEnsuringProfile(pad, setting, fallbackProfile)
      } else {
        dbDao.upsertPadWithSetting(pad, setting)
      }
      if (!saved) {
        Log.w(TAG, "upsertPadWithSetting diabaikan karena profil induk (${pad.profileId}) tidak ditemukan untuk pad ${pad.id}")
      }
      saved
    }
  }

  /**
   * Menyimpan [setting] untuk pad yang masih ada di DB di bawah Mutex + @Transaction.
   * Jika pad induk sudah dihapus (oleh delete/rescan/restore), operasi diabaikan diam-diam
   * (hanya log, tanpa crash dan tanpa menghidupkan ulang pad yang sudah dihapus).
   */
  suspend fun persistPadSettingSafely(setting: PadSettingEntity): Boolean {
    return withDbMutation(
      operation = "persistPadSettingSafely",
      fallback = false,
      padId = setting.padId
    ) { dbDao ->
      val updated = dbDao.persistPadSettingSafelyTransaction(setting)
      if (!updated) {
        Log.i(TAG, "Setting untuk pad yang sudah dihapus diabaikan diam-diam (padId=${setting.padId})")
      }
      updated
    }
  }

  suspend fun bulkDuplicateSelectedPads(
    profileId: String,
    clonedPads: List<SoundPadEntity>,
    clonedSettings: List<PadSettingEntity>
  ): Boolean {
    return withDbMutation(
      operation = "bulkDuplicateSelectedPads",
      fallback = false
    ) { dbDao ->
      val ok = dbDao.bulkDuplicatePadsTransaction(profileId, clonedPads, clonedSettings)
      if (!ok) {
        Log.w(TAG, "bulkDuplicateSelectedPads dibatalkan: profil $profileId tidak ada di DB")
      }
      ok
    }
  }

  suspend fun duplicatePadToBank(
    profileId: String,
    padsToPersist: List<SoundPadEntity>,
    clonedPadId: String,
    clonedSetting: PadSettingEntity,
    bankNamesJson: String? = null,
    bankOrderJson: String? = null,
    bankFolderUrisJson: String? = null
  ): Boolean {
    return withDbMutation(
      operation = "duplicatePadToBank",
      fallback = false,
      padId = clonedPadId
    ) { dbDao ->
      val ok = dbDao.duplicatePadToBankTransaction(
        profileId = profileId,
        padsToPersist = padsToPersist,
        clonedPadId = clonedPadId,
        clonedSetting = clonedSetting,
        bankNamesJson = bankNamesJson,
        bankOrderJson = bankOrderJson,
        bankFolderUrisJson = bankFolderUrisJson
      )
      if (!ok) {
        Log.w(TAG, "duplicatePadToBank dibatalkan: profil $profileId atau pad $clonedPadId tidak ada")
      }
      ok
    }
  }

  suspend fun insertClonedPadAndReindex(
    newPad: SoundPadEntity,
    newSetting: PadSettingEntity,
    reindexedPositions: List<Pair<String, Int>>
  ): Boolean {
    return withDbMutation(
      operation = "insertClonedPadAndReindex",
      fallback = false,
      padId = newPad.id
    ) { dbDao ->
      val ok = dbDao.insertClonedPadAndReindex(newPad, newSetting, reindexedPositions)
      if (!ok) {
        Log.w(TAG, "insertClonedPadAndReindex dibatalkan: profil ${newPad.profileId} belum ada")
      }
      ok
    }
  }

  suspend fun upsertPadsIfProfileExists(
    profileId: String,
    pads: List<SoundPadEntity>,
    bankNamesJson: String? = null,
    bankOrderJson: String? = null,
    bankFolderUrisJson: String? = null
  ): Boolean {
    return withDbMutation(
      operation = "upsertPadsIfProfileExists",
      fallback = false
    ) { dbDao ->
      if (!dbDao.profileExists(profileId)) {
        Log.w(TAG, "upsertPadsIfProfileExists diabaikan: profil $profileId tidak ditemukan")
        return@withDbMutation false
      }
      if (bankNamesJson != null && bankOrderJson != null && bankFolderUrisJson != null) {
        dbDao.updateProfileBankConfig(profileId, bankNamesJson, bankOrderJson, bankFolderUrisJson)
      }
      dbDao.upsertPadsIfProfileExists(profileId, pads)
    }
  }

  suspend fun saveCompleteProfile(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>,
    settings: List<PadSettingEntity>,
    ccMappings: List<MidiCcMappingEntity> = emptyList(),
    pcMappings: List<MidiPcBankMappingEntity> = emptyList(),
    replaceMidiMappings: Boolean = false
  ): Boolean {
    return withDbMutation(
      operation = "saveCompleteProfile",
      fallback = false
    ) { dbDao ->
      dbDao.saveCompleteProfile(profile, pads, settings)
      if (replaceMidiMappings) {
        dbDao.deleteMidiCcMappingsForProfile(profile.id)
        if (ccMappings.isNotEmpty()) {
          dbDao.upsertMidiCcMappings(ccMappings)
        }
        dbDao.deleteMidiPcMappingsForProfile(profile.id)
        pcMappings.forEach { pc -> dbDao.upsertMidiPcMapping(pc) }
      } else {
        if (ccMappings.isNotEmpty()) {
          dbDao.upsertMidiCcMappings(ccMappings)
        }
        pcMappings.forEach { pc -> dbDao.upsertMidiPcMapping(pc) }
      }
      true
    }
  }

  suspend fun syncProfilePadsPreservingSettings(
    profile: PadProfileEntity,
    pads: List<SoundPadEntity>
  ): Boolean {
    return withDbMutation(
      operation = "syncProfilePadsPreservingSettings",
      fallback = false
    ) { dbDao ->
      dbDao.syncProfilePadsPreservingSettings(profile, pads)
      true
    }
  }

  suspend fun syncProfilePadsIfExists(
    profileId: String,
    updatedAtMs: Long,
    pads: List<SoundPadEntity>
  ): Boolean {
    return withDbMutation(
      operation = "syncProfilePadsIfExists",
      fallback = false
    ) { dbDao ->
      val existing = dbDao.getProfileById(profileId) ?: return@withDbMutation false
      dbDao.syncProfilePadsPreservingSettings(existing.copy(updatedAtMs = updatedAtMs), pads)
      true
    }
  }

  suspend fun syncSnapshotWithSettings(
    profileId: String,
    fallbackProfile: PadProfileEntity,
    pads: List<SoundPadEntity>,
    settings: List<PadSettingEntity>
  ): Boolean {
    return withDbMutation(
      operation = "syncSnapshotWithSettings",
      fallback = false
    ) { dbDao ->
      val existing = dbDao.getProfileById(profileId)
      val targetProfile = existing?.copy(updatedAtMs = System.currentTimeMillis()) ?: fallbackProfile
      dbDao.syncSnapshotWithSettings(targetProfile, pads, settings)
      true
    }
  }

  suspend fun deletePadById(padId: String): Boolean {
    return withDbMutation(
      operation = "deletePadById",
      fallback = false,
      padId = padId
    ) { dbDao ->
      dbDao.deletePadById(padId)
      true
    }
  }

  suspend fun deletePadsByIds(padIds: List<String>): Boolean {
    return withDbMutation(
      operation = "deletePadsByIds",
      fallback = false
    ) { dbDao ->
      dbDao.deletePadsByIds(padIds)
      true
    }
  }

  suspend fun setActiveProfile(profileId: String): Boolean {
    return withDbMutation(
      operation = "setActiveProfile",
      fallback = false
    ) { dbDao ->
      if (!dbDao.profileExists(profileId)) return@withDbMutation false
      dbDao.setActiveProfile(profileId)
      true
    }
  }

  suspend fun deleteProfileIfNotLast(profileId: String): String? {
    return withDbMutation(
      operation = "deleteProfileIfNotLast",
      fallback = null
    ) { dbDao ->
      val all = dbDao.getAllProfilesOnce()
      if (all.size <= 1) return@withDbMutation null
      dbDao.deleteProfileById(profileId)
      dbDao.getAllProfilesOnce().firstOrNull()?.id
    }
  }

  suspend fun clearAllConfigTablesAndSeedDefault(defaultProfile: PadProfileEntity): Boolean {
    return withDbMutation(
      operation = "clearAllConfigTablesAndSeedDefault",
      fallback = false
    ) { dbDao ->
      dbDao.clearAllConfigTables()
      dbDao.upsertProfile(defaultProfile)
      true
    }
  }

  suspend fun getLayoutsForProfileOnce(profileId: String): List<PadLayoutEntity> {
    return withSafeDbCall(
      operation = "getLayoutsForProfileOnce",
      fallback = emptyList()
    ) { dbDao ->
      dbDao.getLayoutsForProfileOnce(profileId)
    }
  }

  suspend fun upsertLayoutSafely(layout: PadLayoutEntity): Boolean {
    return withDbMutation(
      operation = "upsertLayoutSafely",
      fallback = false
    ) { dbDao ->
      dbDao.upsertLayoutSafely(layout)
    }
  }

  suspend fun deleteLayoutById(layoutId: String): Boolean {
    return withDbMutation(
      operation = "deleteLayoutById",
      fallback = false
    ) { dbDao ->
      dbDao.deleteLayoutById(layoutId)
      true
    }
  }

  suspend fun applyLayoutPadsSafely(
    profileId: String,
    gridPreset: String,
    gridRows: Int,
    gridCols: Int,
    updatedPads: List<SoundPadEntity>
  ): Boolean {
    return withDbMutation(
      operation = "applyLayoutPadsSafely",
      fallback = false
    ) { dbDao ->
      val existing = dbDao.getProfileById(profileId) ?: return@withDbMutation false
      dbDao.upsertProfile(
        existing.copy(
          gridPreset = gridPreset,
          gridRows = gridRows,
          gridCols = gridCols,
          updatedAtMs = System.currentTimeMillis()
        )
      )
      val validPads = updatedPads.filter { it.profileId == profileId }
      if (validPads.isNotEmpty()) {
        dbDao.upsertPads(validPads)
      }
      true
    }
  }
}
