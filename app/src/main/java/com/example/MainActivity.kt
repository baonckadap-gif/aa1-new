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
import com.example.domain.model.AppLanguage
import com.example.domain.model.PadErrorType
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.scheduler.PadSchedulerManager
import com.example.ui.SoundPadScreen
import com.example.ui.SoundPadViewModel
import com.example.ui.theme.SoundPadProTheme
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
        runCatching {
          contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
          )
        }
        viewModel.onFolderSelected(uri)
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

    LaunchedEffect(uiState.settings.keepScreenOn) {
      if (uiState.settings.keepScreenOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    }

    LaunchedEffect(uiState.settings.isFullscreen) {
      val controller = WindowCompat.getInsetsController(window, window.decorView)
      controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      if (uiState.settings.isFullscreen) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
      } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
      }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
      val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          canScheduleExactAlarms = PadSchedulerManager.canScheduleExactAlarms(this@MainActivity)
          viewModel.checkForNewFilesOnResume()
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
        masterRecordFolderLauncher = masterRecordFolderLauncher
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
    private val masterRecordFolderLauncher: ActivityResultLauncher<Uri?>
  ) {
    val onPickFolderClick: () -> Unit = { folderPickerLauncher.launch(null) }
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
      onHapticIntensityChanged = viewModel::setHapticIntensity,
      onSwipePadVolumeToggle = viewModel::setSwipePadVolumeEnabled,
      onDoubleTapToStopToggle = viewModel::setDoubleTapToStopEnabled,
      onHasCompletedOnboardingToggle = viewModel::setHasCompletedOnboarding,
      onShowFeatureTooltipsToggle = viewModel::setShowFeatureTooltips,
      onAppLanguageChanged = viewModel::setAppLanguage,
      onRequestRecordAudioPermission = actions.onRequestRecordAudioPermission,
      onRequestBleMidiPermissions = actions.onRequestBleMidiPermissions,
      canScheduleExactAlarms = canScheduleExactAlarms,
      onRequestExactAlarmPermission = actions.onRequestExactAlarmPermission,
      onOpenMasterRecorder = viewModel::openMasterRecordSheet,
      onCloseMasterRecorder = viewModel::closeMasterRecordSheet,
      onSelectMasterRecordSampleRate = viewModel::setMasterRecordSampleRate,
      onPickMasterRecordFolder = actions.onPickMasterRecordFolder,
      onToggleMasterRecordAutoAddPad = viewModel::setMasterRecordAutoAddAsPad,
      onStartMasterRecording = viewModel::startMasterRecording,
      onStopAndSaveMasterRecording = viewModel::stopAndSaveMasterRecording,
      onOpenScheduler = viewModel::openSchedulerSheet,
      onCloseScheduler = viewModel::closeSchedulerSheet,
      onSaveScheduledTask = viewModel::saveScheduledTask,
      onToggleScheduledTaskEnabled = viewModel::toggleScheduledTaskEnabled,
      onDeleteScheduledTask = viewModel::deleteScheduledTask,
      onTestTriggerScheduledTask = viewModel::testTriggerScheduledTask,
      onOpenSequencer = viewModel::openSequencerSheet,
      onCloseSequencer = viewModel::closeSequencerSheet,
      onSaveSequencerPlaylist = viewModel::saveSequencerPlaylist,
      onDeleteSequencerPlaylist = viewModel::deleteSequencerPlaylist,
      onPlaySequencerPlaylist = viewModel::startSequencerPlaylist,
      onToggleSequencerPlayPause = viewModel::toggleSequencerPlayPause,
      onSequencerNext = viewModel::sequencerNext,
      onSequencerPrev = viewModel::sequencerPrev,
      onStopSequencer = viewModel::stopSequencer,
      onArmSequencerMidiLearn = viewModel::armMidiLearnForSequencer,
      onClearSequencerMidiTrigger = viewModel::clearSequencerMidiNote,
      onArmMidiLearnForBankPc = viewModel::armMidiLearnForBankPc,
      onClearMidiPcMappingForBank = viewModel::clearMidiPcMappingForBank,
      onConsumeBankSwitchRequest = viewModel::consumeBankSwitchRequest
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
