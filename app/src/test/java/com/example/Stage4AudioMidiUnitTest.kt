package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.audio.AudioMixMath
import com.example.audio.MasterLimiterAudioProcessor
import com.example.audio.MasterLimiterMath
import com.example.audio.MasterLimiterParams
import com.example.audio.MasterMixPcmMath
import com.example.audio.RecordedMasterOutput
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SettingsBackupSerializer
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.SoundPadDatabase
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MasterRecordFormat
import com.example.domain.model.MasterRecordState
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.SoundPad
import com.example.midi.BleReconnectBackoffMath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage4AudioMidiUnitTest {

  @Rule
  @JvmField
  val migrationHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    SoundPadDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  private fun appContext(): Context = ApplicationProvider.getApplicationContext()

  // ==================== 4a: MASTER LIMITER & UNIFIED MIXER BUS GAIN ====================

  @Test
  fun `soft knee limiter clamps hot polyphonic transients to -1 dBFS ceiling`() {
    val limiter = MasterLimiterAudioProcessor(
      MasterLimiterParams(
        enabled = true,
        thresholdDb = -1.0f,
        lookaheadMs = 3.0f,
        attackMs = 0.5f,
        releaseMs = 80.0f,
        kneeWidthDb = 3.0f
      )
    )
    val sampleRate = 48000
    limiter.configureForFormat(sampleRate, 2)
    assertTrue("Lookahead should match 3ms at 48kHz (~144 frames)", limiter.getLookaheadFrames() in 96..240)

    // 25ms block containing a +6 dBFS transient peak (linear 2.0f) across both channels
    val frameCount = 1200
    val stereo = FloatArray(frameCount * 2)
    for (f in 0 until frameCount) {
      val env = if (f in 200..700) 2.0f else 0.1f
      val wave = if (f % 4 < 2) env else -env
      stereo[f * 2] = wave
      stereo[f * 2 + 1] = wave * 0.95f
    }

    limiter.processStereoInterleaved(stereo, frameCount, sampleRate)

    val maxAllowed = MasterLimiterMath.dbToLinear(-1.0f) + 0.002f // ~0.893
    var maxPeak = 0f
    for (sample in stereo) {
      val a = abs(sample)
      if (a > maxPeak) maxPeak = a
    }
    assertTrue("Limiter output peak ($maxPeak) must stay <= $maxAllowed", maxPeak <= maxAllowed)
    assertTrue("Limiter must report positive gain reduction on +6dB signal", limiter.lastGainReductionDb > 3.0f)
  }

  @Test
  fun `soft knee target gain decreases monotonically and releases cleanly after peak`() {
    val belowKnee = MasterLimiterMath.computeSoftKneeTargetGain(0.25f)
    val nearThreshold = MasterLimiterMath.computeSoftKneeTargetGain(0.89f)
    val hotPeak = MasterLimiterMath.computeSoftKneeTargetGain(2.0f)

    assertEquals(1.0f, belowKnee, 0.0001f)
    assertTrue("In-knee peak must attenuate smoothly (<1.0)", nearThreshold < 1.0f && nearThreshold > hotPeak)
    assertTrue("Hot peak gain * input must sit at -1 dBFS", abs(2.0f * hotPeak - MasterLimiterMath.dbToLinear(-1.0f)) < 0.005f)

    // Release recovery check
    val limiter = MasterLimiterAudioProcessor(MasterLimiterParams(enabled = true))
    val sr = 48000
    limiter.configureForFormat(sr, 2)
    val hotBlock = FloatArray(480 * 2) { 1.8f }
    limiter.processStereoInterleaved(hotBlock, 480, sr)
    val peakGr = limiter.lastGainReductionDb
    assertTrue(peakGr > 2.0f)

    // Feed 300ms of near-silence so envelope releases back toward 0 dB GR
    val quietBlock = FloatArray(4800 * 2) { 0.01f }
    repeat(3) { limiter.processStereoInterleaved(quietBlock, 4800, sr) }
    assertTrue("Gain reduction (${limiter.lastGainReductionDb} dB) should recover near 0 dB", limiter.lastGainReductionDb < 0.5f)
  }

  @Test
  fun `limiter disabled passes signal untouched and retains sqrt-n headroom fallback`() {
    val limiter = MasterLimiterAudioProcessor(MasterLimiterParams(enabled = false))
    limiter.configureForFormat(48000, 2)
    val buf = FloatArray(256 * 2) { idx -> if (idx % 2 == 0) 1.25f else -1.25f }
    limiter.processStereoInterleaved(buf, 256, 48000)
    assertEquals(1.25f, buf[0], 0.0001f)
    assertEquals(0.0f, limiter.lastGainReductionDb, 0.0001f)

    // Fallback when limiter off keeps 1.12 / sqrt(n) curve
    val gainLimiterOff = AudioMixMath.computeSlotGain(
      padVolume = 1.0f,
      masterVolume = 1.0f,
      activeCount = 4,
      limiterEnabled = false,
      normalizedGain = 1.0f,
      autoNormalizeEnabled = false,
      duckingAttenuation = 1.0f,
      useRealtimeBusLimiter = false
    )
    assertEquals((1.12 / 2.0).toFloat(), gainLimiterOff, 0.001f)

    // With realtime mixer bus active, unity slot headroom feeds full sum into lookahead limiter
    val gainBusActive = AudioMixMath.computeSlotGain(
      padVolume = 1.0f,
      masterVolume = 1.0f,
      activeCount = 4,
      limiterEnabled = true,
      normalizedGain = 1.0f,
      autoNormalizeEnabled = false,
      duckingAttenuation = 1.0f,
      useRealtimeBusLimiter = true
    )
    assertEquals(1.0f, gainBusActive, 0.0001f)
  }

  // ==================== 4b: BLE MIDI AUTO-RECONNECT & SERIALIZATION ====================

  @Test
  fun `ble reconnect backoff steps cap cleanly at 30s`() {
    assertEquals(2_000L, BleReconnectBackoffMath.computeDelayMs(0))
    assertEquals(4_000L, BleReconnectBackoffMath.computeDelayMs(1))
    assertEquals(8_000L, BleReconnectBackoffMath.computeDelayMs(2))
    assertEquals(15_000L, BleReconnectBackoffMath.computeDelayMs(3))
    assertEquals(30_000L, BleReconnectBackoffMath.computeDelayMs(4))
    assertEquals(30_000L, BleReconnectBackoffMath.computeDelayMs(99))
  }

  @Test
  fun `ble auto reconnect settings roundtrip across SettingsBackupSerializer and FullBackupSerializer`() {
    val settings = GlobalSettings(
      activeProfileId = "prof_stage",
      lastBleMidiAddress = "AA:BB:CC:11:22:33",
      lastBleMidiName = "AKAI LPD8 Wireless",
      bleAutoReconnectEnabled = false
    )
    val exportedSettings = SettingsBackupSerializer.exportBackupJson(SettingsBackupBundle(settings = settings))
    val importedSettings = SettingsBackupSerializer.importBackupJson(exportedSettings)
    assertEquals("AA:BB:CC:11:22:33", importedSettings.settings.lastBleMidiAddress)
    assertEquals("AKAI LPD8 Wireless", importedSettings.settings.lastBleMidiName)
    assertFalse(importedSettings.settings.bleAutoReconnectEnabled)

    val profile = PadProfile(
      id = "prof_stage",
      name = "Live Band",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0
    )
    val fullJson = FullBackupSerializer.exportBackupJson(
      settings = settings,
      profiles = listOf(ExportedProfileBundle(profile = profile, pads = emptyList()))
    )
    val restoredFull = FullBackupSerializer.importBackupJson(fullJson)
    assertEquals("AA:BB:CC:11:22:33", restoredFull.globalSettings.lastBleMidiAddress)
    assertEquals("AKAI LPD8 Wireless", restoredFull.globalSettings.lastBleMidiName)
    assertFalse(restoredFull.globalSettings.bleAutoReconnectEnabled)
  }

  // ==================== 4c: PER-PROFILE FOCUS MODE MIDI NOTE ====================

  @Test
  fun `migration 12 to 13 adds nullable focusModeMidiNote column on pad_profiles and preserves data`() {
    val dbName = "migration_test_12_13.db"
    val context = appContext()
    context.deleteDatabase(dbName)

    migrationHelper.createDatabase(dbName, 12).apply {
      execSQL(
        """
        INSERT INTO pad_profiles (
          id, name, folderUri, gridPreset, gridRows, gridCols, bankNamesJson,
          bankOrderJson, bankFolderUrisJson, masterEqEnabled, masterEqPreset, masterEqBandsJson,
          createdAtMs, updatedAtMs, isActive
        ) VALUES (
          'prof_a', 'Gereja Pagi', NULL, 'GRID_4X4', 4, 4, '{}', '[]', '{}',
          0, 'FLAT', '[0,0,0,0,0]', 1000, 1000, 1
        )
        """.trimIndent()
      )
      close()
    }

    val migrated = migrationHelper.runMigrationsAndValidate(
      dbName,
      13,
      true,
      SoundPadDatabase.MIGRATION_12_13
    )

    try {
      migrated.query("SELECT id, focusModeMidiNote FROM pad_profiles WHERE id = 'prof_a'").use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertEquals("prof_a", cursor.getString(0))
        assertTrue(cursor.isNull(1))
      }
    } finally {
      migrated.close()
      context.deleteDatabase(dbName)
    }
  }

  @Test
  fun `dao updates focusModeMidiNote independently per profile`() = runBlocking {
    val context = appContext()
    val db = Room.inMemoryDatabaseBuilder(context, SoundPadDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    try {
      val dao = db.soundPadDao()
      dao.upsertProfile(
        PadProfileEntity(
          id = "p1",
          name = "Profil 1",
          folderUri = null,
          gridPreset = GridPreset.GRID_4X4.name,
          gridRows = 4,
          gridCols = 4,
          createdAtMs = 1L,
          updatedAtMs = 1L,
          isActive = true,
          focusModeMidiNote = 60
        )
      )
      dao.upsertProfile(
        PadProfileEntity(
          id = "p2",
          name = "Profil 2",
          folderUri = null,
          gridPreset = GridPreset.GRID_4X4.name,
          gridRows = 4,
          gridCols = 4,
          createdAtMs = 2L,
          updatedAtMs = 2L,
          isActive = false,
          focusModeMidiNote = 72
        )
      )

      dao.updateProfileFocusModeMidiNote("p1", 48, 100L)
      val loaded1 = dao.getProfileById("p1")
      val loaded2 = dao.getProfileById("p2")
      assertNotNull(loaded1)
      assertNotNull(loaded2)
      assertEquals(48, loaded1?.focusModeMidiNote)
      assertEquals(72, loaded2?.focusModeMidiNote)

      dao.updateProfileFocusModeMidiNote("p1", null, 200L)
      assertNull(dao.getProfileById("p1")?.focusModeMidiNote)
      assertEquals(72, dao.getProfileById("p2")?.focusModeMidiNote)
    } finally {
      db.close()
    }
  }

  @Test
  fun `per-profile focusModeMidiNote serializes cleanly in ProfileJsonSerializer and FullBackupSerializer`() {
    val profileA = PadProfile(
      id = "prof_a",
      name = "Set A",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0,
      focusModeMidiNote = 36
    )
    val profileB = PadProfile(
      id = "prof_b",
      name = "Set B",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 0,
      focusModeMidiNote = 64
    )

    val singleJson = ProfileJsonSerializer.exportToJson(
      profile = profileA,
      pads = emptyList(),
      focusModeMidiNote = profileA.focusModeMidiNote
    )
    val importedSingle = ProfileJsonSerializer.importFromJson(singleJson)
    assertEquals(36, importedSingle.focusModeMidiNote)
    assertEquals(36, importedSingle.profile.focusModeMidiNote)

    val fullJson = FullBackupSerializer.exportBackupJson(
      settings = GlobalSettings(activeProfileId = "prof_a", focusModeMidiNote = 99),
      profiles = listOf(
        ExportedProfileBundle(profile = profileA, pads = emptyList(), focusModeMidiNote = 36),
        ExportedProfileBundle(profile = profileB, pads = emptyList(), focusModeMidiNote = 64)
      )
    )
    val restoredFull = FullBackupSerializer.importBackupJson(fullJson)
    assertEquals(2, restoredFull.profiles.size)
    assertEquals(36, restoredFull.profiles.first { it.profile.id == "prof_a" }.profile.focusModeMidiNote)
    assertEquals(64, restoredFull.profiles.first { it.profile.id == "prof_b" }.profile.focusModeMidiNote)
  }

  // ==================== 4d: MASTER RECORDER WAV / M4A (AAC) ====================

  @Test
  fun `master record format exposes correct extensions mime types and pcm header`() {
    assertEquals("wav", MasterRecordFormat.WAV.extension)
    assertEquals("audio/wav", MasterRecordFormat.WAV.mimeType)
    assertEquals("m4a", MasterRecordFormat.M4A.extension)
    assertEquals("audio/mp4", MasterRecordFormat.M4A.mimeType)

    val defaultState = MasterRecordState()
    assertEquals(MasterRecordFormat.WAV, defaultState.format)
    assertEquals("wav", defaultState.lastSavedExtension)

    val recorded = RecordedMasterOutput(
      uriString = "file:///tmp/mix.m4a",
      fileName = "SoundPad_Master_test.m4a",
      displayName = "SoundPad_Master_test",
      durationMs = 1500L,
      sizeBytes = 24000L,
      sampleRateHz = 44100,
      format = MasterRecordFormat.M4A
    )
    assertEquals(MasterRecordFormat.M4A, recorded.format)

    val wavHeader = MasterMixPcmMath.createWavHeader(dataSizeBytes = 1024, sampleRateHz = 48000)
    assertEquals(44, wavHeader.size)
    assertEquals('R'.code.toByte(), wavHeader[0])
    assertEquals('W'.code.toByte(), wavHeader[8])
  }
}
