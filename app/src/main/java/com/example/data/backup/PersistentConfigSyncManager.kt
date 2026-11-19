package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.example.audio.AudioOutputRouter
import com.example.data.local.db.SoundPadDao
import com.example.data.local.preferences.GlobalSettingsRepository
import com.example.data.local.preferences.PersistentSyncInternalMeta
import com.example.domain.model.GlobalSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupSyncStatus {
  UNCONFIGURED,
  IDLE,
  PENDING,
  SAVING,
  SAVED,
  ERROR_INACCESSIBLE
}

@Immutable
data class PersistentBackupUiState(
  val status: BackupSyncStatus = BackupSyncStatus.UNCONFIGURED,
  val folderUri: String? = null,
  val folderName: String? = null,
  val autoSyncEnabled: Boolean = true,
  val lastSyncAtMs: Long = 0L,
  val includeWaveforms: Boolean = false,
  val errorMessage: String? = null,
  val shouldShowFirstLaunchPrompt: Boolean = false,
  val snapshotCount: Int = 0,
  val lastSnapshotTrigger: String? = null
)

@OptIn(FlowPreview::class)
class PersistentConfigSyncManager(
  private val context: Context,
  private val dao: SoundPadDao,
  private val settingsRepo: GlobalSettingsRepository,
  private val appScope: CoroutineScope,
  private val storage: SafPersistentConfigStorage = SafPersistentConfigStorage(context),
  private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
  private val appVersionName: String = BuildConfig.VERSION_NAME,
  private val appVersionCode: Int = BuildConfig.VERSION_CODE,
  private val timeProvider: () -> Long = { System.currentTimeMillis() },
  private val dayKeyProvider: (Long) -> String = { ms ->
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
  },
  private val exitSnapshotMinIntervalMs: Long = 60_000L,
  private val postAccessLostNotification: (Context) -> Unit = { ctx ->
    SafPersistentConfigStorage.postBackupAccessLostNotification(ctx)
  }
) {

  companion object {
    const val DEFAULT_DEBOUNCE_MS = 2000L
    const val ERROR_FOLDER_INACCESSIBLE = "Folder backup tidak dapat diakses"
  }

  private val syncMutex = Mutex()
  private val dirtyTriggerFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

  private val _backupState = MutableStateFlow(PersistentBackupUiState())
  val backupState: StateFlow<PersistentBackupUiState> = _backupState.asStateFlow()

  private var observerJob: Job? = null
  private var debounceJob: Job? = null

  @Volatile
  private var hasPendingDirty = false

  @Volatile
  private var lastNotifiedInaccessibleUri: String? = null

  @Volatile
  private var lastExitSnapshotAtMs: Long = 0L

  @Volatile
  private var latestSettings: GlobalSettings = GlobalSettings()

  @Volatile
  private var latestSyncMeta: PersistentSyncInternalMeta = PersistentSyncInternalMeta()

  @Volatile
  private var isSyncPaused: Boolean = false

  var onAccessLostCallback: ((folderUri: String, reason: String) -> Unit)? = null

  fun start() {
    if (observerJob?.isActive == true) return

    observerJob = appScope.launch(Dispatchers.IO) {
      combine(settingsRepo.settingsFlow, settingsRepo.persistentSyncMetaFlow) { gs, meta ->
        gs to meta
      }.collect { (gs, meta) ->
        latestSettings = gs
        latestSyncMeta = meta

        val uriStr = gs.persistentBackupFolderUri
        val promptVisible = gs.hasCompletedOnboarding &&
          uriStr.isNullOrBlank() &&
          !meta.hasPromptedBackupFolder

        _backupState.update { cur ->
          val nextStatus = when {
            uriStr.isNullOrBlank() -> BackupSyncStatus.UNCONFIGURED
            cur.status == BackupSyncStatus.UNCONFIGURED -> {
              if (gs.persistentLastSyncAtMs > 0L) BackupSyncStatus.SAVED else BackupSyncStatus.IDLE
            }
            else -> cur.status
          }
          cur.copy(
            status = nextStatus,
            folderUri = uriStr,
            folderName = gs.persistentBackupFolderName,
            autoSyncEnabled = gs.persistentAutoSyncEnabled,
            lastSyncAtMs = gs.persistentLastSyncAtMs,
            includeWaveforms = gs.persistentIncludeWaveforms,
            shouldShowFirstLaunchPrompt = promptVisible,
            errorMessage = if (uriStr.isNullOrBlank()) null else cur.errorMessage
          )
        }
      }
    }

    debounceJob = appScope.launch(Dispatchers.IO) {
      dirtyTriggerFlow
        .debounce(debounceMs)
        .collectLatest { reason ->
          performDebouncedSync(reason)
        }
    }

    appScope.launch(Dispatchers.IO) {
      val gs = settingsRepo.settingsFlow.first()
      val meta = settingsRepo.persistentSyncMetaFlow.first()
      latestSettings = gs
      latestSyncMeta = meta
      checkPreUpdateAndAccessOnStartup(gs, meta)
    }
  }

  fun notifyConfigDirty(reason: String) {
    if (isSyncPaused) return
    val gs = latestSettings
    if (gs.persistentBackupFolderUri.isNullOrBlank() || !gs.persistentAutoSyncEnabled) {
      return
    }
    hasPendingDirty = true
    _backupState.update { cur ->
      if (cur.status == BackupSyncStatus.ERROR_INACCESSIBLE || cur.status == BackupSyncStatus.SAVING) {
        cur
      } else {
        cur.copy(status = BackupSyncStatus.PENDING)
      }
    }
    dirtyTriggerFlow.tryEmit(reason)
  }

  suspend fun <T> pauseSyncDuring(block: suspend () -> T): T {
    isSyncPaused = true
    return try {
      block()
    } finally {
      hasPendingDirty = false
      isSyncPaused = false
    }
  }

  suspend fun setIncludeWaveforms(include: Boolean) {
    settingsRepo.setPersistentIncludeWaveforms(include)
    if (!latestSettings.persistentBackupFolderUri.isNullOrBlank() && latestSettings.persistentAutoSyncEnabled) {
      notifyConfigDirty("include_waveforms_changed")
    }
  }

  fun listCurrentSnapshots(): List<SnapshotFileInfo> {
    val uriStr = latestSettings.persistentBackupFolderUri?.takeIf { it.isNotBlank() } ?: return emptyList()
    return storage.listSnapshots(Uri.parse(uriStr))
  }

  suspend fun clearBackupFolderFilesIfConfigured(): Boolean {
    val uriStr = settingsRepo.settingsFlow.first().persistentBackupFolderUri
      ?.takeIf { it.isNotBlank() } ?: return false
    return storage.clearBackupFolderContents(Uri.parse(uriStr))
  }

  suspend fun verifyBackupFolderAccess(triggerPreUpdateIfNeeded: Boolean = false): PersistentFolderAccessCheck {
    val gs = settingsRepo.settingsFlow.first()
    val meta = settingsRepo.persistentSyncMetaFlow.first()
    latestSettings = gs
    latestSyncMeta = meta

    val uriStr = gs.persistentBackupFolderUri
    if (uriStr.isNullOrBlank()) {
      _backupState.update {
        it.copy(
          status = BackupSyncStatus.UNCONFIGURED,
          errorMessage = null
        )
      }
      return PersistentFolderAccessCheck(isAccessible = false, reason = "Folder backup belum dipilih")
    }

    val access = storage.checkBackupFolderAccess(Uri.parse(uriStr))
    if (!access.isAccessible) {
      markFolderInaccessible(uriStr, access.reason)
    } else {
      lastNotifiedInaccessibleUri = null
      val snaps = storage.listSnapshots(Uri.parse(uriStr))
      _backupState.update { cur ->
        cur.copy(
          status = if (gs.persistentLastSyncAtMs > 0L) BackupSyncStatus.SAVED else BackupSyncStatus.IDLE,
          snapshotCount = snaps.size,
          errorMessage = null
        )
      }
      if (triggerPreUpdateIfNeeded) {
        checkPreUpdateAndAccessOnStartup(gs, meta)
      }
    }
    return access
  }

  suspend fun onBackupFolderSelected(treeUri: Uri): Boolean {
    SafPersistentConfigStorage.takePersistableRwPermission(context, treeUri)
    val access = storage.checkBackupFolderAccess(treeUri)
    if (!access.isAccessible) {
      markFolderInaccessible(treeUri.toString(), access.reason)
      return false
    }

    val folderName = storage.resolveFolderDisplayName(treeUri)
    lastNotifiedInaccessibleUri = null
    settingsRepo.setPersistentBackupFolder(treeUri.toString(), folderName)
    settingsRepo.setHasPromptedBackupFolder(true)

    val result = executeSyncInternal(
      forceWrite = true,
      explicitSnapshotTrigger = determineInitialSnapshotTrigger()
    )
    return result.success
  }

  suspend fun dismissFirstLaunchPrompt() {
    settingsRepo.setHasPromptedBackupFolder(true)
  }

  suspend fun setAutoSyncEnabled(enabled: Boolean) {
    settingsRepo.setPersistentAutoSyncEnabled(enabled)
    if (enabled && !latestSettings.persistentBackupFolderUri.isNullOrBlank()) {
      notifyConfigDirty("auto_sync_reenabled")
    }
  }

  suspend fun syncNow(
    createSnapshot: Boolean = false,
    trigger: SnapshotTrigger = SnapshotTrigger.MANUAL
  ): PersistentBackupWriteResult {
    return executeSyncInternal(
      forceWrite = true,
      explicitSnapshotTrigger = if (createSnapshot) trigger else null
    )
  }

  fun onAppExiting() {
    appScope.launch(Dispatchers.IO) {
      val gs = settingsRepo.settingsFlow.first()
      latestSettings = gs
      val uriStr = gs.persistentBackupFolderUri
      if (uriStr.isNullOrBlank()) return@launch
      if (!gs.persistentAutoSyncEnabled && !hasPendingDirty) return@launch

      val now = timeProvider()
      val shouldCreateExitSnapshot =
        hasPendingDirty || (now - lastExitSnapshotAtMs) >= exitSnapshotMinIntervalMs
      if (!hasPendingDirty && !shouldCreateExitSnapshot) return@launch

      val trigger = if (shouldCreateExitSnapshot) SnapshotTrigger.APP_EXIT else null
      val result = executeSyncInternal(
        forceWrite = true,
        explicitSnapshotTrigger = trigger
      )
      if (result.success && trigger == SnapshotTrigger.APP_EXIT) {
        lastExitSnapshotAtMs = now
      }
    }
  }

  internal suspend fun performDebouncedSync(reason: String): PersistentBackupWriteResult {
    val gs = settingsRepo.settingsFlow.first()
    latestSettings = gs
    if (gs.persistentBackupFolderUri.isNullOrBlank() || !gs.persistentAutoSyncEnabled) {
      hasPendingDirty = false
      return PersistentBackupWriteResult(success = false, errorMessage = "Auto-sync nonaktif")
    }
    return executeSyncInternal(forceWrite = true, explicitSnapshotTrigger = null)
  }

  private suspend fun checkPreUpdateAndAccessOnStartup(
    gs: GlobalSettings,
    meta: PersistentSyncInternalMeta
  ) {
    val uriStr = gs.persistentBackupFolderUri
    if (uriStr.isNullOrBlank()) return

    val access = storage.checkBackupFolderAccess(Uri.parse(uriStr))
    if (!access.isAccessible) {
      markFolderInaccessible(uriStr, access.reason)
      return
    }

    lastNotifiedInaccessibleUri = null
    if (meta.lastBackedUpVersionCode in 1 until appVersionCode) {
      val result = executeSyncInternal(
        forceWrite = true,
        explicitSnapshotTrigger = SnapshotTrigger.PRE_UPDATE
      )
      if (result.success) {
        settingsRepo.setLastBackedUpVersionCode(appVersionCode)
      }
    } else if (meta.lastBackedUpVersionCode == 0) {
      settingsRepo.setLastBackedUpVersionCode(appVersionCode)
    }
  }

  private suspend fun determineInitialSnapshotTrigger(): SnapshotTrigger? {
    val meta = settingsRepo.persistentSyncMetaFlow.first()
    val today = dayKeyProvider(timeProvider())
    return when {
      meta.lastBackedUpVersionCode in 1 until appVersionCode -> SnapshotTrigger.PRE_UPDATE
      meta.lastDailySnapshotDay != today -> SnapshotTrigger.DAILY
      else -> null
    }
  }

  private suspend fun executeSyncInternal(
    forceWrite: Boolean,
    explicitSnapshotTrigger: SnapshotTrigger?
  ): PersistentBackupWriteResult = syncMutex.withLock {
    val gs = settingsRepo.settingsFlow.first()
    val meta = settingsRepo.persistentSyncMetaFlow.first()
    latestSettings = gs
    latestSyncMeta = meta

    val uriStr = gs.persistentBackupFolderUri
    if (uriStr.isNullOrBlank()) {
      _backupState.update { it.copy(status = BackupSyncStatus.UNCONFIGURED) }
      return@withLock PersistentBackupWriteResult(success = false, errorMessage = "Folder backup belum dipilih")
    }

    if (!forceWrite && !hasPendingDirty) {
      return@withLock PersistentBackupWriteResult(success = true)
    }

    val backupUri = Uri.parse(uriStr)
    val access = storage.checkBackupFolderAccess(backupUri)
    if (!access.isAccessible) {
      markFolderInaccessible(uriStr, access.reason)
      return@withLock PersistentBackupWriteResult(
        success = false,
        errorMessage = ERROR_FOLDER_INACCESSIBLE,
        isAccessError = true
      )
    }

    _backupState.update { it.copy(status = BackupSyncStatus.SAVING, errorMessage = null) }

    val now = timeProvider()
    val today = dayKeyProvider(now)
    val resolvedSnapshotTrigger = when {
      explicitSnapshotTrigger != null -> explicitSnapshotTrigger
      meta.lastBackedUpVersionCode in 1 until appVersionCode -> SnapshotTrigger.PRE_UPDATE
      meta.lastDailySnapshotDay != today -> SnapshotTrigger.DAILY
      else -> null
    }

    val bundle = PersistentConfigSnapshotAssembler.captureFromDatabase(
      dao = dao,
      settings = gs,
      context = context,
      appVersionName = appVersionName,
      appVersionCode = appVersionCode,
      includeWaveforms = gs.persistentIncludeWaveforms,
      computeFileHashes = true,
      outputDeviceNameResolver = { devId ->
        if (devId == null) {
          null
        } else {
          AudioOutputRouter.listOutputDevices(context).firstOrNull { it.id == devId }?.name
        }
      }
    )

    val musicUri = gs.folderUri
      ?.takeIf { it.isNotBlank() && it != "demo://builtin" }
      ?.let { runCatching { Uri.parse(it) }.getOrNull() }

    val writeResult = storage.writeBackup(
      backupTreeUri = backupUri,
      bundle = bundle,
      musicFolderUri = musicUri,
      createSnapshotTrigger = resolvedSnapshotTrigger,
      snapshotTimestampMs = now
    )

    if (writeResult.success) {
      hasPendingDirty = false
      lastNotifiedInaccessibleUri = null
      settingsRepo.setPersistentLastSyncAtMs(now)
      if (resolvedSnapshotTrigger == SnapshotTrigger.DAILY) {
        settingsRepo.setLastDailySnapshotDay(today)
      }
      if (meta.lastBackedUpVersionCode != appVersionCode) {
        settingsRepo.setLastBackedUpVersionCode(appVersionCode)
      }
      _backupState.update { cur ->
        cur.copy(
          status = BackupSyncStatus.SAVED,
          lastSyncAtMs = now,
          errorMessage = null,
          snapshotCount = writeResult.totalSnapshotsRetained,
          lastSnapshotTrigger = writeResult.snapshotWritten?.trigger ?: cur.lastSnapshotTrigger
        )
      }
    } else if (writeResult.isAccessError) {
      markFolderInaccessible(uriStr, writeResult.errorMessage)
    } else {
      _backupState.update { cur ->
        cur.copy(
          status = BackupSyncStatus.ERROR_INACCESSIBLE,
          errorMessage = writeResult.errorMessage ?: ERROR_FOLDER_INACCESSIBLE
        )
      }
    }
    return@withLock writeResult
  }

  private fun markFolderInaccessible(uriStr: String, reason: String?) {
    val wasAlreadyNotified = lastNotifiedInaccessibleUri == uriStr
    lastNotifiedInaccessibleUri = uriStr
    _backupState.update { cur ->
      cur.copy(
        status = BackupSyncStatus.ERROR_INACCESSIBLE,
        errorMessage = ERROR_FOLDER_INACCESSIBLE
      )
    }
    if (!wasAlreadyNotified) {
      postAccessLostNotification(context)
      onAccessLostCallback?.invoke(uriStr, reason ?: ERROR_FOLDER_INACCESSIBLE)
    }
  }
}
