package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.example.audio.ReverbEchoAudioProcessor
import com.example.audio.ReverbEchoParams
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadSettings
import com.example.domain.model.SoundPad
import com.example.ui.SoundPadScreen
import com.example.ui.SoundPadUiState
import com.example.ui.theme.SoundPadProTheme
import com.example.util.DeviceMemoryHelper
import com.example.util.DeviceMemoryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage9BaselineProfileAndLowRamUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun samplePcmBuffer(samples: Int = 64): ByteBuffer {
    return ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
      repeat(samples) { idx -> putShort(((idx * 17) % 1000).toShort()) }
      flip()
    }
  }

  private fun buildPads(count: Int): List<SoundPad> {
    return (0 until count).map { idx ->
      SoundPad(
        id = "pad_$idx",
        uriString = "content://test/pad_$idx.wav",
        fileName = "pad_$idx.wav",
        displayName = "Pad $idx",
        extension = "WAV",
        durationMs = 1500L,
        sizeBytes = 32000L,
        positionIndex = idx,
        bankIndex = 0,
        settings = PadSettings()
      )
    }
  }

  @Test
  fun `low ram threshold triggers only when active pads exceed 12 on low ram or low memoryClass`() {
    assertFalse(
      DeviceMemoryHelper.shouldWarnLowRamActivePads(
        isLowRamDevice = true,
        memoryClassMb = 96,
        activePadCount = 12
      )
    )
    assertTrue(
      DeviceMemoryHelper.shouldWarnLowRamActivePads(
        isLowRamDevice = true,
        memoryClassMb = 256,
        activePadCount = 13
      )
    )
    assertTrue(
      DeviceMemoryHelper.shouldWarnLowRamActivePads(
        isLowRamDevice = false,
        memoryClassMb = 128,
        activePadCount = 13
      )
    )
    assertTrue(
      DeviceMemoryHelper.shouldWarnLowRamActivePads(
        isLowRamDevice = false,
        memoryClassMb = 256,
        activePadCount = 14,
        isSystemLowMemory = true
      )
    )
    assertFalse(
      DeviceMemoryHelper.shouldWarnLowRamActivePads(
        isLowRamDevice = false,
        memoryClassMb = 512,
        activePadCount = 16,
        isSystemLowMemory = false
      )
    )
  }

  @Test
  fun `uiState shows low ram warning above 12 playing pads and clears on dismiss`() {
    val playing13 = (0 until 13).associate { i ->
      "pad_$i" to PadPlaybackState(
        padId = "pad_$i",
        isPlaying = true,
        positionMs = 100L,
        durationMs = 1500L,
        progress = 0.1f,
        activeVoiceCount = 1
      )
    }
    val lowRam = DeviceMemoryInfo(isLowRamDevice = false, memoryClassMb = 128, isSystemLowMemory = false)
    val state = SoundPadUiState(
      playbackStates = playing13,
      deviceMemoryInfo = lowRam,
      isLowRamWarningDismissed = false
    )
    assertEquals(13, state.activeCount)
    assertTrue(state.showLowRamActivePadsWarning)

    val dismissed = state.copy(isLowRamWarningDismissed = true)
    assertFalse(dismissed.showLowRamActivePadsWarning)

    val playing12 = playing13.entries.take(12).associate { it.key to it.value }
    assertFalse(state.copy(playbackStates = playing12).showLowRamActivePadsWarning)
  }

  @Test
  fun `low ram warning banner displays and handles stop all and dismiss in SoundPadScreen`() {
    val pads = buildPads(14)
    val playing13 = pads.take(13).associate { p ->
      p.id to PadPlaybackState(
        padId = p.id,
        isPlaying = true,
        positionMs = 100L,
        durationMs = 1500L,
        progress = 0.1f,
        activeVoiceCount = 1
      )
    }
    var stopAllClicks = 0
    var dismissClicks = 0

    composeTestRule.setContent {
      SoundPadProTheme(darkTheme = true) {
        SoundPadScreen(
          uiState = SoundPadUiState(
            settings = GlobalSettings(
              folderUri = "content://demo",
              gridConfig = GridConfig(GridPreset.GRID_15X15, 15, 15),
              hasCompletedOnboarding = true
            ),
            pads = pads,
            playbackStates = playing13,
            deviceMemoryInfo = DeviceMemoryInfo(
              isLowRamDevice = true,
              memoryClassMb = 96,
              isSystemLowMemory = false
            )
          ),
          onPickFolderClick = {},
          onRescanClick = {},
          onToggleSearch = {},
          onSearchQueryChange = {},
          onPlayModeSelected = {},
          onGridConfigChanged = {},
          onThemeModeChanged = {},
          onAppAccentColorChanged = {},
          onDefaultPadShapeChanged = {},
          onDefaultCornerGlowChanged = {},
          onBottomBarVisibleChanged = {},
          onLoadDemoTracks = {},
          onExportDemoToMusicFolder = {},
          onToggleCrossfade = {},
          onCrossfadeDurationChange = {},
          onCrossfadeCurveChange = {},
          onGlobalFadeInChange = {},
          onGlobalFadeOutChange = {},
          onGlobalFadeCurveChange = {},
          onToggleFullscreen = {},
          onMasterVolumeChanged = {},
          onPreloadToggle = {},
          onKeepScreenOnToggle = {},
          onToggleEditMode = {},
          onPadTapped = {},
          onPadCustomizeClick = {},
          onOpenTrackCustomizer = {},
          onSelectEditingPad = {},
          onPadHoldStart = {},
          onPadHoldRelease = {},
          onSwapPads = { _, _ -> },
          onSavePadAppearance = { _, _ -> },
          onApplyStyleToAllPads = {},
          onUpdatePadSettings = { _, _ -> },
          onOpenAudioEditor = {},
          onCloseAudioEditor = {},
          onStartEditorPreview = { _, _, _ -> },
          onStopEditorPreview = {},
          onSaveNonDestructiveTrim = { _, _, _ -> },
          onExportCutAsNewPad = { _, _, _ -> },
          onExportCutToFile = { _, _, _ -> },
          onOpenVoiceRecorder = {},
          onCloseVoiceRecorder = {},
          onStartVoiceRecording = {},
          onStopAndSaveVoiceRecording = {},
          onCancelVoiceRecording = {},
          onDuplicatePad = {},
          onDeletePad = {},
          onClosePadEditor = {},
          onSaveNewProfile = {},
          onLoadProfile = {},
          onDuplicateProfile = {},
          onDeleteProfile = {},
          onExportProfileJson = {},
          onImportProfileJson = {},
          onToggleMidiLearnMode = {},
          onArmMidiLearnForPad = {},
          onClearPadMidiNote = {},
          onArmMidiLearnForCc = {},
          onClearMidiCcMapping = {},
          onCancelMidiLearn = {},
          onRefreshMidiDevices = {},
          onStartBleMidiScan = {},
          onStopBleMidiScan = {},
          onConnectBleMidiDevice = {},
          onStopAll = { stopAllClicks++ },
          onDismissNewFilesNotice = {},
          onDismissStatus = {},
          onDismissError = {},
          onDismissLowRamWarning = { dismissClicks++ }
        )
      }
    }

    composeTestRule.onNodeWithTag("low_ram_active_pads_warning_banner").assertIsDisplayed()
    composeTestRule.onNodeWithTag("pad_grid_scroll").assertExists()
    composeTestRule.onNodeWithTag("low_ram_warning_stop_all_button").performClick()
    composeTestRule.onNodeWithTag("low_ram_warning_dismiss_button").performClick()
    assertEquals(1, stopAllClicks)
    assertEquals(1, dismissClicks)
  }

  @androidx.annotation.OptIn(UnstableApi::class)
  @Test
  fun `reverb and echo buffers are released immediately when turned OFF`() {
    val processor = ReverbEchoAudioProcessor()
    val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
    processor.configure(format)
    processor.flush(AudioProcessor.StreamMetadata.DEFAULT)

    // Initially bypassed: nothing allocated
    assertFalse(processor.hasAllocatedBuffers())
    val baselineSamples = processor.totalAllocatedSampleCountForTest()
    assertTrue(baselineSamples < 32)

    // Enable both Echo and Reverb and feed audio -> buffers allocate
    processor.setParams(
      ReverbEchoParams(
        echoEnabled = true,
        echoMix = 0.4f,
        reverbEnabled = true,
        reverbMix = 0.4f
      )
    )
    processor.queueInput(samplePcmBuffer())
    assertTrue(processor.hasAllocatedEchoBuffers())
    assertTrue(processor.hasAllocatedReverbBuffers())
    assertTrue(processor.totalAllocatedSampleCountForTest() > 50_000)

    // Turn Echo OFF while keeping Reverb ON -> only Echo buffers released
    processor.setParams(
      ReverbEchoParams(
        echoEnabled = false,
        echoMix = 0.4f,
        reverbEnabled = true,
        reverbMix = 0.4f
      )
    )
    assertFalse(processor.hasAllocatedEchoBuffers())
    assertTrue(processor.hasAllocatedReverbBuffers())

    // Turn Reverb OFF too -> all DSP buffers released back to minimal footprint
    processor.setParams(ReverbEchoParams())
    assertFalse(processor.hasAllocatedBuffers())
    assertEquals(baselineSamples, processor.totalAllocatedSampleCountForTest())
  }

  @Test
  fun `profileinstaller library and memory inspector are available on runtime classpath`() {
    val cls = Class.forName("androidx.profileinstaller.ProfileInstaller")
    assertNotNull(cls)
    val context = ApplicationProvider.getApplicationContext<Context>()
    val info = DeviceMemoryHelper.inspect(context)
    assertTrue(info.memoryClassMb >= 16)
  }

  @Test
  @Config(sdk = [36], application = SoundPadApplication::class)
  fun `SoundPadApplication onCreate resolves CrashLogManager without error`() {
    val app = ApplicationProvider.getApplicationContext<SoundPadApplication>()
    assertNotNull(app)
    app.onCreate()
    assertNotNull(Class.forName("com.example.error.CrashLogManager"))
  }
}
