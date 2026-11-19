package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.toEntity
import com.example.data.backup.AudioOutputConfigBackup
import com.example.data.backup.BackupSectionContributor
import com.example.data.backup.BackupSectionRegistry
import com.example.data.backup.Backupable
import com.example.data.backup.BankBackupEntry
import com.example.data.backup.ConfigMetaBackupSerializer
import com.example.data.backup.ConfigMetaJson
import com.example.data.backup.ConfigSchemaMigrator
import com.example.data.backup.FileHashAndPathHelper
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.LayoutBackupSerializer
import com.example.data.backup.LayoutSlotEntry
import com.example.data.backup.MidiMappingBackupBundle
import com.example.data.backup.MidiMappingBackupSerializer
import com.example.data.backup.PadBackupEntry
import com.example.data.backup.PadFileMetadata
import com.example.data.backup.PadLayoutBackup
import com.example.data.backup.PadMidiNoteEntry
import com.example.data.backup.PersistentConfigBundle
import com.example.data.backup.PersistentConfigBundleSerializer
import com.example.data.backup.PersistentConfigSchema
import com.example.data.backup.PersistentConfigSnapshotAssembler
import com.example.data.backup.PersistentSyncConfigBackup
import com.example.data.backup.PresetsBackupSerializer
import com.example.data.backup.ProfileBackupBundle
import com.example.data.backup.ProfileBackupSerializer
import com.example.data.backup.SchedulerBackupBundle
import com.example.data.backup.SchedulerBackupSerializer
import com.example.data.backup.SequencerBackupBundle
import com.example.data.backup.SequencerBackupSerializer
import com.example.data.backup.SequencerMidiNoteEntry
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SettingsBackupSerializer
import com.example.data.local.db.MidiCcMappingEntity
import com.example.data.local.db.MidiPcBankMappingEntity
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.db.SoundPadEntity
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.AppLanguage
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PlayMode
import com.example.domain.model.ScheduleRepeatMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlayMode
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.scheduler.toEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersistentConfigSerializerTest {

  @Test
  fun `file hash helper computes deterministic quick sha1 and safe slugs`() {
    val smallBytes = "soundpad-audio-sample-123".toByteArray()
    val directSha1 = FileHashAndPathHelper.computeSha1Hex(smallBytes)
    val quickSmall = FileHashAndPathHelper.computeQuickSha1Hex(smallBytes.size.toLong()) {
      ByteArrayInputStream(smallBytes)
    }
    assertEquals(40, directSha1.length)
    assertEquals(40, quickSmall.length)

    val largeBytes = ByteArray(200 * 1024) { idx -> (idx % 251).toByte() }
    val quickA = FileHashAndPathHelper.computeQuickSha1Hex(largeBytes.size.toLong()) {
      ByteArrayInputStream(largeBytes)
    }
    val quickB = FileHashAndPathHelper.computeQuickSha1Hex(largeBytes.size.toLong()) {
      ByteArrayInputStream(largeBytes)
    }
    assertEquals(quickA, quickB)

    val modifiedTail = largeBytes.copyOf().apply { this[lastIndex] = (this[lastIndex] + 1).toByte() }
    val quickModified = FileHashAndPathHelper.computeQuickSha1Hex(modifiedTail.size.toLong()) {
      ByteArrayInputStream(modifiedTail)
    }
    assertTrue(quickA != quickModified)

    assertEquals("live_show_2026", FileHashAndPathHelper.sanitizeFileSlug("Live Show / 2026!"))
    assertEquals("Drums/Kick.wav", FileHashAndPathHelper.extractRelativePath(null, "Drums", "Kick.wav", null))
  }

  @Test
  fun `full folder structure export and import preserves all entities settings and metadata`() {
    val settings = GlobalSettings(
      activeProfileId = "profile_main",
      folderUri = "content://tree/music",
      folderName = "Stage Audio",
      gridConfig = GridConfig(GridPreset.CUSTOM, rows = 5, cols = 6),
      playMode = PlayMode.MULTI,
      masterVolume = 0.85f,
      masterPan = -0.15f,
      masterEqEnabled = true,
      masterEqPreset = EqPreset.CUSTOM,
      masterEqBandsDb = listOf(4f, 1.5f, 0f, 2.5f, -1f),
      themeMode = ThemeMode.AMOLED,
      useDynamicColor = false,
      appAccentColorArgb = 0xFF00E5FFL,
      defaultPadColorMode = DefaultPadColorMode.CUSTOM,
      defaultPadColorArgb = 0xFF3949ABL,
      isFocusMode = true,
      startInFocusMode = true,
      focusCornerPanicEnabled = false,
      focusModeMidiNote = 60,
      preferredMainOutputId = 42,
      preferredMainOutputName = "USB Mixer L/R",
      preferredCueOutputId = 43,
      preferredCueOutputName = "Wired Headphones",
      audioBufferSizeMs = 32,
      persistentBackupFolderUri = "content://tree/docs_soundpadpro",
      persistentBackupFolderName = "SoundPadPro",
      persistentAutoSyncEnabled = true,
      persistentLastSyncAtMs = 1760000000000L,
      persistentIncludeWaveforms = false,
      hapticIntensity = HapticIntensity.STRONG,
      appLanguage = AppLanguage.INDONESIAN
    )

    val padSettings = PadSettings(
      volume = 0.78f,
      speed = 1.15f,
      pitch = 0.92f,
      pan = 0.45f,
      isLooping = true,
      triggerMode = PadTriggerMode.HOLD,
      chokeGroup = 2,
      fadeInMs = 350L,
      fadeOutMs = 600L,
      fadeCurve = FadeCurve.S_CURVE,
      crossfadeEnabled = true,
      trimStartMs = 120L,
      trimEndMs = 4500L,
      isDuckingSource = true,
      normalizedGain = 1.22f,
      eqEnabled = true,
      eqPreset = EqPreset.VOCAL,
      eqBandsDb = EqPreset.VOCAL.gainsDb,
      isReversed = true,
      fxPreset = FxPreset.HALL,
      echoEnabled = true,
      echoDelayMs = 310f,
      echoFeedback = 0.44f,
      echoMix = 0.38f,
      reverbEnabled = true,
      reverbRoomSize = 0.80f,
      reverbDamping = 0.40f,
      reverbMix = 0.50f
    )

    val pad = SoundPad(
      id = "pad_01",
      uriString = "content://tree/music/doc/sfx_intro.wav",
      fileName = "sfx_intro.wav",
      displayName = "Intro Boom",
      extension = "WAV",
      durationMs = 5200L,
      sizeBytes = 942080L,
      positionIndex = 24,
      bankIndex = 1,
      subfolderName = "SFX",
      colorArgb = 0xFFE53935L,
      glowColorArgb = 0xFFFF8A80L,
      glowIntensity = 0.85f,
      shapeStyle = PadShapeStyle.PRISM,
      borderColorArgb = 0xFFFFFFFFL,
      borderWidthDp = 2.0f,
      cornerRadiusDp = 18f,
      textColorArgb = 0xFFFAFAFAL,
      surfaceFinish = PadSurfaceFinish.NEON_GRADIENT,
      badgeIcon = PadBadgeIcon.BOLT,
      showSlotNumber = true,
      showDurationLabel = false,
      isClone = true,
      midiNote = 36,
      isFavorite = true,
      categoryTag = "Intro",
      miniWaveform = listOf(0.2f, 0.7f, 0.95f, 0.4f),
      settings = padSettings
    )

    val profile = PadProfile(
      id = "profile_main",
      name = "Live Concert Show",
      folderUri = "content://tree/music",
      gridConfig = settings.gridConfig,
      padCount = 1,
      bankNames = mapOf(0 to "Main", 1 to "SFX"),
      bankOrder = listOf(1, 0),
      bankFolderUris = mapOf(1 to "content://tree/music/sfx"),
      masterEqEnabled = true,
      masterEqPreset = EqPreset.CUSTOM,
      masterEqBandsDb = listOf(4f, 1.5f, 0f, 2.5f, -1f),
      themeMode = ThemeMode.AMOLED,
      defaultPadColorMode = DefaultPadColorMode.CUSTOM,
      defaultPadColorArgb = 0xFF3949ABL,
      createdAtMs = 1000L,
      updatedAtMs = 2000L,
      isActive = true
    )

    val ccMapping = MidiCcMapping(
      id = "cc_main_focus",
      profileId = "profile_main",
      target = MidiCcTarget.TOGGLE_FOCUS_MODE,
      ccNumber = 64,
      channel = 0
    )
    val pcMapping = MidiProgramBankMapping(
      id = "pc_main_b1",
      profileId = "profile_main",
      programNumber = 7,
      bankIndex = 1,
      channel = 2
    )
    val profileBundle = ProfileBackupBundle(
      profile = profile,
      banks = listOf(
        BankBackupEntry(bankIndex = 1, name = "SFX", folderUri = "content://tree/music/sfx", orderPosition = 0),
        BankBackupEntry(bankIndex = 0, name = "Main", folderUri = null, orderPosition = 1)
      ),
      pads = listOf(
        PadBackupEntry(
          pad = pad,
          slotIndex = 24,
          fileMetadata = PadFileMetadata(
            padId = pad.id,
            uriString = pad.uriString,
            relativePath = "SFX/sfx_intro.wav",
            fileName = pad.fileName,
            sizeBytes = pad.sizeBytes,
            sha1Hash = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
            durationMs = pad.durationMs,
            extension = pad.extension
          )
        )
      ),
      ccMappings = listOf(ccMapping),
      pcMappings = listOf(pcMapping)
    )

    val layout = PadLayoutBackup(
      layoutId = "layout_show_night",
      profileId = "profile_main",
      name = "Show Malam",
      gridRows = 5,
      gridCols = 6,
      isDefault = true,
      createdAtMs = 1000L,
      updatedAtMs = 2500L,
      slots = listOf(
        LayoutSlotEntry(
          padId = "pad_01",
          uriString = pad.uriString,
          relativePath = "SFX/sfx_intro.wav",
          fileName = "sfx_intro.wav",
          sizeBytes = pad.sizeBytes,
          sha1Hash = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
          bankIndex = 1,
          slotIndex = 24,
          colorArgb = pad.colorArgb,
          displayName = "Intro Boom"
        )
      )
    )

    val scheduledTask = ScheduledPadTask(
      id = "task_1",
      title = "Bell Pembuka",
      targetType = ScheduleTargetType.PAD,
      targetId = "pad_01",
      hour = 19,
      minute = 30,
      repeatMode = ScheduleRepeatMode.DAILY,
      enabled = true,
      volume = 0.9f,
      fadeInMs = 800L,
      autoStopMinutes = 3,
      loopPlayback = false,
      nextTriggerAtMs = 50000L,
      createdAtMs = 1000L
    )

    val playlist = SequencerPlaylist(
      id = "seq_1",
      name = "Medley Pembuka",
      padIds = listOf("pad_01"),
      mode = SequencerPlayMode.REPEAT_ALL,
      gapSeconds = 1.5f,
      crossfadeEnabled = true,
      crossfadeDurationMs = 1800L,
      triggerPadId = "pad_01",
      triggerMidiNote = 48,
      updatedAtMs = 3000L
    )

    val bundle = PersistentConfigBundle(
      meta = ConfigMetaJson(appVersionName = "1.0", appVersionCode = 1),
      settings = SettingsBackupBundle(settings = settings),
      profiles = listOf(profileBundle),
      layouts = listOf(layout),
      presets = PersistentConfigBundleSerializer.deriveDefaultPresets(settings, listOf(profileBundle)),
      midiMapping = MidiMappingBackupBundle(
        focusModeMidiNote = 60,
        ccMappings = listOf(ccMapping),
        pcMappings = listOf(pcMapping),
        padNoteMappings = listOf(
          PadMidiNoteEntry("profile_main", "pad_01", "Intro Boom", "sfx_intro.wav", 24, 1, 36)
        ),
        sequencerNoteMappings = listOf(
          SequencerMidiNoteEntry("seq_1", "Medley Pembuka", 48)
        )
      ),
      scheduler = SchedulerBackupBundle(tasks = listOf(scheduledTask)),
      sequencer = SequencerBackupBundle(playlists = listOf(playlist))
    )

    // 1. Export without waveforms -> lightweight, waveforms omitted
    val lightFiles = PersistentConfigBundleSerializer.exportToFolderFiles(bundle, includeWaveforms = false)
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_CONFIG_META))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_SETTINGS))
    assertTrue(lightFiles.containsKey("profiles/live_concert_show.json"))
    assertTrue(lightFiles.containsKey("layouts/show_malam.json"))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_PRESETS_EQ))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_PRESETS_FX))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_PRESETS_CURVES))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_MIDI_MAPPING))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_SCHEDULER))
    assertTrue(lightFiles.containsKey(PersistentConfigSchema.FILE_SEQUENCER))

    val lightRestored = PersistentConfigBundleSerializer.importFromFolderFiles(lightFiles)
    assertTrue(lightRestored.warnings.isEmpty())
    assertEquals(PersistentConfigSchema.CURRENT_SCHEMA_VERSION, lightRestored.bundle.meta.schemaVersion)
    assertEquals(1, lightRestored.bundle.meta.profileCount)
    assertEquals(1, lightRestored.bundle.meta.padCount)
    assertFalse(lightRestored.bundle.meta.includesWaveforms)

    // Verify waveforms were skipped in light export
    val restoredPadEntry = lightRestored.bundle.profiles.single().pads.single()
    assertTrue(restoredPadEntry.pad.miniWaveform.isEmpty())
    assertEquals(24, restoredPadEntry.slotIndex)
    assertEquals("SFX/sfx_intro.wav", restoredPadEntry.fileMetadata.relativePath)
    assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", restoredPadEntry.fileMetadata.sha1Hash)
    assertTrue(restoredPadEntry.pad.isClone)
    assertTrue(restoredPadEntry.pad.settings.isReversed)
    assertTrue(restoredPadEntry.pad.settings.isLooping)
    assertEquals(FadeCurve.S_CURVE, restoredPadEntry.pad.settings.fadeCurve)
    assertEquals(120L, restoredPadEntry.pad.settings.trimStartMs)
    assertEquals(4500L, restoredPadEntry.pad.settings.trimEndMs)
    assertEquals(0.45f, restoredPadEntry.pad.settings.pan, 0.001f)
    assertEquals(FxPreset.HALL, restoredPadEntry.pad.settings.fxPreset)
    assertEquals(EqPreset.VOCAL, restoredPadEntry.pad.settings.eqPreset)

    // Verify GlobalSettings + audio output + buffer size + sync config
    val restoredSettings = lightRestored.bundle.settings.settings
    assertEquals(PlayMode.MULTI, restoredSettings.playMode)
    assertEquals(ThemeMode.AMOLED, restoredSettings.themeMode)
    assertEquals(DefaultPadColorMode.CUSTOM, restoredSettings.defaultPadColorMode)
    assertEquals(0xFF3949ABL, restoredSettings.defaultPadColorArgb)
    assertTrue(restoredSettings.isFocusMode)
    assertTrue(restoredSettings.startInFocusMode)
    assertFalse(restoredSettings.focusCornerPanicEnabled)
    assertEquals(60, restoredSettings.focusModeMidiNote)
    assertEquals(42, restoredSettings.preferredMainOutputId)
    assertEquals("USB Mixer L/R", restoredSettings.preferredMainOutputName)
    assertEquals(43, restoredSettings.preferredCueOutputId)
    assertEquals("Wired Headphones", restoredSettings.preferredCueOutputName)
    assertEquals(32, restoredSettings.audioBufferSizeMs)
    assertEquals("SoundPadPro", restoredSettings.persistentBackupFolderName)

    // Verify MIDI, Scheduler, Sequencer, Layouts, Presets
    assertEquals(1, lightRestored.bundle.midiMapping.ccMappings.size)
    assertEquals(1, lightRestored.bundle.midiMapping.pcMappings.size)
    assertEquals(7, lightRestored.bundle.midiMapping.pcMappings.single().programNumber)
    assertEquals(1, lightRestored.bundle.scheduler.tasks.size)
    assertEquals("Bell Pembuka", lightRestored.bundle.scheduler.tasks.single().title)
    assertEquals(1, lightRestored.bundle.sequencer.playlists.size)
    assertEquals("Medley Pembuka", lightRestored.bundle.sequencer.playlists.single().name)
    assertEquals(24, lightRestored.bundle.layouts.single().slots.single().slotIndex)
    assertTrue(lightRestored.bundle.presets.eqPresets.any { it.profileId == "profile_main" })

    // 2. Combined JSON (.soundpadpro/config.json) with waveforms enabled
    val combinedJson = PersistentConfigBundleSerializer.exportCombinedJson(bundle, includeWaveforms = true)
    val combinedRestored = PersistentConfigBundleSerializer.importCombinedJson(combinedJson)
    assertTrue(combinedRestored.bundle.meta.includesWaveforms)
    assertEquals(4, combinedRestored.bundle.profiles.single().pads.single().pad.miniWaveform.size)
  }

  @Test
  fun `schema migrator upgrades legacy v1 files and handles corrupt sections without crash`() {
    val v1Meta = JSONObject(
      """
      {
        "backupSchemaVersion": 1,
        "exportedAtMs": 1700000000000,
        "profileCount": 1,
        "padCount": 1
      }
      """.trimIndent()
    )
    val v1Settings = JSONObject(
      """
      {
        "schemaVersion": 1,
        "nightMode": true,
        "playMode": "CONTINUOUS",
        "gridPreset": "GRID_4X4",
        "gridRows": 4,
        "gridCols": 4,
        "audioBufferSizeMs": 64
      }
      """.trimIndent()
    )
    val v1Profile = JSONObject(
      """
      {
        "schemaVersion": 1,
        "id": "legacy_prof",
        "name": "Legacy Pack",
        "bankNames": { "0": "Drums" },
        "pads": [
          {
            "id": "p_old",
            "uriString": "content://old/kick.wav",
            "fileName": "kick.wav",
            "displayName": "Kick",
            "extension": "WAV",
            "positionIndex": 5,
            "subfolderName": "Drums",
            "sizeBytes": 12000
          }
        ]
      }
      """.trimIndent()
    )
    val v1Layout = JSONObject(
      """
      {
        "schemaVersion": 1,
        "id": "old_lay",
        "name": "Old Layout",
        "positions": [
          { "padId": "p_old", "fileUri": "content://old/kick.wav", "fileName": "kick.wav", "positionIndex": 5 }
        ]
      }
      """.trimIndent()
    )
    val v1Midi = JSONObject(
      """
      {
        "schemaVersion": 1,
        "mappings": [
          { "profileId": "legacy_prof", "target": "STOP_ALL", "ccNumber": 120, "channel": -1 }
        ]
      }
      """.trimIndent()
    )
    val v1Scheduler = JSONObject(
      """
      {
        "schemaVersion": 1,
        "schedules": [
          { "id": "s1", "title": "Alarm", "targetType": "PAD", "targetId": "p_old", "hour": 8, "minute": 0 }
        ]
      }
      """.trimIndent()
    )
    val v1Sequencer = JSONObject(
      """
      {
        "schemaVersion": 1,
        "sequences": [
          { "id": "q1", "name": "Intro Set", "padIds": ["p_old"], "mode": "SEQUENTIAL" }
        ]
      }
      """.trimIndent()
    )

    val result = PersistentConfigBundleSerializer.importFromFolderFiles(
      mapOf(
        PersistentConfigSchema.FILE_CONFIG_META to v1Meta.toString(),
        PersistentConfigSchema.FILE_SETTINGS to v1Settings.toString(),
        "profiles/legacy_pack.json" to v1Profile.toString(),
        "layouts/old_layout.json" to v1Layout.toString(),
        PersistentConfigSchema.FILE_MIDI_MAPPING to v1Midi.toString(),
        PersistentConfigSchema.FILE_SCHEDULER to v1Scheduler.toString(),
        PersistentConfigSchema.FILE_SEQUENCER to v1Sequencer.toString(),
        "profiles/corrupt.json" to "{not valid json"
      )
    )

    assertEquals(1, result.migratedFromVersion)
    assertEquals(1, result.warnings.size)
    assertTrue(result.warnings.first().contains("corrupt.json"))

    assertEquals(ThemeMode.DARK, result.bundle.settings.settings.themeMode)
    assertEquals(PlayMode.CONTINUOUS, result.bundle.settings.settings.playMode)
    assertEquals(DefaultPadColorMode.GRAY, result.bundle.settings.settings.defaultPadColorMode)
    assertEquals(64, result.bundle.settings.settings.audioBufferSizeMs)

    val restoredProfile = result.bundle.profiles.single()
    assertEquals(1, restoredProfile.banks.size)
    assertEquals("Drums", restoredProfile.banks.single().name)
    assertEquals(5, restoredProfile.pads.single().slotIndex)
    assertEquals("Drums/kick.wav", restoredProfile.pads.single().fileMetadata.relativePath)

    assertEquals(5, result.bundle.layouts.single().slots.single().slotIndex)
    assertEquals(MidiCcTarget.STOP_ALL, result.bundle.midiMapping.ccMappings.single().target)
    assertEquals("Alarm", result.bundle.scheduler.tasks.single().title)
    assertEquals("Intro Set", result.bundle.sequencer.playlists.single().name)
  }

  @Test
  fun `backupSectionRegistry automatically exports and imports future Backupable contributors`() {
    var capturedValue = "unlocked"
    val contributor = object : BackupSectionContributor {
      override val sectionKey: String = "future_lock_layout"
      override val schemaVersion: Int = 1

      override fun exportSectionJson(): JSONObject = JSONObject().apply {
        put("layoutLock", true)
        put("activeLayoutId", "layout_show_2")
      }

      override fun importSectionJson(json: JSONObject, fromVersion: Int) {
        val lock = json.optBoolean("layoutLock", false)
        val layoutId = json.optString("activeLayoutId", "")
        capturedValue = "${lock}_$layoutId"
      }
    }

    BackupSectionRegistry.register(contributor)
    try {
      val files = PersistentConfigBundleSerializer.exportToFolderFiles(PersistentConfigBundle())
      assertTrue(files.containsKey("extensions/future_lock_layout.json"))

      PersistentConfigBundleSerializer.importFromFolderFiles(files)
      assertEquals("true_layout_show_2", capturedValue)

      val combined = PersistentConfigBundleSerializer.exportCombinedJson(PersistentConfigBundle())
      capturedValue = "reset"
      PersistentConfigBundleSerializer.importCombinedJson(combined)
      assertEquals("true_layout_show_2", capturedValue)
    } finally {
      BackupSectionRegistry.unregister("future_lock_layout")
    }
  }

  @Test
  fun `snapshot assembler captures all Room tables into PersistentConfigBundle and round-trips`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      dao.upsertProfile(
        PadProfileEntity(
          id = "prof_db_1",
          name = "Church Service",
          folderUri = "content://tree/church",
          gridPreset = GridPreset.GRID_4X4.name,
          gridRows = 4,
          gridCols = 4,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(mapOf(0 to "Praise")),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(listOf(0)),
          createdAtMs = 100L,
          updatedAtMs = 200L,
          isActive = true
        )
      )
      dao.upsertPad(
        SoundPadEntity(
          id = "pad_db_1",
          profileId = "prof_db_1",
          uriString = "content://tree/church/amen.wav",
          fileName = "amen.wav",
          displayName = "Amen Pad",
          extension = "WAV",
          durationMs = 3400L,
          sizeBytes = 500000L,
          positionIndex = 3,
          bankIndex = 0,
          subfolderName = "Praise",
          glowIntensity = 0f,
          midiNote = 62
        )
      )
      dao.upsertPadSetting(
        PadSettingEntity(
          padId = "pad_db_1",
          volume = 0.9f,
          pan = -0.3f,
          isLooping = true,
          isReversed = true,
          eqEnabled = true,
          eqPreset = EqPreset.BASS_BOOST.name,
          eqBandsJson = ProfileJsonSerializer.encodeEqBandsString(EqPreset.BASS_BOOST.gainsDb)
        )
      )
      dao.upsertMidiCcMapping(
        MidiCcMappingEntity(
          id = "cc_db_1",
          profileId = "prof_db_1",
          target = MidiCcTarget.MASTER_VOLUME.name,
          ccNumber = 7,
          channel = 0
        )
      )
      dao.upsertMidiPcMapping(
        MidiPcBankMappingEntity(
          id = "pc_db_1",
          profileId = "prof_db_1",
          programNumber = 3,
          bankIndex = 0,
          channel = 0
        )
      )
      dao.upsertScheduledTask(
        ScheduledPadTask(
          id = "sched_db_1",
          title = "Opening Chime",
          targetType = ScheduleTargetType.PAD,
          targetId = "pad_db_1",
          hour = 9,
          minute = 0
        ).toEntity()
      )
      dao.upsertSequencerPlaylist(
        SequencerPlaylist(
          id = "seq_db_1",
          name = "Worship Flow",
          padIds = listOf("pad_db_1"),
          mode = SequencerPlayMode.SEQUENTIAL
        ).toEntity()
      )

      val settings = GlobalSettings(
        activeProfileId = "prof_db_1",
        audioBufferSizeMs = 40,
        preferredMainOutputId = 12,
        preferredMainOutputName = "Phone Speaker"
      )

      val bundle = PersistentConfigSnapshotAssembler.captureFromDatabase(
        dao = dao,
        settings = settings,
        context = context,
        computeFileHashes = false
      )

      val folderMap = PersistentConfigBundleSerializer.exportToFolderFiles(bundle)
      val restored = PersistentConfigBundleSerializer.importFromFolderFiles(folderMap)
      assertTrue(restored.warnings.isEmpty())
      assertEquals(1, restored.bundle.profiles.size)
      assertEquals("Church Service", restored.bundle.profiles.single().profile.name)
      assertEquals(3, restored.bundle.profiles.single().pads.single().slotIndex)
      assertEquals(-0.3f, restored.bundle.profiles.single().pads.single().pad.settings.pan, 0.001f)
      assertEquals(1, restored.bundle.midiMapping.ccMappings.size)
      assertEquals(1, restored.bundle.midiMapping.pcMappings.size)
      assertEquals(1, restored.bundle.midiMapping.padNoteMappings.size)
      assertEquals(1, restored.bundle.scheduler.tasks.size)
      assertEquals(1, restored.bundle.sequencer.playlists.size)
      assertEquals(40, restored.bundle.settings.settings.audioBufferSizeMs)

      // Also verify legacy FullBackupSerializer remains compatible
      val legacyCompatJson = FullBackupSerializer.exportBackupJson(
        settings = settings,
        profiles = listOf(ExportedProfileBundle(bundle.profiles.single().profile, listOf(bundle.profiles.single().pads.single().pad)))
      )
      val importedViaBundle = PersistentConfigBundleSerializer.importCombinedJson(legacyCompatJson)
      assertEquals("Church Service", importedViaBundle.bundle.profiles.single().profile.name)
      assertEquals(40, importedViaBundle.bundle.settings.settings.audioBufferSizeMs)
    } finally {
      db.close()
    }
  }
}
