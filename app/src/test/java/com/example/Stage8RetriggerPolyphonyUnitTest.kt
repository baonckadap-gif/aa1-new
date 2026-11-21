package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.audio.ActiveVoiceSnapshot
import com.example.audio.InMemoryAudioCache
import com.example.audio.RetriggerVoicePlanner
import com.example.audio.SoundPadAudioEngine
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.PadBackupEntry
import com.example.data.backup.ProfileBackupBundle
import com.example.data.backup.ProfileBackupSerializer
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SettingsBackupSerializer
import com.example.data.local.db.SoundPadDatabase
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PlayMode
import com.example.domain.model.RetriggerMode
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.ui.components.GridSettingsSheet
import com.example.ui.components.PadCell
import com.example.ui.components.PadDensity
import com.example.ui.components.PadSettingsSheet
import com.example.ui.theme.SoundPadProTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage8RetriggerPolyphonyUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Rule
  @JvmField
  val migrationHelper = MigrationTestHelper(
    InstrumentationRegistry.getInstrumentation(),
    SoundPadDatabase::class.java,
    emptyList(),
    FrameworkSQLiteOpenHelperFactory()
  )

  private fun appContext(): Context = ApplicationProvider.getApplicationContext()

  private fun writeTestWav(file: File, sampleRateHz: Int = 48000, durationMs: Int = 2000): File {
    val totalSamples = (sampleRateHz * durationMs) / 1000
    val dataBytes = totalSamples * 2
    RandomAccessFile(file, "rw").use { out ->
      out.setLength(0L)
      out.writeBytes("RIFF")
      out.writeInt(Integer.reverseBytes(36 + dataBytes))
      out.writeBytes("WAVEfmt ")
      out.writeInt(Integer.reverseBytes(16))
      out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
      out.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt())
      out.writeInt(Integer.reverseBytes(sampleRateHz))
      out.writeInt(Integer.reverseBytes(sampleRateHz * 2))
      out.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt())
      out.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())
      out.writeBytes("data")
      out.writeInt(Integer.reverseBytes(dataBytes))
      val pcm = ByteArray(dataBytes)
      for (i in 0 until totalSamples) {
        val sample = (sin(2.0 * PI * 440.0 * i / sampleRateHz) * 12000.0).toInt().toShort()
        pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
        pcm[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
      }
      out.write(pcm)
    }
    return file
  }

  private fun samplePad(
    id: String = "pad_poly_1",
    name: String = "Kick Layer",
    uriString: String = "file:///tmp/test.wav",
    triggerMode: PadTriggerMode = PadTriggerMode.ONE_SHOT,
    retriggerMode: RetriggerMode? = null,
    maxVoices: Int = 0,
    chokeGroup: Int = 0,
    isLooping: Boolean = false,
    speed: Float = 1.0f,
    pitch: Float = 1.0f
  ): SoundPad {
    return SoundPad(
      id = id,
      uriString = uriString,
      fileName = "$id.wav",
      displayName = name,
      extension = "WAV",
      durationMs = 2000L,
      sizeBytes = 192000L,
      positionIndex = 0,
      bankIndex = 0,
      settings = PadSettings(
        triggerMode = triggerMode,
        isLooping = isLooping,
        chokeGroup = chokeGroup,
        speed = speed,
        pitch = pitch,
        retriggerMode = retriggerMode,
        maxVoices = maxVoices
      )
    )
  }

  // ==================== E.1 - E.2: PLANNER RESOLUTION & VOICE STEALING ====================

  @Test
  fun `RetriggerVoicePlanner resolves global vs per-pad overrides and steals fading then oldest`() {
    val gRestart = GlobalSettings(retriggerMode = RetriggerMode.RESTART, maxVoices = 12)
    val gLayer = GlobalSettings(retriggerMode = RetriggerMode.LAYER, maxVoices = 8)

    assertEquals(RetriggerMode.RESTART, RetriggerVoicePlanner.resolveEffectiveRetriggerMode(PadSettings(retriggerMode = null), gRestart))
    assertEquals(RetriggerMode.LAYER, RetriggerVoicePlanner.resolveEffectiveRetriggerMode(PadSettings(retriggerMode = RetriggerMode.LAYER), gRestart))
    assertEquals(RetriggerMode.RESTART, RetriggerVoicePlanner.resolveEffectiveRetriggerMode(PadSettings(retriggerMode = RetriggerMode.RESTART), gLayer))

    assertEquals(12, RetriggerVoicePlanner.resolveEffectivePadVoiceCap(PadSettings(maxVoices = 0), gRestart))
    assertEquals(4, RetriggerVoicePlanner.resolveEffectivePadVoiceCap(PadSettings(maxVoices = 4), gRestart))
    assertEquals(8, RetriggerVoicePlanner.resolveEffectivePadVoiceCap(PadSettings(maxVoices = 14), gLayer))

    val activeVoices = listOf(
      ActiveVoiceSnapshot("padA#1", "padA", chokeGroup = 0, isFadingOut = false, crossfadeEnabled = false),
      ActiveVoiceSnapshot("padB#2", "padB", chokeGroup = 0, isFadingOut = true, crossfadeEnabled = false),
      ActiveVoiceSnapshot("padA#3", "padA", chokeGroup = 0, isFadingOut = false, crossfadeEnabled = false)
    )
    // Prefer fading out voice before oldest active when evicting globally
    assertEquals("padB#2", RetriggerVoicePlanner.selectVoiceToSteal(activeVoices)?.voiceId)

    // When per-pad cap is 2 and padA already has 2 voices, padA#1 must be evicted; global cap 3 evicts 1 total so size stays < 3
    val evictions = RetriggerVoicePlanner.computeEvictionsForNewVoice(
      voices = activeVoices,
      incomingPadId = "padA",
      perPadCap = 2,
      globalCap = 3
    )
    assertTrue("padA#1" in evictions)
    assertEquals(1, evictions.size)
  }

  // ==================== E.1 - E.6, E.9: ENGINE POLYPHONY & INTERACTIONS ====================

  @Test
  fun `engine supports RESTART vs LAYER across ONE_SHOT, TOGGLE, LOOP, and HOLD`() {
    val ctx = appContext()
    val wavFile = writeTestWav(File(ctx.cacheDir, "stage8_modes.wav"))
    val uri = wavFile.toURI().toString()
    val engine = SoundPadAudioEngine(ctx, InMemoryAudioCache(ctx))
    try {
      engine.updateGlobalSettings(
        GlobalSettings(
          playMode = PlayMode.MULTI,
          retriggerMode = RetriggerMode.RESTART,
          maxVoices = 16,
          masterLimiterEnabled = false,
          audioEffectsEnabled = false,
          globalFadeInMs = 0L,
          globalFadeOutMs = 0L
        )
      )

      // 1. ONE_SHOT + RESTART keeps 1 voice
      val oneShotRestart = samplePad("p_one_restart", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.RESTART)
      engine.updatePlaylist(listOf(oneShotRestart))
      engine.triggerPad(oneShotRestart)
      engine.triggerPad(oneShotRestart)
      engine.flushDispatcherForTest(3000L)
      assertEquals(1, engine.getActiveVoiceCountForPadForTest(oneShotRestart.id))

      // 2. ONE_SHOT + LAYER stacks 3 voices
      val oneShotLayer = samplePad("p_one_layer", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER)
      engine.updatePlaylist(listOf(oneShotLayer))
      repeat(3) { engine.triggerPad(oneShotLayer) }
      engine.flushDispatcherForTest(3000L)
      assertEquals(3, engine.getActiveVoiceCountForPadForTest(oneShotLayer.id))
      assertEquals(3, engine.playbackStates.value[oneShotLayer.id]?.activeVoiceCount)
      engine.stopAll()
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getTotalActiveVoiceCountForTest())

      // 3. TOGGLE + RESTART toggles off on 2nd tap; TOGGLE + LAYER stacks on 2nd tap
      val toggleRestart = samplePad("p_tog_restart", uriString = uri, triggerMode = PadTriggerMode.TOGGLE, retriggerMode = RetriggerMode.RESTART)
      engine.updatePlaylist(listOf(toggleRestart))
      engine.triggerPad(toggleRestart)
      engine.flushDispatcherForTest(3000L)
      assertEquals(1, engine.getActiveVoiceCountForPadForTest(toggleRestart.id))
      engine.triggerPad(toggleRestart)
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getActiveVoiceCountForPadForTest(toggleRestart.id))

      val toggleLayer = samplePad("p_tog_layer", uriString = uri, triggerMode = PadTriggerMode.TOGGLE, retriggerMode = RetriggerMode.LAYER)
      engine.updatePlaylist(listOf(toggleLayer))
      engine.triggerPad(toggleLayer)
      engine.triggerPad(toggleLayer)
      engine.flushDispatcherForTest(3000L)
      assertEquals(2, engine.getActiveVoiceCountForPadForTest(toggleLayer.id))
      engine.stopPad(toggleLayer.id)
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getActiveVoiceCountForPadForTest(toggleLayer.id))

      // 4. LOOP + LAYER stacks repeating voices; LOOP + RESTART replaces
      val loopLayer = samplePad("p_loop_layer", uriString = uri, triggerMode = PadTriggerMode.LOOP, isLooping = true, retriggerMode = RetriggerMode.LAYER)
      engine.updatePlaylist(listOf(loopLayer))
      engine.triggerPad(loopLayer)
      engine.triggerPad(loopLayer)
      engine.flushDispatcherForTest(3000L)
      assertEquals(2, engine.getActiveVoiceCountForPadForTest(loopLayer.id))
      assertEquals(listOf(Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ONE), engine.getActiveVoiceLoopsForPadForTest(loopLayer.id))
      engine.stopAll()
      engine.flushDispatcherForTest(3000L)

      // 5. HOLD + LAYER stacks on triggerHoldStart and stops all on triggerHoldRelease
      val holdLayer = samplePad("p_hold_layer", uriString = uri, triggerMode = PadTriggerMode.HOLD, retriggerMode = RetriggerMode.LAYER)
      engine.updatePlaylist(listOf(holdLayer))
      engine.triggerHoldStart(holdLayer)
      engine.triggerHoldStart(holdLayer)
      engine.flushDispatcherForTest(3000L)
      assertEquals(2, engine.getActiveVoiceCountForPadForTest(holdLayer.id))
      engine.triggerHoldRelease(holdLayer.id)
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getActiveVoiceCountForTestFallback(holdLayer.id))
    } finally {
      engine.release()
    }
  }

  private fun SoundPadAudioEngine.getActiveVoiceCountForTestFallback(padId: String): Int =
    getActiveVoiceCountForPadForTest(padId)

  @Test
  fun `engine respects PlayMode SINGLE, CONTINUOUS, ChokeGroup, and global 16-voice stealing without leak`() {
    val ctx = appContext()
    val wavFile = writeTestWav(File(ctx.cacheDir, "stage8_steal.wav"))
    val uri = wavFile.toURI().toString()
    val engine = SoundPadAudioEngine(ctx, InMemoryAudioCache(ctx))
    try {
      val padA = samplePad("pad_a", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER, chokeGroup = 1)
      val padB = samplePad("pad_b", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER, chokeGroup = 1)
      val padC = samplePad("pad_c", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER, chokeGroup = 2)
      engine.updatePlaylist(listOf(padA, padB, padC))

      // PlayMode.SINGLE: same-pad LAYER stacks, different pad cuts previous pad
      engine.updateGlobalSettings(
        GlobalSettings(
          playMode = PlayMode.SINGLE,
          retriggerMode = RetriggerMode.LAYER,
          maxVoices = 16,
          globalFadeInMs = 0L,
          globalFadeOutMs = 0L,
          globalCrossfadeEnabled = false
        )
      )
      engine.triggerPad(padA)
      engine.triggerPad(padA)
      engine.flushDispatcherForTest(3000L)
      assertEquals(2, engine.getActiveVoiceCountForPadForTest(padA.id))
      engine.triggerPad(padB)
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getActiveVoiceCountForPadForTest(padA.id))
      assertEquals(1, engine.getActiveVoiceCountForPadForTest(padB.id))

      // PlayMode.MULTI + ChokeGroup: padA cuts padB (both choke group 1) but leaves padC (group 2)
      engine.updateGlobalSettings(
        GlobalSettings(
          playMode = PlayMode.MULTI,
          retriggerMode = RetriggerMode.LAYER,
          maxVoices = 16,
          globalFadeInMs = 0L,
          globalFadeOutMs = 0L
        )
      )
      engine.triggerPad(padC)
      engine.triggerPad(padC)
      engine.triggerPad(padA)
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getActiveVoiceCountForPadForTest(padB.id))
      assertEquals(2, engine.getActiveVoiceCountForPadForTest(padC.id))
      assertEquals(1, engine.getActiveVoiceCountForPadForTest(padA.id))

      // Stress 20 triggers on global 16-voice ceiling -> never exceeds 16 active or 16 pooled slots
      engine.stopAll()
      engine.flushDispatcherForTest(3000L)
      val capPad = samplePad("pad_stress", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER, maxVoices = 16)
      engine.updatePlaylist(listOf(capPad))
      repeat(20) {
        engine.triggerPad(capPad)
      }
      engine.flushDispatcherForTest(4000L)
      assertEquals(SoundPadAudioEngine.MAX_PLAYER_POOL, engine.getTotalActiveVoiceCountForTest())
      assertTrue(engine.getTotalAllocatedPoolCountForTest() <= SoundPadAudioEngine.MAX_PLAYER_POOL)

      // Per-pad cap = 3 steals older voices when a 4th triggers
      engine.stopAll()
      engine.flushDispatcherForTest(3000L)
      val capped3 = samplePad("pad_cap3", uriString = uri, triggerMode = PadTriggerMode.ONE_SHOT, retriggerMode = RetriggerMode.LAYER, maxVoices = 3)
      engine.updatePlaylist(listOf(capped3))
      repeat(5) { engine.triggerPad(capped3) }
      engine.flushDispatcherForTest(3000L)
      assertEquals(3, engine.getActiveVoiceCountForPadForTest(capped3.id))

      // E.5: Live updateActivePadParameters propagates speed, pitch, and loop to ALL stacked voices
      val updatedCapped = capped3.copy(
        settings = capped3.settings.copy(speed = 1.35f, pitch = 1.25f, isLooping = true)
      )
      engine.updateActivePadParameters(updatedCapped)
      engine.flushDispatcherForTest(3000L)
      assertEquals(listOf(1.35f, 1.35f, 1.35f), engine.getActiveVoiceSpeedsForPadForTest(capped3.id))
      assertEquals(listOf(1.25f, 1.25f, 1.25f), engine.getActiveVoicePitchesForPadForTest(capped3.id))
      assertEquals(listOf(Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ONE, Player.REPEAT_MODE_ONE), engine.getActiveVoiceLoopsForPadForTest(capped3.id))

      // E.6: stopAll + fadeOutAll leave 0 leaked voices
      engine.stopAll()
      engine.flushDispatcherForTest(3000L)
      assertEquals(0, engine.getTotalActiveVoiceCountForTest())
      assertEquals(emptyMap<String, PadPlaybackState>(), engine.playbackStates.value)
    } finally {
      engine.release()
    }
  }

  // ==================== E.8: ROOM MIGRATION 13 -> 14 & SERIALIZATION ROUND-TRIP ====================

  @Test
  fun `migration 13 to 14 adds retriggerMode NULL and maxVoices 0 cleanly while preserving pad settings`() {
    val dbName = "migration_test_13_14.db"
    val ctx = appContext()
    ctx.deleteDatabase(dbName)

    migrationHelper.createDatabase(dbName, 13).apply {
      execSQL(
        """
        INSERT INTO pad_profiles (
          id, name, folderUri, gridPreset, gridRows, gridCols, bankNamesJson, bankOrderJson, bankFolderUrisJson,
          masterEqEnabled, masterEqPreset, masterEqBandsJson, focusModeMidiNote, createdAtMs, updatedAtMs, isActive
        ) VALUES (
          'prof_13', 'Stage 13', 'content://tree/13', 'GRID_4X4', 4, 4, '{}', '[]', '{}',
          0, 'FLAT', '[0,0,0,0,0]', NULL, 1000, 2000, 1
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO sound_pads (
          id, profileId, uriString, fileName, displayName, extension, durationMs, sizeBytes, positionIndex,
          colorArgb, glowColorArgb, glowIntensity, shapeStyle, borderColorArgb, borderWidthDp, cornerRadiusDp,
          textColorArgb, surfaceFinish, badgeIcon, showSlotNumber, showDurationLabel, isClone, midiNote, isFavorite, categoryTag,
          bankIndex, subfolderName, miniWaveformJson, errorType, errorMessage
        ) VALUES (
          'pad_13', 'prof_13', 'content://audio/13.wav', '13.wav', 'Pad 13', 'WAV', 1000, 10000, 0,
          NULL, NULL, 1.0, NULL, NULL, 1.25, 14.0, NULL, 'CLASSIC', 'NONE', 1, 1, 0, NULL, 0, '',
          0, '', '[]', NULL, NULL
        )
        """.trimIndent()
      )
      execSQL(
        """
        INSERT INTO pad_settings (
          padId, volume, speed, pitch, isLooping, triggerMode, chokeGroup, fadeInMs, fadeOutMs,
          fadeCurve, crossfadeEnabled, trimStartMs, trimEndMs, isDuckingSource, normalizedGain,
          eqEnabled, eqPreset, eqBandsJson, isReversed, pan, fxPreset, echoEnabled, echoDelayMs,
          echoFeedback, echoMix, reverbEnabled, reverbRoomSize, reverbDamping, reverbMix
        ) VALUES (
          'pad_13', 0.85, 1.0, 1.0, 0, 'ONE_SHOT', 0, 0, 0,
          'EQUAL_POWER', 1, 0, 0, 0, 1.0,
          0, 'FLAT', '[0,0,0,0,0]', 0, 0.0, 'OFF', 0, 220.0,
          0.35, 0.25, 0, 0.55, 0.45, 0.25
        )
        """.trimIndent()
      )
      close()
    }

    val migrated = migrationHelper.runMigrationsAndValidate(
      dbName,
      14,
      true,
      SoundPadDatabase.MIGRATION_13_14
    )
    try {
      migrated.query("SELECT volume, retriggerMode, maxVoices FROM pad_settings WHERE padId = 'pad_13'").use { c ->
        assertTrue(c.moveToFirst())
        assertEquals(0.85f, c.getFloat(0), 0.001f)
        assertTrue(c.isNull(1))
        assertEquals(0, c.getInt(2))
      }
    } finally {
      migrated.close()
      ctx.deleteDatabase(dbName)
    }
  }

  @Test
  fun `GlobalSettingsRepository, ProfileJsonSerializer, and Backup serializers round-trip RetriggerMode and maxVoices`(): Unit = runBlocking {
    val ctx = appContext()
    val repo = GlobalSettingsRepository(ctx)
    repo.setRetriggerMode(RetriggerMode.LAYER)
    repo.setMaxVoices(11)
    val loaded = repo.settingsFlow.first()
    assertEquals(RetriggerMode.LAYER, loaded.retriggerMode)
    assertEquals(11, loaded.maxVoices)

    val pad = samplePad(
      id = "pad_json_poly",
      retriggerMode = RetriggerMode.LAYER,
      maxVoices = 6
    )
    val profile = PadProfile(
      id = "prof_poly",
      name = "Poly Profile",
      folderUri = null,
      gridConfig = GridConfig(),
      createdAtMs = 1000L,
      updatedAtMs = 1000L,
      isActive = true
    )

    // ProfileJsonSerializer
    val exportedBundle = ExportedProfileBundle(profile = profile, pads = listOf(pad))
    val profileJson = ProfileJsonSerializer.exportToJson(profile = profile, pads = listOf(pad))
    val importedProfile = ProfileJsonSerializer.importFromJson(profileJson)
    assertEquals(RetriggerMode.LAYER, importedProfile.pads.first().settings.retriggerMode)
    assertEquals(6, importedProfile.pads.first().settings.maxVoices)

    // SettingsBackupSerializer
    val settingsBundle = SettingsBackupBundle(
      settings = GlobalSettings(retriggerMode = RetriggerMode.LAYER, maxVoices = 9)
    )
    val settingsRestored = SettingsBackupSerializer.importBackupJson(
      SettingsBackupSerializer.exportBackupJson(settingsBundle)
    )
    assertEquals(RetriggerMode.LAYER, settingsRestored.settings.retriggerMode)
    assertEquals(9, settingsRestored.settings.maxVoices)

    // ProfileBackupSerializer
    val backupProfile = ProfileBackupBundle(
      profile = profile,
      pads = listOf(PadBackupEntry(pad = pad))
    )
    val restoredBackupProfile = ProfileBackupSerializer.importBackupJson(
      ProfileBackupSerializer.exportBackupJson(backupProfile, includeWaveforms = false)
    )
    assertEquals(RetriggerMode.LAYER, restoredBackupProfile.pads.first().pad.settings.retriggerMode)
    assertEquals(6, restoredBackupProfile.pads.first().pad.settings.maxVoices)

    // FullBackupSerializer
    val fullRestored = FullBackupSerializer.importBackupJson(
      FullBackupSerializer.exportBackupJson(settingsBundle.settings, listOf(exportedBundle))
    )
    assertEquals(RetriggerMode.LAYER, fullRestored.globalSettings.retriggerMode)
    assertEquals(9, fullRestored.globalSettings.maxVoices)
    assertEquals(RetriggerMode.LAYER, fullRestored.profiles.first().pads.first().settings.retriggerMode)
    assertEquals(6, fullRestored.profiles.first().pads.first().settings.maxVoices)

    // Reset repo defaults
    repo.setRetriggerMode(RetriggerMode.RESTART)
    repo.setMaxVoices(16)
  }

  // ==================== E.7: COMPOSE UI BADGE, LAYER TAP, & SETTINGS SHEETS ====================

  @Test
  fun `PadCell displays xN badge when activeVoiceCount exceeds 1 and suppresses double-tap stop in LAYER mode`() {
    val ctx = appContext()
    val layerPad = samplePad("pad_ui_badge", retriggerMode = RetriggerMode.LAYER)
    var clickCount = 0
    var doubleTapStopCount = 0

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.size(160.dp)) {
          PadCell(
            pad = layerPad,
            slotNumber = 1,
            playbackState = PadPlaybackState(
              padId = layerPad.id,
              isPlaying = true,
              positionMs = 120L,
              durationMs = 2000L,
              progress = 0.06f,
              activeVoiceCount = 4
            ),
            density = PadDensity.STANDARD,
            isEditMode = false,
            isDragging = false,
            isDropTarget = false,
            globalCrossfadeEnabled = false,
            defaultPadShape = PadShapeStyle.SQUARE,
            defaultCornerGlowColor = Color.Cyan,
            doubleTapToStopEnabled = true,
            globalRetriggerMode = RetriggerMode.RESTART,
            onClick = { clickCount++ },
            onDoubleTapStop = { doubleTapStopCount++ },
            onHoldStart = {},
            onHoldRelease = {}
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("pad_voice_count_badge_${layerPad.id}")
      .assertIsDisplayed()
    composeTestRule.onNodeWithText("×4").assertIsDisplayed()

    // Rapid double tap on a LAYER pad must trigger clicks twice and NEVER fire doubleTapStop
    composeTestRule.onNodeWithTag("pad_item_${layerPad.positionIndex}").performTouchInput {
      down(center)
      up()
    }
    composeTestRule.onNodeWithTag("pad_item_${layerPad.positionIndex}").performTouchInput {
      down(center)
      up()
    }
    composeTestRule.waitForIdle()
    assertEquals(2, clickCount)
    assertEquals(0, doubleTapStopCount)
  }

  @Test
  fun `PadSettingsSheet and GridSettingsSheet expose RetriggerMode and MaxVoices controls`() {
    var updatedPadSettings: PadSettings? = null
    var selectedGlobalRetrigger: RetriggerMode? = null
    var selectedGlobalMaxVoices = 16
    val pad = samplePad("pad_sheet_poly", retriggerMode = null, maxVoices = 0)

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        PadSettingsSheet(
          pad = pad,
          allPads = listOf(pad),
          defaultPadShape = PadShapeStyle.SQUARE,
          defaultCornerGlowColor = Color.Cyan,
          globalRetriggerMode = RetriggerMode.RESTART,
          globalMaxVoices = 16,
          onSelectPad = {},
          onSaveAppearance = {},
          onApplyStyleToAll = {},
          onSettingsChange = { updatedPadSettings = it },
          onOpenAudioEditor = {},
          onDuplicatePad = {},
          onDeletePad = {},
          onDismiss = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("tab_audio_fade").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("pad_retrigger_layer")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(RetriggerMode.LAYER, updatedPadSettings?.retriggerMode)

    composeTestRule.onNodeWithTag("pad_layer_switch")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    // Clicking switch off reverts to global RESTART -> stored as null
    assertEquals(null, updatedPadSettings?.retriggerMode)
  }

  @Test
  fun `GridSettingsSheet Global Retrigger Mode chips trigger callback`() {
    var globalMode = RetriggerMode.RESTART

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        GridSettingsSheet(
          settings = GlobalSettings(retriggerMode = RetriggerMode.RESTART, maxVoices = 16),
          preloadedCount = 0,
          onGridConfigChange = {},
          onThemeModeChange = {},
          onAppAccentColorChange = {},
          onDefaultPadShapeChange = {},
          onDefaultCornerGlowChange = {},
          onBottomBarVisibleChange = {},
          onLoadDemoTracks = {},
          onExportDemoToMusicFolder = {},
          onPreloadToggle = {},
          onRetriggerModeChange = { globalMode = it },
          onMaxVoicesChange = {},
          onKeepScreenOnToggle = {},
          onGlobalCrossfadeToggle = {},
          onCrossfadeDurationChange = {},
          onCrossfadeCurveChange = {},
          onGlobalFadeInChange = {},
          onGlobalFadeOutChange = {},
          onGlobalFadeCurveChange = {},
          onDismiss = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("settings_tab_audio")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("global_retrigger_layer")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(RetriggerMode.LAYER, globalMode)
  }

  @Test
  fun `Stage 3 retrigger and polyphony strings resolve cleanly in ID and EN`() {
    val base = appContext()
    val idCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("id")) }
    )
    val enCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    )
    val keys = listOf(
      R.string.settings_retrigger_mode_title,
      R.string.settings_retrigger_mode_subtitle,
      R.string.retrigger_mode_restart,
      R.string.retrigger_mode_layer,
      R.string.settings_max_voices_subtitle,
      R.string.cd_reset_max_voices,
      R.string.pad_retrigger_mode_title,
      R.string.pad_layer_switch_title,
      R.string.pad_layer_switch_subtitle,
      R.string.cd_reset_pad_retrigger
    )
    keys.forEach { resId ->
      assertTrue(idCtx.getString(resId).isNotBlank())
      assertTrue(enCtx.getString(resId).isNotBlank())
    }
  }
}
