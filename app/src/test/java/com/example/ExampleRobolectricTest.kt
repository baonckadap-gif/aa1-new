package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioFileValidator
import com.example.audio.AudioMixMath
import com.example.audio.MasterMixPcmMath
import com.example.audio.SequencerQueueMath
import com.example.audio.StereoPanMath
import com.example.audio.toDomain
import com.example.audio.toEntity
import com.example.data.backup.FullBackupSerializer
import com.example.data.demo.DemoTrackManager
import com.example.data.local.db.ErrorLogEntity
import com.example.data.local.db.MidiPcBankMappingEntity
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.db.SoundPadEntity
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.AppErrorLog
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
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
import com.example.domain.model.toVisualConfig
import com.example.error.CrashLogManager
import com.example.midi.MidiEvent
import com.example.midi.MidiMessageParser
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.toDomain
import com.example.scheduler.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SoundPad Pro", appName)
  }

  @Test
  fun `profile json export and import preserves cloned pads shapes glow and trim settings`() {
    val profile = PadProfile(
      id = "profile_live_1",
      name = "Live Show Set",
      folderUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
      gridConfig = GridConfig(GridPreset.GRID_10X10, 10, 10),
      padCount = 2,
      createdAtMs = 1000L,
      updatedAtMs = 2000L,
      isActive = true
    )
    val originalPad = SoundPad(
      id = "pad_1",
      uriString = "content://track/lagu1.mp3",
      fileName = "lagu1.mp3",
      displayName = "Lagu 1",
      extension = "MP3",
      durationMs = 180_000L,
      sizeBytes = 4_000_000L,
      positionIndex = 0,
      colorArgb = 0xFF00E5FF,
      glowColorArgb = 0xFF10B981,
      glowIntensity = 1.6f,
      shapeStyle = PadShapeStyle.STAR,
      borderColorArgb = 0xFFFFFFFF,
      borderWidthDp = 3.0f,
      cornerRadiusDp = 22f,
      textColorArgb = 0xFF00E5FF,
      surfaceFinish = PadSurfaceFinish.NEON_GRADIENT,
      badgeIcon = PadBadgeIcon.BOLT,
      showSlotNumber = false,
      showDurationLabel = true,
      isClone = false,
      midiNote = 36,
      settings = PadSettings(
        volume = 0.9f,
        speed = 1.0f,
        fadeInMs = 300L,
        fadeCurve = FadeCurve.S_CURVE,
        trimStartMs = 1500L,
        trimEndMs = 45_000L
      )
    )
    val clonedPad = SoundPad(
      id = "pad_clone_1",
      uriString = "content://track/lagu1.mp3",
      fileName = "lagu1.mp3",
      displayName = "Lagu 1 (Clone)",
      extension = "MP3",
      durationMs = 180_000L,
      sizeBytes = 4_000_000L,
      positionIndex = 1,
      colorArgb = 0xFFFFB300,
      glowColorArgb = 0xFFEC4899,
      glowIntensity = 0.4f,
      shapeStyle = PadShapeStyle.PRISM,
      borderColorArgb = 0xFFEC4899,
      borderWidthDp = 0.5f,
      cornerRadiusDp = 8f,
      textColorArgb = 0xFF0B1120,
      surfaceFinish = PadSurfaceFinish.GLASS,
      badgeIcon = PadBadgeIcon.MIC,
      showSlotNumber = true,
      showDurationLabel = false,
      isClone = true,
      midiNote = 37,
      settings = PadSettings(
        volume = 0.85f,
        speed = 2.0f,
        isLooping = true,
        triggerMode = PadTriggerMode.LOOP,
        chokeGroup = 2,
        fadeOutMs = 1200L,
        fadeCurve = FadeCurve.EXPONENTIAL,
        crossfadeEnabled = false,
        trimStartMs = 0L,
        trimEndMs = 90_000L
      )
    )

    val json = ProfileJsonSerializer.exportToJson(profile, listOf(originalPad, clonedPad))
    val imported = ProfileJsonSerializer.importFromJson(json)

    assertEquals("Live Show Set", imported.profile.name)
    assertEquals(GridPreset.GRID_10X10, imported.profile.gridConfig.preset)
    assertEquals(10, imported.profile.gridConfig.rows)
    assertEquals(10, imported.profile.gridConfig.cols)
    assertEquals(2, imported.pads.size)

    val p0 = imported.pads[0]
    assertEquals("Lagu 1", p0.displayName)
    assertFalse(p0.isClone)
    assertEquals(1.0f, p0.settings.speed, 0.001f)
    assertEquals(36, p0.midiNote)
    assertEquals(FadeCurve.S_CURVE, p0.settings.fadeCurve)
    assertEquals(0xFF10B981, p0.glowColorArgb)
    assertEquals(1.6f, p0.glowIntensity, 0.001f)
    assertEquals(PadShapeStyle.STAR, p0.shapeStyle)
    assertEquals(0xFFFFFFFF, p0.borderColorArgb)
    assertEquals(3.0f, p0.borderWidthDp, 0.001f)
    assertEquals(22f, p0.cornerRadiusDp, 0.001f)
    assertEquals(0xFF00E5FF, p0.textColorArgb)
    assertEquals(PadSurfaceFinish.NEON_GRADIENT, p0.surfaceFinish)
    assertEquals(PadBadgeIcon.BOLT, p0.badgeIcon)
    assertFalse(p0.showSlotNumber)
    assertTrue(p0.showDurationLabel)
    assertEquals(1500L, p0.settings.trimStartMs)
    assertEquals(45_000L, p0.settings.trimEndMs)
    assertEquals(PadBadgeIcon.BOLT, p0.toVisualConfig().badgeIcon)

    val p1 = imported.pads[1]
    assertEquals("Lagu 1 (Clone)", p1.displayName)
    assertTrue(p1.isClone)
    assertEquals(2.0f, p1.settings.speed, 0.001f)
    assertTrue(p1.settings.isLooping)
    assertEquals(PadTriggerMode.LOOP, p1.settings.triggerMode)
    assertEquals(2, p1.settings.chokeGroup)
    assertEquals(1200L, p1.settings.fadeOutMs)
    assertEquals(FadeCurve.EXPONENTIAL, p1.settings.fadeCurve)
    assertFalse(p1.settings.crossfadeEnabled)
    assertEquals(0xFFFFB300, p1.colorArgb)
    assertEquals(0xFFEC4899, p1.glowColorArgb)
    assertEquals(0.4f, p1.glowIntensity, 0.001f)
    assertEquals(PadShapeStyle.PRISM, p1.shapeStyle)
    assertEquals(0xFFEC4899, p1.borderColorArgb)
    assertEquals(0.5f, p1.borderWidthDp, 0.001f)
    assertEquals(8f, p1.cornerRadiusDp, 0.001f)
    assertEquals(0xFF0B1120, p1.textColorArgb)
    assertEquals(PadSurfaceFinish.GLASS, p1.surfaceFinish)
    assertEquals(PadBadgeIcon.MIC, p1.badgeIcon)
    assertTrue(p1.showSlotNumber)
    assertFalse(p1.showDurationLabel)
    assertEquals(37, p1.midiNote)
    assertEquals(0L, p1.settings.trimStartMs)
    assertEquals(90_000L, p1.settings.trimEndMs)
  }

  @Test
  fun `demo track manager installs valid playable wav files`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val demoPads = DemoTrackManager.ensureDemoTracksInstalled(context)

    assertEquals(6, demoPads.size)
    assertEquals("Kick & Bass Beat", demoPads.first().displayName)
    assertTrue(demoPads.all { it.extension == "WAV" && it.sizeBytes > 44L && it.durationMs > 0L })

    val demoDir = DemoTrackManager.getDemoDirectory(context)
    val firstFile = File(demoDir, demoPads.first().fileName)
    assertTrue(firstFile.exists())
    val header = firstFile.inputStream().use { input ->
      ByteArray(4).also { buf -> input.read(buf) }
    }
    assertEquals("RIFF", String(header, Charsets.US_ASCII))
  }

  @Test
  fun `pad shape styles include all four required shapes`() {
    val shapes = PadShapeStyle.entries
    assertEquals(
      listOf(PadShapeStyle.SQUARE, PadShapeStyle.TRIANGLE, PadShapeStyle.STAR, PadShapeStyle.PRISM),
      shapes
    )
  }

  @Test
  fun `midi message parser decodes note on note off velocity zero and control change`() {
    val packet = byteArrayOf(
      0x90.toByte(), 36, 110,        // Note On ch0 note 36 vel 110
      0x90.toByte(), 36, 0,          // Note On vel 0 -> Note Off
      0x82.toByte(), 48, 64,         // Note Off ch2 note 48
      0xB1.toByte(), 7, 99,          // CC #7 = 99 on ch1
      0xF0.toByte(), 0x7E, 0x7F, 0xF7.toByte(), // SysEx skipped cleanly
      0xB1.toByte(), 74, 127         // CC #74 = 127 on ch1
    )

    val events = MidiMessageParser.parse(packet, 0, packet.size)
    assertEquals(5, events.size)
    assertEquals(MidiEvent.NoteOn(channel = 0, note = 36, velocity = 110), events[0])
    assertEquals(MidiEvent.NoteOff(channel = 0, note = 36), events[1])
    assertEquals(MidiEvent.NoteOff(channel = 2, note = 48), events[2])
    assertEquals(MidiEvent.ControlChange(channel = 1, controller = 7, value = 99), events[3])
    assertEquals(MidiEvent.ControlChange(channel = 1, controller = 74, value = 127), events[4])
    assertEquals("C2 (36)", MidiMessageParser.formatNoteName(36))
  }

  @Test
  fun `profile json round trip preserves midi cc mappings`() {
    val profile = PadProfile(
      id = "profile_midi",
      name = "MIDI Live",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0,
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )
    val ccList = listOf(
      MidiCcMapping(
        id = "cc_1",
        profileId = "profile_midi",
        target = MidiCcTarget.MASTER_VOLUME,
        ccNumber = 7,
        channel = 0
      ),
      MidiCcMapping(
        id = "cc_2",
        profileId = "profile_midi",
        target = MidiCcTarget.PAD_SPEED,
        ccNumber = 14,
        channel = -1
      )
    )

    val json = ProfileJsonSerializer.exportToJson(profile, emptyList(), ccList)
    val imported = ProfileJsonSerializer.importFromJson(json)

    assertEquals(2, imported.ccMappings.size)
    assertEquals(MidiCcTarget.MASTER_VOLUME, imported.ccMappings[0].target)
    assertEquals(7, imported.ccMappings[0].ccNumber)
    assertEquals(0, imported.ccMappings[0].channel)
    assertEquals(MidiCcTarget.PAD_SPEED, imported.ccMappings[1].target)
    assertEquals(14, imported.ccMappings[1].ccNumber)
    assertEquals(-1, imported.ccMappings[1].channel)
  }

  @Test
  fun `audio mix math applies master limiter ducking and loudness normalization`() {
    val singleGain = AudioMixMath.computeSlotGain(
      masterVolume = 1.0f,
      padVolume = 1.0f,
      envelopeGain = 1.0f,
      normalizedGain = 1.0f,
      autoNormalizeEnabled = false,
      limiterEnabled = true,
      activeSlotCount = 1,
      duckingMultiplier = 1.0f
    )
    assertEquals(1.0f, singleGain, 0.001f)

    val polyLimitedGain = AudioMixMath.computeSlotGain(
      masterVolume = 1.0f,
      padVolume = 1.0f,
      envelopeGain = 1.0f,
      normalizedGain = 1.0f,
      autoNormalizeEnabled = false,
      limiterEnabled = true,
      activeSlotCount = 4,
      duckingMultiplier = 1.0f
    )
    assertTrue(polyLimitedGain < 0.7f)

    val duckedGain = AudioMixMath.computeSlotGain(
      masterVolume = 1.0f,
      padVolume = 0.8f,
      envelopeGain = 1.0f,
      normalizedGain = 1.25f,
      autoNormalizeEnabled = true,
      limiterEnabled = false,
      activeSlotCount = 2,
      duckingMultiplier = 0.25f
    )
    assertEquals(0.25f, duckedGain, 0.001f)
  }

  @Test
  fun `profile json round trip preserves bank names favorites category tags ducking and normalization`() {
    val profile = PadProfile(
      id = "profile_stage",
      name = "Stage Set",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      bankNames = mapOf(0 to "Intro", 1 to "Drop"),
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )
    val pad = SoundPad(
      id = "pad_fav_1",
      uriString = "content://track/vox.wav",
      fileName = "vox.wav",
      displayName = "Host Mic",
      extension = "WAV",
      durationMs = 5000L,
      sizeBytes = 1000L,
      positionIndex = 0,
      isFavorite = true,
      categoryTag = "Vocal",
      settings = PadSettings(
        isDuckingSource = true,
        normalizedGain = 1.32f
      )
    )

    val json = ProfileJsonSerializer.exportToJson(profile, listOf(pad))
    val imported = ProfileJsonSerializer.importFromJson(json)

    assertEquals("Intro", imported.profile.bankNames[0])
    assertEquals("Drop", imported.profile.bankNames[1])
    assertEquals(1, imported.pads.size)
    assertTrue(imported.pads[0].isFavorite)
    assertEquals("Vocal", imported.pads[0].categoryTag)
    assertTrue(imported.pads[0].settings.isDuckingSource)
    assertEquals(1.32f, imported.pads[0].settings.normalizedGain, 0.001f)
  }

  @Test
  fun `full backup serializer restores global settings profiles and midi mappings`() {
    val globalSettings = GlobalSettings(
      activeProfileId = "prof_main",
      gridConfig = GridConfig(GridPreset.GRID_10X10, 10, 10),
      playMode = PlayMode.MULTI,
      masterVolume = 0.85f,
      themeMode = ThemeMode.LIGHT,
      masterLimiterEnabled = true,
      duckingEnabled = true,
      duckingAttenuation = 0.25f,
      autoNormalizeEnabled = true,
      isLocked = true
    )
    val profile = PadProfile(
      id = "prof_main",
      name = "Main Gig",
      folderUri = DemoTrackManager.DEMO_FOLDER_URI,
      gridConfig = GridConfig(GridPreset.GRID_10X10, 10, 10),
      padCount = 1,
      bankNames = mapOf(0 to "Warmup"),
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )
    val pad = SoundPad(
      id = "pad_main_1",
      uriString = "file:///demo/kick.wav",
      fileName = "kick.wav",
      displayName = "Kick",
      extension = "WAV",
      durationMs = 2000L,
      sizeBytes = 500L,
      positionIndex = 0,
      isFavorite = true,
      categoryTag = "Beat",
      settings = PadSettings(normalizedGain = 0.88f)
    )
    val cc = MidiCcMapping(
      id = "cc_prof_main_MASTER_VOLUME",
      profileId = "prof_main",
      target = MidiCcTarget.MASTER_VOLUME,
      ccNumber = 7,
      channel = 0
    )

    val backupJson = FullBackupSerializer.exportBackupJson(
      settings = globalSettings,
      profiles = listOf(ExportedProfileBundle(profile, listOf(pad), listOf(cc)))
    )
    val restored = FullBackupSerializer.importBackupJson(backupJson)

    assertEquals("prof_main", restored.globalSettings.activeProfileId)
    assertEquals(PlayMode.MULTI, restored.globalSettings.playMode)
    assertEquals(ThemeMode.LIGHT, restored.globalSettings.themeMode)
    assertEquals(0.85f, restored.globalSettings.masterVolume, 0.001f)
    assertTrue(restored.globalSettings.masterLimiterEnabled)
    assertTrue(restored.globalSettings.duckingEnabled)
    assertEquals(0.25f, restored.globalSettings.duckingAttenuation, 0.001f)
    assertTrue(restored.globalSettings.autoNormalizeEnabled)
    assertTrue(restored.globalSettings.isLocked)

    assertEquals(1, restored.profiles.size)
    assertEquals("Main Gig", restored.profiles[0].profile.name)
    assertEquals("Warmup", restored.profiles[0].profile.bankNames[0])
    assertEquals("Beat", restored.profiles[0].pads[0].categoryTag)
    assertEquals(0.88f, restored.profiles[0].pads[0].settings.normalizedGain, 0.001f)
    assertEquals(1, restored.profiles[0].ccMappings.size)
  }

  @Test
  fun `stereo pan math computes equal power channel gains and formats labels`() {
    val (cLeft, cRight) = StereoPanMath.computeChannelGains(0.0f)
    assertEquals(1.0f, cLeft, 0.001f)
    assertEquals(1.0f, cRight, 0.001f)

    val (lLeft, lRight) = StereoPanMath.computeChannelGains(-1.0f)
    assertEquals(1.0f, lLeft, 0.01f)
    assertEquals(0.0f, lRight, 0.01f)

    val (rLeft, rRight) = StereoPanMath.computeChannelGains(1.0f)
    assertEquals(0.0f, rLeft, 0.01f)
    assertEquals(1.0f, rRight, 0.01f)

    assertEquals(1.0f, StereoPanMath.combinePan(0.7f, 0.8f), 0.001f)
    assertEquals(-1.0f, StereoPanMath.combinePan(-0.6f, -0.7f), 0.001f)
    assertEquals("Center", StereoPanMath.formatPanLabel(0.01f))
    assertEquals("L 50", StereoPanMath.formatPanLabel(-0.5f))
    assertEquals("R 75", StereoPanMath.formatPanLabel(0.75f))
  }

  @Test
  fun `audio file validator detects valid headers and corrupt or truncated files in Indonesian`() {
    val validWav = ByteArray(48).also {
      "RIFF".toByteArray().copyInto(it, 0)
      "WAVE".toByteArray().copyInto(it, 8)
    }
    assertTrue(AudioFileValidator.validateHeaderBytes(validWav, 1024L, "wav").isValid)

    val validMp3 = ByteArray(32).also {
      "ID3".toByteArray().copyInto(it, 0)
    }
    assertTrue(AudioFileValidator.validateHeaderBytes(validMp3, 2048L, "mp3").isValid)

    val validFlac = ByteArray(24).also {
      "fLaC".toByteArray().copyInto(it, 0)
    }
    assertTrue(AudioFileValidator.validateHeaderBytes(validFlac, 4096L, "flac").isValid)

    val corruptWav = "BADHDR1234567890".toByteArray()
    val badWavRes = AudioFileValidator.validateHeaderBytes(corruptWav, 512L, "wav")
    assertFalse(badWavRes.isValid)
    assertEquals(PadErrorType.CORRUPT_HEADER, badWavRes.errorType)
    assertTrue(badWavRes.message!!.contains("Header WAV tidak valid"))

    val truncated = ByteArray(8)
    val truncRes = AudioFileValidator.validateHeaderBytes(truncated, 8L, "mp3")
    assertFalse(truncRes.isValid)
    assertEquals(PadErrorType.CORRUPT_HEADER, truncRes.errorType)
  }

  @Test
  fun `profile json round trip preserves 8A pan subfolder banks and mini waveform codec`() {
    val profile = PadProfile(
      id = "prof_sub_8a",
      name = "Subfolder Bank Set",
      folderUri = "content://tree/Music",
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      bankNames = mapOf(0 to "Drums", 1 to "Synth"),
      bankOrder = listOf(1, 0),
      bankFolderUris = mapOf(0 to "content://doc/Drums", 1 to "content://doc/Synth"),
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )
    val pad = SoundPad(
      id = "pad_pan_1",
      uriString = "content://doc/Drums/snare.wav",
      fileName = "snare.wav",
      displayName = "Snare",
      extension = "WAV",
      durationMs = 1200L,
      sizeBytes = 64000L,
      positionIndex = 0,
      bankIndex = 0,
      subfolderName = "Drums",
      settings = PadSettings(pan = -0.65f)
    )

    val json = ProfileJsonSerializer.exportToJson(profile, listOf(pad))
    val imported = ProfileJsonSerializer.importFromJson(json)

    assertEquals(listOf(1, 0), imported.profile.bankOrder)
    assertEquals("content://doc/Drums", imported.profile.bankFolderUris[0])
    assertEquals("content://doc/Synth", imported.profile.bankFolderUris[1])
    assertEquals(0, imported.pads[0].bankIndex)
    assertEquals("Drums", imported.pads[0].subfolderName)
    assertEquals(-0.65f, imported.pads[0].settings.pan, 0.001f)

    val bars = listOf(0.1f, 0.45f, 0.9f, 0.25f)
    val encoded = ProfileJsonSerializer.encodeMiniWaveform(bars)
    val decoded = ProfileJsonSerializer.decodeMiniWaveform(encoded)
    assertEquals(bars.size, decoded.size)
    assertEquals(0.45f, decoded[1], 0.01f)
  }

  @Test
  fun `crash log manager persists pending crash consumes once and formats txt export`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    CrashLogManager.persistPendingCrash(
      context = context,
      threadName = "audio-thread",
      throwable = IllegalStateException("Test ExoPlayer OOM")
    )

    val consumed = CrashLogManager.consumePendingCrash(context)
    assertNotNull(consumed)
    assertEquals(PadErrorType.APP_CRASH, consumed!!.errorType)
    assertTrue(consumed.message.contains("IllegalStateException"))
    assertTrue(consumed.message.contains("Test ExoPlayer OOM"))

    assertNull(CrashLogManager.consumePendingCrash(context))

    val txt = CrashLogManager.formatErrorLogsAsTxt(listOf(consumed))
    assertTrue(txt.contains("SoundPad Pro - Error & Crash Diagnostic Log"))
    assertTrue(txt.contains("Crash Aplikasi"))
    assertTrue(txt.contains("Test ExoPlayer OOM"))
  }

  @Test
  fun `room database stores and clears error logs in v9 schema`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      val err = ErrorLogEntity(
        id = "err_1",
        timestampMs = 123456L,
        fileName = "broken.mp3",
        uriString = "content://broken",
        errorType = PadErrorType.CORRUPT_HEADER.name,
        message = "Header MP3 rusak",
        suggestion = PadErrorType.CORRUPT_HEADER.defaultSuggestion,
        stackTrace = "trace",
        padId = "pad_x"
      )
      dao.insertErrorLog(err)
      val logs = dao.getErrorLogsOnce()
      assertEquals(1, logs.size)
      assertEquals("broken.mp3", logs[0].fileName)

      dao.clearErrorLogs()
      assertTrue(dao.getErrorLogsOnce().isEmpty())
    } finally {
      db.close()
    }
  }

  @Test
  fun `profile json and full backup preserve master eq pad eq reverse and reverb echo fx`() {
    val masterBands = listOf(4f, 2f, -1f, 3f, 6f)
    val padBands = listOf(-4f, 0f, 5f, 4f, 1f)
    val globalSettings = GlobalSettings(
      activeProfileId = "prof_8b",
      masterEqEnabled = true,
      masterEqPreset = EqPreset.CUSTOM,
      masterEqBandsDb = masterBands,
      audioEffectsEnabled = false,
      visualEffectsEnabled = false
    )
    val profile = PadProfile(
      id = "prof_8b",
      name = "DSP Studio",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      masterEqEnabled = true,
      masterEqPreset = EqPreset.CUSTOM,
      masterEqBandsDb = masterBands,
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )
    val pad = SoundPad(
      id = "pad_8b_1",
      uriString = "content://track/lead.wav",
      fileName = "lead.wav",
      displayName = "Lead Synth",
      extension = "WAV",
      durationMs = 4000L,
      sizeBytes = 128_000L,
      positionIndex = 0,
      settings = PadSettings(
        eqEnabled = true,
        eqPreset = EqPreset.VOCAL,
        eqBandsDb = padBands,
        isReversed = true,
        fxPreset = FxPreset.CUSTOM,
        echoEnabled = true,
        echoDelayMs = 340f,
        echoFeedback = 0.48f,
        echoMix = 0.42f,
        reverbEnabled = true,
        reverbRoomSize = 0.78f,
        reverbDamping = 0.32f,
        reverbMix = 0.45f
      )
    )

    val profileJson = ProfileJsonSerializer.exportToJson(profile, listOf(pad))
    val importedProfile = ProfileJsonSerializer.importFromJson(profileJson)

    assertTrue(importedProfile.profile.masterEqEnabled)
    assertEquals(EqPreset.CUSTOM, importedProfile.profile.masterEqPreset)
    assertEquals(masterBands, importedProfile.profile.masterEqBandsDb)
    val p = importedProfile.pads.first()
    assertTrue(p.settings.eqEnabled)
    assertEquals(EqPreset.VOCAL, p.settings.eqPreset)
    assertEquals(padBands, p.settings.eqBandsDb)
    assertTrue(p.settings.isReversed)
    assertEquals(FxPreset.CUSTOM, p.settings.fxPreset)
    assertTrue(p.settings.echoEnabled)
    assertEquals(340f, p.settings.echoDelayMs, 0.01f)
    assertEquals(0.48f, p.settings.echoFeedback, 0.01f)
    assertEquals(0.42f, p.settings.echoMix, 0.01f)
    assertTrue(p.settings.reverbEnabled)
    assertEquals(0.78f, p.settings.reverbRoomSize, 0.01f)
    assertEquals(0.32f, p.settings.reverbDamping, 0.01f)
    assertEquals(0.45f, p.settings.reverbMix, 0.01f)

    val backupJson = FullBackupSerializer.exportBackupJson(
      settings = globalSettings,
      profiles = listOf(ExportedProfileBundle(profile, listOf(pad), emptyList()))
    )
    val restoredBackup = FullBackupSerializer.importBackupJson(backupJson)
    assertTrue(restoredBackup.globalSettings.masterEqEnabled)
    assertEquals(EqPreset.CUSTOM, restoredBackup.globalSettings.masterEqPreset)
    assertEquals(masterBands, restoredBackup.globalSettings.masterEqBandsDb)
    assertTrue(!restoredBackup.globalSettings.audioEffectsEnabled)
    assertTrue(!restoredBackup.globalSettings.visualEffectsEnabled)
    assertTrue(restoredBackup.profiles.first().pads.first().settings.isReversed)
    assertTrue(restoredBackup.profiles.first().pads.first().settings.echoEnabled)
  }

  @Test
  fun `room v10 stores eq reverse fx and migration 9 to 10 preserves existing rows`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      val profileEntity = PadProfileEntity(
        id = "p_v10",
        name = "V10 Profile",
        folderUri = null,
        gridPreset = GridPreset.GRID_4X4.name,
        gridRows = 4,
        gridCols = 4,
        masterEqEnabled = true,
        masterEqPreset = EqPreset.BASS_BOOST.name,
        masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(EqPreset.BASS_BOOST.gainsDb),
        createdAtMs = 1L,
        updatedAtMs = 2L,
        isActive = true
      )
      val padEntity = SoundPadEntity(
        id = "pad_v10",
        profileId = "p_v10",
        uriString = "content://test/a.wav",
        fileName = "a.wav",
        displayName = "A",
        extension = "WAV",
        durationMs = 1000L,
        sizeBytes = 100L,
        positionIndex = 0
      )
      val settingEntity = PadSettingEntity(
        padId = "pad_v10",
        eqEnabled = true,
        eqPreset = EqPreset.TREBLE.name,
        eqBandsJson = ProfileJsonSerializer.encodeEqBandsString(EqPreset.TREBLE.gainsDb),
        isReversed = true,
        fxPreset = FxPreset.HALL.name,
        echoEnabled = true,
        echoDelayMs = 410f,
        echoFeedback = 0.55f,
        echoMix = 0.35f,
        reverbEnabled = true,
        reverbRoomSize = 0.85f,
        reverbDamping = 0.35f,
        reverbMix = 0.48f
      )

      dao.saveCompleteProfile(profileEntity, listOf(padEntity), listOf(settingEntity))
      val loadedProfile = dao.getProfileById("p_v10")
      val loadedSetting = dao.getPadSetting("pad_v10")

      assertNotNull(loadedProfile)
      assertTrue(loadedProfile!!.masterEqEnabled)
      assertEquals(EqPreset.BASS_BOOST.name, loadedProfile.masterEqPreset)

      assertNotNull(loadedSetting)
      assertTrue(loadedSetting!!.eqEnabled)
      assertTrue(loadedSetting.isReversed)
      assertEquals(FxPreset.HALL.name, loadedSetting.fxPreset)
      assertEquals(410f, loadedSetting.echoDelayMs, 0.01f)
      assertEquals(0.85f, loadedSetting.reverbRoomSize, 0.01f)
    } finally {
      db.close()
    }

    // Verify MIGRATION_9_10 on a real SQLite v9 schema file
    val dbName = "migration_9_10_test.db"
    context.deleteDatabase(dbName)
    val helper = android.database.sqlite.SQLiteOpenHelper::class.java
    val openHelper = object : android.database.sqlite.SQLiteOpenHelper(context, dbName, null, 9) {
      override fun onCreate(sqDb: android.database.sqlite.SQLiteDatabase) {
        sqDb.execSQL(
          """
          CREATE TABLE pad_profiles (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            folderUri TEXT,
            gridPreset TEXT NOT NULL,
            gridRows INTEGER NOT NULL,
            gridCols INTEGER NOT NULL,
            bankNamesJson TEXT NOT NULL DEFAULT '{}',
            bankOrderJson TEXT NOT NULL DEFAULT '[]',
            bankFolderUrisJson TEXT NOT NULL DEFAULT '{}',
            createdAtMs INTEGER NOT NULL,
            updatedAtMs INTEGER NOT NULL,
            isActive INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        sqDb.execSQL(
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
            positionIndex INTEGER NOT NULL,
            bankIndex INTEGER NOT NULL DEFAULT 0,
            subfolderName TEXT NOT NULL DEFAULT '',
            colorArgb INTEGER,
            glowColorArgb INTEGER,
            glowIntensity REAL NOT NULL DEFAULT 1.0,
            shapeStyle TEXT,
            borderColorArgb INTEGER,
            borderWidthDp REAL NOT NULL DEFAULT 1.25,
            cornerRadiusDp REAL NOT NULL DEFAULT 14.0,
            textColorArgb INTEGER,
            surfaceFinish TEXT NOT NULL DEFAULT 'CLASSIC',
            badgeIcon TEXT NOT NULL DEFAULT 'NONE',
            showSlotNumber INTEGER NOT NULL DEFAULT 1,
            showDurationLabel INTEGER NOT NULL DEFAULT 1,
            isClone INTEGER NOT NULL DEFAULT 0,
            midiNote INTEGER,
            isFavorite INTEGER NOT NULL DEFAULT 0,
            categoryTag TEXT NOT NULL DEFAULT '',
            miniWaveformJson TEXT NOT NULL DEFAULT '',
            errorType TEXT,
            errorMessage TEXT
          )
          """.trimIndent()
        )
        sqDb.execSQL(
          """
          CREATE TABLE pad_settings (
            padId TEXT NOT NULL PRIMARY KEY,
            volume REAL NOT NULL DEFAULT 1.0,
            speed REAL NOT NULL DEFAULT 1.0,
            pitch REAL NOT NULL DEFAULT 1.0,
            pan REAL NOT NULL DEFAULT 0.0,
            isLooping INTEGER NOT NULL DEFAULT 0,
            triggerMode TEXT NOT NULL DEFAULT 'TOGGLE',
            chokeGroup INTEGER NOT NULL DEFAULT 0,
            fadeInMs INTEGER NOT NULL DEFAULT 0,
            fadeOutMs INTEGER NOT NULL DEFAULT 0,
            fadeCurve TEXT NOT NULL DEFAULT 'EQUAL_POWER',
            crossfadeEnabled INTEGER NOT NULL DEFAULT 1,
            trimStartMs INTEGER NOT NULL DEFAULT 0,
            trimEndMs INTEGER NOT NULL DEFAULT 0,
            isDuckingSource INTEGER NOT NULL DEFAULT 0,
            normalizedGain REAL NOT NULL DEFAULT 1.0
          )
          """.trimIndent()
        )
        sqDb.execSQL(
          "INSERT INTO pad_profiles (id, name, folderUri, gridPreset, gridRows, gridCols, createdAtMs, updatedAtMs, isActive) " +
            "VALUES ('old_prof', 'Legacy Set', NULL, 'GRID_4X4', 4, 4, 10, 20, 1)"
        )
        sqDb.execSQL(
          "INSERT INTO pad_settings (padId, volume, pan) VALUES ('old_pad', 0.75, -0.5)"
        )
      }

      override fun onUpgrade(sqDb: android.database.sqlite.SQLiteDatabase, oldV: Int, newV: Int) = Unit
    }
    val rawSqlDb = openHelper.writableDatabase
    val supportDb = object : androidx.sqlite.db.SupportSQLiteDatabase by androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
      .create(
        androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
          .name(dbName)
          .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(9) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
          })
          .build()
      ).writableDatabase {}
    rawSqlDb.close()

    try {
      SoundPadDatabase.MIGRATION_9_10.migrate(supportDb)

      supportDb.query("SELECT name, masterEqEnabled, masterEqPreset FROM pad_profiles WHERE id = 'old_prof'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals("Legacy Set", c.getString(0))
        assertEquals(0, c.getInt(1))
        assertEquals("FLAT", c.getString(2))
      }

      supportDb.query("SELECT volume, pan, eqEnabled, isReversed, fxPreset, echoDelayMs FROM pad_settings WHERE padId = 'old_pad'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals(0.75f, c.getFloat(0), 0.001f)
        assertEquals(-0.5f, c.getFloat(1), 0.001f)
        assertEquals(0, c.getInt(2))
        assertEquals(0, c.getInt(3))
        assertEquals("OFF", c.getString(4))
        assertEquals(220f, c.getFloat(5), 0.01f)
      }
    } finally {
      supportDb.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun `subfase 9a design system presets and theme ui appearance round trip cleanly`() {
    assertEquals(12, com.example.ui.theme.SoundPadColorPalette.PAD_PRESETS_12.size)
    assertTrue(ThemeMode.entries.contains(ThemeMode.AMOLED))

    val gs = GlobalSettings(
      activeProfileId = "prof_9a",
      themeMode = ThemeMode.AMOLED,
      useDynamicColor = true,
      appAccentColorArgb = 0xFF8B5CF6L,
      padGridSpacingDp = 12,
      isGlobalCueEnabled = true,
      defaultPadShape = PadShapeStyle.PRISM,
      defaultCornerGlowArgb = 0xFF10B981L
    )
    val profile = PadProfile(
      id = "prof_9a",
      name = "OLED Live Stage",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0,
      themeMode = ThemeMode.AMOLED,
      useDynamicColor = true,
      appAccentColorArgb = 0xFF8B5CF6L,
      padGridSpacingDp = 12,
      defaultPadShape = PadShapeStyle.PRISM,
      defaultCornerGlowArgb = 0xFF10B981L,
      createdAtMs = 100L,
      updatedAtMs = 200L,
      isActive = true
    )

    val profJson = ProfileJsonSerializer.exportToJson(profile, emptyList())
    val importedProf = ProfileJsonSerializer.importFromJson(profJson)
    assertEquals(ThemeMode.AMOLED, importedProf.profile.themeMode)
    assertEquals(true, importedProf.profile.useDynamicColor)
    assertEquals(0xFF8B5CF6L, importedProf.profile.appAccentColorArgb)
    assertEquals(12, importedProf.profile.padGridSpacingDp)
    assertEquals(PadShapeStyle.PRISM, importedProf.profile.defaultPadShape)
    assertEquals(0xFF10B981L, importedProf.profile.defaultCornerGlowArgb)

    val backupJson = FullBackupSerializer.exportBackupJson(
      settings = gs,
      profiles = listOf(ExportedProfileBundle(profile, emptyList(), emptyList()))
    )
    val restoredBackup = FullBackupSerializer.importBackupJson(backupJson)
    assertEquals(ThemeMode.AMOLED, restoredBackup.globalSettings.themeMode)
    assertTrue(restoredBackup.globalSettings.useDynamicColor)
    assertEquals(12, restoredBackup.globalSettings.padGridSpacingDp)
    assertTrue(restoredBackup.globalSettings.isGlobalCueEnabled)
  }

  @Test
  fun `subfase 9b and 9c settings backup dao error delete locale and adaptive breakpoints work cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()

    val gs9b = GlobalSettings(
      activeProfileId = "prof_9b",
      hapticIntensity = com.example.domain.model.HapticIntensity.STRONG,
      swipePadVolumeEnabled = true,
      doubleTapToStopEnabled = false,
      hasCompletedOnboarding = true,
      showFeatureTooltips = false,
      appLanguage = com.example.domain.model.AppLanguage.ENGLISH
    )
    val prof = PadProfile(
      id = "prof_9b",
      name = "9B Test",
      folderUri = null,
      gridConfig = GridConfig(),
      createdAtMs = 1L,
      updatedAtMs = 2L,
      isActive = true
    )
    val backupJson = FullBackupSerializer.exportBackupJson(
      settings = gs9b,
      profiles = listOf(ExportedProfileBundle(prof, emptyList(), emptyList()))
    )
    val restored = FullBackupSerializer.importBackupJson(backupJson)
    assertEquals(com.example.domain.model.HapticIntensity.STRONG, restored.globalSettings.hapticIntensity)
    assertTrue(restored.globalSettings.swipePadVolumeEnabled)
    assertFalse(restored.globalSettings.doubleTapToStopEnabled)
    assertTrue(restored.globalSettings.hasCompletedOnboarding)
    assertFalse(restored.globalSettings.showFeatureTooltips)
    assertEquals(com.example.domain.model.AppLanguage.ENGLISH, restored.globalSettings.appLanguage)

    assertEquals(
      com.example.ui.components.AdaptiveWidthClass.COMPACT,
      com.example.ui.components.resolveAdaptiveWidthClass(androidx.compose.ui.unit.Dp(400f))
    )
    assertEquals(
      com.example.ui.components.AdaptiveWidthClass.MEDIUM,
      com.example.ui.components.resolveAdaptiveWidthClass(androidx.compose.ui.unit.Dp(700f))
    )
    assertEquals(
      com.example.ui.components.AdaptiveWidthClass.EXPANDED,
      com.example.ui.components.resolveAdaptiveWidthClass(androidx.compose.ui.unit.Dp(960f))
    )

    // Verify dual-language strings load cleanly for both AppLanguage tags
    val enConfig = android.content.res.Configuration(context.resources.configuration).apply {
      setLocale(java.util.Locale.forLanguageTag(com.example.domain.model.AppLanguage.ENGLISH.tag))
    }
    val enCtx = context.createConfigurationContext(enConfig)
    assertEquals("Fix", enCtx.getString(R.string.action_fix))

    val idConfig = android.content.res.Configuration(context.resources.configuration).apply {
      setLocale(java.util.Locale.forLanguageTag(com.example.domain.model.AppLanguage.INDONESIAN.tag))
    }
    val idCtx = context.createConfigurationContext(idConfig)
    assertEquals("Perbaiki", idCtx.getString(R.string.action_fix))

    // Verify single error log deletion by ID
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      dao.insertErrorLog(
        ErrorLogEntity(
          id = "err_a",
          timestampMs = 100L,
          fileName = "a.mp3",
          uriString = null,
          errorType = PadErrorType.CORRUPT_HEADER.name,
          message = "Header A rusak",
          suggestion = "Perbaiki",
          stackTrace = "",
          padId = "p1"
        )
      )
      dao.insertErrorLog(
        ErrorLogEntity(
          id = "err_b",
          timestampMs = 200L,
          fileName = "b.mp3",
          uriString = null,
          errorType = PadErrorType.DRM_PROTECTED.name,
          message = "DRM B",
          suggestion = "Ganti file",
          stackTrace = "",
          padId = "p2"
        )
      )
      dao.deleteErrorLogById("err_a")
      val remaining = dao.getErrorLogsOnce()
      assertEquals(1, remaining.size)
      assertEquals("err_b", remaining[0].id)
    } finally {
      db.close()
    }
  }

  @Test
  fun `midi program change parser and master wav pcm mix math work accurately`() {
    val pcBytes = byteArrayOf(0xC0.toByte(), 0x03, 0xC2.toByte(), 0x07)
    val parsed = MidiMessageParser.parse(pcBytes, 0, pcBytes.size)
    assertEquals(2, parsed.size)
    assertEquals(MidiEvent.ProgramChange(channel = 0, program = 3), parsed[0])
    assertEquals(MidiEvent.ProgramChange(channel = 2, program = 7), parsed[1])

    val header = MasterMixPcmMath.createWavHeader(dataSizeBytes = 1024, sampleRateHz = 48000)
    assertEquals(44, header.size)
    assertEquals('R'.code.toByte(), header[0])
    assertEquals('W'.code.toByte(), header[8])
    assertEquals('d'.code.toByte(), header[36])
    val encodedSampleRate = (header[24].toInt() and 0xFF) or
      ((header[25].toInt() and 0xFF) shl 8) or
      ((header[26].toInt() and 0xFF) shl 16) or
      ((header[27].toInt() and 0xFF) shl 24)
    assertEquals(48000, encodedSampleRate)

    // Resample 10ms stereo 44100 -> 48000 should increase frame count proportionally
    val src441 = FloatArray(441 * 2) { 0.25f }
    val resampled = MasterMixPcmMath.resampleStereo(src441, 44100, 48000)
    assertEquals(480 * 2, resampled.size)

    // Mixing two 0.7f streams engages tanh soft limiter without integer overflow
    val slotA = FloatArray(8) { 0.7f }
    val slotB = FloatArray(8) { 0.7f }
    val outPcm = ByteArray(4 * 2 * 2)
    val rms = MasterMixPcmMath.mixAndSoftLimitToPcm16(listOf(slotA, slotB), framesToWrite = 4, outBytes = outPcm)
    assertTrue(rms in 0.5f..1.0f)
  }

  @Test
  fun `scheduler next trigger computation sequencer queue math and room v11 persistence work`() = runBlocking {
    val calNow = java.util.Calendar.getInstance().apply {
      set(2026, java.util.Calendar.NOVEMBER, 16, 10, 0, 0) // Monday 10:00
      set(java.util.Calendar.MILLISECOND, 0)
    }
    val nowMs = calNow.timeInMillis

    // Later same day -> triggers today
    val laterTodayMs = PadSchedulerManager.computeNextTriggerTimeMs(
      hour = 14,
      minute = 30,
      repeatMode = ScheduleRepeatMode.ONCE,
      daysOfWeekMask = 0,
      nowMs = nowMs
    )
    assertTrue(laterTodayMs > nowMs)
    val checkToday = java.util.Calendar.getInstance().apply { timeInMillis = laterTodayMs }
    assertEquals(16, checkToday.get(java.util.Calendar.DAY_OF_MONTH))
    assertEquals(14, checkToday.get(java.util.Calendar.HOUR_OF_DAY))

    // Already passed today -> daily advances +1 day
    val tomorrowMs = PadSchedulerManager.computeNextTriggerTimeMs(
      hour = 8,
      minute = 0,
      repeatMode = ScheduleRepeatMode.DAILY,
      daysOfWeekMask = 0,
      nowMs = nowMs
    )
    val checkTomorrow = java.util.Calendar.getInstance().apply { timeInMillis = tomorrowMs }
    assertEquals(17, checkTomorrow.get(java.util.Calendar.DAY_OF_MONTH))

    // Custom days: Wednesday only (bit 2 -> 1 shl 2 = 4) from Monday 10:00 -> Nov 18
    val wednesdayMs = PadSchedulerManager.computeNextTriggerTimeMs(
      hour = 9,
      minute = 15,
      repeatMode = ScheduleRepeatMode.CUSTOM_DAYS,
      daysOfWeekMask = 1 shl 2,
      nowMs = nowMs
    )
    val checkWed = java.util.Calendar.getInstance().apply { timeInMillis = wednesdayMs }
    assertEquals(18, checkWed.get(java.util.Calendar.DAY_OF_MONTH))
    assertEquals(java.util.Calendar.WEDNESDAY, checkWed.get(java.util.Calendar.DAY_OF_WEEK))

    // Sequencer queue math across modes
    assertEquals(1, SequencerQueueMath.resolveNextIndex(0, 3, SequencerPlayMode.SEQUENTIAL))
    assertNull(SequencerQueueMath.resolveNextIndex(2, 3, SequencerPlayMode.SEQUENTIAL))
    assertEquals(0, SequencerQueueMath.resolveNextIndex(2, 3, SequencerPlayMode.REPEAT_ALL))
    assertEquals(1, SequencerQueueMath.resolveNextIndex(1, 3, SequencerPlayMode.REPEAT_ONE))
    val shuffled = SequencerQueueMath.resolveNextIndex(0, 3, SequencerPlayMode.SHUFFLE, kotlin.random.Random(42))
    assertTrue(shuffled != null && shuffled != 0)

    // SequencerPlaylist entity round-trip
    val playlist = SequencerPlaylist(
      id = "seq_1",
      name = "Show Intro",
      padIds = listOf("pad_1", "pad_2", "pad_3"),
      mode = SequencerPlayMode.REPEAT_ALL,
      gapSeconds = 2.5f,
      crossfadeEnabled = true,
      crossfadeDurationMs = 1200L,
      triggerPadId = "pad_trigger",
      triggerMidiNote = 60,
      updatedAtMs = 5000L
    )
    val decodedPlaylist = playlist.toEntity().toDomain()
    assertEquals(playlist, decodedPlaylist)

    // Room v11 tables CRUD verification
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      dao.upsertProfile(
        PadProfileEntity(
          id = "p_v11",
          name = "V11 Profile",
          folderUri = null,
          gridPreset = GridPreset.GRID_4X4.name,
          gridRows = 4,
          gridCols = 4,
          createdAtMs = 1L,
          updatedAtMs = 1L,
          isActive = true
        )
      )
      val task = ScheduledPadTask(
        id = "sched_1",
        title = "Bell",
        targetType = ScheduleTargetType.SEQUENCER,
        targetId = "seq_1",
        hour = 9,
        minute = 30,
        repeatMode = ScheduleRepeatMode.DAILY,
        enabled = true,
        volume = 0.8f,
        fadeInMs = 1000L,
        autoStopMinutes = 5,
        loopPlayback = false,
        nextTriggerAtMs = tomorrowMs
      )
      dao.upsertScheduledTask(task.toEntity())
      dao.upsertSequencerPlaylist(playlist.toEntity())
      dao.upsertMidiPcMapping(
        MidiPcBankMappingEntity(
          id = "pc_1",
          profileId = "p_v11",
          programNumber = 12,
          bankIndex = 2,
          channel = -1
        )
      )

      assertEquals(1, dao.getActiveScheduledTasksOnce().size)
      assertEquals(task.title, dao.getScheduledTaskById("sched_1")?.toDomain()?.title)
      assertEquals(listOf("pad_1", "pad_2", "pad_3"), dao.getAllSequencerPlaylistsOnce().first().toDomain().padIds)
      assertEquals(12, dao.getMidiPcMappingsOnce("p_v11").first().programNumber)
    } finally {
      db.close()
    }
  }

  @Test
  fun `profile json round trip preserves default pad color settings and defaults missing glowIntensity to zero`() {
    val profile = PadProfile(
      id = "p_stage_a",
      name = "Stage A Profile",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 1,
      createdAtMs = 1L,
      updatedAtMs = 1L,
      isActive = true,
      defaultPadColorMode = DefaultPadColorMode.CUSTOM,
      defaultPadColorArgb = 0xFF1E88E5L
    )
    val pad = SoundPad(
      id = "p1",
      uriString = "content://test/kick.wav",
      fileName = "kick.wav",
      displayName = "Kick",
      extension = "WAV",
      durationMs = 900L,
      sizeBytes = 2048L,
      positionIndex = 0
    )

    val exportedJson = ProfileJsonSerializer.exportToJson(
      profile = profile,
      pads = listOf(pad)
    )
    val imported = ProfileJsonSerializer.importFromJson(exportedJson)
    assertEquals(DefaultPadColorMode.CUSTOM, imported.profile.defaultPadColorMode)
    assertEquals(0xFF1E88E5L, imported.profile.defaultPadColorArgb)
    assertEquals(0f, imported.pads.first().glowIntensity, 0.0001f)

    val autoProfile = profile.copy(defaultPadColorMode = DefaultPadColorMode.AUTO)
    val autoJson = ProfileJsonSerializer.exportToJson(
      profile = autoProfile,
      pads = listOf(pad)
    )
    val autoImported = ProfileJsonSerializer.importFromJson(autoJson)
    assertEquals(DefaultPadColorMode.AUTO, autoImported.profile.defaultPadColorMode)

    // Legacy JSON without glowIntensity must default to 0.0f
    val legacyJson = """
      {
        "id": "legacy",
        "name": "Legacy",
        "pads": [
          {
            "uriString": "content://legacy/snare.wav",
            "fileName": "snare.wav",
            "displayName": "Snare",
            "extension": "WAV"
          }
        ]
      }
    """.trimIndent()
    val legacyImported = ProfileJsonSerializer.importFromJson(legacyJson)
    assertEquals(0f, legacyImported.pads.first().glowIntensity, 0.0001f)
  }

  @Test
  fun `full backup json round trip preserves all Stage A focus and default color settings`() {
    val settings = GlobalSettings(
      activeProfileId = "p_backup",
      defaultPadColorMode = DefaultPadColorMode.CUSTOM,
      defaultPadColorArgb = 0xFF7B1FA2L,
      isFocusMode = true,
      startInFocusMode = true,
      focusCornerPanicEnabled = false,
      focusModeMidiNote = 64
    )
    val profile = PadProfile(
      id = "p_backup",
      name = "Backup Profile",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0,
      createdAtMs = 10L,
      updatedAtMs = 10L,
      isActive = true
    )
    val bundle = ExportedProfileBundle(
      profile = profile,
      pads = emptyList()
    )

    val json = FullBackupSerializer.exportBackupJson(
      settings = settings,
      profiles = listOf(bundle)
    )
    val payload = FullBackupSerializer.importBackupJson(json)
    val restored = payload.globalSettings

    assertEquals(DefaultPadColorMode.CUSTOM, restored.defaultPadColorMode)
    assertEquals(0xFF7B1FA2L, restored.defaultPadColorArgb)
    assertEquals(true, restored.isFocusMode)
    assertEquals(true, restored.startInFocusMode)
    assertEquals(false, restored.focusCornerPanicEnabled)
    assertEquals(64, restored.focusModeMidiNote)
  }
}
