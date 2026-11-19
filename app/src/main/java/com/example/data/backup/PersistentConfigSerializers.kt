package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.data.profile.ProfileJsonSerializer
import com.example.domain.model.AppLanguage
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadBadgeIcon
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object FileHashAndPathHelper {

  private const val QUICK_HASH_CHUNK_BYTES = 64 * 1024

  fun computeSha1Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
    return digest.joinToString("") { "%02x".format(it) }
  }

  /**
   * Hash SHA-1 cepat untuk pencocokan ulang file audio tanpa membaca ratusan MB ke RAM:
   * - Untuk file <= 128 KB: hash seluruh isi + ukuran file.
   * - Untuk file > 128 KB: hash header 64 KB + tail 64 KB (jika ukuran stream tersedia) + ukuran file.
   */
  fun computeQuickSha1Hex(
    sizeBytes: Long,
    openInputStream: () -> InputStream?
  ): String {
    return runCatching {
      openInputStream()?.use { stream ->
        val md = MessageDigest.getInstance("SHA-1")
        val header = ByteArray(QUICK_HASH_CHUNK_BYTES)
        val headerRead = readUpTo(stream, header)
        if (headerRead > 0) {
          md.update(header, 0, headerRead)
        }

        if (sizeBytes <= QUICK_HASH_CHUNK_BYTES * 2L) {
          val rest = ByteArray(16 * 1024)
          while (true) {
            val n = stream.read(rest)
            if (n <= 0) break
            md.update(rest, 0, n)
          }
        } else {
          val skipTarget = (sizeBytes - QUICK_HASH_CHUNK_BYTES - headerRead).coerceAtLeast(0L)
          var skipped = 0L
          while (skipped < skipTarget) {
            val step = stream.skip(skipTarget - skipped)
            if (step <= 0) break
            skipped += step
          }
          val tail = ByteArray(QUICK_HASH_CHUNK_BYTES)
          val tailRead = readUpTo(stream, tail)
          if (tailRead > 0) {
            md.update(tail, 0, tailRead)
          }
        }
        md.update(sizeBytes.toString().toByteArray())
        md.digest().joinToString("") { "%02x".format(it) }
      }.orEmpty()
    }.getOrDefault("")
  }

  fun computeQuickFileSha1(context: Context, uriString: String, sizeBytes: Long): String {
    if (uriString.isBlank()) return ""
    val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return ""
    return computeQuickSha1Hex(sizeBytes = sizeBytes) {
      runCatching { context.contentResolver.openInputStream(parsed) }.getOrNull()
    }
  }

  fun extractRelativePath(
    rootTreeUriString: String?,
    subfolderName: String,
    fileName: String,
    documentUriString: String?
  ): String {
    val cleanFileName = fileName.trim().ifBlank { "audio.wav" }
    val fromDocUri = extractRelativePathFromDocumentUri(rootTreeUriString, documentUriString)
    if (!fromDocUri.isNullOrBlank()) return fromDocUri

    val cleanSub = subfolderName.trim().trim('/')
    return if (cleanSub.isNotEmpty()) "$cleanSub/$cleanFileName" else cleanFileName
  }

  internal fun extractRelativePathFromDocumentUri(
    rootTreeUriString: String?,
    documentUriString: String?
  ): String? {
    if (rootTreeUriString.isNullOrBlank() || documentUriString.isNullOrBlank()) return null
    return runCatching {
      val treeUri = Uri.parse(rootTreeUriString)
      val docUri = Uri.parse(documentUriString)
      val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
      val docId = DocumentsContract.getDocumentId(docUri)
      if (docId.startsWith(treeDocId) && docId.length > treeDocId.length) {
        docId.substring(treeDocId.length).trimStart('/', ':')
      } else {
        null
      }
    }.getOrNull()
  }

  fun sanitizeFileSlug(name: String, fallback: String = "item"): String {
    val cleaned = name
      .trim()
      .lowercase(Locale.US)
      .replace(Regex("[^a-z0-9._-]+"), "_")
      .trim('_', '.')
    return cleaned.ifBlank { fallback }
  }

  private fun readUpTo(stream: InputStream, target: ByteArray): Int {
    var total = 0
    while (total < target.size) {
      val read = stream.read(target, total, target.size - total)
      if (read <= 0) break
      total += read
    }
    return total
  }
}

/**
 * Pipeline migrasi skema JSON Persistent Config dari versi lama (v0/v1 atau legacy FullBackupSerializer)
 * menuju versi skema terkini ([PersistentConfigSchema.CURRENT_SCHEMA_VERSION]).
 */
object ConfigSchemaMigrator {

  fun detectSchemaVersion(json: JSONObject): Int {
    return when {
      json.has("schemaVersion") -> json.optInt("schemaVersion", 1).coerceAtLeast(1)
      json.has("backupSchemaVersion") -> json.optInt("backupSchemaVersion", 1).coerceAtLeast(1)
      else -> 1
    }
  }

  fun migrateMeta(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      val legacyVersion = target.optInt("backupSchemaVersion", 1)
      if (!target.has("appName")) target.put("appName", "SoundPad Pro")
      if (!target.has("appVersionName")) target.put("appVersionName", "1.0")
      if (!target.has("appVersionCode")) target.put("appVersionCode", 1)
      val ts = target.optLong("exportedAtMs", System.currentTimeMillis())
      if (!target.has("createdAtMs")) target.put("createdAtMs", ts)
      if (!target.has("updatedAtMs")) target.put("updatedAtMs", ts)
      if (!target.has("lastSyncedAtMs")) target.put("lastSyncedAtMs", ts)
      if (!target.has("includesWaveforms")) target.put("includesWaveforms", false)
      if (!target.has("isEncrypted")) target.put("isEncrypted", false)
      if (!target.has("backupSource")) target.put("backupSource", "legacy_v$legacyVersion")
      if (!target.has("includedSections")) {
        target.put("includedSections", JSONArray(listOf("settings", "profiles")))
      }
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateSettings(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    val settingsNode = if (target.has("settings") && target.optJSONObject("settings") != null) {
      target.getJSONObject("settings")
    } else if (target.has("globalSettings") && target.optJSONObject("globalSettings") != null) {
      target.getJSONObject("globalSettings")
    } else {
      target
    }

    if (fromVersion < 2) {
      // Legacy v1 -> v2 key upgrades
      if (!settingsNode.has("themeMode") && settingsNode.has("nightMode")) {
        settingsNode.put("themeMode", if (settingsNode.optBoolean("nightMode")) ThemeMode.DARK.name else ThemeMode.LIGHT.name)
      }
      if (!settingsNode.has("defaultPadColorMode")) {
        settingsNode.put("defaultPadColorMode", DefaultPadColorMode.GRAY.name)
      }
      if (!settingsNode.has("defaultPadColorArgb")) {
        settingsNode.put("defaultPadColorArgb", 0xFF4A4F57L)
      }
      if (!settingsNode.has("isFocusMode")) {
        settingsNode.put("isFocusMode", false)
      }
      if (!settingsNode.has("startInFocusMode")) {
        settingsNode.put("startInFocusMode", false)
      }
      if (!settingsNode.has("focusCornerPanicEnabled")) {
        settingsNode.put("focusCornerPanicEnabled", true)
      }
      if (!target.has("audioOutput")) {
        target.put(
          "audioOutput",
          JSONObject().apply {
            put("mainOutputDeviceId", settingsNode.opt("preferredMainOutputId") ?: JSONObject.NULL)
            put("mainOutputDeviceName", settingsNode.opt("preferredMainOutputName") ?: JSONObject.NULL)
            put("cueOutputDeviceId", settingsNode.opt("preferredCueOutputId") ?: JSONObject.NULL)
            put("cueOutputDeviceName", settingsNode.opt("preferredCueOutputName") ?: JSONObject.NULL)
            put("audioBufferSizeMs", settingsNode.optInt("audioBufferSizeMs", 50))
          }
        )
      }
      if (!target.has("persistentSync")) {
        target.put(
          "persistentSync",
          JSONObject().apply {
            put("backupFolderUri", settingsNode.opt("persistentBackupFolderUri") ?: JSONObject.NULL)
            put("backupFolderName", settingsNode.opt("persistentBackupFolderName") ?: JSONObject.NULL)
            put("autoSyncEnabled", settingsNode.optBoolean("persistentAutoSyncEnabled", true))
            put("lastSyncAtMs", settingsNode.optLong("persistentLastSyncAtMs", 0L))
            put("includeWaveforms", settingsNode.optBoolean("persistentIncludeWaveforms", false))
          }
        )
      }
      if (target !== settingsNode) {
        target.put("settings", settingsNode)
      }
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateProfile(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2 || !target.has("banks")) {
      // Derive banks[] from bankNames / bankOrder / bankFolderUris if not yet present
      val bankNamesObj = target.optJSONObject("bankNames")
      val bankFolderUrisObj = target.optJSONObject("bankFolderUris")
      val bankOrderArr = target.optJSONArray("bankOrder")
      val orderedKeys = linkedSetOf<Int>()
      if (bankOrderArr != null) {
        for (i in 0 until bankOrderArr.length()) {
          val k = bankOrderArr.optInt(i, -1)
          if (k >= 0) orderedKeys.add(k)
        }
      }
      bankNamesObj?.keys()?.forEach { key -> key.toIntOrNull()?.let { orderedKeys.add(it) } }
      bankFolderUrisObj?.keys()?.forEach { key -> key.toIntOrNull()?.let { orderedKeys.add(it) } }
      val padsArr = target.optJSONArray("pads")
      if (padsArr != null) {
        for (i in 0 until padsArr.length()) {
          val padObj = padsArr.optJSONObject(i) ?: continue
          orderedKeys.add(padObj.optInt("bankIndex", 0).coerceAtLeast(0))
          // Upgrade slotIndex & fileMetadata inside pad
          val posIdx = padObj.optInt("positionIndex", i).coerceAtLeast(0)
          if (!padObj.has("slotIndex")) {
            padObj.put("slotIndex", posIdx)
          }
          if (!padObj.has("glowIntensity")) {
            padObj.put("glowIntensity", 0.0)
          }
          if (!padObj.has("fileMetadata")) {
            val fileName = padObj.optString("fileName", "sample.wav")
            val subfolder = padObj.optString("subfolderName", "").trim()
            val relPath = if (subfolder.isNotEmpty()) "$subfolder/$fileName" else fileName
            padObj.put(
              "fileMetadata",
              JSONObject().apply {
                put("padId", padObj.optString("id", ""))
                put("uriString", padObj.optString("uriString", ""))
                put("relativePath", relPath)
                put("fileName", fileName)
                put("sizeBytes", padObj.optLong("sizeBytes", 0L))
                put("sha1Hash", padObj.optString("sha1Hash", ""))
                put("durationMs", padObj.optLong("durationMs", 0L))
                put("extension", padObj.optString("extension", "WAV"))
              }
            )
          }
        }
      }
      if (!target.has("banks")) {
        val banksArr = JSONArray()
        orderedKeys.forEachIndexed { orderPos, bankIdx ->
          val label = bankNamesObj?.optString(bankIdx.toString(), "").orEmpty().ifBlank { "B${bankIdx + 1}" }
          val folderUri = bankFolderUrisObj?.optString(bankIdx.toString(), "")?.ifBlank { null }
          banksArr.put(
            JSONObject().apply {
              put("bankIndex", bankIdx)
              put("name", label)
              put("folderUri", folderUri ?: JSONObject.NULL)
              put("orderPosition", orderPos)
            }
          )
        }
        target.put("banks", banksArr)
      }
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateLayout(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      if (!target.has("slots")) {
        val legacyPositions = target.optJSONArray("positions") ?: target.optJSONArray("pads") ?: JSONArray()
        val slots = JSONArray()
        for (i in 0 until legacyPositions.length()) {
          val obj = legacyPositions.optJSONObject(i) ?: continue
          slots.put(
            JSONObject().apply {
              put("padId", obj.optString("padId", obj.optString("id", "")))
              put("uriString", obj.optString("uriString", obj.optString("fileUri", "")))
              put("relativePath", obj.optString("relativePath", obj.optString("fileName", "")))
              put("fileName", obj.optString("fileName", ""))
              put("sizeBytes", obj.optLong("sizeBytes", 0L))
              put("sha1Hash", obj.optString("sha1Hash", ""))
              put("bankIndex", obj.optInt("bankIndex", 0))
              put("slotIndex", obj.optInt("slotIndex", obj.optInt("positionIndex", i)))
              put("colorArgb", obj.opt("colorArgb") ?: JSONObject.NULL)
              put("displayName", obj.optString("displayName", obj.optString("name", "")))
            }
          )
        }
        target.put("slots", slots)
      }
      if (!target.has("layoutId")) {
        target.put("layoutId", target.optString("id", "layout_${UUID.randomUUID()}"))
      }
      if (!target.has("profileId")) {
        target.put("profileId", "default_profile")
      }
      if (!target.has("name")) {
        target.put("name", "Default Layout")
      }
      if (!target.has("gridRows")) target.put("gridRows", 4)
      if (!target.has("gridCols")) target.put("gridCols", 4)
      if (!target.has("isDefault")) target.put("isDefault", true)
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migratePresets(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      if (!target.has("eqPresets")) target.put("eqPresets", JSONArray())
      if (!target.has("fxPresets")) target.put("fxPresets", JSONArray())
      if (!target.has("fadeCurves")) target.put("fadeCurves", JSONArray())
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateMidiMapping(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      if (!target.has("ccMappings") && target.has("mappings")) {
        target.put("ccMappings", target.optJSONArray("mappings") ?: JSONArray())
      }
      if (!target.has("ccMappings")) target.put("ccMappings", JSONArray())
      if (!target.has("pcMappings")) target.put("pcMappings", JSONArray())
      if (!target.has("padNoteMappings")) target.put("padNoteMappings", JSONArray())
      if (!target.has("sequencerNoteMappings")) target.put("sequencerNoteMappings", JSONArray())
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateScheduler(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      if (!target.has("tasks") && target.has("schedules")) {
        target.put("tasks", target.optJSONArray("schedules") ?: JSONArray())
      }
      if (!target.has("tasks")) target.put("tasks", JSONArray())
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }

  fun migrateSequencer(json: JSONObject, fromVersion: Int): JSONObject {
    val target = JSONObject(json.toString())
    if (fromVersion < 2) {
      if (!target.has("playlists") && target.has("sequences")) {
        target.put("playlists", target.optJSONArray("sequences") ?: JSONArray())
      }
      if (!target.has("playlists")) target.put("playlists", JSONArray())
    }
    target.put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
    return target
  }
}

object ConfigMetaBackupSerializer : Backupable<ConfigMetaJson> {
  override val backupKey: String = "meta"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_CONFIG_META
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: ConfigMetaJson): JSONObject {
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("appName", data.appName)
      put("appVersionName", data.appVersionName)
      put("appVersionCode", data.appVersionCode)
      put("createdAtMs", data.createdAtMs)
      put("updatedAtMs", data.updatedAtMs)
      put("lastSyncedAtMs", data.lastSyncedAtMs)
      put("profileCount", data.profileCount)
      put("bankCount", data.bankCount)
      put("padCount", data.padCount)
      put("layoutCount", data.layoutCount)
      put("schedulerCount", data.schedulerCount)
      put("sequencerCount", data.sequencerCount)
      put("includesWaveforms", data.includesWaveforms)
      put("isEncrypted", data.isEncrypted)
      put("backupSource", data.backupSource)
      put("includedSections", JSONArray(data.includedSections))
    }
  }

  override fun importBackupJson(json: JSONObject): ConfigMetaJson {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    val sectionsArr = migrated.optJSONArray("includedSections") ?: JSONArray()
    val sections = buildList {
      for (i in 0 until sectionsArr.length()) {
        val token = sectionsArr.optString(i, "").trim()
        if (token.isNotEmpty()) add(token)
      }
    }
    return ConfigMetaJson(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      appName = migrated.optString("appName", "SoundPad Pro").ifBlank { "SoundPad Pro" },
      appVersionName = migrated.optString("appVersionName", "1.0").ifBlank { "1.0" },
      appVersionCode = migrated.optInt("appVersionCode", 1).coerceAtLeast(1),
      createdAtMs = migrated.optLong("createdAtMs", System.currentTimeMillis()),
      updatedAtMs = migrated.optLong("updatedAtMs", System.currentTimeMillis()),
      lastSyncedAtMs = migrated.optLong("lastSyncedAtMs", System.currentTimeMillis()),
      profileCount = migrated.optInt("profileCount", 0).coerceAtLeast(0),
      bankCount = migrated.optInt("bankCount", 0).coerceAtLeast(0),
      padCount = migrated.optInt("padCount", 0).coerceAtLeast(0),
      layoutCount = migrated.optInt("layoutCount", 0).coerceAtLeast(0),
      schedulerCount = migrated.optInt("schedulerCount", 0).coerceAtLeast(0),
      sequencerCount = migrated.optInt("sequencerCount", 0).coerceAtLeast(0),
      includesWaveforms = migrated.optBoolean("includesWaveforms", false),
      isEncrypted = migrated.optBoolean("isEncrypted", false),
      backupSource = migrated.optString("backupSource", "saf_folder").ifBlank { "saf_folder" },
      includedSections = sections
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateMeta(json, fromVersion)
  }
}

object SettingsBackupSerializer : Backupable<SettingsBackupBundle> {
  override val backupKey: String = "settings"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_SETTINGS
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: SettingsBackupBundle): JSONObject {
    val s = data.settings
    val settingsObj = JSONObject().apply {
      put("activeProfileId", s.activeProfileId)
      put("folderUri", s.folderUri ?: JSONObject.NULL)
      put("folderName", s.folderName ?: JSONObject.NULL)
      put("gridPreset", s.gridConfig.preset.name)
      put("gridRows", s.gridConfig.rows)
      put("gridCols", s.gridConfig.cols)
      put("playMode", s.playMode.name)
      put("masterVolume", s.masterVolume.toDouble())
      put("masterPan", s.masterPan.toDouble())
      put("masterEqEnabled", s.masterEqEnabled)
      put("masterEqPreset", s.masterEqPreset.name)
      put("masterEqBandsDb", ProfileJsonSerializer.encodeEqBandsToJsonArray(s.masterEqBandsDb))
      put("miniWaveformEnabled", s.miniWaveformEnabled)
      put("preloadShortAudio", s.preloadShortAudio)
      put("audioEffectsEnabled", s.audioEffectsEnabled)
      put("visualEffectsEnabled", s.visualEffectsEnabled)
      put("keepScreenOn", s.keepScreenOn)
      put("themeMode", s.themeMode.name)
      put("useDynamicColor", s.useDynamicColor)
      put("appAccentColorArgb", s.appAccentColorArgb)
      put("padGridSpacingDp", s.padGridSpacingDp)
      put("defaultPadShape", s.defaultPadShape.name)
      put("defaultCornerGlowArgb", s.defaultCornerGlowArgb)
      put("isBottomBarVisible", s.isBottomBarVisible)
      put("isFullscreen", s.isFullscreen)
      put("globalFadeInMs", s.globalFadeInMs)
      put("globalFadeOutMs", s.globalFadeOutMs)
      put("globalFadeCurve", s.globalFadeCurve.name)
      put("globalCrossfadeEnabled", s.globalCrossfadeEnabled)
      put("crossfadeDurationMs", s.crossfadeDurationMs)
      put("crossfadeCurve", s.crossfadeCurve.name)
      put("masterLimiterEnabled", s.masterLimiterEnabled)
      put("duckingEnabled", s.duckingEnabled)
      put("duckingAttenuation", s.duckingAttenuation.toDouble())
      put("autoNormalizeEnabled", s.autoNormalizeEnabled)
      put("isLocked", s.isLocked)
      put("isGlobalCueEnabled", s.isGlobalCueEnabled)
      put("hapticIntensity", s.hapticIntensity.name)
      put("swipePadVolumeEnabled", s.swipePadVolumeEnabled)
      put("doubleTapToStopEnabled", s.doubleTapToStopEnabled)
      put("hasCompletedOnboarding", s.hasCompletedOnboarding)
      put("showFeatureTooltips", s.showFeatureTooltips)
      put("appLanguage", s.appLanguage.name)
      put("defaultPadColorMode", s.defaultPadColorMode.name)
      put("defaultPadColorArgb", s.defaultPadColorArgb)
      put("isFocusMode", s.isFocusMode)
      put("startInFocusMode", s.startInFocusMode)
      put("focusCornerPanicEnabled", s.focusCornerPanicEnabled)
      put("focusModeMidiNote", s.focusModeMidiNote ?: JSONObject.NULL)
      put("dragDropConflictMode", s.dragDropConflictMode.name)
    }

    val outputObj = JSONObject().apply {
      put("mainOutputDeviceId", data.audioOutput.mainOutputDeviceId ?: s.preferredMainOutputId ?: JSONObject.NULL)
      put("mainOutputDeviceName", data.audioOutput.mainOutputDeviceName ?: s.preferredMainOutputName ?: JSONObject.NULL)
      put("cueOutputDeviceId", data.audioOutput.cueOutputDeviceId ?: s.preferredCueOutputId ?: JSONObject.NULL)
      put("cueOutputDeviceName", data.audioOutput.cueOutputDeviceName ?: s.preferredCueOutputName ?: JSONObject.NULL)
      put("audioBufferSizeMs", data.audioOutput.audioBufferSizeMs.coerceIn(20, 500))
    }

    val syncObj = JSONObject().apply {
      put("backupFolderUri", data.syncConfig.backupFolderUri ?: s.persistentBackupFolderUri ?: JSONObject.NULL)
      put("backupFolderName", data.syncConfig.backupFolderName ?: s.persistentBackupFolderName ?: JSONObject.NULL)
      put("autoSyncEnabled", data.syncConfig.autoSyncEnabled)
      put("lastSyncAtMs", data.syncConfig.lastSyncAtMs)
      put("includeWaveforms", data.syncConfig.includeWaveforms)
    }

    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("settings", settingsObj)
      put("audioOutput", outputObj)
      put("persistentSync", syncObj)
    }
  }

  override fun importBackupJson(json: JSONObject): SettingsBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    val s = migrated.optJSONObject("settings") ?: migrated
    val out = migrated.optJSONObject("audioOutput") ?: JSONObject()
    val sync = migrated.optJSONObject("persistentSync") ?: JSONObject()

    val gridPreset = runCatching {
      GridPreset.valueOf(s.optString("gridPreset", GridPreset.GRID_4X4.name))
    }.getOrDefault(GridPreset.GRID_4X4)
    val masterEqPreset = runCatching {
      EqPreset.valueOf(s.optString("masterEqPreset", EqPreset.FLAT.name))
    }.getOrDefault(EqPreset.FLAT)

    val mainOutputId = optNullableInt(out, "mainOutputDeviceId")
      ?: optNullableInt(s, "preferredMainOutputId")
    val mainOutputName = optNullableString(out, "mainOutputDeviceName")
      ?: optNullableString(s, "preferredMainOutputName")
    val cueOutputId = optNullableInt(out, "cueOutputDeviceId")
      ?: optNullableInt(s, "preferredCueOutputId")
    val cueOutputName = optNullableString(out, "cueOutputDeviceName")
      ?: optNullableString(s, "preferredCueOutputName")
    val bufferSizeMs = out.optInt("audioBufferSizeMs", s.optInt("audioBufferSizeMs", 50)).coerceIn(20, 500)

    val backupFolderUri = optNullableString(sync, "backupFolderUri")
      ?: optNullableString(s, "persistentBackupFolderUri")
    val backupFolderName = optNullableString(sync, "backupFolderName")
      ?: optNullableString(s, "persistentBackupFolderName")
    val autoSync = if (sync.has("autoSyncEnabled")) {
      sync.optBoolean("autoSyncEnabled", true)
    } else {
      s.optBoolean("persistentAutoSyncEnabled", true)
    }
    val lastSyncAtMs = sync.optLong("lastSyncAtMs", s.optLong("persistentLastSyncAtMs", 0L)).coerceAtLeast(0L)
    val includeWaveforms = sync.optBoolean("includeWaveforms", s.optBoolean("persistentIncludeWaveforms", false))

    val globalSettings = GlobalSettings(
      activeProfileId = s.optString("activeProfileId", "default_profile").ifBlank { "default_profile" },
      folderUri = optNullableString(s, "folderUri"),
      folderName = optNullableString(s, "folderName"),
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
      focusModeMidiNote = optNullableInt(s, "focusModeMidiNote")?.coerceIn(0, 127),
      preferredMainOutputId = mainOutputId,
      preferredMainOutputName = mainOutputName,
      preferredCueOutputId = cueOutputId,
      preferredCueOutputName = cueOutputName,
      audioBufferSizeMs = bufferSizeMs,
      persistentBackupFolderUri = backupFolderUri,
      persistentBackupFolderName = backupFolderName,
      persistentAutoSyncEnabled = autoSync,
      persistentLastSyncAtMs = lastSyncAtMs,
      persistentIncludeWaveforms = includeWaveforms,
      dragDropConflictMode = runCatching {
        DragDropConflictMode.valueOf(s.optString("dragDropConflictMode", DragDropConflictMode.SWAP.name))
      }.getOrDefault(DragDropConflictMode.SWAP)
    )

    return SettingsBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      settings = globalSettings,
      audioOutput = AudioOutputConfigBackup(
        mainOutputDeviceId = mainOutputId,
        mainOutputDeviceName = mainOutputName,
        cueOutputDeviceId = cueOutputId,
        cueOutputDeviceName = cueOutputName,
        audioBufferSizeMs = bufferSizeMs
      ),
      syncConfig = PersistentSyncConfigBackup(
        backupFolderUri = backupFolderUri,
        backupFolderName = backupFolderName,
        autoSyncEnabled = autoSync,
        lastSyncAtMs = lastSyncAtMs,
        includeWaveforms = includeWaveforms
      )
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateSettings(json, fromVersion)
  }
}

object ProfileBackupSerializer : Backupable<ProfileBackupBundle> {
  override val backupKey: String = "profiles"
  override val defaultFilePath: String = "profiles/default.json"
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  fun exportBackupJson(data: ProfileBackupBundle, includeWaveforms: Boolean): JSONObject {
    val profile = data.profile
    val base = ProfileJsonSerializer.toJsonObject(
      profile = profile,
      pads = emptyList(),
      ccMappings = data.ccMappings
    )
    base.put("schemaVersion", schemaVersion)
    base.put("createdAtMs", profile.createdAtMs)
    base.put("updatedAtMs", profile.updatedAtMs)
    base.put("isActive", profile.isActive)

    val banksArr = JSONArray()
    val effectiveBanks = if (data.banks.isNotEmpty()) {
      data.banks
    } else {
      val keys = (profile.bankOrder + profile.bankNames.keys + profile.bankFolderUris.keys).distinct()
      keys.mapIndexed { idx, bankIdx ->
        BankBackupEntry(
          bankIndex = bankIdx,
          name = profile.bankNames[bankIdx] ?: "B${bankIdx + 1}",
          folderUri = profile.bankFolderUris[bankIdx],
          orderPosition = idx
        )
      }
    }
    effectiveBanks.forEach { bank ->
      banksArr.put(
        JSONObject().apply {
          put("bankIndex", bank.bankIndex)
          put("name", bank.name)
          put("folderUri", bank.folderUri ?: JSONObject.NULL)
          put("orderPosition", bank.orderPosition)
        }
      )
    }
    base.put("banks", banksArr)

    val padsArr = JSONArray()
    data.pads.sortedWith(compareBy<PadBackupEntry> { it.pad.bankIndex }.thenBy { it.slotIndex })
      .forEach { entry ->
        val pad = entry.pad
        val meta = entry.fileMetadata
        val settingsObj = encodePadSettingsJson(pad.settings)
        val padObj = JSONObject().apply {
          put("id", pad.id)
          put("uriString", pad.uriString)
          put("fileName", pad.fileName)
          put("displayName", pad.displayName)
          put("extension", pad.extension)
          put("durationMs", pad.durationMs)
          put("sizeBytes", pad.sizeBytes)
          put("positionIndex", pad.positionIndex)
          put("slotIndex", entry.slotIndex)
          put("bankIndex", pad.bankIndex)
          put("subfolderName", pad.subfolderName)
          put("relativePath", meta.relativePath)
          put("sha1Hash", meta.sha1Hash)
          put("isFileMissing", entry.isFileMissing)
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
          put(
            "fileMetadata",
            JSONObject().apply {
              put("padId", pad.id)
              put("uriString", meta.uriString)
              put("relativePath", meta.relativePath)
              put("fileName", meta.fileName)
              put("sizeBytes", meta.sizeBytes)
              put("sha1Hash", meta.sha1Hash)
              put("durationMs", meta.durationMs)
              put("extension", meta.extension)
            }
          )
          if (includeWaveforms && pad.miniWaveform.isNotEmpty()) {
            put("miniWaveform", ProfileJsonSerializer.encodeMiniWaveform(pad.miniWaveform))
          }
        }
        padsArr.put(padObj)
      }
    base.put("pads", padsArr)

    val pcArr = JSONArray()
    data.pcMappings.forEach { pc ->
      pcArr.put(
        JSONObject().apply {
          put("id", pc.id)
          put("programNumber", pc.programNumber)
          put("bankIndex", pc.bankIndex)
          put("channel", pc.channel)
        }
      )
    }
    base.put("midiPcMappings", pcArr)
    return base
  }

  override fun exportBackupJson(data: ProfileBackupBundle): JSONObject {
    return exportBackupJson(data, includeWaveforms = false)
  }

  override fun importBackupJson(json: JSONObject): ProfileBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json

    // Reuse ProfileJsonSerializer for core profile metadata & legacy pad compatibility,
    // while preserving original IDs and reading Stage A persistent metadata cleanly.
    val legacyBundle = ProfileJsonSerializer.fromJsonObject(migrated, generateFreshId = false)
    val profileId = legacyBundle.profile.id

    val banksArr = migrated.optJSONArray("banks") ?: JSONArray()
    val banks = mutableListOf<BankBackupEntry>()
    for (i in 0 until banksArr.length()) {
      val bObj = banksArr.optJSONObject(i) ?: continue
      val bIdx = bObj.optInt("bankIndex", -1)
      if (bIdx < 0) continue
      banks.add(
        BankBackupEntry(
          bankIndex = bIdx,
          name = bObj.optString("name", "B${bIdx + 1}").ifBlank { "B${bIdx + 1}" },
          folderUri = optNullableString(bObj, "folderUri"),
          orderPosition = bObj.optInt("orderPosition", i)
        )
      )
    }

    val padsArr = migrated.optJSONArray("pads") ?: JSONArray()
    val padEntries = mutableListOf<PadBackupEntry>()
    for (i in 0 until padsArr.length()) {
      val pObj = padsArr.optJSONObject(i) ?: continue
      val parsedPad = legacyBundle.pads.getOrNull(i) ?: decodeSinglePad(pObj, i)
      val preservedId = pObj.optString("id", "").ifBlank { parsedPad.id }
      val waveformRaw = optNullableString(pObj, "miniWaveform")
      val waveformBars = ProfileJsonSerializer.decodeMiniWaveform(waveformRaw)
      val slotIndex = pObj.optInt("slotIndex", parsedPad.positionIndex).coerceAtLeast(0)
      val metaObj = pObj.optJSONObject("fileMetadata")
      val relPath = (metaObj?.optString("relativePath", "") ?: "")
        .ifBlank { pObj.optString("relativePath", "") }
        .ifBlank {
          if (parsedPad.subfolderName.isNotBlank()) "${parsedPad.subfolderName}/${parsedPad.fileName}"
          else parsedPad.fileName
        }
      val sha1 = (metaObj?.optString("sha1Hash", "") ?: "")
        .ifBlank { pObj.optString("sha1Hash", "") }
      val meta = PadFileMetadata(
        padId = preservedId,
        uriString = metaObj?.optString("uriString", parsedPad.uriString).orEmpty().ifBlank { parsedPad.uriString },
        relativePath = relPath,
        fileName = metaObj?.optString("fileName", parsedPad.fileName).orEmpty().ifBlank { parsedPad.fileName },
        sizeBytes = metaObj?.optLong("sizeBytes", parsedPad.sizeBytes) ?: parsedPad.sizeBytes,
        sha1Hash = sha1,
        durationMs = metaObj?.optLong("durationMs", parsedPad.durationMs) ?: parsedPad.durationMs,
        extension = metaObj?.optString("extension", parsedPad.extension).orEmpty().ifBlank { parsedPad.extension }
      )
      padEntries.add(
        PadBackupEntry(
          pad = parsedPad.copy(id = preservedId, miniWaveform = waveformBars),
          slotIndex = slotIndex,
          fileMetadata = meta,
          isFileMissing = pObj.optBoolean("isFileMissing", false)
        )
      )
    }

    val pcArr = migrated.optJSONArray("midiPcMappings") ?: JSONArray()
    val pcMappings = mutableListOf<MidiProgramBankMapping>()
    for (i in 0 until pcArr.length()) {
      val obj = pcArr.optJSONObject(i) ?: continue
      val programNumber = obj.optInt("programNumber", -1)
      val bankIndex = obj.optInt("bankIndex", -1)
      if (programNumber !in 0..127 || bankIndex < 0) continue
      val channel = obj.optInt("channel", -1).coerceIn(-1, 15)
      val id = obj.optString("id", "").ifBlank { "pc_${profileId}_$bankIndex" }
      pcMappings.add(
        MidiProgramBankMapping(
          id = id,
          profileId = profileId,
          programNumber = programNumber,
          bankIndex = bankIndex,
          channel = channel
        )
      )
    }

    val restoredProfile = legacyBundle.profile.copy(
      createdAtMs = migrated.optLong("createdAtMs", legacyBundle.profile.createdAtMs),
      updatedAtMs = migrated.optLong("updatedAtMs", legacyBundle.profile.updatedAtMs),
      isActive = migrated.optBoolean("isActive", legacyBundle.profile.isActive),
      padCount = padEntries.size
    )

    return ProfileBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      profile = restoredProfile,
      banks = banks,
      pads = padEntries,
      ccMappings = legacyBundle.ccMappings,
      pcMappings = pcMappings
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateProfile(json, fromVersion)
  }

  private fun encodePadSettingsJson(settings: PadSettings): JSONObject = JSONObject().apply {
    put("volume", settings.volume.toDouble())
    put("speed", settings.speed.toDouble())
    put("pitch", settings.pitch.toDouble())
    put("pan", settings.pan.toDouble())
    put("isLooping", settings.isLooping)
    put("triggerMode", settings.triggerMode.name)
    put("chokeGroup", settings.chokeGroup)
    put("fadeInMs", settings.fadeInMs)
    put("fadeOutMs", settings.fadeOutMs)
    put("fadeCurve", settings.fadeCurve.name)
    put("crossfadeEnabled", settings.crossfadeEnabled)
    put("trimStartMs", settings.trimStartMs)
    put("trimEndMs", settings.trimEndMs)
    put("isDuckingSource", settings.isDuckingSource)
    put("normalizedGain", settings.normalizedGain.toDouble())
    put("eqEnabled", settings.eqEnabled)
    put("eqPreset", settings.eqPreset.name)
    put("eqBandsDb", ProfileJsonSerializer.encodeEqBandsToJsonArray(settings.eqBandsDb))
    put("isReversed", settings.isReversed)
    put("fxPreset", settings.fxPreset.name)
    put("echoEnabled", settings.echoEnabled)
    put("echoDelayMs", settings.echoDelayMs.toDouble())
    put("echoFeedback", settings.echoFeedback.toDouble())
    put("echoMix", settings.echoMix.toDouble())
    put("reverbEnabled", settings.reverbEnabled)
    put("reverbRoomSize", settings.reverbRoomSize.toDouble())
    put("reverbDamping", settings.reverbDamping.toDouble())
    put("reverbMix", settings.reverbMix.toDouble())
  }

  private fun decodeSinglePad(obj: JSONObject, index: Int): SoundPad {
    val sObj = obj.optJSONObject("settings") ?: JSONObject()
    val settings = PadSettings(
      volume = sObj.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
      speed = sObj.optDouble("speed", 1.0).toFloat().coerceIn(0.5f, 2.0f),
      pitch = sObj.optDouble("pitch", 1.0).toFloat().coerceIn(0.5f, 2.0f),
      pan = sObj.optDouble("pan", 0.0).toFloat().coerceIn(-1f, 1f),
      isLooping = sObj.optBoolean("isLooping", false),
      triggerMode = runCatching { PadTriggerMode.valueOf(sObj.optString("triggerMode")) }.getOrDefault(PadTriggerMode.TOGGLE),
      chokeGroup = sObj.optInt("chokeGroup", 0).coerceIn(0, 4),
      fadeInMs = sObj.optLong("fadeInMs", 0L).coerceIn(0L, 10000L),
      fadeOutMs = sObj.optLong("fadeOutMs", 0L).coerceIn(0L, 10000L),
      fadeCurve = runCatching { FadeCurve.valueOf(sObj.optString("fadeCurve")) }.getOrDefault(FadeCurve.EQUAL_POWER),
      crossfadeEnabled = sObj.optBoolean("crossfadeEnabled", true),
      trimStartMs = sObj.optLong("trimStartMs", 0L).coerceAtLeast(0L),
      trimEndMs = sObj.optLong("trimEndMs", 0L).coerceAtLeast(0L),
      isDuckingSource = sObj.optBoolean("isDuckingSource", false),
      normalizedGain = sObj.optDouble("normalizedGain", 1.0).toFloat().coerceIn(0.35f, 1.60f),
      eqEnabled = sObj.optBoolean("eqEnabled", false),
      eqPreset = runCatching { EqPreset.valueOf(sObj.optString("eqPreset")) }.getOrDefault(EqPreset.FLAT),
      eqBandsDb = ProfileJsonSerializer.decodeEqBandsJsonArray(sObj.optJSONArray("eqBandsDb")),
      isReversed = sObj.optBoolean("isReversed", false),
      fxPreset = runCatching { FxPreset.valueOf(sObj.optString("fxPreset")) }.getOrDefault(FxPreset.OFF),
      echoEnabled = sObj.optBoolean("echoEnabled", false),
      echoDelayMs = sObj.optDouble("echoDelayMs", 220.0).toFloat().coerceIn(60f, 800f),
      echoFeedback = sObj.optDouble("echoFeedback", 0.30).toFloat().coerceIn(0f, 0.75f),
      echoMix = sObj.optDouble("echoMix", 0.25).toFloat().coerceIn(0f, 0.85f),
      reverbEnabled = sObj.optBoolean("reverbEnabled", false),
      reverbRoomSize = sObj.optDouble("reverbRoomSize", 0.35).toFloat().coerceIn(0f, 1f),
      reverbDamping = sObj.optDouble("reverbDamping", 0.45).toFloat().coerceIn(0f, 1f),
      reverbMix = sObj.optDouble("reverbMix", 0.25).toFloat().coerceIn(0f, 0.85f)
    )
    return SoundPad(
      id = obj.optString("id", "").ifBlank { "pad_${UUID.randomUUID()}" },
      uriString = obj.optString("uriString", ""),
      fileName = obj.optString("fileName", "sample.wav"),
      displayName = obj.optString("displayName", "Sample"),
      extension = obj.optString("extension", "WAV"),
      durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
      sizeBytes = obj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
      positionIndex = obj.optInt("positionIndex", index).coerceAtLeast(0),
      bankIndex = obj.optInt("bankIndex", 0).coerceAtLeast(0),
      subfolderName = obj.optString("subfolderName", ""),
      colorArgb = optNullableLong(obj, "colorArgb"),
      glowColorArgb = optNullableLong(obj, "glowColorArgb"),
      glowIntensity = obj.optDouble("glowIntensity", 0.0).toFloat().coerceIn(0f, 1.8f),
      shapeStyle = optNullableString(obj, "shapeStyle")?.let { runCatching { PadShapeStyle.valueOf(it) }.getOrNull() },
      borderColorArgb = optNullableLong(obj, "borderColorArgb"),
      borderWidthDp = obj.optDouble("borderWidthDp", 1.25).toFloat().coerceIn(0f, 4f),
      cornerRadiusDp = obj.optDouble("cornerRadiusDp", 14.0).toFloat().coerceIn(2f, 28f),
      textColorArgb = optNullableLong(obj, "textColorArgb"),
      surfaceFinish = runCatching { PadSurfaceFinish.valueOf(obj.optString("surfaceFinish")) }.getOrDefault(PadSurfaceFinish.CLASSIC),
      badgeIcon = runCatching { PadBadgeIcon.valueOf(obj.optString("badgeIcon")) }.getOrDefault(PadBadgeIcon.NONE),
      showSlotNumber = obj.optBoolean("showSlotNumber", true),
      showDurationLabel = obj.optBoolean("showDurationLabel", true),
      isClone = obj.optBoolean("isClone", false),
      midiNote = optNullableInt(obj, "midiNote"),
      isFavorite = obj.optBoolean("isFavorite", false),
      categoryTag = obj.optString("categoryTag", ""),
      settings = settings
    )
  }
}

object LayoutBackupSerializer : Backupable<PadLayoutBackup> {
  override val backupKey: String = "layouts"
  override val defaultFilePath: String = "layouts/default_layout.json"
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: PadLayoutBackup): JSONObject {
    val slotsArr = JSONArray()
    data.slots.sortedWith(compareBy<LayoutSlotEntry> { it.bankIndex }.thenBy { it.slotIndex })
      .forEach { slot ->
        slotsArr.put(
          JSONObject().apply {
            put("padId", slot.padId)
            put("uriString", slot.uriString)
            put("relativePath", slot.relativePath)
            put("fileName", slot.fileName)
            put("sizeBytes", slot.sizeBytes)
            put("sha1Hash", slot.sha1Hash)
            put("bankIndex", slot.bankIndex)
            put("slotIndex", slot.slotIndex)
            put("colorArgb", slot.colorArgb ?: JSONObject.NULL)
            put("displayName", slot.displayName)
          }
        )
      }
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("layoutId", data.layoutId)
      put("profileId", data.profileId)
      put("name", data.name)
      put("gridRows", data.gridRows)
      put("gridCols", data.gridCols)
      put("isDefault", data.isDefault)
      put("createdAtMs", data.createdAtMs)
      put("updatedAtMs", data.updatedAtMs)
      put("slots", slotsArr)
    }
  }

  override fun importBackupJson(json: JSONObject): PadLayoutBackup {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    val slotsArr = migrated.optJSONArray("slots") ?: JSONArray()
    val slots = mutableListOf<LayoutSlotEntry>()
    for (i in 0 until slotsArr.length()) {
      val s = slotsArr.optJSONObject(i) ?: continue
      slots.add(
        LayoutSlotEntry(
          padId = s.optString("padId", "").ifBlank { "slot_pad_$i" },
          uriString = s.optString("uriString", ""),
          relativePath = s.optString("relativePath", ""),
          fileName = s.optString("fileName", ""),
          sizeBytes = s.optLong("sizeBytes", 0L).coerceAtLeast(0L),
          sha1Hash = s.optString("sha1Hash", ""),
          bankIndex = s.optInt("bankIndex", 0).coerceAtLeast(0),
          slotIndex = s.optInt("slotIndex", i).coerceAtLeast(0),
          colorArgb = optNullableLong(s, "colorArgb"),
          displayName = s.optString("displayName", "")
        )
      )
    }
    return PadLayoutBackup(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      layoutId = migrated.optString("layoutId", "layout_${UUID.randomUUID()}"),
      profileId = migrated.optString("profileId", "default_profile"),
      name = migrated.optString("name", "Default Layout").ifBlank { "Default Layout" },
      gridRows = migrated.optInt("gridRows", 4).coerceIn(1, 16),
      gridCols = migrated.optInt("gridCols", 4).coerceIn(1, 16),
      isDefault = migrated.optBoolean("isDefault", true),
      createdAtMs = migrated.optLong("createdAtMs", System.currentTimeMillis()),
      updatedAtMs = migrated.optLong("updatedAtMs", System.currentTimeMillis()),
      slots = slots
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateLayout(json, fromVersion)
  }
}

object PresetsBackupSerializer : Backupable<PresetsBackupBundle> {
  override val backupKey: String = "presets"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_PRESETS_COMBINED
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: PresetsBackupBundle): JSONObject {
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("eqPresets", encodeEqPresetsArray(data.eqPresets))
      put("fxPresets", encodeFxPresetsArray(data.fxPresets))
      put("fadeCurves", encodeFadeCurvesArray(data.fadeCurves))
    }
  }

  fun exportSplitPresetFiles(data: PresetsBackupBundle): Map<String, JSONObject> {
    return mapOf(
      PersistentConfigSchema.FILE_PRESETS_EQ to JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("eqPresets", encodeEqPresetsArray(data.eqPresets))
      },
      PersistentConfigSchema.FILE_PRESETS_FX to JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("fxPresets", encodeFxPresetsArray(data.fxPresets))
      },
      PersistentConfigSchema.FILE_PRESETS_CURVES to JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("fadeCurves", encodeFadeCurvesArray(data.fadeCurves))
      }
    )
  }

  override fun importBackupJson(json: JSONObject): PresetsBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    return PresetsBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      eqPresets = decodeEqPresetsArray(migrated.optJSONArray("eqPresets")),
      fxPresets = decodeFxPresetsArray(migrated.optJSONArray("fxPresets")),
      fadeCurves = decodeFadeCurvesArray(migrated.optJSONArray("fadeCurves"))
    )
  }

  fun importFromMergedPresetJsons(
    combinedJson: JSONObject? = null,
    eqJson: JSONObject? = null,
    fxJson: JSONObject? = null,
    curvesJson: JSONObject? = null
  ): PresetsBackupBundle {
    val base = combinedJson?.let { importBackupJson(it) } ?: PresetsBackupBundle()
    val eq = eqJson?.optJSONArray("eqPresets")?.let(::decodeEqPresetsArray) ?: base.eqPresets
    val fx = fxJson?.optJSONArray("fxPresets")?.let(::decodeFxPresetsArray) ?: base.fxPresets
    val curves = curvesJson?.optJSONArray("fadeCurves")?.let(::decodeFadeCurvesArray) ?: base.fadeCurves
    return base.copy(eqPresets = eq, fxPresets = fx, fadeCurves = curves)
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migratePresets(json, fromVersion)
  }

  private fun encodeEqPresetsArray(entries: List<EqPresetBackupEntry>): JSONArray = JSONArray().apply {
    entries.forEach { e ->
      put(
        JSONObject().apply {
          put("name", e.name)
          put("label", e.label)
          put("gainsDb", ProfileJsonSerializer.encodeEqBandsToJsonArray(e.gainsDb))
          put("isCustom", e.isCustom)
          put("profileId", e.profileId ?: JSONObject.NULL)
        }
      )
    }
  }

  private fun decodeEqPresetsArray(arr: JSONArray?): List<EqPresetBackupEntry> {
    if (arr == null || arr.length() == 0) return PresetsBackupBundle().eqPresets
    val out = mutableListOf<EqPresetBackupEntry>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      out.add(
        EqPresetBackupEntry(
          name = obj.optString("name", EqPreset.CUSTOM.name),
          label = obj.optString("label", "EQ Preset"),
          gainsDb = ProfileJsonSerializer.decodeEqBandsJsonArray(obj.optJSONArray("gainsDb")),
          isCustom = obj.optBoolean("isCustom", false),
          profileId = optNullableString(obj, "profileId")
        )
      )
    }
    return out
  }

  private fun encodeFxPresetsArray(entries: List<FxPresetBackupEntry>): JSONArray = JSONArray().apply {
    entries.forEach { e ->
      put(
        JSONObject().apply {
          put("name", e.name)
          put("label", e.label)
          put("echoEnabled", e.echoEnabled)
          put("echoDelayMs", e.echoDelayMs.toDouble())
          put("echoFeedback", e.echoFeedback.toDouble())
          put("echoMix", e.echoMix.toDouble())
          put("reverbEnabled", e.reverbEnabled)
          put("reverbRoomSize", e.reverbRoomSize.toDouble())
          put("reverbDamping", e.reverbDamping.toDouble())
          put("reverbMix", e.reverbMix.toDouble())
          put("isCustom", e.isCustom)
        }
      )
    }
  }

  private fun decodeFxPresetsArray(arr: JSONArray?): List<FxPresetBackupEntry> {
    if (arr == null || arr.length() == 0) return PresetsBackupBundle().fxPresets
    val out = mutableListOf<FxPresetBackupEntry>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      out.add(
        FxPresetBackupEntry(
          name = obj.optString("name", FxPreset.CUSTOM.name),
          label = obj.optString("label", "FX Preset"),
          echoEnabled = obj.optBoolean("echoEnabled", false),
          echoDelayMs = obj.optDouble("echoDelayMs", 220.0).toFloat().coerceIn(60f, 800f),
          echoFeedback = obj.optDouble("echoFeedback", 0.30).toFloat().coerceIn(0f, 0.75f),
          echoMix = obj.optDouble("echoMix", 0.25).toFloat().coerceIn(0f, 0.85f),
          reverbEnabled = obj.optBoolean("reverbEnabled", false),
          reverbRoomSize = obj.optDouble("reverbRoomSize", 0.35).toFloat().coerceIn(0f, 1f),
          reverbDamping = obj.optDouble("reverbDamping", 0.45).toFloat().coerceIn(0f, 1f),
          reverbMix = obj.optDouble("reverbMix", 0.25).toFloat().coerceIn(0f, 0.85f),
          isCustom = obj.optBoolean("isCustom", false)
        )
      )
    }
    return out
  }

  private fun encodeFadeCurvesArray(entries: List<FadeCurveBackupEntry>): JSONArray = JSONArray().apply {
    entries.forEach { e ->
      put(
        JSONObject().apply {
          put("name", e.name)
          put("label", e.label)
          put("shortLabel", e.shortLabel)
          put("defaultFadeInMs", e.defaultFadeInMs)
          put("defaultFadeOutMs", e.defaultFadeOutMs)
          put("isGlobalFadeCurve", e.isGlobalFadeCurve)
          put("isGlobalCrossfadeCurve", e.isGlobalCrossfadeCurve)
        }
      )
    }
  }

  private fun decodeFadeCurvesArray(arr: JSONArray?): List<FadeCurveBackupEntry> {
    if (arr == null || arr.length() == 0) return PresetsBackupBundle().fadeCurves
    val out = mutableListOf<FadeCurveBackupEntry>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      out.add(
        FadeCurveBackupEntry(
          name = obj.optString("name", FadeCurve.EQUAL_POWER.name),
          label = obj.optString("label", "Equal-Power"),
          shortLabel = obj.optString("shortLabel", "EQ-PWR"),
          defaultFadeInMs = obj.optLong("defaultFadeInMs", 0L).coerceIn(0L, 10000L),
          defaultFadeOutMs = obj.optLong("defaultFadeOutMs", 0L).coerceIn(0L, 10000L),
          isGlobalFadeCurve = obj.optBoolean("isGlobalFadeCurve", false),
          isGlobalCrossfadeCurve = obj.optBoolean("isGlobalCrossfadeCurve", false)
        )
      )
    }
    return out
  }
}

object MidiMappingBackupSerializer : Backupable<MidiMappingBackupBundle> {
  override val backupKey: String = "midi_mapping"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_MIDI_MAPPING
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: MidiMappingBackupBundle): JSONObject {
    val ccArr = JSONArray().apply {
      data.ccMappings.forEach { m ->
        put(
          JSONObject().apply {
            put("id", m.id)
            put("profileId", m.profileId)
            put("target", m.target.name)
            put("ccNumber", m.ccNumber)
            put("channel", m.channel)
          }
        )
      }
    }
    val pcArr = JSONArray().apply {
      data.pcMappings.forEach { m ->
        put(
          JSONObject().apply {
            put("id", m.id)
            put("profileId", m.profileId)
            put("programNumber", m.programNumber)
            put("bankIndex", m.bankIndex)
            put("channel", m.channel)
          }
        )
      }
    }
    val padNotesArr = JSONArray().apply {
      data.padNoteMappings.forEach { n ->
        put(
          JSONObject().apply {
            put("profileId", n.profileId)
            put("padId", n.padId)
            put("padName", n.padName)
            put("fileName", n.fileName)
            put("slotIndex", n.slotIndex)
            put("bankIndex", n.bankIndex)
            put("midiNote", n.midiNote)
          }
        )
      }
    }
    val seqNotesArr = JSONArray().apply {
      data.sequencerNoteMappings.forEach { s ->
        put(
          JSONObject().apply {
            put("playlistId", s.playlistId)
            put("playlistName", s.playlistName)
            put("midiNote", s.midiNote)
          }
        )
      }
    }
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("focusModeMidiNote", data.focusModeMidiNote ?: JSONObject.NULL)
      put("ccMappings", ccArr)
      put("pcMappings", pcArr)
      put("padNoteMappings", padNotesArr)
      put("sequencerNoteMappings", seqNotesArr)
    }
  }

  override fun importBackupJson(json: JSONObject): MidiMappingBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json

    val ccArr = migrated.optJSONArray("ccMappings") ?: JSONArray()
    val ccList = mutableListOf<MidiCcMapping>()
    for (i in 0 until ccArr.length()) {
      val obj = ccArr.optJSONObject(i) ?: continue
      val target = runCatching { MidiCcTarget.valueOf(obj.optString("target")) }.getOrNull() ?: continue
      val ccNum = obj.optInt("ccNumber", -1)
      if (ccNum !in 0..127) continue
      val profileId = obj.optString("profileId", "default_profile").ifBlank { "default_profile" }
      ccList.add(
        MidiCcMapping(
          id = obj.optString("id", "").ifBlank { "cc_${profileId}_${target.name}" },
          profileId = profileId,
          target = target,
          ccNumber = ccNum,
          channel = obj.optInt("channel", -1).coerceIn(-1, 15)
        )
      )
    }

    val pcArr = migrated.optJSONArray("pcMappings") ?: JSONArray()
    val pcList = mutableListOf<MidiProgramBankMapping>()
    for (i in 0 until pcArr.length()) {
      val obj = pcArr.optJSONObject(i) ?: continue
      val program = obj.optInt("programNumber", -1)
      val bankIdx = obj.optInt("bankIndex", -1)
      if (program !in 0..127 || bankIdx < 0) continue
      val profileId = obj.optString("profileId", "default_profile").ifBlank { "default_profile" }
      pcList.add(
        MidiProgramBankMapping(
          id = obj.optString("id", "").ifBlank { "pc_${profileId}_$bankIdx" },
          profileId = profileId,
          programNumber = program,
          bankIndex = bankIdx,
          channel = obj.optInt("channel", -1).coerceIn(-1, 15)
        )
      )
    }

    val padNotesArr = migrated.optJSONArray("padNoteMappings") ?: JSONArray()
    val padNotes = mutableListOf<PadMidiNoteEntry>()
    for (i in 0 until padNotesArr.length()) {
      val obj = padNotesArr.optJSONObject(i) ?: continue
      val note = obj.optInt("midiNote", -1)
      if (note !in 0..127) continue
      padNotes.add(
        PadMidiNoteEntry(
          profileId = obj.optString("profileId", "default_profile"),
          padId = obj.optString("padId", ""),
          padName = obj.optString("padName", ""),
          fileName = obj.optString("fileName", ""),
          slotIndex = obj.optInt("slotIndex", 0).coerceAtLeast(0),
          bankIndex = obj.optInt("bankIndex", 0).coerceAtLeast(0),
          midiNote = note
        )
      )
    }

    val seqNotesArr = migrated.optJSONArray("sequencerNoteMappings") ?: JSONArray()
    val seqNotes = mutableListOf<SequencerMidiNoteEntry>()
    for (i in 0 until seqNotesArr.length()) {
      val obj = seqNotesArr.optJSONObject(i) ?: continue
      val note = obj.optInt("midiNote", -1)
      if (note !in 0..127) continue
      seqNotes.add(
        SequencerMidiNoteEntry(
          playlistId = obj.optString("playlistId", ""),
          playlistName = obj.optString("playlistName", ""),
          midiNote = note
        )
      )
    }

    return MidiMappingBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      focusModeMidiNote = optNullableInt(migrated, "focusModeMidiNote")?.coerceIn(0, 127),
      ccMappings = ccList,
      pcMappings = pcList,
      padNoteMappings = padNotes,
      sequencerNoteMappings = seqNotes
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateMidiMapping(json, fromVersion)
  }
}

object SchedulerBackupSerializer : Backupable<SchedulerBackupBundle> {
  override val backupKey: String = "scheduler"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_SCHEDULER
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: SchedulerBackupBundle): JSONObject {
    val tasksArr = JSONArray()
    data.tasks.forEach { t ->
      tasksArr.put(
        JSONObject().apply {
          put("id", t.id)
          put("title", t.title)
          put("targetType", t.targetType.name)
          put("targetId", t.targetId)
          put("hour", t.hour)
          put("minute", t.minute)
          put("repeatMode", t.repeatMode.name)
          put("daysOfWeekMask", t.daysOfWeekMask)
          put("enabled", t.enabled)
          put("volume", t.volume.toDouble())
          put("fadeInMs", t.fadeInMs)
          put("autoStopMinutes", t.autoStopMinutes)
          put("loopPlayback", t.loopPlayback)
          put("nextTriggerAtMs", t.nextTriggerAtMs)
          put("createdAtMs", t.createdAtMs)
        }
      )
    }
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("tasks", tasksArr)
    }
  }

  override fun importBackupJson(json: JSONObject): SchedulerBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    val tasksArr = migrated.optJSONArray("tasks") ?: JSONArray()
    val tasks = mutableListOf<ScheduledPadTask>()
    for (i in 0 until tasksArr.length()) {
      val obj = tasksArr.optJSONObject(i) ?: continue
      val id = obj.optString("id", "").ifBlank { "sched_${UUID.randomUUID()}" }
      tasks.add(
        ScheduledPadTask(
          id = id,
          title = obj.optString("title", "Jadwal").ifBlank { "Jadwal" },
          targetType = runCatching { ScheduleTargetType.valueOf(obj.optString("targetType")) }
            .getOrDefault(ScheduleTargetType.PAD),
          targetId = obj.optString("targetId", ""),
          hour = obj.optInt("hour", 9).coerceIn(0, 23),
          minute = obj.optInt("minute", 0).coerceIn(0, 59),
          repeatMode = runCatching { ScheduleRepeatMode.valueOf(obj.optString("repeatMode")) }
            .getOrDefault(ScheduleRepeatMode.ONCE),
          daysOfWeekMask = obj.optInt("daysOfWeekMask", 0),
          enabled = obj.optBoolean("enabled", true),
          volume = obj.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f),
          fadeInMs = obj.optLong("fadeInMs", 0L).coerceIn(0L, 10000L),
          autoStopMinutes = obj.optInt("autoStopMinutes", 0).coerceAtLeast(0),
          loopPlayback = obj.optBoolean("loopPlayback", false),
          nextTriggerAtMs = obj.optLong("nextTriggerAtMs", 0L).coerceAtLeast(0L),
          createdAtMs = obj.optLong("createdAtMs", System.currentTimeMillis())
        )
      )
    }
    return SchedulerBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      tasks = tasks
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateScheduler(json, fromVersion)
  }
}

object SequencerBackupSerializer : Backupable<SequencerBackupBundle> {
  override val backupKey: String = "sequencer"
  override val defaultFilePath: String = PersistentConfigSchema.FILE_SEQUENCER
  override val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

  override fun exportBackupJson(data: SequencerBackupBundle): JSONObject {
    val playlistsArr = JSONArray()
    data.playlists.forEach { p ->
      playlistsArr.put(
        JSONObject().apply {
          put("id", p.id)
          put("name", p.name)
          put("padIds", JSONArray(p.padIds))
          put("mode", p.mode.name)
          put("gapSeconds", p.gapSeconds.toDouble())
          put("crossfadeEnabled", p.crossfadeEnabled)
          put("crossfadeDurationMs", p.crossfadeDurationMs)
          put("triggerPadId", p.triggerPadId ?: JSONObject.NULL)
          put("triggerMidiNote", p.triggerMidiNote ?: JSONObject.NULL)
          put("updatedAtMs", p.updatedAtMs)
        }
      )
    }
    return JSONObject().apply {
      put("schemaVersion", schemaVersion)
      put("playlists", playlistsArr)
    }
  }

  override fun importBackupJson(json: JSONObject): SequencerBackupBundle {
    val detected = ConfigSchemaMigrator.detectSchemaVersion(json)
    val migrated = if (detected < schemaVersion) migrateSchema(json, detected) else json
    val playlistsArr = migrated.optJSONArray("playlists") ?: JSONArray()
    val playlists = mutableListOf<SequencerPlaylist>()
    for (i in 0 until playlistsArr.length()) {
      val obj = playlistsArr.optJSONObject(i) ?: continue
      val padIdsArr = obj.optJSONArray("padIds") ?: JSONArray()
      val padIds = buildList {
        for (j in 0 until padIdsArr.length()) {
          val padId = padIdsArr.optString(j, "").trim()
          if (padId.isNotEmpty()) add(padId)
        }
      }
      playlists.add(
        SequencerPlaylist(
          id = obj.optString("id", "").ifBlank { "seq_${UUID.randomUUID()}" },
          name = obj.optString("name", "Playlist").ifBlank { "Playlist" },
          padIds = padIds,
          mode = runCatching { SequencerPlayMode.valueOf(obj.optString("mode")) }
            .getOrDefault(SequencerPlayMode.SEQUENTIAL),
          gapSeconds = obj.optDouble("gapSeconds", 0.0).toFloat().coerceIn(0f, 120f),
          crossfadeEnabled = obj.optBoolean("crossfadeEnabled", false),
          crossfadeDurationMs = obj.optLong("crossfadeDurationMs", 1500L).coerceIn(100L, 15000L),
          triggerPadId = optNullableString(obj, "triggerPadId"),
          triggerMidiNote = optNullableInt(obj, "triggerMidiNote")?.coerceIn(0, 127),
          updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis())
        )
      )
    }
    return SequencerBackupBundle(
      schemaVersion = migrated.optInt("schemaVersion", schemaVersion),
      playlists = playlists
    )
  }

  override fun migrateSchema(json: JSONObject, fromVersion: Int): JSONObject {
    return ConfigSchemaMigrator.migrateSequencer(json, fromVersion)
  }
}

object PersistentConfigBundleSerializer {

  /**
   * Menghasilkan peta file terstruktur sesuai spesifikasi Persistent Config:
   * - config_meta.json
   * - settings.json
   * - profiles/<nama>.json
   * - layouts/<nama>.json
   * - presets/eq_presets.json, presets/fx_presets.json, presets/fade_curves.json
   * - midi_mapping.json
   * - scheduler.json
   * - sequencer.json
   */
  fun exportToFolderFiles(
    bundle: PersistentConfigBundle,
    includeWaveforms: Boolean = bundle.meta.includesWaveforms
  ): Map<String, String> {
    val files = linkedMapOf<String, String>()
    val effectiveLayouts = bundle.layouts.ifEmpty { deriveLayoutsFromProfiles(bundle.profiles) }
    val effectiveMeta = finalizeMeta(bundle, effectiveLayouts, includeWaveforms)

    files[PersistentConfigSchema.FILE_CONFIG_META] =
      ConfigMetaBackupSerializer.exportBackupJson(effectiveMeta).toString(2)
    files[PersistentConfigSchema.FILE_SETTINGS] =
      SettingsBackupSerializer.exportBackupJson(bundle.settings).toString(2)

    val usedProfileSlugs = mutableSetOf<String>()
    bundle.profiles.forEachIndexed { idx, profileBundle ->
      val baseSlug = FileHashAndPathHelper.sanitizeFileSlug(profileBundle.profile.name, "profile_${idx + 1}")
      val uniqueSlug = makeUniqueSlug(baseSlug, usedProfileSlugs)
      files["${PersistentConfigSchema.DIR_PROFILES}/$uniqueSlug.json"] =
        ProfileBackupSerializer.exportBackupJson(profileBundle, includeWaveforms = includeWaveforms).toString(2)
    }

    val usedLayoutSlugs = mutableSetOf<String>()
    effectiveLayouts.forEachIndexed { idx, layout ->
      val baseSlug = FileHashAndPathHelper.sanitizeFileSlug(layout.name, "layout_${idx + 1}")
      val uniqueSlug = makeUniqueSlug(baseSlug, usedLayoutSlugs)
      files["${PersistentConfigSchema.DIR_LAYOUTS}/$uniqueSlug.json"] =
        LayoutBackupSerializer.exportBackupJson(layout).toString(2)
    }

    PresetsBackupSerializer.exportSplitPresetFiles(bundle.presets).forEach { (path, jsonObj) ->
      files[path] = jsonObj.toString(2)
    }

    files[PersistentConfigSchema.FILE_MIDI_MAPPING] =
      MidiMappingBackupSerializer.exportBackupJson(bundle.midiMapping).toString(2)
    files[PersistentConfigSchema.FILE_SCHEDULER] =
      SchedulerBackupSerializer.exportBackupJson(bundle.scheduler).toString(2)
    files[PersistentConfigSchema.FILE_SEQUENCER] =
      SequencerBackupSerializer.exportBackupJson(bundle.sequencer).toString(2)

    // Kontributor tambahan (interface Backupable / BackupSectionContributor dari fase selanjutnya)
    val mergedExtensions = linkedMapOf<String, JSONObject>().apply {
      putAll(bundle.extensionSections)
      putAll(BackupSectionRegistry.exportAllExtensions())
    }
    mergedExtensions.forEach { (key, extJson) ->
      val safeKey = FileHashAndPathHelper.sanitizeFileSlug(key, "ext")
      files["extensions/$safeKey.json"] = extJson.toString(2)
    }

    return files
  }

  /**
   * Menghasilkan JSON tunggal gabungan (.soundpadpro/config.json atau snapshot tunggal).
   */
  fun exportCombinedJson(
    bundle: PersistentConfigBundle,
    includeWaveforms: Boolean = bundle.meta.includesWaveforms
  ): String {
    val effectiveLayouts = bundle.layouts.ifEmpty { deriveLayoutsFromProfiles(bundle.profiles) }
    val effectiveMeta = finalizeMeta(bundle, effectiveLayouts, includeWaveforms)

    val root = JSONObject().apply {
      put("schemaVersion", PersistentConfigSchema.CURRENT_SCHEMA_VERSION)
      put("meta", ConfigMetaBackupSerializer.exportBackupJson(effectiveMeta))
      put("settings", SettingsBackupSerializer.exportBackupJson(bundle.settings))
      put(
        "profiles",
        JSONArray().apply {
          bundle.profiles.forEach { put(ProfileBackupSerializer.exportBackupJson(it, includeWaveforms)) }
        }
      )
      put(
        "layouts",
        JSONArray().apply {
          effectiveLayouts.forEach { put(LayoutBackupSerializer.exportBackupJson(it)) }
        }
      )
      put("presets", PresetsBackupSerializer.exportBackupJson(bundle.presets))
      put("midiMapping", MidiMappingBackupSerializer.exportBackupJson(bundle.midiMapping))
      put("scheduler", SchedulerBackupSerializer.exportBackupJson(bundle.scheduler))
      put("sequencer", SequencerBackupSerializer.exportBackupJson(bundle.sequencer))

      val mergedExtensions = linkedMapOf<String, JSONObject>().apply {
        putAll(bundle.extensionSections)
        putAll(BackupSectionRegistry.exportAllExtensions())
      }
      if (mergedExtensions.isNotEmpty()) {
        put(
          "extensions",
          JSONObject().apply {
            mergedExtensions.forEach { (k, v) -> put(k, v) }
          }
        )
      }
    }
    return root.toString(2)
  }

  /**
   * Mengimpor kembali struktur folder multi-file ke [PersistentConfigBundle] dengan
   * validasi JSON, migrasi schemaVersion otomatis, dan isolasi error per bagian.
   */
  fun importFromFolderFiles(files: Map<String, String>): PersistentConfigRestoreResult {
    val warnings = mutableListOf<String>()
    val restoredKeys = linkedSetOf<String>()
    var minMigratedFrom = PersistentConfigSchema.CURRENT_SCHEMA_VERSION

    fun parseObject(path: String): JSONObject? {
      val raw = files[path] ?: return null
      return runCatching { JSONObject(raw) }
        .onFailure { err -> warnings.add("File JSON rusak pada '$path': ${err.message}") }
        .getOrNull()
    }

    val metaJson = parseObject(PersistentConfigSchema.FILE_CONFIG_META)
    val meta = if (metaJson != null) {
      val v = ConfigSchemaMigrator.detectSchemaVersion(metaJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      restoredKeys.add("meta")
      ConfigMetaBackupSerializer.importBackupJson(metaJson)
    } else {
      ConfigMetaJson()
    }

    val settingsJson = parseObject(PersistentConfigSchema.FILE_SETTINGS)
    val settings = if (settingsJson != null) {
      val v = ConfigSchemaMigrator.detectSchemaVersion(settingsJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      restoredKeys.add("settings")
      SettingsBackupSerializer.importBackupJson(settingsJson)
    } else {
      SettingsBackupBundle()
    }

    val profileEntries = files.keys
      .filter { it.startsWith("${PersistentConfigSchema.DIR_PROFILES}/") && it.endsWith(".json") }
      .sorted()
    val profiles = mutableListOf<ProfileBackupBundle>()
    profileEntries.forEach { path ->
      val pJson = parseObject(path) ?: return@forEach
      val v = ConfigSchemaMigrator.detectSchemaVersion(pJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      runCatching { ProfileBackupSerializer.importBackupJson(pJson) }
        .onSuccess {
          profiles.add(it)
          restoredKeys.add("profiles")
        }
        .onFailure { err -> warnings.add("Gagal memuat profil '$path': ${err.message}") }
    }

    val layoutEntries = files.keys
      .filter { it.startsWith("${PersistentConfigSchema.DIR_LAYOUTS}/") && it.endsWith(".json") }
      .sorted()
    val layouts = mutableListOf<PadLayoutBackup>()
    layoutEntries.forEach { path ->
      val lJson = parseObject(path) ?: return@forEach
      val v = ConfigSchemaMigrator.detectSchemaVersion(lJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      runCatching { LayoutBackupSerializer.importBackupJson(lJson) }
        .onSuccess {
          layouts.add(it)
          restoredKeys.add("layouts")
        }
        .onFailure { err -> warnings.add("Gagal memuat layout '$path': ${err.message}") }
    }

    val combinedPresetsJson = parseObject(PersistentConfigSchema.FILE_PRESETS_COMBINED)
    val eqJson = parseObject(PersistentConfigSchema.FILE_PRESETS_EQ)
    val fxJson = parseObject(PersistentConfigSchema.FILE_PRESETS_FX)
    val curvesJson = parseObject(PersistentConfigSchema.FILE_PRESETS_CURVES)
    val presets = if (combinedPresetsJson != null || eqJson != null || fxJson != null || curvesJson != null) {
      restoredKeys.add("presets")
      PresetsBackupSerializer.importFromMergedPresetJsons(
        combinedJson = combinedPresetsJson,
        eqJson = eqJson,
        fxJson = fxJson,
        curvesJson = curvesJson
      )
    } else {
      PresetsBackupBundle()
    }

    val midiJson = parseObject(PersistentConfigSchema.FILE_MIDI_MAPPING)
    val midiMapping = if (midiJson != null) {
      val v = ConfigSchemaMigrator.detectSchemaVersion(midiJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      restoredKeys.add("midi_mapping")
      MidiMappingBackupSerializer.importBackupJson(midiJson)
    } else {
      MidiMappingBackupBundle()
    }

    val schedulerJson = parseObject(PersistentConfigSchema.FILE_SCHEDULER)
    val scheduler = if (schedulerJson != null) {
      val v = ConfigSchemaMigrator.detectSchemaVersion(schedulerJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      restoredKeys.add("scheduler")
      SchedulerBackupSerializer.importBackupJson(schedulerJson)
    } else {
      SchedulerBackupBundle()
    }

    val sequencerJson = parseObject(PersistentConfigSchema.FILE_SEQUENCER)
    val sequencer = if (sequencerJson != null) {
      val v = ConfigSchemaMigrator.detectSchemaVersion(sequencerJson)
      minMigratedFrom = minOf(minMigratedFrom, v)
      restoredKeys.add("sequencer")
      SequencerBackupSerializer.importBackupJson(sequencerJson)
    } else {
      SequencerBackupBundle()
    }

    val extensions = linkedMapOf<String, JSONObject>()
    files.keys.filter { it.startsWith("extensions/") && it.endsWith(".json") }.sorted().forEach { path ->
      val extJson = parseObject(path) ?: return@forEach
      val extKey = path.removePrefix("extensions/").removeSuffix(".json")
      extensions[extKey] = extJson
    }
    if (extensions.isNotEmpty()) {
      warnings.addAll(BackupSectionRegistry.importAllExtensions(extensions))
      restoredKeys.add("extensions")
    }

    return PersistentConfigRestoreResult(
      bundle = PersistentConfigBundle(
        meta = meta,
        settings = settings,
        profiles = profiles,
        layouts = layouts,
        presets = presets,
        midiMapping = midiMapping,
        scheduler = scheduler,
        sequencer = sequencer,
        extensionSections = extensions
      ),
      migratedFromVersion = minMigratedFrom,
      warnings = warnings,
      restoredSectionKeys = restoredKeys
    )
  }

  /**
   * Mengimpor JSON tunggal gabungan (.soundpadpro/config.json maupun format lama FullBackupSerializer).
   */
  fun importCombinedJson(jsonString: String): PersistentConfigRestoreResult {
    val root = JSONObject(jsonString)
    val warnings = mutableListOf<String>()
    val restoredKeys = linkedSetOf<String>()
    val detectedVersion = ConfigSchemaMigrator.detectSchemaVersion(root)

    // Jika ini format legacy FullBackupSerializer (tanpa node "meta" / "settings" baru, hanya globalSettings)
    if (!root.has("meta") && root.has("globalSettings")) {
      val legacy = FullBackupSerializer.importBackupJson(jsonString)
      val settingsBundle = SettingsBackupBundle(settings = legacy.globalSettings)
      val profileBundles = legacy.profiles.map { exported ->
        val entries = exported.pads.map { p ->
          PadBackupEntry(pad = p, slotIndex = p.positionIndex.coerceAtLeast(0))
        }
        val banks = (exported.profile.bankOrder + exported.profile.bankNames.keys + exported.profile.bankFolderUris.keys)
          .distinct()
          .mapIndexed { bOrder, bIdx ->
            BankBackupEntry(
              bankIndex = bIdx,
              name = exported.profile.bankNames[bIdx] ?: "B${bIdx + 1}",
              folderUri = exported.profile.bankFolderUris[bIdx],
              orderPosition = bOrder
            )
          }
        ProfileBackupBundle(
          profile = exported.profile,
          banks = banks,
          pads = entries,
          ccMappings = exported.ccMappings
        )
      }
      val layouts = deriveLayoutsFromProfiles(profileBundles)
      val bundle = PersistentConfigBundle(
        meta = ConfigMetaJson(
          schemaVersion = detectedVersion,
          profileCount = profileBundles.size,
          padCount = profileBundles.sumOf { it.pads.size },
          layoutCount = layouts.size,
          backupSource = "legacy_full_backup"
        ),
        settings = settingsBundle,
        profiles = profileBundles,
        layouts = layouts,
        presets = deriveDefaultPresets(legacy.globalSettings, profileBundles),
        midiMapping = MidiMappingBackupBundle(
          focusModeMidiNote = legacy.globalSettings.focusModeMidiNote,
          ccMappings = profileBundles.flatMap { it.ccMappings }
        )
      )
      return PersistentConfigRestoreResult(
        bundle = bundle,
        migratedFromVersion = detectedVersion,
        warnings = warnings,
        restoredSectionKeys = setOf("settings", "profiles", "layouts", "presets", "midi_mapping")
      )
    }

    val meta = root.optJSONObject("meta")?.let {
      restoredKeys.add("meta")
      ConfigMetaBackupSerializer.importBackupJson(it)
    } ?: ConfigMetaJson()

    val settings = root.optJSONObject("settings")?.let {
      restoredKeys.add("settings")
      SettingsBackupSerializer.importBackupJson(it)
    } ?: SettingsBackupBundle()

    val profilesArr = root.optJSONArray("profiles") ?: JSONArray()
    val profiles = mutableListOf<ProfileBackupBundle>()
    for (i in 0 until profilesArr.length()) {
      val obj = profilesArr.optJSONObject(i) ?: continue
      runCatching { ProfileBackupSerializer.importBackupJson(obj) }
        .onSuccess {
          profiles.add(it)
          restoredKeys.add("profiles")
        }
        .onFailure { warnings.add("Profil #${i + 1} gagal dimuat: ${it.message}") }
    }

    val layoutsArr = root.optJSONArray("layouts") ?: JSONArray()
    val layouts = mutableListOf<PadLayoutBackup>()
    for (i in 0 until layoutsArr.length()) {
      val obj = layoutsArr.optJSONObject(i) ?: continue
      runCatching { LayoutBackupSerializer.importBackupJson(obj) }
        .onSuccess {
          layouts.add(it)
          restoredKeys.add("layouts")
        }
        .onFailure { warnings.add("Layout #${i + 1} gagal dimuat: ${it.message}") }
    }

    val presets = root.optJSONObject("presets")?.let {
      restoredKeys.add("presets")
      PresetsBackupSerializer.importBackupJson(it)
    } ?: PresetsBackupBundle()

    val midiMapping = root.optJSONObject("midiMapping")?.let {
      restoredKeys.add("midi_mapping")
      MidiMappingBackupSerializer.importBackupJson(it)
    } ?: MidiMappingBackupBundle()

    val scheduler = root.optJSONObject("scheduler")?.let {
      restoredKeys.add("scheduler")
      SchedulerBackupSerializer.importBackupJson(it)
    } ?: SchedulerBackupBundle()

    val sequencer = root.optJSONObject("sequencer")?.let {
      restoredKeys.add("sequencer")
      SequencerBackupSerializer.importBackupJson(it)
    } ?: SequencerBackupBundle()

    val extensions = linkedMapOf<String, JSONObject>()
    val extObj = root.optJSONObject("extensions")
    if (extObj != null) {
      val keys = extObj.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        val v = extObj.optJSONObject(k) ?: continue
        extensions[k] = v
      }
      if (extensions.isNotEmpty()) {
        warnings.addAll(BackupSectionRegistry.importAllExtensions(extensions))
        restoredKeys.add("extensions")
      }
    }

    return PersistentConfigRestoreResult(
      bundle = PersistentConfigBundle(
        meta = meta,
        settings = settings,
        profiles = profiles,
        layouts = layouts,
        presets = presets,
        midiMapping = midiMapping,
        scheduler = scheduler,
        sequencer = sequencer,
        extensionSections = extensions
      ),
      migratedFromVersion = detectedVersion,
      warnings = warnings,
      restoredSectionKeys = restoredKeys
    )
  }

  fun deriveLayoutsFromProfiles(profiles: List<ProfileBackupBundle>): List<PadLayoutBackup> {
    return profiles.map { pBundle ->
      val p = pBundle.profile
      val slots = pBundle.pads.map { entry ->
        LayoutSlotEntry(
          padId = entry.pad.id,
          uriString = entry.fileMetadata.uriString,
          relativePath = entry.fileMetadata.relativePath,
          fileName = entry.fileMetadata.fileName,
          sizeBytes = entry.fileMetadata.sizeBytes,
          sha1Hash = entry.fileMetadata.sha1Hash,
          bankIndex = entry.pad.bankIndex,
          slotIndex = entry.slotIndex,
          colorArgb = entry.pad.colorArgb,
          displayName = entry.pad.displayName
        )
      }
      PadLayoutBackup(
        layoutId = "layout_${p.id}_default",
        profileId = p.id,
        name = "${p.name} Layout",
        gridRows = p.gridConfig.rows,
        gridCols = p.gridConfig.cols,
        isDefault = true,
        createdAtMs = p.createdAtMs,
        updatedAtMs = p.updatedAtMs,
        slots = slots
      )
    }
  }

  fun deriveDefaultPresets(
    settings: GlobalSettings,
    profiles: List<ProfileBackupBundle>
  ): PresetsBackupBundle {
    val eqList = EqPreset.entries.map { preset ->
      EqPresetBackupEntry(
        name = preset.name,
        label = preset.label,
        gainsDb = if (preset == EqPreset.CUSTOM && settings.masterEqPreset == EqPreset.CUSTOM) {
          settings.masterEqBandsDb
        } else {
          preset.gainsDb
        },
        isCustom = preset == EqPreset.CUSTOM
      )
    }.toMutableList()

    profiles.forEach { bundle ->
      val p = bundle.profile
      if (p.masterEqEnabled || p.masterEqPreset == EqPreset.CUSTOM) {
        eqList.add(
          EqPresetBackupEntry(
            name = "PROFILE_${p.id}",
            label = "${p.name} Master EQ",
            gainsDb = p.masterEqBandsDb,
            isCustom = true,
            profileId = p.id
          )
        )
      }
    }

    val fadeList = FadeCurve.entries.map { curve ->
      FadeCurveBackupEntry(
        name = curve.name,
        label = curve.label,
        shortLabel = curve.shortLabel,
        defaultFadeInMs = if (curve == settings.globalFadeCurve) settings.globalFadeInMs else 0L,
        defaultFadeOutMs = if (curve == settings.globalFadeCurve) settings.globalFadeOutMs else 0L,
        isGlobalFadeCurve = curve == settings.globalFadeCurve,
        isGlobalCrossfadeCurve = curve == settings.crossfadeCurve
      )
    }

    return PresetsBackupBundle(
      eqPresets = eqList,
      fxPresets = PresetsBackupBundle().fxPresets,
      fadeCurves = fadeList
    )
  }

  private fun finalizeMeta(
    bundle: PersistentConfigBundle,
    layouts: List<PadLayoutBackup>,
    includeWaveforms: Boolean
  ): ConfigMetaJson {
    val now = System.currentTimeMillis()
    val totalBanks = bundle.profiles.sumOf { p ->
      maxOf(p.banks.size, p.pads.map { it.pad.bankIndex }.distinct().size)
    }
    val totalPads = bundle.profiles.sumOf { it.pads.size }
    val included = buildList {
      add("settings")
      add("profiles")
      add("layouts")
      add("presets")
      add("midi_mapping")
      add("scheduler")
      add("sequencer")
      if (bundle.extensionSections.isNotEmpty() || BackupSectionRegistry.registeredKeys().isNotEmpty()) {
        add("extensions")
      }
    }
    return bundle.meta.copy(
      schemaVersion = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
      updatedAtMs = now,
      lastSyncedAtMs = if (bundle.meta.lastSyncedAtMs > 0L) bundle.meta.lastSyncedAtMs else now,
      profileCount = bundle.profiles.size,
      bankCount = totalBanks,
      padCount = totalPads,
      layoutCount = layouts.size,
      schedulerCount = bundle.scheduler.tasks.size,
      sequencerCount = bundle.sequencer.playlists.size,
      includesWaveforms = includeWaveforms,
      includedSections = included
    )
  }

  private fun makeUniqueSlug(baseSlug: String, used: MutableSet<String>): String {
    var candidate = baseSlug
    var suffix = 2
    while (!used.add(candidate)) {
      candidate = "${baseSlug}_$suffix"
      suffix++
    }
    return candidate
  }
}

private fun optNullableString(obj: JSONObject, key: String): String? {
  if (!obj.has(key) || obj.isNull(key)) return null
  return obj.optString(key, "").ifBlank { null }
}

private fun optNullableInt(obj: JSONObject, key: String): Int? {
  if (!obj.has(key) || obj.isNull(key)) return null
  return obj.optInt(key)
}

private fun optNullableLong(obj: JSONObject, key: String): Long? {
  if (!obj.has(key) || obj.isNull(key)) return null
  return obj.optLong(key)
}
