package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import com.example.audio.toEntity
import com.example.data.local.db.ErrorLogEntity
import com.example.data.local.db.MidiCcMappingEntity
import com.example.data.local.db.MidiPcBankMappingEntity
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadDao
import com.example.data.local.db.SoundPadEntity
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.profile.ProfileJsonSerializer
import com.example.data.saf.SafAudioScanner
import com.example.domain.model.EqPreset
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.SoundPad
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID

enum class BackupSourceKind(val label: String) {
  SAF_FOLDER("Folder Backup SAF"),
  MUSIC_SUBFOLDER("Subfolder .soundpadpro"),
  ZIP_ARCHIVE("Arsip .zip"),
  SNAPSHOT_FILE("Snapshot Backup")
}

@Immutable
data class ProfilePreviewItem(
  val id: String,
  val name: String,
  val padCount: Int,
  val bankCount: Int
)

@Immutable
data class RestoreSelectionOptions(
  val restoreSettings: Boolean = true,
  val restoreProfilesAndPads: Boolean = true,
  val selectedProfileIds: Set<String> = emptySet(),
  val restoreMidiMapping: Boolean = true,
  val restoreSchedulerAndSequencer: Boolean = true
)

@Immutable
data class BackupInspectionSummary(
  val sourceLabel: String,
  val sourceKind: BackupSourceKind,
  val schemaVersion: Int,
  val migratedFromVersion: Int,
  val backupDateMs: Long,
  val appVersionName: String,
  val profileCount: Int,
  val bankCount: Int,
  val padCount: Int,
  val layoutCount: Int,
  val schedulerCount: Int,
  val sequencerCount: Int,
  val includesWaveforms: Boolean,
  val usedFallbackSnapshotName: String? = null,
  val detectedMusicFolderUri: String? = null,
  val warnings: List<String> = emptyList(),
  val profilesPreview: List<ProfilePreviewItem> = emptyList(),
  val availableSnapshots: List<SnapshotFileInfo> = emptyList(),
  val bundle: PersistentConfigBundle
)

data class IndexedAudioCandidate(
  val uriString: String,
  val relativePath: String,
  val fileName: String,
  val sizeBytes: Long,
  val subfolderName: String = "",
  val sha1Provider: () -> String = { "" }
) {
  private val lazySha1: String by lazy { sha1Provider() }
  fun quickSha1(): String = lazySha1
}

enum class PadRelinkMatchType {
  UNCHANGED_LOCAL,
  RELATIVE_PATH,
  NAME_AND_SIZE,
  SHA1_HASH,
  MISSING
}

data class PadRelinkOutcome(
  val entry: PadBackupEntry,
  val resolvedUriString: String,
  val matchType: PadRelinkMatchType,
  val resolvedRelativePath: String,
  val resolvedSubfolderName: String
)

@Immutable
data class RestoreExecutionOutcome(
  val success: Boolean,
  val restoredProfileCount: Int = 0,
  val restoredPadCount: Int = 0,
  val relinkedPadCount: Int = 0,
  val missingFilePadCount: Int = 0,
  val missingFileNames: List<String> = emptyList(),
  val usedFallbackSnapshotName: String? = null,
  val warnings: List<String> = emptyList(),
  val errorMessage: String? = null
)

@Immutable
data class RestoreUiState(
  val isInspecting: Boolean = false,
  val pendingInspection: BackupInspectionSummary? = null,
  val selectedRelinkMusicUri: String? = null,
  val selectedRelinkMusicName: String? = null,
  val availableSnapshots: List<SnapshotFileInfo> = emptyList(),
  val isSnapshotPickerOpen: Boolean = false,
  val isRestoreInProgress: Boolean = false,
  val restoreProgress: Float = 0f,
  val restoreStageText: String = "",
  val lastRestoreOutcome: RestoreExecutionOutcome? = null
)

object AudioFileRelinker {

  fun indexSafMusicFolder(
    context: Context,
    musicTreeUri: Uri
  ): List<IndexedAudioCandidate> {
    val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(musicTreeUri) }.getOrNull()
      ?: return emptyList()
    val candidates = mutableListOf<IndexedAudioCandidate>()
    traverseDirectory(
      context = context,
      treeUri = musicTreeUri,
      parentDocId = rootDocId,
      parentRelPath = "",
      topSubfolder = "",
      out = candidates
    )
    return candidates
  }

  private fun traverseDirectory(
    context: Context,
    treeUri: Uri,
    parentDocId: String,
    parentRelPath: String,
    topSubfolder: String,
    out: MutableList<IndexedAudioCandidate>
  ) {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE
    )
    runCatching {
      context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
        val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

        while (cursor.moveToNext()) {
          val docId = if (idCol >= 0) cursor.getString(idCol) else continue
          val name = if (nameCol >= 0) cursor.getString(nameCol) ?: "" else ""
          val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
          val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

          if (name.startsWith(".")) continue
          val currentRel = if (parentRelPath.isEmpty()) name else "$parentRelPath/$name"
          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            val nextTopSub = if (topSubfolder.isEmpty()) name else topSubfolder
            traverseDirectory(
              context = context,
              treeUri = treeUri,
              parentDocId = docId,
              parentRelPath = currentRel,
              topSubfolder = nextTopSub,
              out = out
            )
          } else {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext in SafAudioScanner.SUPPORTED_EXTENSIONS) {
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
              val docUriStr = docUri.toString()
              out.add(
                IndexedAudioCandidate(
                  uriString = docUriStr,
                  relativePath = currentRel,
                  fileName = name,
                  sizeBytes = size,
                  subfolderName = topSubfolder,
                  sha1Provider = {
                    FileHashAndPathHelper.computeQuickFileSha1(context, docUriStr, size)
                  }
                )
              )
            }
          }
        }
      }
    }
  }

  fun relinkPadEntry(
    entry: PadBackupEntry,
    candidates: List<IndexedAudioCandidate>,
    uriReadableChecker: (String) -> Boolean = { false }
  ): PadRelinkOutcome {
    val rawUri = entry.pad.uriString
    // Built-in demo tracks & existing local files stay valid without external folder scanning
    if (rawUri.startsWith("demo://")) {
      return PadRelinkOutcome(
        entry = entry,
        resolvedUriString = rawUri,
        matchType = PadRelinkMatchType.UNCHANGED_LOCAL,
        resolvedRelativePath = entry.fileMetadata.relativePath,
        resolvedSubfolderName = entry.pad.subfolderName
      )
    }
    if (rawUri.startsWith("file://")) {
      val path = runCatching { Uri.parse(rawUri).path }.getOrNull()
      if (path != null && File(path).exists()) {
        return PadRelinkOutcome(
          entry = entry,
          resolvedUriString = rawUri,
          matchType = PadRelinkMatchType.UNCHANGED_LOCAL,
          resolvedRelativePath = entry.fileMetadata.relativePath,
          resolvedSubfolderName = entry.pad.subfolderName
        )
      }
    }

    val expectedRel = normalizeRelPath(entry.fileMetadata.relativePath)
    val expectedName = entry.fileMetadata.fileName.ifBlank { entry.pad.fileName }.trim()
    val expectedSize = entry.fileMetadata.sizeBytes.takeIf { it > 0L } ?: entry.pad.sizeBytes
    val expectedSha1 = entry.fileMetadata.sha1Hash.trim().lowercase(Locale.US)

    if (candidates.isNotEmpty()) {
      // Prioritas 1: Pencocokan lewat path relatif terhadap folder musik
      if (expectedRel.isNotEmpty()) {
        val exactRel = candidates.firstOrNull { normalizeRelPath(it.relativePath) == expectedRel }
          ?: candidates.firstOrNull {
            normalizeRelPath(it.relativePath).equals(expectedRel, ignoreCase = true)
          }
        if (exactRel != null) {
          return PadRelinkOutcome(
            entry = entry,
            resolvedUriString = exactRel.uriString,
            matchType = PadRelinkMatchType.RELATIVE_PATH,
            resolvedRelativePath = exactRel.relativePath,
            resolvedSubfolderName = exactRel.subfolderName.ifBlank { entry.pad.subfolderName }
          )
        }
      }

      // Prioritas 2: Fallback ke nama file + ukuran file
      if (expectedName.isNotEmpty()) {
        val nameMatches = candidates.filter { it.fileName.equals(expectedName, ignoreCase = true) }
        val byNameAndSize = if (expectedSize > 0L) {
          nameMatches.firstOrNull { it.sizeBytes == expectedSize }
        } else {
          null
        }
        if (byNameAndSize != null) {
          return PadRelinkOutcome(
            entry = entry,
            resolvedUriString = byNameAndSize.uriString,
            matchType = PadRelinkMatchType.NAME_AND_SIZE,
            resolvedRelativePath = byNameAndSize.relativePath,
            resolvedSubfolderName = byNameAndSize.subfolderName.ifBlank { entry.pad.subfolderName }
          )
        }
      }

      // Prioritas 3: Fallback ke hash SHA-1 cepat (utamakan kandidat dengan ukuran yang sama)
      if (expectedSha1.isNotEmpty()) {
        val prioritizedCandidates = if (expectedSize > 0L) {
          candidates.filter { it.sizeBytes == expectedSize || it.sizeBytes <= 0L } +
            candidates.filter { it.sizeBytes > 0L && it.sizeBytes != expectedSize }
        } else {
          candidates
        }
        val byHash = prioritizedCandidates.firstOrNull { cand ->
          cand.quickSha1().lowercase(Locale.US) == expectedSha1
        }
        if (byHash != null) {
          return PadRelinkOutcome(
            entry = entry,
            resolvedUriString = byHash.uriString,
            matchType = PadRelinkMatchType.SHA1_HASH,
            resolvedRelativePath = byHash.relativePath,
            resolvedSubfolderName = byHash.subfolderName.ifBlank { entry.pad.subfolderName }
          )
        }
      }

      // Tambahan fallback aman jika expectedSize tidak terekam (0) dan hanya ada 1 file bernama sama persis
      if (expectedSize <= 0L && expectedName.isNotEmpty()) {
        val singleName = candidates.filter { it.fileName.equals(expectedName, ignoreCase = true) }
        if (singleName.size == 1) {
          val match = singleName.first()
          return PadRelinkOutcome(
            entry = entry,
            resolvedUriString = match.uriString,
            matchType = PadRelinkMatchType.NAME_AND_SIZE,
            resolvedRelativePath = match.relativePath,
            resolvedSubfolderName = match.subfolderName.ifBlank { entry.pad.subfolderName }
          )
        }
      }
    }

    // Jika folder musik tidak dipilih saat restore tetapi URI lama kebetulan masih bisa diakses
    if (rawUri.isNotBlank() && uriReadableChecker(rawUri)) {
      return PadRelinkOutcome(
        entry = entry,
        resolvedUriString = rawUri,
        matchType = PadRelinkMatchType.UNCHANGED_LOCAL,
        resolvedRelativePath = entry.fileMetadata.relativePath,
        resolvedSubfolderName = entry.pad.subfolderName
      )
    }

    return PadRelinkOutcome(
      entry = entry,
      resolvedUriString = rawUri,
      matchType = PadRelinkMatchType.MISSING,
      resolvedRelativePath = entry.fileMetadata.relativePath,
      resolvedSubfolderName = entry.pad.subfolderName
    )
  }

  /**
   * Dipanggil oleh `scanAndPersist` saat user memilih/memindai folder musik setelah restore,
   * agar pad yang sebelumnya berstatus "File hilang" otomatis tertaut kembali ke file baru
   * dengan tetap mempertahankan ID, slot (`positionIndex`), warna, tampilan, dan semua `PadSettings`.
   */
  fun matchScannedPadToMissingEntity(
    scannedPads: List<SoundPad>,
    missingEntity: SoundPadEntity
  ): SoundPad? {
    val exactUri = scannedPads.firstOrNull { it.uriString == missingEntity.uriString }
    if (exactUri != null) return exactUri

    val expectedSub = missingEntity.subfolderName.trim().lowercase(Locale.US)
    val expectedName = missingEntity.fileName.trim().lowercase(Locale.US)
    if (expectedName.isEmpty()) return null

    // 1. Subfolder + fileName cocok
    if (expectedSub.isNotEmpty()) {
      val bySubAndName = scannedPads.firstOrNull { pad ->
        pad.subfolderName.trim().lowercase(Locale.US) == expectedSub &&
          pad.fileName.trim().lowercase(Locale.US) == expectedName
      }
      if (bySubAndName != null) return bySubAndName
    }

    // 2. fileName + sizeBytes cocok
    if (missingEntity.sizeBytes > 0L) {
      val byNameAndSize = scannedPads.firstOrNull { pad ->
        pad.fileName.trim().lowercase(Locale.US) == expectedName &&
          pad.sizeBytes == missingEntity.sizeBytes
      }
      if (byNameAndSize != null) return byNameAndSize
    }

    // 3. Fallback fileName unik jika ukuran 0 atau tidak tersedia
    val sameName = scannedPads.filter { it.fileName.trim().lowercase(Locale.US) == expectedName }
    return if (sameName.size == 1) sameName.first() else null
  }

  private fun normalizeRelPath(path: String): String {
    return path.replace('\\', '/').trim().trim('/')
  }
}

object PersistentConfigRestoreEngine {

  const val MISSING_FILE_ERROR_PREFIX = "File hilang: "

  suspend fun inspectTreeUri(
    context: Context,
    treeUri: Uri,
    storage: SafPersistentConfigStorage = SafPersistentConfigStorage(context)
  ): Result<BackupInspectionSummary> = withContext(Dispatchers.IO) {
    val backend = storage.openBackend(treeUri)
    val folderName = runCatching { backend.resolveDisplayName() }.getOrDefault("Backup Config")
    inspectBackend(
      backend = backend,
      sourceLabel = folderName,
      sourceTreeUriString = treeUri.toString()
    )
  }

  suspend fun inspectSnapshotFromTree(
    context: Context,
    backupTreeUri: Uri,
    snapshot: SnapshotFileInfo,
    storage: SafPersistentConfigStorage = SafPersistentConfigStorage(context)
  ): Result<BackupInspectionSummary> = withContext(Dispatchers.IO) {
    val backend = storage.openBackend(backupTreeUri)
    inspectSingleSnapshotOnBackend(backend, snapshot)
  }

  fun inspectSingleSnapshotOnBackend(
    backend: ConfigTreeStorageBackend,
    snapshot: SnapshotFileInfo
  ): Result<BackupInspectionSummary> = runCatching {
    val raw = backend.readFileText("${PersistentConfigSchema.DIR_BACKUPS}/${snapshot.fileName}")
      ?: error("Snapshot '${snapshot.fileName}' tidak dapat dibaca")
    val result = PersistentConfigBundleSerializer.importCombinedJson(raw)
    val allSnaps = listAllSnapshotsOnBackend(backend)
    buildSummary(
      restoreResult = result,
      sourceLabel = snapshot.fileName,
      sourceKind = BackupSourceKind.SNAPSHOT_FILE,
      usedFallbackSnapshotName = null,
      availableSnapshots = allSnaps
    )
  }

  fun inspectBackend(
    backend: ConfigTreeStorageBackend,
    sourceLabel: String,
    sourceTreeUriString: String? = null
  ): Result<BackupInspectionSummary> = runCatching {
    val readCheck = backend.checkReadAccess()
    if (!readCheck.isAccessible) {
      error(readCheck.reason ?: "Folder tidak dapat dibaca")
    }

    val directFiles = collectConfigFilesFromBackend(backend, prefix = "")
    val musicSubFiles = if (directFiles.isEmpty()) {
      collectConfigFilesFromBackend(backend, prefix = PersistentConfigSchema.MUSIC_SUBFOLDER_NAME)
    } else {
      emptyMap()
    }

    val allSnapshots = listAllSnapshotsOnBackend(backend)

    val (activeFiles, detectedKind) = when {
      directFiles.isNotEmpty() -> directFiles to BackupSourceKind.SAF_FOLDER
      musicSubFiles.isNotEmpty() -> musicSubFiles to BackupSourceKind.MUSIC_SUBFOLDER
      else -> emptyMap<String, String>() to BackupSourceKind.SAF_FOLDER
    }

    if (activeFiles.isNotEmpty()) {
      val primaryResult = parseFolderFilesOrCombined(activeFiles)
      if (primaryResult != null && !isRestoreCriticallyBroken(primaryResult, activeFiles)) {
        val effectiveSource = if (detectedKind == BackupSourceKind.MUSIC_SUBFOLDER) {
          "$sourceLabel/${PersistentConfigSchema.MUSIC_SUBFOLDER_NAME}"
        } else {
          sourceLabel
        }
        return@runCatching buildSummary(
          restoreResult = primaryResult,
          sourceLabel = effectiveSource,
          sourceKind = detectedKind,
          usedFallbackSnapshotName = null,
          detectedMusicFolderUri = if (detectedKind == BackupSourceKind.MUSIC_SUBFOLDER) sourceTreeUriString else null,
          availableSnapshots = allSnapshots
        )
      }
    }

    // Jika JSON utama tidak ditemukan atau rusak/korup, coba snapshot di backups/ dari yang terbaru ke terlama
    val fallbackResult = tryLoadHealthyFallbackSnapshot(backend, allSnapshots)
    if (fallbackResult != null) {
      val (snapInfo, parsed) = fallbackResult
      val warn = listOf("Config utama rusak atau tidak lengkap — memakai snapshot cadangan '${snapInfo.fileName}'.")
      return@runCatching buildSummary(
        restoreResult = parsed.copy(warnings = warn + parsed.warnings),
        sourceLabel = "$sourceLabel (${snapInfo.fileName})",
        sourceKind = BackupSourceKind.SNAPSHOT_FILE,
        usedFallbackSnapshotName = snapInfo.fileName,
        availableSnapshots = allSnapshots
      )
    }

    error("Tidak ditemukan config SoundPad Pro yang valid di folder ini (periksa config_meta.json, .soundpadpro/, atau backups/).")
  }

  fun inspectFolderFilesMap(
    files: Map<String, String>,
    sourceLabel: String,
    sourceKind: BackupSourceKind
  ): Result<BackupInspectionSummary> = runCatching {
    val normalized = normalizeArchiveFileKeys(files)
    val parsed = parseFolderFilesOrCombined(normalized)
      ?: error("Arsip backup tidak berisi config SoundPad Pro yang valid.")
    if (isRestoreCriticallyBroken(parsed, normalized)) {
      error("File config di dalam arsip rusak atau korup.")
    }
    buildSummary(
      restoreResult = parsed,
      sourceLabel = sourceLabel,
      sourceKind = sourceKind,
      usedFallbackSnapshotName = null
    )
  }

  fun listAllSnapshotsOnBackend(backend: ConfigTreeStorageBackend): List<SnapshotFileInfo> {
    return backend.listFiles(PersistentConfigSchema.DIR_BACKUPS)
      .filter { it.fileName.endsWith(".json", ignoreCase = true) }
      .sortedWith(
        compareByDescending<SnapshotFileInfo> { it.fileName }
          .thenByDescending { it.lastModifiedMs }
      )
  }

  private fun collectConfigFilesFromBackend(
    backend: ConfigTreeStorageBackend,
    prefix: String
  ): Map<String, String> {
    val p = if (prefix.isBlank()) "" else "$prefix/"
    val files = linkedMapOf<String, String>()

    val singleCandidates = listOf(
      PersistentConfigSchema.FILE_CONFIG_META,
      PersistentConfigSchema.FILE_SETTINGS,
      PersistentConfigSchema.FILE_MIDI_MAPPING,
      PersistentConfigSchema.FILE_SCHEDULER,
      PersistentConfigSchema.FILE_SEQUENCER,
      PersistentConfigSchema.FILE_COMBINED_CONFIG,
      PersistentConfigSchema.FILE_PRESETS_EQ,
      PersistentConfigSchema.FILE_PRESETS_FX,
      PersistentConfigSchema.FILE_PRESETS_CURVES,
      PersistentConfigSchema.FILE_PRESETS_COMBINED
    )
    singleCandidates.forEach { rel ->
      val content = backend.readFileText("$p$rel")
      if (!content.isNullOrBlank()) {
        files[rel] = content
      }
    }

    // Jika mirror musik memakai mapping/midi_mapping.json
    val altMidi = backend.readFileText("${p}mapping/${PersistentConfigSchema.FILE_MIDI_MAPPING}")
    if (!altMidi.isNullOrBlank() && PersistentConfigSchema.FILE_MIDI_MAPPING !in files) {
      files[PersistentConfigSchema.FILE_MIDI_MAPPING] = altMidi
    }

    listOf(
      PersistentConfigSchema.DIR_PROFILES,
      PersistentConfigSchema.DIR_LAYOUTS,
      "extensions"
    ).forEach { dir ->
      backend.listFiles("$p$dir")
        .filter { it.fileName.endsWith(".json", ignoreCase = true) }
        .forEach { item ->
          val content = backend.readFileText("$p$dir/${item.fileName}")
          if (!content.isNullOrBlank()) {
            files["$dir/${item.fileName}"] = content
          }
        }
    }
    return files
  }

  private fun normalizeArchiveFileKeys(raw: Map<String, String>): Map<String, String> {
    val cleaned = raw.entries.associate { (k, v) ->
      k.replace('\\', '/').trim().trimStart('/') to v
    }
    // Cek bila ZIP/folder dibungkus 1 top-level root seperti "SoundPadPro/..."
    val hasDirectKnown = cleaned.containsKey(PersistentConfigSchema.FILE_CONFIG_META) ||
      cleaned.containsKey(PersistentConfigSchema.FILE_SETTINGS) ||
      cleaned.containsKey(PersistentConfigSchema.FILE_COMBINED_CONFIG) ||
      cleaned.keys.any { it.startsWith("${PersistentConfigSchema.DIR_PROFILES}/") }
    if (hasDirectKnown) {
      return remapAltMidiPath(cleaned)
    }

    val firstSegments = cleaned.keys.mapNotNull { key ->
      key.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    }.distinct()
    if (firstSegments.size == 1) {
      val root = "${firstSegments.first()}/"
      return remapAltMidiPath(cleaned.mapKeys { (k, _) -> k.removePrefix(root) })
    }
    return remapAltMidiPath(cleaned)
  }

  private fun remapAltMidiPath(files: Map<String, String>): Map<String, String> {
    val altMidiKey = "mapping/${PersistentConfigSchema.FILE_MIDI_MAPPING}"
    if (files.containsKey(altMidiKey) && !files.containsKey(PersistentConfigSchema.FILE_MIDI_MAPPING)) {
      return files + (PersistentConfigSchema.FILE_MIDI_MAPPING to files.getValue(altMidiKey))
    }
    return files
  }

  private fun parseFolderFilesOrCombined(files: Map<String, String>): PersistentConfigRestoreResult? {
    val hasSplitFiles = files.keys.any {
      it == PersistentConfigSchema.FILE_CONFIG_META ||
        it == PersistentConfigSchema.FILE_SETTINGS ||
        it.startsWith("${PersistentConfigSchema.DIR_PROFILES}/")
    }
    if (hasSplitFiles) {
      val res = PersistentConfigBundleSerializer.importFromFolderFiles(files)
      if (res.restoredSectionKeys.isNotEmpty() && !isRestoreCriticallyBroken(res, files)) {
        return res
      }
    }
    val combinedRaw = files[PersistentConfigSchema.FILE_COMBINED_CONFIG] ?: return null
    return runCatching { PersistentConfigBundleSerializer.importCombinedJson(combinedRaw) }.getOrNull()
  }

  private fun isRestoreCriticallyBroken(
    res: PersistentConfigRestoreResult,
    sourceFiles: Map<String, String>
  ): Boolean {
    val hadMetaFile = sourceFiles.containsKey(PersistentConfigSchema.FILE_CONFIG_META)
    val metaCorrupt = hadMetaFile && "meta" !in res.restoredSectionKeys
    val hadSettingsFile = sourceFiles.containsKey(PersistentConfigSchema.FILE_SETTINGS)
    val settingsCorrupt = hadSettingsFile && "settings" !in res.restoredSectionKeys
    val profileFilesCount = sourceFiles.keys.count {
      it.startsWith("${PersistentConfigSchema.DIR_PROFILES}/") && it.endsWith(".json")
    }
    val allProfilesFailed = profileFilesCount > 0 && res.bundle.profiles.isEmpty()
    val nothingRestored = res.bundle.profiles.isEmpty() && "settings" !in res.restoredSectionKeys
    return metaCorrupt || settingsCorrupt || allProfilesFailed || nothingRestored
  }

  private fun tryLoadHealthyFallbackSnapshot(
    backend: ConfigTreeStorageBackend,
    snapshots: List<SnapshotFileInfo>
  ): Pair<SnapshotFileInfo, PersistentConfigRestoreResult>? {
    for (snap in snapshots) {
      val raw = backend.readFileText("${PersistentConfigSchema.DIR_BACKUPS}/${snap.fileName}") ?: continue
      val parsed = runCatching { PersistentConfigBundleSerializer.importCombinedJson(raw) }.getOrNull() ?: continue
      if (parsed.bundle.profiles.isNotEmpty() || "settings" in parsed.restoredSectionKeys) {
        return snap to parsed
      }
    }
    return null
  }

  private fun buildSummary(
    restoreResult: PersistentConfigRestoreResult,
    sourceLabel: String,
    sourceKind: BackupSourceKind,
    usedFallbackSnapshotName: String?,
    detectedMusicFolderUri: String? = null,
    availableSnapshots: List<SnapshotFileInfo> = emptyList()
  ): BackupInspectionSummary {
    val bundle = restoreResult.bundle
    val derivedBanks = bundle.profiles.sumOf { p ->
      maxOf(p.banks.size, p.pads.map { it.pad.bankIndex }.distinct().size)
    }
    val derivedPads = bundle.profiles.sumOf { it.pads.size }
    val derivedLayouts = maxOf(bundle.meta.layoutCount, bundle.layouts.size)
    val previews = bundle.profiles.map { pb ->
      val bankCount = maxOf(pb.banks.size, pb.pads.map { it.pad.bankIndex }.distinct().size)
      ProfilePreviewItem(
        id = pb.profile.id,
        name = pb.profile.name,
        padCount = pb.pads.size,
        bankCount = bankCount
      )
    }
    val effectiveDate = bundle.meta.lastSyncedAtMs
      .takeIf { it > 0L }
      ?: bundle.meta.updatedAtMs.takeIf { it > 0L }
      ?: System.currentTimeMillis()

    return BackupInspectionSummary(
      sourceLabel = sourceLabel,
      sourceKind = sourceKind,
      schemaVersion = bundle.meta.schemaVersion,
      migratedFromVersion = restoreResult.migratedFromVersion,
      backupDateMs = effectiveDate,
      appVersionName = bundle.meta.appVersionName,
      profileCount = bundle.profiles.size.coerceAtLeast(bundle.meta.profileCount.takeIf { bundle.profiles.isEmpty() } ?: 0),
      bankCount = derivedBanks.coerceAtLeast(bundle.meta.bankCount.takeIf { derivedBanks == 0 } ?: 0),
      padCount = derivedPads.coerceAtLeast(bundle.meta.padCount.takeIf { derivedPads == 0 } ?: 0),
      layoutCount = derivedLayouts,
      schedulerCount = bundle.scheduler.tasks.size,
      sequencerCount = bundle.sequencer.playlists.size,
      includesWaveforms = bundle.meta.includesWaveforms ||
        bundle.profiles.any { p -> p.pads.any { it.pad.miniWaveform.isNotEmpty() } },
      usedFallbackSnapshotName = usedFallbackSnapshotName,
      detectedMusicFolderUri = detectedMusicFolderUri,
      warnings = restoreResult.warnings,
      profilesPreview = previews,
      availableSnapshots = availableSnapshots,
      bundle = bundle
    )
  }

  /**
   * Menerapkan config hasil restore ke Room DB & GlobalSettingsRepository secara aman
   * dengan progress update bertahap dan pencocokan ulang file audio (SAF / lokal).
   */
  suspend fun applyRestore(
    context: Context,
    dao: SoundPadDao,
    settingsRepo: GlobalSettingsRepository,
    inspection: BackupInspectionSummary,
    options: RestoreSelectionOptions = RestoreSelectionOptions(),
    musicFolderUri: Uri? = null,
    overrideCandidates: List<IndexedAudioCandidate>? = null,
    rescheduleAlarms: Boolean = true,
    onProgress: suspend (progress: Float, stageLabel: String) -> Unit = { _, _ -> }
  ): RestoreExecutionOutcome = withContext(Dispatchers.IO) {
    runCatching {
      val bundle = inspection.bundle
      val warnings = inspection.warnings.toMutableList()

      onProgress(0.10f, "Menyiapkan indeks file audio…")
      val effectiveMusicUri = musicFolderUri
        ?: inspection.detectedMusicFolderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
      val candidates = overrideCandidates ?: if (effectiveMusicUri != null) {
        AudioFileRelinker.indexSafMusicFolder(context, effectiveMusicUri)
      } else {
        emptyList()
      }

      val uriReadableChecker: (String) -> Boolean = { uriStr ->
        runCatching {
          val u = Uri.parse(uriStr)
          context.contentResolver.openInputStream(u)?.use { true } ?: false
        }.getOrDefault(false)
      }

      var restoredProfileCount = 0
      var restoredPadCount = 0
      var relinkedPadCount = 0
      var missingFilePadCount = 0
      val missingNames = mutableListOf<String>()
      val missingErrorLogs = mutableListOf<ErrorLogEntity>()

      val targetProfiles = if (!options.restoreProfilesAndPads) {
        emptyList()
      } else if (options.selectedProfileIds.isEmpty()) {
        bundle.profiles
      } else {
        bundle.profiles.filter { it.profile.id in options.selectedProfileIds }
      }

      // Peta layout per profil untuk memastikan slotIndex/colorArgb/displayName konsisten saat restore
      val defaultLayoutByProfile = bundle.layouts
        .groupBy { it.profileId }
        .mapValues { (_, list) -> list.firstOrNull { it.isDefault } ?: list.firstOrNull() }

      if (options.restoreProfilesAndPads && targetProfiles.isNotEmpty()) {
        val totalProfiles = targetProfiles.size
        targetProfiles.forEachIndexed { idx, pBundle ->
          val progressFraction = 0.20f + (0.50f * (idx.toFloat() / totalProfiles.coerceAtLeast(1)))
          onProgress(progressFraction, "Memulihkan profil \"${pBundle.profile.name}\"…")

          val layoutSlotsByPadId = defaultLayoutByProfile[pBundle.profile.id]
            ?.slots
            ?.associateBy { it.padId }
            .orEmpty()

          val profileFolderUri = when {
            effectiveMusicUri != null -> effectiveMusicUri.toString()
            else -> pBundle.profile.folderUri
          }

          val isActiveProfile = if (options.selectedProfileIds.isEmpty()) {
            pBundle.profile.id == bundle.settings.settings.activeProfileId || pBundle.profile.isActive
          } else {
            idx == 0
          }

          val profileEntity = pBundle.profile.copy(
            folderUri = profileFolderUri,
            isActive = isActiveProfile,
            updatedAtMs = System.currentTimeMillis()
          ).toProfileEntity()

          val usedPadIds = mutableSetOf<String>()
          val padEntities = mutableListOf<SoundPadEntity>()
          val padSettingEntities = mutableListOf<PadSettingEntity>()

          pBundle.pads.forEachIndexed { padIdx, entry ->
            val relinked = AudioFileRelinker.relinkPadEntry(
              entry = entry,
              candidates = candidates,
              uriReadableChecker = uriReadableChecker
            )
            val layoutOverride = layoutSlotsByPadId[entry.pad.id]
            val finalSlotIndex = (layoutOverride?.slotIndex ?: entry.slotIndex).coerceAtLeast(0)
            val finalBankIndex = (layoutOverride?.bankIndex ?: entry.pad.bankIndex).coerceAtLeast(0)
            val finalColor = layoutOverride?.colorArgb ?: entry.pad.colorArgb
            val finalDisplayName = layoutOverride?.displayName?.takeIf { it.isNotBlank() }
              ?: entry.pad.displayName

            // Pertahankan ID asli jika unik agar relasi Scheduler/Sequencer/Layout tetap utuh
            val finalPadId = if (entry.pad.id.isNotBlank() && usedPadIds.add(entry.pad.id)) {
              entry.pad.id
            } else {
              val gen = "pad_${pBundle.profile.id}_${padIdx}_${UUID.randomUUID().toString().take(6)}"
              usedPadIds.add(gen)
              gen
            }

            val isMissing = relinked.matchType == PadRelinkMatchType.MISSING
            if (isMissing) {
              missingFilePadCount++
              missingNames.add(entry.pad.fileName)
              missingErrorLogs.add(
                ErrorLogEntity(
                  id = "restore_missing_${finalPadId}_${UUID.randomUUID().toString().take(6)}",
                  timestampMs = System.currentTimeMillis(),
                  fileName = entry.pad.fileName,
                  uriString = entry.pad.uriString,
                  errorType = PadErrorType.MISSING_PERMISSION.name,
                  message = "${MISSING_FILE_ERROR_PREFIX}${entry.pad.fileName}",
                  suggestion = "Pilih ulang folder musik Anda untuk menautkan otomatis file yang hilang. Posisi slot pad tetap dipertahankan.",
                  padId = finalPadId
                )
              )
            } else if (relinked.matchType != PadRelinkMatchType.UNCHANGED_LOCAL) {
              relinkedPadCount++
            }

            val finalPad = entry.pad.copy(
              id = finalPadId,
              uriString = relinked.resolvedUriString,
              displayName = finalDisplayName,
              positionIndex = finalSlotIndex,
              bankIndex = finalBankIndex,
              subfolderName = relinked.resolvedSubfolderName,
              colorArgb = finalColor,
              errorType = if (isMissing) PadErrorType.MISSING_PERMISSION else null,
              errorMessage = if (isMissing) "${MISSING_FILE_ERROR_PREFIX}${entry.pad.fileName}" else null
            )

            padEntities.add(finalPad.toBackupPadEntity(pBundle.profile.id))
            padSettingEntities.add(finalPad.settings.toBackupSettingEntity(finalPadId))
            restoredPadCount++
          }

          dao.saveCompleteProfile(
            profile = profileEntity,
            pads = padEntities,
            settings = padSettingEntities
          )
          restoredProfileCount++

          if (options.restoreMidiMapping) {
            dao.deleteMidiCcMappingsForProfile(pBundle.profile.id)
            if (pBundle.ccMappings.isNotEmpty()) {
              dao.upsertMidiCcMappings(
                pBundle.ccMappings.map { m ->
                  MidiCcMappingEntity(
                    id = m.id.ifBlank { "cc_${pBundle.profile.id}_${m.target.name}" },
                    profileId = pBundle.profile.id,
                    target = m.target.name,
                    ccNumber = m.ccNumber,
                    channel = m.channel
                  )
                }
              )
            }
            dao.deleteMidiPcMappingsForProfile(pBundle.profile.id)
            pBundle.pcMappings.forEach { pc ->
              dao.upsertMidiPcMapping(
                MidiPcBankMappingEntity(
                  id = pc.id.ifBlank { "pc_${pBundle.profile.id}_${pc.bankIndex}" },
                  profileId = pBundle.profile.id,
                  programNumber = pc.programNumber,
                  bankIndex = pc.bankIndex,
                  channel = pc.channel
                )
              )
            }
            val isActiveTarget = pBundle.profile.id == bundle.settings.settings.activeProfileId ||
              pBundle.profile.isActive
            if (pBundle.profile.focusModeMidiNote == null && isActiveTarget && bundle.midiMapping.focusModeMidiNote != null) {
              dao.updateProfileFocusModeMidiNote(
                pBundle.profile.id,
                bundle.midiMapping.focusModeMidiNote,
                System.currentTimeMillis()
              )
            }
          }
        }

        // Pastikan profil aktif menunjuk profil yang valid
        val activeTargetId = targetProfiles.firstOrNull {
          it.profile.id == bundle.settings.settings.activeProfileId || it.profile.isActive
        }?.profile?.id ?: targetProfiles.first().profile.id
        dao.setActiveProfile(activeTargetId)
        settingsRepo.setActiveProfileId(activeTargetId)
      }

      if (options.restoreMidiMapping && !options.restoreProfilesAndPads) {
        onProgress(0.75f, "Memulihkan pemetaan MIDI…")
        val existingProfiles = dao.getAllProfilesOnce().map { it.id }.toSet()
        bundle.midiMapping.ccMappings.filter { it.profileId in existingProfiles }.let { validCc ->
          if (validCc.isNotEmpty()) {
            dao.upsertMidiCcMappings(
              validCc.map { m ->
                MidiCcMappingEntity(
                  id = m.id,
                  profileId = m.profileId,
                  target = m.target.name,
                  ccNumber = m.ccNumber,
                  channel = m.channel
                )
              }
            )
          }
        }
        bundle.midiMapping.pcMappings.filter { it.profileId in existingProfiles }.forEach { pc ->
          dao.upsertMidiPcMapping(
            MidiPcBankMappingEntity(
              id = pc.id,
              profileId = pc.profileId,
              programNumber = pc.programNumber,
              bankIndex = pc.bankIndex,
              channel = pc.channel
            )
          )
        }
        val activeId = settingsRepo.settingsFlow.first().activeProfileId
        dao.updateProfileFocusModeMidiNote(
          activeId,
          bundle.midiMapping.focusModeMidiNote,
          System.currentTimeMillis()
        )
        settingsRepo.setFocusModeMidiNote(bundle.midiMapping.focusModeMidiNote)
      } else if (options.restoreMidiMapping) {
        val activeProfileNote = targetProfiles.firstOrNull {
          it.profile.id == bundle.settings.settings.activeProfileId || it.profile.isActive
        }?.profile?.focusModeMidiNote ?: bundle.midiMapping.focusModeMidiNote
        settingsRepo.setFocusModeMidiNote(activeProfileNote)
      }

      if (options.restoreSchedulerAndSequencer) {
        onProgress(0.84f, "Memulihkan Scheduler & Sequencer…")
        dao.deleteAllScheduledTasks()
        bundle.scheduler.tasks.forEach { task ->
          dao.upsertScheduledTaskAssigningRequestCode(task.toEntity())
        }
        dao.deleteAllSequencerPlaylists()
        bundle.sequencer.playlists.forEach { pl ->
          dao.upsertSequencerPlaylist(pl.toEntity())
        }
        if (rescheduleAlarms) {
          runCatching { PadSchedulerManager.rescheduleAllActive(context) }
        }
      }

      if (options.restoreSettings) {
        onProgress(0.92f, "Menerapkan pengaturan global…")
        val restoredGs = bundle.settings.settings
        val activeFallbackId = if (options.restoreProfilesAndPads && targetProfiles.isNotEmpty()) {
          targetProfiles.firstOrNull {
            it.profile.id == restoredGs.activeProfileId || it.profile.isActive
          }?.profile?.id ?: targetProfiles.first().profile.id
        } else {
          restoredGs.activeProfileId
        }
        val finalGs = restoredGs.copy(
          activeProfileId = activeFallbackId,
          folderUri = effectiveMusicUri?.toString() ?: restoredGs.folderUri,
          folderName = if (effectiveMusicUri != null) {
            SafAudioScanner(context).resolveFolderDisplayName(effectiveMusicUri)
          } else {
            restoredGs.folderName
          },
          hasCompletedOnboarding = true
        )
        settingsRepo.applyRestoredGlobalSettings(
          gs = finalGs,
          restoreSyncFolderConfig = false
        )
      } else if (effectiveMusicUri != null) {
        val folderName = SafAudioScanner(context).resolveFolderDisplayName(effectiveMusicUri)
        settingsRepo.setSelectedFolder(effectiveMusicUri.toString(), folderName)
      }

      if (missingErrorLogs.isNotEmpty()) {
        dao.insertErrorLogs(missingErrorLogs)
      }

      onProgress(1.0f, "Pemulihan selesai")
      RestoreExecutionOutcome(
        success = true,
        restoredProfileCount = restoredProfileCount,
        restoredPadCount = restoredPadCount,
        relinkedPadCount = relinkedPadCount,
        missingFilePadCount = missingFilePadCount,
        missingFileNames = missingNames,
        usedFallbackSnapshotName = inspection.usedFallbackSnapshotName,
        warnings = warnings
      )
    }.getOrElse { err ->
      RestoreExecutionOutcome(
        success = false,
        errorMessage = err.localizedMessage ?: "Gagal memulihkan config"
      )
    }
  }

  private fun PadProfile.toProfileEntity(): PadProfileEntity = PadProfileEntity(
    id = id,
    name = name,
    folderUri = folderUri,
    gridPreset = gridConfig.preset.name,
    gridRows = gridConfig.rows,
    gridCols = gridConfig.cols,
    bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(bankNames),
    bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(bankOrder),
    bankFolderUrisJson = ProfileJsonSerializer.encodeBankFolderUrisString(bankFolderUris),
    masterEqEnabled = masterEqEnabled,
    masterEqPreset = masterEqPreset.name,
    masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(masterEqBandsDb),
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
    isActive = isActive,
    focusModeMidiNote = focusModeMidiNote
  )

  private fun SoundPad.toBackupPadEntity(profileId: String): SoundPadEntity = SoundPadEntity(
    id = id,
    profileId = profileId,
    uriString = uriString,
    fileName = fileName,
    displayName = displayName,
    extension = extension,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    positionIndex = positionIndex,
    bankIndex = bankIndex,
    subfolderName = subfolderName,
    colorArgb = colorArgb,
    glowColorArgb = glowColorArgb,
    glowIntensity = glowIntensity,
    shapeStyle = shapeStyle?.name,
    borderColorArgb = borderColorArgb,
    borderWidthDp = borderWidthDp,
    cornerRadiusDp = cornerRadiusDp,
    textColorArgb = textColorArgb,
    surfaceFinish = surfaceFinish.name,
    badgeIcon = badgeIcon.name,
    showSlotNumber = showSlotNumber,
    showDurationLabel = showDurationLabel,
    isClone = isClone,
    midiNote = midiNote,
    isFavorite = isFavorite,
    categoryTag = categoryTag,
    miniWaveformJson = ProfileJsonSerializer.encodeMiniWaveform(miniWaveform),
    errorType = errorType?.name,
    errorMessage = errorMessage
  )

  private fun PadSettings.toBackupSettingEntity(padId: String): PadSettingEntity = PadSettingEntity(
    padId = padId,
    volume = volume,
    speed = speed,
    pitch = pitch,
    pan = pan,
    isLooping = isLooping,
    triggerMode = triggerMode.name,
    chokeGroup = chokeGroup,
    fadeInMs = fadeInMs,
    fadeOutMs = fadeOutMs,
    fadeCurve = fadeCurve.name,
    crossfadeEnabled = crossfadeEnabled,
    trimStartMs = trimStartMs,
    trimEndMs = trimEndMs,
    isDuckingSource = isDuckingSource,
    normalizedGain = normalizedGain,
    eqEnabled = eqEnabled,
    eqPreset = eqPreset.name,
    eqBandsJson = ProfileJsonSerializer.encodeEqBandsString(eqBandsDb),
    isReversed = isReversed,
    fxPreset = fxPreset.name,
    echoEnabled = echoEnabled,
    echoDelayMs = echoDelayMs,
    echoFeedback = echoFeedback,
    echoMix = echoMix,
    reverbEnabled = reverbEnabled,
    reverbRoomSize = reverbRoomSize,
    reverbDamping = reverbDamping,
    reverbMix = reverbMix
  )
}
