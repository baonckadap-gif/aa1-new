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
import com.example.domain.model.AppLanguage
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PlayMode
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
      appLanguage = appLanguage
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
    context.soundPadDataStore.edit { prefs -> prefs[Keys.VISUAL_EFFECTS_ENABLED] = enabled }
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
}
