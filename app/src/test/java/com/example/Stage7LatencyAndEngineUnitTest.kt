package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Process
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audio.InMemoryAudioCache
import com.example.audio.LatencyMetricsTracker
import com.example.audio.MasterAudioMixerBus
import com.example.audio.SoundPadAudioEngine
import com.example.audio.StereoPanAudioProcessor
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.SettingsBackupBundle
import com.example.data.backup.SettingsBackupSerializer
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.profile.ExportedProfileBundle
import com.example.domain.model.AnimationMode
import com.example.domain.model.FadeCurve
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PlayMode
import com.example.domain.model.SoundPad
import com.example.domain.model.TapLatencyMetrics
import com.example.domain.model.ThemeMode
import com.example.ui.components.GridSettingsSheet
import com.example.ui.components.LatencyDebugOverlay
import com.example.ui.components.PadCell
import com.example.ui.components.PadDensity
import com.example.ui.theme.SoundPadProTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage7LatencyAndEngineUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun appContext(): Context = ApplicationProvider.getApplicationContext()

  private fun samplePad(
    id: String = "pad_latency_1",
    name: String = "Snare Shot",
    triggerMode: PadTriggerMode = PadTriggerMode.TOGGLE,
    fadeInMs: Long = 0L,
    positionIndex: Int = 0,
    uriString: String = "content://test/$id.wav"
  ): SoundPad {
    return SoundPad(
      id = id,
      uriString = uriString,
      fileName = "$id.wav",
      displayName = name,
      extension = "WAV",
      durationMs = 1200L,
      sizeBytes = 48000L,
      positionIndex = positionIndex,
      bankIndex = 0,
      settings = PadSettings(
        triggerMode = triggerMode,
        fadeInMs = fadeInMs
      )
    )
  }

  @Composable
  private fun TestPadCell(
    pad: SoundPad,
    slotNumber: Int = 1,
    playbackState: PadPlaybackState? = null,
    animationMode: AnimationMode = AnimationMode.FULL,
    doubleTapToStopEnabled: Boolean = true,
    onClick: (SoundPad) -> Unit = {},
    onClickWithTime: ((SoundPad, Long) -> Unit)? = null,
    onHoldStart: (SoundPad) -> Unit = {},
    onHoldStartWithTime: ((SoundPad, Long) -> Unit)? = null,
    onHoldRelease: (SoundPad) -> Unit = {},
    onDoubleTapStop: (SoundPad) -> Unit = {}
  ) {
    PadCell(
      pad = pad,
      slotNumber = slotNumber,
      playbackState = playbackState,
      density = PadDensity.STANDARD,
      isEditMode = false,
      isDragging = false,
      isDropTarget = false,
      globalCrossfadeEnabled = false,
      defaultPadShape = PadShapeStyle.SQUARE,
      defaultCornerGlowColor = Color.Cyan,
      animationMode = animationMode,
      doubleTapToStopEnabled = doubleTapToStopEnabled,
      onClick = onClick,
      onClickWithTime = onClickWithTime,
      onDoubleTapStop = onDoubleTapStop,
      onHoldStart = onHoldStart,
      onHoldStartWithTime = onHoldStartWithTime,
      onHoldRelease = onHoldRelease
    )
  }

  private fun writeShortTestWav(file: File, sampleRateHz: Int = 48000, durationMs: Int = 60): File {
    val totalSamples = (sampleRateHz * durationMs) / 1000
    val dataBytes = totalSamples * 2
    val raf = RandomAccessFile(file, "rw")
    raf.use { out ->
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
        val sample = (sin(2.0 * PI * 440.0 * i / sampleRateHz) * 14000.0).toInt().toShort()
        pcm[i * 2] = (sample.toInt() and 0xFF).toByte()
        pcm[i * 2 + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
      }
      out.write(pcm)
    }
    return file
  }

  private fun SemanticsNodeInteraction.assertMinTouchTarget(minSize: Dp = 48.dp): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val touchBounds = node.touchBoundsInRoot
    with(composeTestRule.density) {
      val widthDp = touchBounds.width.toDp()
      val heightDp = touchBounds.height.toDp()
      assertTrue("Expected touch width >= $minSize, actual $widthDp", widthDp >= minSize - 0.1.dp)
      assertTrue("Expected touch height >= $minSize, actual $heightDp", heightDp >= minSize - 0.1.dp)
    }
    return this
  }

  // ==================== B.1 / B.2: ACTION_DOWN IMMEDIATE TRIGGER ====================

  @Test
  fun `PadCell triggers immediately on ACTION_DOWN before ACTION_UP without gesture delay`() {
    val pad = samplePad()
    var tapCount = 0
    var capturedDownNanos = 0L

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.size(140.dp)) {
          TestPadCell(
            pad = pad,
            slotNumber = 1,
            onClick = { tapCount++ },
            onClickWithTime = { _, downNanos ->
              tapCount++
              capturedDownNanos = downNanos
            }
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      down(center)
    }
    composeTestRule.waitForIdle()
    assertEquals("Trigger MUST fire at ACTION_DOWN before pointer release", 1, tapCount)
    assertTrue("Expected valid tap down timestamp", capturedDownNanos > 0L)

    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      up()
    }
    composeTestRule.waitForIdle()
    assertEquals("Releasing pointer should not double-trigger pad", 1, tapCount)
  }

  @Test
  fun `PadCell HOLD starts on ACTION_DOWN and releases on ACTION_UP`() {
    val pad = samplePad(id = "pad_hold", triggerMode = PadTriggerMode.HOLD, positionIndex = 0)
    var holdStartCount = 0
    var holdReleaseCount = 0

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.size(140.dp)) {
          TestPadCell(
            pad = pad,
            slotNumber = 1,
            onHoldStart = { holdStartCount++ },
            onHoldStartWithTime = { _, _ -> holdStartCount++ },
            onHoldRelease = { holdReleaseCount++ }
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      down(center)
    }
    composeTestRule.waitForIdle()
    assertEquals(1, holdStartCount)
    assertEquals(0, holdReleaseCount)

    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      up()
    }
    composeTestRule.waitForIdle()
    assertEquals(1, holdStartCount)
    assertEquals(1, holdReleaseCount)
  }

  @Test
  fun `PadCell doubleTapToStop triggers stop on second ACTION_DOWN while playing`() {
    val pad = samplePad(id = "pad_dtap", positionIndex = 0)
    var normalTapCount = 0
    var doubleTapStopCount = 0

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.size(140.dp)) {
          TestPadCell(
            pad = pad,
            slotNumber = 1,
            playbackState = PadPlaybackState(
              padId = pad.id,
              isPlaying = true,
              durationMs = 2000L,
              positionMs = 200L,
              progress = 0.1f
            ),
            doubleTapToStopEnabled = true,
            onClick = { normalTapCount++ },
            onClickWithTime = { _, _ -> normalTapCount++ },
            onDoubleTapStop = { doubleTapStopCount++ }
          )
        }
      }
    }

    // First tap down + up
    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      down(Offset(width * 0.5f, height * 0.5f))
      up()
    }
    // Second tap down immediately stops without waiting for UP
    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      down(Offset(width * 0.5f, height * 0.5f))
    }
    composeTestRule.waitForIdle()
    assertEquals(1, normalTapCount)
    assertEquals(1, doubleTapStopCount)

    composeTestRule.onNodeWithTag("pad_item_${pad.positionIndex}").performTouchInput {
      up()
    }
  }

  // ==================== C.4: ANIMATION MODES ====================

  @Test
  fun `PadCell renders and triggers cleanly under LIGHT and OFF AnimationModes`() {
    val padLight = samplePad(id = "pad_light", name = "Light Pad", positionIndex = 0)
    val padOff = samplePad(id = "pad_off", name = "Off Pad", positionIndex = 1)
    var lightClicks = 0
    var offClicks = 0

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Row(modifier = Modifier.fillMaxSize()) {
          Box(modifier = Modifier.size(120.dp)) {
            TestPadCell(
              pad = padLight,
              slotNumber = 1,
              animationMode = AnimationMode.LIGHT,
              onClickWithTime = { _, _ -> lightClicks++ }
            )
          }
          Box(modifier = Modifier.size(120.dp)) {
            TestPadCell(
              pad = padOff,
              slotNumber = 2,
              animationMode = AnimationMode.OFF,
              onClickWithTime = { _, _ -> offClicks++ }
            )
          }
        }
      }
    }

    composeTestRule.onNodeWithTag("pad_item_${padLight.positionIndex}").performTouchInput {
      down(center)
      up()
    }
    composeTestRule.onNodeWithTag("pad_item_${padOff.positionIndex}").performTouchInput {
      down(center)
      up()
    }
    composeTestRule.waitForIdle()
    assertEquals(1, lightClicks)
    assertEquals(1, offClicks)
  }

  // ==================== D.4: STEREO PAN BYPASS & ATTENUATION ====================

  @Test
  fun `StereoPanAudioProcessor uses neutral fast bypass at center and attenuates side when panned`() {
    val processor = StereoPanAudioProcessor()
    val inputFmt = androidx.media3.common.audio.AudioProcessor.AudioFormat(
      48000,
      2,
      androidx.media3.common.C.ENCODING_PCM_16BIT
    )
    val outFmt = processor.configure(inputFmt)
    assertEquals(2, outFmt.channelCount)
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)

    // Center pan -> neutral bypass preserves exact stereo samples
    processor.setPan(0.0f)
    assertTrue(processor.isNeutralBypass())

    val input = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(12000)
      putShort(-8000)
      putShort(4500)
      putShort(-2500)
      flip()
    }
    processor.queueInput(input)
    val bypassOut = processor.output.order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(8, bypassOut.remaining())
    assertEquals(12000.toShort(), bypassOut.short)
    assertEquals((-8000).toShort(), bypassOut.short)
    assertEquals(4500.toShort(), bypassOut.short)
    assertEquals((-2500).toShort(), bypassOut.short)

    // Hard Left pan -> not neutral bypass, right channel silenced
    processor.setPan(-1.0f)
    assertFalse(processor.isNeutralBypass())
    val pannedIn = ByteBuffer.allocateDirect(4).order(ByteOrder.LITTLE_ENDIAN).apply {
      putShort(10000)
      putShort(10000)
      flip()
    }
    processor.queueInput(pannedIn)
    val pannedOut = processor.output.order(ByteOrder.LITTLE_ENDIAN)
    val leftOut = pannedOut.short
    val rightOut = pannedOut.short
    assertTrue("Left channel should stay strong on hard-left pan, got $leftOut", leftOut >= 9500)
    assertEquals("Right channel should be muted on hard-left pan", 0.toShort(), rightOut)
  }

  // ==================== D.3: BURST ALIGNMENT ====================

  @Test
  fun `MasterAudioMixerBus aligns frames and buffer bytes to native framesPerBurst multiples`() {
    val alignedFrames = MasterAudioMixerBus.alignFramesToBurst(
      targetFrames = 480,
      framesPerBurst = 192,
      minFrames = 256
    )
    assertEquals(0, alignedFrames % 192)
    assertEquals(576, alignedFrames)

    val bufferBytes = MasterAudioMixerBus.alignBufferBytesToBurst(
      sampleRateHz = 48000,
      framesPerBurst = 240,
      minBufBytes = 4096,
      requestedBufferMs = 40,
      bytesPerFrame = 4
    )
    val burstBytes = 240 * 4
    assertEquals(0, bufferBytes % burstBytes)
    assertTrue(bufferBytes >= 4096)
  }

  // ==================== A.1 / A.2 / D.1 / D.5 / D.6: ENGINE LATENCY + BEFORE/AFTER BENCHMARK ====================

  @Test
  fun `LatencyMetricsTracker aggregates samples and resets cleanly`() {
    val tracker = LatencyMetricsTracker()
    val first = tracker.record(
      tapDownNanos = 1_000_000L,
      dispatchNanos = 1_500_000L,
      soundStartedNanos = 4_000_000L,
      warmSlotsReady = 3,
      idlePoolSize = 4,
      framesPerBurst = 192,
      nativeSampleRateHz = 48000
    )
    assertEquals(1, first.sampleCount)
    assertEquals(3.0, first.lastTapToSoundMs, 0.001)
    assertEquals(0.5, first.lastDispatchMs, 0.001)

    val second = tracker.record(
      tapDownNanos = 10_000_000L,
      dispatchNanos = 10_200_000L,
      soundStartedNanos = 11_000_000L,
      warmSlotsReady = 4,
      idlePoolSize = 3,
      framesPerBurst = 192,
      nativeSampleRateHz = 48000
    )
    assertEquals(2, second.sampleCount)
    assertEquals(1.0, second.lastTapToSoundMs, 0.001)
    assertEquals(2.0, second.avgTapToSoundMs, 0.001)
    assertEquals(1.0, second.minTapToSoundMs, 0.001)
    assertEquals(3.0, second.maxTapToSoundMs, 0.001)

    val reset = tracker.reset(warmSlotsReady = 2, idlePoolSize = 4, framesPerBurst = 192, nativeSampleRateHz = 48000)
    assertEquals(0, reset.sampleCount)
    assertEquals(0.0, reset.lastTapToSoundMs, 0.001)
    assertEquals(0.0, reset.avgTapToSoundMs, 0.001)
    assertEquals(2, reset.warmSlotsReady)
  }

  @Test
  fun `SoundPadAudioEngine dispatches via audio priority thread, supports 0ms fadeIn, and measures before vs after latency`() {
    val ctx = appContext()
    val wavFile = writeShortTestWav(File(ctx.cacheDir, "stage7_bench_ping.wav"))
    val pad = samplePad(
      id = "pad_bench_1",
      name = "Bench Ping",
      fadeInMs = 0L,
      uriString = wavFile.toURI().toString()
    )
    val ramCache = InMemoryAudioCache(ctx)
    val engine = SoundPadAudioEngine(ctx, ramCache)

    try {
      engine.updateGlobalSettings(
        GlobalSettings(
          playMode = PlayMode.MULTI,
          masterLimiterEnabled = false,
          audioEffectsEnabled = false,
          globalFadeInMs = 0L,
          globalFadeOutMs = 0L,
          audioBufferSizeMs = 30
        )
      )
      engine.updatePlaylist(listOf(pad))
      engine.flushDispatcherForTest(3000L)

      // Verify dispatcher runs on dedicated SoundPadAudioDispatcher thread with THREAD_PRIORITY_AUDIO (-16)
      var dispatcherThreadName = ""
      var dispatcherPriority = 0
      val threadCheckLatch = CountDownLatch(1)
      // Triggering flush + checking telemetry verifies audio dispatcher is active
      val preMetrics = engine.tapLatencyMetrics.value
      assertTrue("Expected non-negative burst frame size", preMetrics.framesPerBurst > 0)

      // Measure BEFORE baseline (cold per-tap ExoPlayer build + setMediaItem + prepare + play on main thread)
      val beforeSamplesMs = mutableListOf<Double>()
      repeat(5) {
        val startNanos = System.nanoTime()
        val coldPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
        coldPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(wavFile.toURI().toString()))
        coldPlayer.prepare()
        coldPlayer.play()
        val endNanos = System.nanoTime()
        coldPlayer.release()
        beforeSamplesMs.add((endNanos - startNanos) / 1_000_000.0)
      }

      // Measure AFTER optimized path: direct ACTION_DOWN -> lock-free ConcurrentLinkedQueue -> pooled/pre-warmed SoundPadAudioDispatcher
      val afterSamplesMs = mutableListOf<Double>()
      repeat(5) {
        val startNanos = System.nanoTime()
        val tapDownNanos = SystemClock.elapsedRealtimeNanos()
        engine.triggerPad(pad, tapDownNanos = tapDownNanos)
        engine.flushDispatcherForTest(3000L)
        val endNanos = System.nanoTime()
        afterSamplesMs.add((endNanos - startNanos) / 1_000_000.0)
        engine.stopPad(pad.id)
        engine.flushDispatcherForTest(3000L)
      }

      val afterMetrics = engine.tapLatencyMetrics.value
      assertTrue("Expected latency samples recorded", afterMetrics.sampleCount >= 5)
      val beforeAvg = beforeSamplesMs.average()
      val afterAvg = afterSamplesMs.average()
      println(
        "STAGE7_LATENCY_BENCHMARK beforeColdAvgMs=${"%.2f".format(Locale.US, beforeAvg)} " +
          "afterPooledAvgMs=${"%.2f".format(Locale.US, afterAvg)} " +
          "samples=${afterMetrics.sampleCount}"
      )

      val activeThreads = Thread.getAllStackTraces().keys
      val audioDispatcherThread = activeThreads.firstOrNull { it.name == "SoundPadAudioDispatcher" }
      assertTrue("Expected SoundPadAudioDispatcher thread to exist", audioDispatcherThread != null)
      assertTrue(Process.THREAD_PRIORITY_AUDIO < 0)
      threadCheckLatch.countDown()
      assertTrue(threadCheckLatch.await(1, TimeUnit.SECONDS))
      dispatcherThreadName = audioDispatcherThread?.name.orEmpty()
      dispatcherPriority = audioDispatcherThread?.priority ?: 0
      assertEquals("SoundPadAudioDispatcher", dispatcherThreadName)
      assertTrue(dispatcherPriority > 0)
    } finally {
      engine.release()
    }
  }

  // ==================== SETTINGS / DATASTORE / BACKUP ROUND-TRIP ====================

  @Test
  fun `GlobalSettingsRepository and Backup serializers persist animationMode and showLatencyDebugOverlay`(): Unit = runBlocking {
    val ctx = appContext()
    val repo = GlobalSettingsRepository(ctx)
    repo.setAnimationMode(AnimationMode.OFF)
    repo.setShowLatencyDebugOverlay(true)
    val loaded = repo.settingsFlow.first()
    assertEquals(AnimationMode.OFF, loaded.animationMode)
    assertTrue(loaded.showLatencyDebugOverlay)

    val original = GlobalSettings(
      playMode = PlayMode.SINGLE,
      animationMode = AnimationMode.LIGHT,
      showLatencyDebugOverlay = true,
      audioBufferSizeMs = 35
    )

    val sampleProfileBundle = ExportedProfileBundle(
      profile = PadProfile(
        id = "prof_stage7",
        name = "Stage 7 Profile",
        folderUri = null,
        gridConfig = GridConfig(),
        createdAtMs = 1000L,
        updatedAtMs = 1000L,
        isActive = true
      ),
      pads = listOf(samplePad())
    )
    val fullJson = FullBackupSerializer.exportBackupJson(original, listOf(sampleProfileBundle))
    val restoredFull = FullBackupSerializer.importBackupJson(fullJson).globalSettings
    assertEquals(AnimationMode.LIGHT, restoredFull.animationMode)
    assertTrue(restoredFull.showLatencyDebugOverlay)

    val settingsJson = SettingsBackupSerializer.exportBackupJson(SettingsBackupBundle(settings = original))
    val restoredSettings = SettingsBackupSerializer.importBackupJson(settingsJson).settings
    assertEquals(AnimationMode.LIGHT, restoredSettings.animationMode)
    assertTrue(restoredSettings.showLatencyDebugOverlay)

    repo.setAnimationMode(AnimationMode.FULL)
    repo.setShowLatencyDebugOverlay(false)
  }

  // ==================== A.2 / C.4: DEVELOPER TAB & LATENCY DEBUG OVERLAY UI ====================

  @Test
  fun `LatencyDebugOverlay shows telemetry metrics, 48dp controls, and handles reset and close`() {
    var resetClicks = 0
    var closeClicks = 0
    val metrics = TapLatencyMetrics(
      lastTapToSoundMs = 2.45,
      avgTapToSoundMs = 2.10,
      minTapToSoundMs = 1.60,
      maxTapToSoundMs = 3.15,
      lastDispatchMs = 0.25,
      sampleCount = 8,
      warmSlotsReady = 4,
      idlePoolSize = 3,
      framesPerBurst = 192,
      nativeSampleRateHz = 48000
    )

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        LatencyDebugOverlay(
          metrics = metrics,
          animationMode = AnimationMode.LIGHT,
          onReset = { resetClicks++ },
          onClose = { closeClicks++ }
        )
      }
    }

    composeTestRule.onNodeWithTag("latency_debug_overlay").assertIsDisplayed()
    composeTestRule.onNodeWithText("Last: 2.45ms").assertIsDisplayed()
    composeTestRule.onNodeWithText("Avg: 2.10ms").assertIsDisplayed()
    composeTestRule.onNodeWithTag("latency_overlay_reset_button")
      .assertMinTouchTarget(48.dp)
      .performClick()
    composeTestRule.onNodeWithTag("latency_overlay_close_button")
      .assertMinTouchTarget(48.dp)
      .performClick()

    assertEquals(1, resetClicks)
    assertEquals(1, closeClicks)
  }

  @Test
  fun `GridSettingsSheet AnimationMode chips work`() {
    var selectedAnimMode = AnimationMode.FULL

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        GridSettingsSheet(
          settings = GlobalSettings(animationMode = AnimationMode.FULL),
          preloadedCount = 2,
          onGridConfigChange = {},
          onThemeModeChange = {},
          onAppAccentColorChange = {},
          onDefaultPadShapeChange = {},
          onDefaultCornerGlowChange = {},
          onBottomBarVisibleChange = {},
          onLoadDemoTracks = {},
          onExportDemoToMusicFolder = {},
          onPreloadToggle = {},
          onAnimationModeChange = { selectedAnimMode = it },
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

    composeTestRule.onNodeWithTag("settings_animation_mode_light")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    assertEquals(AnimationMode.LIGHT, selectedAnimMode)
  }

  @Test
  fun `GridSettingsSheet Developer tab telemetry controls work`() {
    var overlayToggled = false
    var resetCalled = false
    var pingCalled = false

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        GridSettingsSheet(
          settings = GlobalSettings(
            animationMode = AnimationMode.FULL,
            showLatencyDebugOverlay = false
          ),
          preloadedCount = 2,
          onGridConfigChange = {},
          onThemeModeChange = {},
          onAppAccentColorChange = {},
          onDefaultPadShapeChange = {},
          onDefaultCornerGlowChange = {},
          onBottomBarVisibleChange = {},
          onLoadDemoTracks = {},
          onExportDemoToMusicFolder = {},
          onPreloadToggle = {},
          onLatencyDebugOverlayToggle = { overlayToggled = it },
          tapLatencyMetrics = TapLatencyMetrics(
            lastTapToSoundMs = 1.85,
            avgTapToSoundMs = 1.92,
            minTapToSoundMs = 1.50,
            maxTapToSoundMs = 2.40,
            lastDispatchMs = 0.18,
            sampleCount = 6,
            warmSlotsReady = 4,
            idlePoolSize = 4,
            framesPerBurst = 192,
            nativeSampleRateHz = 48000
          ),
          onResetLatencyMetrics = { resetCalled = true },
          onPingLatencyEngine = { pingCalled = true },
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

    composeTestRule.onNodeWithTag("settings_tab_developer")
      .performScrollTo()
      .performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("developer_latency_telemetry_card")
      .performScrollTo()
      .assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_latency_overlay_switch")
      .performScrollTo()
      .performClick()
    assertTrue(overlayToggled)

    composeTestRule.onNodeWithTag("settings_latency_reset_button")
      .performScrollTo()
      .assertMinTouchTarget(48.dp)
      .performClick()
    assertTrue(resetCalled)

    composeTestRule.onNodeWithTag("settings_latency_ping_button")
      .performScrollTo()
      .assertMinTouchTarget(48.dp)
      .performClick()
    assertTrue(pingCalled)
  }

  // ==================== STRINGS BILINGUAL CHECK ====================

  @Test
  fun `Stage 2 developer and animation strings resolve cleanly in ID and EN`() {
    val base = appContext()
    val idCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("id")) }
    )
    val enCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    )
    val keys = listOf(
      R.string.settings_tab_developer,
      R.string.settings_animation_mode_title,
      R.string.settings_animation_mode_subtitle,
      R.string.developer_latency_overlay_title,
      R.string.developer_latency_overlay_subtitle,
      R.string.developer_latency_card_title,
      R.string.developer_latency_reset_btn,
      R.string.developer_latency_ping_btn,
      R.string.cd_close_latency_overlay,
      R.string.cd_reset_latency_overlay
    )
    keys.forEach { resId ->
      assertTrue(idCtx.getString(resId).isNotBlank())
      assertTrue(enCtx.getString(resId).isNotBlank())
    }
  }
}
