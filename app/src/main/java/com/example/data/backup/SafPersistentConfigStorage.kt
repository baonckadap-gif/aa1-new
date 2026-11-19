package com.example.data.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class SnapshotTrigger(val slug: String, val label: String) {
  DAILY("daily", "Harian"),
  PRE_UPDATE("pre_update", "Sebelum Update App"),
  APP_EXIT("app_exit", "Keluar Aplikasi"),
  MANUAL("manual", "Manual")
}

data class PersistentFolderAccessCheck(
  val isAccessible: Boolean,
  val hasReadPermission: Boolean = false,
  val hasWritePermission: Boolean = false,
  val folderExists: Boolean = false,
  val reason: String? = null
)

data class SnapshotFileInfo(
  val fileName: String,
  val uriString: String,
  val sizeBytes: Long,
  val lastModifiedMs: Long,
  val trigger: String
)

data class PersistentBackupWriteResult(
  val success: Boolean,
  val primaryFilesWritten: Int = 0,
  val musicMirrorWritten: Boolean = false,
  val snapshotWritten: SnapshotFileInfo? = null,
  val rotatedDeletedCount: Int = 0,
  val totalSnapshotsRetained: Int = 0,
  val syncedAtMs: Long = System.currentTimeMillis(),
  val warnings: List<String> = emptyList(),
  val errorMessage: String? = null,
  val isAccessError: Boolean = false
)

/**
 * Abstraksi tree storage agar penulisan atomik, pembersihan file lama, dan rotasi snapshot
 * dapat dieksekusi baik di SAF [DocumentFile] maupun direktori lokal (untuk unit/Robolectric test).
 */
interface ConfigTreeStorageBackend {
  fun checkAccess(): PersistentFolderAccessCheck
  fun checkReadAccess(): PersistentFolderAccessCheck = checkAccess()
  fun resolveDisplayName(): String
  fun writeFileAtomic(relativePath: String, content: String): Result<Unit>
  fun readFileText(relativePath: String): String?
  fun listFiles(relativeDir: String): List<SnapshotFileInfo>
  fun deleteFile(relativePath: String): Boolean
}

class SafConfigTreeStorageBackend(
  private val context: Context,
  private val treeUri: Uri
) : ConfigTreeStorageBackend {

  private val dirCache = mutableMapOf<String, DocumentFile>()

  override fun checkAccess(): PersistentFolderAccessCheck {
    val persisted = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == treeUri }
    val hasRead = persisted?.isReadPermission == true
    val hasWrite = persisted?.isWritePermission == true
    if (persisted == null || !hasRead || !hasWrite) {
      return PersistentFolderAccessCheck(
        isAccessible = false,
        hasReadPermission = hasRead,
        hasWritePermission = hasWrite,
        folderExists = false,
        reason = "Izin akses tulis SAF untuk folder backup tidak ditemukan atau sudah dicabut."
      )
    }

    val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
    val exists = root != null && root.exists() && root.isDirectory
    val canWrite = exists && root?.canWrite() == true
    return PersistentFolderAccessCheck(
      isAccessible = exists && canWrite,
      hasReadPermission = hasRead,
      hasWritePermission = hasWrite && canWrite,
      folderExists = exists,
      reason = when {
        !exists -> "Folder backup tidak ditemukan atau telah dihapus/dipindahkan."
        !canWrite -> "Folder backup tidak memiliki izin tulis."
        else -> null
      }
    )
  }

  override fun checkReadAccess(): PersistentFolderAccessCheck {
    val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
    val exists = root != null && root.exists() && root.isDirectory
    val canRead = exists && root?.canRead() == true
    return PersistentFolderAccessCheck(
      isAccessible = canRead,
      hasReadPermission = canRead,
      hasWritePermission = exists && root?.canWrite() == true,
      folderExists = exists,
      reason = if (canRead) null else "Folder tidak dapat dibaca atau tidak ditemukan."
    )
  }

  override fun resolveDisplayName(): String {
    return runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?: treeUri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
      ?: "SoundPadPro"
  }

  override fun writeFileAtomic(relativePath: String, content: String): Result<Unit> = runCatching {
    val cleanRel = relativePath.trim().trimStart('/')
    val parts = cleanRel.split('/').filter { it.isNotBlank() }
    require(parts.isNotEmpty()) { "Invalid relative path: $relativePath" }

    val fileName = parts.last()
    val parentParts = parts.dropLast(1)
    val parentDir = ensureDirectoryChain(parentParts)
      ?: error("Gagal membuka/membuat direktori untuk '$cleanRel'")

    // Bersihkan sisa temp korup bila pernah crash di tulis sebelumnya
    cleanupStaleTempFiles(parentDir, fileName)

    val tempName = ".${fileName}.tmp.${UUID.randomUUID().toString().take(6)}"
    val mimeType = if (fileName.endsWith(".json", ignoreCase = true)) "application/json" else "application/octet-stream"
    val tempDoc = parentDir.createFile(mimeType, tempName)
      ?: parentDir.createFile("application/octet-stream", tempName)
      ?: error("Gagal membuat file sementara '$tempName'")

    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    var writeCompleted = false
    try {
      val outStream = context.contentResolver.openOutputStream(tempDoc.uri, "wt")
        ?: context.contentResolver.openOutputStream(tempDoc.uri)
        ?: error("Tidak bisa membuka stream tulis untuk '$tempName'")
      outStream.use { stream ->
        stream.write(bytes)
        stream.flush()
      }
      if (bytes.isNotEmpty()) {
        val writtenLen = runCatching { tempDoc.length() }.getOrDefault(bytes.size.toLong())
        if (writtenLen == 0L) {
          error("Verifikasi ukuran tulis file sementara bernilai 0 byte")
        }
      }
      writeCompleted = true
    } finally {
      if (!writeCompleted) {
        runCatching { tempDoc.delete() }
      }
    }

    // Hanya hapus target lama setelah temp write benar-benar sukses & terverifikasi
    val existing = findExactChildFile(parentDir, fileName)
    if (existing != null) {
      val directRenameOk = runCatching {
        DocumentsContract.renameDocument(context.contentResolver, tempDoc.uri, fileName) != null
      }.getOrDefault(false)
      if (!directRenameOk) {
        val existingDeleted = runCatching { existing.delete() }.getOrDefault(false)
        if (!existingDeleted) {
          // Provider tidak izinkan rename langsung menimpa: timpa target langsung setelah temp sukses diverifikasi
          val overwriteStream = context.contentResolver.openOutputStream(existing.uri, "wt")
            ?: error("Gagal menimpa file target '$fileName'")
          overwriteStream.use { stream ->
            stream.write(bytes)
            stream.flush()
          }
          runCatching { tempDoc.delete() }
          return@runCatching
        }
        val renamed = renameSafDocument(tempDoc, fileName)
        if (!renamed) {
          fallbackCopyAndDelete(parentDir, mimeType, fileName, tempDoc, bytes)
        }
      }
    } else {
      val renamed = renameSafDocument(tempDoc, fileName)
      if (!renamed) {
        fallbackCopyAndDelete(parentDir, mimeType, fileName, tempDoc, bytes)
      }
    }
  }

  override fun readFileText(relativePath: String): String? {
    val cleanRel = relativePath.trim().trimStart('/')
    val parts = cleanRel.split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val dir = findDirectoryChain(parts.dropLast(1)) ?: return null
    val doc = findExactChildFile(dir, parts.last()) ?: return null
    return runCatching {
      context.contentResolver.openInputStream(doc.uri)?.use { input ->
        input.readBytes().toString(StandardCharsets.UTF_8)
      }
    }.getOrNull()
  }

  override fun listFiles(relativeDir: String): List<SnapshotFileInfo> {
    val parts = relativeDir.trim().trim('/').split('/').filter { it.isNotBlank() }
    val dir = findDirectoryChain(parts) ?: return emptyList()
    return runCatching {
      dir.listFiles().mapNotNull { child ->
        val name = child.name ?: return@mapNotNull null
        if (!child.isFile || name.startsWith(".")) return@mapNotNull null
        SnapshotFileInfo(
          fileName = name,
          uriString = child.uri.toString(),
          sizeBytes = runCatching { child.length() }.getOrDefault(0L),
          lastModifiedMs = runCatching { child.lastModified() }.getOrDefault(0L),
          trigger = extractTriggerFromSnapshotName(name)
        )
      }
    }.getOrDefault(emptyList())
  }

  override fun deleteFile(relativePath: String): Boolean {
    val parts = relativePath.trim().trim('/').split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return false
    val dir = findDirectoryChain(parts.dropLast(1)) ?: return false
    val target = findExactChildFile(dir, parts.last()) ?: return false
    return runCatching { target.delete() }.getOrDefault(false)
  }

  private fun ensureDirectoryChain(dirParts: List<String>): DocumentFile? {
    val key = dirParts.joinToString("/")
    dirCache[key]?.let { cached ->
      if (runCatching { cached.exists() && cached.isDirectory }.getOrDefault(false)) {
        return cached
      }
    }
    var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    if (!current.exists() || !current.isDirectory) return null
    val built = StringBuilder()
    for (segment in dirParts) {
      if (built.isNotEmpty()) built.append('/')
      built.append(segment)
      val subKey = built.toString()
      val next = dirCache[subKey]?.takeIf { runCatching { it.exists() && it.isDirectory }.getOrDefault(false) }
        ?: findExactChildDir(current, segment)
        ?: runCatching { current.createDirectory(segment) }.getOrNull()
        ?: return null
      dirCache[subKey] = next
      current = next
    }
    return current
  }

  private fun findDirectoryChain(dirParts: List<String>): DocumentFile? {
    val key = dirParts.joinToString("/")
    dirCache[key]?.let { cached ->
      if (runCatching { cached.exists() && cached.isDirectory }.getOrDefault(false)) {
        return cached
      }
    }
    var current = DocumentFile.fromTreeUri(context, treeUri) ?: return null
    if (!current.exists() || !current.isDirectory) return null
    for (segment in dirParts) {
      current = findExactChildDir(current, segment) ?: return null
    }
    return current
  }

  private fun findExactChildDir(parent: DocumentFile, name: String): DocumentFile? {
    return runCatching {
      parent.listFiles().firstOrNull { it.isDirectory && it.name == name }
    }.getOrNull()
  }

  private fun findExactChildFile(parent: DocumentFile, name: String): DocumentFile? {
    return runCatching {
      parent.listFiles().firstOrNull { it.isFile && it.name == name }
    }.getOrNull()
  }

  private fun cleanupStaleTempFiles(parent: DocumentFile, targetFileName: String) {
    val prefix = ".${targetFileName}.tmp."
    runCatching {
      parent.listFiles()
        .filter { it.isFile && (it.name?.startsWith(prefix) == true) }
        .forEach { runCatching { it.delete() } }
    }
  }

  private fun renameSafDocument(doc: DocumentFile, targetName: String): Boolean {
    val contractRenamed = runCatching {
      DocumentsContract.renameDocument(context.contentResolver, doc.uri, targetName) != null
    }.getOrDefault(false)
    if (contractRenamed) return true
    return runCatching { doc.renameTo(targetName) }.getOrDefault(false)
  }

  private fun fallbackCopyAndDelete(
    parentDir: DocumentFile,
    mimeType: String,
    targetName: String,
    tempDoc: DocumentFile,
    bytes: ByteArray
  ) {
    val created = parentDir.createFile(mimeType, targetName)
      ?: parentDir.createFile("application/octet-stream", targetName)
      ?: error("Gagal rename maupun membuat file '$targetName'")
    try {
      val outStream = context.contentResolver.openOutputStream(created.uri, "wt")
        ?: context.contentResolver.openOutputStream(created.uri)
        ?: error("Gagal membuka output stream fallback '$targetName'")
      outStream.use { stream ->
        stream.write(bytes)
        stream.flush()
      }
    } finally {
      runCatching { tempDoc.delete() }
    }
  }
}

/**
 * Implementasi [ConfigTreeStorageBackend] berbasis java.io.File untuk pengujian atomik write,
 * mirror folder musik, dan rotasi snapshot di JVM / Robolectric.
 */
class LocalDirectoryConfigTreeStorageBackend(
  private val rootDir: File,
  private val preRenameHook: ((targetPath: String) -> Unit)? = null
) : ConfigTreeStorageBackend {

  override fun checkAccess(): PersistentFolderAccessCheck {
    val exists = rootDir.exists() && rootDir.isDirectory
    val writable = exists && rootDir.canWrite()
    return PersistentFolderAccessCheck(
      isAccessible = exists && writable,
      hasReadPermission = exists && rootDir.canRead(),
      hasWritePermission = writable,
      folderExists = exists,
      reason = if (exists && writable) null else "Folder backup tidak dapat diakses"
    )
  }

  override fun resolveDisplayName(): String = rootDir.name.ifBlank { "SoundPadPro" }

  override fun writeFileAtomic(relativePath: String, content: String): Result<Unit> = runCatching {
    val cleanRel = relativePath.trim().trimStart('/')
    val targetFile = File(rootDir, cleanRel)
    val parent = targetFile.parentFile ?: rootDir
    if (!parent.exists() && !parent.mkdirs()) {
      error("Gagal membuat folder '${parent.absolutePath}'")
    }

    val tempFile = File(parent, ".${targetFile.name}.tmp.${UUID.randomUUID().toString().take(6)}")
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    var success = false
    try {
      FileOutputStream(tempFile).use { fos ->
        fos.write(bytes)
        fos.flush()
        runCatching { fos.fd.sync() }
      }
      preRenameHook?.invoke(cleanRel)
      try {
        Files.move(
          tempFile.toPath(),
          targetFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE
        )
      } catch (_: Exception) {
        Files.move(
          tempFile.toPath(),
          targetFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING
        )
      }
      success = true
    } finally {
      if (!success && tempFile.exists()) {
        tempFile.delete()
      }
    }
  }

  override fun readFileText(relativePath: String): String? {
    val f = File(rootDir, relativePath.trim().trimStart('/'))
    if (!f.exists() || !f.isFile) return null
    return runCatching {
      FileInputStream(f).use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }.getOrNull()
  }

  override fun listFiles(relativeDir: String): List<SnapshotFileInfo> {
    val dir = File(rootDir, relativeDir.trim().trim('/'))
    if (!dir.exists() || !dir.isDirectory) return emptyList()
    return dir.listFiles()
      ?.filter { it.isFile && !it.name.startsWith(".") }
      ?.map { file ->
        SnapshotFileInfo(
          fileName = file.name,
          uriString = Uri.fromFile(file).toString(),
          sizeBytes = file.length(),
          lastModifiedMs = file.lastModified(),
          trigger = extractTriggerFromSnapshotName(file.name)
        )
      }
      .orEmpty()
  }

  override fun deleteFile(relativePath: String): Boolean {
    val f = File(rootDir, relativePath.trim().trim('/'))
    return f.exists() && f.isFile && f.delete()
  }
}

class SafPersistentConfigStorage(
  private val context: Context,
  private val safBackendFactory: (Uri) -> ConfigTreeStorageBackend = { uri ->
    SafConfigTreeStorageBackend(context, uri)
  }
) {

  companion object {
    const val BACKUP_ALERT_CHANNEL_ID = "soundpad_backup_alerts"
    private const val BACKUP_ALERT_NOTIFICATION_ID = 4088

    fun buildSuggestedBackupInitialUri(): Uri? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
      return runCatching {
        DocumentsContract.buildDocumentUri(
          "com.android.externalstorage.documents",
          "primary:Documents"
        )
      }.getOrNull()
    }

    fun takePersistableRwPermission(context: Context, treeUri: Uri): Boolean {
      val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      return runCatching {
        context.contentResolver.takePersistableUriPermission(treeUri, rwFlags)
        true
      }.getOrDefault(false)
    }

    fun postBackupAccessLostNotification(context: Context) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
          context,
          Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
      }

      val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          BACKUP_ALERT_CHANNEL_ID,
          context.getString(R.string.notification_channel_backup),
          NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
      }

      val launchIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      }
      val pendingIntent = PendingIntent.getActivity(
        context,
        BACKUP_ALERT_NOTIFICATION_ID,
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      )

      val notification = NotificationCompat.Builder(context, BACKUP_ALERT_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher_round)
        .setContentTitle(context.getString(R.string.backup_error_inaccessible_title))
        .setContentText(context.getString(R.string.backup_error_inaccessible_notif_body))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      runCatching {
        NotificationManagerCompat.from(context).notify(BACKUP_ALERT_NOTIFICATION_ID, notification)
      }
    }
  }

  fun checkBackupFolderAccess(backupTreeUri: Uri): PersistentFolderAccessCheck {
    return runCatching { safBackendFactory(backupTreeUri).checkAccess() }
      .getOrElse { err ->
        PersistentFolderAccessCheck(
          isAccessible = false,
          reason = err.localizedMessage ?: "Folder backup tidak dapat diakses"
        )
      }
  }

  fun resolveFolderDisplayName(treeUri: Uri): String {
    return runCatching { safBackendFactory(treeUri).resolveDisplayName() }.getOrDefault("SoundPadPro")
  }

  fun openBackend(treeUri: Uri): ConfigTreeStorageBackend = safBackendFactory(treeUri)

  suspend fun clearBackupFolderContents(backupTreeUri: Uri): Boolean = withContext(Dispatchers.IO) {
    val backend = safBackendFactory(backupTreeUri)
    if (!backend.checkAccess().isAccessible) return@withContext false
    val singleFiles = listOf(
      PersistentConfigSchema.FILE_CONFIG_META,
      PersistentConfigSchema.FILE_SETTINGS,
      PersistentConfigSchema.FILE_MIDI_MAPPING,
      PersistentConfigSchema.FILE_SCHEDULER,
      PersistentConfigSchema.FILE_SEQUENCER,
      PersistentConfigSchema.FILE_COMBINED_CONFIG
    )
    singleFiles.forEach { rel -> backend.deleteFile(rel) }
    listOf(
      PersistentConfigSchema.DIR_PROFILES,
      PersistentConfigSchema.DIR_LAYOUTS,
      PersistentConfigSchema.DIR_PRESETS,
      PersistentConfigSchema.DIR_BACKUPS,
      "extensions"
    ).forEach { dir ->
      backend.listFiles(dir).forEach { file ->
        backend.deleteFile("$dir/${file.fileName}")
      }
    }
    true
  }

  /**
   * Menulis backup config lengkap secara atomik ke:
   * 1. Folder Backup SAF utama (`config_meta.json`, `settings.json`, `profiles/`, `layouts/`, `presets/`, `midi_mapping.json`, `scheduler.json`, `sequencer.json`)
   * 2. Mirror subfolder `.soundpadpro/` di dalam folder musik yang dipilih user (jika folder musik writable).
   * 3. Opsional snapshot harian / sebelum update / keluar app ke `backups/` dengan rotasi maksimal 10 snapshot.
   */
  suspend fun writeBackup(
    backupTreeUri: Uri,
    bundle: PersistentConfigBundle,
    musicFolderUri: Uri? = null,
    createSnapshotTrigger: SnapshotTrigger? = null,
    snapshotTimestampMs: Long = System.currentTimeMillis(),
    maxSnapshots: Int = PersistentConfigSchema.MAX_ROTATED_SNAPSHOTS
  ): PersistentBackupWriteResult = withContext(Dispatchers.IO) {
    writeToBackends(
      primaryBackend = safBackendFactory(backupTreeUri),
      musicBackend = musicFolderUri
        ?.takeIf { it.toString().isNotBlank() && it != backupTreeUri }
        ?.let { safBackendFactory(it) },
      bundle = bundle,
      createSnapshotTrigger = createSnapshotTrigger,
      snapshotTimestampMs = snapshotTimestampMs,
      maxSnapshots = maxSnapshots
    )
  }

  internal fun writeToBackends(
    primaryBackend: ConfigTreeStorageBackend,
    musicBackend: ConfigTreeStorageBackend?,
    bundle: PersistentConfigBundle,
    createSnapshotTrigger: SnapshotTrigger? = null,
    snapshotTimestampMs: Long = System.currentTimeMillis(),
    maxSnapshots: Int = PersistentConfigSchema.MAX_ROTATED_SNAPSHOTS
  ): PersistentBackupWriteResult {
    val access = primaryBackend.checkAccess()
    if (!access.isAccessible) {
      return PersistentBackupWriteResult(
        success = false,
        errorMessage = access.reason ?: "Folder backup tidak dapat diakses",
        isAccessError = true
      )
    }

    val warnings = mutableListOf<String>()
    val includeWaveforms = bundle.meta.includesWaveforms
    val fileMap = PersistentConfigBundleSerializer.exportToFolderFiles(bundle, includeWaveforms)

    var writtenCount = 0
    for ((relativePath, jsonText) in fileMap) {
      val res = primaryBackend.writeFileAtomic(relativePath, jsonText)
      if (res.isFailure) {
        val msg = res.exceptionOrNull()?.localizedMessage ?: "Gagal menulis $relativePath"
        return PersistentBackupWriteResult(
          success = false,
          primaryFilesWritten = writtenCount,
          warnings = warnings,
          errorMessage = msg,
          isAccessError = !primaryBackend.checkAccess().isAccessible
        )
      }
      writtenCount++
    }

    // Bersihkan file profil/layout lama yang sudah dihapus/di-rename, jangan sentuh backups/
    pruneOrphanedJsonFiles(
      backend = primaryBackend,
      dirPath = PersistentConfigSchema.DIR_PROFILES,
      expectedRelativePaths = fileMap.keys
    )
    pruneOrphanedJsonFiles(
      backend = primaryBackend,
      dirPath = PersistentConfigSchema.DIR_LAYOUTS,
      expectedRelativePaths = fileMap.keys
    )

    var snapshotInfo: SnapshotFileInfo? = null
    var deletedSnapshots = 0
    var retainedSnapshots = primaryBackend.listFiles(PersistentConfigSchema.DIR_BACKUPS).size

    if (createSnapshotTrigger != null) {
      val combinedJson = PersistentConfigBundleSerializer.exportCombinedJson(bundle, includeWaveforms)
      val snapOutcome = writeSnapshotAndRotateOnBackend(
        backend = primaryBackend,
        trigger = createSnapshotTrigger,
        combinedJson = combinedJson,
        maxSnapshots = maxSnapshots,
        timestampMs = snapshotTimestampMs
      )
      snapshotInfo = snapOutcome.first
      deletedSnapshots = snapOutcome.second
      retainedSnapshots = snapOutcome.third
      if (snapshotInfo == null) {
        warnings.add("Gagal menyimpan snapshot (${createSnapshotTrigger.label})")
      }
    }

    var mirrorWritten = false
    if (musicBackend != null) {
      val mirrorAccess = runCatching { musicBackend.checkAccess() }.getOrNull()
      if (mirrorAccess?.isAccessible == true) {
        val mirrorResult = writeMusicSubfolderMirror(musicBackend, bundle, fileMap, includeWaveforms)
        mirrorWritten = mirrorResult.isSuccess
        if (mirrorResult.isFailure) {
          warnings.add("Mirror .soundpadpro gagal: ${mirrorResult.exceptionOrNull()?.localizedMessage}")
        }
      }
    }

    return PersistentBackupWriteResult(
      success = true,
      primaryFilesWritten = writtenCount,
      musicMirrorWritten = mirrorWritten,
      snapshotWritten = snapshotInfo,
      rotatedDeletedCount = deletedSnapshots,
      totalSnapshotsRetained = retainedSnapshots,
      syncedAtMs = snapshotTimestampMs,
      warnings = warnings,
      isAccessError = false
    )
  }

  internal fun writeSnapshotAndRotateOnBackend(
    backend: ConfigTreeStorageBackend,
    trigger: SnapshotTrigger,
    combinedJson: String,
    maxSnapshots: Int = PersistentConfigSchema.MAX_ROTATED_SNAPSHOTS,
    timestampMs: Long = System.currentTimeMillis()
  ): Triple<SnapshotFileInfo?, Int, Int> {
    val fileName = formatSnapshotFileName(timestampMs, trigger)
    val relPath = "${PersistentConfigSchema.DIR_BACKUPS}/$fileName"
    val writeRes = backend.writeFileAtomic(relPath, combinedJson)
    if (writeRes.isFailure) {
      val existingCount = backend.listFiles(PersistentConfigSchema.DIR_BACKUPS).size
      return Triple(null, 0, existingCount)
    }

    val allSnapshots = backend.listFiles(PersistentConfigSchema.DIR_BACKUPS)
      .filter { it.fileName.endsWith(".json", ignoreCase = true) }
      .sortedWith(
        compareByDescending<SnapshotFileInfo> { it.fileName }
          .thenByDescending { it.lastModifiedMs }
      )

    val keepLimit = maxSnapshots.coerceAtLeast(1)
    val toDelete = allSnapshots.drop(keepLimit)
    var deleted = 0
    toDelete.forEach { old ->
      if (backend.deleteFile("${PersistentConfigSchema.DIR_BACKUPS}/${old.fileName}")) {
        deleted++
      }
    }

    val writtenInfo = allSnapshots.firstOrNull { it.fileName == fileName }
      ?: SnapshotFileInfo(
        fileName = fileName,
        uriString = relPath,
        sizeBytes = combinedJson.toByteArray(StandardCharsets.UTF_8).size.toLong(),
        lastModifiedMs = timestampMs,
        trigger = trigger.slug
      )
    val retainedCount = (allSnapshots.size - deleted).coerceAtLeast(0)
    return Triple(writtenInfo, deleted, retainedCount)
  }

  fun listSnapshots(backupTreeUri: Uri): List<SnapshotFileInfo> {
    return runCatching {
      safBackendFactory(backupTreeUri)
        .listFiles(PersistentConfigSchema.DIR_BACKUPS)
        .filter { it.fileName.endsWith(".json", ignoreCase = true) }
        .sortedByDescending { it.fileName }
    }.getOrDefault(emptyList())
  }

  private fun writeMusicSubfolderMirror(
    musicBackend: ConfigTreeStorageBackend,
    bundle: PersistentConfigBundle,
    exportedPrimaryFiles: Map<String, String>,
    includeWaveforms: Boolean
  ): Result<Unit> = runCatching {
    val prefix = PersistentConfigSchema.MUSIC_SUBFOLDER_NAME
    val combinedJson = PersistentConfigBundleSerializer.exportCombinedJson(bundle, includeWaveforms)
    musicBackend.writeFileAtomic("$prefix/${PersistentConfigSchema.FILE_COMBINED_CONFIG}", combinedJson).getOrThrow()

    exportedPrimaryFiles.forEach { (relPath, content) ->
      val mirrorRel = when (relPath) {
        PersistentConfigSchema.FILE_MIDI_MAPPING -> "$prefix/mapping/${PersistentConfigSchema.FILE_MIDI_MAPPING}"
        else -> "$prefix/$relPath"
      }
      musicBackend.writeFileAtomic(mirrorRel, content).getOrThrow()
    }
  }

  private fun pruneOrphanedJsonFiles(
    backend: ConfigTreeStorageBackend,
    dirPath: String,
    expectedRelativePaths: Set<String>
  ) {
    val expectedNames = expectedRelativePaths
      .filter { it.startsWith("$dirPath/") }
      .map { it.removePrefix("$dirPath/") }
      .toSet()

    backend.listFiles(dirPath)
      .filter { it.fileName.endsWith(".json", ignoreCase = true) && it.fileName !in expectedNames }
      .forEach { stale ->
        runCatching { backend.deleteFile("$dirPath/${stale.fileName}") }
      }
  }
}

internal fun formatSnapshotFileName(timestampMs: Long, trigger: SnapshotTrigger): String {
  val fmt = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
  val stamp = fmt.format(Date(timestampMs))
  return "snapshot_${stamp}_${trigger.slug}.json"
}

internal fun extractTriggerFromSnapshotName(fileName: String): String {
  val withoutExt = fileName.removeSuffix(".json")
  return when {
    withoutExt.endsWith("_${SnapshotTrigger.DAILY.slug}") -> SnapshotTrigger.DAILY.slug
    withoutExt.endsWith("_${SnapshotTrigger.PRE_UPDATE.slug}") -> SnapshotTrigger.PRE_UPDATE.slug
    withoutExt.endsWith("_${SnapshotTrigger.APP_EXIT.slug}") -> SnapshotTrigger.APP_EXIT.slug
    withoutExt.endsWith("_${SnapshotTrigger.MANUAL.slug}") -> SnapshotTrigger.MANUAL.slug
    else -> "snapshot"
  }
}
