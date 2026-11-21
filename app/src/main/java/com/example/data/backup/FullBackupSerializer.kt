package com.example.data.backup

import com.example.data.profile.ExportedProfileBundle
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
import org.json.JSONArray
import org.json.JSONObject

data class FullBackupPayload(
  val globalSettings: GlobalSettings,
  val profiles: List<ExportedProfileBundle>
)

object FullBackupSerializer {

  private const val BACKUP_SCHEMA_VERSION = 2

  fun exportBackupJson(
    settings: GlobalSettings,
    profiles: List<ExportedProfileBundle>
  ): String {
    val settingsJson = JSONObject().apply {
      put("activeProfileId", settings.activeProfileId)
      put("folderUri", settings.folderUri ?: JSONObject.NULL)
      put("folderName", settings.folderName ?: JSONObject.NULL)
      put("gridPreset", settings.gridConfig.preset.name)
      put("gridRows", settings.gridConfig.rows)
      put("gridCols", settings.gridConfig.cols)
      put("playMode", settings.playMode.name)
      put("masterVolume", settings.masterVolume.toDouble())
      put("masterPan", settings.masterPan.toDouble())
      put("masterEqEnabled", settings.masterEqEnabled)
      put("masterEqPreset", settings.masterEqPreset.name)
      put("masterEqBandsDb", ProfileJsonSerializer.encodeEqBandsToJsonArray(settings.masterEqBandsDb))
      put("miniWaveformEnabled", settings.miniWaveformEnabled)
      put("preloadShortAudio", settings.preloadShortAudio)
      put("audioEffectsEnabled", settings.audioEffectsEnabled)
      put("visualEffectsEnabled", settings.visualEffectsEnabled)
      put("keepScreenOn", settings.keepScreenOn)
      put("themeMode", settings.themeMode.name)
      put("useDynamicColor", settings.useDynamicColor)
      put("appAccentColorArgb", settings.appAccentColorArgb)
      put("padGridSpacingDp", settings.padGridSpacingDp)
      put("defaultPadShape", settings.defaultPadShape.name)
      put("defaultCornerGlowArgb", settings.defaultCornerGlowArgb)
      put("isBottomBarVisible", settings.isBottomBarVisible)
      put("isFullscreen", settings.isFullscreen)
      put("globalFadeInMs", settings.globalFadeInMs)
      put("globalFadeOutMs", settings.globalFadeOutMs)
      put("globalFadeCurve", settings.globalFadeCurve.name)
      put("globalCrossfadeEnabled", settings.globalCrossfadeEnabled)
      put("crossfadeDurationMs", settings.crossfadeDurationMs)
      put("crossfadeCurve", settings.crossfadeCurve.name)
      put("masterLimiterEnabled", settings.masterLimiterEnabled)
      put("duckingEnabled", settings.duckingEnabled)
      put("duckingAttenuation", settings.duckingAttenuation.toDouble())
      put("autoNormalizeEnabled", settings.autoNormalizeEnabled)
      put("isLocked", settings.isLocked)
      put("isGlobalCueEnabled", settings.isGlobalCueEnabled)
      put("hapticIntensity", settings.hapticIntensity.name)
      put("swipePadVolumeEnabled", settings.swipePadVolumeEnabled)
      put("doubleTapToStopEnabled", settings.doubleTapToStopEnabled)
      put("hasCompletedOnboarding", settings.hasCompletedOnboarding)
      put("showFeatureTooltips", settings.showFeatureTooltips)
      put("appLanguage", settings.appLanguage.name)
      put("defaultPadColorMode", settings.defaultPadColorMode.name)
      put("defaultPadColorArgb", settings.defaultPadColorArgb)
      put("isFocusMode", settings.isFocusMode)
      put("startInFocusMode", settings.startInFocusMode)
      put("focusCornerPanicEnabled", settings.focusCornerPanicEnabled)
      put("focusModeMidiNote", settings.focusModeMidiNote ?: JSONObject.NULL)
      put("preferredMainOutputId", settings.preferredMainOutputId ?: JSONObject.NULL)
      put("preferredMainOutputName", settings.preferredMainOutputName ?: JSONObject.NULL)
      put("preferredMainOutputProductName", settings.preferredMainOutputProductName ?: JSONObject.NULL)
      put("preferredMainOutputType", settings.preferredMainOutputType ?: JSONObject.NULL)
      put("preferredMainOutputAddress", settings.preferredMainOutputAddress ?: JSONObject.NULL)
      put("preferredCueOutputId", settings.preferredCueOutputId ?: JSONObject.NULL)
      put("preferredCueOutputName", settings.preferredCueOutputName ?: JSONObject.NULL)
      put("preferredCueOutputProductName", settings.preferredCueOutputProductName ?: JSONObject.NULL)
      put("preferredCueOutputType", settings.preferredCueOutputType ?: JSONObject.NULL)
      put("preferredCueOutputAddress", settings.preferredCueOutputAddress ?: JSONObject.NULL)
      put("cueVolume", settings.cueVolume.toDouble())
      put("audioBufferSizeMs", settings.audioBufferSizeMs)
      put("persistentBackupFolderUri", settings.persistentBackupFolderUri ?: JSONObject.NULL)
      put("persistentBackupFolderName", settings.persistentBackupFolderName ?: JSONObject.NULL)
      put("persistentAutoSyncEnabled", settings.persistentAutoSyncEnabled)
      put("persistentLastSyncAtMs", settings.persistentLastSyncAtMs)
      put("persistentIncludeWaveforms", settings.persistentIncludeWaveforms)
      put("dragDropConflictMode", settings.dragDropConflictMode.name)
      put("lastBleMidiAddress", settings.lastBleMidiAddress ?: JSONObject.NULL)
      put("lastBleMidiName", settings.lastBleMidiName ?: JSONObject.NULL)
      put("bleAutoReconnectEnabled", settings.bleAutoReconnectEnabled)
      put("animationMode", settings.animationMode.name)
      put("showLatencyDebugOverlay", settings.showLatencyDebugOverlay)
      put("retriggerMode", settings.retriggerMode.name)
      put("maxVoices", settings.maxVoices.coerceIn(1, 16))
      put("highContrastEnabled", settings.highContrastEnabled)
      put("colorBlindMode", settings.colorBlindMode.name)
    }

    val profilesArr = JSONArray()
    profiles.forEach { bundle ->
      profilesArr.put(
        ProfileJsonSerializer.toJsonObject(
          profile = bundle.profile,
          pads = bundle.pads,
          ccMappings = bundle.ccMappings,
          pcMappings = bundle.pcMappings,
          focusModeMidiNote = bundle.profile.focusModeMidiNote ?: bundle.focusModeMidiNote ?: settings.focusModeMidiNote
        )
      )
    }

    val root = JSONObject().apply {
      put("backupSchemaVersion", BACKUP_SCHEMA_VERSION)
      put("appName", "SoundPad Pro")
      put("exportedAtMs", System.currentTimeMillis())
      put("globalSettings", settingsJson)
      put("profiles", profilesArr)
    }
    return root.toString(2)
  }

  fun exportBackupJson(
    settings: GlobalSettings,
    profiles: List<ExportedProfileBundle>,
    passphrase: String?
  ): String {
    val plain = exportBackupJson(settings, profiles)
    val trimmed = passphrase?.trim().orEmpty()
    return if (trimmed.isEmpty()) plain else BackupCryptoManager.encryptText(plain, trimmed)
  }

  fun importBackupJson(jsonString: String): FullBackupPayload {
    return importBackupJson(jsonString, passphrase = null)
  }

  fun importBackupJson(rawPayload: String, passphrase: String?): FullBackupPayload {
    val jsonString = if (BackupCryptoManager.isEncryptedPayload(rawPayload)) {
      val trimmed = passphrase?.trim().orEmpty()
      if (trimmed.isEmpty()) {
        throw EncryptedBackupPassphraseRequiredException()
      }
      BackupCryptoManager.decryptText(rawPayload, trimmed)
    } else {
      rawPayload
    }
    val root = JSONObject(jsonString)
    val s = root.optJSONObject("globalSettings") ?: JSONObject()

    val gridPreset = runCatching {
      GridPreset.valueOf(s.optString("gridPreset", GridPreset.GRID_4X4.name))
    }.getOrDefault(GridPreset.GRID_4X4)

    val masterEqPreset = runCatching {
      EqPreset.valueOf(s.optString("masterEqPreset", EqPreset.FLAT.name))
    }.getOrDefault(EqPreset.FLAT)

    val settings = GlobalSettings(
      activeProfileId = s.optString("activeProfileId", "default_profile").ifBlank { "default_profile" },
      folderUri = if (s.isNull("folderUri")) null else s.optString("folderUri", "").ifBlank { null },
      folderName = if (s.isNull("folderName")) null else s.optString("folderName", "").ifBlank { null },
      gridConfig = GridConfig(
        preset = gridPreset,
        rows = s.optInt("gridRows", gridPreset.rows).coerceIn(1, 16),
        cols = s.optInt("gridCols", gridPreset.cols).coerceIn(1, 16)
      ),
      playMode = runCatching { PlayMode.valueOf(s.optString("playMode", PlayMode.SINGLE.name)) }
        .getOrDefault(PlayMode.SINGLE),
      masterVolume = s.optDouble("masterVolume", 1.0).toFloat().coerceIn(0f, 1f),
      masterPan = s.optDouble("masterPan", 0.0).toFloat().coerceIn(-1f, 1f),
      masterEqEnabled = s.optBoolean("masterEqEnabled", false),
      masterEqPreset = masterEqPreset,
      masterEqBandsDb = ProfileJsonSerializer.decodeEqBandsJsonArray(s.optJSONArray("masterEqBandsDb")),
      miniWaveformEnabled = s.optBoolean("miniWaveformEnabled", true),
      preloadShortAudio = s.optBoolean("preloadShortAudio", true),
      audioEffectsEnabled = s.optBoolean("audioEffectsEnabled", true),
      visualEffectsEnabled = s.optBoolean("visualEffectsEnabled", true),
      keepScreenOn = s.optBoolean("keepScreenOn", true),
      themeMode = runCatching { ThemeMode.valueOf(s.optString("themeMode", ThemeMode.DARK.name)) }
        .getOrDefault(ThemeMode.DARK),
      useDynamicColor = s.optBoolean("useDynamicColor", false),
      appAccentColorArgb = s.optLong("appAccentColorArgb", 0xFF00E5FFL),
      padGridSpacingDp = s.optInt("padGridSpacingDp", 8).coerceIn(2, 16),
      defaultPadShape = runCatching {
        PadShapeStyle.valueOf(s.optString("defaultPadShape", PadShapeStyle.SQUARE.name))
      }.getOrDefault(PadShapeStyle.SQUARE),
      defaultCornerGlowArgb = s.optLong("defaultCornerGlowArgb", 0xFF00E5FFL),
      isBottomBarVisible = s.optBoolean("isBottomBarVisible", true),
      isFullscreen = s.optBoolean("isFullscreen", false),
      globalFadeInMs = s.optLong("globalFadeInMs", 0L).coerceIn(0L, 10000L),
      globalFadeOutMs = s.optLong("globalFadeOutMs", 0L).coerceIn(0L, 10000L),
      globalFadeCurve = runCatching {
        FadeCurve.valueOf(s.optString("globalFadeCurve", FadeCurve.EQUAL_POWER.name))
      }.getOrDefault(FadeCurve.EQUAL_POWER),
      globalCrossfadeEnabled = s.optBoolean("globalCrossfadeEnabled", false),
      crossfadeDurationMs = s.optLong("crossfadeDurationMs", 2000L).coerceIn(200L, 12000L),
      crossfadeCurve = runCatching {
        FadeCurve.valueOf(s.optString("crossfadeCurve", FadeCurve.EQUAL_POWER.name))
      }.getOrDefault(FadeCurve.EQUAL_POWER),
      masterLimiterEnabled = s.optBoolean("masterLimiterEnabled", true),
      duckingEnabled = s.optBoolean("duckingEnabled", false),
      duckingAttenuation = s.optDouble("duckingAttenuation", 0.30).toFloat().coerceIn(0.05f, 0.90f),
      autoNormalizeEnabled = s.optBoolean("autoNormalizeEnabled", false),
      isLocked = s.optBoolean("isLocked", false),
      isGlobalCueEnabled = s.optBoolean("isGlobalCueEnabled", false),
      hapticIntensity = runCatching {
        HapticIntensity.valueOf(s.optString("hapticIntensity", HapticIntensity.SOFT.name))
      }.getOrDefault(HapticIntensity.SOFT),
      swipePadVolumeEnabled = s.optBoolean("swipePadVolumeEnabled", false),
      doubleTapToStopEnabled = s.optBoolean("doubleTapToStopEnabled", true),
      hasCompletedOnboarding = s.optBoolean("hasCompletedOnboarding", false),
      showFeatureTooltips = s.optBoolean("showFeatureTooltips", true),
      appLanguage = runCatching {
        AppLanguage.valueOf(s.optString("appLanguage", AppLanguage.SYSTEM.name))
      }.getOrDefault(AppLanguage.SYSTEM),
      defaultPadColorMode = runCatching {
        DefaultPadColorMode.valueOf(s.optString("defaultPadColorMode", DefaultPadColorMode.GRAY.name))
      }.getOrDefault(DefaultPadColorMode.GRAY),
      defaultPadColorArgb = s.optLong("defaultPadColorArgb", 0xFF4A4F57L),
      isFocusMode = s.optBoolean("isFocusMode", false),
      startInFocusMode = s.optBoolean("startInFocusMode", false),
      focusCornerPanicEnabled = s.optBoolean("focusCornerPanicEnabled", true),
      focusModeMidiNote = if (s.isNull("focusModeMidiNote")) null else s.optInt("focusModeMidiNote").coerceIn(0, 127),
      preferredMainOutputId = if (s.isNull("preferredMainOutputId")) null else s.optInt("preferredMainOutputId"),
      preferredMainOutputName = if (s.isNull("preferredMainOutputName")) null else s.optString("preferredMainOutputName", "").ifBlank { null },
      preferredMainOutputProductName = if (s.isNull("preferredMainOutputProductName")) null else s.optString("preferredMainOutputProductName", "").ifBlank { null },
      preferredMainOutputType = if (s.isNull("preferredMainOutputType")) null else s.optInt("preferredMainOutputType"),
      preferredMainOutputAddress = if (s.isNull("preferredMainOutputAddress")) null else s.optString("preferredMainOutputAddress", "").ifBlank { null },
      preferredCueOutputId = if (s.isNull("preferredCueOutputId")) null else s.optInt("preferredCueOutputId"),
      preferredCueOutputName = if (s.isNull("preferredCueOutputName")) null else s.optString("preferredCueOutputName", "").ifBlank { null },
      preferredCueOutputProductName = if (s.isNull("preferredCueOutputProductName")) null else s.optString("preferredCueOutputProductName", "").ifBlank { null },
      preferredCueOutputType = if (s.isNull("preferredCueOutputType")) null else s.optInt("preferredCueOutputType"),
      preferredCueOutputAddress = if (s.isNull("preferredCueOutputAddress")) null else s.optString("preferredCueOutputAddress", "").ifBlank { null },
      cueVolume = s.optDouble("cueVolume", 0.85).toFloat().coerceIn(0f, 1f),
      audioBufferSizeMs = s.optInt("audioBufferSizeMs", 50).coerceIn(20, 500),
      persistentBackupFolderUri = if (s.isNull("persistentBackupFolderUri")) null else s.optString("persistentBackupFolderUri", "").ifBlank { null },
      persistentBackupFolderName = if (s.isNull("persistentBackupFolderName")) null else s.optString("persistentBackupFolderName", "").ifBlank { null },
      persistentAutoSyncEnabled = s.optBoolean("persistentAutoSyncEnabled", true),
      persistentLastSyncAtMs = s.optLong("persistentLastSyncAtMs", 0L).coerceAtLeast(0L),
      persistentIncludeWaveforms = s.optBoolean("persistentIncludeWaveforms", false),
      dragDropConflictMode = runCatching {
        DragDropConflictMode.valueOf(s.optString("dragDropConflictMode", DragDropConflictMode.SWAP.name))
      }.getOrDefault(DragDropConflictMode.SWAP),
      lastBleMidiAddress = if (s.isNull("lastBleMidiAddress")) null else s.optString("lastBleMidiAddress", "").ifBlank { null },
      lastBleMidiName = if (s.isNull("lastBleMidiName")) null else s.optString("lastBleMidiName", "").ifBlank { null },
      bleAutoReconnectEnabled = s.optBoolean("bleAutoReconnectEnabled", true),
      animationMode = runCatching {
        AnimationMode.valueOf(
          s.optString(
            "animationMode",
            if (s.optBoolean("visualEffectsEnabled", true)) AnimationMode.FULL.name else AnimationMode.OFF.name
          )
        )
      }.getOrDefault(AnimationMode.FULL),
      showLatencyDebugOverlay = s.optBoolean("showLatencyDebugOverlay", false),
      retriggerMode = runCatching {
        RetriggerMode.valueOf(s.optString("retriggerMode", RetriggerMode.RESTART.name))
      }.getOrDefault(RetriggerMode.RESTART),
      maxVoices = s.optInt("maxVoices", 16).coerceIn(1, 16),
      highContrastEnabled = s.optBoolean("highContrastEnabled", false),
      colorBlindMode = runCatching {
        ColorBlindMode.valueOf(s.optString("colorBlindMode", ColorBlindMode.NONE.name))
      }.getOrDefault(ColorBlindMode.NONE)
    )

    val profilesArr = root.optJSONArray("profiles") ?: JSONArray()
    val profiles = mutableListOf<ExportedProfileBundle>()
    for (i in 0 until profilesArr.length()) {
      val obj = profilesArr.optJSONObject(i) ?: continue
      profiles.add(ProfileJsonSerializer.fromJsonObject(obj, generateFreshId = false))
    }
    require(profiles.isNotEmpty()) { "Backup contains no profiles" }

    return FullBackupPayload(globalSettings = settings, profiles = profiles)
  }
}
