package com.example.data.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Mengelola Export/Import arsip satu file `.zip` untuk seluruh Persistent Config SoundPad Pro
 * serta menyiapkan arsip sementara di `cacheDir/shared_backups/` untuk dibagikan via Android Share Sheet.
 */
object ZipBackupArchiver {

  private const val MAX_ZIP_ENTRIES = 512
  private const val MAX_ENTRY_BYTES = 10 * 1024 * 1024 // 10 MB per JSON entry
  private const val SHARE_CACHE_DIR = "shared_backups"

  fun exportBundleToZipOutputStream(
    bundle: PersistentConfigBundle,
    outputStream: OutputStream,
    includeWaveforms: Boolean = bundle.meta.includesWaveforms
  ): Int {
    val fileMap = PersistentConfigBundleSerializer.exportToFolderFiles(bundle, includeWaveforms)
    ZipOutputStream(outputStream.buffered()).use { zos ->
      fileMap.forEach { (relativePath, content) ->
        val cleanEntry = relativePath.replace('\\', '/').trimStart('/')
        zos.putNextEntry(ZipEntry(cleanEntry))
        zos.write(content.toByteArray(StandardCharsets.UTF_8))
        zos.closeEntry()
      }
      zos.finish()
    }
    return fileMap.size
  }

  suspend fun exportBundleToUri(
    context: Context,
    bundle: PersistentConfigBundle,
    targetUri: Uri,
    includeWaveforms: Boolean = bundle.meta.includesWaveforms
  ): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      val stream = context.contentResolver.openOutputStream(targetUri, "wt")
        ?: context.contentResolver.openOutputStream(targetUri)
        ?: error("Tidak dapat membuka lokasi tujuan file .zip")
      stream.use { out ->
        exportBundleToZipOutputStream(bundle, out, includeWaveforms)
      }
    }
  }

  fun readZipEntriesSafe(inputStream: InputStream): Map<String, String> {
    val result = linkedMapOf<String, String>()
    ZipInputStream(inputStream.buffered()).use { zis ->
      var count = 0
      var entry = zis.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          count++
          require(count <= MAX_ZIP_ENTRIES) { "Arsip ZIP memiliki terlalu banyak file (> $MAX_ZIP_ENTRIES)" }
          val safePath = sanitizeZipEntryPath(entry.name)
          if (safePath != null && safePath.endsWith(".json", ignoreCase = true)) {
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
              val read = zis.read(buf)
              if (read <= 0) break
              total += read
              require(total <= MAX_ENTRY_BYTES) { "Ukuran entri '$safePath' melebihi batas aman (10 MB)" }
              baos.write(buf, 0, read)
            }
            result[safePath] = baos.toString(StandardCharsets.UTF_8.name())
          }
        }
        zis.closeEntry()
        entry = zis.nextEntry
      }
    }
    return result
  }

  suspend fun inspectZipUri(
    context: Context,
    zipUri: Uri,
    displayName: String = "SoundPadPro_Backup.zip"
  ): Result<BackupInspectionSummary> = withContext(Dispatchers.IO) {
    runCatching {
      val entries = context.contentResolver.openInputStream(zipUri)?.use { stream ->
        readZipEntriesSafe(stream)
      } ?: error("Tidak dapat membuka file arsip .zip")

      PersistentConfigRestoreEngine.inspectFolderFilesMap(
        files = entries,
        sourceLabel = displayName,
        sourceKind = BackupSourceKind.ZIP_ARCHIVE
      ).getOrThrow()
    }
  }

  suspend fun createShareableZipFile(
    context: Context,
    bundle: PersistentConfigBundle,
    includeWaveforms: Boolean = bundle.meta.includesWaveforms
  ): Result<Pair<File, Uri>> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(context.cacheDir, SHARE_CACHE_DIR)
      if (!dir.exists()) {
        dir.mkdirs()
      } else {
        // Bersihkan file share lama agar cache tidak menumpuk
        dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach {
          runCatching { it.delete() }
        }
      }
      val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
      val zipFile = File(dir, "SoundPadPro_Backup_$stamp.zip")
      FileOutputStream(zipFile).use { fos ->
        exportBundleToZipOutputStream(bundle, fos, includeWaveforms)
      }
      val contentUri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        zipFile
      )
      zipFile to contentUri
    }
  }

  fun buildShareBackupIntent(
    context: Context,
    zipContentUri: Uri,
    chooserTitle: String = "Bagikan Backup SoundPad Pro"
  ): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
      type = "application/zip"
      putExtra(Intent.EXTRA_STREAM, zipContentUri)
      putExtra(Intent.EXTRA_SUBJECT, "SoundPad Pro Config Backup (.zip)")
      putExtra(
        Intent.EXTRA_TEXT,
        "Backup lengkap konfigurasi SoundPad Pro (profil, posisi pad, efek, MIDI, jadwal, sequencer)."
      )
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(sendIntent, chooserTitle).apply {
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
  }

  private fun sanitizeZipEntryPath(rawName: String): String? {
    val normalized = rawName.replace('\\', '/').trim().trimStart('/')
    if (normalized.isEmpty()) return null
    val segments = normalized.split('/').filter { it.isNotEmpty() }
    if (segments.any { it == ".." || it == "." || it.contains('\u0000') }) {
      throw SecurityException("Entri ZIP tidak aman (path traversal terdeteksi): $rawName")
    }
    return segments.joinToString("/")
  }
}
