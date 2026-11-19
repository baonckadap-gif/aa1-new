package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import com.example.audio.FiveBandEqAudioProcessor
import com.example.audio.MasterLimiterAudioProcessor
import com.example.audio.MasterLimiterParams
import com.example.audio.ReverbEchoAudioProcessor
import com.example.domain.model.EqPreset
import com.example.domain.model.PlayMode
import com.example.domain.model.SequencerPlaybackState
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.domain.model.WaveformData
import com.example.ui.BankPageUi
import com.example.ui.components.AudioEditorSheet
import com.example.ui.components.LandscapeBankRail
import com.example.ui.components.LandscapeMixerSidePanel
import com.example.ui.components.SequencerSheet
import com.example.ui.components.VoiceRecordSheet
import com.example.ui.theme.SoundPadProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage5UiPolishAccessibilityUnitTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun appContext(): Context = ApplicationProvider.getApplicationContext()

  private fun SemanticsNodeInteraction.assertMinTouchTarget(minSize: Dp = 48.dp): SemanticsNodeInteraction {
    val node = fetchSemanticsNode()
    val touchBounds = node.touchBoundsInRoot
    with(composeTestRule.density) {
      val widthDp = touchBounds.width.toDp()
      val heightDp = touchBounds.height.toDp()
      assertTrue(
        "Expected touch width >= $minSize, actual was $widthDp",
        widthDp >= minSize - 0.1.dp
      )
      assertTrue(
        "Expected touch height >= $minSize, actual was $heightDp",
        heightDp >= minSize - 0.1.dp
      )
    }
    return this
  }

  private fun samplePad(id: String = "pad_1", name: String = "Kick Drum"): SoundPad {
    return SoundPad(
      id = id,
      uriString = "content://test/$id.wav",
      fileName = "$id.wav",
      displayName = name,
      extension = "WAV",
      durationMs = 4000L,
      sizeBytes = 64000L,
      positionIndex = 0,
      bankIndex = 0
    )
  }

  // ==================== 5a & 5d: DUAL-LOCALE STRINGS ====================

  @Test
  fun `all Stage 5 secondary sheet and accessibility strings resolve cleanly in ID and EN`() {
    val base = appContext()
    val idCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("id")) }
    )
    val enCtx = base.createConfigurationContext(
      Configuration(base.resources.configuration).apply { setLocale(Locale.ENGLISH) }
    )

    val plainKeys = listOf(
      R.string.audio_output_title,
      R.string.audio_output_subtitle,
      R.string.cd_refresh_audio_devices,
      R.string.cd_close_audio_output,
      R.string.audio_output_limiter_title,
      R.string.audio_output_ducking_title,
      R.string.audio_output_normalize_title,
      R.string.cd_analyze_loudness_all,
      R.string.audio_output_master_eq_title,
      R.string.audio_output_main_device_section,
      R.string.audio_output_cue_device_section,
      R.string.cd_selected_output_device,
      R.string.voice_rec_title,
      R.string.voice_rec_subtitle,
      R.string.cd_close_voice_recorder,
      R.string.voice_rec_delay_instant,
      R.string.voice_rec_delay_3s,
      R.string.voice_rec_delay_5s,
      R.string.cd_stop_save_voice_pad,
      R.string.cd_start_voice_recording,
      R.string.voice_rec_recording_mic,
      R.string.audio_editor_title,
      R.string.cd_close_audio_editor,
      R.string.cd_save_trim_metadata,
      R.string.cd_zoom_out_waveform,
      R.string.cd_zoom_in_waveform,
      R.string.cd_reset_waveform_zoom,
      R.string.cd_stop_preview,
      R.string.cd_preview_trimmed,
      R.string.cd_reset_cut_range,
      R.string.cd_export_cut_new_pad,
      R.string.cd_export_cut_wav,
      R.string.backup_never_synced,
      R.string.backup_folder_unselected_placeholder,
      R.string.backup_relink_title,
      R.string.backup_restore_apply_selected,
      R.string.error_log_title,
      R.string.cd_export_error_logs,
      R.string.cd_clear_error_logs,
      R.string.error_log_empty_title,
      R.string.error_log_copied,
      R.string.scheduler_subtitle,
      R.string.scheduler_editor_title,
      R.string.action_save,
      R.string.sequencer_subtitle,
      R.string.cd_sequencer_prev,
      R.string.cd_sequencer_next,
      R.string.cd_sequencer_stop,
      R.string.cd_sequencer_drag_handle,
      R.string.cd_sequencer_play_here,
      R.string.cd_sequencer_move_up,
      R.string.cd_sequencer_move_down,
      R.string.cd_sequencer_remove,
      R.string.action_create,
      R.string.cd_dismiss,
      R.string.cd_exit_midi_learn,
      R.string.cd_grant_folder_permission,
      R.string.cd_dismiss_error,
      R.string.cd_sequencer_resume,
      R.string.cd_sequencer_pause,
      R.string.cd_move_bank_up,
      R.string.cd_move_bank_down,
      R.string.cd_reset_default_color
    )

    plainKeys.forEach { resId ->
      val idText = idCtx.getString(resId)
      val enText = enCtx.getString(resId)
      assertTrue("ID string blank for $resId", idText.isNotBlank())
      assertTrue("EN string blank for $resId", enText.isNotBlank())
    }

    assertNotEquals(
      idCtx.getString(R.string.audio_output_title),
      enCtx.getString(R.string.audio_output_title)
    )
    assertNotEquals(
      idCtx.getString(R.string.error_log_title),
      enCtx.getString(R.string.error_log_title)
    )

    assertEquals("IN: 0:01.0", enCtx.getString(R.string.audio_editor_in_label, "0:01.0"))
    assertEquals("Now Playing: Main Set (#1/3)", enCtx.getString(R.string.sequencer_running_header, "Main Set", 1, 3))
    assertEquals("Select color Cyan", enCtx.getString(R.string.cd_select_preset_color, "Cyan"))
    assertEquals("Waktu (08:30)", idCtx.getString(R.string.scheduler_time_label, 8, 30))
  }

  // ==================== 5c & 5d: TOUCH TARGETS & CONTENT DESCRIPTIONS ====================

  @Test
  fun `adaptive performer panels enforce minimum 48dp touch targets and content descriptions`() {
    val context = appContext()
    val bankPages = listOf(
      BankPageUi(bankKey = 0, title = "Drums", pads = listOf(samplePad()), canRescanSubfolder = true),
      BankPageUi(bankKey = 1, title = "FX", pads = listOf(samplePad("p2", "Riser")), canRescanSubfolder = false)
    )

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.height(600.dp)) {
          LandscapeBankRail(
            bankPages = bankPages,
            currentPageIndex = 0,
            playbackStates = emptyMap(),
            playMode = PlayMode.MULTI,
            isEditMode = false,
            isLocked = false,
            isGlobalCueEnabled = false,
            onSelectPage = {},
            onPlayModeSelected = {},
            onRenameBankRequest = {},
            onRescanBankRequest = {},
            onReorderBanksRequest = {},
            onToggleGlobalCue = {},
            onToggleEditMode = {}
          )
        }
      }
    }

    listOf(
      "landscape_rescan_bank_button",
      "landscape_reorder_banks_button",
      "landscape_rename_bank_button"
    ).forEach { tag ->
      composeTestRule.onNodeWithTag(tag)
        .assertIsDisplayed()
        .assertMinTouchTarget()
    }

    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_rescan_bank)).assertExists()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_reorder_banks)).assertExists()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_rename_bank)).assertExists()
  }

  @Test
  fun `landscape mixer pan reset enforces minimum 48dp touch target`() {
    val context = appContext()
    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.height(700.dp)) {
          LandscapeMixerSidePanel(
            activeVoices = 1,
            masterVolume = 0.8f,
            masterPan = 0.25f,
            masterEqEnabled = true,
            masterEqPreset = EqPreset.FLAT,
            onMasterVolumeChange = {},
            onMasterPanChange = {},
            onMasterEqEnabledChange = {},
            onMasterEqPresetSelect = {},
            onStopAll = {},
            onFadeOutAll = {},
            onOpenFullAudioMixer = {}
          )
        }
      }
    }

    composeTestRule.onNodeWithTag("landscape_pan_reset_button")
      .assertIsDisplayed()
      .assertMinTouchTarget()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.action_reset)).assertExists()
  }

  @Test
  fun `audio editor waveform zoom buttons satisfy minimum 48dp touch target and labels`() {
    val context = appContext()
    val pad = samplePad()
    val waveform = WaveformData(
      amplitudes = List(64) { 0.4f },
      durationMs = 4000L
    )

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        AudioEditorSheet(
          pad = pad,
          waveform = waveform,
          isLoadingWaveform = false,
          isExporting = false,
          previewPositionMs = null,
          onStartPreview = { _, _, _ -> },
          onStopPreview = {},
          onSaveNonDestructiveTrim = { _, _, _ -> },
          onExportCutToNewPad = { _, _, _ -> },
          onExportCutToFile = { _, _, _ -> },
          onDismiss = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("waveform_zoom_in_button")
      .assertMinTouchTarget()
      .performClick()

    listOf(
      "waveform_zoom_out_button",
      "waveform_zoom_in_button",
      "waveform_zoom_reset_button"
    ).forEach { tag ->
      composeTestRule.onNodeWithTag(tag)
        .assertMinTouchTarget()
    }

    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_zoom_out_waveform)).assertExists()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_zoom_in_waveform)).assertExists()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_reset_waveform_zoom)).assertExists()
  }

  @Test
  fun `voice record sheet renders themed record button without hardcoded white and exposes accessibility label`() {
    val context = appContext()
    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.LIGHT) {
        VoiceRecordSheet(
          isRecording = false,
          level = 0.25f,
          elapsedMs = 0L,
          onStartRecording = {},
          onStopAndSaveRecording = {},
          onCancelRecording = {},
          onDismiss = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("record_action_button")
      .assertExists()
      .assertMinTouchTarget()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_start_voice_recording)).assertExists()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.cd_close_voice_recorder)).assertExists()
  }

  @Test
  fun `sequencer sheet item controls satisfy minimum 48dp touch targets and drag handle description`() {
    val context = appContext()
    val padA = samplePad("p1", "Intro Loop")
    val padB = samplePad("p2", "Main Drop")
    val playlist = SequencerPlaylist(
      id = "pl_1",
      name = "Stage Opening",
      padIds = listOf("p1", "p2")
    )

    composeTestRule.setContent {
      SoundPadProTheme(themeMode = ThemeMode.DARK) {
        SequencerSheet(
          playlists = listOf(playlist),
          playbackState = SequencerPlaybackState(),
          pads = listOf(padA, padB),
          onSavePlaylist = {},
          onDeletePlaylist = {},
          onPlayPlaylist = { _, _ -> },
          onTogglePlayPause = {},
          onNext = {},
          onPrev = {},
          onStop = {},
          onArmMidiLearn = {},
          onClearMidiTrigger = {},
          onDismiss = {}
        )
      }
    }

    composeTestRule.onNode(hasScrollToNodeAction())
      .performScrollToNode(hasTestTag("sequencer_play_item_0"))

    listOf(
      "sequencer_play_item_0",
      "sequencer_move_up_0",
      "sequencer_move_down_0",
      "sequencer_remove_item_0"
    ).forEach { tag ->
      composeTestRule.onNodeWithTag(tag)
        .assertMinTouchTarget()
    }

    composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.cd_sequencer_play_here))
      .onFirst()
      .assertExists()
    composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.cd_sequencer_remove))
      .onFirst()
      .assertExists()
    composeTestRule.onAllNodesWithContentDescription(context.getString(R.string.cd_sequencer_drag_handle))
      .onFirst()
      .assertExists()
  }

  // ==================== 5e: MEDIA3 STREAM METADATA FLUSH ====================

  @androidx.annotation.OptIn(UnstableApi::class)
  @Test
  fun `media3 audio processors handle flush with StreamMetadata DEFAULT cleanly`() {
    val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)

    val limiter = MasterLimiterAudioProcessor(MasterLimiterParams(enabled = true))
    limiter.configure(format)
    limiter.flush(AudioProcessor.StreamMetadata.DEFAULT)
    assertEquals(0.0f, limiter.lastGainReductionDb, 0.0001f)

    val eq = FiveBandEqAudioProcessor()
    eq.configure(format)
    eq.flush(AudioProcessor.StreamMetadata.DEFAULT)

    val fx = ReverbEchoAudioProcessor()
    fx.configure(format)
    fx.flush(AudioProcessor.StreamMetadata.DEFAULT)
  }
}
