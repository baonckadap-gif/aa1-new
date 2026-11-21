package com.example.data.local.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.backup.BankBackupEntry
import com.example.data.backup.ConfigMetaJson
import com.example.data.backup.MidiMappingBackupBundle
import com.example.data.backup.PadBackupEntry
import com.example.data.backup.PersistentConfigBundle
import com.example.data.backup.PersistentConfigBundleSerializer
import com.example.data.backup.PersistentConfigSchema
import com.example.data.backup.ProfileBackupBundle
import com.example.data.backup.SchedulerBackupBundle
import com.example.data.backup.SequencerBackupBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlayMode
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import org.json.JSONArray
import java.io.File

/**
 * Exports an existing v1..v7 SQLite database into a PersistentConfig JSON backup *before* Room
 * executes fallbackToDestructiveMigrationFrom(1..7), and automatically restores the exported data
 * right after Room recreates the latest schema tables in onDestructiveMigration(db).
 */
object SoundPadPreMigrationBackupManager {

  private const val BACKUP_DIR_NAME = "migration_backups"
  private const val LATEST_BACKUP_FILE = "pre_destructive_latest.json"

  fun getLatestBackupFile(context: Context): File {
    val dir = File(context.filesDir, BACKUP_DIR_NAME)
    if (!dir.exists()) dir.mkdirs()
    return File(dir, LATEST_BACKUP_FILE)
  }

  /**
   * Inspects the on-disk SQLite file *before* Room.databaseBuilder().build() opens it.
   * Returns the backup File if a destructive migration backup was written, or null otherwise.
   */
  fun backupIfDestructiveMigrationNeeded(
    context: Context,
    dbName: String = SoundPadDatabase.DATABASE_NAME,
    minIncrementalVersion: Int = 8
  ): File? {
    val dbFile = context.getDatabasePath(dbName)
    if (!dbFile.exists() || dbFile.length() <= 0L) return null
    val backupFile = getLatestBackupFile(context)
    val version = readSqliteUserVersion(dbFile)
    if (version !in 1 until minIncrementalVersion) return null

    val exported = exportRawSqliteToPersistentConfigJson(dbFile, backupFile, fromDbVersion = version)
    if (!exported) return null

    val versionedCopy = File(
      backupFile.parentFile,
      "pre_destructive_v${version}_${System.currentTimeMillis()}.json"
    )
    runCatching { backupFile.copyTo(versionedCopy, overwrite = true) }
    return backupFile
  }

  fun readSqliteUserVersion(dbFile: File): Int {
    if (!dbFile.exists()) return 0
    return runCatching {
      SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
        sqlDb.rawQuery("PRAGMA user_version", null).use { cursor ->
          if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
      }
    }.getOrDefault(0)
  }

  fun exportRawSqliteToPersistentConfigJson(
    dbFile: File,
    targetBackupFile: File,
    fromDbVersion: Int = readSqliteUserVersion(dbFile)
  ): Boolean {
    if (!dbFile.exists()) return false
    return runCatching {
      SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sqlDb ->
        val tables = queryExistingTableNames(sqlDb)
        if ("pad_profiles" !in tables && "sound_pads" !in tables) {
          return false
        }

        val profiles = if ("pad_profiles" in tables) readLegacyProfiles(sqlDb) else emptyList()
        val settingsByPadId = if ("pad_settings" in tables) readLegacyPadSettings(sqlDb) else emptyMap()
        val padsByProfileId = if ("sound_pads" in tables) readLegacyPads(sqlDb, settingsByPadId) else emptyMap()
        val ccByProfileId = if ("midi_cc_mappings" in tables) readLegacyCcMappings(sqlDb) else emptyMap()
        val pcByProfileId = if ("midi_pc_bank_mappings" in tables) readLegacyPcMappings(sqlDb) else emptyMap()
        val tasks = if ("scheduled_tasks" in tables) readLegacyScheduledTasks(sqlDb) else emptyList()
        val playlists = if ("sequencer_playlists" in tables) readLegacySequencerPlaylists(sqlDb) else emptyList()

        val effectiveProfiles = if (profiles.isNotEmpty()) {
          profiles
        } else if (padsByProfileId.isNotEmpty()) {
          padsByProfileId.keys.mapIndexed { idx, pId ->
            PadProfile(
              id = pId,
              name = if (idx == 0) "Default Session" else "Profile ${idx + 1}",
              folderUri = null,
              gridConfig = GridConfig(),
              createdAtMs = System.currentTimeMillis(),
              updatedAtMs = System.currentTimeMillis(),
              isActive = idx == 0
            )
          }
        } else {
          emptyList()
        }

        val profileBundles = effectiveProfiles.map { profile ->
          val pads = padsByProfileId[profile.id].orEmpty()
          val orderedBanks = linkedSetOf<Int>().apply {
            addAll(profile.bankOrder)
            addAll(profile.bankNames.keys.sorted())
            addAll(profile.bankFolderUris.keys.sorted())
            pads.forEach { add(it.bankIndex) }
          }
          val banks = orderedBanks.mapIndexed { orderPos, bankIdx ->
            BankBackupEntry(
              bankIndex = bankIdx,
              name = profile.bankNames[bankIdx] ?: "B${bankIdx + 1}",
              folderUri = profile.bankFolderUris[bankIdx],
              orderPosition = orderPos
            )
          }
          ProfileBackupBundle(
            profile = profile.copy(padCount = pads.size),
            banks = banks,
            pads = pads.map { pad -> PadBackupEntry(pad = pad, slotIndex = pad.positionIndex) },
            ccMappings = ccByProfileId[profile.id].orEmpty(),
            pcMappings = pcByProfileId[profile.id].orEmpty()
          )
        }

        val bundle = PersistentConfigBundle(
          meta = ConfigMetaJson(
            schemaVersion = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
            profileCount = profileBundles.size,
            padCount = profileBundles.sumOf { it.pads.size },
            schedulerCount = tasks.size,
            sequencerCount = playlists.size,
            backupSource = "pre_destructive_db_v$fromDbVersion"
          ),
          profiles = profileBundles,
          midiMapping = MidiMappingBackupBundle(
            ccMappings = ccByProfileId.values.flatten(),
            pcMappings = pcByProfileId.values.flatten()
          ),
          scheduler = SchedulerBackupBundle(tasks = tasks),
          sequencer = SequencerBackupBundle(playlists = playlists)
        )

        val jsonText = PersistentConfigBundleSerializer.exportCombinedJson(bundle, includeWaveforms = true)
        targetBackupFile.parentFile?.mkdirs()
        targetBackupFile.writeText(jsonText, Charsets.UTF_8)
        true
      }
    }.getOrDefault(false)
  }

  /**
   * Restores the backup written immediately prior to Room's destructive reset into the newly
   * recreated v12 [SupportSQLiteDatabase]. Runs synchronously inside [RoomDatabase.Callback.onDestructiveMigration].
   */
  fun restoreLatestBackupIntoDatabase(
    context: Context,
    db: SupportSQLiteDatabase
  ): Int {
    val backupFile = getLatestBackupFile(context)
    if (!backupFile.exists() || backupFile.length() <= 0L) return 0
    val count = restoreJsonFileIntoSupportDb(backupFile, db)
    if (count > 0) {
      val archived = File(backupFile.parentFile, "pre_destructive_restored_${System.currentTimeMillis()}.json")
      runCatching { backupFile.renameTo(archived) }
    }
    return count
  }

  fun restoreJsonFileIntoSupportDb(
    backupFile: File,
    db: SupportSQLiteDatabase
  ): Int {
    if (!backupFile.exists()) return 0
    val rawJson = runCatching { backupFile.readText(Charsets.UTF_8) }.getOrNull() ?: return 0
    val parsed = runCatching { PersistentConfigBundleSerializer.importCombinedJson(rawJson) }.getOrNull()
      ?: return 0
    val bundle = parsed.bundle
    var restoredRows = 0

    db.beginTransaction()
    try {
      bundle.profiles.forEach { pBundle ->
        val profile = pBundle.profile
        val profileValues = ContentValues().apply {
          put("id", profile.id)
          put("name", profile.name)
          put("folderUri", profile.folderUri)
          put("gridPreset", profile.gridConfig.preset.name)
          put("gridRows", profile.gridConfig.rows)
          put("gridCols", profile.gridConfig.cols)
          put("bankNamesJson", ProfileJsonSerializer.encodeBankNamesString(profile.bankNames))
          put("bankOrderJson", ProfileJsonSerializer.encodeBankOrderString(profile.bankOrder))
          put("bankFolderUrisJson", ProfileJsonSerializer.encodeBankFolderUrisString(profile.bankFolderUris))
          put("masterEqEnabled", if (profile.masterEqEnabled) 1 else 0)
          put("masterEqPreset", profile.masterEqPreset.name)
          put("masterEqBandsJson", ProfileJsonSerializer.encodeEqBandsString(profile.masterEqBandsDb))
          put("createdAtMs", profile.createdAtMs)
          put("updatedAtMs", profile.updatedAtMs)
          put("isActive", if (profile.isActive) 1 else 0)
          profile.focusModeMidiNote?.let { put("focusModeMidiNote", it) } ?: putNull("focusModeMidiNote")
        }
        db.insert("pad_profiles", SQLiteDatabase.CONFLICT_REPLACE, profileValues)
        restoredRows++

        pBundle.pads.forEach { entry ->
          val pad = entry.pad
          val padValues = ContentValues().apply {
            put("id", pad.id)
            put("profileId", profile.id)
            put("uriString", pad.uriString)
            put("fileName", pad.fileName)
            put("displayName", pad.displayName)
            put("extension", pad.extension)
            put("durationMs", pad.durationMs)
            put("sizeBytes", pad.sizeBytes)
            put("positionIndex", entry.slotIndex)
            put("bankIndex", pad.bankIndex)
            put("subfolderName", pad.subfolderName)
            pad.colorArgb?.let { put("colorArgb", it) } ?: putNull("colorArgb")
            pad.glowColorArgb?.let { put("glowColorArgb", it) } ?: putNull("glowColorArgb")
            put("glowIntensity", pad.glowIntensity)
            pad.shapeStyle?.name?.let { put("shapeStyle", it) } ?: putNull("shapeStyle")
            pad.borderColorArgb?.let { put("borderColorArgb", it) } ?: putNull("borderColorArgb")
            put("borderWidthDp", pad.borderWidthDp)
            put("cornerRadiusDp", pad.cornerRadiusDp)
            pad.textColorArgb?.let { put("textColorArgb", it) } ?: putNull("textColorArgb")
            put("surfaceFinish", pad.surfaceFinish.name)
            put("badgeIcon", pad.badgeIcon.name)
            put("showSlotNumber", if (pad.showSlotNumber) 1 else 0)
            put("showDurationLabel", if (pad.showDurationLabel) 1 else 0)
            put("isClone", if (pad.isClone) 1 else 0)
            pad.midiNote?.let { put("midiNote", it) } ?: putNull("midiNote")
            put("isFavorite", if (pad.isFavorite) 1 else 0)
            put("categoryTag", pad.categoryTag)
            put("miniWaveformJson", ProfileJsonSerializer.encodeMiniWaveform(pad.miniWaveform))
            pad.errorType?.name?.let { put("errorType", it) } ?: putNull("errorType")
            pad.errorMessage?.let { put("errorMessage", it) } ?: putNull("errorMessage")
          }
          db.insert("sound_pads", SQLiteDatabase.CONFLICT_REPLACE, padValues)

          val s = pad.settings
          val settingValues = ContentValues().apply {
            put("padId", pad.id)
            put("volume", s.volume)
            put("speed", s.speed)
            put("pitch", s.pitch)
            put("pan", s.pan)
            put("isLooping", if (s.isLooping) 1 else 0)
            put("triggerMode", s.triggerMode.name)
            put("chokeGroup", s.chokeGroup)
            put("fadeInMs", s.fadeInMs)
            put("fadeOutMs", s.fadeOutMs)
            put("fadeCurve", s.fadeCurve.name)
            put("crossfadeEnabled", if (s.crossfadeEnabled) 1 else 0)
            put("trimStartMs", s.trimStartMs)
            put("trimEndMs", s.trimEndMs)
            put("isDuckingSource", if (s.isDuckingSource) 1 else 0)
            put("normalizedGain", s.normalizedGain)
            put("eqEnabled", if (s.eqEnabled) 1 else 0)
            put("eqPreset", s.eqPreset.name)
            put("eqBandsJson", ProfileJsonSerializer.encodeEqBandsString(s.eqBandsDb))
            put("isReversed", if (s.isReversed) 1 else 0)
            put("fxPreset", s.fxPreset.name)
            put("echoEnabled", if (s.echoEnabled) 1 else 0)
            put("echoDelayMs", s.echoDelayMs)
            put("echoFeedback", s.echoFeedback)
            put("echoMix", s.echoMix)
            put("reverbEnabled", if (s.reverbEnabled) 1 else 0)
            put("reverbRoomSize", s.reverbRoomSize)
            put("reverbDamping", s.reverbDamping)
            put("reverbMix", s.reverbMix)
            s.retriggerMode?.name?.let { put("retriggerMode", it) } ?: putNull("retriggerMode")
            put("maxVoices", s.maxVoices.coerceIn(0, 16))
          }
          db.insert("pad_settings", SQLiteDatabase.CONFLICT_REPLACE, settingValues)
          restoredRows++
        }

        pBundle.ccMappings.forEach { cc ->
          val cv = ContentValues().apply {
            put("id", cc.id.ifBlank { "cc_${profile.id}_${cc.target.name}" })
            put("profileId", profile.id)
            put("target", cc.target.name)
            put("ccNumber", cc.ccNumber)
            put("channel", cc.channel)
          }
          db.insert("midi_cc_mappings", SQLiteDatabase.CONFLICT_REPLACE, cv)
          restoredRows++
        }

        pBundle.pcMappings.forEach { pc ->
          val cv = ContentValues().apply {
            put("id", pc.id.ifBlank { "pc_${profile.id}_${pc.bankIndex}" })
            put("profileId", profile.id)
            put("programNumber", pc.programNumber)
            put("bankIndex", pc.bankIndex)
            put("channel", pc.channel)
          }
          db.insert("midi_pc_bank_mappings", SQLiteDatabase.CONFLICT_REPLACE, cv)
          restoredRows++
        }
      }

      var nextReqCode = 100
      bundle.scheduler.tasks.forEach { task ->
        val assigned = if (task.requestCode > 0) task.requestCode else nextReqCode++
        if (assigned >= nextReqCode) nextReqCode = assigned + 1
        val cv = ContentValues().apply {
          put("id", task.id)
          put("title", task.title)
          put("targetType", task.targetType.name)
          put("targetId", task.targetId)
          put("hour", task.hour)
          put("minute", task.minute)
          put("repeatMode", task.repeatMode.name)
          put("daysOfWeekMask", task.daysOfWeekMask)
          put("enabled", if (task.enabled) 1 else 0)
          put("volume", task.volume)
          put("fadeInMs", task.fadeInMs)
          put("autoStopMinutes", task.autoStopMinutes)
          put("loopPlayback", if (task.loopPlayback) 1 else 0)
          put("requestCode", assigned)
          put("nextTriggerAtMs", task.nextTriggerAtMs)
          put("createdAtMs", task.createdAtMs)
        }
        db.insert("scheduled_tasks", SQLiteDatabase.CONFLICT_REPLACE, cv)
        restoredRows++
      }

      db.insert(
        "scheduler_id_counter",
        SQLiteDatabase.CONFLICT_REPLACE,
        ContentValues().apply {
          put("id", 1)
          put("nextRequestCode", nextReqCode.coerceAtLeast(100))
        }
      )

      bundle.sequencer.playlists.forEach { pl ->
        val cv = ContentValues().apply {
          put("id", pl.id)
          put("name", pl.name)
          put("padIdsJson", JSONArray(pl.padIds).toString())
          put("mode", pl.mode.name)
          put("gapSeconds", pl.gapSeconds)
          put("crossfadeEnabled", if (pl.crossfadeEnabled) 1 else 0)
          put("crossfadeDurationMs", pl.crossfadeDurationMs)
          pl.triggerPadId?.let { put("triggerPadId", it) } ?: putNull("triggerPadId")
          pl.triggerMidiNote?.let { put("triggerMidiNote", it) } ?: putNull("triggerMidiNote")
          put("updatedAtMs", pl.updatedAtMs)
        }
        db.insert("sequencer_playlists", SQLiteDatabase.CONFLICT_REPLACE, cv)
        restoredRows++
      }

      db.setTransactionSuccessful()
    } finally {
      db.endTransaction()
    }
    return restoredRows
  }

  private fun queryExistingTableNames(db: SQLiteDatabase): Set<String> {
    val result = mutableSetOf<String>()
    db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table'", null).use { cursor ->
      while (cursor.moveToNext()) {
        cursor.getString(0)?.let { result.add(it) }
      }
    }
    return result
  }

  private fun readLegacyProfiles(db: SQLiteDatabase): List<PadProfile> {
    val out = mutableListOf<PadProfile>()
    db.rawQuery("SELECT * FROM pad_profiles", null).use { c ->
      while (c.moveToNext()) {
        val id = c.optStr("id", "default_profile")
        val preset = runCatching { GridPreset.valueOf(c.optStr("gridPreset", GridPreset.GRID_4X4.name)) }
          .getOrDefault(GridPreset.GRID_4X4)
        val rows = c.optInt("gridRows", preset.rows).coerceIn(1, 16)
        val cols = c.optInt("gridCols", preset.cols).coerceIn(1, 16)
        val eqPreset = runCatching { EqPreset.valueOf(c.optStr("masterEqPreset", EqPreset.FLAT.name)) }
          .getOrDefault(EqPreset.FLAT)
        out.add(
          PadProfile(
            id = id,
            name = c.optStr("name", "Default Session"),
            folderUri = c.optNullStr("folderUri"),
            gridConfig = GridConfig(preset, rows, cols),
            bankNames = ProfileJsonSerializer.decodeBankNamesString(c.optNullStr("bankNamesJson")),
            bankOrder = ProfileJsonSerializer.decodeBankOrderString(c.optNullStr("bankOrderJson")),
            bankFolderUris = ProfileJsonSerializer.decodeBankFolderUrisString(c.optNullStr("bankFolderUrisJson")),
            masterEqEnabled = c.optBool("masterEqEnabled", false),
            masterEqPreset = eqPreset,
            masterEqBandsDb = ProfileJsonSerializer.decodeEqBandsString(c.optNullStr("masterEqBandsJson")),
            createdAtMs = c.optLong("createdAtMs", System.currentTimeMillis()),
            updatedAtMs = c.optLong("updatedAtMs", System.currentTimeMillis()),
            isActive = c.optBool("isActive", false),
            focusModeMidiNote = c.optNullInt("focusModeMidiNote")
          )
        )
      }
    }
    return out
  }

  private fun readLegacyPadSettings(db: SQLiteDatabase): Map<String, PadSettings> {
    val map = mutableMapOf<String, PadSettings>()
    db.rawQuery("SELECT * FROM pad_settings", null).use { c ->
      while (c.moveToNext()) {
        val padId = c.optNullStr("padId") ?: continue
        map[padId] = PadSettings(
          volume = c.optFloat("volume", 1.0f).coerceIn(0f, 1f),
          speed = c.optFloat("speed", 1.0f).coerceIn(0.5f, 2.0f),
          pitch = c.optFloat("pitch", 1.0f).coerceIn(0.5f, 2.0f),
          pan = c.optFloat("pan", 0.0f).coerceIn(-1f, 1f),
          isLooping = c.optBool("isLooping", false),
          triggerMode = runCatching { PadTriggerMode.valueOf(c.optStr("triggerMode", PadTriggerMode.TOGGLE.name)) }
            .getOrDefault(PadTriggerMode.TOGGLE),
          chokeGroup = c.optInt("chokeGroup", 0).coerceIn(0, 4),
          fadeInMs = c.optLong("fadeInMs", 0L).coerceAtLeast(0L),
          fadeOutMs = c.optLong("fadeOutMs", 0L).coerceAtLeast(0L),
          fadeCurve = runCatching { FadeCurve.valueOf(c.optStr("fadeCurve", FadeCurve.EQUAL_POWER.name)) }
            .getOrDefault(FadeCurve.EQUAL_POWER),
          crossfadeEnabled = c.optBool("crossfadeEnabled", true),
          trimStartMs = c.optLong("trimStartMs", 0L).coerceAtLeast(0L),
          trimEndMs = c.optLong("trimEndMs", 0L).coerceAtLeast(0L),
          isDuckingSource = c.optBool("isDuckingSource", false),
          normalizedGain = c.optFloat("normalizedGain", 1.0f).coerceIn(0.35f, 1.60f),
          eqEnabled = c.optBool("eqEnabled", false),
          eqPreset = runCatching { EqPreset.valueOf(c.optStr("eqPreset", EqPreset.FLAT.name)) }
            .getOrDefault(EqPreset.FLAT),
          eqBandsDb = ProfileJsonSerializer.decodeEqBandsString(c.optNullStr("eqBandsJson")),
          isReversed = c.optBool("isReversed", false),
          fxPreset = runCatching { FxPreset.valueOf(c.optStr("fxPreset", FxPreset.OFF.name)) }
            .getOrDefault(FxPreset.OFF),
          echoEnabled = c.optBool("echoEnabled", false),
          echoDelayMs = c.optFloat("echoDelayMs", 220f),
          echoFeedback = c.optFloat("echoFeedback", 0.30f),
          echoMix = c.optFloat("echoMix", 0.25f),
          reverbEnabled = c.optBool("reverbEnabled", false),
          reverbRoomSize = c.optFloat("reverbRoomSize", 0.35f),
          reverbDamping = c.optFloat("reverbDamping", 0.45f),
          reverbMix = c.optFloat("reverbMix", 0.25f),
          retriggerMode = c.optNullStr("retriggerMode")?.let { raw ->
            runCatching { com.example.domain.model.RetriggerMode.valueOf(raw) }.getOrNull()
          },
          maxVoices = c.optInt("maxVoices", 0).coerceIn(0, 16)
        )
      }
    }
    return map
  }

  private fun readLegacyPads(
    db: SQLiteDatabase,
    settingsMap: Map<String, PadSettings>
  ): Map<String, List<SoundPad>> {
    val grouped = linkedMapOf<String, MutableList<SoundPad>>()
    db.rawQuery("SELECT * FROM sound_pads", null).use { c ->
      var idx = 0
      while (c.moveToNext()) {
        val id = c.optStr("id", "pad_$idx")
        val profileId = c.optStr("profileId", "default_profile")
        val pad = SoundPad(
          id = id,
          uriString = c.optStr("uriString", ""),
          fileName = c.optStr("fileName", "sample.wav"),
          displayName = c.optStr("displayName", "Sample"),
          extension = c.optStr("extension", "WAV"),
          durationMs = c.optLong("durationMs", 0L).coerceAtLeast(0L),
          sizeBytes = c.optLong("sizeBytes", 0L).coerceAtLeast(0L),
          positionIndex = c.optInt("positionIndex", idx).coerceAtLeast(0),
          bankIndex = c.optInt("bankIndex", 0).coerceAtLeast(0),
          subfolderName = c.optStr("subfolderName", ""),
          colorArgb = c.optNullLong("colorArgb"),
          glowColorArgb = c.optNullLong("glowColorArgb"),
          glowIntensity = c.optFloat("glowIntensity", 0f),
          shapeStyle = c.optNullStr("shapeStyle")?.let { runCatching { PadShapeStyle.valueOf(it) }.getOrNull() },
          borderColorArgb = c.optNullLong("borderColorArgb"),
          borderWidthDp = c.optFloat("borderWidthDp", 1.25f),
          cornerRadiusDp = c.optFloat("cornerRadiusDp", 14f),
          textColorArgb = c.optNullLong("textColorArgb"),
          surfaceFinish = runCatching {
            PadSurfaceFinish.valueOf(c.optStr("surfaceFinish", PadSurfaceFinish.CLASSIC.name))
          }.getOrDefault(PadSurfaceFinish.CLASSIC),
          badgeIcon = runCatching {
            PadBadgeIcon.valueOf(c.optStr("badgeIcon", PadBadgeIcon.NONE.name))
          }.getOrDefault(PadBadgeIcon.NONE),
          showSlotNumber = c.optBool("showSlotNumber", true),
          showDurationLabel = c.optBool("showDurationLabel", true),
          isClone = c.optBool("isClone", false),
          midiNote = c.optNullInt("midiNote"),
          isFavorite = c.optBool("isFavorite", false),
          categoryTag = c.optStr("categoryTag", ""),
          miniWaveform = ProfileJsonSerializer.decodeMiniWaveform(c.optNullStr("miniWaveformJson")),
          errorType = c.optNullStr("errorType")?.let { runCatching { PadErrorType.valueOf(it) }.getOrNull() },
          errorMessage = c.optNullStr("errorMessage"),
          settings = settingsMap[id] ?: PadSettings()
        )
        grouped.getOrPut(profileId) { mutableListOf() }.add(pad)
        idx++
      }
    }
    return grouped
  }

  private fun readLegacyCcMappings(db: SQLiteDatabase): Map<String, List<MidiCcMapping>> {
    val map = linkedMapOf<String, MutableList<MidiCcMapping>>()
    db.rawQuery("SELECT * FROM midi_cc_mappings", null).use { c ->
      while (c.moveToNext()) {
        val profileId = c.optStr("profileId", "default_profile")
        val target = runCatching { MidiCcTarget.valueOf(c.optStr("target", "")) }.getOrNull() ?: continue
        val ccNumber = c.optInt("ccNumber", -1)
        if (ccNumber !in 0..127) continue
        val mapping = MidiCcMapping(
          id = c.optStr("id", "cc_${profileId}_${target.name}"),
          profileId = profileId,
          target = target,
          ccNumber = ccNumber,
          channel = c.optInt("channel", -1).coerceIn(-1, 15)
        )
        map.getOrPut(profileId) { mutableListOf() }.add(mapping)
      }
    }
    return map
  }

  private fun readLegacyPcMappings(db: SQLiteDatabase): Map<String, List<MidiProgramBankMapping>> {
    val map = linkedMapOf<String, MutableList<MidiProgramBankMapping>>()
    db.rawQuery("SELECT * FROM midi_pc_bank_mappings", null).use { c ->
      while (c.moveToNext()) {
        val profileId = c.optStr("profileId", "default_profile")
        val program = c.optInt("programNumber", -1)
        val bank = c.optInt("bankIndex", -1)
        if (program !in 0..127 || bank < 0) continue
        val mapping = MidiProgramBankMapping(
          id = c.optStr("id", "pc_${profileId}_$bank"),
          profileId = profileId,
          programNumber = program,
          bankIndex = bank,
          channel = c.optInt("channel", -1).coerceIn(-1, 15)
        )
        map.getOrPut(profileId) { mutableListOf() }.add(mapping)
      }
    }
    return map
  }

  private fun readLegacyScheduledTasks(db: SQLiteDatabase): List<ScheduledPadTask> {
    val out = mutableListOf<ScheduledPadTask>()
    db.rawQuery("SELECT * FROM scheduled_tasks", null).use { c ->
      while (c.moveToNext()) {
        val id = c.optNullStr("id") ?: continue
        out.add(
          ScheduledPadTask(
            id = id,
            title = c.optStr("title", "Jadwal"),
            targetType = runCatching { ScheduleTargetType.valueOf(c.optStr("targetType", ScheduleTargetType.PAD.name)) }
              .getOrDefault(ScheduleTargetType.PAD),
            targetId = c.optStr("targetId", ""),
            hour = c.optInt("hour", 9).coerceIn(0, 23),
            minute = c.optInt("minute", 0).coerceIn(0, 59),
            repeatMode = runCatching { ScheduleRepeatMode.valueOf(c.optStr("repeatMode", ScheduleRepeatMode.ONCE.name)) }
              .getOrDefault(ScheduleRepeatMode.ONCE),
            daysOfWeekMask = c.optInt("daysOfWeekMask", 0),
            enabled = c.optBool("enabled", true),
            volume = c.optFloat("volume", 1.0f).coerceIn(0f, 1f),
            fadeInMs = c.optLong("fadeInMs", 0L).coerceAtLeast(0L),
            autoStopMinutes = c.optInt("autoStopMinutes", 0).coerceAtLeast(0),
            loopPlayback = c.optBool("loopPlayback", false),
            requestCode = c.optInt("requestCode", 0),
            nextTriggerAtMs = c.optLong("nextTriggerAtMs", 0L),
            createdAtMs = c.optLong("createdAtMs", System.currentTimeMillis())
          )
        )
      }
    }
    return out
  }

  private fun readLegacySequencerPlaylists(db: SQLiteDatabase): List<SequencerPlaylist> {
    val out = mutableListOf<SequencerPlaylist>()
    db.rawQuery("SELECT * FROM sequencer_playlists", null).use { c ->
      while (c.moveToNext()) {
        val id = c.optNullStr("id") ?: continue
        val padIds = runCatching {
          val arr = JSONArray(c.optStr("padIdsJson", "[]"))
          buildList {
            for (i in 0 until arr.length()) {
              arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let(::add)
            }
          }
        }.getOrDefault(emptyList())
        out.add(
          SequencerPlaylist(
            id = id,
            name = c.optStr("name", "Playlist"),
            padIds = padIds,
            mode = runCatching { SequencerPlayMode.valueOf(c.optStr("mode", SequencerPlayMode.SEQUENTIAL.name)) }
              .getOrDefault(SequencerPlayMode.SEQUENTIAL),
            gapSeconds = c.optFloat("gapSeconds", 0f),
            crossfadeEnabled = c.optBool("crossfadeEnabled", false),
            crossfadeDurationMs = c.optLong("crossfadeDurationMs", 1500L),
            triggerPadId = c.optNullStr("triggerPadId"),
            triggerMidiNote = c.optNullInt("triggerMidiNote"),
            updatedAtMs = c.optLong("updatedAtMs", System.currentTimeMillis())
          )
        )
      }
    }
    return out
  }

  private fun Cursor.optStr(col: String, default: String): String {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return default
    return getString(i) ?: default
  }

  private fun Cursor.optNullStr(col: String): String? {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return null
    return getString(i)
  }

  private fun Cursor.optInt(col: String, default: Int): Int {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return default
    return getInt(i)
  }

  private fun Cursor.optNullInt(col: String): Int? {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return null
    return getInt(i)
  }

  private fun Cursor.optLong(col: String, default: Long): Long {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return default
    return getLong(i)
  }

  private fun Cursor.optNullLong(col: String): Long? {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return null
    return getLong(i)
  }

  private fun Cursor.optFloat(col: String, default: Float): Float {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return default
    return getFloat(i)
  }

  private fun Cursor.optBool(col: String, default: Boolean): Boolean {
    val i = getColumnIndex(col)
    if (i < 0 || isNull(i)) return default
    return getInt(i) != 0
  }
}
