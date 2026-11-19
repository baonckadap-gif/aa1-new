package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.media.AudioDeviceInfo
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.audio.AudioBufferMath
import com.example.audio.AudioMixMath
import com.example.audio.AudioOutputRouter
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SettingsBackupSerializer
import com.example.data.local.db.ScheduledTaskEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.db.SoundPadPreMigrationBackupManager
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.AudioOutputDeviceUi
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.SoundPad
import com.example.scheduler.PadSchedulerManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage3DatabaseSchedulerAudioUnitTest {

  @Rule
  @JvmField
  val migrationHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    SoundPadDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  private fun appContext(): Context = ApplicationProvider.getApplicationContext()

  // ==================== 3a: ROOM MIGRATION & PRE-DESTRUCTIVE BACKUP ====================

  @Test
  fun `migration 11 to 12 assigns unique request codes and initializes counter`() {
    val dbName = "migration_test_11_12.db"
    val context = appContext()
    context.deleteDatabase(dbName)

    migrationHelper.createDatabase(dbName, 11).apply {
      execSQL(
        """
        INSERT INTO scheduled_tasks (
          id, title, targetType, targetId, hour, minute, repeatMode, daysOfWeekMask,
          enabled, volume, fadeInMs, autoStopMinutes, loopPlayback, nextTriggerAtMs, createdAtMs
        ) VALUES (
          'task_alpha', 'Bel Masuk', 'PAD', 'pad_1', 7, 30, 'DAILY', 0,
          1, 0.9, 500, 2, 0, 1700000000000, 1699999000000
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO scheduled_tasks (
          id, title, targetType, targetId, hour, minute, repeatMode, daysOfWeekMask,
          enabled, volume, fadeInMs, autoStopMinutes, loopPlayback, nextTriggerAtMs, createdAtMs
        ) VALUES (
          'task_beta', 'Bel Pulang', 'BANK', '1', 16, 0, 'ONCE', 0,
          1, 1.0, 0, 1, 1, 1700000500000, 1699999500000
        )
        """.trimIndent()
      )
      close()
    }

    val migrated = migrationHelper.runMigrationsAndValidate(
      dbName,
      12,
      true,
      SoundPadDatabase.MIGRATION_11_12
    )

    try {
      migrated.query("SELECT id, requestCode FROM scheduled_tasks ORDER BY rowid ASC").use { cursor ->
        assertEquals(2, cursor.count)
        assertTrue(cursor.moveToFirst())
        val code1 = cursor.getInt(1)
        assertTrue(cursor.moveToNext())
        val code2 = cursor.getInt(1)
        assertTrue(code1 >= 100)
        assertTrue(code2 > code1)

        migrated.query("SELECT nextRequestCode FROM scheduler_id_counter WHERE id = 1").use { counterCursor ->
          assertTrue(counterCursor.moveToFirst())
          val nextCode = counterCursor.getInt(0)
          assertTrue(nextCode > code2)
        }
      }
    } finally {
      migrated.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun `full incremental migration 8 to 12 preserves profiles pads settings and cc mappings`() {
    val dbName = "migration_test_8_to_12.db"
    val context = appContext()
    context.deleteDatabase(dbName)

    migrationHelper.createDatabase(dbName, 8).apply {
      execSQL(
        """
        INSERT INTO pad_profiles (
          id, name, folderUri, gridPreset, gridRows, gridCols, bankNamesJson, createdAtMs, updatedAtMs, isActive
        ) VALUES (
          'prof_v8', 'V8 Event', 'content://tree/v8', 'GRID_4X4', 4, 4, '{"0":"Main"}', 1000, 2000, 1
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO sound_pads (
          id, profileId, uriString, fileName, displayName, extension, durationMs, sizeBytes, positionIndex,
          colorArgb, glowColorArgb, glowIntensity, shapeStyle, borderColorArgb, borderWidthDp, cornerRadiusDp,
          textColorArgb, surfaceFinish, badgeIcon, showSlotNumber, showDurationLabel, isClone, midiNote, isFavorite, categoryTag
        ) VALUES (
          'pad_v8', 'prof_v8', 'content://audio/v8.wav', 'v8.wav', 'V8 Gong', 'WAV', 3400, 120000, 5,
          NULL, NULL, 1.0, NULL, NULL, 1.25, 14.0, NULL, 'CLASSIC', 'NONE', 1, 1, 0, 60, 1, 'ceremony'
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO pad_settings (
          padId, volume, speed, pitch, isLooping, triggerMode, chokeGroup, fadeInMs, fadeOutMs,
          fadeCurve, crossfadeEnabled, trimStartMs, trimEndMs, isDuckingSource, normalizedGain
        ) VALUES (
          'pad_v8', 0.88, 1.0, 1.0, 1, 'TOGGLE', 2, 150, 300, 'EQUAL_POWER', 1, 0, 0, 0, 1.15
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO midi_cc_mappings (id, profileId, target, ccNumber, channel)
        VALUES ('cc_v8', 'prof_v8', 'MASTER_VOLUME', 7, -1)
        """.trimIndent()
      )
      close()
    }

    val migrated = migrationHelper.runMigrationsAndValidate(
      dbName,
      12,
      true,
      *SoundPadDatabase.ALL_MIGRATIONS
    )

    try {
      migrated.query("SELECT name, bankOrderJson, masterEqPreset FROM pad_profiles WHERE id = 'prof_v8'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("V8 Event", c.getString(0))
        assertEquals("[]", c.getString(1))
        assertEquals("FLAT", c.getString(2))
      }
      migrated.query("SELECT displayName, positionIndex, bankIndex, midiNote FROM sound_pads WHERE id = 'pad_v8'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("V8 Gong", c.getString(0))
        assertEquals(5, c.getInt(1))
        assertEquals(0, c.getInt(2))
        assertEquals(60, c.getInt(3))
      }
      migrated.query("SELECT volume, pan, eqPreset FROM pad_settings WHERE padId = 'pad_v8'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals(0.88f, c.getFloat(0), 0.001f)
        assertEquals(0f, c.getFloat(1), 0.001f)
        assertEquals("FLAT", c.getString(2))
      }
      migrated.query("SELECT ccNumber FROM midi_cc_mappings WHERE id = 'cc_v8'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals(7, c.getInt(0))
      }
    } finally {
      migrated.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun `pre destructive backup exports legacy v5 database and restores it automatically into v12`() {
    val context = appContext()
    val dbName = "legacy_v5_destructive_test.db"
    context.deleteDatabase(dbName)
    val backupFile = SoundPadPreMigrationBackupManager.getLatestBackupFile(context)
    backupFile.delete()

    val dbFile: File = context.getDatabasePath(dbName)
    dbFile.parentFile?.mkdirs()

    // Create synthetic legacy v5 SQLite database
    SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacyDb ->
      legacyDb.version = 5
      legacyDb.execSQL(
        """
        CREATE TABLE pad_profiles (
          id TEXT NOT NULL PRIMARY KEY,
          name TEXT NOT NULL,
          folderUri TEXT,
          gridPreset TEXT NOT NULL,
          gridRows INTEGER NOT NULL,
          gridCols INTEGER NOT NULL,
          bankNamesJson TEXT NOT NULL,
          createdAtMs INTEGER NOT NULL,
          updatedAtMs INTEGER NOT NULL,
          isActive INTEGER NOT NULL
        )
        """.trimIndent()
      )
      legacyDb.execSQL(
        """
        CREATE TABLE sound_pads (
          id TEXT NOT NULL PRIMARY KEY,
          profileId TEXT NOT NULL,
          uriString TEXT NOT NULL,
          fileName TEXT NOT NULL,
          displayName TEXT NOT NULL,
          extension TEXT NOT NULL,
          durationMs INTEGER NOT NULL,
          sizeBytes INTEGER NOT NULL,
          positionIndex INTEGER NOT NULL
        )
        """.trimIndent()
      )
      legacyDb.execSQL(
        """
        CREATE TABLE pad_settings (
          padId TEXT NOT NULL PRIMARY KEY,
          volume REAL NOT NULL,
          speed REAL NOT NULL,
          pitch REAL NOT NULL,
          isLooping INTEGER NOT NULL
        )
        """.trimIndent()
      )
      legacyDb.execSQL(
        """
        INSERT INTO pad_profiles (id, name, folderUri, gridPreset, gridRows, gridCols, bankNamesJson, createdAtMs, updatedAtMs, isActive)
        VALUES ('v5_prof', 'Legacy Show v5', 'content://legacy/tree', 'GRID_4X4', 4, 4, '{"0":"Utama"}', 100, 200, 1)
        """.trimIndent()
      )
      legacyDb.execSQL(
        """
        INSERT INTO sound_pads (id, profileId, uriString, fileName, displayName, extension, durationMs, sizeBytes, positionIndex)
        VALUES ('v5_pad_1', 'v5_prof', 'content://legacy/intro.wav', 'intro.wav', 'Intro Lama', 'WAV', 4200, 96000, 7)
        """.trimIndent()
      )
      legacyDb.execSQL(
        """
        INSERT INTO pad_settings (padId, volume, speed, pitch, isLooping)
        VALUES ('v5_pad_1', 0.72, 1.1, 0.95, 1)
        """.trimIndent()
      )
    }

    val writtenBackup = SoundPadPreMigrationBackupManager.backupIfDestructiveMigrationNeeded(
      context = context,
      dbName = dbName,
      minIncrementalVersion = 8
    )
    assertNotNull("Expected JSON pre-destructive backup to be written for v5 db", writtenBackup)
    assertTrue(writtenBackup!!.exists())

    val roomDb = Room.databaseBuilder(context, SoundPadDatabase::class.java, dbName)
      .addMigrations(*SoundPadDatabase.ALL_MIGRATIONS)
      .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7)
      .addCallback(SoundPadDatabase.createRestoreCallback(context))
      .allowMainThreadQueries()
      .build()

    try {
      runBlocking {
        val dao = roomDb.soundPadDao()
        val profiles = dao.getAllProfilesOnce()
        assertEquals(1, profiles.size)
        assertEquals("v5_prof", profiles.first().id)
        assertEquals("Legacy Show v5", profiles.first().name)

        val pads = dao.getPadsForProfileOnce("v5_prof")
        assertEquals(1, pads.size)
        assertEquals("Intro Lama", pads.first().displayName)
        assertEquals(7, pads.first().positionIndex)

        val setting = dao.getPadSetting("v5_pad_1")
        assertNotNull(setting)
        assertEquals(0.72f, setting!!.volume, 0.001f)
        assertEquals(1.1f, setting.speed, 0.001f)
        assertTrue(setting.isLooping)
      }
    } finally {
      roomDb.close()
      context.deleteDatabase(dbName)
      backupFile.delete()
    }
  }

  // ==================== 3b: SCHEDULER UNIQUE REQUEST CODE & TARGETED AUTO-STOP ====================

  @Test
  fun `dao assigns unique monotonic request codes even on colliding taskId hashCodes`() {
    val context = appContext()
    val roomDb = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()

    try {
      runBlocking {
        val dao = roomDb.soundPadDao()
        // "Aa" and "BB" have identical String.hashCode() == 2112
        assertEquals("Aa".hashCode(), "BB".hashCode())

        val task1 = ScheduledTaskEntity(
          id = "Aa",
          title = "Task Aa",
          targetType = ScheduleTargetType.PAD.name,
          targetId = "pad_a",
          hour = 8,
          minute = 0,
          repeatMode = ScheduleRepeatMode.ONCE.name,
          daysOfWeekMask = 0,
          enabled = true,
          volume = 1f,
          fadeInMs = 0L,
          autoStopMinutes = 2,
          loopPlayback = false,
          requestCode = 0,
          nextTriggerAtMs = 1000L,
          createdAtMs = 1000L
        )
        val task2 = task1.copy(id = "BB", title = "Task BB", targetId = "pad_b")

        val saved1 = dao.upsertScheduledTaskAssigningRequestCode(task1)
        val saved2 = dao.upsertScheduledTaskAssigningRequestCode(task2)

        assertTrue(saved1.requestCode >= 100)
        assertTrue(saved2.requestCode > saved1.requestCode)
        assertNotEquals(
          PadSchedulerManager.startRequestCode(saved1.id, saved1.requestCode),
          PadSchedulerManager.startRequestCode(saved2.id, saved2.requestCode)
        )
        assertNotEquals(
          PadSchedulerManager.stopRequestCode(saved1.id, saved1.requestCode),
          PadSchedulerManager.startRequestCode(saved1.id, saved1.requestCode)
        )

        // Updating existing task must preserve its assigned requestCode
        val updated1 = dao.upsertScheduledTaskAssigningRequestCode(
          saved1.copy(title = "Task Aa Edited", requestCode = 0)
        )
        assertEquals(saved1.requestCode, updated1.requestCode)
      }
    } finally {
      roomDb.close()
    }
  }

  @Test
  fun `scheduled auto stop stops only target pad or target bank without stopping unrelated pads`() {
    val stoppedPads = mutableListOf<String>()
    var sequencerStopped = false

    val allPads = listOf(
      "pad_target" to 0,
      "pad_other_playing" to 0,
      "pad_bank1_a" to 1,
      "pad_bank1_b" to 1
    )

    // 1. PAD auto-stop stops ONLY pad_target
    ScheduledAutoStopDispatcher.dispatchStopTarget(
      targetType = ScheduleTargetType.PAD,
      targetId = "pad_target",
      activePlaylistId = "pl_1",
      padsProvider = { allPads },
      onStopPad = { stoppedPads.add(it) },
      onStopSequencer = { sequencerStopped = true }
    )
    assertEquals(listOf("pad_target"), stoppedPads)
    assertFalse(sequencerStopped)

    // 2. BANK auto-stop stops ONLY pads in bank 1
    stoppedPads.clear()
    ScheduledAutoStopDispatcher.dispatchStopTarget(
      targetType = ScheduleTargetType.BANK,
      targetId = "1",
      activePlaylistId = "pl_1",
      padsProvider = { allPads },
      onStopPad = { stoppedPads.add(it) },
      onStopSequencer = { sequencerStopped = true }
    )
    assertEquals(listOf("pad_bank1_a", "pad_bank1_b"), stoppedPads)
    assertFalse(sequencerStopped)

    // 3. SEQUENCER auto-stop ignores mismatch and stops active playlist
    stoppedPads.clear()
    ScheduledAutoStopDispatcher.dispatchStopTarget(
      targetType = ScheduleTargetType.SEQUENCER,
      targetId = "other_pl",
      activePlaylistId = "pl_1",
      padsProvider = { allPads },
      onStopPad = { stoppedPads.add(it) },
      onStopSequencer = { sequencerStopped = true }
    )
    assertFalse(sequencerStopped)

    ScheduledAutoStopDispatcher.dispatchStopTarget(
      targetType = ScheduleTargetType.SEQUENCER,
      targetId = "pl_1",
      activePlaylistId = "pl_1",
      padsProvider = { allPads },
      onStopPad = { stoppedPads.add(it) },
      onStopSequencer = { sequencerStopped = true }
    )
    assertTrue(sequencerStopped)
    assertTrue(stoppedPads.isEmpty())
  }

  // ==================== 3c: AUDIO BUFFER & LOAD CONTROL ====================

  @Test
  fun `audio buffer size ms maps consistently to load control buffer durations`() {
    val low = AudioBufferMath.computeLoadControlDurations(20)
    assertEquals(20, low.bufferForPlaybackMs)
    assertTrue(low.bufferForPlaybackAfterRebufferMs >= low.bufferForPlaybackMs)
    assertTrue(low.minBufferMs >= low.bufferForPlaybackAfterRebufferMs)
    assertTrue(low.maxBufferMs >= low.minBufferMs)

    val mid = AudioBufferMath.computeLoadControlDurations(250)
    assertEquals(250, mid.bufferForPlaybackMs)
    assertEquals(500, mid.bufferForPlaybackAfterRebufferMs)
    assertEquals(1000, mid.minBufferMs)
    assertEquals(4000, mid.maxBufferMs)

    val high = AudioBufferMath.computeLoadControlDurations(500)
    assertEquals(500, high.bufferForPlaybackMs)
    assertEquals(1000, high.bufferForPlaybackAfterRebufferMs)
    assertEquals(2000, high.minBufferMs)
    assertEquals(4000, high.maxBufferMs)

    // Out-of-bounds clamped to [20, 500]
    assertEquals(20, AudioBufferMath.computeLoadControlDurations(1).bufferForPlaybackMs)
    assertEquals(500, AudioBufferMath.computeLoadControlDurations(2000).bufferForPlaybackMs)
  }

  // ==================== 3d: STABLE AUDIO OUTPUT DEVICE IDENTITY ====================

  @Test
  fun `audio output matching reconnects by stable address and product name across volatile id changes`() {
    val speaker = AudioOutputDeviceUi(
      id = 2,
      name = "Phone Speaker",
      typeLabel = "Phone Speaker",
      isHeadphoneLike = false,
      deviceType = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
      productName = "",
      address = ""
    )
    val usbDacNewId = AudioOutputDeviceUi(
      id = 77, // Volatile ID changed after unplug + replug
      name = "Focusrite Scarlett (USB Headset)",
      typeLabel = "USB Headset",
      isHeadphoneLike = true,
      deviceType = AudioDeviceInfo.TYPE_USB_HEADSET,
      productName = "Focusrite Scarlett",
      address = "usb:001/004"
    )
    val btHeadsetNewId = AudioOutputDeviceUi(
      id = 91,
      name = "Sony WH-1000XM5 (Bluetooth Audio)",
      typeLabel = "Bluetooth Audio",
      isHeadphoneLike = true,
      deviceType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
      productName = "Sony WH-1000XM5",
      address = "AA:BB:CC:DD:EE:FF"
    )
    val wiredRecycledId = AudioOutputDeviceUi(
      id = 10, // Recycled previous ID but completely different device type
      name = "Wired Headset",
      typeLabel = "Wired Headset",
      isHeadphoneLike = true,
      deviceType = AudioDeviceInfo.TYPE_WIRED_HEADSET,
      productName = "Generic Jack",
      address = ""
    )

    val attached = listOf(speaker, wiredRecycledId, usbDacNewId, btHeadsetNewId)

    // Reconnect USB DAC even though old id (10) was recycled by wired jack
    val matchedUsb = AudioOutputRouter.findBestMatchingOutputDevice(
      devices = attached,
      preferredId = 10,
      preferredProductName = "Focusrite Scarlett",
      preferredType = AudioDeviceInfo.TYPE_USB_HEADSET,
      preferredAddress = "usb:001/004"
    )
    assertEquals(usbDacNewId, matchedUsb)

    // Reconnect Bluetooth by deviceType + productName when address is unavailable
    val matchedBt = AudioOutputRouter.findBestMatchingOutputDevice(
      devices = attached,
      preferredId = 35,
      preferredProductName = "Sony WH-1000XM5",
      preferredType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
      preferredAddress = ""
    )
    assertEquals(btHeadsetNewId, matchedBt)

    // Disconnected external device returns null cleanly so saved preference is preserved for reconnect
    val unplugged = AudioOutputRouter.findBestMatchingOutputDevice(
      devices = listOf(speaker),
      preferredId = 77,
      preferredProductName = "Focusrite Scarlett",
      preferredType = AudioDeviceInfo.TYPE_USB_HEADSET,
      preferredAddress = "usb:001/004"
    )
    assertNull(unplugged)
  }

  // ==================== 3e: CUE VOLUME & SETTINGS SERIALIZATION ====================

  @Test
  fun `cue gain uses dedicated cueVolume and settings backup roundtrips cueVolume buffer and stable device identity`() {
    assertEquals(0.42f, AudioMixMath.computeCueGain(padVolume = 0.6f, cueVolume = 0.7f), 0.0001f)
    assertEquals(0f, AudioMixMath.computeCueGain(padVolume = 1.0f, cueVolume = 0f), 0.0001f)

    val originalSettings = GlobalSettings(
      cueVolume = 0.63f,
      audioBufferSizeMs = 35,
      preferredMainOutputId = 12,
      preferredMainOutputName = "Focusrite Scarlett (USB Audio)",
      preferredMainOutputProductName = "Focusrite Scarlett",
      preferredMainOutputType = AudioDeviceInfo.TYPE_USB_DEVICE,
      preferredMainOutputAddress = "usb:002/001",
      preferredCueOutputId = 25,
      preferredCueOutputName = "JBL Monitor (Bluetooth Audio)",
      preferredCueOutputProductName = "JBL Monitor",
      preferredCueOutputType = AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
      preferredCueOutputAddress = "11:22:33:44:55:66"
    )

    val settingsJson = SettingsBackupSerializer.exportBackupJson(SettingsBackupBundle(settings = originalSettings))
    val restoredSettingsBundle = SettingsBackupSerializer.importBackupJson(settingsJson)
    assertEquals(0.63f, restoredSettingsBundle.settings.cueVolume, 0.001f)
    assertEquals(35, restoredSettingsBundle.settings.audioBufferSizeMs)
    assertEquals("Focusrite Scarlett", restoredSettingsBundle.settings.preferredMainOutputProductName)
    assertEquals(AudioDeviceInfo.TYPE_USB_DEVICE, restoredSettingsBundle.settings.preferredMainOutputType)
    assertEquals("usb:002/001", restoredSettingsBundle.settings.preferredMainOutputAddress)
    assertEquals("JBL Monitor", restoredSettingsBundle.settings.preferredCueOutputProductName)
    assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, restoredSettingsBundle.settings.preferredCueOutputType)
    assertEquals("11:22:33:44:55:66", restoredSettingsBundle.settings.preferredCueOutputAddress)

    val sampleBundle = ProfileJsonSerializer.importFromJson(
      ProfileJsonSerializer.exportToJson(
        profile = PadProfile(
          id = "prof_full_rt",
          name = "Main Profile",
          folderUri = null,
          gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
          createdAtMs = 100L,
          updatedAtMs = 200L,
          isActive = true
        ),
        pads = emptyList()
      )
    )
    val fullJson = FullBackupSerializer.exportBackupJson(originalSettings, listOf(sampleBundle))
    val restoredFull = FullBackupSerializer.importBackupJson(fullJson)
    assertEquals(0.63f, restoredFull.globalSettings.cueVolume, 0.001f)
    assertEquals("Focusrite Scarlett", restoredFull.globalSettings.preferredMainOutputProductName)
    assertEquals("11:22:33:44:55:66", restoredFull.globalSettings.preferredCueOutputAddress)
  }

  // ==================== 3f: PROFILE JSON SERIALIZER PC & FOCUS MAPPINGS ====================

  @Test
  fun `profile json serializer v7 roundtrips midi pc bank mappings and focus mode note and imports v6 cleanly`() {
    val profile = PadProfile(
      id = "prof_stage3",
      name = "Live Concert Rig",
      folderUri = "content://tree/concert",
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      bankNames = mapOf(0 to "Intro", 1 to "Drop"),
      bankOrder = listOf(0, 1),
      createdAtMs = 1000L,
      updatedAtMs = 2000L,
      isActive = true
    )
    val pad = SoundPad(
      id = "pad_1",
      uriString = "content://audio/boom.wav",
      fileName = "boom.wav",
      displayName = "Sub Boom",
      extension = "WAV",
      durationMs = 1500L,
      sizeBytes = 65536L,
      positionIndex = 2,
      bankIndex = 0,
      settings = PadSettings(volume = 0.9f)
    )
    val ccMappings = listOf(
      MidiCcMapping(
        id = "cc_1",
        profileId = profile.id,
        target = MidiCcTarget.MASTER_VOLUME,
        ccNumber = 7,
        channel = 0
      )
    )
    val pcMappings = listOf(
      MidiProgramBankMapping(
        id = "pc_0",
        profileId = profile.id,
        programNumber = 12,
        bankIndex = 0,
        channel = 1
      ),
      MidiProgramBankMapping(
        id = "pc_1",
        profileId = profile.id,
        programNumber = 24,
        bankIndex = 1,
        channel = -1
      )
    )

    val jsonStr = ProfileJsonSerializer.exportToJson(
      profile = profile,
      pads = listOf(pad),
      ccMappings = ccMappings,
      pcMappings = pcMappings,
      focusModeMidiNote = 84
    )
    val jsonRoot = JSONObject(jsonStr)
    assertEquals(7, jsonRoot.getInt("schemaVersion"))

    val imported = ProfileJsonSerializer.importFromJson(jsonStr)
    assertEquals(1, imported.ccMappings.size)
    assertEquals(2, imported.pcMappings.size)
    assertEquals(12, imported.pcMappings[0].programNumber)
    assertEquals(0, imported.pcMappings[0].bankIndex)
    assertEquals(1, imported.pcMappings[0].channel)
    assertEquals(24, imported.pcMappings[1].programNumber)
    assertEquals(1, imported.pcMappings[1].bankIndex)
    assertEquals(-1, imported.pcMappings[1].channel)
    assertEquals(84, imported.focusModeMidiNote)

    // Simulate legacy v6 JSON without midiPcMappings or focusModeMidiNote
    val legacyV6Root = JSONObject(jsonStr).apply {
      put("schemaVersion", 6)
      remove("midiPcMappings")
      remove("focusModeMidiNote")
    }
    val importedV6 = ProfileJsonSerializer.importFromJson(legacyV6Root.toString())
    assertTrue(importedV6.pcMappings.isEmpty())
    assertNull(importedV6.focusModeMidiNote)
    assertEquals(1, importedV6.ccMappings.size)
    assertEquals(1, importedV6.pads.size)
  }
}
