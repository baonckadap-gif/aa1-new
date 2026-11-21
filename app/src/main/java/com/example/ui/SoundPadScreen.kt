package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.example.R
import com.example.data.backup.BackupSyncStatus
import com.example.data.backup.RestoreSelectionOptions
import com.example.data.backup.SnapshotFileInfo
import com.example.domain.model.AnimationMode
import com.example.domain.model.AppErrorLog
import com.example.domain.model.AppLanguage
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.DragDropConflictMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.GridConfig
import com.example.domain.model.HapticIntensity
import com.example.domain.model.MasterRecordFormat
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
import android.os.SystemClock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.AdaptiveWidthClass
import com.example.ui.components.AudioEditorSheet
import com.example.ui.components.AudioOutputSheet
import com.example.ui.components.BottomConsoleBar
import com.example.ui.components.BulkMoveSlotsDialog
import com.example.ui.components.ErrorLogSheet
import com.example.ui.components.FocusModeOverlay
import com.example.ui.components.GridSettingsSheet
import com.example.ui.components.HoldToUnlockControl
import com.example.ui.components.LandscapeBankRail
import com.example.ui.components.LandscapeMixerSidePanel
import com.example.ui.components.LatencyDebugOverlay
import com.example.ui.components.MasterRecordSheet
import com.example.ui.components.MidiControllerSheet
import com.example.ui.components.MiuiGuideDialog
import com.example.ui.components.MovePadToSlotDialog
import com.example.ui.components.OnboardingDialog
import com.example.ui.components.PadCell
import com.example.ui.components.PadDensity
import com.example.ui.components.PadSettingsSheet
import com.example.ui.components.ProfileManagerSheet
import com.example.ui.components.ResetAllConfigDialog
import com.example.ui.components.RestorePreviewAndSelectDialog
import com.example.ui.components.RestoreProgressDialog
import com.example.ui.components.SchedulerSheet
import com.example.ui.components.SequencerSheet
import com.example.ui.components.SnapshotPickerDialog
import com.example.ui.components.TopControlBar
import com.example.ui.components.VoiceRecordSheet
import com.example.ui.components.performPadHaptic
import com.example.ui.components.resolveAdaptiveWidthClass
import com.example.ui.theme.SoundPadColorPalette
import com.example.ui.theme.SoundPadTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
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
  onAudioEffectsToggle: (Boolean) -> Unit = {},
  onVisualEffectsToggle: (Boolean) -> Unit = {},
  onAnimationModeChange: (AnimationMode) -> Unit = {},
  onLatencyDebugOverlayToggle: (Boolean) -> Unit = {},
  onResetLatencyMetrics: () -> Unit = {},
  onRetriggerModeChange: (com.example.domain.model.RetriggerMode) -> Unit = {},
  onMaxVoicesChange: (Int) -> Unit = {},
  onKeepScreenOnToggle: (Boolean) -> Unit,
  onToggleEditMode: () -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadTappedWithTime: ((SoundPad, Long) -> Unit)? = null,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onOpenTrackCustomizer: () -> Unit,
  onSelectEditingPad: (String) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldStartWithTime: ((SoundPad, Long) -> Unit)? = null,
  onPadHoldRelease: (SoundPad) -> Unit,
  onSwapPads: (fromPadId: String, toPadId: String) -> Unit,
  onMovePadToSlot: (padId: String, targetBankIndex: Int, targetSlotIndex: Int) -> Unit = { _, _, _ -> },
  onBulkMoveSelectedToSlot: (targetBankIndex: Int, baseTargetSlotIndex: Int) -> Unit = { _, _ -> },
  onDragDropConflictModeChanged: (DragDropConflictMode) -> Unit = {},
  onCompactBankSlots: (Int?) -> Unit = {},
  onSortBankAlphabetically: (Int?) -> Unit = {},
  onRestoreInitialPositions: (Int?) -> Unit = {},
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
  onToggleBleAutoReconnect: (Boolean) -> Unit = {},
  onTriggerBleReconnectNow: () -> Unit = {},
  onForgetSavedBleDevice: () -> Unit = {},
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
  onAddBank: () -> Unit = {},
  onRefreshOutputDevices: () -> Unit = {},
  onSelectMainOutputDevice: (Int?) -> Unit = {},
  onSelectCueOutputDevice: (Int?) -> Unit = {},
  onCueVolumeChange: (Float) -> Unit = {},
  onAudioBufferSizeChange: (Int) -> Unit = {},
  onRunLatencyTest: () -> Unit = {},
  onToggleCuePreview: (SoundPad) -> Unit = {},
  onMasterLimiterToggle: (Boolean) -> Unit = {},
  onDuckingToggle: (Boolean) -> Unit = {},
  onDuckingAttenuationChange: (Float) -> Unit = {},
  onAutoNormalizeToggle: (Boolean) -> Unit = {},
  onAnalyzeLoudnessAll: () -> Unit = {},
  onBackupAllData: () -> Unit = {},
  onRestoreAllData: () -> Unit = {},
  onDuplicatePadToBank: (String, Int?, Int?) -> Unit = { _, _, _ -> },
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
  onSelectMasterRecordFormat: (MasterRecordFormat) -> Unit = {},
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
  onDefaultPadColorModeChanged: (DefaultPadColorMode) -> Unit = {},
  onDefaultPadColorArgbChanged: (Long) -> Unit = {},
  onResetAllPadsToGray: () -> Unit = {},
  onSetFocusMode: (Boolean) -> Unit = {},
  onToggleFocusMode: () -> Unit = {},
  onStartInFocusModeToggle: (Boolean) -> Unit = {},
  onFocusCornerPanicToggle: (Boolean) -> Unit = {},
  onArmMidiLearnForFocusMode: () -> Unit = {},
  onClearFocusModeMidiNote: () -> Unit = {},
  onConsumeBankSwitchRequest: () -> Unit = {},
  onPickBackupFolderClick: () -> Unit = {},
  onDismissBackupPrompt: () -> Unit = {},
  onSyncBackupNow: () -> Unit = {},
  onSetAutoSyncEnabled: (Boolean) -> Unit = {},
  onSetIncludeWaveforms: (Boolean) -> Unit = {},
  onInspectRestoreFolderClick: () -> Unit = {},
  onPickRelinkMusicFolder: () -> Unit = {},
  onOpenSnapshotPickerClick: () -> Unit = {},
  onCloseSnapshotPicker: () -> Unit = {},
  onSelectSnapshot: (SnapshotFileInfo) -> Unit = {},
  onConfirmApplyRestore: (RestoreSelectionOptions) -> Unit = {},
  onDismissRestorePreview: () -> Unit = {},
  onExportZipClick: () -> Unit = {},
  onImportZipClick: () -> Unit = {},
  onShareZipClick: () -> Unit = {},
  onDeleteAllConfig: (Boolean) -> Unit = {},
  onDismissLowRamWarning: () -> Unit = {}
) {
  val isFocusMode = uiState.settings.isFocusMode
  BackHandler(enabled = isFocusMode) {
    onSetFocusMode(false)
  }
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
  val effectiveAnimationMode = if (isBatterySaver || !uiState.settings.visualEffectsEnabled) {
    AnimationMode.OFF
  } else {
    uiState.settings.animationMode
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
  var showBulkMoveSlotDialog by remember { mutableStateOf(false) }
  var renamingBankKey by remember { mutableStateOf<Int?>(null) }
  var quickActionPad by remember { mutableStateOf<SoundPad?>(null) }
  var duplicateToBankSourcePad by remember { mutableStateOf<SoundPad?>(null) }
  var moveToSlotTargetPad by remember { mutableStateOf<SoundPad?>(null) }
  var showResetAllConfigDialog by remember { mutableStateOf(false) }

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

  val memoizedFilteredPads = remember(
    uiState.pads,
    uiState.searchQuery,
    uiState.showFavoritesOnly,
    uiState.selectedCategoryTag
  ) { uiState.filteredPads }

  val memoizedBankPages = remember(
    uiState.pads,
    uiState.searchQuery,
    uiState.showFavoritesOnly,
    uiState.selectedCategoryTag,
    uiState.settings.gridConfig,
    uiState.activeProfile
  ) { uiState.bankPages }

  val pageCount = memoizedBankPages.size.coerceAtLeast(1)
  val sharedPagerState = rememberPagerState(pageCount = { pageCount })
  val rootCoroutineScope = rememberCoroutineScope()

  LaunchedEffect(uiState.requestedBankPageIndex, memoizedBankPages.size) {
    val targetIdx = uiState.requestedBankPageIndex
    if (targetIdx != null) {
      if (memoizedBankPages.isNotEmpty()) {
        val clamped = targetIdx.coerceIn(0, memoizedBankPages.lastIndex)
        sharedPagerState.animateScrollToPage(clamped)
      }
      onConsumeBankSwitchRequest()
    }
  }

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .semantics { testTagsAsResourceId = true },
    containerColor = MaterialTheme.colorScheme.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    topBar = {
      if (!isFocusMode) {
        val topMod = if (uiState.settings.isFullscreen) Modifier else Modifier.statusBarsPadding()
        val currentBankPage = memoizedBankPages.getOrNull(sharedPagerState.currentPage)
        val distinctBankCount = remember(memoizedBankPages) { memoizedBankPages.map { it.bankKey }.distinct().size }
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
          isGlobalCueEnabled = uiState.settings.isGlobalCueEnabled,
          isLocked = uiState.settings.isLocked,
          midiConnectedCount = uiState.midiDevices.size,
          isMidiLearnMode = uiState.isMidiLearnMode,
          errorLogCount = uiState.errorLogs.size,
          isSequencerPlaying = uiState.sequencerPlayback.isPlaying,
          activeSchedulesCount = uiState.scheduledTasks.count { it.enabled },
          canRescanCurrentBank = currentBankPage?.canRescanSubfolder == true,
          canReorderBanks = distinctBankCount > 1,
          isFocusMode = isFocusMode,
          onPickFolderClick = onPickFolderClick,
          onRescanClick = onRescanClick,
          onRescanCurrentBank = {
            val bankKey = currentBankPage?.bankKey
            if (bankKey != null && currentBankPage.canRescanSubfolder) {
              onRescanBank(bankKey)
            } else {
              onRescanClick()
            }
          },
          onOpenReorderBanks = {
            if (!uiState.settings.isLocked) showReorderBanksDialog = true
          },
          onOpenRenameCurrentBank = {
            if (!uiState.settings.isLocked) {
              renamingBankKey = currentBankPage?.bankKey ?: sharedPagerState.currentPage
            }
          },
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
          onToggleFocusMode = onToggleFocusMode,
          onOpenErrorLogs = { showErrorLogSheet = true },
          onOpenMiuiGuide = { showMiuiDialog = true },
          modifier = topMod
        )
      }
    },
    bottomBar = {
      AnimatedVisibility(visible = uiState.settings.isBottomBarVisible && !isFocusMode) {
        BottomConsoleBar(
          activeVoices = uiState.activeCount,
          masterVolume = uiState.settings.masterVolume,
          isEditMode = uiState.isEditMode,
          isLocked = uiState.settings.isLocked,
          isGlobalCueEnabled = uiState.settings.isGlobalCueEnabled,
          isMasterRecording = uiState.masterRecordState.isRecording,
          masterRecordElapsedMs = uiState.masterRecordState.elapsedMs,
          hasPads = uiState.pads.isNotEmpty(),
          animationMode = effectiveAnimationMode,
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
      val showBankSideRail = !isFocusMode &&
        isLandscapeStage &&
        uiState.pads.isNotEmpty() &&
        memoizedFilteredPads.isNotEmpty()
      val showMixerSidePanel = !isFocusMode &&
        (widthClass == AdaptiveWidthClass.EXPANDED || (isLandscapeStage && maxWidth >= 720.dp)) &&
        uiState.pads.isNotEmpty() &&
        !uiState.settings.isLocked

      Row(modifier = Modifier.fillMaxSize()) {
        if (showBankSideRail) {
          LandscapeBankRail(
            bankPages = memoizedBankPages,
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
          AnimatedVisibility(visible = uiState.settings.isLocked && !isFocusMode) {
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

          AnimatedVisibility(visible = uiState.isEditMode && !uiState.settings.isLocked && !isFocusMode) {
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
                      .minimumInteractiveComponentSize()
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
                      .minimumInteractiveComponentSize()
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
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_bulk_move), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_move_bank_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        enabled = hasSelected,
                        onClick = { showBulkMoveSlotDialog = true },
                        leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_bulk_move_slot), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("bulk_move_slot_button")
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
                          modifier = Modifier.minimumInteractiveComponentSize().testTag("bulk_clear_button")
                        ) {
                          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), modifier = Modifier.size(16.dp))
                        }
                      }
                    }
                  }
                } else {
                  val currentBankKeyForOrg = memoizedBankPages.getOrNull(sharedPagerState.currentPage)?.targetPadBankIndex
                  val currentConflictMode = uiState.settings.dragDropConflictMode
                  LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    item {
                      FilterChip(
                        selected = currentConflictMode == DragDropConflictMode.SHIFT,
                        onClick = {
                          val nextMode = if (currentConflictMode == DragDropConflictMode.SWAP) {
                            DragDropConflictMode.SHIFT
                          } else {
                            DragDropConflictMode.SWAP
                          }
                          onDragDropConflictModeChanged(nextMode)
                        },
                        leadingIcon = { Icon(Icons.Filled.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = {
                          Text(
                            text = if (currentConflictMode == DragDropConflictMode.SWAP) {
                              stringResource(R.string.edit_drag_mode_swap)
                            } else {
                              stringResource(R.string.edit_drag_mode_shift)
                            },
                            style = MaterialTheme.typography.labelSmall
                          )
                        },
                        modifier = Modifier.testTag("edit_drag_mode_chip")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        onClick = { onCompactBankSlots(currentBankKeyForOrg) },
                        leadingIcon = { Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_compact_slots), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("edit_compact_slots_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        onClick = { onSortBankAlphabetically(currentBankKeyForOrg) },
                        leadingIcon = { Icon(Icons.Filled.SortByAlpha, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_sort_az), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("edit_sort_az_button")
                      )
                    }
                    item {
                      FilterChip(
                        selected = false,
                        onClick = { onRestoreInitialPositions(currentBankKeyForOrg) },
                        leadingIcon = { Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        label = { Text(stringResource(R.string.edit_restore_initial), style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("edit_restore_initial_button")
                      )
                    }
                  }
                }
              }
            }
          }

      AnimatedVisibility(visible = !isFocusMode && (uiState.isMidiLearnMode || uiState.midiLearnTarget != null)) {
        val bannerText = when (val target = uiState.midiLearnTarget) {
          is MidiLearnTarget.PadNote -> "MIDI LEARN: Press a MIDI key for \"${target.padName}\""
          is MidiLearnTarget.CcControl -> "MIDI LEARN: Move MIDI fader/knob for \"${target.target.label}\""
          is MidiLearnTarget.ProgramChangeBank -> "MIDI LEARN: Kirim Program Change untuk pindah ke \"${target.bankLabel}\""
          is MidiLearnTarget.SequencerPlaylistNote -> "MIDI LEARN: Tekan tuts MIDI untuk memicu playlist \"${target.playlistName}\""
          MidiLearnTarget.FocusModeNote -> "MIDI LEARN: Tekan tuts MIDI untuk toggle Mode Fokus"
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
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_exit_midi_learn),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = !isFocusMode && uiState.newlyDetectedCount > 0) {
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
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_dismiss),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = !isFocusMode && uiState.statusMessage != null) {
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
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_dismiss),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = !isFocusMode && uiState.errorMessage != null) {
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
                modifier = Modifier.minimumInteractiveComponentSize(),
                colors = IconButtonDefaults.filledIconButtonColors(
                  containerColor = MaterialTheme.colorScheme.error,
                  contentColor = MaterialTheme.colorScheme.onError
                )
              ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = stringResource(R.string.cd_grant_folder_permission), modifier = Modifier.size(18.dp))
              }
            } else {
              IconButton(
                onClick = onDismissError,
                modifier = Modifier.minimumInteractiveComponentSize()
              ) {
                Icon(
                  imageVector = Icons.Filled.Close,
                  contentDescription = stringResource(R.string.cd_dismiss_error),
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      AnimatedVisibility(visible = uiState.showLowRamActivePadsWarning) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("low_ram_active_pads_warning_banner"),
          shape = RoundedCornerShape(10.dp),
          color = SoundPadTheme.colors.warningAmber.copy(alpha = 0.16f),
          border = BorderStroke(1.dp, SoundPadTheme.colors.warningAmber.copy(alpha = 0.7f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Memory,
              contentDescription = null,
              tint = SoundPadTheme.colors.warningAmber,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(
                R.string.low_ram_active_pads_warning,
                uiState.activeCount,
                uiState.deviceMemoryInfo.memoryClassMb
              ),
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
            )
            TextButton(
              onClick = onStopAll,
              modifier = Modifier.testTag("low_ram_warning_stop_all_button")
            ) {
              Text(
                text = stringResource(R.string.low_ram_warning_stop_all),
                color = MaterialTheme.colorScheme.error
              )
            }
            IconButton(
              onClick = onDismissLowRamWarning,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("low_ram_warning_dismiss_button")
            ) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.cd_dismiss_low_ram_warning),
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = !isFocusMode && uiState.persistentBackupState.status == BackupSyncStatus.ERROR_INACCESSIBLE) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("backup_inaccessible_banner"),
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.CloudOff,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = stringResource(R.string.backup_error_inaccessible_title),
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.weight(1f)
            )
            TextButton(
              onClick = onPickBackupFolderClick,
              modifier = Modifier.testTag("backup_inaccessible_repick_button")
            ) {
              Text(
                text = stringResource(R.string.backup_action_repick),
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }
      }

      AnimatedVisibility(visible = !isFocusMode && uiState.sequencerPlayback.isPlaying) {
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
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = if (seq.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = stringResource(
                  if (seq.isPaused) R.string.cd_sequencer_resume else R.string.cd_sequencer_pause
                ),
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(
              onClick = onSequencerNext,
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.cd_sequencer_next),
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(
              onClick = onStopSequencer,
              modifier = Modifier.minimumInteractiveComponentSize()
            ) {
              Icon(
                imageVector = Icons.Filled.Stop,
                contentDescription = stringResource(R.string.cd_sequencer_stop),
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
        memoizedFilteredPads.isEmpty() -> {
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
            visible = !isFocusMode &&
              uiState.settings.showFeatureTooltips &&
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

          if (!isFocusMode &&
            (uiState.pads.any { it.isFavorite } || uiState.availableCategoryTags.isNotEmpty() || uiState.showFavoritesOnly || uiState.selectedCategoryTag != null)
          ) {
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
            bankPages = memoizedBankPages,
            pagerState = sharedPagerState,
            showInlineBankStrip = !showBankSideRail && !isFocusMode,
            showPagerDots = !isFocusMode,
            isFocusMode = isFocusMode,
            gridConfig = uiState.settings.gridConfig,
            playbackStates = uiState.playbackStates,
            isEditMode = !isFocusMode && uiState.isEditMode && !uiState.settings.isLocked && uiState.searchQuery.isBlank() && !uiState.showFavoritesOnly && uiState.selectedCategoryTag == null,
            isMultiSelectMode = !isFocusMode && uiState.isMultiSelectMode,
            selectedPadIds = uiState.selectedPadIds,
            hapticIntensity = uiState.settings.hapticIntensity,
            swipePadVolumeEnabled = uiState.settings.swipePadVolumeEnabled,
            doubleTapToStopEnabled = uiState.settings.doubleTapToStopEnabled,
            globalRetriggerMode = uiState.settings.retriggerMode,
            isMidiLearnMode = !isFocusMode && uiState.isMidiLearnMode,
            armedLearnPadId = uiState.armedLearnPadId,
            cuePreviewPadId = uiState.cuePreviewPadId,
            preparingReversePadId = uiState.isPreparingReversePadId,
            isLocked = uiState.settings.isLocked,
            padGridSpacingDp = uiState.settings.padGridSpacingDp,
            reduceMotion = isBatterySaver || !uiState.settings.visualEffectsEnabled,
            visualEffectsEnabled = uiState.settings.visualEffectsEnabled,
            animationMode = effectiveAnimationMode,
            globalCrossfadeEnabled = uiState.settings.globalCrossfadeEnabled,
            miniWaveformEnabled = uiState.settings.miniWaveformEnabled,
            defaultPadShape = uiState.settings.defaultPadShape,
            defaultCornerGlowColor = Color(uiState.settings.defaultCornerGlowArgb.toInt()),
            defaultPadColorMode = uiState.settings.defaultPadColorMode,
            defaultCustomPadColorArgb = uiState.settings.defaultPadColorArgb,
            onRenameBankRequest = { bankKey ->
              if (!uiState.settings.isLocked) renamingBankKey = bankKey
            },
            onAddBankRequest = {
              if (!uiState.settings.isLocked) onAddBank()
            },
            onPadTapped = onPadTapped,
            onPadTappedWithTime = onPadTappedWithTime,
            onPadCustomizeClick = { pad ->
              if (!isFocusMode && !uiState.settings.isLocked) {
                if (pad.errorMessage != null) {
                  onPadTapped(pad)
                } else {
                  quickActionPad = pad
                }
              }
            },
            onPadHoldStart = onPadHoldStart,
            onPadHoldStartWithTime = onPadHoldStartWithTime,
            onPadHoldRelease = onPadHoldRelease,
            onDoubleTapStop = onDoubleTapStop,
            onSwipeVolumeChange = onSwipeVolumeChange,
            onTogglePadSelection = onTogglePadSelection,
            onMovePadToSlot = onMovePadToSlot,
            onEmptyPadClick = { if (!isFocusMode && !uiState.settings.isLocked) onPickFolderClick() },
            onExitFocusMode = { onSetFocusMode(false) },
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

      if (!uiState.settings.isBottomBarVisible && !isFocusMode) {
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

      if (uiState.settings.showLatencyDebugOverlay) {
        LatencyDebugOverlay(
          metrics = uiState.tapLatencyMetrics,
          animationMode = effectiveAnimationMode,
          onReset = onResetLatencyMetrics,
          onClose = { onLatencyDebugOverlayToggle(false) },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = 8.dp, end = 10.dp)
        )
      }

      if (isFocusMode) {
        val currentBankTitle = memoizedBankPages.getOrNull(sharedPagerState.currentPage)?.title ?: "Bank 1"
        FocusModeOverlay(
          currentBankTitle = currentBankTitle,
          activeVoices = uiState.activeCount,
          masterVolume = uiState.settings.masterVolume,
          focusCornerPanicEnabled = uiState.settings.focusCornerPanicEnabled,
          hapticIntensity = uiState.settings.hapticIntensity,
          onMasterVolumeChange = onMasterVolumeChanged,
          onStopAll = onStopAll,
          onExitFocusMode = { onSetFocusMode(false) },
          modifier = Modifier.fillMaxSize()
        )
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
      defaultPadColorMode = uiState.settings.defaultPadColorMode,
      defaultCustomPadColorArgb = uiState.settings.defaultPadColorArgb,
      globalRetriggerMode = uiState.settings.retriggerMode,
      globalMaxVoices = uiState.settings.maxVoices,
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
      onMoveToSlot = { moveToSlotTargetPad = editingPad },
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
      onAudioEffectsToggle = onAudioEffectsToggle,
      onVisualEffectsToggle = onVisualEffectsToggle,
      onAnimationModeChange = onAnimationModeChange,
      onLatencyDebugOverlayToggle = onLatencyDebugOverlayToggle,
      tapLatencyMetrics = uiState.tapLatencyMetrics,
      onResetLatencyMetrics = onResetLatencyMetrics,
      onRetriggerModeChange = onRetriggerModeChange,
      onMaxVoicesChange = onMaxVoicesChange,
      onPingLatencyEngine = {
        val testPad = uiState.pads.firstOrNull { it.errorMessage == null }
        if (testPad != null) {
          val nowNanos = SystemClock.elapsedRealtimeNanos()
          onPadTappedWithTime?.invoke(testPad, nowNanos) ?: onPadTapped(testPad)
        }
      },
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
      onDragDropConflictModeChange = onDragDropConflictModeChanged,
      onSwipePadVolumeToggle = onSwipePadVolumeToggle,
      onDoubleTapToStopToggle = onDoubleTapToStopToggle,
      onAppLanguageChange = onAppLanguageChanged,
      onShowFeatureTooltipsToggle = onShowFeatureTooltipsToggle,
      onDefaultPadColorModeChange = onDefaultPadColorModeChanged,
      onDefaultPadColorArgbChange = onDefaultPadColorArgbChanged,
      onResetAllPadsToGray = onResetAllPadsToGray,
      onEnterFocusMode = { onSetFocusMode(true) },
      onStartInFocusModeToggle = onStartInFocusModeToggle,
      onFocusCornerPanicToggle = onFocusCornerPanicToggle,
      onReplayOnboarding = { showOnboardingManual = true },
      onOpenMiuiGuide = { showMiuiDialog = true },
      backupState = uiState.persistentBackupState,
      onPickBackupFolderClick = onPickBackupFolderClick,
      onSetAutoSyncEnabled = onSetAutoSyncEnabled,
      onSetIncludeWaveforms = onSetIncludeWaveforms,
      onSyncBackupNow = onSyncBackupNow,
      onInspectRestoreFolderClick = onInspectRestoreFolderClick,
      onOpenSnapshotPickerClick = onOpenSnapshotPickerClick,
      onExportZipClick = onExportZipClick,
      onImportZipClick = onImportZipClick,
      onShareZipClick = onShareZipClick,
      onRequestDeleteAllConfig = { showResetAllConfigDialog = true },
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
      bankPages = memoizedBankPages,
      pcMappings = uiState.midiPcMappings,
      onArmBankPcLearn = onArmMidiLearnForBankPc,
      onClearBankPcMapping = onClearMidiPcMappingForBank,
      focusModeMidiNote = uiState.effectiveFocusModeMidiNote,
      activeProfileName = uiState.activeProfile?.name,
      bleReconnectStatus = uiState.bleReconnectStatus,
      onToggleBleAutoReconnect = onToggleBleAutoReconnect,
      onTriggerBleReconnectNow = onTriggerBleReconnectNow,
      onForgetSavedBleDevice = onForgetSavedBleDevice,
      onArmFocusModeLearn = onArmMidiLearnForFocusMode,
      onClearFocusModeNote = onClearFocusModeMidiNote,
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
      hasPendingBufferRebuild = uiState.hasPendingBufferRebuild,
      isRunningLatencyTest = uiState.isRunningLatencyTest,
      latencyTestReport = uiState.latencyTestReport,
      onRefreshDevices = onRefreshOutputDevices,
      onSelectMainDevice = onSelectMainOutputDevice,
      onSelectCueDevice = onSelectCueOutputDevice,
      onCueVolumeChange = onCueVolumeChange,
      onAudioBufferSizeChange = onAudioBufferSizeChange,
      onRunLatencyTest = onRunLatencyTest,
      onAudioEffectsToggle = onAudioEffectsToggle,
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
      onSelectFormat = onSelectMasterRecordFormat,
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

  if (showBulkMoveSlotDialog && uiState.selectedPadIds.isNotEmpty()) {
    val selectedList = uiState.pads.filter { it.id in uiState.selectedPadIds }
    BulkMoveSlotsDialog(
      selectedPads = selectedList,
      bankPages = memoizedBankPages,
      isBankExplicitMode = uiState.isBankExplicitMode,
      conflictMode = uiState.settings.dragDropConflictMode,
      onConfirmBulkMove = { targetBank, baseSlot ->
        onBulkMoveSelectedToSlot(targetBank, baseSlot)
        showBulkMoveSlotDialog = false
      },
      onDismiss = { showBulkMoveSlotDialog = false }
    )
  }

  val restoreUi = uiState.restoreUiState
  val isRestoreOverlayVisible = restoreUi.isInspecting ||
    restoreUi.pendingInspection != null ||
    restoreUi.isRestoreInProgress

  if ((!uiState.settings.hasCompletedOnboarding || showOnboardingManual) && !isRestoreOverlayVisible) {
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
      onRestoreOldConfig = onInspectRestoreFolderClick,
      onFinishOnboarding = {
        showOnboardingManual = false
        onHasCompletedOnboardingToggle(true)
      },
      onDismissSkip = {
        showOnboardingManual = false
        onHasCompletedOnboardingToggle(true)
      }
    )
  } else if (!isFocusMode && !isRestoreOverlayVisible && uiState.persistentBackupState.shouldShowFirstLaunchPrompt) {
    AlertDialog(
      onDismissRequest = onDismissBackupPrompt,
      containerColor = MaterialTheme.colorScheme.surface,
      icon = {
        Icon(
          imageVector = Icons.Filled.FolderOpen,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary
        )
      },
      title = {
        Text(
          text = stringResource(R.string.backup_prompt_title),
          style = MaterialTheme.typography.titleLarge
        )
      },
      text = {
        Text(
          text = stringResource(R.string.backup_prompt_desc),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      confirmButton = {
        Button(
          onClick = onPickBackupFolderClick,
          modifier = Modifier
            .height(48.dp)
            .testTag("backup_prompt_pick_button")
        ) {
          Text(stringResource(R.string.backup_prompt_pick_folder))
        }
      },
      dismissButton = {
        TextButton(
          onClick = onDismissBackupPrompt,
          modifier = Modifier.testTag("backup_prompt_later_button")
        ) {
          Text(stringResource(R.string.backup_prompt_later))
        }
      }
    )
  }

  if (restoreUi.isInspecting) {
    RestoreProgressDialog(
      progress = 0.25f,
      stageText = "Memeriksa & memvalidasi file backup…"
    )
  }

  val pendingInspection = restoreUi.pendingInspection
  if (pendingInspection != null && !restoreUi.isRestoreInProgress) {
    RestorePreviewAndSelectDialog(
      inspection = pendingInspection,
      currentMusicFolderName = restoreUi.selectedRelinkMusicName ?: uiState.settings.folderName,
      onPickMusicFolderForRelink = onPickRelinkMusicFolder,
      onRestoreAll = { options ->
        showOnboardingManual = false
        onConfirmApplyRestore(options)
      },
      onSkipDismiss = onDismissRestorePreview
    )
  }

  if (restoreUi.isRestoreInProgress) {
    RestoreProgressDialog(
      progress = restoreUi.restoreProgress,
      stageText = restoreUi.restoreStageText
    )
  }

  if (restoreUi.isSnapshotPickerOpen) {
    SnapshotPickerDialog(
      snapshots = restoreUi.availableSnapshots,
      hasBackupFolder = !uiState.settings.persistentBackupFolderUri.isNullOrBlank(),
      onSelectSnapshot = onSelectSnapshot,
      onPickFolderInstead = onInspectRestoreFolderClick,
      onDismiss = onCloseSnapshotPicker
    )
  }

  if (showResetAllConfigDialog) {
    ResetAllConfigDialog(
      hasBackupFolder = !uiState.settings.persistentBackupFolderUri.isNullOrBlank(),
      onConfirmReset = { alsoClearSaf ->
        showResetAllConfigDialog = false
        onDeleteAllConfig(alsoClearSaf)
      },
      onDismiss = { showResetAllConfigDialog = false }
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
        onDuplicatePadToBank(actionTargetPad.id, null, null)
      },
      onDuplicateToOtherBank = {
        quickActionPad = null
        duplicateToBankSourcePad = actionTargetPad
      },
      onMoveToSlot = {
        quickActionPad = null
        moveToSlotTargetPad = actionTargetPad
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
      onSelectTargetBank = { targetBankKey, targetSlotZeroBased ->
        onDuplicatePadToBank(dupSource.id, targetBankKey, targetSlotZeroBased)
        duplicateToBankSourcePad = null
      },
      onDismiss = { duplicateToBankSourcePad = null }
    )
  }

  val slotMoveTarget = moveToSlotTargetPad
  if (slotMoveTarget != null) {
    MovePadToSlotDialog(
      pad = slotMoveTarget,
      allPads = uiState.pads,
      bankPages = memoizedBankPages,
      isBankExplicitMode = uiState.isBankExplicitMode,
      padsPerBank = uiState.settings.gridConfig.padsPerBank,
      conflictMode = uiState.settings.dragDropConflictMode,
      onConfirmMove = { targetBankIndex, targetSlotIndex ->
        onMovePadToSlot(slotMoveTarget.id, targetBankIndex, targetSlotIndex)
        moveToSlotTargetPad = null
      },
      onDismiss = { moveToSlotTargetPad = null }
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

private data class GridCellHitTarget(
  val key: String,
  val boundsInRoot: Rect,
  val pageIdx: Int,
  val targetPadBankIndex: Int,
  val absoluteSlotIndex: Int,
  val occupiedPad: SoundPad?
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BankedPadMatrix(
  bankPages: List<BankPageUi>,
  pagerState: PagerState,
  showInlineBankStrip: Boolean,
  showPagerDots: Boolean,
  isFocusMode: Boolean,
  gridConfig: GridConfig,
  playbackStates: Map<String, PadPlaybackState>,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean,
  selectedPadIds: Set<String>,
  hapticIntensity: HapticIntensity,
  swipePadVolumeEnabled: Boolean,
  doubleTapToStopEnabled: Boolean,
  globalRetriggerMode: com.example.domain.model.RetriggerMode = com.example.domain.model.RetriggerMode.RESTART,
  isMidiLearnMode: Boolean,
  armedLearnPadId: String?,
  cuePreviewPadId: String?,
  preparingReversePadId: String?,
  isLocked: Boolean,
  padGridSpacingDp: Int,
  reduceMotion: Boolean,
  visualEffectsEnabled: Boolean,
  animationMode: AnimationMode = AnimationMode.FULL,
  globalCrossfadeEnabled: Boolean,
  miniWaveformEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  defaultPadColorMode: DefaultPadColorMode,
  defaultCustomPadColorArgb: Long,
  onRenameBankRequest: (Int) -> Unit,
  onAddBankRequest: () -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadTappedWithTime: ((SoundPad, Long) -> Unit)? = null,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldStartWithTime: ((SoundPad, Long) -> Unit)? = null,
  onPadHoldRelease: (SoundPad) -> Unit,
  onDoubleTapStop: (String) -> Unit,
  onSwipeVolumeChange: (String, Float, Boolean) -> Unit,
  onTogglePadSelection: (String) -> Unit,
  onMovePadToSlot: (padId: String, targetBankIndex: Int, targetSlotIndex: Int) -> Unit,
  onEmptyPadClick: () -> Unit,
  onExitFocusMode: () -> Unit,
  modifier: Modifier = Modifier
) {
  val latestPlaybackStates by rememberUpdatedState(playbackStates)
  val playbackStatesProvider = remember { { latestPlaybackStates } }
  val pageCount = bankPages.size.coerceAtLeast(1)
  val coroutineScope = rememberCoroutineScope()
  val bankListState = rememberLazyListState()
  val haptic = LocalHapticFeedback.current
  val topBarTokens = SoundPadTheme.topBarTokens
  val extColors = SoundPadTheme.colors
  val spacing = SoundPadTheme.spacing

  val visibleCells = remember { mutableStateMapOf<String, GridCellHitTarget>() }
  var pagerBoxBounds by remember { mutableStateOf(Rect.Zero) }
  var draggingPad by remember { mutableStateOf<SoundPad?>(null) }
  var dragRootPosition by remember { mutableStateOf(Offset.Zero) }
  var hoveredTargetKey by remember { mutableStateOf<String?>(null) }

  val isDragEnabled = isEditMode && !isMultiSelectMode && !isFocusMode && !isLocked
  val currentPageState by rememberUpdatedState(pagerState.currentPage)
  val latestOnMovePadToSlot by rememberUpdatedState(onMovePadToSlot)
  val edgeThresholdPx = with(LocalDensity.current) { 44.dp.toPx() }

  val edgeScrollDir = remember(draggingPad?.id, dragRootPosition, pagerBoxBounds, pagerState.currentPage, pageCount, edgeThresholdPx) {
    if (draggingPad != null && pagerBoxBounds.width > edgeThresholdPx * 2f) {
      when {
        dragRootPosition.x < pagerBoxBounds.left + edgeThresholdPx && pagerState.currentPage > 0 -> -1
        dragRootPosition.x > pagerBoxBounds.right - edgeThresholdPx && pagerState.currentPage < pageCount - 1 -> 1
        else -> 0
      }
    } else {
      0
    }
  }

  LaunchedEffect(draggingPad?.id, edgeScrollDir, pageCount) {
    val activeDrag = draggingPad ?: return@LaunchedEffect
    if (edgeScrollDir == 0) return@LaunchedEffect
    delay(450L)
    while (isActive && draggingPad?.id == activeDrag.id) {
      val nextPage = (pagerState.currentPage + edgeScrollDir).coerceIn(0, (pageCount - 1).coerceAtLeast(0))
      if (nextPage != pagerState.currentPage) {
        performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.TextHandleMove)
        pagerState.animateScrollToPage(nextPage)
        hoveredTargetKey = null
      }
      delay(650L)
    }
  }

  LaunchedEffect(pagerState.currentPage, bankPages.size) {
    if (pagerState.currentPage in bankPages.indices) {
      bankListState.animateScrollToItem(pagerState.currentPage)
    }
    val cur = pagerState.currentPage
    val staleKeys = visibleCells.filterValues { abs(it.pageIdx - cur) > 1 }.keys.toList()
    staleKeys.forEach { visibleCells.remove(it) }
  }

  LaunchedEffect(isDragEnabled) {
    if (!isDragEnabled) {
      draggingPad = null
      hoveredTargetKey = null
    }
  }

  Column(modifier = modifier.fillMaxSize()) {
    if (showInlineBankStrip) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = topBarTokens.bankTabRowHorizontalPaddingDp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          LazyRow(
            state = bankListState,
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs)
          ) {
            itemsIndexed(bankPages, key = { index, page -> "${page.bankKey}_${page.subPageIndex}_$index" }) { index, page ->
              val isCurrent = pagerState.currentPage == index
              val hasActiveInBank = page.pads.any { playbackStates[it.id]?.isPlaying == true }
              Column(
                modifier = Modifier
                  .height(topBarTokens.bankTabHeightDp)
                  .width(IntrinsicSize.Max)
                  .clip(MaterialTheme.shapes.small)
                  .testTag("bank_tab_$index")
                  .combinedClickable(
                    onClick = {
                      coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                      }
                    },
                    onLongClick = {
                      if (!isLocked) {
                        performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
                        onRenameBankRequest(page.bankKey)
                      }
                    }
                  ),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Row(
                  modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = topBarTokens.bankTabHorizontalPaddingDp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                  if (hasActiveInBank) {
                    Box(
                      modifier = Modifier
                        .size(topBarTokens.bankActiveDotDp)
                        .clip(CircleShape)
                        .background(extColors.playModeActiveGreen)
                    )
                  }
                  Text(
                    text = page.title,
                    style = MaterialTheme.typography.labelMedium.copy(
                      fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarTokens.bankTabIndicatorHeightDp)
                    .background(
                      if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent
                    )
                )
              }
            }
          }

          val addTooltipState = rememberTooltipState()
          TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
              PlainTooltip(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
              ) {
                Text(
                  text = stringResource(R.string.tooltip_add_bank),
                  style = MaterialTheme.typography.labelSmall
                )
              }
            },
            state = addTooltipState
          ) {
            IconButton(
              onClick = onAddBankRequest,
              enabled = !isLocked,
              modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("add_bank_button")
            ) {
              Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_add_bank),
                tint = if (isLocked) {
                  MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = topBarTokens.disabledAlpha)
                } else {
                  MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(topBarTokens.bankAddIconDp)
              )
            }
          }
        }

        HorizontalDivider(
          thickness = topBarTokens.bankTabDividerDp,
          color = MaterialTheme.colorScheme.outlineVariant
        )
      }
    }

    val dragGestureModifier = if (isDragEnabled) {
      Modifier.pointerInput(isDragEnabled, hapticIntensity) {
        detectDragGesturesAfterLongPress(
          onDragStart = { localOffset ->
            val startRoot = pagerBoxBounds.topLeft + localOffset
            val activePage = currentPageState
            val hit = visibleCells.values.firstOrNull { target ->
              target.pageIdx == activePage &&
                target.occupiedPad != null &&
                target.boundsInRoot.contains(startRoot)
            } ?: return@detectDragGesturesAfterLongPress
            val sourcePad = hit.occupiedPad ?: return@detectDragGesturesAfterLongPress
            performPadHaptic(haptic, hapticIntensity, HapticFeedbackType.LongPress)
            draggingPad = sourcePad
            dragRootPosition = startRoot
            hoveredTargetKey = hit.key
          },
          onDrag = { change, dragAmount ->
            if (draggingPad == null) return@detectDragGesturesAfterLongPress
            change.consume()
            dragRootPosition += dragAmount
            val activePage = currentPageState
            hoveredTargetKey = visibleCells.values.firstOrNull { target ->
              target.pageIdx == activePage && target.boundsInRoot.contains(dragRootPosition)
            }?.key
          },
          onDragEnd = {
            val dragged = draggingPad
            val activePage = currentPageState
            val target = hoveredTargetKey?.let { visibleCells[it] }?.takeIf { it.pageIdx == activePage }
              ?: visibleCells.values.firstOrNull {
                it.pageIdx == activePage && it.boundsInRoot.contains(dragRootPosition)
              }
            if (dragged != null && target != null && target.occupiedPad?.id != dragged.id) {
              latestOnMovePadToSlot(dragged.id, target.targetPadBankIndex, target.absoluteSlotIndex)
            }
            draggingPad = null
            hoveredTargetKey = null
          },
          onDragCancel = {
            draggingPad = null
            hoveredTargetKey = null
          }
        )
      }
    } else {
      Modifier
    }

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .onGloballyPositioned { coords ->
          pagerBoxBounds = coords.boundsInRoot()
        }
        .then(dragGestureModifier)
    ) {
      HorizontalPager(
        state = pagerState,
        userScrollEnabled = draggingPad == null,
        modifier = Modifier.fillMaxSize()
      ) { pageIndex ->
        val page = bankPages.getOrNull(pageIndex) ?: BankPageUi(
          bankKey = pageIndex,
          title = "B${pageIndex + 1}",
          pads = emptyList(),
          canRescanSubfolder = false
        )
        PadMatrixGrid(
          page = page,
          pageIndex = pageIndex,
          rows = gridConfig.rows,
          cols = gridConfig.cols,
          padGridSpacingDp = padGridSpacingDp,
          isFocusMode = isFocusMode,
          playbackStatesProvider = playbackStatesProvider,
          isEditMode = isEditMode,
          isMultiSelectMode = isMultiSelectMode,
          isDragEnabled = isDragEnabled,
          selectedPadIds = selectedPadIds,
          hapticIntensity = hapticIntensity,
          swipePadVolumeEnabled = swipePadVolumeEnabled,
          doubleTapToStopEnabled = doubleTapToStopEnabled,
          globalRetriggerMode = globalRetriggerMode,
          isMidiLearnMode = isMidiLearnMode,
          armedLearnPadId = armedLearnPadId,
          cuePreviewPadId = cuePreviewPadId,
          preparingReversePadId = preparingReversePadId,
          draggingPadId = draggingPad?.id,
          hoveredTargetKey = hoveredTargetKey,
          reduceMotion = reduceMotion,
          visualEffectsEnabled = visualEffectsEnabled,
          animationMode = animationMode,
          globalCrossfadeEnabled = globalCrossfadeEnabled,
          miniWaveformEnabled = miniWaveformEnabled,
          defaultPadShape = defaultPadShape,
          defaultCornerGlowColor = defaultCornerGlowColor,
          defaultPadColorMode = defaultPadColorMode,
          defaultCustomPadColorArgb = defaultCustomPadColorArgb,
          onRegisterCellTarget = { target -> visibleCells[target.key] = target },
          onUnregisterCellTarget = { key -> visibleCells.remove(key) },
          onPadTapped = onPadTapped,
          onPadTappedWithTime = onPadTappedWithTime,
          onPadCustomizeClick = onPadCustomizeClick,
          onPadHoldStart = onPadHoldStart,
          onPadHoldStartWithTime = onPadHoldStartWithTime,
          onPadHoldRelease = onPadHoldRelease,
          onDoubleTapStop = onDoubleTapStop,
          onSwipeVolumeChange = onSwipeVolumeChange,
          onTogglePadSelection = onTogglePadSelection,
          onEmptyPadClick = onEmptyPadClick,
          onExitFocusMode = onExitFocusMode,
          modifier = Modifier.fillMaxSize()
        )
      }

      if (draggingPad != null && edgeScrollDir != 0) {
        Surface(
          modifier = Modifier
            .align(if (edgeScrollDir < 0) Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = 8.dp),
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
        ) {
          Icon(
            imageVector = if (edgeScrollDir < 0) {
              Icons.AutoMirrored.Filled.KeyboardArrowLeft
            } else {
              Icons.AutoMirrored.Filled.KeyboardArrowRight
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(8.dp)
          )
        }
      }

      val activeDrag = draggingPad
      if (activeDrag != null) {
        val localOffset = dragRootPosition - pagerBoxBounds.topLeft
        val tint = activeDrag.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.secondary
        val hoveredTarget = hoveredTargetKey?.let { visibleCells[it] }
        val targetSlotLabel = hoveredTarget?.let { " → #${it.absoluteSlotIndex + 1}" } ?: ""
        Surface(
          modifier = Modifier.offset {
            IntOffset(
              (localOffset.x - 68f).roundToInt(),
              (localOffset.y - 42f).roundToInt()
            )
          },
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(2.dp, tint),
          shadowElevation = 10.dp
        ) {
          Text(
            text = "${activeDrag.displayName}$targetSlotLabel",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
          )
        }
      }
    }

    // Dot indicator below the pad grid (supports sliding window for >12 subpages)
    if (showInlineBankStrip && showPagerDots) {
      val maxDots = topBarTokens.maxPagerDots
      val windowStart = when {
        pageCount <= maxDots -> 0
        pagerState.currentPage <= maxDots / 2 -> 0
        pagerState.currentPage >= pageCount - (maxDots - maxDots / 2) -> pageCount - maxDots
        else -> pagerState.currentPage - maxDots / 2
      }
      val windowEnd = minOf(pageCount, windowStart + maxDots)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = topBarTokens.pagerDotsVerticalPaddingDp)
          .testTag("bank_pager_dots"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        for (dotIdx in windowStart until windowEnd) {
          val activeDot = pagerState.currentPage == dotIdx
          val dotWidth by animateDpAsState(
            targetValue = if (activeDot) {
              topBarTokens.pagerDotActiveWidthDp
            } else {
              topBarTokens.pagerDotInactiveWidthDp
            },
            label = "pager_dot_width"
          )
          Box(
            modifier = Modifier
              .padding(horizontal = topBarTokens.pagerDotSpacingDp)
              .size(width = dotWidth, height = topBarTokens.pagerDotHeightDp)
              .clip(CircleShape)
              .background(
                if (activeDot) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.outline.copy(alpha = topBarTokens.subtitleDimAlpha)
                }
              )
              .clickable {
                coroutineScope.launch {
                  pagerState.animateScrollToPage(dotIdx)
                }
              }
          )
        }
        if (pageCount > 1) {
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "${pagerState.currentPage + 1}/$pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("bank_pager_counter")
          )
        }
      }
    }
  }
}

@Composable
private fun PadMatrixGrid(
  page: BankPageUi,
  pageIndex: Int,
  rows: Int,
  cols: Int,
  padGridSpacingDp: Int,
  isFocusMode: Boolean,
  playbackStatesProvider: () -> Map<String, PadPlaybackState>,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean,
  isDragEnabled: Boolean,
  selectedPadIds: Set<String>,
  hapticIntensity: HapticIntensity,
  swipePadVolumeEnabled: Boolean,
  doubleTapToStopEnabled: Boolean,
  globalRetriggerMode: com.example.domain.model.RetriggerMode = com.example.domain.model.RetriggerMode.RESTART,
  isMidiLearnMode: Boolean,
  armedLearnPadId: String?,
  cuePreviewPadId: String?,
  preparingReversePadId: String?,
  draggingPadId: String?,
  hoveredTargetKey: String?,
  reduceMotion: Boolean,
  visualEffectsEnabled: Boolean,
  animationMode: AnimationMode,
  globalCrossfadeEnabled: Boolean,
  miniWaveformEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  defaultPadColorMode: DefaultPadColorMode,
  defaultCustomPadColorArgb: Long,
  onRegisterCellTarget: (GridCellHitTarget) -> Unit,
  onUnregisterCellTarget: (String) -> Unit,
  onPadTapped: (SoundPad) -> Unit,
  onPadTappedWithTime: ((SoundPad, Long) -> Unit)?,
  onPadCustomizeClick: (SoundPad) -> Unit,
  onPadHoldStart: (SoundPad) -> Unit,
  onPadHoldStartWithTime: ((SoundPad, Long) -> Unit)?,
  onPadHoldRelease: (SoundPad) -> Unit,
  onDoubleTapStop: (String) -> Unit,
  onSwipeVolumeChange: (String, Float, Boolean) -> Unit,
  onTogglePadSelection: (String) -> Unit,
  onEmptyPadClick: () -> Unit,
  onExitFocusMode: () -> Unit,
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
  val outerPadding: Dp = if (cols > 10 || rows > 10 || isFocusMode) SoundPadTheme.spacing.xs else SoundPadTheme.spacing.sm

  BoxWithConstraints(
    modifier = modifier
      .padding(horizontal = outerPadding, vertical = SoundPadTheme.spacing.xs)
      .then(
        if (isFocusMode) {
          Modifier.pointerInput(onExitFocusMode) {
            detectTapGestures(onDoubleTap = { onExitFocusMode() })
          }
        } else {
          Modifier
        }
      )
  ) {
    val totalHorizontalSpacing = spacing * (cols - 1)
    val cellWidth = ((maxWidth - totalHorizontalSpacing) / cols).coerceAtLeast(16.dp)
    val totalVerticalSpacing = spacing * (rows - 1)
    val focusCellHeight = ((maxHeight - totalVerticalSpacing) / rows).coerceAtLeast(48.dp)

    val density = remember(cellWidth, cols, rows) {
      when {
        cellWidth >= 64.dp && cols <= 6 && rows <= 6 -> PadDensity.STANDARD
        cellWidth >= 34.dp && cols <= 11 -> PadDensity.COMPACT
        else -> PadDensity.MICRO
      }
    }

    val totalSlots = rows * cols
    val clickHandler: (SoundPad) -> Unit = remember(isEditMode, isMultiSelectMode, onTogglePadSelection, onPadTapped) {
      { targetPad ->
        if (isEditMode && isMultiSelectMode) {
          onTogglePadSelection(targetPad.id)
        } else {
          onPadTapped(targetPad)
        }
      }
    }
    val clickHandlerWithTime: ((SoundPad, Long) -> Unit)? = remember(
      isEditMode,
      isMultiSelectMode,
      onTogglePadSelection,
      onPadTappedWithTime,
      onPadTapped
    ) {
      if (onPadTappedWithTime == null) null else { targetPad, downNanos ->
        if (isEditMode && isMultiSelectMode) {
          onTogglePadSelection(targetPad.id)
        } else {
          onPadTappedWithTime(targetPad, downNanos)
        }
      }
    }
    val doubleTapStopHandler: (SoundPad) -> Unit = remember(onDoubleTapStop) {
      { p -> onDoubleTapStop(p.id) }
    }
    val swipeVolumeHandler: (SoundPad, Float, Boolean) -> Unit = remember(onSwipeVolumeChange) {
      { p, vol, persist -> onSwipeVolumeChange(p.id, vol, persist) }
    }

    LazyVerticalGrid(
      columns = GridCells.Fixed(cols),
      modifier = Modifier
        .fillMaxSize()
        .testTag("pad_grid_scroll"),
      horizontalArrangement = Arrangement.spacedBy(spacing),
      verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
      items(
        count = totalSlots,
        key = { localIndex -> "slot_${page.bankKey}_${page.subPageIndex}_$localIndex" }
      ) { localIndex ->
        val pad = page.slotMap[localIndex]
        val absoluteSlotIndex = page.slotOffset + localIndex
        val targetKey = "${pageIndex}_$localIndex"

        if (isDragEnabled) {
          DisposableEffect(targetKey) {
            onDispose { onUnregisterCellTarget(targetKey) }
          }
        }

        val cellBoundsModifier = if (isDragEnabled) {
          Modifier.onGloballyPositioned { coords ->
            onRegisterCellTarget(
              GridCellHitTarget(
                key = targetKey,
                boundsInRoot = coords.boundsInRoot(),
                pageIdx = pageIndex,
                targetPadBankIndex = page.targetPadBankIndex,
                absoluteSlotIndex = absoluteSlotIndex,
                occupiedPad = pad
              )
            )
          }
        } else {
          Modifier
        }

        val sizeModifier = if (isFocusMode) {
          Modifier
            .fillMaxWidth()
            .height(focusCellHeight)
        } else {
          Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
        }

        val isHoveredTarget = hoveredTargetKey == targetKey &&
          draggingPadId != null &&
          pad?.id != draggingPadId

        IsolatedPadCellSlot(
          pad = pad,
          slotNumber = absoluteSlotIndex + 1,
          playbackStatesProvider = playbackStatesProvider,
          density = density,
          isEditMode = isEditMode,
          isMultiSelectMode = isMultiSelectMode,
          isSelected = pad != null && pad.id in selectedPadIds,
          isMidiLearnMode = isMidiLearnMode,
          isMidiLearnArmed = pad != null && armedLearnPadId == pad.id,
          isCuePlaying = pad != null && cuePreviewPadId == pad.id,
          isDragging = pad != null && draggingPadId == pad.id,
          isDropTarget = isHoveredTarget,
          globalCrossfadeEnabled = globalCrossfadeEnabled,
          defaultPadShape = defaultPadShape,
          defaultCornerGlowColor = defaultCornerGlowColor,
          defaultPadColorMode = defaultPadColorMode,
          defaultCustomPadColorArgb = defaultCustomPadColorArgb,
          miniWaveformEnabled = miniWaveformEnabled && density == PadDensity.STANDARD && totalSlots <= 64,
          isLoading = pad != null && preparingReversePadId == pad.id,
          gridRows = rows,
          gridCols = cols,
          reduceMotion = reduceMotion,
          visualEffectsEnabled = visualEffectsEnabled,
          animationMode = animationMode,
          hapticIntensity = hapticIntensity,
          swipePadVolumeEnabled = swipePadVolumeEnabled,
          doubleTapToStopEnabled = doubleTapToStopEnabled,
          globalRetriggerMode = globalRetriggerMode,
          onClick = clickHandler,
          onClickWithTime = clickHandlerWithTime,
          onEmptyClick = onEmptyPadClick,
          onEmptyDoubleClick = if (isFocusMode) onExitFocusMode else null,
          onCustomizeClick = onPadCustomizeClick,
          onHoldStart = onPadHoldStart,
          onHoldStartWithTime = onPadHoldStartWithTime,
          onHoldRelease = onPadHoldRelease,
          onDoubleTapStop = doubleTapStopHandler,
          onSwipeVolumeChange = swipeVolumeHandler,
          modifier = sizeModifier.then(cellBoundsModifier)
        )
      }
    }
  }
}

@Composable
private fun IsolatedPadCellSlot(
  pad: SoundPad?,
  slotNumber: Int,
  playbackStatesProvider: () -> Map<String, PadPlaybackState>,
  density: PadDensity,
  isEditMode: Boolean,
  isMultiSelectMode: Boolean,
  isSelected: Boolean,
  isMidiLearnMode: Boolean,
  isMidiLearnArmed: Boolean,
  isCuePlaying: Boolean,
  isDragging: Boolean,
  isDropTarget: Boolean,
  globalCrossfadeEnabled: Boolean,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  defaultPadColorMode: DefaultPadColorMode,
  defaultCustomPadColorArgb: Long,
  miniWaveformEnabled: Boolean,
  isLoading: Boolean,
  gridRows: Int,
  gridCols: Int,
  reduceMotion: Boolean,
  visualEffectsEnabled: Boolean,
  animationMode: AnimationMode,
  hapticIntensity: HapticIntensity,
  swipePadVolumeEnabled: Boolean,
  doubleTapToStopEnabled: Boolean,
  globalRetriggerMode: com.example.domain.model.RetriggerMode = com.example.domain.model.RetriggerMode.RESTART,
  onClick: (SoundPad) -> Unit,
  onClickWithTime: ((SoundPad, Long) -> Unit)?,
  onEmptyClick: () -> Unit,
  onEmptyDoubleClick: (() -> Unit)?,
  onCustomizeClick: (SoundPad) -> Unit,
  onHoldStart: (SoundPad) -> Unit,
  onHoldStartWithTime: ((SoundPad, Long) -> Unit)?,
  onHoldRelease: (SoundPad) -> Unit,
  onDoubleTapStop: (SoundPad) -> Unit,
  onSwipeVolumeChange: (SoundPad, Float, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val padId = pad?.id
  val isPlaying by remember(padId) {
    derivedStateOf { if (padId == null) false else playbackStatesProvider()[padId]?.isPlaying == true }
  }
  val isFadingOut by remember(padId) {
    derivedStateOf { if (padId == null) false else playbackStatesProvider()[padId]?.isFadingOut == true }
  }
  val activeVoiceCount by remember(padId) {
    derivedStateOf { if (padId == null) 1 else playbackStatesProvider()[padId]?.activeVoiceCount ?: 1 }
  }
  val durationMs by remember(padId) {
    derivedStateOf { if (padId == null) 0L else playbackStatesProvider()[padId]?.durationMs ?: 0L }
  }
  val remainingSec by remember(padId) {
    derivedStateOf {
      val st = if (padId == null) null else playbackStatesProvider()[padId]
      if (st != null && st.isPlaying && st.durationMs > 0L) {
        ((st.durationMs - st.positionMs).coerceAtLeast(0L) / 1000L)
      } else {
        -1L
      }
    }
  }
  val playbackSummary = remember(padId, isPlaying, isFadingOut, activeVoiceCount, durationMs, remainingSec) {
    if (padId == null || !isPlaying) {
      null
    } else {
      val posMs = if (remainingSec >= 0L && durationMs > 0L) {
        (durationMs - remainingSec * 1000L).coerceAtLeast(0L)
      } else {
        0L
      }
      PadPlaybackState(
        padId = padId,
        isPlaying = true,
        isFadingOut = isFadingOut,
        positionMs = posMs,
        durationMs = durationMs,
        progress = 0f,
        activeVoiceCount = activeVoiceCount
      )
    }
  }
  val progressProvider = remember(padId) {
    { if (padId == null) 0f else playbackStatesProvider()[padId]?.progress ?: 0f }
  }

  PadCell(
    pad = pad,
    slotNumber = slotNumber,
    playbackState = playbackSummary,
    density = density,
    isEditMode = isEditMode,
    isMultiSelectMode = isMultiSelectMode,
    isSelected = isSelected,
    isMidiLearnMode = isMidiLearnMode,
    isMidiLearnArmed = isMidiLearnArmed,
    isCuePlaying = isCuePlaying,
    isDragging = isDragging,
    isDropTarget = isDropTarget,
    globalCrossfadeEnabled = globalCrossfadeEnabled,
    defaultPadShape = defaultPadShape,
    defaultCornerGlowColor = defaultCornerGlowColor,
    defaultPadColorMode = defaultPadColorMode,
    defaultCustomPadColorArgb = defaultCustomPadColorArgb,
    miniWaveformEnabled = miniWaveformEnabled,
    isLoading = isLoading,
    gridRows = gridRows,
    gridCols = gridCols,
    reduceMotion = reduceMotion,
    visualEffectsEnabled = visualEffectsEnabled,
    animationMode = animationMode,
    globalRetriggerMode = globalRetriggerMode,
    progressProvider = progressProvider,
    hapticIntensity = hapticIntensity,
    swipePadVolumeEnabled = swipePadVolumeEnabled,
    doubleTapToStopEnabled = doubleTapToStopEnabled,
    onClick = onClick,
    onClickWithTime = onClickWithTime,
    onEmptyClick = onEmptyClick,
    onEmptyDoubleClick = onEmptyDoubleClick,
    onCustomizeClick = onCustomizeClick,
    onHoldStart = onHoldStart,
    onHoldStartWithTime = onHoldStartWithTime,
    onHoldRelease = onHoldRelease,
    onDoubleTapStop = onDoubleTapStop,
    onSwipeVolumeChange = onSwipeVolumeChange,
    modifier = modifier
  )
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
  onMoveToSlot: () -> Unit = {},
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
        icon = Icons.Filled.SwapHoriz,
        title = stringResource(R.string.action_move_to_slot),
        subtitle = "Saat ini Slot #${pad.positionIndex + 1} · Pindah ke nomor slot atau bank tertentu",
        tint = MaterialTheme.colorScheme.secondary,
        testTag = "quick_action_move_slot",
        onClick = onMoveToSlot
      )
      QuickActionRow(
        icon = Icons.Filled.ContentCopy,
        title = "Duplikat Pad (Setting Independen)",
        subtitle = "Buat pad kloning di slot kosong terdekat tanpa menggandakan file audio",
        tint = MaterialTheme.colorScheme.secondary,
        testTag = "quick_action_duplicate_here",
        onClick = onDuplicateHere
      )
      QuickActionRow(
        icon = Icons.AutoMirrored.Filled.DriveFileMove,
        title = "Duplikat ke Bank Lain",
        subtitle = "Salin pad dengan setting independen ke bank tujuan atau slot tertentu",
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
  onSelectTargetBank: (Int?, Int?) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages) {
    bankPages.distinctBy { it.targetPadBankIndex }.map { page ->
      val cleanTitle = page.title.replace(Regex(" \\(\\d+\\)$"), "")
      page.targetPadBankIndex to cleanTitle
    }
  }
  var optionalSlotText by remember { mutableStateOf("") }
  val parsedSlot = optionalSlotText.trim().toIntOrNull()
  val isSlotValid = optionalSlotText.isBlank() || (parsedSlot != null && parsedSlot in 1..999)
  val targetSlotZeroBased = parsedSlot?.minus(1)

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
        OutlinedTextField(
          value = optionalSlotText,
          onValueChange = { input ->
            if (input.length <= 4 && input.all { it.isDigit() }) {
              optionalSlotText = input
            }
          },
          label = { Text("Nomor Slot Tujuan (Opsional, 1–999)") },
          placeholder = { Text("Kosongkan = slot kosong pertama") },
          isError = !isSlotValid,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_target_slot_input")
        )
        LazyColumn(
          modifier = Modifier.height(200.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(distinctBanks, key = { it.first }) { (bankKey, title) ->
            Surface(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .testTag("duplicate_target_bank_$bankKey")
                .clickable(enabled = isSlotValid) { onSelectTargetBank(bankKey, targetSlotZeroBased) },
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
                .clickable(enabled = isSlotValid) { onSelectTargetBank(-1, targetSlotZeroBased) },
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
    bankPages.distinctBy { it.bankKey }.map { page ->
      page.bankKey to page.title.replace(Regex(" \\(\\d+\\)$"), "")
    }
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
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.cd_move_bank_up))
                }
                IconButton(
                  enabled = idx < distinctBanks.lastIndex,
                  onClick = { onMoveBank(bankKey, 1) },
                  modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                  Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.cd_move_bank_down))
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        val resetCd = stringResource(R.string.cd_reset_default_color)
        Box(
          modifier = Modifier
            .size(SoundPadTheme.spacing.minTouchTarget)
            .clip(CircleShape)
            .semantics { contentDescription = resetCd }
            .clickable { onSelectColor(null) }
            .testTag("bulk_color_default"),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            }
          }
        }
        SoundPadColorPalette.PAD_PRESETS_12.forEachIndexed { idx, preset ->
          val presetCd = stringResource(R.string.cd_select_preset_color, preset.name)
          Box(
            modifier = Modifier
              .size(SoundPadTheme.spacing.minTouchTarget)
              .clip(CircleShape)
              .semantics { contentDescription = presetCd }
              .clickable { onSelectColor(preset.argb) }
              .testTag("bulk_color_preset_$idx"),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(preset.argb.toInt()))
            )
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
private fun BulkMoveToBankDialog(
  selectedCount: Int,
  bankPages: List<BankPageUi>,
  onSelectTargetBank: (Int?) -> Unit,
  onDismiss: () -> Unit
) {
  val distinctBanks = remember(bankPages) {
    bankPages.distinctBy { it.targetPadBankIndex }.map { page ->
      page.targetPadBankIndex to page.title.replace(Regex(" \\(\\d+\\)$"), "")
    }
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
