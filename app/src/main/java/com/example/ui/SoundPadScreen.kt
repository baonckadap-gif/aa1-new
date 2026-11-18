package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.R
import com.example.domain.model.AppErrorLog
import com.example.domain.model.AppLanguage
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.GridConfig
import com.example.domain.model.HapticIntensity
import com.example.domain.model.MasterRecordSampleRate
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiLearnTarget
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadVisualConfig
import com.example.domain.model.PlayMode
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.AdaptiveWidthClass
import com.example.ui.components.AudioEditorSheet
import com.example.ui.components.AudioOutputSheet
import com.example.ui.components.BottomConsoleBar
import com.example.ui.components.ErrorLogSheet
import com.example.ui.components.GridSettingsSheet
import com.example.ui.components.HoldToUnlockControl
import com.example.ui.components.LandscapeBankRail
import com.example.ui.components.LandscapeMixerSidePanel
import com.example.ui.components.MasterRecordSheet
import com.example.ui.components.MidiControllerSheet
import com.example.ui.components.MiuiGuideDialog
import com.example.ui.components.OnboardingDialog
import com.example.ui.components.PadCell
import com.example.ui.components.PadDensity
import com.example.ui.components.PadSettingsSheet
import com.example.ui.components.ProfileManagerSheet
import com.example.ui.components.SchedulerSheet
import com.example.ui.components.SequencerSheet
import com.example.ui.components.TopControlBar
import com.example.ui.components.VoiceRecordSheet
import com.example.ui.components.performPadHaptic
import com.example.ui.components.resolveAdaptiveWidthClass
import com.example.ui.theme.SoundPadColorPalette
import com.example.ui.theme.SoundPadTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SoundPadScreen(
  uiState: SoundPadUiState,
  onPickFolderClick: () -> Unit,
  onRescanClick: () -> Unit,
  onToggleSearch: () -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onPlayModeSelected: (PlayMode) -> Unit,
  onGridConfigChanged: (GridConfig) -> Unit,
  onThemeModeChanged: (ThemeMode) -> Unit,
  onAppAccentColorChanged: (Long) -> Unit,
  onDefaultPadShapeChanged: (PadShapeStyle) -> Unit,
  onDefaultCornerGlowChanged: (Long) -> Unit,
  onBottomBarVisibleChanged: (Boolean) -> Unit,
  onLoadDemoTracks: () -> Unit,
  onExportDemoToMusicFolder: () -> Unit,
  onToggleCrossfade: () -> Unit,
  onCrossfadeDurationChange: (Long) -> Unit,
  onCrossfadeCurveChange: (FadeCurve) -> Unit,
  onGlobalFadeInChange: (Long) -> Unit,
  onGlobalFadeOutChange: (Long) -> Unit,
  onGlobalFadeCurveChange: (FadeCurve) -> Unit,
  onToggleFullscreen: () -> Unit,
  onMasterVolumeChanged: (Float) -> Unit,
  onPreloadToggle: (Boolean) -> Unit,
  onKeepScreenOnToggle: (Boolean) -> Unit,
  onToggleEditMode: () -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onOpenTrackCustomizer: () -> Unit,
  onSelectEditingPad: (String) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldRelease: (SoundPad) -> Unit,
  onSwapPads: (fromPadId: String, toPadId: String) -> Unit,
  onSavePadAppearance: (padId: String, config: PadVisualConfig) -> Unit,
  onApplyStyleToAllPads: (config: PadVisualConfig) -> Unit,
  onUpdatePadSettings: (padId: String, settings: PadSettings) -> Unit,
  onOpenAudioEditor: (SoundPad) -> Unit,
  onCloseAudioEditor: () -> Unit,
  onStartEditorPreview: (uriString: String, startMs: Long, endMs: Long) -> Unit,
  onStopEditorPreview: () -> Unit,
  onSaveNonDestructiveTrim: (padId: String, startMs: Long, endMs: Long) -> Unit,
  onExportCutAsNewPad: (pad: SoundPad, startMs: Long, endMs: Long) -> Unit,
  onExportCutToFile: (pad: SoundPad, startMs: Long, endMs: Long) -> Unit,
  onOpenVoiceRecorder: () -> Unit,
  onCloseVoiceRecorder: () -> Unit,
  onStartVoiceRecording: () -> Unit,
  onStopAndSaveVoiceRecording: () -> Unit,
  onCancelVoiceRecording: () -> Unit,
  onDuplicatePad: (String) -> Unit,
  onDeletePad: (String) -> Unit,
  onClosePadEditor: () -> Unit,
  onSaveNewProfile: (String) -> Unit,
  onLoadProfile: (String) -> Unit,
  onDuplicateProfile: (String) -> Unit,
  onDeleteProfile: (String) -> Unit,
  onExportProfileJson: (PadProfile) -> Unit,
  onImportProfileJson: () -> Unit,
  onToggleMidiLearnMode: () -> Unit,
  onArmMidiLearnForPad: (SoundPad) -> Unit,
  onClearPadMidiNote: (String) -> Unit,
  onArmMidiLearnForCc: (MidiCcTarget) -> Unit,
  onClearMidiCcMapping: (MidiCcTarget) -> Unit,
  onCancelMidiLearn: () -> Unit,
  onRefreshMidiDevices: () -> Unit,
  onStartBleMidiScan: () -> Unit,
  onStopBleMidiScan: () -> Unit,
  onConnectBleMidiDevice: (String) -> Unit,
  onStopAll: () -> Unit,
  onFadeOutAll: () -> Unit = {},
  onToggleLockMode: () -> Unit = {},
  onUndoEdit: () -> Unit = {},
  onRedoEdit: () -> Unit = {},
  onToggleFavoritesFilter: () -> Unit = {},
  onSelectCategoryTagFilter: (String?) -> Unit = {},
  onTogglePadFavorite: (String) -> Unit = {},
  onSetPadCategoryTag: (String, String) -> Unit = { _, _ -> },
  onRenameBank: (Int, String) -> Unit = { _, _ -> },
  onRefreshOutputDevices: () -> Unit = {},
  onSelectMainOutputDevice: (Int?) -> Unit = {},
  onSelectCueOutputDevice: (Int?) -> Unit = {},
  onToggleCuePreview: (SoundPad) -> Unit = {},
  onMasterLimiterToggle: (Boolean) -> Unit = {},
  onDuckingToggle: (Boolean) -> Unit = {},
  onDuckingAttenuationChange: (Float) -> Unit = {},
  onAutoNormalizeToggle: (Boolean) -> Unit = {},
  onAnalyzeLoudnessAll: () -> Unit = {},
  onBackupAllData: () -> Unit = {},
  onRestoreAllData: () -> Unit = {},
  onDuplicatePadToBank: (String, Int?) -> Unit = { _, _ -> },
  onMoveBankOrder: (Int, Int) -> Unit = { _, _ -> },
  onRescanBank: (Int) -> Unit = {},
  onMasterPanChanged: (Float) -> Unit = {},
  onMiniWaveformToggle: (Boolean) -> Unit = {},
  onMasterEqEnabledChange: (Boolean) -> Unit = {},
  onMasterEqPresetSelect: (EqPreset) -> Unit = {},
  onMasterEqBandChange: (Int, Float) -> Unit = { _, _ -> },
  onResetMasterEq: () -> Unit = {},
  onClearErrorLogs: () -> Unit = {},
  onExportErrorLogs: () -> Unit = {},
  onDismissPendingCrashDialog: () -> Unit = {},
  onDismissPadDiagnostic: () -> Unit = {},
  onDismissNewFilesNotice: () -> Unit,
  onDismissStatus: () -> Unit,
  onDismissError: () -> Unit,
  onUseDynamicColorChanged: (Boolean) -> Unit = {},
  onPadGridSpacingChanged: (Int) -> Unit = {},
  onToggleGlobalCue: () -> Unit = {},
  onLockUnlockHint: () -> Unit = {},
  onDoubleTapStop: (String) -> Unit = {},
  onSwipeVolumeChange: (String, Float, Boolean) -> Unit = { _, _, _ -> },
  onToggleMultiSelectMode: () -> Unit = {},
  onTogglePadSelection: (String) -> Unit = {},
  onSelectAllPadsInBank: (List<String>) -> Unit = {},
  onClearPadSelection: () -> Unit = {},
  onBulkDeleteSelected: () -> Unit = {},
  onBulkColorSelected: (Long?) -> Unit = {},
  onBulkMoveSelectedToBank: (Int?) -> Unit = {},
  onBulkDuplicateSelected: () -> Unit = {},
  onDeleteErrorLog: (String) -> Unit = {},
  onFixErrorLog: (AppErrorLog) -> Unit = {},
  onHapticIntensityChanged: (HapticIntensity) -> Unit = {},
  onSwipePadVolumeToggle: (Boolean) -> Unit = {},
  onDoubleTapToStopToggle: (Boolean) -> Unit = {},
  onHasCompletedOnboardingToggle: (Boolean) -> Unit = {},
  onShowFeatureTooltipsToggle: (Boolean) -> Unit = {},
  onAppLanguageChanged: (AppLanguage) -> Unit = {},
  onRequestRecordAudioPermission: () -> Unit = {},
  onRequestBleMidiPermissions: () -> Unit = {},
  canScheduleExactAlarms: Boolean = true,
  onRequestExactAlarmPermission: () -> Unit = {},
  onOpenMasterRecorder: () -> Unit = {},
  onCloseMasterRecorder: () -> Unit = {},
  onSelectMasterRecordSampleRate: (MasterRecordSampleRate) -> Unit = {},
  onPickMasterRecordFolder: () -> Unit = {},
  onToggleMasterRecordAutoAddPad: (Boolean) -> Unit = {},
  onStartMasterRecording: () -> Unit = {},
  onStopAndSaveMasterRecording: () -> Unit = {},
  onOpenScheduler: () -> Unit = {},
  onCloseScheduler: () -> Unit = {},
  onSaveScheduledTask: (ScheduledPadTask) -> Unit = {},
  onToggleScheduledTaskEnabled: (ScheduledPadTask, Boolean) -> Unit = { _, _ -> },
  onDeleteScheduledTask: (String) -> Unit = {},
  onTestTriggerScheduledTask: (ScheduledPadTask) -> Unit = {},
  onOpenSequencer: () -> Unit = {},
  onCloseSequencer: () -> Unit = {},
  onSaveSequencerPlaylist: (SequencerPlaylist) -> Unit = {},
  onDeleteSequencerPlaylist: (String) -> Unit = {},
  onPlaySequencerPlaylist: (SequencerPlaylist, Int) -> Unit = { _, _ -> },
  onToggleSequencerPlayPause: (SequencerPlaylist?) -> Unit = {},
  onSequencerNext: () -> Unit = {},
  onSequencerPrev: () -> Unit = {},
  onStopSequencer: () -> Unit = {},
  onArmSequencerMidiLearn: (SequencerPlaylist) -> Unit = {},
  onClearSequencerMidiTrigger: (SequencerPlaylist) -> Unit = {},
  onArmMidiLearnForBankPc: (Int, String) -> Unit = { _, _ -> },
  onClearMidiPcMappingForBank: (Int) -> Unit = {},
  onConsumeBankSwitchRequest: () -> Unit = {}
) {
  val context = LocalContext.current
  val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
  var isBatterySaver by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, powerManager) {
    val obs = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        isBatterySaver = powerManager?.isPowerSaveMode == true
      }
    }
    lifecycleOwner.lifecycle.addObserver(obs)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(obs)
    }
  }
  var showGridSheet by remember { mutableStateOf(false) }
  var showMiuiDialog by remember { mutableStateOf(false) }
  var showProfileSheet by remember { mutableStateOf(false) }
  var showMidiSheet by remember { mutableStateOf(false) }
  var showAudioOutputSheet by remember { mutableStateOf(false) }
  var showErrorLogSheet by remember { mutableStateOf(false) }
  var showReorderBanksDialog by remember { mutableStateOf(false) }
  var showOnboardingManual by remember { mutableStateOf(false) }
  var showBulkColorDialog by remember { mutableStateOf(false) }
  var showBulkMoveBankDialog by remember { mutableStateOf(false) }
  var renamingBankKey by remember { mutableStateOf<Int?>(null) }
  var quickActionPad by remember { mutableStateOf<SoundPad?>(null) }
  var duplicateToBankSourcePad by remember { mutableStateOf<SoundPad?>(null) }

  val snackbarHostState = remember { SnackbarHostState() }
  val undoActionLabel = stringResource(R.string.action_undo)
  LaunchedEffect(uiState.undoSnackbarVersion) {
    val msg = uiState.undoSnackbarMessage
    if (uiState.undoSnackbarVersion > 0 && msg != null && uiState.canUndoEdit) {
      val result = snackbarHostState.showSnackbar(
        message = msg,
        actionLabel = undoActionLabel,
        duration = SnackbarDuration.Short
      )
      if (result == SnackbarResult.ActionPerformed) {
        onUndoEdit()
      }
    }
  }

  val activeProfileName = uiState.profiles.firstOrNull {
    it.id == uiState.settings.activeProfileId
  }?.name ?: "Default Session"

  val pageCount = uiState.bankPages.size.coerceAtLeast(1)
  val sharedPagerState = rememberPagerState(pageCount = { pageCount })
  val rootCoroutineScope = rememberCoroutineScope()

  LaunchedEffect(uiState.requestedBankPageIndex, uiState.bankPages.size) {
    val targetIdx = uiState.requestedBankPageIndex
    if (targetIdx != null) {
      if (uiState.bankPages.isNotEmpty()) {
        val clamped = targetIdx.coerceIn(0, uiState.bankPages.lastIndex)
        sharedPagerState.animateScrollToPage(clamped)
      }
      onConsumeBankSwitchRequest()
    }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      val topMod = if (uiState.settings.isFullscreen) Modifier else Modifier.statusBarsPadding()
      TopControlBar(
        activeProfileName = activeProfileName,
        folderName = uiState.settings.folderName,
        padCount = uiState.pads.size,
        isScanning = uiState.isScanning,
        newlyDetectedCount = uiState.newlyDetectedCount,
        playMode = uiState.settings.playMode,
        gridConfig = uiState.settings.gridConfig,
        isSearchVisible = uiState.isSearchVisible,
        searchQuery = uiState.searchQuery,
        isFullscreen = uiState.settings.isFullscreen,
        isCrossfadeEnabled = uiState.settings.globalCrossfadeEnabled,
        isLocked = uiState.settings.isLocked,
        midiConnectedCount = uiState.midiDevices.size,
        isMidiLearnMode = uiState.isMidiLearnMode,
        errorLogCount = uiState.errorLogs.size,
        isSequencerPlaying = uiState.sequencerPlayback.isPlaying,
        activeSchedulesCount = uiState.scheduledTasks.count { it.enabled },
        onPickFolderClick = onPickFolderClick,
        onRescanClick = onRescanClick,
        onToggleSearch = onToggleSearch,
        onSearchQueryChange = onSearchQueryChange,
        onPlayModeSelected = onPlayModeSelected,
        onOpenProfiles = { showProfileSheet = true },
        onOpenMidiController = {
          onRefreshMidiDevices()
          showMidiSheet = true
        },
        onOpenAudioOutput = {
          onRefreshOutputDevices()
          showAudioOutputSheet = true
        },
        onOpenSequencer = onOpenSequencer,
        onOpenScheduler = onOpenScheduler,
        onToggleLockMode = onToggleLockMode,
        onLockUnlockHint = onLockUnlockHint,
        onOpenGridSettings = { showGridSheet = true },
        onToggleCrossfade = onToggleCrossfade,
        onToggleFullscreen = onToggleFullscreen,
        onOpenErrorLogs = { showErrorLogSheet = true },
        onOpenMiuiGuide = { showMiuiDialog = true },
        modifier = topMod
      )
    },
    bottomBar = {
      AnimatedVisibility(visible = uiState.settings.isBottomBarVisible) {
        BottomConsoleBar(
          activeVoices = uiState.activeCount,
          masterVolume = uiState.settings.masterVolume,
          isEditMode = uiState.isEditMode,
          isLocked = uiState.settings.isLocked,
          isGlobalCueEnabled = uiState.settings.isGlobalCueEnabled,
          isMasterRecording = uiState.masterRecordState.isRecording,
          masterRecordElapsedMs = uiState.masterRecordState.elapsedMs,
          hasPads = uiState.pads.isNotEmpty(),
          onStopAll = onStopAll,
          onFadeOutAll = onFadeOutAll,
          onMasterVolumeChange = onMasterVolumeChanged,
          onToggleGlobalCue = onToggleGlobalCue,
          onOpenMasterRecorder = onOpenMasterRecorder,
          onOpenTrackCustomizer = onOpenTrackCustomizer,
          onOpenVoiceRecorder = onOpenVoiceRecorder,
          onToggleEditMode = onToggleEditMode,
          onHideBottomBar = { onBottomBarVisibleChanged(false) }
        )
      }
    }
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      val widthClass = remember(maxWidth) { resolveAdaptiveWidthClass(maxWidth) }
      val isLandscapeStage = remember(maxWidth, maxHeight) {
        maxWidth > maxHeight && maxWidth >= 600.dp
      }
      val showBankSideRail = isLandscapeStage &&
        uiState.pads.isNotEmpty() &&
        uiState.filteredPads.isNotEmpty()
      val showMixerSidePanel = (widthClass == AdaptiveWidthClass.EXPANDED || (isLandscapeStage && maxWidth >= 720.dp)) &&
        uiState.pads.isNotEmpty() &&
        !uiState.settings.isLocked

      Row(modifier = Modifier.fillMaxSize()) {
        if (showBankSideRail) {
          LandscapeBankRail(
            bankPages = uiState.bankPages,
            currentPageIndex = sharedPagerState.currentPage,
            playbackStates = uiState.playbackStates,
            playMode = uiState.settings.playMode,
            isEditMode = uiState.isEditMode,
            isLocked = uiState.settings.isLocked,
            isGlobalCueEnabled = uiState.settings.isGlobalCueEnabled,
            onSelectPage = { idx ->
              rootCoroutineScope.launch { sharedPagerState.animateScrollToPage(idx) }
            },
            onPlayModeSelected = onPlayModeSelected,
            onRenameBankRequest = { bankKey ->
              if (!uiState.settings.isLocked) renamingBankKey = bankKey
            },
            onRescanBankRequest = { bankKey ->
              if (!uiState.settings.isLocked) onRescanBank(bankKey)
            },
            onReorderBanksRequest = {
              if (!uiState.settings.isLocked) showReorderBanksDialog = true
            },
            onToggleGlobalCue = onToggleGlobalCue,
            onToggleEditMode = onToggleEditMode
          )
        }

        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
        ) {
          AnimatedVisibility(visible = uiState.settings.isLocked) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.errorContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Lock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = stringResource(R.string.stage_lock_active_banner),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.weight(1f)
                )
                HoldToUnlockControl(
                  isLocked = true,
                  onLockNow = {},
                  onUnlockHoldComplete = onToggleLockMode,
                  onShortTapWhileLocked = onLockUnlockHint,
                  tint = MaterialTheme.colorScheme.error,
                  modifier = Modifier.testTag("unlock_stage_banner_button")
                )
              }
            }
          }

          AnimatedVisibility(visible = uiState.isEditMode && !uiState.settings.isLocked) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.secondaryContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = if (uiState.isMultiSelectMode) {
                      stringResource(R.string.edit_bulk_selected_count, uiState.selectedPadIds.size)
                    } else {
                      "EDIT MODE: Tap pad customize · Hold & drag swap"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  FilterChip(
                    selected = uiState.isMultiSelectMode,
                    onClick = onToggleMultiSelectMode,
                    leadingIcon = {
                      Icon(Icons.Filled.CheckBox, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    label = {
                      Text(stringResource(R.string.edit_multi_select), style = MaterialTheme.typography.labelSmall)
                    },
                    modifier = Modifier.testTag("multi_select_toggle_button")
                  )
                  IconButton(
                    enabled = uiState.canUndoEdit,
                    onClick = onUndoEdit,
                    modifier = Modifier
                      .size(32.dp)
                      .testTag("undo_edit_button")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Undo,
                      contentDescription = stringResource(R.string.cd_undo_edit),
                      tint = if (uiState.canUndoEdit) {
                        MaterialTheme.colorScheme.secondary
                      } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.35f)
                      },
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  IconButton(
                    enabled = uiState.canRedoEdit,
                    onClick = onRedoEdit,
                    modifier = Modifier
                      .size(32.dp)
                      .testTag("redo_edit_button")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.Redo,
                      contentDescription = stringResource(R.string.cd_redo_edit),
                      tint = if (uiState.canRedoEdit) {
                        MaterialTheme.colorScheme.secondary
                      } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.35f)
                      },
                      modifier = Modifier.size(18.dp)
                    )
                  }
                }

                if (uiState.isMultiSelectMode) {
                  val currentBankPadIds = uiState.bankPages.getOrNull(sharedPagerState.currentPage)?.pads?.map { it.id }.orEmpty()
                  val hasSelected = uiState.selectedPadIds.isNotEmpty()
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    item {
                      FilterChip(
                        selected = false,
                        onClick = { onSelectAllPadsInBank(currentBankPadIds) },
                        leadingIcon = { Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_select_all), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_select_all_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        enabled = hasSelected,
                        onClick = { showBulkColorDialog = true },
                        leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_bulk_color), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_color_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        enabled = hasSelected,
                        onClick = { showBulkMoveBankDialog = true },
                        leadingIcon = { Icon(Icons.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_bulk_move), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_move_bank_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        enabled = hasSelected,
                        onClick = onBulkDuplicateSelected,
                        leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_bulk_duplicate), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_duplicate_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        enabled = hasSelected,
                        onClick = onBulkDeleteSelected,
                        leadingIcon = {
                          Icon(
                            Icons.Filled.Delete,
                            contentDescription = null,
                            tint = if (hasSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                          )
                        },
                        label = { Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_delete_button")
                      )
                    }
                    if (hasSelected) {
                      item {
                        IconButton(
                          onClick = onClearPadSelection,
                          modifier = Modifier.size(32.dp).testTag("bulk_clear_button")
                        ) {
                          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(16.dp))
                        }
                      }
                    }
                  }
                }
              }
            }
          }

      AnimatedVisibility(visible = uiState.isMidiLearnMode || uiState.midiLearnTarget != null) {
        val bannerText = when (val target = uiState.midiLearnTarget) {
          is MidiLearnTarget.PadNote -> "MIDI LEARN: Press a MIDI key for \"${target.padName}\""
          is MidiLearnTarget.CcControl -> "MIDI LEARN: Move MIDI fader/knob for \"${target.target.label}\""
          is MidiLearnTarget.ProgramChangeBank -> "MIDI LEARN: Kirim Program Change untuk pindah ke \"${target.bankLabel}\""
          is MidiLearnTarget.SequencerPlaylistNote -> "MIDI LEARN: Tekan tuts MIDI untuk memicu playlist \"${target.playlistName}\""
          null -> "MIDI LEARN: Tap any pad on the board, then press a MIDI key to map"
        }
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              modifier = Modifier.weight(1f),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Piano,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = bannerText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
              )
            }
            IconButton(
              onClick = onCancelMidiLearn,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Exit MIDI Learn mode",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = uiState.newlyDetectedCount > 0) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = "+${uiState.newlyDetectedCount} new audio file(s) auto-detected and loaded",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = onDismissNewFilesNotice,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = uiState.statusMessage != null) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.tertiaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = uiState.statusMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.weight(1f)
            )
            IconButton(
              onClick = onDismissStatus,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss notice",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = uiState.errorMessage != null) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.errorContainer
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text(
              text = uiState.errorMessage.orEmpty(),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f)
            )
            if (uiState.hasFolderPermissionError) {
              Spacer(modifier = Modifier.width(8.dp))
              FilledIconButton(
                onClick = onPickFolderClick,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError
                )
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Grant folder permission", modifier = Modifier.size(18.dp))
              }
            } else {
              IconButton(
                onClick = onDismissError,
                modifier = Modifier.size(28.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = "Dismiss error",
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      AnimatedVisibility(visible = uiState.sequencerPlayback.isPlaying) {
        val seq = uiState.sequencerPlayback
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpenSequencer)
            .testTag("mini_sequencer_bar"),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.secondaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.QueueMusic,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${seq.activePlaylistName ?: "Sequencer"} (${seq.currentIndex + 1}/${seq.queueSize.coerceAtLeast(1)})",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Text(
                text = if (seq.isInGap) {
                  "Jeda %.1fd: ${seq.currentPadName.orEmpty()}".format(seq.remainingGapSec)
                } else {
                  seq.currentPadName.orEmpty()
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
            IconButton(
              onClick = { onToggleSequencerPlayPause(null) },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = if (seq.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = if (seq.isPaused) "Resume Sequencer" else "Pause Sequencer",
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(
              onClick = onSequencerNext,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Next Sequencer Item",
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(
              onClick = onStopSequencer,
              modifier = Modifier.size(32.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = "Stop Sequencer",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      when {
        uiState.settings.folderUri == null && uiState.pads.isEmpty() -> {
          EmptyFolderOnboarding(
            onPickFolderClick = onPickFolderClick,
            onLoadDemoTracks = onLoadDemoTracks,
            modifier = Modifier.weight(1f)
          )
        }
        uiState.isScanning && uiState.pads.isEmpty() -> {
          ScanningState(modifier = Modifier.weight(1f))
        }
        uiState.pads.isEmpty() -> {
          EmptyAudioFolderState(
            folderName = uiState.settings.folderName ?: "Selected Folder",
            onRescanClick = onRescanClick,
            onPickFolderClick = onPickFolderClick,
            onLoadDemoTracks = onLoadDemoTracks,
            modifier = Modifier.weight(1f)
          )
        }
        uiState.filteredPads.isEmpty() -> {
          EmptySearchState(
            query = uiState.searchQuery.ifBlank {
              uiState.selectedCategoryTag ?: if (uiState.showFavoritesOnly) "Favorites" else ""
            },
            onClearSearch = {
              onSearchQueryChange("")
              if (uiState.showFavoritesOnly) onToggleFavoritesFilter()
              if (uiState.selectedCategoryTag != null) onSelectCategoryTagFilter(null)
            },
            modifier = Modifier.weight(1f)
          )
        }
        else -> {
          AnimatedVisibility(
            visible = uiState.settings.showFeatureTooltips &&
              !uiState.settings.isLocked &&
              !uiState.isEditMode
          ) {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .testTag("feature_tooltip_banner"),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Lightbulb,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(16.dp)
                )
                Text(
                  text = stringResource(R.string.tooltip_live_tip),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.weight(1f),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
                TextButton(
                  onClick = { onShowFeatureTooltipsToggle(false) },
                  modifier = Modifier.testTag("dismiss_tooltip_banner_button")
                ) {
                  Text(
                    text = stringResource(R.string.tooltip_dismiss),
                    style = MaterialTheme.typography.labelSmall
                  )
                }
              }
            }
          }

          if (uiState.pads.any { it.isFavorite } || uiState.availableCategoryTags.isNotEmpty() || uiState.showFavoritesOnly || uiState.selectedCategoryTag != null) {
            LazyRow(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              item {
                FilterChip(
                  selected = uiState.showFavoritesOnly,
                  onClick = onToggleFavoritesFilter,
                  leadingIcon = {
                    Icon(
                      imageVector = Icons.Filled.Star,
                      contentDescription = null,
                      tint = if (uiState.showFavoritesOnly) SoundPadTheme.colors.favoriteGold else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(15.dp)
                    )
                  },
                  label = { Text("Fav", style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.testTag("filter_favorites_chip")
                )
              }
              items(uiState.availableCategoryTags, key = { it }) { tag ->
                val selected = uiState.selectedCategoryTag.equals(tag, ignoreCase = true)
                FilterChip(
                  selected = selected,
                  onClick = { onSelectCategoryTagFilter(tag) },
                  label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.testTag("filter_tag_${tag.lowercase()}")
                )
              }
            }
          }
          BankedPadMatrix(
            bankPages = uiState.bankPages,
            pagerState = sharedPagerState,
            showInlineBankStrip = !showBankSideRail,
            gridConfig = uiState.settings.gridConfig,
            playbackStates = uiState.playbackStates,
            isEditMode = uiState.isEditMode && !uiState.settings.isLocked && uiState.searchQuery.isBlank() && !uiState.showFavoritesOnly && uiState.selectedCategoryTag == null,
            isMultiSelectMode = uiState.isMultiSelectMode,
            selectedPadIds = uiState.selectedPadIds,
            hapticIntensity = uiState.settings.hapticIntensity,
            swipePadVolumeEnabled = uiState.settings.swipePadVolumeEnabled,
            doubleTapToStopEnabled = uiState.settings.doubleTapToStopEnabled,
            isMidiLearnMode = uiState.isMidiLearnMode,
            armedLearnPadId = uiState.armedLearnPadId,
            cuePreviewPadId = uiState.cuePreviewPadId,
            preparingReversePadId = uiState.isPreparingReversePadId,
            padGridSpacingDp = uiState.settings.padGridSpacingDp,
            reduceMotion = isBatterySaver,
            globalCrossfadeEnabled = uiState.settings.globalCrossfadeEnabled,
            miniWaveformEnabled = uiState.settings.miniWaveformEnabled,
            defaultPadShape = uiState.settings.defaultPadShape,
            defaultCornerGlowColor = Color(uiState.settings.defaultCornerGlowArgb.toInt()),
            onRenameBankRequest = { bankKey ->
              if (!uiState.settings.isLocked) renamingBankKey = bankKey
            },
            onRescanBankRequest = { bankKey ->
              if (!uiState.settings.isLocked) onRescanBank(bankKey)
            },
            onReorderBanksRequest = {
              if (!uiState.settings.isLocked) showReorderBanksDialog = true
            },
            onPadTapped = onPadTapped,
            onPadCustomizeClick = { pad ->
              if (!uiState.settings.isLocked) {
                if (pad.errorMessage != null) {
                  onPadTapped(pad)
                } else {
                  quickActionPad = pad
                }
              }
            },
            onPadHoldStart = onPadHoldStart,
            onPadHoldRelease = onPadHoldRelease,
            onDoubleTapStop = onDoubleTapStop,
            onSwipeVolumeChange = onSwipeVolumeChange,
            onTogglePadSelection = onTogglePadSelection,
            onSwapPads = onSwapPads,
            modifier = Modifier.weight(1f)
          )
        }
      }
        } // end center Column

        if (showMixerSidePanel) {
          LandscapeMixerSidePanel(
            activeVoices = uiState.activeCount,
            masterVolume = uiState.settings.masterVolume,
            masterPan = uiState.settings.masterPan,
            masterEqEnabled = uiState.settings.masterEqEnabled,
            masterEqPreset = uiState.settings.masterEqPreset,
            onMasterVolumeChange = onMasterVolumeChanged,
            onMasterPanChange = onMasterPanChanged,
            onMasterEqEnabledChange = onMasterEqEnabledChange,
            onMasterEqPresetSelect = onMasterEqPresetSelect,
            onStopAll = onStopAll,
            onFadeOutAll = onFadeOutAll,
            onOpenFullAudioMixer = {
              onRefreshOutputDevices()
              showAudioOutputSheet = true
            }
          )
        }
      } // end adaptive Row

      if (!uiState.settings.isBottomBarVisible) {
        FilledIconButton(
          onClick = { onBottomBarVisibleChanged(true) },
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(14.dp)
            .size(48.dp)
            .testTag("show_bottom_bar_button"),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = Icons.Filled.KeyboardArrowUp,
            contentDescription = stringResource(R.string.cd_show_bottom_bar)
          )
        }
      }
    }
  }

  val editingPad = uiState.editingPad
  if (editingPad != null) {
    PadSettingsSheet(
      pad = editingPad,
      allPads = uiState.pads,
      defaultPadShape = uiState.settings.defaultPadShape,
      defaultCornerGlowColor = Color(uiState.settings.defaultCornerGlowArgb.toInt()),
      isCuePlaying = uiState.cuePreviewPadId == editingPad.id,
      isPreparingReverse = uiState.isPreparingReversePadId == editingPad.id,
      onSelectPad = onSelectEditingPad,
      onSaveAppearance = { visualConfig ->
        onSavePadAppearance(editingPad.id, visualConfig)
      },
      onApplyStyleToAll = onApplyStyleToAllPads,
      onSettingsChange = { newSettings ->
        onUpdatePadSettings(editingPad.id, newSettings)
      },
      onToggleFavorite = { onTogglePadFavorite(editingPad.id) },
      onCategoryTagChange = { tag -> onSetPadCategoryTag(editingPad.id, tag) },
      onToggleCuePreview = { onToggleCuePreview(editingPad) },
      onOpenAudioEditor = { onOpenAudioEditor(editingPad) },
      onDuplicatePad = { onDuplicatePad(editingPad.id) },
      onDuplicateToBank = { duplicateToBankSourcePad = editingPad },
      onDeletePad = { onDeletePad(editingPad.id) },
      onDismiss = onClosePadEditor
    )
  }

  val editorPad = uiState.audioEditorPad
  if (editorPad != null) {
    AudioEditorSheet(
      pad = editorPad,
      waveform = uiState.waveformData,
      isLoadingWaveform = uiState.isLoadingWaveform,
      isExporting = uiState.isExportingCut,
      previewPositionMs = uiState.editorPreviewPositionMs,
      onStartPreview = onStartEditorPreview,
      onStopPreview = onStopEditorPreview,
      onSaveNonDestructiveTrim = onSaveNonDestructiveTrim,
      onExportCutToNewPad = onExportCutAsNewPad,
      onExportCutToFile = onExportCutToFile,
      onDismiss = onCloseAudioEditor
    )
  }

  if (uiState.isVoiceRecordSheetOpen) {
    VoiceRecordSheet(
      isRecording = uiState.isRecordingVoice,
      level = uiState.voiceLevel,
      elapsedMs = uiState.voiceElapsedMs,
      onStartRecording = onStartVoiceRecording,
      onStopAndSaveRecording = onStopAndSaveVoiceRecording,
      onCancelRecording = onCancelVoiceRecording,
      onDismiss = onCloseVoiceRecorder
    )
  }

  if (showProfileSheet) {
    ProfileManagerSheet(
      profiles = uiState.profiles,
      activeProfileId = uiState.settings.activeProfileId,
      onSaveNewProfile = onSaveNewProfile,
      onLoadProfile = { id ->
        onLoadProfile(id)
        showProfileSheet = false
      },
      onDuplicateProfile = onDuplicateProfile,
      onDeleteProfile = onDeleteProfile,
      onExportProfileJson = onExportProfileJson,
      onImportProfileJson = onImportProfileJson,
      onBackupAllData = onBackupAllData,
      onRestoreAllData = onRestoreAllData,
      onDismiss = { showProfileSheet = false }
    )
  }

  if (showGridSheet) {
    GridSettingsSheet(
      settings = uiState.settings,
      preloadedCount = uiState.preloadedCount,
      onGridConfigChange = onGridConfigChanged,
      onThemeModeChange = onThemeModeChanged,
      onUseDynamicColorChange = onUseDynamicColorChanged,
      onAppAccentColorChange = onAppAccentColorChanged,
      onDefaultPadShapeChange = onDefaultPadShapeChanged,
      onDefaultCornerGlowChange = onDefaultCornerGlowChanged,
      onPadGridSpacingChange = onPadGridSpacingChanged,
      onBottomBarVisibleChange = onBottomBarVisibleChanged,
      onLoadDemoTracks = onLoadDemoTracks,
      onExportDemoToMusicFolder = onExportDemoToMusicFolder,
      onPreloadToggle = onPreloadToggle,
      onKeepScreenOnToggle = onKeepScreenOnToggle,
      onGlobalCrossfadeToggle = { onToggleCrossfade() },
      onCrossfadeDurationChange = onCrossfadeDurationChange,
      onCrossfadeCurveChange = onCrossfadeCurveChange,
      onGlobalFadeInChange = onGlobalFadeInChange,
      onGlobalFadeOutChange = onGlobalFadeOutChange,
      onGlobalFadeCurveChange = onGlobalFadeCurveChange,
      onMasterPanChange = onMasterPanChanged,
      onMiniWaveformToggle = onMiniWaveformToggle,
      onHapticIntensityChange = onHapticIntensityChanged,
      onSwipePadVolumeToggle = onSwipePadVolumeToggle,
      onDoubleTapToStopToggle = onDoubleTapToStopToggle,
      onAppLanguageChange = onAppLanguageChanged,
      onShowFeatureTooltipsToggle = onShowFeatureTooltipsToggle,
      onReplayOnboarding = { showOnboardingManual = true },
      onOpenMiuiGuide = { showMiuiDialog = true },
      onDismiss = { showGridSheet = false }
    )
  }

  if (showMiuiDialog) {
    MiuiGuideDialog(onDismiss = { showMiuiDialog = false })
  }

  if (showMidiSheet) {
    MidiControllerSheet(
      midiDevices = uiState.midiDevices,
      discoveredBleDevices = uiState.discoveredBleDevices,
      isScanningBle = uiState.isScanningBleMidi,
      lastActivityLabel = uiState.lastMidiActivity,
      pads = uiState.pads,
      ccMappings = uiState.midiCcMappings,
      isMidiLearnMode = uiState.isMidiLearnMode,
      midiLearnTarget = uiState.midiLearnTarget,
      onToggleMidiLearnMode = onToggleMidiLearnMode,
      onArmPadLearn = { pad ->
        onArmMidiLearnForPad(pad)
      },
      onClearPadNote = onClearPadMidiNote,
      onArmCcLearn = onArmMidiLearnForCc,
      onClearCcMapping = onClearMidiCcMapping,
      onCancelLearn = onCancelMidiLearn,
      onRefreshDevices = onRefreshMidiDevices,
      onStartBleScan = onStartBleMidiScan,
      onStopBleScan = onStopBleMidiScan,
      onConnectBleDevice = onConnectBleMidiDevice,
      bankPages = uiState.bankPages,
      pcMappings = uiState.midiPcMappings,
      onArmBankPcLearn = onArmMidiLearnForBankPc,
      onClearBankPcMapping = onClearMidiPcMappingForBank,
      onDismiss = { showMidiSheet = false }
    )
  }
  if (showAudioOutputSheet) {
    AudioOutputSheet(
      settings = uiState.settings,
      outputDevices = uiState.outputDevices,
      selectedMainDeviceId = uiState.selectedMainOutputId,
      selectedCueDeviceId = uiState.selectedCueOutputId,
      isNormalizingAll = uiState.isNormalizingAll,
      onRefreshDevices = onRefreshOutputDevices,
      onSelectMainDevice = onSelectMainOutputDevice,
      onSelectCueDevice = onSelectCueOutputDevice,
      onMasterLimiterToggle = onMasterLimiterToggle,
      onDuckingToggle = onDuckingToggle,
      onDuckingAttenuationChange = onDuckingAttenuationChange,
      onAutoNormalizeToggle = onAutoNormalizeToggle,
      onAnalyzeLoudnessAll = onAnalyzeLoudnessAll,
      onMasterEqEnabledChange = onMasterEqEnabledChange,
      onMasterEqPresetSelect = onMasterEqPresetSelect,
      onMasterEqBandChange = onMasterEqBandChange,
      onResetMasterEq = onResetMasterEq,
      onDismiss = { showAudioOutputSheet = false }
    )
  }

  if (showErrorLogSheet) {
    ErrorLogSheet(
      errorLogs = uiState.errorLogs,
      onClearLogs = onClearErrorLogs,
      onExportTxt = onExportErrorLogs,
      onDeleteLog = onDeleteErrorLog,
      onFixLog = { log ->
        showErrorLogSheet = false
        onFixErrorLog(log)
      },
      onDismiss = { showErrorLogSheet = false }
    )
  }

  if (uiState.isMasterRecordSheetOpen) {
    MasterRecordSheet(
      recordState = uiState.masterRecordState,
      onSelectSampleRate = onSelectMasterRecordSampleRate,
      onPickOutputFolder = onPickMasterRecordFolder,
      onToggleAutoAddPad = onToggleMasterRecordAutoAddPad,
      onStartRecording = onStartMasterRecording,
      onStopAndSave = onStopAndSaveMasterRecording,
      onDismiss = onCloseMasterRecorder
    )
  }

  if (uiState.isSchedulerSheetOpen) {
    SchedulerSheet(
      tasks = uiState.scheduledTasks,
      pads = uiState.pads,
      bankPages = uiState.bankPages,
      playlists = uiState.sequencerPlaylists,
      canScheduleExactAlarms = canScheduleExactAlarms,
      onRequestExactAlarmPermission = onRequestExactAlarmPermission,
      onSaveTask = onSaveScheduledTask,
      onToggleTaskEnabled = onToggleScheduledTaskEnabled,
      onDeleteTask = onDeleteScheduledTask,
      onTestTriggerTask = onTestTriggerScheduledTask,
      onDismiss = onCloseScheduler
    )
  }

  if (uiState.isSequencerSheetOpen) {
    SequencerSheet(
      playlists = uiState.sequencerPlaylists,
      playbackState = uiState.sequencerPlayback,
      pads = uiState.pads,
      onSavePlaylist = onSaveSequencerPlaylist,
      onDeletePlaylist = onDeleteSequencerPlaylist,
      onPlayPlaylist = onPlaySequencerPlaylist,
      onTogglePlayPause = onToggleSequencerPlayPause,
      onNext = onSequencerNext,
      onPrev = onSequencerPrev,
      onStop = onStopSequencer,
      onArmMidiLearn = onArmSequencerMidiLearn,
      onClearMidiTrigger = onClearSequencerMidiTrigger,
      onDismiss = onCloseSequencer
    )
  }

  if (showBulkColorDialog) {
    BulkColorPickerDialog(
      selectedCount = uiState.selectedPadIds.size,
      onSelectColor = { colorArgb ->
        onBulkColorSelected(colorArgb)
        showBulkColorDialog = false
      },
      onDismiss = { showBulkColorDialog = false }
    )
  }

  if (showBulkMoveBankDialog) {
    BulkMoveToBankDialog(
      selectedCount = uiState.selectedPadIds.size,
      bankPages = uiState.bankPages,
      onSelectTargetBank = { bankKey ->
        onBulkMoveSelectedToBank(bankKey)
        showBulkMoveBankDialog = false
      },
      onDismiss = { showBulkMoveBankDialog = false }
    )
  }

  if (!uiState.settings.hasCompletedOnboarding || showOnboardingManual) {
    OnboardingDialog(
      currentGridConfig = uiState.settings.gridConfig,
      currentThemeMode = uiState.settings.themeMode,
      onPickFolder = onPickFolderClick,
      onLoadDemoTracks = onLoadDemoTracks,
      onSelectGridConfig = onGridConfigChanged,
      onSelectThemeMode = onThemeModeChanged,
      onRequestMicPermission = onRequestRecordAudioPermission,
      onRequestBlePermissions = onRequestBleMidiPermissions,
      onOpenMiuiGuide = { showMiuiDialog = true },
      onFinishOnboarding = {
        showOnboardingManual = false
        onHasCompletedOnboardingToggle(true)
      },
      onDismissSkip = {
        showOnboardingManual = false
        onHasCompletedOnboardingToggle(true)
      }
    )
  }

  val actionTargetPad = quickActionPad
  if (actionTargetPad != null) {
    PadQuickActionSheet(
      pad = actionTargetPad,
      isCuePlaying = uiState.cuePreviewPadId == actionTargetPad.id,
      onCustomize = {
        quickActionPad = null
        onPadCustomizeClick(actionTargetPad)
      },
      onDuplicateHere = {
        quickActionPad = null
        onDuplicatePadToBank(actionTargetPad.id, null)
      },
      onDuplicateToOtherBank = {
        quickActionPad = null
        duplicateToBankSourcePad = actionTargetPad
      },
      onCuePreview = {
        quickActionPad = null
        onToggleCuePreview(actionTargetPad)
      },
      onEditAudio = {
        quickActionPad = null
        onOpenAudioEditor(actionTargetPad)
      },
      onDeletePad = {
        quickActionPad = null
        onDeletePad(actionTargetPad.id)
      },
      onDismiss = { quickActionPad = null }
    )
  }

  val dupSource = duplicateToBankSourcePad
  if (dupSource != null) {
    DuplicateToBankDialog(
      sourcePad = dupSource,
      bankPages = uiState.bankPages,
      onSelectTargetBank = { targetBankKey ->
        onDuplicatePadToBank(dupSource.id, targetBankKey)
        duplicateToBankSourcePad = null
      },
      onDismiss = { duplicateToBankSourcePad = null }
    )
  }

  if (showReorderBanksDialog) {
    ReorderBanksDialog(
      bankPages = uiState.bankPages,
      onMoveBank = onMoveBankOrder,
      onDismiss = { showReorderBanksDialog = false }
    )
  }

  val targetBankToRename = renamingBankKey
  if (targetBankToRename != null) {
    val currentTitle = uiState.bankPages.firstOrNull { it.bankKey == targetBankToRename }?.title
      ?: uiState.activeBankNames[targetBankToRename]
      ?: ""
    var bankLabelInput by remember(targetBankToRename) {
      mutableStateOf(currentTitle)
    }
    AlertDialog(
      onDismissRequest = { renamingBankKey = null },
      title = { Text("Rename Bank ${targetBankToRename + 1}") },
      text = {
        OutlinedTextField(
          value = bankLabelInput,
          onValueChange = { bankLabelInput = it.take(20) },
          placeholder = { Text("e.g. Intro, Drop, SFX...") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("rename_bank_input")
        )
      },
      confirmButton = {
        FilledIconButton(
          onClick = {
            onRenameBank(targetBankToRename, bankLabelInput)
            renamingBankKey = null
          },
          modifier = Modifier.testTag("confirm_rename_bank_button")
        ) {
          Icon(Icons.Filled.Check, contentDescription = "Save bank name")
        }
      },
      dismissButton = {
        IconButton(onClick = { renamingBankKey = null }) {
          Icon(Icons.Filled.Close, contentDescription = "Cancel rename bank")
        }
      }
    )
  }

  val diagnosticPad = uiState.diagnosticPad
  if (diagnosticPad != null) {
    PadErrorDiagnosticDialog(
      pad = diagnosticPad,
      onOpenErrorLogs = {
        onDismissPadDiagnostic()
        showErrorLogSheet = true
      },
      onDismiss = onDismissPadDiagnostic
    )
  }

  val pendingCrash = uiState.pendingCrashLog
  if (pendingCrash != null) {
    PreviousCrashDialog(
      crashLog = pendingCrash,
      onExportTxt = {
        onDismissPendingCrashDialog()
        onExportErrorLogs()
      },
      onViewLogs = {
        onDismissPendingCrashDialog()
        showErrorLogSheet = true
      },
      onDismiss = onDismissPendingCrashDialog
    )
  }
}

@Composable
private fun BankedPadMatrix(
  bankPages: List<BankPageUi>,
  pagerState: PagerState,
  showInlineBankStrip: Boolean,
  gridConfig: GridConfig,
  playbackStates: Map<String, PadPlaybackState>,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean,
  selectedPadIds: Set<String>,
  hapticIntensity: HapticIntensity,
  swipePadVolumeEnabled: Boolean,
  doubleTapToStopEnabled: Boolean,
  isMidiLearnMode: Boolean,
  armedLearnPadId: String?,
  cuePreviewPadId: String?,
  preparingReversePadId: String?,
  padGridSpacingDp: Int,
  reduceMotion: Boolean,
  globalCrossfadeEnabled: Boolean,
  miniWaveformEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  onRenameBankRequest: (Int) -> Unit,
  onRescanBankRequest: (Int) -> Unit,
  onReorderBanksRequest: () -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldRelease: (SoundPad) -> Unit,
  onDoubleTapStop: (String) -> Unit,
  onSwipeVolumeChange: (String, Float, Boolean) -> Unit,
  onTogglePadSelection: (String) -> Unit,
  onSwapPads: (fromPadId: String, toPadId: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val pageCount = bankPages.size.coerceAtLeast(1)
  val coroutineScope = rememberCoroutineScope()
  var isDraggingAnyPad by remember { mutableStateOf(false) }
  val currentPage = bankPages.getOrNull(pagerState.currentPage)
  val distinctBankCount = remember(bankPages) { bankPages.map { it.bankKey }.distinct().size }

  Column(modifier = modifier.fillMaxSize()) {
    if (showInlineBankStrip) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        LazyRow(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          itemsIndexed(bankPages, key = { index, page -> "${page.bankKey}_$index" }) { index, page ->
            val isCurrent = pagerState.currentPage == index
            val hasActiveInBank = page.pads.any { playbackStates[it.id]?.isPlaying == true }
            Surface(
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .testTag("bank_tab_$index")
                .clickable {
                  coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                  }
                },
              shape = RoundedCornerShape(8.dp),
              color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(
                width = 1.dp,
                color = when {
                  isCurrent -> MaterialTheme.colorScheme.primary
                  hasActiveInBank -> MaterialTheme.colorScheme.secondary
                  else -> MaterialTheme.colorScheme.outline
                }
              )
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
              ) {
                if (hasActiveInBank) {
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.primary)
                  )
                }
                Text(
                  text = page.title,
                  style = MaterialTheme.typography.labelMedium,
                  color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        if (currentPage?.canRescanSubfolder == true) {
          IconButton(
            onClick = { onRescanBankRequest(currentPage.bankKey) },
            modifier = Modifier
              .size(32.dp)
              .testTag("rescan_bank_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = stringResource(R.string.cd_rescan_bank),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        if (distinctBankCount > 1) {
          IconButton(
            onClick = onReorderBanksRequest,
            modifier = Modifier
              .size(32.dp)
              .testTag("reorder_banks_button")
          ) {
            Icon(
              imageVector = Icons.Filled.SwapVert,
              contentDescription = stringResource(R.string.cd_reorder_banks),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        IconButton(
          onClick = { onRenameBankRequest(currentPage?.bankKey ?: pagerState.currentPage) },
          modifier = Modifier
            .size(32.dp)
            .testTag("rename_bank_button")
        ) {
          Icon(
            imageVector = Icons.Filled.DriveFileRenameOutline,
            contentDescription = stringResource(R.string.cd_rename_bank),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
          )
        }
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Text(
            text = "${pagerState.currentPage + 1}/$pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      if (pageCount > 1) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          for (dotIdx in 0 until minOf(pageCount, 12)) {
            val activeDot = pagerState.currentPage == dotIdx
            Box(
              modifier = Modifier
                .padding(horizontal = 2.dp)
                .size(width = if (activeDot) 14.dp else 5.dp, height = 5.dp)
                .clip(CircleShape)
                .background(
                  if (activeDot) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline
                )
            )
          }
        }
      }
    }

    HorizontalPager(
      state = pagerState,
      userScrollEnabled = !isDraggingAnyPad,
      modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
      val bankPads = bankPages.getOrNull(pageIndex)?.pads.orEmpty()
      PadMatrixGrid(
        pads = bankPads,
        bankIndex = pageIndex,
        rows = gridConfig.rows,
        cols = gridConfig.cols,
        padGridSpacingDp = padGridSpacingDp,
        playbackStates = playbackStates,
        isEditMode = isEditMode,
        isMultiSelectMode = isMultiSelectMode,
        selectedPadIds = selectedPadIds,
        hapticIntensity = hapticIntensity,
        swipePadVolumeEnabled = swipePadVolumeEnabled,
        doubleTapToStopEnabled = doubleTapToStopEnabled,
        isMidiLearnMode = isMidiLearnMode,
        armedLearnPadId = armedLearnPadId,
        cuePreviewPadId = cuePreviewPadId,
        preparingReversePadId = preparingReversePadId,
        reduceMotion = reduceMotion,
        globalCrossfadeEnabled = globalCrossfadeEnabled,
        miniWaveformEnabled = miniWaveformEnabled,
        defaultPadShape = defaultPadShape,
        defaultCornerGlowColor = defaultCornerGlowColor,
        onDragActiveChange = { isDraggingAnyPad = it },
        onPadTapped = onPadTapped,
        onPadCustomizeClick = onPadCustomizeClick,
        onPadHoldStart = onPadHoldStart,
        onPadHoldRelease = onPadHoldRelease,
        onDoubleTapStop = onDoubleTapStop,
        onSwipeVolumeChange = onSwipeVolumeChange,
        onTogglePadSelection = onTogglePadSelection,
        onSwapPads = onSwapPads,
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}

@Composable
private fun PadMatrixGrid(
  pads: List<SoundPad>,
  bankIndex: Int,
  rows: Int,
  cols: Int,
  padGridSpacingDp: Int,
  playbackStates: Map<String, PadPlaybackState>,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean,
  selectedPadIds: Set<String>,
  hapticIntensity: HapticIntensity,
  swipePadVolumeEnabled: Boolean,
  doubleTapToStopEnabled: Boolean,
  isMidiLearnMode: Boolean,
  armedLearnPadId: String?,
  cuePreviewPadId: String?,
  preparingReversePadId: String?,
  reduceMotion: Boolean,
  globalCrossfadeEnabled: Boolean,
  miniWaveformEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  onDragActiveChange: (Boolean) -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldRelease: (SoundPad) -> Unit,
  onDoubleTapStop: (String) -> Unit,
  onSwipeVolumeChange: (String, Float, Boolean) -> Unit,
  onTogglePadSelection: (String) -> Unit,
  onSwapPads: (fromPadId: String, toPadId: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val spacing: Dp = remember(padGridSpacingDp, rows, cols) {
    val baseGap = padGridSpacingDp.coerceIn(2, 16)
    when {
      cols <= 5 && rows <= 5 -> baseGap.dp
      cols <= 10 && rows <= 10 -> (baseGap * 0.55f).roundToInt().coerceIn(2, 10).dp
      else -> (baseGap * 0.30f).roundToInt().coerceIn(1, 4).dp
    }
  }
  val outerPadding: Dp = if (cols > 10 || rows > 10) 6.dp else 10.dp

  val cellBounds = remember { mutableStateMapOf<String, Rect>() }
  var gridRootOrigin by remember { mutableStateOf(Offset.Zero) }
  var draggingPad by remember { mutableStateOf<SoundPad?>(null) }
  var dragRootPosition by remember { mutableStateOf(Offset.Zero) }
  var hoveredTargetPadId by remember { mutableStateOf<String?>(null) }
  val haptic = LocalHapticFeedback.current

  BoxWithConstraints(
    modifier = modifier
      .padding(horizontal = outerPadding, vertical = 4.dp)
      .onGloballyPositioned { coords ->
        gridRootOrigin = coords.positionInRoot()
      }
  ) {
    val totalHorizontalSpacing = spacing * (cols - 1)
    val totalVerticalSpacing = spacing * (rows - 1)
    val cellWidth = ((maxWidth - totalHorizontalSpacing) / cols).coerceAtLeast(16.dp)
    val cellHeight = ((maxHeight - totalVerticalSpacing) / rows).coerceAtLeast(16.dp)
    val minSide = min(cellWidth, cellHeight)

    val density = remember(minSide, cols, rows) {
      when {
        minSide >= 64.dp && cols <= 6 && rows <= 6 -> PadDensity.STANDARD
        minSide >= 34.dp && cols <= 11 -> PadDensity.COMPACT
        else -> PadDensity.MICRO
      }
    }

    val totalSlots = rows * cols
    val bankOffset = bankIndex * totalSlots
    val clickHandler: (SoundPad) -> Unit = remember(isEditMode, isMultiSelectMode, onTogglePadSelection, onPadTapped) {
      { targetPad ->
        if (isEditMode && isMultiSelectMode) {
          onTogglePadSelection(targetPad.id)
        } else {
          onPadTapped(targetPad)
        }
      }
    }

    if (totalSlots > 64) {
      val denseCellHeight = cellHeight.coerceAtLeast(36.dp)
      LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
      ) {
        items(
          count = totalSlots,
          key = { localIndex -> pads.getOrNull(localIndex)?.id ?: "empty_${bankIndex}_$localIndex" }
        ) { localIndex ->
          val pad = pads.getOrNull(localIndex)
          val playbackState = pad?.let { playbackStates[it.id] }
          val cellModifier = if (pad != null && isEditMode && !isMultiSelectMode) {
            Modifier
              .onGloballyPositioned { coords -> cellBounds[pad.id] = coords.boundsInRoot() }
              .pointerInput(pad.id, isEditMode, isMultiSelectMode, hapticIntensity) {
                detectDragGesturesAfterLongPress(
                  onDragStart = { localOffset ->
                    val box = cellBounds[pad.id] ?: return@detectDragGesturesAfterLongPress
                    performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
                    draggingPad = pad
                    dragRootPosition = box.topLeft + localOffset
                    hoveredTargetPadId = pad.id
                    onDragActiveChange(true)
                  },
                  onDrag = { change, dragAmount ->
                    change.consume()
                    dragRootPosition += dragAmount
                    hoveredTargetPadId = cellBounds.entries
                      .firstOrNull { it.value.contains(dragRootPosition) }
                      ?.key
                  },
                  onDragEnd = {
                    val fromId = draggingPad?.id
                    val toId = hoveredTargetPadId
                    if (fromId != null && toId != null && fromId != toId) {
                      onSwapPads(fromId, toId)
                    }
                    draggingPad = null
                    hoveredTargetPadId = null
                    onDragActiveChange(false)
                  },
                  onDragCancel = {
                    draggingPad = null
                    hoveredTargetPadId = null
                    onDragActiveChange(false)
                  }
                )
              }
          } else {
            Modifier
          }

          PadCell(
            pad = pad,
            slotNumber = bankOffset + localIndex + 1,
            playbackState = playbackState,
            density = density,
            isEditMode = isEditMode,
            isMultiSelectMode = isMultiSelectMode,
            isSelected = pad != null && pad.id in selectedPadIds,
            isMidiLearnMode = isMidiLearnMode,
            isMidiLearnArmed = pad != null && armedLearnPadId == pad.id,
            isCuePlaying = pad != null && cuePreviewPadId == pad.id,
            isDragging = pad != null && draggingPad?.id == pad.id,
            isDropTarget = pad != null && hoveredTargetPadId == pad.id && draggingPad?.id != pad.id,
            globalCrossfadeEnabled = globalCrossfadeEnabled,
            defaultPadShape = defaultPadShape,
            defaultCornerGlowColor = defaultCornerGlowColor,
            miniWaveformEnabled = false,
            isLoading = pad != null && preparingReversePadId == pad.id,
            gridRows = rows,
            gridCols = cols,
            reduceMotion = reduceMotion,
            hapticIntensity = hapticIntensity,
            swipePadVolumeEnabled = swipePadVolumeEnabled,
            doubleTapToStopEnabled = doubleTapToStopEnabled,
            onClick = clickHandler,
            onCustomizeClick = onPadCustomizeClick,
            onHoldStart = onPadHoldStart,
            onHoldRelease = onPadHoldRelease,
            onDoubleTapStop = { p -> onDoubleTapStop(p.id) },
            onSwipeVolumeChange = { p, vol, persist -> onSwipeVolumeChange(p.id, vol, persist) },
            modifier = Modifier
              .fillMaxWidth()
              .height(denseCellHeight)
              .then(cellModifier)
          )
        }
      }
    } else {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing)
      ) {
        for (r in 0 until rows) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(spacing)
          ) {
            for (c in 0 until cols) {
              val localIndex = r * cols + c
              val pad = pads.getOrNull(localIndex)
              val playbackState = pad?.let { playbackStates[it.id] }

              val cellModifier = if (pad != null) {
                val boundsMod = Modifier.onGloballyPositioned { coords ->
                  cellBounds[pad.id] = coords.boundsInRoot()
                }
                val dragMod = if (isEditMode && !isMultiSelectMode) {
                  Modifier.pointerInput(pad.id, isEditMode, isMultiSelectMode, hapticIntensity) {
                    detectDragGesturesAfterLongPress(
                      onDragStart = { localOffset ->
                        val box = cellBounds[pad.id] ?: return@detectDragGesturesAfterLongPress
                        performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
                        draggingPad = pad
                        dragRootPosition = box.topLeft + localOffset
                        hoveredTargetPadId = pad.id
                        onDragActiveChange(true)
                      },
                      onDrag = { change, dragAmount ->
                        change.consume()
                        dragRootPosition += dragAmount
                        hoveredTargetPadId = cellBounds.entries
                          .firstOrNull { it.value.contains(dragRootPosition) }
                          ?.key
                      },
                      onDragEnd = {
                        val fromId = draggingPad?.id
                        val toId = hoveredTargetPadId
                        if (fromId != null && toId != null && fromId != toId) {
                          onSwapPads(fromId, toId)
                        }
                        draggingPad = null
                        hoveredTargetPadId = null
                        onDragActiveChange(false)
                      },
                      onDragCancel = {
                        draggingPad = null
                        hoveredTargetPadId = null
                        onDragActiveChange(false)
                      }
                    )
                  }
                } else {
                  Modifier
                }
                boundsMod.then(dragMod)
              } else {
                Modifier
              }

              PadCell(
                pad = pad,
                slotNumber = bankOffset + localIndex + 1,
                playbackState = playbackState,
                density = density,
                isEditMode = isEditMode,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = pad != null && pad.id in selectedPadIds,
                isMidiLearnMode = isMidiLearnMode,
                isMidiLearnArmed = pad != null && armedLearnPadId == pad.id,
                isCuePlaying = pad != null && cuePreviewPadId == pad.id,
                isDragging = pad != null && draggingPad?.id == pad.id,
                isDropTarget = pad != null && hoveredTargetPadId == pad.id && draggingPad?.id != pad.id,
                globalCrossfadeEnabled = globalCrossfadeEnabled,
                defaultPadShape = defaultPadShape,
                defaultCornerGlowColor = defaultCornerGlowColor,
                miniWaveformEnabled = miniWaveformEnabled && density == PadDensity.STANDARD,
                isLoading = pad != null && preparingReversePadId == pad.id,
                gridRows = rows,
                gridCols = cols,
                reduceMotion = reduceMotion,
                hapticIntensity = hapticIntensity,
                swipePadVolumeEnabled = swipePadVolumeEnabled,
                doubleTapToStopEnabled = doubleTapToStopEnabled,
                onClick = clickHandler,
                onCustomizeClick = onPadCustomizeClick,
                onHoldStart = onPadHoldStart,
                onHoldRelease = onPadHoldRelease,
                onDoubleTapStop = { p -> onDoubleTapStop(p.id) },
                onSwipeVolumeChange = { p, vol, persist -> onSwipeVolumeChange(p.id, vol, persist) },
                modifier = Modifier
                  .weight(1f)
                  .height(cellHeight)
                  .then(cellModifier)
              )
            }
          }
        }
      }
    }

    val activeDrag = draggingPad
    if (activeDrag != null) {
      val localOffset = dragRootPosition - gridRootOrigin
      val tint = activeDrag.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.secondary
      Surface(
        modifier = Modifier.offset {
          IntOffset(
            (localOffset.x - 60f).roundToInt(),
            (localOffset.y - 36f).roundToInt()
          )
        },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(2.dp, tint),
        shadowElevation = 10.dp
      ) {
        Text(
          text = activeDrag.displayName,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
      }
    }
  }
}

@Composable
private fun EmptyFolderOnboarding(
  onPickFolderClick: () -> Unit,
  onLoadDemoTracks: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
      tonalElevation = 4.dp
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Box(
          modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
          )
        }

        Text(
          text = "SoundPad Pro",
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.onSurface
        )

        Text(
          text = "Pick a music folder or load the 6 built-in test tracks to start jamming immediately.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("MP3", "WAV", "OGG", "M4A", "FLAC").forEach { ext ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
              Text(
                text = ext,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          FilledIconButton(
            onClick = onPickFolderClick,
            modifier = Modifier
              .size(60.dp)
              .testTag("onboarding_select_folder_button"),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.FolderOpen,
              contentDescription = "Select Music Folder",
              modifier = Modifier.size(28.dp)
            )
          }

          OutlinedIconButton(
            onClick = onLoadDemoTracks,
            modifier = Modifier
              .size(60.dp)
              .testTag("onboarding_load_demo_button"),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.LibraryMusic,
              contentDescription = stringResource(R.string.cd_load_demo_tracks),
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyAudioFolderState(
  folderName: String,
  onRescanClick: () -> Unit,
  onPickFolderClick: () -> Unit,
  onLoadDemoTracks: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier.widthIn(max = 420.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.AudioFile,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(48.dp)
      )
      Text(
        text = "No supported audio files in \"$folderName\"",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
      )
      Text(
        text = "Add .mp3, .wav, .ogg, .m4a, or .flac files and tap Rescan, select another folder, or load test tracks.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedIconButton(
          onClick = onRescanClick,
          modifier = Modifier.size(52.dp)
        ) {
          Icon(Icons.Filled.Refresh, contentDescription = "Rescan folder")
        }
        FilledIconButton(
          onClick = onPickFolderClick,
          modifier = Modifier.size(52.dp)
        ) {
          Icon(Icons.Filled.FolderOpen, contentDescription = "Change folder")
        }
        OutlinedIconButton(
          onClick = onLoadDemoTracks,
          modifier = Modifier.size(52.dp)
        ) {
          Icon(Icons.Filled.LibraryMusic, contentDescription = stringResource(R.string.cd_load_demo_tracks))
        }
      }
    }
  }
}

@Composable
private fun EmptySearchState(
  query: String,
  onClearSearch: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = Icons.Filled.SearchOff,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(44.dp)
      )
      Text(
        text = "No pads match \"$query\"",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
      )
      FilledIconButton(
        onClick = onClearSearch,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(Icons.Filled.Close, contentDescription = "Clear search")
      }
    }
  }
}

@Composable
private fun ScanningState(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
      Text(
        text = "Scanning folder & preloading short samples...",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PadQuickActionSheet(
  pad: SoundPad,
  isCuePlaying: Boolean,
  onCustomize: () -> Unit,
  onDuplicateHere: () -> Unit,
  onDuplicateToOtherBank: () -> Unit,
  onCuePreview: () -> Unit,
  onEditAudio: () -> Unit,
  onDeletePad: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = pad.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "${pad.fileName}${if (pad.isClone) " · Clone (Independen)" else ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Filled.Close, contentDescription = "Tutup menu")
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      QuickActionRow(
        icon = Icons.Filled.Palette,
        title = "Customize Tampilan, Pan & Audio",
        subtitle = "Ubah warna, bentuk, pan Kiri/Kanan, speed, fade & choke",
        tint = MaterialTheme.colorScheme.primary,
        testTag = "quick_action_customize",
        onClick = onCustomize
      )
      QuickActionRow(
        icon = Icons.Filled.ContentCopy,
        title = "Duplikat Pad (Setting Independen)",
        subtitle = "Buat pad kloning di bank ini tanpa menggandakan file audio",
        tint = MaterialTheme.colorScheme.secondary,
        testTag = "quick_action_duplicate_here",
        onClick = onDuplicateHere
      )
      QuickActionRow(
        icon = Icons.Filled.DriveFileMove,
        title = "Duplikat ke Bank Lain",
        subtitle = "Salin pad dengan setting independen ke bank tujuan atau bank baru",
        tint = MaterialTheme.colorScheme.tertiary,
        testTag = "quick_action_duplicate_to_bank",
        onClick = onDuplicateToOtherBank
      )
      QuickActionRow(
        icon = Icons.Filled.Headphones,
        title = if (isCuePlaying) "Hentikan Cue Headphone" else "Cue Preview ke Headphone",
        subtitle = "Dengarkan preview tanpa mengganggu output utama",
        tint = MaterialTheme.colorScheme.tertiary,
        testTag = "quick_action_cue",
        onClick = onCuePreview
      )
      QuickActionRow(
        icon = Icons.Filled.ContentCut,
        title = "Edit / Cut Audio (Non-Destructive)",
        subtitle = "Potong start/end dari waveform tanpa merusak file asli",
        tint = MaterialTheme.colorScheme.primary,
        testTag = "quick_action_edit_audio",
        onClick = onEditAudio
      )
      QuickActionRow(
        icon = Icons.Filled.Delete,
        title = if (pad.isClone) "Hapus Pad Duplikat Ini" else "Hapus Pad dari Papan",
        subtitle = "File audio asli di penyimpanan HP tetap aman",
        tint = MaterialTheme.colorScheme.error,
        testTag = "quick_action_delete_pad",
        onClick = onDeletePad
      )

      Spacer(modifier = Modifier.height(6.dp))
    }
  }
}

@Composable
private fun QuickActionRow(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  tint: Color,
  testTag: String,
  onClick: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .testTag(testTag)
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(22.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun DuplicateToBankDialog(
  sourcePad: SoundPad,
  bankPages: List<BankPageUi>,
  onSelectTargetBank: (Int?) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages) {
    bankPages.distinctBy { it.bankKey }.map { it.bankKey to it.title }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Duplikat ke Bank Lain") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Pilih bank tujuan untuk duplikat \"${sourcePad.displayName}\":",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(
          modifier = Modifier.height(220.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(distinctBanks, key = { it.first }) { (bankKey, title) ->
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .testTag("duplicate_target_bank_$bankKey")
                .clickable { onSelectTargetBank(bankKey) },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = title,
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Bank ${bankKey + 1}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
          item {
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .testTag("duplicate_target_new_bank")
                .clickable { onSelectTargetBank(-1) },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                  text = "Buat Bank Baru",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    }
  )
}

@Composable
private fun ReorderBanksDialog(
  bankPages: List<BankPageUi>,
  onMoveBank: (bankKey: Int, delta: Int) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages) {
    bankPages.distinctBy { it.bankKey }.map { it.bankKey to it.title }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Atur Urutan Bank") },
    text = {
      LazyColumn(
        modifier = Modifier.height(260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        itemsIndexed(distinctBanks, key = { _, pair -> pair.first }) { idx, (bankKey, title) ->
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${idx + 1}. $title",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
              Row {
                IconButton(
                  enabled = idx > 0,
                  onClick = { onMoveBank(bankKey, -1) },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Geser bank ke atas")
                }
                IconButton(
                  enabled = idx < distinctBanks.lastIndex,
                  onClick = { onMoveBank(bankKey, 1) },
                  modifier = Modifier.size(36.dp)
                ) {
                  Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Geser bank ke bawah")
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text("Selesai")
      }
    }
  )
}

@Composable
private fun PadErrorDiagnosticDialog(
  pad: SoundPad,
  onOpenErrorLogs: () -> Unit,
  onDismiss: () -> Unit
) {
  val errorType = pad.errorType ?: PadErrorType.PLAYBACK_ERROR
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.Warning,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(32.dp)
      )
    },
    title = { Text("Pad Bermasalah: ${errorType.label}") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "File: ${pad.fileName}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = pad.errorMessage ?: "File audio tidak dapat diputar.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error
        )
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ) {
          Text(
            text = "Saran Perbaikan:\n${errorType.defaultSuggestion}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(10.dp)
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onOpenErrorLogs) {
        Text("Buka Error Log")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Tutup")
      }
    }
  )
}

@Composable
private fun PreviousCrashDialog(
  crashLog: AppErrorLog,
  onExportTxt: () -> Unit,
  onViewLogs: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Filled.BugReport,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
        modifier = Modifier.size(32.dp)
      )
    },
    title = { Text("App Sebelumnya Crash") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Aplikasi sempat berhenti mendadak pada sesi sebelumnya. Log crash telah disimpan otomatis. Ingin lihat atau kirim log (.txt)?",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        ) {
          Text(
            text = crashLog.message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp)
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onExportTxt) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Ekspor .txt")
      }
    },
    dismissButton = {
      Row {
        TextButton(onClick = onViewLogs) {
          Text("Lihat Log")
        }
        TextButton(onClick = onDismiss) {
          Text("Tutup")
        }
      }
    }
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BulkColorPickerDialog(
  selectedCount: Int,
  onSelectColor: (Long?) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Warna untuk $selectedCount Pad") },
    text = {
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable { onSelectColor(null) }
            .testTag("bulk_color_default"),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.surfaceVariant,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, contentDescription = "Reset default color", modifier = Modifier.size(18.dp))
          }
        }
        SoundPadColorPalette.PAD_PRESETS_12.forEachIndexed { idx, preset ->
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(Color(preset.argb.toInt()))
              .clickable { onSelectColor(preset.argb) }
              .testTag("bulk_color_preset_$idx")
          )
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    }
  )
}

@Composable
private fun BulkMoveToBankDialog(
  selectedCount: Int,
  bankPages: List<BankPageUi>,
  onSelectTargetBank: (Int?) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages) {
    bankPages.distinctBy { it.bankKey }.map { it.bankKey to it.title }
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Pindahkan $selectedCount Pad ke Bank") },
    text = {
      LazyColumn(
        modifier = Modifier.height(220.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(distinctBanks, key = { it.first }) { (bankKey, title) ->
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectTargetBank(bankKey) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(title, style = MaterialTheme.typography.titleSmall)
              Text("Bank ${bankKey + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
        item {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .clickable { onSelectTargetBank(null) },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Text("Buat Bank Baru", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Batal")
      }
    }
  )
}
