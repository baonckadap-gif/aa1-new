package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.backup.RestoreSelectionOptions
import com.example.data.backup.SafPersistentConfigStorage
import com.example.data.backup.SnapshotFileInfo
import com.example.domain.model.AppLanguage
import com.example.domain.model.PadErrorType
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.scheduler.PadSchedulerManager
import com.example.ui.SoundPadScreen
import com.example.ui.SoundPadViewModel
import com.example.ui.theme.SoundPadProTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

  private val viewModel: SoundPadViewModel by viewModels {
    SoundPadViewModel.AppHolder.application = application
    SoundPadViewModel.Factory
  }

  private var pendingExportProfileId: String? = null
  private var pendingCutExport: Triple<SoundPad, Long, Long>? = null
  private var hasRequestedNotificationPermission: Boolean = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      SoundPadRoot()
    }
  }

  @Composable
  private fun SoundPadRoot() {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        val rwOk = runCatching {
          contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
        }.isSuccess
        if (!rwOk) {
          runCatching {
            contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          }
        }
        viewModel.onFolderSelected(uri)
      }
    }

    val backupFolderPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        SafPersistentConfigStorage.takePersistableRwPermission(this, uri)
        viewModel.onPersistentBackupFolderSelected(uri)
      }
    }

    val restoreFolderPickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        viewModel.inspectRestoreFolderUri(uri)
      }
    }

    val restoreRelinkMusicFolderLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        val rwOk = runCatching {
          contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
        }.isSuccess
        if (!rwOk) {
          runCatching {
            contentResolver.takePersistableUriPermission(
              uri,
              Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
          }
        }
        viewModel.setRestoreRelinkMusicFolder(uri)
      }
    }

    val exportConfigZipLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
      if (uri != null) {
        viewModel.exportConfigZipToUri(uri)
      }
    }

    val importConfigZipLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        viewModel.inspectRestoreZipUri(uri)
      }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { /* Playback works even if notification permission is denied */ }

    val exportJsonLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      val profileId = pendingExportProfileId
      pendingExportProfileId = null
      if (uri != null && profileId != null) {
        viewModel.exportProfileToUri(profileId, uri)
      }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        viewModel.importProfileFromUri(uri)
      }
    }

    val backupExportLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      if (uri != null) {
        viewModel.exportFullBackupToUri(uri)
      }
    }

    val backupRestoreLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
      if (uri != null) {
        viewModel.importFullBackupFromUri(uri)
      }
    }

    val exportErrorLogLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
      if (uri != null) {
        viewModel.exportErrorLogsToUri(uri)
      }
    }

    val exportCutWavLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri ->
      val pending = pendingCutExport
      pendingCutExport = null
      if (uri != null && pending != null) {
        viewModel.exportCutToSafUri(pending.first, pending.second, pending.third, uri)
      }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
      if (granted) {
        viewModel.openVoiceRecordSheet()
      }
    }

    val bluetoothMidiPermissionLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
      if (grants.values.all { it }) {
        viewModel.startBleMidiScan()
      }
    }

    val masterRecordFolderLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
      if (uri != null) {
        runCatching {
          contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
          )
        }
        viewModel.setMasterRecordOutputFolder(uri)
      }
    }

    var canScheduleExactAlarms by remember {
      mutableStateOf(PadSchedulerManager.canScheduleExactAlarms(this))
    }

    val keepAwake = uiState.settings.keepScreenOn || uiState.settings.isFocusMode
    LaunchedEffect(keepAwake) {
      if (keepAwake) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }

    val hideSystemBars = uiState.settings.isFullscreen || uiState.settings.isFocusMode
    LaunchedEffect(hideSystemBars) {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      if (hideSystemBars) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> {
            canScheduleExactAlarms = PadSchedulerManager.canScheduleExactAlarms(this@MainActivity)
            viewModel.checkForNewFilesOnResume()
            viewModel.verifyBackupFolderAccess()
          }
          Lifecycle.Event.ON_STOP -> {
            viewModel.onAppStopping()
          }
          else -> Unit
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
      }
    }

    val useDarkTheme = when (uiState.settings.themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK, ThemeMode.AMOLED -> true
    }

    val localizedContext = rememberLocalizedContext(uiState.settings.appLanguage)

    val actions = remember(viewModel) {
      MainScreenActions(
        activity = this,
        viewModel = viewModel,
        folderPickerLauncher = folderPickerLauncher,
        notificationPermissionLauncher = notificationPermissionLauncher,
        exportJsonLauncher = exportJsonLauncher,
        importJsonLauncher = importJsonLauncher,
        backupExportLauncher = backupExportLauncher,
        backupRestoreLauncher = backupRestoreLauncher,
        exportErrorLogLauncher = exportErrorLogLauncher,
        exportCutWavLauncher = exportCutWavLauncher,
        recordAudioPermissionLauncher = recordAudioPermissionLauncher,
        bluetoothMidiPermissionLauncher = bluetoothMidiPermissionLauncher,
        masterRecordFolderLauncher = masterRecordFolderLauncher,
        backupFolderPickerLauncher = backupFolderPickerLauncher,
        restoreFolderPickerLauncher = restoreFolderPickerLauncher,
        restoreRelinkMusicFolderLauncher = restoreRelinkMusicFolderLauncher,
        exportConfigZipLauncher = exportConfigZipLauncher,
        importConfigZipLauncher = importConfigZipLauncher
      )
    }

    CompositionLocalProvider(
      LocalContext provides localizedContext,
      LocalConfiguration provides localizedContext.resources.configuration
    ) {
      SoundPadProTheme(
        darkTheme = useDarkTheme,
        themeMode = uiState.settings.themeMode,
        useDynamicColor = uiState.settings.useDynamicColor,
        accentColorArgb = uiState.settings.appAccentColorArgb
      ) {
        RenderSoundPadScreen(
          uiState = uiState,
          canScheduleExactAlarms = canScheduleExactAlarms,
          actions = actions
        )
      }
    }
  }

  private fun handlePadTapped(
    pad: SoundPad,
    isEditMode: Boolean,
    notificationPermissionLauncher: ActivityResultLauncher<String>
  ) {
    if (!hasRequestedNotificationPermission && !isEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      hasRequestedNotificationPermission = true
      val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
      if (!granted) {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    }
    viewModel.onPadTapped(pad)
  }

  private fun handleStartBleMidiScan(
    bluetoothMidiPermissionLauncher: ActivityResultLauncher<Array<String>>
  ) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val scanGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_SCAN
      ) == PackageManager.PERMISSION_GRANTED
      val connectGranted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.BLUETOOTH_CONNECT
      ) == PackageManager.PERMISSION_GRANTED
      if (scanGranted && connectGranted) {
        viewModel.startBleMidiScan()
      } else {
        bluetoothMidiPermissionLauncher.launch(
          arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
          )
        )
      }
    } else {
      viewModel.startBleMidiScan()
    }
  }

  private fun handleOpenVoiceRecorder(
    recordAudioPermissionLauncher: ActivityResultLauncher<String>
  ) {
    val granted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    if (granted) {
      viewModel.openVoiceRecordSheet()
    } else {
      recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  private class MainScreenActions(
    private val activity: MainActivity,
    private val viewModel: SoundPadViewModel,
    private val folderPickerLauncher: ActivityResultLauncher<Uri?>,
    private val notificationPermissionLauncher: ActivityResultLauncher<String>,
    private val exportJsonLauncher: ActivityResultLauncher<String>,
    private val importJsonLauncher: ActivityResultLauncher<Array<String>>,
    private val backupExportLauncher: ActivityResultLauncher<String>,
    private val backupRestoreLauncher: ActivityResultLauncher<Array<String>>,
    private val exportErrorLogLauncher: ActivityResultLauncher<String>,
    private val exportCutWavLauncher: ActivityResultLauncher<String>,
    private val recordAudioPermissionLauncher: ActivityResultLauncher<String>,
    private val bluetoothMidiPermissionLauncher: ActivityResultLauncher<Array<String>>,
    private val masterRecordFolderLauncher: ActivityResultLauncher<Uri?>,
    private val backupFolderPickerLauncher: ActivityResultLauncher<Uri?>,
    private val restoreFolderPickerLauncher: ActivityResultLauncher<Uri?>,
    private val restoreRelinkMusicFolderLauncher: ActivityResultLauncher<Uri?>,
    private val exportConfigZipLauncher: ActivityResultLauncher<String>,
    private val importConfigZipLauncher: ActivityResultLauncher<Array<String>>
  ) {
    val onPickFolderClick: () -> Unit = { folderPickerLauncher.launch(null) }
    val onPickBackupFolderClick: () -> Unit = {
      backupFolderPickerLauncher.launch(SafPersistentConfigStorage.buildSuggestedBackupInitialUri())
    }
    val onInspectRestoreFolderClick: () -> Unit = {
      restoreFolderPickerLauncher.launch(SafPersistentConfigStorage.buildSuggestedBackupInitialUri())
    }
    val onPickRelinkMusicFolder: () -> Unit = {
      restoreRelinkMusicFolderLauncher.launch(null)
    }
    val onExportZipClick: () -> Unit = {
      val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
      exportConfigZipLauncher.launch("SoundPadPro_Backup_$stamp.zip")
    }
    val onImportZipClick: () -> Unit = {
      importConfigZipLauncher.launch(
        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")
      )
    }
    val onShareZipClick: () -> Unit = {
      viewModel.prepareAndShareConfigZip { chooserIntent ->
        activity.startActivity(chooserIntent)
      }
    }
    val onLoadDemoTracks: () -> Unit = { viewModel.loadDemoTracks(showBanner = true) }
    val onOpenTrackCustomizerDefault: () -> Unit = { viewModel.openTrackCustomizer() }
    val onPadCustomizeClick: (SoundPad) -> Unit = { pad -> viewModel.openTrackCustomizer(pad.id) }
    val onOpenVoiceRecorder: () -> Unit = { activity.handleOpenVoiceRecorder(recordAudioPermissionLauncher) }
    val onExportCutToFile: (SoundPad, Long, Long) -> Unit = { pad, startMs, endMs ->
      activity.pendingCutExport = Triple(pad, startMs, endMs)
      val safeName = pad.displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "cut_audio" }
      exportCutWavLauncher.launch("${safeName}_cut.wav")
    }
    val onExportProfileJson: (com.example.domain.model.PadProfile) -> Unit = { profile ->
      activity.pendingExportProfileId = profile.id
      val safeName = profile.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "soundpad_profile" }
      exportJsonLauncher.launch("$safeName.json")
    }
    val onImportProfileJson: () -> Unit = {
      importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }
    val onStartBleMidiScan: () -> Unit = { activity.handleStartBleMidiScan(bluetoothMidiPermissionLauncher) }
    val onBackupAllData: () -> Unit = { backupExportLauncher.launch("soundpad_pro_backup.json") }
    val onRestoreAllData: () -> Unit = {
      backupRestoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }
    val onExportErrorLogs: () -> Unit = { exportErrorLogLauncher.launch("soundpad_error_log.txt") }
    val onBulkMoveSelectedToBank: (Int?) -> Unit = { bankKey ->
      viewModel.bulkMoveSelectedPadsToBank(bankKey ?: -1)
    }
    val onFixErrorLog: (com.example.domain.model.AppErrorLog) -> Unit = { log ->
      if (log.errorType == PadErrorType.MISSING_PERMISSION) {
        folderPickerLauncher.launch(null)
      } else {
        viewModel.rescanCurrentFolder()
      }
    }
    val onRequestRecordAudioPermission: () -> Unit = {
      recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    val onRequestBleMidiPermissions: () -> Unit = {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        bluetoothMidiPermissionLauncher.launch(
          arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
          )
        )
      }
    }
    val onRequestExactAlarmPermission: () -> Unit = {
      activity.startActivity(PadSchedulerManager.buildExactAlarmPermissionIntent(activity))
    }
    val onPickMasterRecordFolder: () -> Unit = { masterRecordFolderLauncher.launch(null) }
    fun onPadTapped(pad: SoundPad, isEditMode: Boolean) {
      activity.handlePadTapped(pad, isEditMode, notificationPermissionLauncher)
    }

    val onHapticIntensityChanged = viewModel::setHapticIntensity
    val onSwipePadVolumeToggle = viewModel::setSwipePadVolumeEnabled
    val onDoubleTapToStopToggle = viewModel::setDoubleTapToStopEnabled
    val onHasCompletedOnboardingToggle = viewModel::setHasCompletedOnboarding
    val onShowFeatureTooltipsToggle = viewModel::setShowFeatureTooltips
    val onAppLanguageChanged = viewModel::setAppLanguage
    val onOpenMasterRecorder = viewModel::openMasterRecordSheet
    val onCloseMasterRecorder = viewModel::closeMasterRecordSheet
    val onSelectMasterRecordFormat = viewModel::setMasterRecordFormat
    val onSelectMasterRecordSampleRate = viewModel::setMasterRecordSampleRate
    val onToggleMasterRecordAutoAddPad = viewModel::setMasterRecordAutoAddAsPad
    val onStartMasterRecording = viewModel::startMasterRecording
    val onStopAndSaveMasterRecording = viewModel::stopAndSaveMasterRecording
    val onOpenScheduler = viewModel::openSchedulerSheet
    val onCloseScheduler = viewModel::closeSchedulerSheet
    val onSaveScheduledTask = viewModel::saveScheduledTask
    val onToggleScheduledTaskEnabled = viewModel::toggleScheduledTaskEnabled
    val onDeleteScheduledTask = viewModel::deleteScheduledTask
    val onTestTriggerScheduledTask = viewModel::testTriggerScheduledTask
    val onOpenSequencer = viewModel::openSequencerSheet
    val onCloseSequencer = viewModel::closeSequencerSheet
    val onSaveSequencerPlaylist = viewModel::saveSequencerPlaylist
    val onDeleteSequencerPlaylist = viewModel::deleteSequencerPlaylist
    val onPlaySequencerPlaylist = viewModel::startSequencerPlaylist
    val onToggleSequencerPlayPause = viewModel::toggleSequencerPlayPause
    val onSequencerNext = viewModel::sequencerNext
    val onSequencerPrev = viewModel::sequencerPrev
    val onStopSequencer = viewModel::stopSequencer
    val onArmSequencerMidiLearn = viewModel::armMidiLearnForSequencer
    val onClearSequencerMidiTrigger = viewModel::clearSequencerMidiNote
    val onArmMidiLearnForBankPc = viewModel::armMidiLearnForBankPc
    val onClearMidiPcMappingForBank = viewModel::clearMidiPcMappingForBank
    val onDefaultPadColorModeChanged = viewModel::setDefaultPadColorMode
    val onDefaultPadColorArgbChanged = viewModel::setDefaultPadColorArgb
    val onResetAllPadsToGray = viewModel::resetAllPadsToGray
    val onSetFocusMode = viewModel::setFocusMode
    val onToggleFocusMode = viewModel::toggleFocusMode
    val onStartInFocusModeToggle = viewModel::setStartInFocusMode
    val onFocusCornerPanicToggle = viewModel::setFocusCornerPanicEnabled
    val onArmMidiLearnForFocusMode = viewModel::armMidiLearnForFocusMode
    val onClearFocusModeMidiNote = viewModel::clearFocusModeMidiNote
    val onConsumeBankSwitchRequest = viewModel::consumeBankSwitchRequest
    val onDismissBackupPrompt = viewModel::dismissBackupFolderPrompt
    val onSyncBackupNow = viewModel::triggerManualBackupSync
    val onSetAutoSyncEnabled = viewModel::setPersistentAutoSyncEnabled
    val onSetIncludeWaveforms = viewModel::setPersistentIncludeWaveforms
    val onOpenSnapshotPickerClick = viewModel::openSnapshotPicker
    val onCloseSnapshotPicker = viewModel::closeSnapshotPicker
    val onSelectSnapshot: (SnapshotFileInfo) -> Unit = viewModel::inspectSnapshotBackup
    val onConfirmApplyRestore: (RestoreSelectionOptions) -> Unit = viewModel::confirmApplyRestore
    val onDismissRestorePreview = viewModel::dismissRestorePreview
    val onDeleteAllConfig: (Boolean) -> Unit = viewModel::deleteAllConfig
  }

  @Composable
  private fun RenderSoundPadScreen(
    uiState: com.example.ui.SoundPadUiState,
    canScheduleExactAlarms: Boolean,
    actions: MainScreenActions
  ) {
    SoundPadScreen(
      uiState = uiState,
      onPickFolderClick = actions.onPickFolderClick,
      onRescanClick = viewModel::rescanCurrentFolder,
      onToggleSearch = viewModel::toggleSearchVisibility,
      onSearchQueryChange = viewModel::setSearchQuery,
      onPlayModeSelected = viewModel::setPlayMode,
      onGridConfigChanged = viewModel::setGridConfig,
      onThemeModeChanged = viewModel::setThemeMode,
      onAppAccentColorChanged = viewModel::setAppAccentColor,
      onDefaultPadShapeChanged = viewModel::setDefaultPadShape,
      onDefaultCornerGlowChanged = viewModel::setDefaultCornerGlow,
      onBottomBarVisibleChanged = viewModel::setBottomBarVisible,
      onLoadDemoTracks = actions.onLoadDemoTracks,
      onExportDemoToMusicFolder = viewModel::exportDemoTracksToMusicFolder,
      onToggleCrossfade = viewModel::toggleGlobalCrossfade,
      onCrossfadeDurationChange = viewModel::setCrossfadeDurationMs,
      onCrossfadeCurveChange = viewModel::setCrossfadeCurve,
      onGlobalFadeInChange = viewModel::setGlobalFadeInMs,
      onGlobalFadeOutChange = viewModel::setGlobalFadeOutMs,
      onGlobalFadeCurveChange = viewModel::setGlobalFadeCurve,
      onToggleFullscreen = viewModel::toggleFullscreen,
      onMasterVolumeChanged = viewModel::setMasterVolume,
      onPreloadToggle = viewModel::setPreloadShortAudio,
      onAudioEffectsToggle = viewModel::setAudioEffectsEnabled,
      onVisualEffectsToggle = viewModel::setVisualEffectsEnabled,
      onKeepScreenOnToggle = viewModel::setKeepScreenOn,
      onToggleEditMode = viewModel::toggleEditMode,
      onPadTapped = { pad -> actions.onPadTapped(pad, uiState.isEditMode) },
      onPadCustomizeClick = actions.onPadCustomizeClick,
      onOpenTrackCustomizer = actions.onOpenTrackCustomizerDefault,
      onSelectEditingPad = viewModel::selectEditingPad,
      onPadHoldStart = viewModel::onPadHoldStart,
      onPadHoldRelease = viewModel::onPadHoldRelease,
      onSwapPads = viewModel::swapPadPositions,
      onMovePadToSlot = viewModel::movePadToSlot,
      onBulkMoveSelectedToSlot = viewModel::bulkMoveSelectedPadsToSlot,
      onDragDropConflictModeChanged = viewModel::setDragDropConflictMode,
      onCompactBankSlots = viewModel::compactBankSlots,
      onSortBankAlphabetically = viewModel::sortBankAlphabetically,
      onRestoreInitialPositions = viewModel::restoreInitialPositions,
      onSavePadAppearance = viewModel::updatePadAppearance,
      onApplyStyleToAllPads = viewModel::applyVisualStyleToAllPads,
      onUpdatePadSettings = viewModel::updatePadSettings,
      onOpenAudioEditor = viewModel::openAudioEditor,
      onCloseAudioEditor = viewModel::closeAudioEditor,
      onStartEditorPreview = viewModel::startEditorPreview,
      onStopEditorPreview = viewModel::stopEditorPreview,
      onSaveNonDestructiveTrim = viewModel::saveNonDestructiveTrim,
      onExportCutAsNewPad = viewModel::exportCutAsNewPad,
      onExportCutToFile = actions.onExportCutToFile,
      onOpenVoiceRecorder = actions.onOpenVoiceRecorder,
      onCloseVoiceRecorder = viewModel::closeVoiceRecordSheet,
      onStartVoiceRecording = viewModel::startVoiceRecording,
      onStopAndSaveVoiceRecording = viewModel::stopAndSaveVoiceRecording,
      onCancelVoiceRecording = viewModel::cancelVoiceRecording,
      onDuplicatePad = viewModel::duplicatePadForDifferentSettings,
      onDeletePad = viewModel::deletePadFromBoard,
      onClosePadEditor = viewModel::closePadEditor,
      onSaveNewProfile = viewModel::saveCurrentAsNewProfile,
      onLoadProfile = viewModel::loadProfile,
      onDuplicateProfile = viewModel::duplicateProfile,
      onDeleteProfile = viewModel::deleteProfile,
      onExportProfileJson = actions.onExportProfileJson,
      onImportProfileJson = actions.onImportProfileJson,
      onToggleMidiLearnMode = viewModel::toggleMidiLearnMode,
      onArmMidiLearnForPad = viewModel::armMidiLearnForPad,
      onClearPadMidiNote = viewModel::clearPadMidiNote,
      onArmMidiLearnForCc = viewModel::armMidiLearnForCc,
      onClearMidiCcMapping = viewModel::clearMidiCcMapping,
      onCancelMidiLearn = viewModel::cancelMidiLearn,
      onRefreshMidiDevices = viewModel::refreshMidiDevices,
      onStartBleMidiScan = actions.onStartBleMidiScan,
      onStopBleMidiScan = viewModel::stopBleMidiScan,
      onConnectBleMidiDevice = viewModel::connectBleMidiDevice,
      onToggleBleAutoReconnect = viewModel::setBleAutoReconnectEnabled,
      onTriggerBleReconnectNow = viewModel::triggerBleReconnectNow,
      onForgetSavedBleDevice = viewModel::forgetSavedBleMidiDevice,
      onStopAll = viewModel::stopAll,
      onFadeOutAll = viewModel::fadeOutAll,
      onToggleLockMode = viewModel::toggleLockMode,
      onUndoEdit = viewModel::undoEdit,
      onRedoEdit = viewModel::redoEdit,
      onToggleFavoritesFilter = viewModel::toggleFavoritesFilter,
      onSelectCategoryTagFilter = viewModel::selectCategoryTagFilter,
      onTogglePadFavorite = viewModel::togglePadFavorite,
      onSetPadCategoryTag = viewModel::setPadCategoryTag,
      onRenameBank = viewModel::renameBank,
      onAddBank = viewModel::addNewBank,
      onRefreshOutputDevices = viewModel::refreshOutputDevices,
      onSelectMainOutputDevice = viewModel::selectMainOutputDevice,
      onSelectCueOutputDevice = viewModel::selectCueOutputDevice,
      onCueVolumeChange = viewModel::setCueVolume,
      onAudioBufferSizeChange = viewModel::setAudioBufferSizeMs,
      onRunLatencyTest = viewModel::runAudioLatencyTest,
      onToggleCuePreview = viewModel::toggleCuePreview,
      onMasterLimiterToggle = viewModel::setMasterLimiterEnabled,
      onDuckingToggle = viewModel::setDuckingEnabled,
      onDuckingAttenuationChange = viewModel::setDuckingAttenuation,
      onAutoNormalizeToggle = viewModel::setAutoNormalizeEnabled,
      onAnalyzeLoudnessAll = viewModel::analyzeLoudnessForAllPads,
      onBackupAllData = actions.onBackupAllData,
      onRestoreAllData = actions.onRestoreAllData,
      onDuplicatePadToBank = viewModel::duplicatePadToBank,
      onMoveBankOrder = viewModel::moveBankOrder,
      onRescanBank = viewModel::rescanBank,
      onMasterPanChanged = viewModel::setMasterPan,
      onMiniWaveformToggle = viewModel::setMiniWaveformEnabled,
      onMasterEqEnabledChange = viewModel::setMasterEqEnabled,
      onMasterEqPresetSelect = viewModel::setMasterEqPreset,
      onMasterEqBandChange = viewModel::setMasterEqBand,
      onResetMasterEq = viewModel::resetMasterEq,
      onClearErrorLogs = viewModel::clearErrorLogs,
      onExportErrorLogs = actions.onExportErrorLogs,
      onDismissPendingCrashDialog = viewModel::dismissPendingCrashDialog,
      onDismissPadDiagnostic = viewModel::dismissPadDiagnostic,
      onDismissNewFilesNotice = viewModel::dismissNewFilesNotice,
      onDismissStatus = viewModel::clearStatusMessage,
      onDismissError = viewModel::clearError,
      onUseDynamicColorChanged = viewModel::setUseDynamicColor,
      onPadGridSpacingChanged = viewModel::setPadGridSpacingDp,
      onToggleGlobalCue = viewModel::toggleGlobalCue,
      onLockUnlockHint = viewModel::showLockUnlockHint,
      onDoubleTapStop = viewModel::stopSinglePad,
      onSwipeVolumeChange = viewModel::updatePadVolumeLive,
      onToggleMultiSelectMode = viewModel::toggleMultiSelectMode,
      onTogglePadSelection = viewModel::togglePadSelection,
      onSelectAllPadsInBank = viewModel::selectAllPadsInBank,
      onClearPadSelection = viewModel::clearPadSelection,
      onBulkDeleteSelected = viewModel::bulkDeleteSelectedPads,
      onBulkColorSelected = viewModel::bulkSetColorSelectedPads,
      onBulkMoveSelectedToBank = actions.onBulkMoveSelectedToBank,
      onBulkDuplicateSelected = viewModel::bulkDuplicateSelectedPads,
      onDeleteErrorLog = viewModel::deleteErrorLogById,
      onFixErrorLog = actions.onFixErrorLog,
      onHapticIntensityChanged = actions.onHapticIntensityChanged,
      onSwipePadVolumeToggle = actions.onSwipePadVolumeToggle,
      onDoubleTapToStopToggle = actions.onDoubleTapToStopToggle,
      onHasCompletedOnboardingToggle = actions.onHasCompletedOnboardingToggle,
      onShowFeatureTooltipsToggle = actions.onShowFeatureTooltipsToggle,
      onAppLanguageChanged = actions.onAppLanguageChanged,
      onRequestRecordAudioPermission = actions.onRequestRecordAudioPermission,
      onRequestBleMidiPermissions = actions.onRequestBleMidiPermissions,
      canScheduleExactAlarms = canScheduleExactAlarms,
      onRequestExactAlarmPermission = actions.onRequestExactAlarmPermission,
      onOpenMasterRecorder = actions.onOpenMasterRecorder,
      onCloseMasterRecorder = actions.onCloseMasterRecorder,
      onSelectMasterRecordFormat = actions.onSelectMasterRecordFormat,
      onSelectMasterRecordSampleRate = actions.onSelectMasterRecordSampleRate,
      onPickMasterRecordFolder = actions.onPickMasterRecordFolder,
      onToggleMasterRecordAutoAddPad = actions.onToggleMasterRecordAutoAddPad,
      onStartMasterRecording = actions.onStartMasterRecording,
      onStopAndSaveMasterRecording = actions.onStopAndSaveMasterRecording,
      onOpenScheduler = actions.onOpenScheduler,
      onCloseScheduler = actions.onCloseScheduler,
      onSaveScheduledTask = actions.onSaveScheduledTask,
      onToggleScheduledTaskEnabled = actions.onToggleScheduledTaskEnabled,
      onDeleteScheduledTask = actions.onDeleteScheduledTask,
      onTestTriggerScheduledTask = actions.onTestTriggerScheduledTask,
      onOpenSequencer = actions.onOpenSequencer,
      onCloseSequencer = actions.onCloseSequencer,
      onSaveSequencerPlaylist = actions.onSaveSequencerPlaylist,
      onDeleteSequencerPlaylist = actions.onDeleteSequencerPlaylist,
      onPlaySequencerPlaylist = actions.onPlaySequencerPlaylist,
      onToggleSequencerPlayPause = actions.onToggleSequencerPlayPause,
      onSequencerNext = actions.onSequencerNext,
      onSequencerPrev = actions.onSequencerPrev,
      onStopSequencer = actions.onStopSequencer,
      onArmSequencerMidiLearn = actions.onArmSequencerMidiLearn,
      onClearSequencerMidiTrigger = actions.onClearSequencerMidiTrigger,
      onArmMidiLearnForBankPc = actions.onArmMidiLearnForBankPc,
      onClearMidiPcMappingForBank = actions.onClearMidiPcMappingForBank,
      onDefaultPadColorModeChanged = actions.onDefaultPadColorModeChanged,
      onDefaultPadColorArgbChanged = actions.onDefaultPadColorArgbChanged,
      onResetAllPadsToGray = actions.onResetAllPadsToGray,
      onSetFocusMode = actions.onSetFocusMode,
      onToggleFocusMode = actions.onToggleFocusMode,
      onStartInFocusModeToggle = actions.onStartInFocusModeToggle,
      onFocusCornerPanicToggle = actions.onFocusCornerPanicToggle,
      onArmMidiLearnForFocusMode = actions.onArmMidiLearnForFocusMode,
      onClearFocusModeMidiNote = actions.onClearFocusModeMidiNote,
      onConsumeBankSwitchRequest = actions.onConsumeBankSwitchRequest,
      onPickBackupFolderClick = actions.onPickBackupFolderClick,
      onDismissBackupPrompt = actions.onDismissBackupPrompt,
      onSyncBackupNow = actions.onSyncBackupNow,
      onSetAutoSyncEnabled = actions.onSetAutoSyncEnabled,
      onSetIncludeWaveforms = actions.onSetIncludeWaveforms,
      onInspectRestoreFolderClick = actions.onInspectRestoreFolderClick,
      onPickRelinkMusicFolder = actions.onPickRelinkMusicFolder,
      onOpenSnapshotPickerClick = actions.onOpenSnapshotPickerClick,
      onCloseSnapshotPicker = actions.onCloseSnapshotPicker,
      onSelectSnapshot = actions.onSelectSnapshot,
      onConfirmApplyRestore = actions.onConfirmApplyRestore,
      onDismissRestorePreview = actions.onDismissRestorePreview,
      onExportZipClick = actions.onExportZipClick,
      onImportZipClick = actions.onImportZipClick,
      onShareZipClick = actions.onShareZipClick,
      onDeleteAllConfig = actions.onDeleteAllConfig
    )
  }
}

@Composable
private fun rememberLocalizedContext(language: AppLanguage): Context {
  val base = LocalContext.current
  return remember(base, language) {
    if (language == AppLanguage.SYSTEM || language.tag.isBlank()) {
      base
    } else {
      val locale = Locale.forLanguageTag(language.tag)
      val config = Configuration(base.resources.configuration)
      config.setLocale(locale)
      base.createConfigurationContext(config)
    }
  }
}
