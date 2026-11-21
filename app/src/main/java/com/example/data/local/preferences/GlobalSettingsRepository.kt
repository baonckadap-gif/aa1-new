package com.example.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.AnimationMode
import com.example.domain.model.AppLanguage
import com.example.domain.model.ColorBlindMode
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PlayMode
import com.example.domain.model.RetriggerMode
import com.example.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.soundPadDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "soundpad_global_settings"
)

class GlobalSettingsRepository(private val context: Context) {

  private object Keys {
    val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    val FOLDER_URI = stringPreferencesKey("folder_uri")
    val FOLDER_NAME = stringPreferencesKey("folder_name")
    val GRID_PRESET = stringPreferencesKey("grid_preset")
    val GRID_ROWS = intPreferencesKey("grid_rows")
    val GRID_COLS = intPreferencesKey("grid_cols")
    val PLAY_MODE = stringPreferencesKey("play_mode")
    val MASTER_VOLUME = floatPreferencesKey("master_volume")
    val MASTER_PAN = floatPreferencesKey("master_pan")
    val MASTER_EQ_ENABLED = booleanPreferencesKey("master_eq_enabled")
    val MASTER_EQ_PRESET = stringPreferencesKey("master_eq_preset")
    val MASTER_EQ_BANDS_JSON = stringPreferencesKey("master_eq_bands_json")
    val MINI_WAVEFORM_ENABLED = booleanPreferencesKey("mini_waveform_enabled")
    val PRELOAD_SHORT_AUDIO = booleanPreferencesKey("preload_short_audio")
    val AUDIO_EFFECTS_ENABLED = booleanPreferencesKey("audio_effects_enabled")
    val VISUAL_EFFECTS_ENABLED = booleanPreferencesKey("visual_effects_enabled")
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val APP_ACCENT_COLOR_ARGB = longPreferencesKey("app_accent_color_argb")
    val PAD_GRID_SPACING_DP = intPreferencesKey("pad_grid_spacing_dp")
    val DEFAULT_PAD_SHAPE = stringPreferencesKey("default_pad_shape")
    val DEFAULT_CORNER_GLOW_ARGB = longPreferencesKey("default_corner_glow_argb")
    val BOTTOM_BAR_VISIBLE = booleanPreferencesKey("bottom_bar_visible")
    val FULLSCREEN = booleanPreferencesKey("fullscreen_immersive")
    val GLOBAL_FADE_IN_MS = longPreferencesKey("global_fade_in_ms")
    val GLOBAL_FADE_OUT_MS = longPreferencesKey("global_fade_out_ms")
    val GLOBAL_FADE_CURVE = stringPreferencesKey("global_fade_curve")
    val GLOBAL_CROSSFADE_ENABLED = booleanPreferencesKey("global_crossfade_enabled")
    val CROSSFADE_DURATION_MS = longPreferencesKey("crossfade_duration_ms")
    val CROSSFADE_CURVE = stringPreferencesKey("crossfade_curve")
    val MASTER_LIMITER_ENABLED = booleanPreferencesKey("master_limiter_enabled")
    val DUCKING_ENABLED = booleanPreferencesKey("ducking_enabled")
    val DUCKING_ATTENUATION = floatPreferencesKey("ducking_attenuation")
    val AUTO_NORMALIZE_ENABLED = booleanPreferencesKey("auto_normalize_enabled")
    val IS_LOCKED = booleanPreferencesKey("is_locked")
    val IS_GLOBAL_CUE_ENABLED = booleanPreferencesKey("is_global_cue_enabled")
    val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
    val SWIPE_PAD_VOLUME_ENABLED = booleanPreferencesKey("swipe_pad_volume_enabled")
    val DOUBLE_TAP_STOP_ENABLED = booleanPreferencesKey("double_tap_stop_enabled")
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    val SHOW_FEATURE_TOOLTIPS = booleanPreferencesKey("show_feature_tooltips")
    val APP_LANGUAGE = stringPreferencesKey("app_language")
    val DEFAULT_PAD_COLOR_MODE = stringPreferencesKey("default_pad_color_mode")
    val DEFAULT_PAD_COLOR_ARGB = longPreferencesKey("default_pad_color_argb")
    val IS_FOCUS_MODE = booleanPreferencesKey("is_focus_mode")
    val START_IN_FOCUS_MODE = booleanPreferencesKey("start_in_focus_mode")
    val FOCUS_CORNER_PANIC_ENABLED = booleanPreferencesKey("focus_corner_panic_enabled")
    val FOCUS_MODE_MIDI_NOTE = intPreferencesKey("focus_mode_midi_note")
    val PREFERRED_MAIN_OUTPUT_ID = intPreferencesKey("preferred_main_output_id")
    val PREFERRED_MAIN_OUTPUT_NAME = stringPreferencesKey("preferred_main_output_name")
    val PREFERRED_MAIN_OUTPUT_PRODUCT_NAME = stringPreferencesKey("preferred_main_output_product_name")
    val PREFERRED_MAIN_OUTPUT_TYPE = intPreferencesKey("preferred_main_output_type")
    val PREFERRED_MAIN_OUTPUT_ADDRESS = stringPreferencesKey("preferred_main_output_address")
    val PREFERRED_CUE_OUTPUT_ID = intPreferencesKey("preferred_cue_output_id")
    val PREFERRED_CUE_OUTPUT_NAME = stringPreferencesKey("preferred_cue_output_name")
    val PREFERRED_CUE_OUTPUT_PRODUCT_NAME = stringPreferencesKey("preferred_cue_output_product_name")
    val PREFERRED_CUE_OUTPUT_TYPE = intPreferencesKey("preferred_cue_output_type")
    val PREFERRED_CUE_OUTPUT_ADDRESS = stringPreferencesKey("preferred_cue_output_address")
    val CUE_VOLUME = floatPreferencesKey("cue_volume")
    val AUDIO_BUFFER_SIZE_MS = intPreferencesKey("audio_buffer_size_ms")
    val PERSISTENT_BACKUP_FOLDER_URI = stringPreferencesKey("persistent_backup_folder_uri")
    val PERSISTENT_BACKUP_FOLDER_NAME = stringPreferencesKey("persistent_backup_folder_name")
    val PERSISTENT_AUTO_SYNC_ENABLED = booleanPreferencesKey("persistent_auto_sync_enabled")
    val PERSISTENT_LAST_SYNC_AT_MS = longPreferencesKey("persistent_last_sync_at_ms")
    val PERSISTENT_INCLUDE_WAVEFORMS = booleanPreferencesKey("persistent_include_waveforms")
    val HAS_PROMPTED_BACKUP_FOLDER = booleanPreferencesKey("has_prompted_backup_folder")
    val LAST_DAILY_SNAPSHOT_DAY = stringPreferencesKey("last_daily_snapshot_day")
    val LAST_BACKED_UP_VERSION_CODE = intPreferencesKey("last_backed_up_version_code")
    val DRAG_DROP_CONFLICT_MODE = stringPreferencesKey("drag_drop_conflict_mode")
    val LAST_BLE_MIDI_ADDRESS = stringPreferencesKey("last_ble_midi_address")
    val LAST_BLE_MIDI_NAME = stringPreferencesKey("last_ble_midi_name")
    val BLE_AUTO_RECONNECT_ENABLED = booleanPreferencesKey("ble_auto_reconnect_enabled")
    val ANIMATION_MODE = stringPreferencesKey("animation_mode")
    val SHOW_LATENCY_DEBUG_OVERLAY = booleanPreferencesKey("show_latency_debug_overlay")
    val RETRIGGER_MODE = stringPreferencesKey("retrigger_mode")
    val MAX_VOICES = intPreferencesKey("max_voices")
    val HIGH_CONTRAST_ENABLED = booleanPreferencesKey("high_contrast_enabled")
    val COLOR_BLIND_MODE = stringPreferencesKey("color_blind_mode")
  }

  val settingsFlow: Flow<GlobalSettings> = context.soundPadDataStore.data.map { prefs ->
    val preset = runCatching {
      prefs[Keys.GRID_PRESET]?.let { GridPreset.valueOf(it) }
    }.getOrNull() ?: GridPreset.GRID_4X4

    val rows = prefs[Keys.GRID_ROWS] ?: preset.rows
    val cols = prefs[Keys.GRID_COLS] ?: preset.cols

    val playMode = runCatching {
      prefs[Keys.PLAY_MODE]?.let { PlayMode.valueOf(it) }
    }.getOrNull() ?: PlayMode.SINGLE

    val themeMode = runCatching {
      prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) }
    }.getOrNull() ?: ThemeMode.DARK

    val globalFadeCurve = runCatching {
      prefs[Keys.GLOBAL_FADE_CURVE]?.let { FadeCurve.valueOf(it) }
    }.getOrNull() ?: FadeCurve.EQUAL_POWER

    val crossfadeCurve = runCatching {
      prefs[Keys.CROSSFADE_CURVE]?.let { FadeCurve.valueOf(it) }
    }.getOrNull() ?: FadeCurve.EQUAL_POWER

    val defaultPadShape = runCatching {
      prefs[Keys.DEFAULT_PAD_SHAPE]?.let { PadShapeStyle.valueOf(it) }
    }.getOrNull() ?: PadShapeStyle.SQUARE

    val masterEqPreset = runCatching {
      prefs[Keys.MASTER_EQ_PRESET]?.let { EqPreset.valueOf(it) }
    }.getOrNull() ?: EqPreset.FLAT
    val masterEqBands = ProfileJsonSerializer.decodeEqBandsString(prefs[Keys.MASTER_EQ_BANDS_JSON])

    val hapticIntensity = runCatching {
      prefs[Keys.HAPTIC_INTENSITY]?.let { HapticIntensity.valueOf(it) }
    }.getOrNull() ?: HapticIntensity.SOFT

    val appLanguage = runCatching {
      prefs[Keys.APP_LANGUAGE]?.let { AppLanguage.valueOf(it) }
    }.getOrNull() ?: AppLanguage.SYSTEM

    val defaultPadColorMode = runCatching {
      prefs[Keys.DEFAULT_PAD_COLOR_MODE]?.let { DefaultPadColorMode.valueOf(it) }
    }.getOrNull() ?: DefaultPadColorMode.GRAY

    val dragDropConflictMode = runCatching {
      prefs[Keys.DRAG_DROP_CONFLICT_MODE]?.let { DragDropConflictMode.valueOf(it) }
    }.getOrNull() ?: DragDropConflictMode.SWAP

    val visualEffectsEnabled = prefs[Keys.VISUAL_EFFECTS_ENABLED] ?: true
    val animationMode = runCatching {
      prefs[Keys.ANIMATION_MODE]?.let { AnimationMode.valueOf(it) }
    }.getOrNull() ?: if (visualEffectsEnabled) AnimationMode.FULL else AnimationMode.OFF
    val retriggerMode = runCatching {
      prefs[Keys.RETRIGGER_MODE]?.let { RetriggerMode.valueOf(it) }
    }.getOrNull() ?: RetriggerMode.RESTART
    val colorBlindMode = runCatching {
      prefs[Keys.COLOR_BLIND_MODE]?.let { ColorBlindMode.valueOf(it) }
    }.getOrNull() ?: ColorBlindMode.NONE

    GlobalSettings(
      activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: "default_profile",
      folderUri = prefs[Keys.FOLDER_URI],
      folderName = prefs[Keys.FOLDER_NAME],
      gridConfig = GridConfig(preset = preset, rows = rows.coerceIn(1, 16), cols = cols.coerceIn(1, 16)),
      playMode = playMode,
      masterVolume = (prefs[Keys.MASTER_VOLUME] ?: 1.0f).coerceIn(0f, 1f),
      masterPan = (prefs[Keys.MASTER_PAN] ?: 0.0f).coerceIn(-1f, 1f),
      masterEqEnabled = prefs[Keys.MASTER_EQ_ENABLED] ?: false,
      masterEqPreset = masterEqPreset,
      masterEqBandsDb = masterEqBands,
      miniWaveformEnabled = prefs[Keys.MINI_WAVEFORM_ENABLED] ?: true,
      preloadShortAudio = prefs[Keys.PRELOAD_SHORT_AUDIO] ?: true,
      audioEffectsEnabled = prefs[Keys.AUDIO_EFFECTS_ENABLED] ?: true,
      visualEffectsEnabled = prefs[Keys.VISUAL_EFFECTS_ENABLED] ?: true,
      keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
      themeMode = themeMode,
      useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
      appAccentColorArgb = prefs[Keys.APP_ACCENT_COLOR_ARGB] ?: 0xFF00E5FFL,
      padGridSpacingDp = (prefs[Keys.PAD_GRID_SPACING_DP] ?: 8).coerceIn(2, 16),
      defaultPadShape = defaultPadShape,
      defaultCornerGlowArgb = prefs[Keys.DEFAULT_CORNER_GLOW_ARGB] ?: 0xFF00E5FFL,
      isBottomBarVisible = prefs[Keys.BOTTOM_BAR_VISIBLE] ?: true,
      isFullscreen = prefs[Keys.FULLSCREEN] ?: false,
      globalFadeInMs = (prefs[Keys.GLOBAL_FADE_IN_MS] ?: 0L).coerceIn(0L, 10000L),
      globalFadeOutMs = (prefs[Keys.GLOBAL_FADE_OUT_MS] ?: 0L).coerceIn(0L, 10000L),
      globalFadeCurve = globalFadeCurve,
      globalCrossfadeEnabled = prefs[Keys.GLOBAL_CROSSFADE_ENABLED] ?: false,
      crossfadeDurationMs = (prefs[Keys.CROSSFADE_DURATION_MS] ?: 2000L).coerceIn(200L, 12000L),
      crossfadeCurve = crossfadeCurve,
      masterLimiterEnabled = prefs[Keys.MASTER_LIMITER_ENABLED] ?: true,
      duckingEnabled = prefs[Keys.DUCKING_ENABLED] ?: false,
      duckingAttenuation = (prefs[Keys.DUCKING_ATTENUATION] ?: 0.30f).coerceIn(0.05f, 0.90f),
      autoNormalizeEnabled = prefs[Keys.AUTO_NORMALIZE_ENABLED] ?: false,
      isLocked = prefs[Keys.IS_LOCKED] ?: false,
      isGlobalCueEnabled = prefs[Keys.IS_GLOBAL_CUE_ENABLED] ?: false,
      hapticIntensity = hapticIntensity,
      swipePadVolumeEnabled = prefs[Keys.SWIPE_PAD_VOLUME_ENABLED] ?: false,
      doubleTapToStopEnabled = prefs[Keys.DOUBLE_TAP_STOP_ENABLED] ?: true,
      hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
      showFeatureTooltips = prefs[Keys.SHOW_FEATURE_TOOLTIPS] ?: true,
      appLanguage = appLanguage,
      defaultPadColorMode = defaultPadColorMode,
      defaultPadColorArgb = prefs[Keys.DEFAULT_PAD_COLOR_ARGB] ?: 0xFF4A4F57L,
      isFocusMode = prefs[Keys.IS_FOCUS_MODE] ?: false,
      startInFocusMode = prefs[Keys.START_IN_FOCUS_MODE] ?: false,
      focusCornerPanicEnabled = prefs[Keys.FOCUS_CORNER_PANIC_ENABLED] ?: true,
      focusModeMidiNote = prefs[Keys.FOCUS_MODE_MIDI_NOTE],
      preferredMainOutputId = prefs[Keys.PREFERRED_MAIN_OUTPUT_ID],
      preferredMainOutputName = prefs[Keys.PREFERRED_MAIN_OUTPUT_NAME],
      preferredMainOutputProductName = prefs[Keys.PREFERRED_MAIN_OUTPUT_PRODUCT_NAME],
      preferredMainOutputType = prefs[Keys.PREFERRED_MAIN_OUTPUT_TYPE],
      preferredMainOutputAddress = prefs[Keys.PREFERRED_MAIN_OUTPUT_ADDRESS],
      preferredCueOutputId = prefs[Keys.PREFERRED_CUE_OUTPUT_ID],
      preferredCueOutputName = prefs[Keys.PREFERRED_CUE_OUTPUT_NAME],
      preferredCueOutputProductName = prefs[Keys.PREFERRED_CUE_OUTPUT_PRODUCT_NAME],
      preferredCueOutputType = prefs[Keys.PREFERRED_CUE_OUTPUT_TYPE],
      preferredCueOutputAddress = prefs[Keys.PREFERRED_CUE_OUTPUT_ADDRESS],
      cueVolume = (prefs[Keys.CUE_VOLUME] ?: 0.85f).coerceIn(0f, 1f),
      audioBufferSizeMs = (prefs[Keys.AUDIO_BUFFER_SIZE_MS] ?: 50).coerceIn(20, 500),
      persistentBackupFolderUri = prefs[Keys.PERSISTENT_BACKUP_FOLDER_URI],
      persistentBackupFolderName = prefs[Keys.PERSISTENT_BACKUP_FOLDER_NAME],
      persistentAutoSyncEnabled = prefs[Keys.PERSISTENT_AUTO_SYNC_ENABLED] ?: true,
      persistentLastSyncAtMs = prefs[Keys.PERSISTENT_LAST_SYNC_AT_MS] ?: 0L,
      persistentIncludeWaveforms = prefs[Keys.PERSISTENT_INCLUDE_WAVEFORMS] ?: false,
      dragDropConflictMode = dragDropConflictMode,
      lastBleMidiAddress = prefs[Keys.LAST_BLE_MIDI_ADDRESS],
      lastBleMidiName = prefs[Keys.LAST_BLE_MIDI_NAME],
      bleAutoReconnectEnabled = prefs[Keys.BLE_AUTO_RECONNECT_ENABLED] ?: true,
      animationMode = animationMode,
      showLatencyDebugOverlay = prefs[Keys.SHOW_LATENCY_DEBUG_OVERLAY] ?: false,
      retriggerMode = retriggerMode,
      maxVoices = (prefs[Keys.MAX_VOICES] ?: 16).coerceIn(1, 16),
      highContrastEnabled = prefs[Keys.HIGH_CONTRAST_ENABLED] ?: false,
      colorBlindMode = colorBlindMode
    )
  }

  suspend fun setActiveProfileId(profileId: String) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_PROFILE_ID] = profileId
    }
  }

  suspend fun setSelectedFolder(uri: String, name: String) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.FOLDER_URI] = uri
      prefs[Keys.FOLDER_NAME] = name
    }
  }

  suspend fun setGridConfig(config: GridConfig) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.GRID_PRESET] = config.preset.name
      prefs[Keys.GRID_ROWS] = config.rows.coerceIn(1, 16)
      prefs[Keys.GRID_COLS] = config.cols.coerceIn(1, 16)
    }
  }

  suspend fun setPlayMode(mode: PlayMode) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.PLAY_MODE] = mode.name }
  }

  suspend fun setMasterVolume(volume: Float) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.MASTER_VOLUME] = volume.coerceIn(0f, 1f) }
  }

  suspend fun setMasterPan(pan: Float) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.MASTER_PAN] = pan.coerceIn(-1f, 1f) }
  }

  suspend fun setMasterEq(enabled: Boolean, preset: EqPreset, bandsDb: List<Float>) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.MASTER_EQ_ENABLED] = enabled
      prefs[Keys.MASTER_EQ_PRESET] = preset.name
      prefs[Keys.MASTER_EQ_BANDS_JSON] = ProfileJsonSerializer.encodeEqBandsString(bandsDb)
    }
  }

  suspend fun setMiniWaveformEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.MINI_WAVEFORM_ENABLED] = enabled }
  }

  suspend fun setPreloadShortAudio(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.PRELOAD_SHORT_AUDIO] = enabled }
  }

  suspend fun setAudioEffectsEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.AUDIO_EFFECTS_ENABLED] = enabled }
  }

  suspend fun setVisualEffectsEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.VISUAL_EFFECTS_ENABLED] = enabled
      if (!enabled) {
        prefs[Keys.ANIMATION_MODE] = AnimationMode.OFF.name
      } else if (prefs[Keys.ANIMATION_MODE] == AnimationMode.OFF.name) {
        prefs[Keys.ANIMATION_MODE] = AnimationMode.FULL.name
      }
    }
  }

  suspend fun setAnimationMode(mode: AnimationMode) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.ANIMATION_MODE] = mode.name
      prefs[Keys.VISUAL_EFFECTS_ENABLED] = mode != AnimationMode.OFF
    }
  }

  suspend fun setShowLatencyDebugOverlay(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.SHOW_LATENCY_DEBUG_OVERLAY] = enabled
    }
  }

  suspend fun setRetriggerMode(mode: RetriggerMode) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.RETRIGGER_MODE] = mode.name
    }
  }

  suspend fun setMaxVoices(maxVoices: Int) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.MAX_VOICES] = maxVoices.coerceIn(1, 16)
    }
  }

  suspend fun setHighContrastEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.HIGH_CONTRAST_ENABLED] = enabled
    }
  }

  suspend fun setColorBlindMode(mode: ColorBlindMode) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.COLOR_BLIND_MODE] = mode.name
    }
  }

  suspend fun setKeepScreenOn(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.KEEP_SCREEN_ON] = enabled }
  }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setUseDynamicColor(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.USE_DYNAMIC_COLOR] = enabled }
  }

  suspend fun setAppAccentColor(colorArgb: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.APP_ACCENT_COLOR_ARGB] = colorArgb }
  }

  suspend fun setPadGridSpacingDp(spacingDp: Int) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.PAD_GRID_SPACING_DP] = spacingDp.coerceIn(2, 16) }
  }

  suspend fun setDefaultPadShape(shape: PadShapeStyle) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DEFAULT_PAD_SHAPE] = shape.name }
  }

  suspend fun setDefaultCornerGlow(colorArgb: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DEFAULT_CORNER_GLOW_ARGB] = colorArgb }
  }

  suspend fun setBottomBarVisible(visible: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.BOTTOM_BAR_VISIBLE] = visible }
  }

  suspend fun setFullscreen(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.FULLSCREEN] = enabled }
  }

  suspend fun setGlobalFadeInMs(durationMs: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.GLOBAL_FADE_IN_MS] = durationMs.coerceIn(0L, 10000L) }
  }

  suspend fun setGlobalFadeOutMs(durationMs: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.GLOBAL_FADE_OUT_MS] = durationMs.coerceIn(0L, 10000L) }
  }

  suspend fun setGlobalFadeCurve(curve: FadeCurve) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.GLOBAL_FADE_CURVE] = curve.name }
  }

  suspend fun setGlobalCrossfadeEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.GLOBAL_CROSSFADE_ENABLED] = enabled }
  }

  suspend fun setCrossfadeDurationMs(durationMs: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.CROSSFADE_DURATION_MS] = durationMs.coerceIn(200L, 12000L) }
  }

  suspend fun setCrossfadeCurve(curve: FadeCurve) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.CROSSFADE_CURVE] = curve.name }
  }

  suspend fun setMasterLimiterEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.MASTER_LIMITER_ENABLED] = enabled }
  }

  suspend fun setDuckingEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DUCKING_ENABLED] = enabled }
  }

  suspend fun setDuckingAttenuation(attenuation: Float) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DUCKING_ATTENUATION] = attenuation.coerceIn(0.05f, 0.90f) }
  }

  suspend fun setAutoNormalizeEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.AUTO_NORMALIZE_ENABLED] = enabled }
  }

  suspend fun setLocked(locked: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.IS_LOCKED] = locked }
  }

  suspend fun setGlobalCueEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.IS_GLOBAL_CUE_ENABLED] = enabled }
  }

  suspend fun setHapticIntensity(intensity: HapticIntensity) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.HAPTIC_INTENSITY] = intensity.name }
  }

  suspend fun setSwipePadVolumeEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.SWIPE_PAD_VOLUME_ENABLED] = enabled }
  }

  suspend fun setDoubleTapToStopEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DOUBLE_TAP_STOP_ENABLED] = enabled }
  }

  suspend fun setHasCompletedOnboarding(completed: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.HAS_COMPLETED_ONBOARDING] = completed }
  }

  suspend fun setShowFeatureTooltips(show: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.SHOW_FEATURE_TOOLTIPS] = show }
  }

  suspend fun setAppLanguage(language: AppLanguage) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.APP_LANGUAGE] = language.name }
  }

  suspend fun setDefaultPadColorMode(mode: DefaultPadColorMode) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DEFAULT_PAD_COLOR_MODE] = mode.name }
  }

  suspend fun setDefaultPadColorArgb(colorArgb: Long) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.DEFAULT_PAD_COLOR_ARGB] = colorArgb }
  }

  suspend fun setFocusMode(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.IS_FOCUS_MODE] = enabled }
  }

  suspend fun setStartInFocusMode(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.START_IN_FOCUS_MODE] = enabled }
  }

  suspend fun setFocusCornerPanicEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs -> prefs[Keys.FOCUS_CORNER_PANIC_ENABLED] = enabled }
  }

  suspend fun setFocusModeMidiNote(note: Int?) {
    context.soundPadDataStore.edit { prefs ->
      if (note == null) {
        prefs.remove(Keys.FOCUS_MODE_MIDI_NOTE)
      } else {
        prefs[Keys.FOCUS_MODE_MIDI_NOTE] = note.coerceIn(0, 127)
      }
    }
  }

  suspend fun setPreferredMainOutput(
    deviceId: Int?,
    deviceName: String?,
    productName: String? = null,
    deviceType: Int? = null,
    address: String? = null
  ) {
    context.soundPadDataStore.edit { prefs ->
      if (deviceId == null) prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_ID)
      else prefs[Keys.PREFERRED_MAIN_OUTPUT_ID] = deviceId
      if (deviceName.isNullOrBlank()) prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_NAME)
      else prefs[Keys.PREFERRED_MAIN_OUTPUT_NAME] = deviceName
      if (productName.isNullOrBlank()) prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_PRODUCT_NAME)
      else prefs[Keys.PREFERRED_MAIN_OUTPUT_PRODUCT_NAME] = productName
      if (deviceType == null) prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_TYPE)
      else prefs[Keys.PREFERRED_MAIN_OUTPUT_TYPE] = deviceType
      if (address.isNullOrBlank()) prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_ADDRESS)
      else prefs[Keys.PREFERRED_MAIN_OUTPUT_ADDRESS] = address
    }
  }

  suspend fun setPreferredCueOutput(
    deviceId: Int?,
    deviceName: String?,
    productName: String? = null,
    deviceType: Int? = null,
    address: String? = null
  ) {
    context.soundPadDataStore.edit { prefs ->
      if (deviceId == null) prefs.remove(Keys.PREFERRED_CUE_OUTPUT_ID)
      else prefs[Keys.PREFERRED_CUE_OUTPUT_ID] = deviceId
      if (deviceName.isNullOrBlank()) prefs.remove(Keys.PREFERRED_CUE_OUTPUT_NAME)
      else prefs[Keys.PREFERRED_CUE_OUTPUT_NAME] = deviceName
      if (productName.isNullOrBlank()) prefs.remove(Keys.PREFERRED_CUE_OUTPUT_PRODUCT_NAME)
      else prefs[Keys.PREFERRED_CUE_OUTPUT_PRODUCT_NAME] = productName
      if (deviceType == null) prefs.remove(Keys.PREFERRED_CUE_OUTPUT_TYPE)
      else prefs[Keys.PREFERRED_CUE_OUTPUT_TYPE] = deviceType
      if (address.isNullOrBlank()) prefs.remove(Keys.PREFERRED_CUE_OUTPUT_ADDRESS)
      else prefs[Keys.PREFERRED_CUE_OUTPUT_ADDRESS] = address
    }
  }

  suspend fun setCueVolume(volume: Float) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.CUE_VOLUME] = volume.coerceIn(0f, 1f)
    }
  }

  suspend fun setAudioBufferSizeMs(bufferMs: Int) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.AUDIO_BUFFER_SIZE_MS] = bufferMs.coerceIn(20, 500)
    }
  }

  suspend fun setPersistentBackupFolder(uri: String?, name: String?) {
    context.soundPadDataStore.edit { prefs ->
      if (uri.isNullOrBlank()) prefs.remove(Keys.PERSISTENT_BACKUP_FOLDER_URI)
      else prefs[Keys.PERSISTENT_BACKUP_FOLDER_URI] = uri
      if (name.isNullOrBlank()) prefs.remove(Keys.PERSISTENT_BACKUP_FOLDER_NAME)
      else prefs[Keys.PERSISTENT_BACKUP_FOLDER_NAME] = name
    }
  }

  suspend fun setPersistentAutoSyncEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.PERSISTENT_AUTO_SYNC_ENABLED] = enabled
    }
  }

  suspend fun setPersistentLastSyncAtMs(timestampMs: Long) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.PERSISTENT_LAST_SYNC_AT_MS] = timestampMs.coerceAtLeast(0L)
    }
  }

  suspend fun setPersistentIncludeWaveforms(include: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.PERSISTENT_INCLUDE_WAVEFORMS] = include
    }
  }

  val hasPromptedBackupFolderFlow: Flow<Boolean> = context.soundPadDataStore.data.map { prefs ->
    prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] ?: false
  }

  val persistentSyncMetaFlow: Flow<PersistentSyncInternalMeta> = context.soundPadDataStore.data.map { prefs ->
    PersistentSyncInternalMeta(
      hasPromptedBackupFolder = prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] ?: false,
      lastDailySnapshotDay = prefs[Keys.LAST_DAILY_SNAPSHOT_DAY] ?: "",
      lastBackedUpVersionCode = prefs[Keys.LAST_BACKED_UP_VERSION_CODE] ?: 0
    )
  }

  suspend fun setHasPromptedBackupFolder(prompted: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.HAS_PROMPTED_BACKUP_FOLDER] = prompted
    }
  }

  suspend fun setLastDailySnapshotDay(dayIso: String) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.LAST_DAILY_SNAPSHOT_DAY] = dayIso
    }
  }

  suspend fun setLastBackedUpVersionCode(versionCode: Int) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.LAST_BACKED_UP_VERSION_CODE] = versionCode.coerceAtLeast(0)
    }
  }

  suspend fun setDragDropConflictMode(mode: DragDropConflictMode) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.DRAG_DROP_CONFLICT_MODE] = mode.name
    }
  }

  suspend fun setLastBleMidiDevice(address: String?, name: String?) {
    context.soundPadDataStore.edit { prefs ->
      if (address.isNullOrBlank()) {
        prefs.remove(Keys.LAST_BLE_MIDI_ADDRESS)
      } else {
        prefs[Keys.LAST_BLE_MIDI_ADDRESS] = address
      }
      if (name.isNullOrBlank()) {
        prefs.remove(Keys.LAST_BLE_MIDI_NAME)
      } else {
        prefs[Keys.LAST_BLE_MIDI_NAME] = name
      }
    }
  }

  suspend fun setBleAutoReconnectEnabled(enabled: Boolean) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.BLE_AUTO_RECONNECT_ENABLED] = enabled
    }
  }

  suspend fun applyRestoredGlobalSettings(
    gs: GlobalSettings,
    restoreSyncFolderConfig: Boolean = false
  ) {
    context.soundPadDataStore.edit { prefs ->
      prefs[Keys.ACTIVE_PROFILE_ID] = gs.activeProfileId
      if (!gs.folderUri.isNullOrBlank()) {
        prefs[Keys.FOLDER_URI] = gs.folderUri
      }
      if (!gs.folderName.isNullOrBlank()) {
        prefs[Keys.FOLDER_NAME] = gs.folderName
      }
      prefs[Keys.GRID_PRESET] = gs.gridConfig.preset.name
      prefs[Keys.GRID_ROWS] = gs.gridConfig.rows.coerceIn(1, 16)
      prefs[Keys.GRID_COLS] = gs.gridConfig.cols.coerceIn(1, 16)
      prefs[Keys.PLAY_MODE] = gs.playMode.name
      prefs[Keys.MASTER_VOLUME] = gs.masterVolume.coerceIn(0f, 1f)
      prefs[Keys.MASTER_PAN] = gs.masterPan.coerceIn(-1f, 1f)
      prefs[Keys.MASTER_EQ_ENABLED] = gs.masterEqEnabled
      prefs[Keys.MASTER_EQ_PRESET] = gs.masterEqPreset.name
      prefs[Keys.MASTER_EQ_BANDS_JSON] = ProfileJsonSerializer.encodeEqBandsString(gs.masterEqBandsDb)
      prefs[Keys.MINI_WAVEFORM_ENABLED] = gs.miniWaveformEnabled
      prefs[Keys.PRELOAD_SHORT_AUDIO] = gs.preloadShortAudio
      prefs[Keys.AUDIO_EFFECTS_ENABLED] = gs.audioEffectsEnabled
      prefs[Keys.VISUAL_EFFECTS_ENABLED] = gs.visualEffectsEnabled
      prefs[Keys.KEEP_SCREEN_ON] = gs.keepScreenOn
      prefs[Keys.THEME_MODE] = gs.themeMode.name
      prefs[Keys.USE_DYNAMIC_COLOR] = gs.useDynamicColor
      prefs[Keys.APP_ACCENT_COLOR_ARGB] = gs.appAccentColorArgb
      prefs[Keys.PAD_GRID_SPACING_DP] = gs.padGridSpacingDp.coerceIn(2, 16)
      prefs[Keys.DEFAULT_PAD_SHAPE] = gs.defaultPadShape.name
      prefs[Keys.DEFAULT_CORNER_GLOW_ARGB] = gs.defaultCornerGlowArgb
      prefs[Keys.BOTTOM_BAR_VISIBLE] = gs.isBottomBarVisible
      prefs[Keys.FULLSCREEN] = gs.isFullscreen
      prefs[Keys.GLOBAL_FADE_IN_MS] = gs.globalFadeInMs.coerceIn(0L, 10000L)
      prefs[Keys.GLOBAL_FADE_OUT_MS] = gs.globalFadeOutMs.coerceIn(0L, 10000L)
      prefs[Keys.GLOBAL_FADE_CURVE] = gs.globalFadeCurve.name
      prefs[Keys.GLOBAL_CROSSFADE_ENABLED] = gs.globalCrossfadeEnabled
      prefs[Keys.CROSSFADE_DURATION_MS] = gs.crossfadeDurationMs.coerceIn(200L, 12000L)
      prefs[Keys.CROSSFADE_CURVE] = gs.crossfadeCurve.name
      prefs[Keys.MASTER_LIMITER_ENABLED] = gs.masterLimiterEnabled
      prefs[Keys.DUCKING_ENABLED] = gs.duckingEnabled
      prefs[Keys.DUCKING_ATTENUATION] = gs.duckingAttenuation.coerceIn(0.05f, 0.90f)
      prefs[Keys.AUTO_NORMALIZE_ENABLED] = gs.autoNormalizeEnabled
      prefs[Keys.IS_LOCKED] = gs.isLocked
      prefs[Keys.IS_GLOBAL_CUE_ENABLED] = gs.isGlobalCueEnabled
      prefs[Keys.HAPTIC_INTENSITY] = gs.hapticIntensity.name
      prefs[Keys.SWIPE_PAD_VOLUME_ENABLED] = gs.swipePadVolumeEnabled
      prefs[Keys.DOUBLE_TAP_STOP_ENABLED] = gs.doubleTapToStopEnabled
      prefs[Keys.SHOW_FEATURE_TOOLTIPS] = gs.showFeatureTooltips
      prefs[Keys.APP_LANGUAGE] = gs.appLanguage.name
      prefs[Keys.DEFAULT_PAD_COLOR_MODE] = gs.defaultPadColorMode.name
      prefs[Keys.DEFAULT_PAD_COLOR_ARGB] = gs.defaultPadColorArgb
      prefs[Keys.IS_FOCUS_MODE] = gs.isFocusMode
      prefs[Keys.START_IN_FOCUS_MODE] = gs.startInFocusMode
      prefs[Keys.FOCUS_CORNER_PANIC_ENABLED] = gs.focusCornerPanicEnabled
      if (gs.focusModeMidiNote != null) {
        prefs[Keys.FOCUS_MODE_MIDI_NOTE] = gs.focusModeMidiNote.coerceIn(0, 127)
      } else {
        prefs.remove(Keys.FOCUS_MODE_MIDI_NOTE)
      }
      if (gs.preferredMainOutputId != null) {
        prefs[Keys.PREFERRED_MAIN_OUTPUT_ID] = gs.preferredMainOutputId
      } else {
        prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_ID)
      }
      if (!gs.preferredMainOutputName.isNullOrBlank()) {
        prefs[Keys.PREFERRED_MAIN_OUTPUT_NAME] = gs.preferredMainOutputName
      } else {
        prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_NAME)
      }
      if (!gs.preferredMainOutputProductName.isNullOrBlank()) {
        prefs[Keys.PREFERRED_MAIN_OUTPUT_PRODUCT_NAME] = gs.preferredMainOutputProductName
      } else {
        prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_PRODUCT_NAME)
      }
      if (gs.preferredMainOutputType != null) {
        prefs[Keys.PREFERRED_MAIN_OUTPUT_TYPE] = gs.preferredMainOutputType
      } else {
        prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_TYPE)
      }
      if (!gs.preferredMainOutputAddress.isNullOrBlank()) {
        prefs[Keys.PREFERRED_MAIN_OUTPUT_ADDRESS] = gs.preferredMainOutputAddress
      } else {
        prefs.remove(Keys.PREFERRED_MAIN_OUTPUT_ADDRESS)
      }
      if (gs.preferredCueOutputId != null) {
        prefs[Keys.PREFERRED_CUE_OUTPUT_ID] = gs.preferredCueOutputId
      } else {
        prefs.remove(Keys.PREFERRED_CUE_OUTPUT_ID)
      }
      if (!gs.preferredCueOutputName.isNullOrBlank()) {
        prefs[Keys.PREFERRED_CUE_OUTPUT_NAME] = gs.preferredCueOutputName
      } else {
        prefs.remove(Keys.PREFERRED_CUE_OUTPUT_NAME)
      }
      if (!gs.preferredCueOutputProductName.isNullOrBlank()) {
        prefs[Keys.PREFERRED_CUE_OUTPUT_PRODUCT_NAME] = gs.preferredCueOutputProductName
      } else {
        prefs.remove(Keys.PREFERRED_CUE_OUTPUT_PRODUCT_NAME)
      }
      if (gs.preferredCueOutputType != null) {
        prefs[Keys.PREFERRED_CUE_OUTPUT_TYPE] = gs.preferredCueOutputType
      } else {
        prefs.remove(Keys.PREFERRED_CUE_OUTPUT_TYPE)
      }
      if (!gs.preferredCueOutputAddress.isNullOrBlank()) {
        prefs[Keys.PREFERRED_CUE_OUTPUT_ADDRESS] = gs.preferredCueOutputAddress
      } else {
        prefs.remove(Keys.PREFERRED_CUE_OUTPUT_ADDRESS)
      }
      prefs[Keys.CUE_VOLUME] = gs.cueVolume.coerceIn(0f, 1f)
      prefs[Keys.AUDIO_BUFFER_SIZE_MS] = gs.audioBufferSizeMs.coerceIn(20, 500)
      prefs[Keys.PERSISTENT_AUTO_SYNC_ENABLED] = gs.persistentAutoSyncEnabled
      prefs[Keys.PERSISTENT_INCLUDE_WAVEFORMS] = gs.persistentIncludeWaveforms
      prefs[Keys.DRAG_DROP_CONFLICT_MODE] = gs.dragDropConflictMode.name
      if (!gs.lastBleMidiAddress.isNullOrBlank()) {
        prefs[Keys.LAST_BLE_MIDI_ADDRESS] = gs.lastBleMidiAddress
      } else {
        prefs.remove(Keys.LAST_BLE_MIDI_ADDRESS)
      }
      if (!gs.lastBleMidiName.isNullOrBlank()) {
        prefs[Keys.LAST_BLE_MIDI_NAME] = gs.lastBleMidiName
      } else {
        prefs.remove(Keys.LAST_BLE_MIDI_NAME)
      }
      prefs[Keys.BLE_AUTO_RECONNECT_ENABLED] = gs.bleAutoReconnectEnabled
      prefs[Keys.ANIMATION_MODE] = gs.animationMode.name
      prefs[Keys.SHOW_LATENCY_DEBUG_OVERLAY] = gs.showLatencyDebugOverlay
      prefs[Keys.RETRIGGER_MODE] = gs.retriggerMode.name
      prefs[Keys.MAX_VOICES] = gs.maxVoices.coerceIn(1, 16)
      prefs[Keys.HIGH_CONTRAST_ENABLED] = gs.highContrastEnabled
      prefs[Keys.COLOR_BLIND_MODE] = gs.colorBlindMode.name
      if (restoreSyncFolderConfig) {
        if (!gs.persistentBackupFolderUri.isNullOrBlank()) {
          prefs[Keys.PERSISTENT_BACKUP_FOLDER_URI] = gs.persistentBackupFolderUri
        }
        if (!gs.persistentBackupFolderName.isNullOrBlank()) {
          prefs[Keys.PERSISTENT_BACKUP_FOLDER_NAME] = gs.persistentBackupFolderName
        }
      }
    }
  }

  suspend fun resetAllSettingsToDefaults(clearBackupFolder: Boolean = true) {
    context.soundPadDataStore.edit { prefs ->
      val savedBackupUri = prefs[Keys.PERSISTENT_BACKUP_FOLDER_URI]
      val savedBackupName = prefs[Keys.PERSISTENT_BACKUP_FOLDER_NAME]
      prefs.clear()
      prefs[Keys.HAS_COMPLETED_ONBOARDING] = true
      if (!clearBackupFolder && !savedBackupUri.isNullOrBlank()) {
        prefs[Keys.PERSISTENT_BACKUP_FOLDER_URI] = savedBackupUri
        if (!savedBackupName.isNullOrBlank()) {
          prefs[Keys.PERSISTENT_BACKUP_FOLDER_NAME] = savedBackupName
        }
      }
    }
  }
}

data class PersistentSyncInternalMeta(
  val hasPromptedBackupFolder: Boolean = false,
  val lastDailySnapshotDay: String = "",
  val lastBackedUpVersionCode: Int = 0
)
