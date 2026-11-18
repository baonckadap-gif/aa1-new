package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import android.content.Context
import android.content.res.Configuration
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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
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

      var pendingExportProfileId by remember { mutableStateOf<String?>(null) }

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

      var pendingCutExport by remember { mutableStateOf<Triple<SoundPad, Long, Long>?>(null) }

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
        mutableStateOf(PadSchedulerManager.canScheduleExactAlarms(this@MainActivity))
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
        SoundPadScreen(
          uiState = uiState,
          onPickFolderClick = { folderPickerLauncher.launch(null) },
          onRescanClick = { viewModel.rescanCurrentFolder() },
          onToggleSearch = { viewModel.toggleSearchVisibility() },
          onSearchQueryChange = { query -> viewModel.setSearchQuery(query) },
          onPlayModeSelected = { mode -> viewModel.setPlayMode(mode) },
          onGridConfigChanged = { config -> viewModel.setGridConfig(config) },
          onThemeModeChanged = { mode -> viewModel.setThemeMode(mode) },
          onAppAccentColorChanged = { argb -> viewModel.setAppAccentColor(argb) },
          onDefaultPadShapeChanged = { style -> viewModel.setDefaultPadShape(style) },
          onDefaultCornerGlowChanged = { argb -> viewModel.setDefaultCornerGlow(argb) },
          onBottomBarVisibleChanged = { visible -> viewModel.setBottomBarVisible(visible) },
          onLoadDemoTracks = { viewModel.loadDemoTracks(showBanner = true) },
          onExportDemoToMusicFolder = { viewModel.exportDemoTracksToMusicFolder() },
          onToggleCrossfade = { viewModel.toggleGlobalCrossfade() },
          onCrossfadeDurationChange = { ms -> viewModel.setCrossfadeDurationMs(ms) },
          onCrossfadeCurveChange = { curve -> viewModel.setCrossfadeCurve(curve) },
          onGlobalFadeInChange = { ms -> viewModel.setGlobalFadeInMs(ms) },
          onGlobalFadeOutChange = { ms -> viewModel.setGlobalFadeOutMs(ms) },
          onGlobalFadeCurveChange = { curve -> viewModel.setGlobalFadeCurve(curve) },
          onToggleFullscreen = { viewModel.toggleFullscreen() },
          onMasterVolumeChanged = { volume -> viewModel.setMasterVolume(volume) },
          onPreloadToggle = { enabled -> viewModel.setPreloadShortAudio(enabled) },
          onKeepScreenOnToggle = { enabled -> viewModel.setKeepScreenOn(enabled) },
          onToggleEditMode = { viewModel.toggleEditMode() },
          onPadTapped = { pad ->
            if (!uiState.isEditMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS
              ) == PackageManager.PERMISSION_GRANTED
              if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
              }
            }
            viewModel.onPadTapped(pad)
          },
          onPadCustomizeClick = { pad -> viewModel.openTrackCustomizer(pad.id) },
          onOpenTrackCustomizer = { viewModel.openTrackCustomizer() },
          onSelectEditingPad = { padId -> viewModel.selectEditingPad(padId) },
          onPadHoldStart = { pad -> viewModel.onPadHoldStart(pad) },
          onPadHoldRelease = { pad -> viewModel.onPadHoldRelease(pad) },
          onSwapPads = { fromId, toId -> viewModel.swapPadPositions(fromId, toId) },
          onSavePadAppearance = { padId, config ->
            viewModel.updatePadAppearance(padId, config)
          },
          onApplyStyleToAllPads = { config ->
            viewModel.applyVisualStyleToAllPads(config)
          },
          onUpdatePadSettings = { padId, padSettings ->
            viewModel.updatePadSettings(padId, padSettings)
          },
          onOpenAudioEditor = { pad -> viewModel.openAudioEditor(pad) },
          onCloseAudioEditor = { viewModel.closeAudioEditor() },
          onStartEditorPreview = { uri, startMs, endMs ->
            viewModel.startEditorPreview(uri, startMs, endMs)
          },
          onStopEditorPreview = { viewModel.stopEditorPreview() },
          onSaveNonDestructiveTrim = { padId, startMs, endMs ->
            viewModel.saveNonDestructiveTrim(padId, startMs, endMs)
          },
          onExportCutAsNewPad = { pad, startMs, endMs ->
            viewModel.exportCutAsNewPad(pad, startMs, endMs)
          },
          onExportCutToFile = { pad, startMs, endMs ->
            pendingCutExport = Triple(pad, startMs, endMs)
            val safeName = pad.displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "cut_audio" }
            exportCutWavLauncher.launch("${safeName}_cut.wav")
          },
          onOpenVoiceRecorder = {
            val granted = ContextCompat.checkSelfPermission(
              this@MainActivity,
              Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
              viewModel.openVoiceRecordSheet()
            } else {
              recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          onCloseVoiceRecorder = { viewModel.closeVoiceRecordSheet() },
          onStartVoiceRecording = { viewModel.startVoiceRecording() },
          onStopAndSaveVoiceRecording = { viewModel.stopAndSaveVoiceRecording() },
          onCancelVoiceRecording = { viewModel.cancelVoiceRecording() },
          onDuplicatePad = { padId -> viewModel.duplicatePadForDifferentSettings(padId) },
          onDeletePad = { padId -> viewModel.deletePadFromBoard(padId) },
          onClosePadEditor = { viewModel.closePadEditor() },
          onSaveNewProfile = { name -> viewModel.saveCurrentAsNewProfile(name) },
          onLoadProfile = { id -> viewModel.loadProfile(id) },
          onDuplicateProfile = { id -> viewModel.duplicateProfile(id) },
          onDeleteProfile = { id -> viewModel.deleteProfile(id) },
          onExportProfileJson = { profile ->
            pendingExportProfileId = profile.id
            val safeName = profile.name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "soundpad_profile" }
            exportJsonLauncher.launch("$safeName.json")
          },
          onImportProfileJson = {
            importJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
          },
          onToggleMidiLearnMode = { viewModel.toggleMidiLearnMode() },
          onArmMidiLearnForPad = { pad -> viewModel.armMidiLearnForPad(pad) },
          onClearPadMidiNote = { padId -> viewModel.clearPadMidiNote(padId) },
          onArmMidiLearnForCc = { target -> viewModel.armMidiLearnForCc(target) },
          onClearMidiCcMapping = { target -> viewModel.clearMidiCcMapping(target) },
          onCancelMidiLearn = { viewModel.cancelMidiLearn() },
          onRefreshMidiDevices = { viewModel.refreshMidiDevices() },
          onStartBleMidiScan = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              val scanGranted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.BLUETOOTH_SCAN
              ) == PackageManager.PERMISSION_GRANTED
              val connectGranted = ContextCompat.checkSelfPermission(
                this@MainActivity,
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
          },
          onStopBleMidiScan = { viewModel.stopBleMidiScan() },
          onConnectBleMidiDevice = { address -> viewModel.connectBleMidiDevice(address) },
          onStopAll = { viewModel.stopAll() },
          onFadeOutAll = { viewModel.fadeOutAll() },
          onToggleLockMode = { viewModel.toggleLockMode() },
          onUndoEdit = { viewModel.undoEdit() },
          onRedoEdit = { viewModel.redoEdit() },
          onToggleFavoritesFilter = { viewModel.toggleFavoritesFilter() },
          onSelectCategoryTagFilter = { tag -> viewModel.selectCategoryTagFilter(tag) },
          onTogglePadFavorite = { padId -> viewModel.togglePadFavorite(padId) },
          onSetPadCategoryTag = { padId, tag -> viewModel.setPadCategoryTag(padId, tag) },
          onRenameBank = { bankIdx, name -> viewModel.renameBank(bankIdx, name) },
          onRefreshOutputDevices = { viewModel.refreshOutputDevices() },
          onSelectMainOutputDevice = { id -> viewModel.selectMainOutputDevice(id) },
          onSelectCueOutputDevice = { id -> viewModel.selectCueOutputDevice(id) },
          onToggleCuePreview = { pad -> viewModel.toggleCuePreview(pad) },
          onMasterLimiterToggle = { enabled -> viewModel.setMasterLimiterEnabled(enabled) },
          onDuckingToggle = { enabled -> viewModel.setDuckingEnabled(enabled) },
          onDuckingAttenuationChange = { atten -> viewModel.setDuckingAttenuation(atten) },
          onAutoNormalizeToggle = { enabled -> viewModel.setAutoNormalizeEnabled(enabled) },
          onAnalyzeLoudnessAll = { viewModel.analyzeLoudnessForAllPads() },
          onBackupAllData = {
            backupExportLauncher.launch("soundpad_pro_backup.json")
          },
          onRestoreAllData = {
            backupRestoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
          },
          onDuplicatePadToBank = { padId, targetBank ->
            viewModel.duplicatePadToBank(padId, targetBank)
          },
          onMoveBankOrder = { bankKey, delta ->
            viewModel.moveBankOrder(bankKey, delta)
          },
          onRescanBank = { bankKey ->
            viewModel.rescanBank(bankKey)
          },
          onMasterPanChanged = { pan ->
            viewModel.setMasterPan(pan)
          },
          onMiniWaveformToggle = { enabled ->
            viewModel.setMiniWaveformEnabled(enabled)
          },
          onMasterEqEnabledChange = { enabled ->
            viewModel.setMasterEqEnabled(enabled)
          },
          onMasterEqPresetSelect = { preset ->
            viewModel.setMasterEqPreset(preset)
          },
          onMasterEqBandChange = { idx, db ->
            viewModel.setMasterEqBand(idx, db)
          },
          onResetMasterEq = {
            viewModel.resetMasterEq()
          },
          onClearErrorLogs = {
            viewModel.clearErrorLogs()
          },
          onExportErrorLogs = {
            exportErrorLogLauncher.launch("soundpad_error_log.txt")
          },
          onDismissPendingCrashDialog = {
            viewModel.dismissPendingCrashDialog()
          },
          onDismissPadDiagnostic = {
            viewModel.dismissPadDiagnostic()
          },
          onDismissNewFilesNotice = { viewModel.dismissNewFilesNotice() },
          onDismissStatus = { viewModel.clearStatusMessage() },
          onDismissError = { viewModel.clearError() },
          onUseDynamicColorChanged = { enabled -> viewModel.setUseDynamicColor(enabled) },
          onPadGridSpacingChanged = { dp -> viewModel.setPadGridSpacingDp(dp) },
          onToggleGlobalCue = { viewModel.toggleGlobalCue() },
          onLockUnlockHint = { viewModel.showLockUnlockHint() },
          onDoubleTapStop = { padId -> viewModel.stopSinglePad(padId) },
          onSwipeVolumeChange = { padId, volume, persist ->
            viewModel.updatePadVolumeLive(padId, volume, persist)
          },
          onToggleMultiSelectMode = { viewModel.toggleMultiSelectMode() },
          onTogglePadSelection = { padId -> viewModel.togglePadSelection(padId) },
          onSelectAllPadsInBank = { ids -> viewModel.selectAllPadsInBank(ids) },
          onClearPadSelection = { viewModel.clearPadSelection() },
          onBulkDeleteSelected = { viewModel.bulkDeleteSelectedPads() },
          onBulkColorSelected = { colorArgb -> viewModel.bulkSetColorSelectedPads(colorArgb) },
          onBulkMoveSelectedToBank = { bankKey -> viewModel.bulkMoveSelectedPadsToBank(bankKey ?: -1) },
          onBulkDuplicateSelected = { viewModel.bulkDuplicateSelectedPads() },
          onDeleteErrorLog = { logId -> viewModel.deleteErrorLogById(logId) },
          onFixErrorLog = { log ->
            if (log.errorType == PadErrorType.MISSING_PERMISSION) {
              folderPickerLauncher.launch(null)
            } else {
              viewModel.rescanCurrentFolder()
            }
          },
          onHapticIntensityChanged = { intensity -> viewModel.setHapticIntensity(intensity) },
          onSwipePadVolumeToggle = { enabled -> viewModel.setSwipePadVolumeEnabled(enabled) },
          onDoubleTapToStopToggle = { enabled -> viewModel.setDoubleTapToStopEnabled(enabled) },
          onHasCompletedOnboardingToggle = { completed -> viewModel.setHasCompletedOnboarding(completed) },
          onShowFeatureTooltipsToggle = { show -> viewModel.setShowFeatureTooltips(show) },
          onAppLanguageChanged = { language -> viewModel.setAppLanguage(language) },
          onRequestRecordAudioPermission = {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          },
          onRequestBleMidiPermissions = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              bluetoothMidiPermissionLauncher.launch(
                arrayOf(
                  Manifest.permission.BLUETOOTH_SCAN,
                  Manifest.permission.BLUETOOTH_CONNECT
                )
              )
            }
          },
          canScheduleExactAlarms = canScheduleExactAlarms,
          onRequestExactAlarmPermission = {
            startActivity(PadSchedulerManager.buildExactAlarmPermissionIntent(this@MainActivity))
          },
          onOpenMasterRecorder = { viewModel.openMasterRecordSheet() },
          onCloseMasterRecorder = { viewModel.closeMasterRecordSheet() },
          onSelectMasterRecordSampleRate = { rate -> viewModel.setMasterRecordSampleRate(rate) },
          onPickMasterRecordFolder = { masterRecordFolderLauncher.launch(null) },
          onToggleMasterRecordAutoAddPad = { autoAdd -> viewModel.setMasterRecordAutoAddAsPad(autoAdd) },
          onStartMasterRecording = { viewModel.startMasterRecording() },
          onStopAndSaveMasterRecording = { viewModel.stopAndSaveMasterRecording() },
          onOpenScheduler = { viewModel.openSchedulerSheet() },
          onCloseScheduler = { viewModel.closeSchedulerSheet() },
          onSaveScheduledTask = { task -> viewModel.saveScheduledTask(task) },
          onToggleScheduledTaskEnabled = { task, enabled ->
            viewModel.toggleScheduledTaskEnabled(task, enabled)
          },
          onDeleteScheduledTask = { taskId -> viewModel.deleteScheduledTask(taskId) },
          onTestTriggerScheduledTask = { task -> viewModel.testTriggerScheduledTask(task) },
          onOpenSequencer = { viewModel.openSequencerSheet() },
          onCloseSequencer = { viewModel.closeSequencerSheet() },
          onSaveSequencerPlaylist = { playlist -> viewModel.saveSequencerPlaylist(playlist) },
          onDeleteSequencerPlaylist = { playlistId -> viewModel.deleteSequencerPlaylist(playlistId) },
          onPlaySequencerPlaylist = { playlist, startIdx ->
            viewModel.startSequencerPlaylist(playlist, startIdx)
          },
          onToggleSequencerPlayPause = { playlist -> viewModel.toggleSequencerPlayPause(playlist) },
          onSequencerNext = { viewModel.sequencerNext() },
          onSequencerPrev = { viewModel.sequencerPrev() },
          onStopSequencer = { viewModel.stopSequencer() },
          onArmSequencerMidiLearn = { playlist -> viewModel.armMidiLearnForSequencer(playlist) },
          onClearSequencerMidiTrigger = { playlist -> viewModel.clearSequencerMidiNote(playlist) },
          onArmMidiLearnForBankPc = { bankIndex, label ->
            viewModel.armMidiLearnForBankPc(bankIndex, label)
          },
          onClearMidiPcMappingForBank = { bankIndex ->
            viewModel.clearMidiPcMappingForBank(bankIndex)
          },
          onConsumeBankSwitchRequest = { viewModel.consumeBankSwitchRequest() }
        )
      }
      }
    }
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
