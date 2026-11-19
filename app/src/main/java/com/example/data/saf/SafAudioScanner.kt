package com.example.data.saf

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.audio.AudioFileValidator
import com.example.domain.model.AppErrorLog
import com.example.domain.model.FolderScanResult
import com.example.domain.model.SoundPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class SafAudioScanner(private val context: Context) {

  companion object {
    val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "flac")
  }

  fun takePersistablePermission(treeUri: Uri) {
    val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    val grantedRw = runCatching {
      context.contentResolver.takePersistableUriPermission(treeUri, rwFlags)
    }.isSuccess
    if (!grantedRw) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
      }
    }
  }

  fun hasPersistedReadPermission(treeUri: Uri): Boolean {
    return context.contentResolver.persistedUriPermissions.any { permission ->
      permission.uri == treeUri && permission.isReadPermission
    }
  }

  fun resolveFolderDisplayName(treeUri: Uri): String {
    return runCatching {
      DocumentFile.fromTreeUri(context, treeUri)?.name
    }.getOrNull() ?: treeUri.lastPathSegment?.substringAfterLast(':') ?: "Audio Folder"
  }

  suspend fun scanFolder(
    treeUri: Uri,
    previousIds: Set<String> = emptySet()
  ): FolderScanResult = withContext(Dispatchers.IO) {
    val folderName = resolveFolderDisplayName(treeUri)
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)

    val rootFiles = mutableListOf<RawScannedAudio>()
    val subDirs = mutableListOf<Pair<String, String>>() // docId to dirName

    queryDirectoryImmediateChildren(treeUri, rootDocId, rootFiles, subDirs)

    val sortedDirs = subDirs.sortedBy { it.second.lowercase() }
    val bankFiles = mutableListOf<Triple<Int, String, List<RawScannedAudio>>>()
    val bankFolderUris = mutableMapOf<Int, String>()
    val bankNames = mutableMapOf<Int, String>()

    // 1 subfolder = 1 bank (1-level recursive scan)
    var nextBankIdx = 0
    for ((dirDocId, dirName) in sortedDirs) {
      val dirAudio = mutableListOf<RawScannedAudio>()
      val ignoredNestedDirs = mutableListOf<Pair<String, String>>()
      queryDirectoryImmediateChildren(treeUri, dirDocId, dirAudio, ignoredNestedDirs)
      if (dirAudio.isNotEmpty()) {
        val idx = nextBankIdx++
        bankNames[idx] = dirName
        bankFolderUris[idx] = DocumentsContract.buildDocumentUriUsingTree(treeUri, dirDocId).toString()
        bankFiles.add(Triple(idx, dirName, dirAudio.sortedBy { it.fileName.lowercase() }))
      }
    }

    val hasSubfolderBanks = bankFiles.isNotEmpty()
    if (hasSubfolderBanks && rootFiles.isNotEmpty()) {
      val rootIdx = nextBankIdx
      bankNames[rootIdx] = folderName.ifBlank { "Root" }
      bankFolderUris[rootIdx] = treeUri.toString()
      bankFiles.add(Triple(rootIdx, bankNames.getValue(rootIdx), rootFiles.sortedBy { it.fileName.lowercase() }))
    }

    val collectedErrors = mutableListOf<AppErrorLog>()
    val pads = mutableListOf<SoundPad>()
    var globalPos = 0

    if (hasSubfolderBanks) {
      for ((bIdx, bName, items) in bankFiles) {
        for (item in items) {
          val pad = buildValidatedSoundPad(
            item = item,
            positionIndex = globalPos++,
            bankIndex = bIdx,
            subfolderName = bName,
            errorsOut = collectedErrors
          )
          pads.add(pad)
        }
      }
    } else {
      val sorted = rootFiles.sortedBy { it.fileName.lowercase() }
      for (item in sorted) {
        val pad = buildValidatedSoundPad(
          item = item,
          positionIndex = globalPos++,
          bankIndex = 0,
          subfolderName = "",
          errorsOut = collectedErrors
        )
        pads.add(pad)
      }
    }

    val newCount = if (previousIds.isEmpty()) 0 else pads.count { it.id !in previousIds }
    FolderScanResult(
      folderName = folderName,
      folderUri = treeUri.toString(),
      pads = pads,
      newlyDetectedCount = newCount,
      hasSubfolderBanks = hasSubfolderBanks,
      bankNames = bankNames,
      bankFolderUris = bankFolderUris,
      detectedErrors = collectedErrors
    )
  }

  suspend fun scanSingleBankFolder(
    rootTreeUri: Uri,
    bankDocUriString: String,
    bankIndex: Int,
    bankName: String
  ): Pair<List<SoundPad>, List<AppErrorLog>> = withContext(Dispatchers.IO) {
    val targetUri = Uri.parse(bankDocUriString)
    val docId = runCatching { DocumentsContract.getDocumentId(targetUri) }.getOrNull()
      ?: DocumentsContract.getTreeDocumentId(rootTreeUri)
    val rawFiles = mutableListOf<RawScannedAudio>()
    val subDirs = mutableListOf<Pair<String, String>>()
    queryDirectoryImmediateChildren(rootTreeUri, docId, rawFiles, subDirs)

    val sorted = rawFiles.sortedBy { it.fileName.lowercase() }
    val errors = mutableListOf<AppErrorLog>()
    val pads = sorted.mapIndexed { idx, item ->
      buildValidatedSoundPad(
        item = item,
        positionIndex = idx,
        bankIndex = bankIndex,
        subfolderName = bankName,
        errorsOut = errors
      )
    }
    pads to errors
  }

  private fun buildValidatedSoundPad(
    item: RawScannedAudio,
    positionIndex: Int,
    bankIndex: Int,
    subfolderName: String,
    errorsOut: MutableList<AppErrorLog>
  ): SoundPad {
    val padId = stablePadId(item.uri.toString())
    val validation = AudioFileValidator.validateAudioUri(
      context = context,
      uri = item.uri,
      fileName = item.fileName,
      extension = item.extension,
      knownSizeBytes = item.sizeBytes
    )
    val durationMs = if (validation.isValid) extractDurationMs(item.uri) else 0L
    if (!validation.isValid && validation.errorType != null) {
      errorsOut.add(
        AppErrorLog(
          id = "scanerr_${UUID.randomUUID()}",
          timestampMs = System.currentTimeMillis(),
          fileName = item.fileName,
          uriString = item.uri.toString(),
          errorType = validation.errorType,
          message = validation.message ?: validation.errorType.label,
          suggestion = validation.suggestion ?: validation.errorType.defaultSuggestion,
          padId = padId
        )
      )
    }
    val baseName = item.fileName.substringBeforeLast('.').ifBlank { item.fileName }
    return SoundPad(
      id = padId,
      uriString = item.uri.toString(),
      fileName = item.fileName,
      displayName = baseName,
      extension = item.extension.uppercase(),
      durationMs = durationMs,
      sizeBytes = item.sizeBytes,
      positionIndex = positionIndex,
      bankIndex = bankIndex,
      subfolderName = subfolderName,
      colorArgb = null,
      glowColorArgb = null,
      glowIntensity = 0f,
      isPreloadedInRam = false,
      errorType = validation.errorType,
      errorMessage = validation.message
    )
  }

  fun observeFolderChanges(treeUri: Uri): Flow<Unit> = callbackFlow {
    val childrenUri = runCatching {
      val rootId = DocumentsContract.getTreeDocumentId(treeUri)
      DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, rootId)
    }.getOrNull()

    if (childrenUri == null) {
      close()
      return@callbackFlow
    }

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
      override fun onChange(selfChange: Boolean) {
        trySend(Unit)
      }

      override fun onChange(selfChange: Boolean, uri: Uri?) {
        trySend(Unit)
      }
    }

    runCatching {
      context.contentResolver.registerContentObserver(childrenUri, true, observer)
    }

    awaitClose {
      runCatching {
        context.contentResolver.unregisterContentObserver(observer)
      }
    }
  }

  private fun queryDirectoryImmediateChildren(
    treeUri: Uri,
    parentDocId: String,
    outFiles: MutableList<RawScannedAudio>,
    outDirs: MutableList<Pair<String, String>>
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

          if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
            if (!name.startsWith(".")) {
              outDirs.add(docId to name.ifBlank { "Bank" })
            }
          } else {
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in SUPPORTED_EXTENSIONS) {
              val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
              outFiles.add(
                RawScannedAudio(
                  uri = docUri,
                  fileName = name,
                  extension = ext,
                  sizeBytes = size
                )
              )
            }
          }
        }
      }
    }
  }

  private fun extractDurationMs(uri: Uri): Long {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
      0L
    } finally {
      runCatching { retriever.release() }
    }
  }

  private fun stablePadId(uriString: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(uriString.toByteArray())
    return bytes.take(12).joinToString("") { "%02x".format(it) }
  }

  private data class RawScannedAudio(
    val uri: Uri,
    val fileName: String,
    val extension: String,
    val sizeBytes: Long
  )
}
