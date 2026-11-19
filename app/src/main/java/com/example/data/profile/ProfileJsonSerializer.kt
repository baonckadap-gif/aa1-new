package com.example.data.profile

import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.ui.PadGridSlotManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ExportedProfileBundle(
  val profile: PadProfile,
  val pads: List<SoundPad>,
  val ccMappings: List<MidiCcMapping> = emptyList()
)

object ProfileJsonSerializer {

  private const val SCHEMA_VERSION = 6

  fun exportToJson(
    profile: PadProfile,
    pads: List<SoundPad>,
    ccMappings: List<MidiCcMapping> = emptyList()
  ): String {
    return toJsonObject(profile, pads, ccMappings).toString(2)
  }

  fun toJsonObject(
    profile: PadProfile,
    pads: List<SoundPad>,
    ccMappings: List<MidiCcMapping> = emptyList()
  ): JSONObject {
    return JSONObject().apply {
      put("schemaVersion", SCHEMA_VERSION)
      put("id", profile.id)
      put("name", profile.name)
      put("folderUri", profile.folderUri ?: JSONObject.NULL)
      put("gridPreset", profile.gridConfig.preset.name)
      put("gridRows", profile.gridConfig.rows)
      put("gridCols", profile.gridConfig.cols)
      put("bankNames", encodeBankNamesToJsonObject(profile.bankNames))
      put("bankOrder", JSONArray(profile.bankOrder))
      put("bankFolderUris", encodeBankNamesToJsonObject(profile.bankFolderUris))
      put("masterEqEnabled", profile.masterEqEnabled)
      put("masterEqPreset", profile.masterEqPreset.name)
      put("masterEqBandsDb", encodeEqBandsToJsonArray(profile.masterEqBandsDb))
      if (profile.themeMode != null ||
        profile.useDynamicColor != null ||
        profile.appAccentColorArgb != null ||
        profile.padGridSpacingDp != null ||
        profile.defaultPadShape != null ||
        profile.defaultCornerGlowArgb != null ||
        profile.defaultPadColorMode != null ||
        profile.defaultPadColorArgb != null
      ) {
        put(
          "uiAppearance",
          JSONObject().apply {
            profile.themeMode?.let { put("themeMode", it.name) }
            profile.useDynamicColor?.let { put("useDynamicColor", it) }
            profile.appAccentColorArgb?.let { put("appAccentColorArgb", it) }
            profile.padGridSpacingDp?.let { put("padGridSpacingDp", it) }
            profile.defaultPadShape?.let { put("defaultPadShape", it.name) }
            profile.defaultCornerGlowArgb?.let { put("defaultCornerGlowArgb", it) }
            profile.defaultPadColorMode?.let { put("defaultPadColorMode", it.name) }
            profile.defaultPadColorArgb?.let { put("defaultPadColorArgb", it) }
          }
        )
      }
      put("exportedAtMs", System.currentTimeMillis())

      val padsArray = JSONArray()
      pads.sortedWith(compareBy<SoundPad> { it.bankIndex }.thenBy { it.positionIndex }).forEach { pad ->
        val settingsObj = JSONObject().apply {
          put("volume", pad.settings.volume.toDouble())
          put("speed", pad.settings.speed.toDouble())
          put("pitch", pad.settings.pitch.toDouble())
          put("pan", pad.settings.pan.toDouble())
          put("isLooping", pad.settings.isLooping)
          put("triggerMode", pad.settings.triggerMode.name)
          put("chokeGroup", pad.settings.chokeGroup)
          put("fadeInMs", pad.settings.fadeInMs)
          put("fadeOutMs", pad.settings.fadeOutMs)
          put("fadeCurve", pad.settings.fadeCurve.name)
          put("crossfadeEnabled", pad.settings.crossfadeEnabled)
          put("trimStartMs", pad.settings.trimStartMs)
          put("trimEndMs", pad.settings.trimEndMs)
          put("isDuckingSource", pad.settings.isDuckingSource)
          put("normalizedGain", pad.settings.normalizedGain.toDouble())
          put("eqEnabled", pad.settings.eqEnabled)
          put("eqPreset", pad.settings.eqPreset.name)
          put("eqBandsDb", encodeEqBandsToJsonArray(pad.settings.eqBandsDb))
          put("isReversed", pad.settings.isReversed)
          put("fxPreset", pad.settings.fxPreset.name)
          put("echoEnabled", pad.settings.echoEnabled)
          put("echoDelayMs", pad.settings.echoDelayMs.toDouble())
          put("echoFeedback", pad.settings.echoFeedback.toDouble())
          put("echoMix", pad.settings.echoMix.toDouble())
          put("reverbEnabled", pad.settings.reverbEnabled)
          put("reverbRoomSize", pad.settings.reverbRoomSize.toDouble())
          put("reverbDamping", pad.settings.reverbDamping.toDouble())
          put("reverbMix", pad.settings.reverbMix.toDouble())
        }
        val padObj = JSONObject().apply {
          put("uriString", pad.uriString)
          put("fileName", pad.fileName)
          put("displayName", pad.displayName)
          put("extension", pad.extension)
          put("durationMs", pad.durationMs)
          put("sizeBytes", pad.sizeBytes)
          put("positionIndex", pad.positionIndex)
          put("bankIndex", pad.bankIndex)
          put("subfolderName", pad.subfolderName)
          put("colorArgb", pad.colorArgb ?: JSONObject.NULL)
          put("glowColorArgb", pad.glowColorArgb ?: JSONObject.NULL)
          put("glowIntensity", pad.glowIntensity.toDouble())
          put("shapeStyle", pad.shapeStyle?.name ?: JSONObject.NULL)
          put("borderColorArgb", pad.borderColorArgb ?: JSONObject.NULL)
          put("borderWidthDp", pad.borderWidthDp.toDouble())
          put("cornerRadiusDp", pad.cornerRadiusDp.toDouble())
          put("textColorArgb", pad.textColorArgb ?: JSONObject.NULL)
          put("surfaceFinish", pad.surfaceFinish.name)
          put("badgeIcon", pad.badgeIcon.name)
          put("showSlotNumber", pad.showSlotNumber)
          put("showDurationLabel", pad.showDurationLabel)
          put("isClone", pad.isClone)
          put("midiNote", pad.midiNote ?: JSONObject.NULL)
          put("isFavorite", pad.isFavorite)
          put("categoryTag", pad.categoryTag)
          put("settings", settingsObj)
        }
        padsArray.put(padObj)
      }
      put("pads", padsArray)

      val ccArray = JSONArray()
      ccMappings.forEach { mapping ->
        ccArray.put(
          JSONObject().apply {
            put("target", mapping.target.name)
            put("ccNumber", mapping.ccNumber)
            put("channel", mapping.channel)
          }
        )
      }
      put("midiCcMappings", ccArray)
    }
  }

  fun importFromJson(jsonString: String): ExportedProfileBundle {
    return fromJsonObject(JSONObject(jsonString), generateFreshId = true)
  }

  fun fromJsonObject(root: JSONObject, generateFreshId: Boolean = true): ExportedProfileBundle {
    val now = System.currentTimeMillis()
    val rawId = root.optString("id", "").ifBlank { "profile_${UUID.randomUUID()}" }
    val profileId = if (generateFreshId) "profile_${UUID.randomUUID()}" else rawId
    val name = root.optString("name", "Imported Profile").ifBlank { "Imported Profile" }
    val folderUri = if (root.isNull("folderUri")) null else root.optString("folderUri", "").ifBlank { null }

    val preset = runCatching {
      GridPreset.valueOf(root.optString("gridPreset", GridPreset.GRID_4X4.name))
    }.getOrDefault(GridPreset.GRID_4X4)
    val rows = root.optInt("gridRows", preset.rows).coerceIn(1, 16)
    val cols = root.optInt("gridCols", preset.cols).coerceIn(1, 16)
    val gridConfig = GridConfig(preset = preset, rows = rows, cols = cols)
    val bankNames = decodeBankNames(root.optJSONObject("bankNames"))
    val bankOrderArr = root.optJSONArray("bankOrder")
    val bankOrder = buildList {
      if (bankOrderArr != null) {
        for (idx in 0 until bankOrderArr.length()) {
          val v = bankOrderArr.optInt(idx, -1)
          if (v >= 0 && v !in this) add(v)
        }
      }
    }
    val bankFolderUris = decodeBankNames(root.optJSONObject("bankFolderUris"))

    val padsArray = root.optJSONArray("pads") ?: JSONArray()
    val pads = mutableListOf<SoundPad>()
    for (i in 0 until padsArray.length()) {
      val obj = padsArray.optJSONObject(i) ?: continue
      val settingsObj = obj.optJSONObject("settings") ?: JSONObject()

      val triggerMode = runCatching {
        PadTriggerMode.valueOf(settingsObj.optString("triggerMode", PadTriggerMode.TOGGLE.name))
      }.getOrDefault(PadTriggerMode.TOGGLE)

      val fadeCurve = runCatching {
        FadeCurve.valueOf(settingsObj.optString("fadeCurve", FadeCurve.EQUAL_POWER.name))
      }.getOrDefault(FadeCurve.EQUAL_POWER)

      val eqPreset = runCatching {
        EqPreset.valueOf(settingsObj.optString("eqPreset", EqPreset.FLAT.name))
      }.getOrDefault(EqPreset.FLAT)

      val fxPreset = runCatching {
        FxPreset.valueOf(settingsObj.optString("fxPreset", FxPreset.OFF.name))
      }.getOrDefault(FxPreset.OFF)

      val settings = PadSettings(
        volume = settingsObj.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
        speed = settingsObj.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
        pitch = settingsObj.optDouble("pitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
        pan = settingsObj.optDouble("pan", 0.0).toFloat().coerceIn(-1.0f, 1.0f),
        isLooping = settingsObj.optBoolean("isLooping", false),
        triggerMode = triggerMode,
        chokeGroup = settingsObj.optInt("chokeGroup", 0).coerceIn(0, 4),
        fadeInMs = settingsObj.optLong("fadeInMs", 0L).coerceIn(0L, 10000L),
        fadeOutMs = settingsObj.optLong("fadeOutMs", 0L).coerceIn(0L, 10000L),
        fadeCurve = fadeCurve,
        crossfadeEnabled = settingsObj.optBoolean("crossfadeEnabled", true),
        trimStartMs = settingsObj.optLong("trimStartMs", 0L).coerceAtLeast(0L),
        trimEndMs = settingsObj.optLong("trimEndMs", 0L).coerceAtLeast(0L),
        isDuckingSource = settingsObj.optBoolean("isDuckingSource", false),
        normalizedGain = settingsObj.optDouble("normalizedGain", 1.0).toFloat().coerceIn(0.35f, 1.60f),
        eqEnabled = settingsObj.optBoolean("eqEnabled", false),
        eqPreset = eqPreset,
        eqBandsDb = decodeEqBandsJsonArray(settingsObj.optJSONArray("eqBandsDb")),
        isReversed = settingsObj.optBoolean("isReversed", false),
        fxPreset = fxPreset,
        echoEnabled = settingsObj.optBoolean("echoEnabled", false),
        echoDelayMs = settingsObj.optDouble("echoDelayMs", 220.0).toFloat().coerceIn(60f, 800f),
        echoFeedback = settingsObj.optDouble("echoFeedback", 0.30).toFloat().coerceIn(0f, 0.75f),
        echoMix = settingsObj.optDouble("echoMix", 0.25).toFloat().coerceIn(0f, 0.85f),
        reverbEnabled = settingsObj.optBoolean("reverbEnabled", false),
        reverbRoomSize = settingsObj.optDouble("reverbRoomSize", 0.35).toFloat().coerceIn(0f, 1f),
        reverbDamping = settingsObj.optDouble("reverbDamping", 0.45).toFloat().coerceIn(0f, 1f),
        reverbMix = settingsObj.optDouble("reverbMix", 0.25).toFloat().coerceIn(0f, 0.85f)
      )

      val colorArgb = if (obj.isNull("colorArgb")) null else obj.optLong("colorArgb")
      val glowColorArgb = if (obj.isNull("glowColorArgb")) null else obj.optLong("glowColorArgb")
      val glowIntensity = obj.optDouble("glowIntensity", 0.0).toFloat().coerceIn(0f, 1.8f)
      val shapeStyle = if (obj.isNull("shapeStyle")) {
        null
      } else {
        runCatching { PadShapeStyle.valueOf(obj.optString("shapeStyle")) }.getOrNull()
      }
      val borderColorArgb = if (obj.isNull("borderColorArgb")) null else obj.optLong("borderColorArgb")
      val borderWidthDp = obj.optDouble("borderWidthDp", 1.25).toFloat().coerceIn(0f, 4f)
      val cornerRadiusDp = obj.optDouble("cornerRadiusDp", 14.0).toFloat().coerceIn(2f, 28f)
      val textColorArgb = if (obj.isNull("textColorArgb")) null else obj.optLong("textColorArgb")
      val surfaceFinish = runCatching {
        PadSurfaceFinish.valueOf(obj.optString("surfaceFinish", PadSurfaceFinish.CLASSIC.name))
      }.getOrDefault(PadSurfaceFinish.CLASSIC)
      val badgeIcon = runCatching {
        PadBadgeIcon.valueOf(obj.optString("badgeIcon", PadBadgeIcon.NONE.name))
      }.getOrDefault(PadBadgeIcon.NONE)
      val showSlotNumber = obj.optBoolean("showSlotNumber", true)
      val showDurationLabel = obj.optBoolean("showDurationLabel", true)
      val midiNote = if (obj.isNull("midiNote")) null else obj.optInt("midiNote")
      val isFavorite = obj.optBoolean("isFavorite", false)
      val categoryTag = obj.optString("categoryTag", "")

      pads.add(
        SoundPad(
          id = "pad_${UUID.randomUUID()}",
          uriString = obj.optString("uriString", ""),
          fileName = obj.optString("fileName", "sample.wav"),
          displayName = obj.optString("displayName", "Sample"),
          extension = obj.optString("extension", "WAV"),
          durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
          sizeBytes = obj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
          positionIndex = obj.optInt("positionIndex", i).coerceAtLeast(0),
          bankIndex = obj.optInt("bankIndex", 0).coerceAtLeast(0),
          subfolderName = obj.optString("subfolderName", ""),
          colorArgb = colorArgb,
          glowColorArgb = glowColorArgb,
          glowIntensity = glowIntensity,
          shapeStyle = shapeStyle,
          borderColorArgb = borderColorArgb,
          borderWidthDp = borderWidthDp,
          cornerRadiusDp = cornerRadiusDp,
          textColorArgb = textColorArgb,
          surfaceFinish = surfaceFinish,
          badgeIcon = badgeIcon,
          showSlotNumber = showSlotNumber,
          showDurationLabel = showDurationLabel,
          isClone = obj.optBoolean("isClone", false),
          midiNote = midiNote,
          isPreloadedInRam = false,
          isFavorite = isFavorite,
          categoryTag = categoryTag,
          settings = settings
        )
      )
    }

    val resolvedPads = PadGridSlotManager.resolveBankSlotCollisions(pads)

    val ccArray = root.optJSONArray("midiCcMappings") ?: JSONArray()
    val ccMappings = mutableListOf<MidiCcMapping>()
    for (i in 0 until ccArray.length()) {
      val obj = ccArray.optJSONObject(i) ?: continue
      val target = runCatching {
        MidiCcTarget.valueOf(obj.optString("target"))
      }.getOrNull() ?: continue
      val ccNum = obj.optInt("ccNumber", -1)
      if (ccNum !in 0..127) continue
      val channel = obj.optInt("channel", -1).coerceIn(-1, 15)
      ccMappings.add(
        MidiCcMapping(
          id = "cc_${profileId}_${target.name}",
          profileId = profileId,
          target = target,
          ccNumber = ccNum,
          channel = channel
        )
      )
    }

    val masterEqPreset = runCatching {
      EqPreset.valueOf(root.optString("masterEqPreset", EqPreset.FLAT.name))
    }.getOrDefault(EqPreset.FLAT)

    val uiAppearanceObj = root.optJSONObject("uiAppearance")
    val importedThemeMode = uiAppearanceObj?.optString("themeMode", "")?.takeIf { it.isNotBlank() }?.let { raw ->
      runCatching { ThemeMode.valueOf(raw) }.getOrNull()
    }
    val importedDynamicColor = if (uiAppearanceObj != null && uiAppearanceObj.has("useDynamicColor")) {
      uiAppearanceObj.optBoolean("useDynamicColor")
    } else null
    val importedAccent = if (uiAppearanceObj != null && uiAppearanceObj.has("appAccentColorArgb")) {
      uiAppearanceObj.optLong("appAccentColorArgb")
    } else null
    val importedSpacing = if (uiAppearanceObj != null && uiAppearanceObj.has("padGridSpacingDp")) {
      uiAppearanceObj.optInt("padGridSpacingDp").coerceIn(2, 16)
    } else null
    val importedShape = uiAppearanceObj?.optString("defaultPadShape", "")?.takeIf { it.isNotBlank() }?.let { raw ->
      runCatching { PadShapeStyle.valueOf(raw) }.getOrNull()
    }
    val importedGlow = if (uiAppearanceObj != null && uiAppearanceObj.has("defaultCornerGlowArgb")) {
      uiAppearanceObj.optLong("defaultCornerGlowArgb")
    } else null
    val importedDefaultPadColorMode = uiAppearanceObj?.optString("defaultPadColorMode", "")?.takeIf { it.isNotBlank() }?.let { raw ->
      runCatching { DefaultPadColorMode.valueOf(raw) }.getOrNull()
    }
    val importedDefaultPadColorArgb = if (uiAppearanceObj != null && uiAppearanceObj.has("defaultPadColorArgb")) {
      uiAppearanceObj.optLong("defaultPadColorArgb")
    } else null

    val profile = PadProfile(
      id = profileId,
      name = name,
      folderUri = folderUri,
      gridConfig = gridConfig,
      padCount = resolvedPads.size,
      bankNames = bankNames,
      bankOrder = bankOrder,
      bankFolderUris = bankFolderUris,
      masterEqEnabled = root.optBoolean("masterEqEnabled", false),
      masterEqPreset = masterEqPreset,
      masterEqBandsDb = decodeEqBandsJsonArray(root.optJSONArray("masterEqBandsDb")),
      themeMode = importedThemeMode,
      useDynamicColor = importedDynamicColor,
      appAccentColorArgb = importedAccent,
      padGridSpacingDp = importedSpacing,
      defaultPadShape = importedShape,
      defaultCornerGlowArgb = importedGlow,
      defaultPadColorMode = importedDefaultPadColorMode,
      defaultPadColorArgb = importedDefaultPadColorArgb,
      createdAtMs = now,
      updatedAtMs = now,
      isActive = true
    )

    return ExportedProfileBundle(profile = profile, pads = resolvedPads, ccMappings = ccMappings)
  }

  fun encodeBankNamesString(bankNames: Map<Int, String>): String {
    return encodeBankNamesToJsonObject(bankNames).toString()
  }

  fun decodeBankNamesString(json: String?): Map<Int, String> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching { decodeBankNames(JSONObject(json)) }.getOrDefault(emptyMap())
  }

  fun encodeBankOrderString(order: List<Int>): String {
    return JSONArray(order).toString()
  }

  fun decodeBankOrderString(json: String?): List<Int> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      buildList {
        for (i in 0 until arr.length()) {
          val v = arr.optInt(i, -1)
          if (v >= 0 && v !in this) add(v)
        }
      }
    }.getOrDefault(emptyList())
  }

  fun encodeBankFolderUrisString(uris: Map<Int, String>): String {
    return encodeBankNamesToJsonObject(uris).toString()
  }

  fun decodeBankFolderUrisString(json: String?): Map<Int, String> {
    return decodeBankNamesString(json)
  }

  fun encodeMiniWaveform(bars: List<Float>): String {
    if (bars.isEmpty()) return ""
    return bars.joinToString(",") { ((it.coerceIn(0f, 1f) * 99f).toInt()).coerceIn(1, 99).toString() }
  }

  fun decodeMiniWaveform(raw: String?): List<Float> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',').mapNotNull { token ->
      token.trim().toIntOrNull()?.let { (it.coerceIn(1, 99) / 99f) }
    }
  }

  fun encodeEqBandsString(bandsDb: List<Float>): String {
    return encodeEqBandsToJsonArray(bandsDb).toString()
  }

  fun decodeEqBandsString(json: String?): List<Float> {
    if (json.isNullOrBlank()) return EqPreset.FLAT.gainsDb
    return runCatching {
      decodeEqBandsJsonArray(JSONArray(json))
    }.getOrDefault(EqPreset.FLAT.gainsDb)
  }

  fun encodeEqBandsToJsonArray(bandsDb: List<Float>): JSONArray {
    val sanitized = EqPreset.sanitizeBands(bandsDb)
    return JSONArray().apply {
      sanitized.forEach { put(it.toDouble()) }
    }
  }

  fun decodeEqBandsJsonArray(arr: JSONArray?): List<Float> {
    if (arr == null) return EqPreset.FLAT.gainsDb
    return List(5) { idx ->
      arr.optDouble(idx, 0.0).toFloat().coerceIn(-12f, 12f)
    }
  }

  private fun encodeBankNamesToJsonObject(bankNames: Map<Int, String>): JSONObject {
    return JSONObject().apply {
      bankNames.forEach { (idx, label) ->
        if (label.isNotBlank()) {
          put(idx.toString(), label.trim())
        }
      }
    }
  }

  private fun decodeBankNames(obj: JSONObject?): Map<Int, String> {
    if (obj == null) return emptyMap()
    val result = mutableMapOf<Int, String>()
    val keys = obj.keys()
    while (keys.hasNext()) {
      val key = keys.next()
      val idx = key.toIntOrNull() ?: continue
      val value = obj.optString(key, "").trim()
      if (value.isNotEmpty()) {
        result[idx] = value
      }
    }
    return result
  }
}
