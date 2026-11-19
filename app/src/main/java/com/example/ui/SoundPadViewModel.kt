package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.SoundPadApplication
import com.example.audio.AudioOutputRouter
import com.example.audio.AudioWaveformAndCutEngine
import com.example.audio.ReverseAudioCacheManager
import com.example.audio.SoundPadPlaybackService
import com.example.audio.toDomain
import com.example.audio.toEntity
import com.example.data.backup.AudioFileRelinker
import com.example.data.backup.BackupInspectionSummary
import com.example.data.backup.BackupSyncStatus
import com.example.data.backup.FullBackupSerializer
import com.example.data.backup.PersistentBackupUiState
import com.example.data.backup.PersistentConfigRestoreEngine
import com.example.data.backup.PersistentConfigSnapshotAssembler
import com.example.data.backup.RestoreSelectionOptions
import com.example.data.backup.RestoreUiState
import com.example.data.backup.SafPersistentConfigStorage
import com.example.data.backup.SnapshotFileInfo
import com.example.data.backup.SnapshotTrigger
import com.example.data.backup.ZipBackupArchiver
import com.example.data.demo.DemoTrackManager
import com.example.data.local.db.ErrorLogEntity
import com.example.data.local.db.MidiCcMappingEntity
import com.example.data.local.db.MidiPcBankMappingEntity
import com.example.data.local.db.PadProfileEntity
import com.example.data.local.db.PadSettingEntity
import com.example.data.local.db.SoundPadEntity
import com.example.data.profile.ExportedProfileBundle
import com.example.data.profile.ProfileJsonSerializer
import com.example.scheduler.PadSchedulerManager
import com.example.scheduler.toDomain
import com.example.scheduler.toEntity
import com.example.domain.model.AppErrorLog
import com.example.domain.model.AppLanguage
import com.example.domain.model.AudioOutputDeviceUi
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.MasterRecordSampleRate
import com.example.domain.model.MasterRecordState
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiCcTarget
import com.example.domain.model.MidiDeviceSummary
import com.example.domain.model.MidiLearnTarget
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadErrorType
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadProfile
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PadVisualConfig
import com.example.domain.model.PlayMode
import com.example.domain.model.ScheduleTargetType
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlaybackState
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import com.example.domain.model.ThemeMode
import com.example.domain.model.WaveformData
import com.example.error.CrashLogManager
import com.example.midi.BleMidiCandidate
import com.example.midi.MidiEvent
import com.example.midi.MidiMessageParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Immutable
data class BankPageUi(
  val bankKey: Int,
  val title: String,
  val pads: List<SoundPad>,
  val canRescanSubfolder: Boolean
)

@Immutable
data class SoundPadUiState(
  val settings: GlobalSettings = GlobalSettings(),
  val profiles: List<PadProfile> = emptyList(),
  val pads: List<SoundPad> = emptyList(),
  val playbackStates: Map<String, PadPlaybackState> = emptyMap(),
  val searchQuery: String = "",
  val isSearchVisible: Boolean = false,
  val isEditMode: Boolean = false,
  val editingPadId: String? = null,
  val audioEditorPadId: String? = null,
  val waveformData: WaveformData? = null,
  val isLoadingWaveform: Boolean = false,
  val isExportingCut: Boolean = false,
  val editorPreviewPositionMs: Long? = null,
  val isVoiceRecordSheetOpen: Boolean = false,
  val isRecordingVoice: Boolean = false,
  val voiceLevel: Float = 0f,
  val voiceElapsedMs: Long = 0L,
  val isScanning: Boolean = false,
  val preloadedCount: Int = 0,
  val newlyDetectedCount: Int = 0,
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val hasFolderPermissionError: Boolean = false,
  val midiDevices: List<MidiDeviceSummary> = emptyList(),
  val discoveredBleDevices: List<BleMidiCandidate> = emptyList(),
  val isScanningBleMidi: Boolean = false,
  val lastMidiActivity: String? = null,
  val midiCcMappings: List<MidiCcMapping> = emptyList(),
  val isMidiLearnMode: Boolean = false,
  val midiLearnTarget: MidiLearnTarget? = null,
  val showFavoritesOnly: Boolean = false,
  val selectedCategoryTag: String? = null,
  val outputDevices: List<AudioOutputDeviceUi> = emptyList(),
  val selectedMainOutputId: Int? = null,
  val selectedCueOutputId: Int? = null,
  val cuePreviewPadId: String? = null,
  val isNormalizingAll: Boolean = false,
  val canUndoEdit: Boolean = false,
  val canRedoEdit: Boolean = false,
  val isMultiSelectMode: Boolean = false,
  val selectedPadIds: Set<String> = emptySet(),
  val undoSnackbarVersion: Int = 0,
  val undoSnackbarMessage: String? = null,
  val errorLogs: List<AppErrorLog> = emptyList(),
  val pendingCrashLog: AppErrorLog? = null,
  val diagnosticPadId: String? = null,
  val isPreparingReversePadId: String? = null,
  // Phase 8 Completion: Master Output Recording, Scheduler, Sequencer, MIDI Program Change
  val isMasterRecordSheetOpen: Boolean = false,
  val masterRecordState: MasterRecordState = MasterRecordState(),
  val isSchedulerSheetOpen: Boolean = false,
  val scheduledTasks: List<ScheduledPadTask> = emptyList(),
  val isSequencerSheetOpen: Boolean = false,
  val sequencerPlaylists: List<SequencerPlaylist> = emptyList(),
  val sequencerPlayback: SequencerPlaybackState = SequencerPlaybackState(),
  val midiPcMappings: List<MidiProgramBankMapping> = emptyList(),
  val requestedBankPageIndex: Int? = null,
  val persistentBackupState: PersistentBackupUiState = PersistentBackupUiState(),
  val restoreUiState: RestoreUiState = RestoreUiState()
) {
  val activeCount: Int
    get() = playbackStates.count { it.value.isPlaying }

  val armedLearnPadId: String?
    get() = (midiLearnTarget as? MidiLearnTarget.PadNote)?.padId

  val activeProfile: PadProfile?
    get() = profiles.firstOrNull { it.id == settings.activeProfileId }

  val activeBankNames: Map<Int, String>
    get() = activeProfile?.bankNames ?: emptyMap()

  val activeBankOrder: List<Int>
    get() = activeProfile?.bankOrder ?: emptyList()

  val activeBankFolderUris: Map<Int, String>
    get() = activeProfile?.bankFolderUris ?: emptyMap()

  val isBankExplicitMode: Boolean
    get() = activeBankFolderUris.isNotEmpty() || activeBankOrder.isNotEmpty() || pads.any { it.subfolderName.isNotBlank() || it.bankIndex > 0 }

  val availableCategoryTags: List<String>
    get() = pads.mapNotNull { it.categoryTag.trim().takeIf(String::isNotEmpty) }.distinct().sorted()

  val filteredPads: List<SoundPad>
    get() {
      val q = searchQuery.trim().lowercase()
      val tagFilter = selectedCategoryTag?.trim()?.lowercase()
      return pads.filter { pad ->
        val matchesQuery = q.isEmpty() ||
          pad.displayName.lowercase().contains(q) ||
          pad.fileName.lowercase().contains(q) ||
          pad.extension.lowercase().contains(q) ||
          pad.categoryTag.lowercase().contains(q) ||
          pad.subfolderName.lowercase().contains(q)
        val matchesFav = !showFavoritesOnly || pad.isFavorite
        val matchesTag = tagFilter.isNullOrEmpty() || pad.categoryTag.trim().lowercase() == tagFilter
        matchesQuery && matchesFav && matchesTag
      }
    }

  val bankPages: List<BankPageUi>
    get() {
      val perBank = settings.gridConfig.padsPerBank.coerceAtLeast(1)
      val source = filteredPads
      val isFiltered = searchQuery.isNotBlank() || showFavoritesOnly || !selectedCategoryTag.isNullOrBlank()
      if (!isBankExplicitMode) {
        if (source.isEmpty()) return emptyList()
        val chunks = source.chunked(perBank)
        return chunks.mapIndexed { idx, list ->
          val title = activeBankNames[idx]?.takeIf { it.isNotBlank() } ?: "B${idx + 1}"
          BankPageUi(
            bankKey = idx,
            title = title,
            pads = list,
            canRescanSubfolder = false
          )
        }
      }

      val grouped = source.groupBy { it.bankIndex }
      val allKeys = buildList {
        activeBankOrder.forEach { if (it !in this) add(it) }
        (grouped.keys + activeBankFolderUris.keys + activeBankNames.keys).sorted().forEach {
          if (it !in this) add(it)
        }
      }.filter { key ->
        if (isFiltered) {
          grouped[key]?.isNotEmpty() == true
        } else {
          grouped[key]?.isNotEmpty() == true || activeBankFolderUris.containsKey(key) || key in activeBankOrder
        }
      }

      val pages = mutableListOf<BankPageUi>()
      allKeys.forEach { key ->
        val padsInBank = grouped[key].orEmpty()
        val baseTitle = activeBankNames[key]?.takeIf { it.isNotBlank() }
          ?: padsInBank.firstOrNull { it.subfolderName.isNotBlank() }?.subfolderName
          ?: "B${key + 1}"
        val canRescan = activeBankFolderUris.containsKey(key)
        if (padsInBank.isEmpty()) {
          pages.add(BankPageUi(bankKey = key, title = baseTitle, pads = emptyList(), canRescanSubfolder = canRescan))
        } else {
          val subChunks = padsInBank.chunked(perBank)
          subChunks.forEachIndexed { subIdx, subPads ->
            val pageTitle = if (subChunks.size > 1) "$baseTitle (${subIdx + 1})" else baseTitle
            pages.add(BankPageUi(bankKey = key, title = pageTitle, pads = subPads, canRescanSubfolder = canRescan))
          }
        }
      }
      return pages
    }

  val banks: List<List<SoundPad>>
    get() = bankPages.map { it.pads }

  val editingPad: SoundPad?
    get() = editingPadId?.let { id -> pads.firstOrNull { it.id == id } }

  val audioEditorPad: SoundPad?
    get() = audioEditorPadId?.let { id -> pads.firstOrNull { it.id == id } }

  val diagnosticPad: SoundPad?
    get() = diagnosticPadId?.let { id -> pads.firstOrNull { it.id == id } }
}

@OptIn(FlowPreview::class)
class SoundPadViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    private const val DEFAULT_PROFILE_ID = "default_profile"
    private const val DEFAULT_PROFILE_NAME = "Default Session"

    val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        val app = checkNotNull(AppHolder.application) { "SoundPadApplication not initialized" }
        return SoundPadViewModel(app) as T
      }
    }
  }

  private val app = application as SoundPadApplication
  private val settingsRepo = app.settingsRepository
  private val dao = app.database.soundPadDao()
  private val scanner = app.safScanner
  private val ramCache = app.ramCache
  private val audioEngine = app.audioEngine
  private val masterRecorder = app.masterRecorder
  private val sequencerEngine = app.sequencerEngine
  private val voiceRecorder = app.voiceRecorder
  private val midiController = app.midiController
  private val persistentConfigManager = app.persistentConfigManager

  private val _uiState = MutableStateFlow(SoundPadUiState())
  val uiState: StateFlow<SoundPadUiState> = _uiState.asStateFlow()

  private var folderObserverJob: Job? = null
  private var miniWaveformJob: Job? = null
  private var lastObservedFolderUri: String? = null
  private var lastLoadedProfileId: String? = null
  private var didAttemptInitialDemoSeed = false
  private var didApplyStartupFocus = false
  private val undoStack = ArrayDeque<List<SoundPad>>()
  private val redoStack = ArrayDeque<List<SoundPad>>()

  init {
    AppHolder.application = application
    refreshOutputDevices()

    audioEngine.onPlaybackError = { pad, errorType, message, stackTrace ->
      recordPlaybackError(pad, errorType, message, stackTrace)
    }

    SoundPadPlaybackService.onStopMasterRecordingRequested = {
      stopAndSaveMasterRecording()
    }

    persistentConfigManager.onAccessLostCallback = { uriStr, reason ->
      viewModelScope.launch {
        logErrorInternal(
          fileName = "Folder Backup Config",
          uriString = uriStr,
          type = PadErrorType.MISSING_PERMISSION,
          message = "Folder backup tidak dapat diakses: $reason",
          suggestion = "Pilih ulang folder backup config di Pengaturan atau banner peringatan."
        )
      }
    }
    persistentConfigManager.start()

    viewModelScope.launch {
      persistentConfigManager.backupState.collectLatest { bState ->
        _uiState.update { it.copy(persistentBackupState = bState) }
      }
    }

    // Observe DataStore changes (ignoring lastSyncAtMs updates so sync timestamp never triggers re-save loop)
    viewModelScope.launch {
      settingsRepo.settingsFlow
        .map { it.copy(persistentLastSyncAtMs = 0L) }
        .distinctUntilChanged()
        .drop(1)
        .collectLatest {
          persistentConfigManager.notifyConfigDirty("global_settings")
        }
    }

    viewModelScope.launch {
      dao.observeProfiles().drop(1).collectLatest {
        persistentConfigManager.notifyConfigDirty("profiles_table")
      }
    }

    viewModelScope.launch {
      dao.observeScheduledTasks().drop(1).collectLatest {
        persistentConfigManager.notifyConfigDirty("scheduler_table")
      }
    }

    viewModelScope.launch {
      dao.observeSequencerPlaylists().drop(1).collectLatest {
        persistentConfigManager.notifyConfigDirty("sequencer_table")
      }
    }

    viewModelScope.launch {
      val pendingCrash = withContext(Dispatchers.IO) { CrashLogManager.consumePendingCrash(app) }
      if (pendingCrash != null) {
        dao.insertErrorLog(pendingCrash.toEntity())
        _uiState.update { it.copy(pendingCrashLog = pendingCrash) }
      }
    }

    viewModelScope.launch {
      dao.observeErrorLogs().collectLatest { entities ->
        val mapped = entities.map { entity ->
          val type = runCatching { PadErrorType.valueOf(entity.errorType) }.getOrDefault(PadErrorType.PLAYBACK_ERROR)
          AppErrorLog(
            id = entity.id,
            timestampMs = entity.timestampMs,
            fileName = entity.fileName,
            uriString = entity.uriString,
            errorType = type,
            message = entity.message,
            suggestion = entity.suggestion,
            stackTrace = entity.stackTrace,
            padId = entity.padId
          )
        }
        _uiState.update { it.copy(errorLogs = mapped) }
      }
    }

    viewModelScope.launch {
      dao.observeProfiles().collectLatest { entities ->
        val mapped = entities.map { entity ->
          val preset = runCatching { GridPreset.valueOf(entity.gridPreset) }.getOrDefault(GridPreset.GRID_4X4)
          val profilePads = dao.getPadsForProfileOnce(entity.id)
          val masterEqPreset = runCatching { EqPreset.valueOf(entity.masterEqPreset) }.getOrDefault(EqPreset.FLAT)
          PadProfile(
            id = entity.id,
            name = entity.name,
            folderUri = entity.folderUri,
            gridConfig = GridConfig(preset, entity.gridRows, entity.gridCols),
            padCount = profilePads.size,
            bankNames = ProfileJsonSerializer.decodeBankNamesString(entity.bankNamesJson),
            bankOrder = ProfileJsonSerializer.decodeBankOrderString(entity.bankOrderJson),
            bankFolderUris = ProfileJsonSerializer.decodeBankFolderUrisString(entity.bankFolderUrisJson),
            masterEqEnabled = entity.masterEqEnabled,
            masterEqPreset = masterEqPreset,
            masterEqBandsDb = ProfileJsonSerializer.decodeEqBandsString(entity.masterEqBandsJson),
            createdAtMs = entity.createdAtMs,
            updatedAtMs = entity.updatedAtMs,
            isActive = entity.isActive,
            previewColorArgbs = profilePads.take(16).map { it.colorArgb }
          )
        }
        _uiState.update { it.copy(profiles = mapped) }
      }
    }

    viewModelScope.launch {
      settingsRepo.settingsFlow.collectLatest { settings ->
        audioEngine.updateGlobalSettings(settings)
        if (!didApplyStartupFocus) {
          didApplyStartupFocus = true
          if (settings.startInFocusMode && !settings.isFocusMode) {
            settingsRepo.setFocusMode(true)
          }
        }
        _uiState.update { state ->
          if (settings.isFocusMode && (state.isEditMode || state.isMultiSelectMode)) {
            state.copy(
              settings = settings,
              isEditMode = false,
              isMultiSelectMode = false,
              selectedPadIds = emptySet()
            )
          } else {
            state.copy(settings = settings)
          }
        }

        val profileChanged = settings.activeProfileId != lastLoadedProfileId
        val folderChanged = settings.folderUri != null && settings.folderUri != lastObservedFolderUri

        if (profileChanged) {
          lastLoadedProfileId = settings.activeProfileId
          loadProfilePads(settings.activeProfileId, settings.preloadShortAudio)
        }

        val uriStr = settings.folderUri
        if (uriStr == null && !didAttemptInitialDemoSeed) {
          didAttemptInitialDemoSeed = true
          val existing = dao.getPadsForProfileOnce(settings.activeProfileId)
          if (existing.isEmpty()) {
            loadDemoTracks(showBanner = false)
          }
        } else if (uriStr != null && (folderChanged || profileChanged)) {
          lastObservedFolderUri = uriStr
          if (uriStr != DemoTrackManager.DEMO_FOLDER_URI) {
            if (!profileChanged) {
              loadCachedAndScan(settings.activeProfileId, Uri.parse(uriStr), settings.preloadShortAudio)
            }
            startObservingFolder(Uri.parse(uriStr))
          } else {
            folderObserverJob?.cancel()
          }
        }
      }
    }

    viewModelScope.launch {
      audioEngine.playbackStates.collect { states ->
        _uiState.update { it.copy(playbackStates = states) }
      }
    }

    viewModelScope.launch {
      audioEngine.previewPositionMs.collect { posMs ->
        _uiState.update { it.copy(editorPreviewPositionMs = posMs) }
      }
    }

    viewModelScope.launch {
      audioEngine.cuePreviewPadId.collect { cueId ->
        _uiState.update { it.copy(cuePreviewPadId = cueId) }
      }
    }

    viewModelScope.launch {
      voiceRecorder.isRecording.collect { recording ->
        _uiState.update { it.copy(isRecordingVoice = recording) }
      }
    }

    viewModelScope.launch {
      voiceRecorder.level.collect { lvl ->
        _uiState.update { it.copy(voiceLevel = lvl) }
      }
    }

    viewModelScope.launch {
      voiceRecorder.elapsedMs.collect { elapsed ->
        _uiState.update { it.copy(voiceElapsedMs = elapsed) }
      }
    }

    viewModelScope.launch {
      midiController.connectedDevices.collect { devices ->
        _uiState.update { it.copy(midiDevices = devices) }
      }
    }

    viewModelScope.launch {
      midiController.discoveredBleDevices.collect { candidates ->
        _uiState.update { it.copy(discoveredBleDevices = candidates) }
      }
    }

    viewModelScope.launch {
      midiController.isScanningBle.collect { scanning ->
        _uiState.update { it.copy(isScanningBleMidi = scanning) }
      }
    }

    viewModelScope.launch {
      midiController.lastActivityLabel.collect { activity ->
        _uiState.update { it.copy(lastMidiActivity = activity) }
      }
    }

    viewModelScope.launch {
      midiController.bleStatusMessage.collect { msg ->
        if (msg != null) {
          _uiState.update { it.copy(statusMessage = msg) }
          midiController.clearBleMessage()
        }
      }
    }

    viewModelScope.launch {
      midiController.midiEvents.collect { event ->
        handleIncomingMidiEvent(event)
      }
    }

    // Master Output Recording flows
    viewModelScope.launch {
      masterRecorder.isRecording.collect { recording ->
        _uiState.update { it.copy(masterRecordState = it.masterRecordState.copy(isRecording = recording)) }
      }
    }
    viewModelScope.launch {
      masterRecorder.level.collect { lvl ->
        _uiState.update { it.copy(masterRecordState = it.masterRecordState.copy(level = lvl)) }
      }
    }
    viewModelScope.launch {
      masterRecorder.elapsedMs.collect { ms ->
        _uiState.update { it.copy(masterRecordState = it.masterRecordState.copy(elapsedMs = ms)) }
      }
    }

    // Scheduler tasks observer
    viewModelScope.launch {
      dao.observeScheduledTasks().collectLatest { entities ->
        val domainTasks = entities.map { it.toDomain() }
        _uiState.update { it.copy(scheduledTasks = domainTasks) }
      }
    }

    // Sequencer playlists & playback state observers
    viewModelScope.launch {
      dao.observeSequencerPlaylists().collectLatest { entities ->
        val domainLists = entities.map { it.toDomain() }
        sequencerEngine.syncPlaylists(domainLists)
        _uiState.update { it.copy(sequencerPlaylists = domainLists) }
      }
    }
    viewModelScope.launch {
      sequencerEngine.state.collect { seqState ->
        _uiState.update { it.copy(sequencerPlayback = seqState) }
      }
    }
  }

  fun loadDemoTracks(showBanner: Boolean = true) {
    viewModelScope.launch {
      _uiState.update { it.copy(isScanning = true, errorMessage = null, hasFolderPermissionError = false) }
      val profileId = _uiState.value.settings.activeProfileId
      val demoPads = DemoTrackManager.ensureDemoTracksInstalled(app)
      DemoTrackManager.exportDemoTracksToPhoneMusicFolder(app)

      val existingEntities = dao.getPadsForProfileOnce(profileId)
      val existingByUri = existingEntities.groupBy { it.uriString }
      val existingSettings = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

      val merged = mutableListOf<SoundPad>()
      var nextPos = (existingEntities.maxOfOrNull { it.positionIndex } ?: -1) + 1
      for (demo in demoPads) {
        val matches = existingByUri[demo.uriString]
        if (!matches.isNullOrEmpty()) {
          matches.forEach { entity ->
            merged.add(
              demo.copy(
                id = entity.id,
                displayName = entity.displayName,
                positionIndex = entity.positionIndex,
                colorArgb = entity.colorArgb,
                glowColorArgb = entity.glowColorArgb,
                glowIntensity = entity.glowIntensity,
                shapeStyle = entity.shapeStyle?.let { s -> runCatching { PadShapeStyle.valueOf(s) }.getOrNull() },
                borderColorArgb = entity.borderColorArgb,
                borderWidthDp = entity.borderWidthDp,
                cornerRadiusDp = entity.cornerRadiusDp,
                textColorArgb = entity.textColorArgb,
                surfaceFinish = runCatching { PadSurfaceFinish.valueOf(entity.surfaceFinish) }.getOrDefault(PadSurfaceFinish.CLASSIC),
                badgeIcon = runCatching { PadBadgeIcon.valueOf(entity.badgeIcon) }.getOrDefault(PadBadgeIcon.NONE),
                showSlotNumber = entity.showSlotNumber,
                showDurationLabel = entity.showDurationLabel,
                isClone = entity.isClone,
                midiNote = entity.midiNote,
                isFavorite = entity.isFavorite,
                categoryTag = entity.categoryTag,
                settings = existingSettings[entity.id].toDomain()
              )
            )
          }
        } else {
          merged.add(
            demo.copy(
              positionIndex = nextPos++,
              colorArgb = null,
              glowColorArgb = null,
              glowIntensity = 0f
            )
          )
        }
      }
      // Keep local voice tracks / cut exports too
      val demoUris = demoPads.map { it.uriString }.toSet()
      existingEntities.filter { it.uriString !in demoUris && it.uriString.startsWith("file://") }.forEach { entity ->
        merged.add(entityToDomain(entity, existingSettings[entity.id].toDomain()))
      }

      val normalized = merged.sortedBy { it.positionIndex }.mapIndexed { idx, p -> p.copy(positionIndex = idx) }
      val preloadedIds = if (_uiState.value.settings.preloadShortAudio) {
        ramCache.preloadShortSamples(normalized)
      } else {
        emptySet()
      }
      val ready = normalized.map { it.copy(isPreloadedInRam = it.id in preloadedIds) }

      val now = System.currentTimeMillis()
      val existingProfile = dao.getProfileById(profileId)
      val grid = _uiState.value.settings.gridConfig
      val gs = _uiState.value.settings
      val profile = PadProfileEntity(
        id = profileId,
        name = existingProfile?.name ?: DEFAULT_PROFILE_NAME,
        folderUri = DemoTrackManager.DEMO_FOLDER_URI,
        gridPreset = grid.preset.name,
        gridRows = grid.rows,
        gridCols = grid.cols,
        bankNamesJson = existingProfile?.bankNamesJson ?: "{}",
        masterEqEnabled = existingProfile?.masterEqEnabled ?: gs.masterEqEnabled,
        masterEqPreset = existingProfile?.masterEqPreset ?: gs.masterEqPreset.name,
        masterEqBandsJson = existingProfile?.masterEqBandsJson
          ?: ProfileJsonSerializer.encodeEqBandsString(gs.masterEqBandsDb),
        createdAtMs = existingProfile?.createdAtMs ?: now,
        updatedAtMs = now,
        isActive = true
      )
      val entities = ready.map { it.toPadEntity(profileId) }
      val settingEntities = ready.map { it.settings.toEntity(it.id) }
      dao.saveCompleteProfile(profile, entities, settingEntities)
      persistentConfigManager.notifyConfigDirty("demo_tracks")

      lastObservedFolderUri = DemoTrackManager.DEMO_FOLDER_URI
      settingsRepo.setSelectedFolder(DemoTrackManager.DEMO_FOLDER_URI, DemoTrackManager.DEMO_FOLDER_NAME)
      audioEngine.updatePlaylist(ready)
      _uiState.update {
        it.copy(
          pads = ready,
          isScanning = false,
          preloadedCount = preloadedIds.size,
          statusMessage = if (showBanner) "Loaded 6 sample tracks (also saved to Music/SoundPad Demo)" else null
        )
      }
    }
  }

  fun exportDemoTracksToMusicFolder() {
    viewModelScope.launch {
      val count = DemoTrackManager.exportDemoTracksToPhoneMusicFolder(app)
      _uiState.update {
        it.copy(
          statusMessage = if (count > 0) {
            "Saved $count sample WAV files to Music/SoundPad Demo"
          } else {
            "Sample tracks ready in app storage"
          }
        )
      }
    }
  }

  fun onFolderSelected(treeUri: Uri) {
    viewModelScope.launch {
      scanner.takePersistablePermission(treeUri)
      val previousFolderUri = _uiState.value.settings.folderUri
      val folderName = scanner.resolveFolderDisplayName(treeUri)
      settingsRepo.setSelectedFolder(treeUri.toString(), folderName)
      lastObservedFolderUri = treeUri.toString()
      audioEngine.stopAll()
      val profileId = _uiState.value.settings.activeProfileId
      scanAndPersist(
        profileId = profileId,
        treeUri = treeUri,
        preloadRam = _uiState.value.settings.preloadShortAudio,
        showAutoDetectBadge = false
      )
      startObservingFolder(treeUri)

      // Jika user memilih folder musik yang sebelumnya berbeda & di dalamnya terdapat .soundpadpro/,
      // tawarkan restore config otomatis dengan pencocokan pad langsung ke folder musik ini.
      val switchedToDifferentFolder = previousFolderUri.isNullOrBlank() ||
        previousFolderUri == DemoTrackManager.DEMO_FOLDER_URI ||
        previousFolderUri != treeUri.toString()
      if (switchedToDifferentFolder) {
        val inspection = withContext(Dispatchers.IO) {
          val docRoot = runCatching { DocumentFile.fromTreeUri(app, treeUri) }.getOrNull()
          val hasSubfolder = docRoot?.findFile(".soundpadpro")?.isDirectory == true
          if (hasSubfolder) {
            PersistentConfigRestoreEngine.inspectTreeUri(app, treeUri).getOrNull()
          } else {
            null
          }
        }
        if (inspection != null) {
          _uiState.update { state ->
            state.copy(
              restoreUiState = state.restoreUiState.copy(
                pendingInspection = inspection,
                selectedRelinkMusicUri = treeUri.toString(),
                selectedRelinkMusicName = folderName
              )
            )
          }
        }
      }
    }
  }

  fun rescanCurrentFolder() {
    val uriStr = _uiState.value.settings.folderUri ?: return
    if (uriStr == DemoTrackManager.DEMO_FOLDER_URI) {
      loadDemoTracks(showBanner = true)
      return
    }
    val treeUri = Uri.parse(uriStr)
    if (!scanner.hasPersistedReadPermission(treeUri)) {
      _uiState.update {
        it.copy(
          hasFolderPermissionError = true,
          errorMessage = "Folder permission expired. Please re-select your audio folder."
        )
      }
      return
    }
    viewModelScope.launch {
      scanAndPersist(
        profileId = _uiState.value.settings.activeProfileId,
        treeUri = treeUri,
        preloadRam = _uiState.value.settings.preloadShortAudio,
        showAutoDetectBadge = false
      )
    }
  }

  fun checkForNewFilesOnResume() {
    val uriStr = _uiState.value.settings.folderUri ?: return
    if (uriStr == DemoTrackManager.DEMO_FOLDER_URI) return
    val treeUri = Uri.parse(uriStr)
    if (!scanner.hasPersistedReadPermission(treeUri)) return
    viewModelScope.launch {
      scanAndPersist(
        profileId = _uiState.value.settings.activeProfileId,
        treeUri = treeUri,
        preloadRam = _uiState.value.settings.preloadShortAudio,
        showAutoDetectBadge = true
      )
    }
  }

  fun onPadTapped(pad: SoundPad) {
    if (_uiState.value.isMidiLearnMode) {
      _uiState.update {
        it.copy(
          midiLearnTarget = MidiLearnTarget.PadNote(pad.id, pad.displayName),
          statusMessage = "Waiting for MIDI key to map \"${pad.displayName}\"..."
        )
      }
      return
    }
    if (pad.errorMessage != null) {
      _uiState.update { it.copy(diagnosticPadId = pad.id) }
      return
    }
    if (_uiState.value.isEditMode) {
      if (_uiState.value.isMultiSelectMode) {
        togglePadSelection(pad.id)
      } else {
        _uiState.update { it.copy(editingPadId = pad.id) }
      }
      return
    }
    if (_uiState.value.settings.isGlobalCueEnabled) {
      toggleCuePreview(pad)
      return
    }
    val designatedPlaylist = _uiState.value.sequencerPlaylists.firstOrNull { it.triggerPadId == pad.id }
    if (designatedPlaylist != null) {
      sequencerEngine.updateAvailablePads(_uiState.value.pads)
      sequencerEngine.togglePlayPause(designatedPlaylist)
      return
    }
    audioEngine.triggerPad(pad)
  }

  fun stopSinglePad(padId: String) {
    audioEngine.stopPad(padId)
  }

  fun updatePadVolumeLive(padId: String, newVolume: Float, persist: Boolean) {
    val clamped = newVolume.coerceIn(0f, 1f)
    var updatedTarget: SoundPad? = null
    _uiState.update { state ->
      val updatedPads = state.pads.map { p ->
        if (p.id == padId) {
          val modified = p.copy(settings = p.settings.copy(volume = clamped))
          updatedTarget = modified
          modified
        } else {
          p
        }
      }
      audioEngine.updatePlaylist(updatedPads)
      state.copy(pads = updatedPads)
    }
    updatedTarget?.let { target ->
      audioEngine.updateActivePadParameters(target)
      if (persist) {
        viewModelScope.launch {
          dao.upsertPadSetting(target.settings.toEntity(padId))
          persistentConfigManager.notifyConfigDirty("pad_volume")
        }
      }
    }
  }

  fun onPadHoldStart(pad: SoundPad) {
    if (_uiState.value.isEditMode || _uiState.value.isMidiLearnMode) return
    audioEngine.triggerHoldStart(pad)
  }

  fun onPadHoldRelease(pad: SoundPad) {
    if (_uiState.value.isEditMode || _uiState.value.isMidiLearnMode) return
    audioEngine.triggerHoldRelease(pad.id)
  }

  fun closePadEditor() {
    _uiState.update { it.copy(editingPadId = null) }
  }

  fun openTrackCustomizer(padId: String? = null) {
    if (_uiState.value.settings.isLocked) {
      _uiState.update { it.copy(statusMessage = "Board is locked. Unlock Stage Mode to customize pads.") }
      return
    }
    val pads = _uiState.value.pads
    if (pads.isEmpty()) return
    val targetId = padId ?: _uiState.value.editingPadId ?: pads.first().id
    _uiState.update { it.copy(editingPadId = targetId) }
  }

  fun selectEditingPad(padId: String) {
    _uiState.update { it.copy(editingPadId = padId) }
  }

  fun duplicatePadForDifferentSettings(sourcePadId: String) {
    duplicatePadToBank(sourcePadId, targetBankIndex = null)
  }

  fun duplicatePadToBank(sourcePadId: String, targetBankIndex: Int?) {
    if (_uiState.value.settings.isLocked) return
    val state = _uiState.value
    val currentPads = state.pads
    val sourceIdx = currentPads.indexOfFirst { it.id == sourcePadId }
    if (sourceIdx < 0) return
    recordEditSnapshot(currentPads, "Duplikat pad dibuat")

    val sourcePad = currentPads[sourceIdx]
    val profileId = state.settings.activeProfileId
    val perBank = state.settings.gridConfig.padsPerBank.coerceAtLeast(1)

    val baseLabel = sourcePad.displayName.removeSuffix(" (Clone)").trim().ifEmpty { sourcePad.displayName }
    val existingNames = currentPads.map { it.displayName }.toSet()
    var cloneLabel = "$baseLabel (Clone)"
    var counter = 2
    while (cloneLabel in existingNames) {
      cloneLabel = "$baseLabel (Clone $counter)"
      counter++
    }

    val newPadId = "pad_clone_${UUID.randomUUID()}"

    // If duplicating into a specific bank (or new bank sentinel = -1), ensure explicit bank assignment cleanly
    val createNewBank = targetBankIndex == -1
    if (targetBankIndex == null) {
      // Duplicate right next to source in the same bank
      val clonedPad = sourcePad.copy(
        id = newPadId,
        displayName = cloneLabel,
        isClone = true,
        midiNote = null
      )
      val mutableList = currentPads.toMutableList()
      mutableList.add(sourceIdx + 1, clonedPad)
      val reindexed = mutableList.mapIndexed { idx, pad -> pad.copy(positionIndex = idx) }
      val savedClone = reindexed[sourceIdx + 1]

      audioEngine.updatePlaylist(reindexed)
      _uiState.update {
        it.copy(
          pads = reindexed,
          editingPadId = if (it.editingPadId != null) savedClone.id else null,
          statusMessage = "Duplikat \"${sourcePad.displayName}\" → \"$cloneLabel\""
        )
      }

      viewModelScope.launch {
        dao.insertClonedPadAndReindex(
          newPad = savedClone.toPadEntity(profileId),
          newSetting = savedClone.settings.toEntity(savedClone.id),
          reindexedPositions = reindexed.map { it.id to it.positionIndex }
        )
        persistentConfigManager.notifyConfigDirty("duplicate_pad")
      }
      return
    }

    // Cross-bank duplication
    val wasExplicit = state.isBankExplicitMode
    val promotedPads = if (!wasExplicit) {
      currentPads.mapIndexed { idx, p -> p.copy(bankIndex = idx / perBank) }
    } else {
      currentPads
    }

    val resolvedTargetBank = if (createNewBank) {
      ((promotedPads.maxOfOrNull { it.bankIndex } ?: 0) + 1)
    } else {
      targetBankIndex.coerceAtLeast(0)
    }
    val targetSubName = state.activeBankNames[resolvedTargetBank]
      ?: promotedPads.firstOrNull { it.bankIndex == resolvedTargetBank }?.subfolderName
      ?: "Bank ${resolvedTargetBank + 1}"

    val clonedPad = sourcePad.copy(
      id = newPadId,
      displayName = cloneLabel,
      bankIndex = resolvedTargetBank,
      subfolderName = targetSubName,
      isClone = true,
      midiNote = null
    )
    val reindexed = (promotedPads + clonedPad).mapIndexed { idx, p -> p.copy(positionIndex = idx) }
    val savedClone = reindexed.last()

    audioEngine.updatePlaylist(reindexed)
    _uiState.update {
      it.copy(
        pads = reindexed,
        editingPadId = if (it.editingPadId != null) savedClone.id else null,
        statusMessage = "Duplikat \"${sourcePad.displayName}\" → \"$targetSubName\""
      )
    }

    viewModelScope.launch {
      if (!wasExplicit) {
        promotedPads.forEach { p ->
          dao.updatePadBankIndex(p.id, p.bankIndex, p.subfolderName)
        }
        val existingProfile = dao.getProfileById(profileId)
        val existingOrder = ProfileJsonSerializer.decodeBankOrderString(existingProfile?.bankOrderJson)
        val newOrder = (existingOrder + (0..resolvedTargetBank)).distinct()
        val namesMap = ProfileJsonSerializer.decodeBankNamesString(existingProfile?.bankNamesJson).toMutableMap()
        if (!namesMap.containsKey(resolvedTargetBank)) {
          namesMap[resolvedTargetBank] = targetSubName
        }
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(namesMap),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(newOrder),
          bankFolderUrisJson = existingProfile?.bankFolderUrisJson ?: "{}"
        )
      } else if (createNewBank) {
        val existingProfile = dao.getProfileById(profileId)
        val existingOrder = ProfileJsonSerializer.decodeBankOrderString(existingProfile?.bankOrderJson)
        val newOrder = (existingOrder + resolvedTargetBank).distinct()
        val namesMap = ProfileJsonSerializer.decodeBankNamesString(existingProfile?.bankNamesJson).toMutableMap()
        namesMap[resolvedTargetBank] = targetSubName
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(namesMap),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(newOrder),
          bankFolderUrisJson = existingProfile?.bankFolderUrisJson ?: "{}"
        )
      }
      dao.insertClonedPadAndReindex(
        newPad = savedClone.toPadEntity(profileId),
        newSetting = savedClone.settings.toEntity(savedClone.id),
        reindexedPositions = reindexed.map { it.id to it.positionIndex }
      )
      persistentConfigManager.notifyConfigDirty("duplicate_pad_bank")
    }
  }

  fun deletePadFromBoard(padId: String) {
    if (_uiState.value.settings.isLocked) return
    val before = _uiState.value.pads
    val deletedName = before.firstOrNull { it.id == padId }?.displayName ?: "Pad"
    if (before.none { it.id == padId }) return
    recordEditSnapshot(before, "Pad \"$deletedName\" dihapus")
    audioEngine.stopPad(padId)
    val remaining = before
      .filterNot { it.id == padId }
      .mapIndexed { idx, pad -> pad.copy(positionIndex = idx) }
    audioEngine.updatePlaylist(remaining)
    _uiState.update {
      it.copy(
        pads = remaining,
        editingPadId = if (it.editingPadId == padId) null else it.editingPadId,
        audioEditorPadId = if (it.audioEditorPadId == padId) null else it.audioEditorPadId
      )
    }
    viewModelScope.launch {
      dao.deletePadById(padId)
      dao.updateAllPadPositions(remaining.map { it.id to it.positionIndex })
      persistentConfigManager.notifyConfigDirty("delete_pad")
    }
  }

  fun updatePadAppearance(padId: String, config: PadVisualConfig) {
    val currentPads = _uiState.value.pads
    val target = currentPads.firstOrNull { it.id == padId } ?: return
    val trimmedName = config.displayName.trim().ifEmpty {
      target.fileName.substringBeforeLast('.').ifEmpty { "Pad" }
    }
    if (target.displayName != trimmedName ||
      target.colorArgb != config.colorArgb ||
      target.glowColorArgb != config.glowColorArgb ||
      target.shapeStyle != config.shapeStyle ||
      target.surfaceFinish != config.surfaceFinish ||
      target.badgeIcon != config.badgeIcon
    ) {
      recordEditSnapshot(currentPads)
    }
    viewModelScope.launch {
      dao.updatePadAppearance(
        padId = padId,
        displayName = trimmedName,
        colorArgb = config.colorArgb,
        glowColorArgb = config.glowColorArgb,
        glowIntensity = config.glowIntensity,
        shapeStyle = config.shapeStyle?.name,
        borderColorArgb = config.borderColorArgb,
        borderWidthDp = config.borderWidthDp,
        cornerRadiusDp = config.cornerRadiusDp,
        textColorArgb = config.textColorArgb,
        surfaceFinish = config.surfaceFinish.name,
        badgeIcon = config.badgeIcon.name,
        showSlotNumber = config.showSlotNumber,
        showDurationLabel = config.showDurationLabel
      )
      _uiState.update { state ->
        val updatedPads = state.pads.map { pad ->
          if (pad.id == padId) {
            pad.copy(
              displayName = trimmedName,
              colorArgb = config.colorArgb,
              glowColorArgb = config.glowColorArgb,
              glowIntensity = config.glowIntensity,
              shapeStyle = config.shapeStyle,
              borderColorArgb = config.borderColorArgb,
              borderWidthDp = config.borderWidthDp,
              cornerRadiusDp = config.cornerRadiusDp,
              textColorArgb = config.textColorArgb,
              surfaceFinish = config.surfaceFinish,
              badgeIcon = config.badgeIcon,
              showSlotNumber = config.showSlotNumber,
              showDurationLabel = config.showDurationLabel
            )
          } else {
            pad
          }
        }
        audioEngine.updatePlaylist(updatedPads)
        state.copy(pads = updatedPads)
      }
      persistentConfigManager.notifyConfigDirty("pad_appearance")
    }
  }

  fun applyVisualStyleToAllPads(config: PadVisualConfig) {
    recordEditSnapshot(_uiState.value.pads)
    val profileId = _uiState.value.settings.activeProfileId
    viewModelScope.launch {
      dao.applyVisualStyleToAllInProfile(
        profileId = profileId,
        colorArgb = config.colorArgb,
        glowColorArgb = config.glowColorArgb,
        glowIntensity = config.glowIntensity,
        shapeStyle = config.shapeStyle?.name,
        borderColorArgb = config.borderColorArgb,
        borderWidthDp = config.borderWidthDp,
        cornerRadiusDp = config.cornerRadiusDp,
        textColorArgb = config.textColorArgb,
        surfaceFinish = config.surfaceFinish.name,
        showSlotNumber = config.showSlotNumber,
        showDurationLabel = config.showDurationLabel
      )
      _uiState.update { state ->
        val updatedPads = state.pads.map { pad ->
          pad.copy(
            colorArgb = config.colorArgb,
            glowColorArgb = config.glowColorArgb,
            glowIntensity = config.glowIntensity,
            shapeStyle = config.shapeStyle,
            borderColorArgb = config.borderColorArgb,
            borderWidthDp = config.borderWidthDp,
            cornerRadiusDp = config.cornerRadiusDp,
            textColorArgb = config.textColorArgb,
            surfaceFinish = config.surfaceFinish,
            showSlotNumber = config.showSlotNumber,
            showDurationLabel = config.showDurationLabel
          )
        }
        audioEngine.updatePlaylist(updatedPads)
        state.copy(
          pads = updatedPads,
          statusMessage = "Applied button style to all ${updatedPads.size} songs"
        )
      }
      persistentConfigManager.notifyConfigDirty("pad_style_all")
    }
  }

  fun updatePadSettings(padId: String, newSettings: PadSettings) {
    val existingPad = _uiState.value.pads.firstOrNull { it.id == padId }
    if (existingPad != null &&
      (existingPad.settings.triggerMode != newSettings.triggerMode ||
        existingPad.settings.isLooping != newSettings.isLooping ||
        existingPad.settings.isDuckingSource != newSettings.isDuckingSource ||
        existingPad.settings.chokeGroup != newSettings.chokeGroup)
    ) {
      recordEditSnapshot(_uiState.value.pads)
    }
    val shouldPrepareReverse = newSettings.isReversed &&
      existingPad != null &&
      ReverseAudioCacheManager.getCachedReversedFileIfReady(
        context = app,
        uriString = existingPad.uriString,
        trimStartMs = newSettings.trimStartMs,
        trimEndMs = newSettings.trimEndMs
      ) == null

    viewModelScope.launch {
      dao.upsertPadSetting(newSettings.toEntity(padId))
      persistentConfigManager.notifyConfigDirty("pad_settings")
      var updatedTarget: SoundPad? = null
      _uiState.update { state ->
        val updatedPads = state.pads.map { pad ->
          if (pad.id == padId) {
            val modified = pad.copy(settings = newSettings)
            updatedTarget = modified
            modified
          } else {
            pad
          }
        }
        audioEngine.updatePlaylist(updatedPads)
        state.copy(
          pads = updatedPads,
          isPreparingReversePadId = if (shouldPrepareReverse) padId else state.isPreparingReversePadId
        )
      }
      updatedTarget?.let { audioEngine.updateActivePadParameters(it) }

      if (shouldPrepareReverse && existingPad != null) {
        val result = ReverseAudioCacheManager.getOrBuildReversedWav(
          context = app,
          uriString = existingPad.uriString,
          trimStartMs = newSettings.trimStartMs,
          trimEndMs = newSettings.trimEndMs
        )
        _uiState.update { state ->
          if (state.isPreparingReversePadId == padId) state.copy(isPreparingReversePadId = null) else state
        }
        result.onFailure { err ->
          val msg = "Gagal membalik audio \"${existingPad.displayName}\": ${err.localizedMessage ?: err.javaClass.simpleName}"
          logErrorInternal(
            fileName = existingPad.fileName,
            uriString = existingPad.uriString,
            type = PadErrorType.PLAYBACK_ERROR,
            message = msg,
            suggestion = PadErrorType.PLAYBACK_ERROR.defaultSuggestion,
            stackTrace = err.stackTraceToString().take(2000),
            padId = padId
          )
          val reverted = newSettings.copy(isReversed = false)
          dao.upsertPadSetting(reverted.toEntity(padId))
          _uiState.update { state ->
            val updated = state.pads.map { p ->
              if (p.id == padId) p.copy(settings = reverted) else p
            }
            audioEngine.updatePlaylist(updated)
            state.copy(pads = updated, errorMessage = msg)
          }
        }
      }
    }
  }

  fun togglePadFavorite(padId: String) {
    val pads = _uiState.value.pads
    val target = pads.firstOrNull { it.id == padId } ?: return
    recordEditSnapshot(pads)
    val nextFav = !target.isFavorite
    viewModelScope.launch {
      dao.updatePadFavoriteAndTag(padId, nextFav, target.categoryTag)
      persistentConfigManager.notifyConfigDirty("pad_favorite")
      _uiState.update { state ->
        val updated = state.pads.map { p ->
          if (p.id == padId) p.copy(isFavorite = nextFav) else p
        }
        audioEngine.updatePlaylist(updated)
        state.copy(pads = updated)
      }
    }
  }

  fun setPadCategoryTag(padId: String, tag: String) {
    val pads = _uiState.value.pads
    val target = pads.firstOrNull { it.id == padId } ?: return
    val cleaned = tag.trim().take(24)
    if (target.categoryTag == cleaned) return
    recordEditSnapshot(pads)
    viewModelScope.launch {
      dao.updatePadFavoriteAndTag(padId, target.isFavorite, cleaned)
      persistentConfigManager.notifyConfigDirty("pad_tag")
      _uiState.update { state ->
        val updated = state.pads.map { p ->
          if (p.id == padId) p.copy(categoryTag = cleaned) else p
        }
        audioEngine.updatePlaylist(updated)
        state.copy(pads = updated)
      }
    }
  }

  // ---- FASE 5: Audio Waveform Editor (Non-Destructive Cut & WAV Export) & Voice Track Record ----

  fun openAudioEditor(pad: SoundPad) {
    _uiState.update {
      it.copy(
        editingPadId = null,
        audioEditorPadId = pad.id,
        waveformData = null,
        isLoadingWaveform = true
      )
    }
    viewModelScope.launch {
      val data = AudioWaveformAndCutEngine.extractWaveform(app, pad.uriString)
      _uiState.update {
        if (it.audioEditorPadId == pad.id) {
          it.copy(waveformData = data, isLoadingWaveform = false)
        } else {
          it
        }
      }
    }
  }

  fun closeAudioEditor() {
    audioEngine.stopEditorPreview()
    _uiState.update {
      it.copy(
        audioEditorPadId = null,
        waveformData = null,
        isLoadingWaveform = false
      )
    }
  }

  fun startEditorPreview(uriString: String, startMs: Long, endMs: Long) {
    audioEngine.startEditorPreview(uriString, startMs, endMs)
  }

  fun stopEditorPreview() {
    audioEngine.stopEditorPreview()
  }

  fun saveNonDestructiveTrim(padId: String, startMs: Long, endMs: Long) {
    val target = _uiState.value.pads.firstOrNull { it.id == padId } ?: return
    val updatedSettings = target.settings.copy(trimStartMs = startMs, trimEndMs = endMs)
    updatePadSettings(padId, updatedSettings)
    _uiState.update {
      it.copy(
        statusMessage = if (startMs == 0L && endMs == 0L) {
          "Cleared cut points on \"${target.displayName}\""
        } else {
          "Saved non-destructive cut on \"${target.displayName}\""
        }
      )
    }
  }

  fun exportCutAsNewPad(sourcePad: SoundPad, startMs: Long, endMs: Long) {
    viewModelScope.launch {
      _uiState.update { it.copy(isExportingCut = true) }
      val exportDir = File(app.filesDir, "cut_exports").apply { mkdirs() }
      val safeBase = sourcePad.displayName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "cut" }
      val outFile = File(exportDir, "${safeBase}_cut_${System.currentTimeMillis()}.wav")

      val result = AudioWaveformAndCutEngine.exportTrimmedToFile(
        context = app,
        sourceUriString = sourcePad.uriString,
        trimStartMs = startMs,
        trimEndMs = endMs,
        targetFile = outFile
      )

      result.onSuccess { durationMs ->
        val fileUri = Uri.fromFile(outFile).toString()
        val newPad = SoundPad(
          id = "pad_cut_${UUID.randomUUID()}",
          uriString = fileUri,
          fileName = outFile.name,
          displayName = "${sourcePad.displayName} (Cut)",
          extension = "WAV",
          durationMs = durationMs.coerceAtLeast(endMs - startMs),
          sizeBytes = outFile.length(),
          positionIndex = _uiState.value.pads.size,
          colorArgb = sourcePad.colorArgb,
          glowColorArgb = sourcePad.glowColorArgb,
          glowIntensity = sourcePad.glowIntensity,
          shapeStyle = sourcePad.shapeStyle,
          borderColorArgb = sourcePad.borderColorArgb,
          borderWidthDp = sourcePad.borderWidthDp,
          cornerRadiusDp = sourcePad.cornerRadiusDp,
          textColorArgb = sourcePad.textColorArgb,
          surfaceFinish = sourcePad.surfaceFinish,
          badgeIcon = sourcePad.badgeIcon,
          showSlotNumber = sourcePad.showSlotNumber,
          showDurationLabel = sourcePad.showDurationLabel,
          isClone = false,
          settings = sourcePad.settings.copy(trimStartMs = 0L, trimEndMs = 0L)
        )
        addCreatedPadToBoard(newPad, "Exported cut \"${newPad.displayName}\" as new pad")
      }.onFailure { err ->
        _uiState.update {
          it.copy(errorMessage = err.localizedMessage ?: "Failed to export cut audio")
        }
      }
      _uiState.update { it.copy(isExportingCut = false, audioEditorPadId = null) }
    }
  }

  fun exportCutToSafUri(sourcePad: SoundPad, startMs: Long, endMs: Long, targetUri: Uri) {
    viewModelScope.launch {
      _uiState.update { it.copy(isExportingCut = true) }
      AudioWaveformAndCutEngine.exportTrimmedToUri(
        context = app,
        sourceUriString = sourcePad.uriString,
        trimStartMs = startMs,
        trimEndMs = endMs,
        targetUri = targetUri
      ).onSuccess {
        _uiState.update { it.copy(statusMessage = "Exported WAV cut to selected folder") }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Cut export failed") }
      }
      _uiState.update { it.copy(isExportingCut = false) }
    }
  }

  fun openVoiceRecordSheet() {
    _uiState.update { it.copy(isVoiceRecordSheetOpen = true) }
  }

  fun closeVoiceRecordSheet() {
    voiceRecorder.cancelRecording()
    audioEngine.setVoiceDuckingActive(false)
    _uiState.update { it.copy(isVoiceRecordSheetOpen = false) }
  }

  fun startVoiceRecording() {
    voiceRecorder.startRecording()
      .onSuccess {
        audioEngine.setVoiceDuckingActive(true)
      }
      .onFailure { err ->
        audioEngine.setVoiceDuckingActive(false)
        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Microphone unavailable") }
      }
  }

  fun stopAndSaveVoiceRecording() {
    viewModelScope.launch {
      audioEngine.setVoiceDuckingActive(false)
      val recorded = voiceRecorder.stopRecording()
      _uiState.update { it.copy(isVoiceRecordSheetOpen = false) }
      if (recorded == null) {
        _uiState.update { it.copy(errorMessage = "Recording too short or empty") }
        return@launch
      }

      val voiceNum = _uiState.value.pads.count { it.displayName.startsWith("Voice ") } + 1
      val newPad = SoundPad(
        id = "pad_voice_${UUID.randomUUID()}",
        uriString = Uri.fromFile(recorded.file).toString(),
        fileName = recorded.file.name,
        displayName = "Voice $voiceNum",
        extension = "WAV",
        durationMs = recorded.durationMs,
        sizeBytes = recorded.file.length(),
        positionIndex = _uiState.value.pads.size,
        colorArgb = null,
        glowColorArgb = null,
        glowIntensity = 0f,
        isPreloadedInRam = false,
        categoryTag = "Vocal",
        settings = PadSettings(volume = 1.0f, triggerMode = PadTriggerMode.ONE_SHOT)
      )
      addCreatedPadToBoard(newPad, "Recorded \"${newPad.displayName}\" added to board")
    }
  }

  fun cancelVoiceRecording() {
    voiceRecorder.cancelRecording()
    audioEngine.setVoiceDuckingActive(false)
  }

  private suspend fun addCreatedPadToBoard(pad: SoundPad, successNotice: String) {
    val profileId = _uiState.value.settings.activeProfileId
    val existing = _uiState.value.pads
    val inserted = pad.copy(positionIndex = existing.size)
    val preloaded = if (_uiState.value.settings.preloadShortAudio) {
      ramCache.preloadShortSamples(listOf(inserted))
    } else {
      emptySet()
    }
    val readyPad = inserted.copy(isPreloadedInRam = inserted.id in preloaded)
    val updatedList = existing + readyPad

    val existingProfile = dao.getProfileById(profileId)
    if (existingProfile == null) {
      val now = System.currentTimeMillis()
      val gs = _uiState.value.settings
      val grid = gs.gridConfig
      dao.upsertProfile(
        PadProfileEntity(
          id = profileId,
          name = DEFAULT_PROFILE_NAME,
          folderUri = gs.folderUri,
          gridPreset = grid.preset.name,
          gridRows = grid.rows,
          gridCols = grid.cols,
          masterEqEnabled = gs.masterEqEnabled,
          masterEqPreset = gs.masterEqPreset.name,
          masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(gs.masterEqBandsDb),
          createdAtMs = now,
          updatedAtMs = now,
          isActive = true
        )
      )
    }
    dao.upsertPad(readyPad.toPadEntity(profileId))
    dao.upsertPadSetting(readyPad.settings.toEntity(readyPad.id))
    persistentConfigManager.notifyConfigDirty("add_created_pad")

    audioEngine.updatePlaylist(updatedList)
    _uiState.update {
      it.copy(
        pads = updatedList,
        preloadedCount = updatedList.count { p -> p.isPreloadedInRam },
        statusMessage = successNotice
      )
    }
  }

  fun swapPadPositions(fromPadId: String, toPadId: String) {
    if (_uiState.value.settings.isLocked || fromPadId == toPadId) return
    val currentPads = _uiState.value.pads.toMutableList()
    val fromIdx = currentPads.indexOfFirst { it.id == fromPadId }
    val toIdx = currentPads.indexOfFirst { it.id == toPadId }
    if (fromIdx < 0 || toIdx < 0) return

    recordEditSnapshot(_uiState.value.pads, "Posisi pad dipindahkan")

    val fromPad = currentPads[fromIdx]
    val toPad = currentPads[toIdx]
    currentPads[fromIdx] = toPad.copy(positionIndex = fromIdx)
    currentPads[toIdx] = fromPad.copy(positionIndex = toIdx)

    val reindexed = currentPads.mapIndexed { idx, pad -> pad.copy(positionIndex = idx) }
    audioEngine.updatePlaylist(reindexed)
    _uiState.update { it.copy(pads = reindexed) }

    viewModelScope.launch {
      dao.updateAllPadPositions(reindexed.map { it.id to it.positionIndex })
      persistentConfigManager.notifyConfigDirty("swap_pad_positions")
    }
  }

  // ---- FASE 4: Profile Management & JSON Export/Import ----

  fun saveCurrentAsNewProfile(name: String) {
    viewModelScope.launch {
      val now = System.currentTimeMillis()
      val newProfileId = "profile_${UUID.randomUUID()}"
      val state = _uiState.value
      val grid = state.settings.gridConfig
      val bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(state.activeBankNames)
      val bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(state.activeBankOrder)
      val bankFolderUrisJson = ProfileJsonSerializer.encodeBankFolderUrisString(state.activeBankFolderUris)

      val newProfile = PadProfileEntity(
        id = newProfileId,
        name = name,
        folderUri = state.settings.folderUri,
        gridPreset = grid.preset.name,
        gridRows = grid.rows,
        gridCols = grid.cols,
        bankNamesJson = bankNamesJson,
        bankOrderJson = bankOrderJson,
        bankFolderUrisJson = bankFolderUrisJson,
        masterEqEnabled = state.settings.masterEqEnabled,
        masterEqPreset = state.settings.masterEqPreset.name,
        masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(state.settings.masterEqBandsDb),
        createdAtMs = now,
        updatedAtMs = now,
        isActive = true
      )

      val idMap = state.pads.associate { it.id to "pad_${UUID.randomUUID()}" }
      val padEntities = state.pads.map { pad ->
        pad.copy(id = idMap.getValue(pad.id)).toPadEntity(newProfileId)
      }
      val settingEntities = state.pads.map { pad ->
        pad.settings.toEntity(idMap.getValue(pad.id))
      }

      dao.saveCompleteProfile(newProfile, padEntities, settingEntities)
      val copiedCc = state.midiCcMappings.map { mapping ->
        MidiCcMappingEntity(
          id = "cc_${newProfileId}_${mapping.target.name}",
          profileId = newProfileId,
          target = mapping.target.name,
          ccNumber = mapping.ccNumber,
          channel = mapping.channel
        )
      }
      if (copiedCc.isNotEmpty()) {
        dao.upsertMidiCcMappings(copiedCc)
      }
      lastLoadedProfileId = newProfileId
      settingsRepo.setActiveProfileId(newProfileId)
      _uiState.update { it.copy(statusMessage = "Saved profile \"$name\"") }
    }
  }

  fun loadProfile(profileId: String) {
    viewModelScope.launch {
      val profile = dao.getProfileById(profileId) ?: return@launch
      audioEngine.stopAll()
      dao.setActiveProfile(profileId)

      val preset = runCatching { GridPreset.valueOf(profile.gridPreset) }.getOrDefault(GridPreset.GRID_4X4)
      settingsRepo.setGridConfig(GridConfig(preset, profile.gridRows, profile.gridCols))

      val eqPreset = runCatching { EqPreset.valueOf(profile.masterEqPreset) }.getOrDefault(EqPreset.FLAT)
      val eqBands = ProfileJsonSerializer.decodeEqBandsString(profile.masterEqBandsJson)
      settingsRepo.setMasterEq(profile.masterEqEnabled, eqPreset, eqBands)
      audioEngine.setMasterEq(profile.masterEqEnabled, eqBands)

      val folderUriStr = profile.folderUri
      if (folderUriStr != null) {
        val folderName = if (folderUriStr == DemoTrackManager.DEMO_FOLDER_URI) {
          DemoTrackManager.DEMO_FOLDER_NAME
        } else {
          scanner.resolveFolderDisplayName(Uri.parse(folderUriStr))
        }
        settingsRepo.setSelectedFolder(folderUriStr, folderName)
        lastObservedFolderUri = folderUriStr
      }

      lastLoadedProfileId = profileId
      settingsRepo.setActiveProfileId(profileId)
      loadProfilePads(profileId, _uiState.value.settings.preloadShortAudio)
      _uiState.update { it.copy(statusMessage = "Loaded profile \"${profile.name}\"") }
    }
  }

  fun duplicateProfile(profileId: String) {
    viewModelScope.launch {
      val source = dao.getProfileById(profileId) ?: return@launch
      val sourcePads = dao.getPadsForProfileOnce(profileId)
      val sourceSettings = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

      val now = System.currentTimeMillis()
      val newProfileId = "profile_${UUID.randomUUID()}"
      val copyProfile = source.copy(
        id = newProfileId,
        name = "${source.name} (Copy)",
        createdAtMs = now,
        updatedAtMs = now,
        isActive = true
      )

      val newPads = mutableListOf<SoundPadEntity>()
      val newSettings = mutableListOf<PadSettingEntity>()
      for (pad in sourcePads) {
        val newPadId = "pad_${UUID.randomUUID()}"
        newPads.add(pad.copy(id = newPadId, profileId = newProfileId))
        val setting = sourceSettings[pad.id] ?: PadSettingEntity(padId = newPadId)
        newSettings.add(setting.copy(padId = newPadId))
      }

      dao.saveCompleteProfile(copyProfile, newPads, newSettings)
      val sourceCc = dao.getMidiCcMappingsOnce(profileId)
      if (sourceCc.isNotEmpty()) {
        dao.upsertMidiCcMappings(
          sourceCc.map { cc ->
            cc.copy(id = "cc_${newProfileId}_${cc.target}", profileId = newProfileId)
          }
        )
      }
      lastLoadedProfileId = newProfileId
      settingsRepo.setActiveProfileId(newProfileId)
      loadProfilePads(newProfileId, _uiState.value.settings.preloadShortAudio)
      _uiState.update { it.copy(statusMessage = "Duplicated \"${source.name}\"") }
    }
  }

  fun deleteProfile(profileId: String) {
    viewModelScope.launch {
      val all = dao.getAllProfilesOnce()
      if (all.size <= 1) return@launch
      val deletingActive = profileId == _uiState.value.settings.activeProfileId
      dao.deleteProfileById(profileId)
      if (deletingActive) {
        val fallback = dao.getAllProfilesOnce().firstOrNull()
        if (fallback != null) {
          loadProfile(fallback.id)
        }
      }
      _uiState.update { it.copy(statusMessage = "Profile deleted") }
    }
  }

  private suspend fun buildExportBundleForProfile(profileId: String): ExportedProfileBundle {
    val profileEntity = dao.getProfileById(profileId)
    val padEntities = dao.getPadsForProfileOnce(profileId)
    val settingEntities = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

    val preset = profileEntity?.let {
      runCatching { GridPreset.valueOf(it.gridPreset) }.getOrDefault(GridPreset.GRID_4X4)
    } ?: _uiState.value.settings.gridConfig.preset

    val masterEqPreset = profileEntity?.let {
      runCatching { EqPreset.valueOf(it.masterEqPreset) }.getOrDefault(EqPreset.FLAT)
    } ?: _uiState.value.settings.masterEqPreset
    val masterEqBandsDb = profileEntity?.let {
      ProfileJsonSerializer.decodeEqBandsString(it.masterEqBandsJson)
    } ?: _uiState.value.settings.masterEqBandsDb

    val gs = _uiState.value.settings
    val profile = PadProfile(
      id = profileId,
      name = profileEntity?.name ?: DEFAULT_PROFILE_NAME,
      folderUri = profileEntity?.folderUri ?: _uiState.value.settings.folderUri,
      gridConfig = GridConfig(
        preset = preset,
        rows = profileEntity?.gridRows ?: _uiState.value.settings.gridConfig.rows,
        cols = profileEntity?.gridCols ?: _uiState.value.settings.gridConfig.cols
      ),
      padCount = padEntities.size,
      bankNames = ProfileJsonSerializer.decodeBankNamesString(profileEntity?.bankNamesJson),
      bankOrder = ProfileJsonSerializer.decodeBankOrderString(profileEntity?.bankOrderJson),
      bankFolderUris = ProfileJsonSerializer.decodeBankFolderUrisString(profileEntity?.bankFolderUrisJson),
      masterEqEnabled = profileEntity?.masterEqEnabled ?: _uiState.value.settings.masterEqEnabled,
      masterEqPreset = masterEqPreset,
      masterEqBandsDb = masterEqBandsDb,
      themeMode = gs.themeMode,
      useDynamicColor = gs.useDynamicColor,
      appAccentColorArgb = gs.appAccentColorArgb,
      padGridSpacingDp = gs.padGridSpacingDp,
      defaultPadShape = gs.defaultPadShape,
      defaultCornerGlowArgb = gs.defaultCornerGlowArgb,
      defaultPadColorMode = gs.defaultPadColorMode,
      defaultPadColorArgb = gs.defaultPadColorArgb,
      createdAtMs = profileEntity?.createdAtMs ?: System.currentTimeMillis(),
      updatedAtMs = profileEntity?.updatedAtMs ?: System.currentTimeMillis(),
      isActive = profileEntity?.isActive ?: true
    )

    val domainPads = padEntities.map { entity ->
      entityToDomain(entity, settingEntities[entity.id].toDomain())
    }
    val ccMappings = dao.getMidiCcMappingsOnce(profileId).toDomainMappings()
    return ExportedProfileBundle(profile = profile, pads = domainPads, ccMappings = ccMappings)
  }

  suspend fun buildExportJsonForProfile(profileId: String): String = withContext(Dispatchers.IO) {
    val bundle = buildExportBundleForProfile(profileId)
    ProfileJsonSerializer.exportToJson(bundle.profile, bundle.pads, bundle.ccMappings)
  }

  fun exportProfileToUri(profileId: String, targetUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val json = buildExportJsonForProfile(profileId)
        withContext(Dispatchers.IO) {
          app.contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
          } ?: error("Cannot open destination file")
        }
      }.onSuccess {
        _uiState.update { it.copy(statusMessage = "Profile exported to JSON") }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Export failed") }
      }
    }
  }

  fun importProfileFromUri(sourceUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val jsonText = withContext(Dispatchers.IO) {
          app.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read selected JSON file")
        }
        val bundle = ProfileJsonSerializer.importFromJson(jsonText)
        val profile = bundle.profile
        val profileEntity = PadProfileEntity(
          id = profile.id,
          name = profile.name,
          folderUri = profile.folderUri,
          gridPreset = profile.gridConfig.preset.name,
          gridRows = profile.gridConfig.rows,
          gridCols = profile.gridConfig.cols,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(profile.bankNames),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(profile.bankOrder),
          bankFolderUrisJson = ProfileJsonSerializer.encodeBankFolderUrisString(profile.bankFolderUris),
          masterEqEnabled = profile.masterEqEnabled,
          masterEqPreset = profile.masterEqPreset.name,
          masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(profile.masterEqBandsDb),
          createdAtMs = profile.createdAtMs,
          updatedAtMs = profile.updatedAtMs,
          isActive = true
        )
        val padEntities = bundle.pads.map { pad -> pad.toPadEntity(profile.id) }
        val settingEntities = bundle.pads.map { pad -> pad.settings.toEntity(pad.id) }
        dao.saveCompleteProfile(profileEntity, padEntities, settingEntities)
        if (bundle.ccMappings.isNotEmpty()) {
          dao.upsertMidiCcMappings(
            bundle.ccMappings.map { mapping ->
              MidiCcMappingEntity(
                id = "cc_${profile.id}_${mapping.target.name}",
                profileId = profile.id,
                target = mapping.target.name,
                ccNumber = mapping.ccNumber,
                channel = mapping.channel
              )
            }
          )
        }

        settingsRepo.setGridConfig(profile.gridConfig)
        settingsRepo.setMasterEq(profile.masterEqEnabled, profile.masterEqPreset, profile.masterEqBandsDb)
        audioEngine.setMasterEq(profile.masterEqEnabled, profile.masterEqBandsDb)
        profile.themeMode?.let { settingsRepo.setThemeMode(it) }
        profile.useDynamicColor?.let { settingsRepo.setUseDynamicColor(it) }
        profile.appAccentColorArgb?.let { settingsRepo.setAppAccentColor(it) }
        profile.padGridSpacingDp?.let { settingsRepo.setPadGridSpacingDp(it) }
        profile.defaultPadShape?.let { settingsRepo.setDefaultPadShape(it) }
        profile.defaultCornerGlowArgb?.let { settingsRepo.setDefaultCornerGlow(it) }
        profile.defaultPadColorMode?.let { settingsRepo.setDefaultPadColorMode(it) }
        profile.defaultPadColorArgb?.let { settingsRepo.setDefaultPadColorArgb(it) }
        if (profile.folderUri != null) {
          val folderName = if (profile.folderUri == DemoTrackManager.DEMO_FOLDER_URI) {
            DemoTrackManager.DEMO_FOLDER_NAME
          } else {
            scanner.resolveFolderDisplayName(Uri.parse(profile.folderUri))
          }
          settingsRepo.setSelectedFolder(profile.folderUri, folderName)
          lastObservedFolderUri = profile.folderUri
        }
        lastLoadedProfileId = profile.id
        settingsRepo.setActiveProfileId(profile.id)
        loadProfilePads(profile.id, _uiState.value.settings.preloadShortAudio)
        profile.name
      }.onSuccess { importedName ->
        _uiState.update { it.copy(statusMessage = "Imported profile \"$importedName\"") }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = "Invalid profile JSON: ${err.localizedMessage}") }
      }
    }
  }

  fun exportFullBackupToUri(targetUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val json = withContext(Dispatchers.IO) {
          val allProfiles = dao.getAllProfilesOnce()
          val bundles = if (allProfiles.isEmpty()) {
            listOf(buildExportBundleForProfile(_uiState.value.settings.activeProfileId))
          } else {
            allProfiles.map { buildExportBundleForProfile(it.id) }
          }
          FullBackupSerializer.exportBackupJson(_uiState.value.settings, bundles)
        }
        withContext(Dispatchers.IO) {
          app.contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
          } ?: error("Cannot open backup destination")
        }
      }.onSuccess {
        _uiState.update { it.copy(statusMessage = "Full backup exported (${_uiState.value.profiles.size.coerceAtLeast(1)} profile(s))") }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Backup export failed") }
      }
    }
  }

  fun importFullBackupFromUri(sourceUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val jsonText = withContext(Dispatchers.IO) {
          app.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: error("Cannot read selected backup file")
        }
        val payload = FullBackupSerializer.importBackupJson(jsonText)
        audioEngine.stopAll()
        audioEngine.stopCuePreview()

        payload.profiles.forEach { bundle ->
          val profile = bundle.profile
          val isTargetActive = profile.id == payload.globalSettings.activeProfileId
          val entity = PadProfileEntity(
            id = profile.id,
            name = profile.name,
            folderUri = profile.folderUri,
            gridPreset = profile.gridConfig.preset.name,
            gridRows = profile.gridConfig.rows,
            gridCols = profile.gridConfig.cols,
            bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(profile.bankNames),
            bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(profile.bankOrder),
            bankFolderUrisJson = ProfileJsonSerializer.encodeBankFolderUrisString(profile.bankFolderUris),
            masterEqEnabled = profile.masterEqEnabled,
            masterEqPreset = profile.masterEqPreset.name,
            masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(profile.masterEqBandsDb),
            createdAtMs = profile.createdAtMs,
            updatedAtMs = profile.updatedAtMs,
            isActive = isTargetActive
          )
          val idPrefix = profile.id
          val padEntities = bundle.pads.mapIndexed { idx, pad ->
            val uniquePadId = "pad_${idPrefix}_${idx}_${UUID.randomUUID().toString().take(8)}"
            pad.copy(id = uniquePadId) to pad.settings
          }
          dao.saveCompleteProfile(
            profile = entity,
            pads = padEntities.map { (p, _) -> p.toPadEntity(profile.id) },
            settings = padEntities.map { (p, s) -> s.toEntity(p.id) }
          )
          dao.deleteMidiCcMappingsForProfile(profile.id)
          if (bundle.ccMappings.isNotEmpty()) {
            dao.upsertMidiCcMappings(
              bundle.ccMappings.map { mapping ->
                MidiCcMappingEntity(
                  id = "cc_${profile.id}_${mapping.target.name}",
                  profileId = profile.id,
                  target = mapping.target.name,
                  ccNumber = mapping.ccNumber,
                  channel = mapping.channel
                )
              }
            )
          }
        }

        val gs = payload.globalSettings
        val restoredActiveId = payload.profiles.firstOrNull { it.profile.id == gs.activeProfileId }?.profile?.id
          ?: payload.profiles.first().profile.id
        dao.setActiveProfile(restoredActiveId)

        settingsRepo.setGridConfig(gs.gridConfig)
        settingsRepo.setPlayMode(gs.playMode)
        settingsRepo.setMasterVolume(gs.masterVolume)
        settingsRepo.setMasterPan(gs.masterPan)
        settingsRepo.setMasterEq(gs.masterEqEnabled, gs.masterEqPreset, gs.masterEqBandsDb)
        audioEngine.setMasterEq(gs.masterEqEnabled, gs.masterEqBandsDb)
        settingsRepo.setMiniWaveformEnabled(gs.miniWaveformEnabled)
        settingsRepo.setPreloadShortAudio(gs.preloadShortAudio)
        settingsRepo.setAudioEffectsEnabled(gs.audioEffectsEnabled)
        settingsRepo.setVisualEffectsEnabled(gs.visualEffectsEnabled)
        settingsRepo.setKeepScreenOn(gs.keepScreenOn)
        settingsRepo.setThemeMode(gs.themeMode)
        settingsRepo.setUseDynamicColor(gs.useDynamicColor)
        settingsRepo.setAppAccentColor(gs.appAccentColorArgb)
        settingsRepo.setPadGridSpacingDp(gs.padGridSpacingDp)
        settingsRepo.setDefaultPadShape(gs.defaultPadShape)
        settingsRepo.setDefaultCornerGlow(gs.defaultCornerGlowArgb)
        settingsRepo.setBottomBarVisible(gs.isBottomBarVisible)
        settingsRepo.setFullscreen(gs.isFullscreen)
        settingsRepo.setGlobalFadeInMs(gs.globalFadeInMs)
        settingsRepo.setGlobalFadeOutMs(gs.globalFadeOutMs)
        settingsRepo.setGlobalFadeCurve(gs.globalFadeCurve)
        settingsRepo.setGlobalCrossfadeEnabled(gs.globalCrossfadeEnabled)
        settingsRepo.setCrossfadeDurationMs(gs.crossfadeDurationMs)
        settingsRepo.setCrossfadeCurve(gs.crossfadeCurve)
        settingsRepo.setMasterLimiterEnabled(gs.masterLimiterEnabled)
        settingsRepo.setDuckingEnabled(gs.duckingEnabled)
        settingsRepo.setDuckingAttenuation(gs.duckingAttenuation)
        settingsRepo.setAutoNormalizeEnabled(gs.autoNormalizeEnabled)
        settingsRepo.setLocked(gs.isLocked)
        settingsRepo.setGlobalCueEnabled(gs.isGlobalCueEnabled)
        settingsRepo.setHapticIntensity(gs.hapticIntensity)
        settingsRepo.setSwipePadVolumeEnabled(gs.swipePadVolumeEnabled)
        settingsRepo.setDoubleTapToStopEnabled(gs.doubleTapToStopEnabled)
        settingsRepo.setHasCompletedOnboarding(gs.hasCompletedOnboarding)
        settingsRepo.setShowFeatureTooltips(gs.showFeatureTooltips)
        settingsRepo.setAppLanguage(gs.appLanguage)
        settingsRepo.setDefaultPadColorMode(gs.defaultPadColorMode)
        settingsRepo.setDefaultPadColorArgb(gs.defaultPadColorArgb)
        settingsRepo.setFocusMode(gs.isFocusMode)
        settingsRepo.setStartInFocusMode(gs.startInFocusMode)
        settingsRepo.setFocusCornerPanicEnabled(gs.focusCornerPanicEnabled)
        settingsRepo.setFocusModeMidiNote(gs.focusModeMidiNote)

        if (gs.folderUri != null) {
          settingsRepo.setSelectedFolder(gs.folderUri, gs.folderName ?: "Restored Folder")
          lastObservedFolderUri = gs.folderUri
        }
        lastLoadedProfileId = restoredActiveId
        settingsRepo.setActiveProfileId(restoredActiveId)
        undoStack.clear()
        redoStack.clear()
        loadProfilePads(restoredActiveId, gs.preloadShortAudio)
        payload.profiles.size
      }.onSuccess { count ->
        _uiState.update {
          it.copy(
            canUndoEdit = false,
            canRedoEdit = false,
            statusMessage = "Restored full backup ($count profile(s))"
          )
        }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = "Restore failed: ${err.localizedMessage}") }
      }
    }
  }

  // ---- FASE 6: MIDI Controller & MIDI Learn ----

  fun toggleMidiLearnMode() {
    _uiState.update { state ->
      val next = !state.isMidiLearnMode
      state.copy(
        isMidiLearnMode = next,
        isEditMode = if (next) false else state.isEditMode,
        midiLearnTarget = if (next) state.midiLearnTarget else null
      )
    }
  }

  fun armMidiLearnForPad(pad: SoundPad) {
    _uiState.update {
      it.copy(
        isMidiLearnMode = true,
        isEditMode = false,
        midiLearnTarget = MidiLearnTarget.PadNote(pad.id, pad.displayName),
        statusMessage = "Press a MIDI key/pad for \"${pad.displayName}\""
      )
    }
  }

  fun armMidiLearnForCc(target: MidiCcTarget) {
    _uiState.update {
      it.copy(
        midiLearnTarget = MidiLearnTarget.CcControl(target),
        statusMessage = "Move a MIDI knob/fader for ${target.label}"
      )
    }
  }

  fun armMidiLearnForFocusMode() {
    _uiState.update {
      it.copy(
        isMidiLearnMode = true,
        isEditMode = false,
        midiLearnTarget = MidiLearnTarget.FocusModeNote,
        statusMessage = "Tekan tombol MIDI untuk Toggle Fokus Mode"
      )
    }
  }

  fun clearFocusModeMidiNote() {
    viewModelScope.launch {
      settingsRepo.setFocusModeMidiNote(null)
      _uiState.update { it.copy(statusMessage = "Mapping MIDI Note Fokus Mode dihapus") }
    }
  }

  fun cancelMidiLearn() {
    _uiState.update { it.copy(isMidiLearnMode = false, midiLearnTarget = null) }
  }

  fun clearPadMidiNote(padId: String) {
    viewModelScope.launch {
      dao.updatePadMidiNote(padId, null)
      persistentConfigManager.notifyConfigDirty("clear_pad_midi")
      _uiState.update { state ->
        val updated = state.pads.map { pad ->
          if (pad.id == padId) pad.copy(midiNote = null) else pad
        }
        audioEngine.updatePlaylist(updated)
        state.copy(pads = updated, statusMessage = "Cleared MIDI note mapping")
      }
    }
  }

  fun clearMidiCcMapping(target: MidiCcTarget) {
    val profileId = _uiState.value.settings.activeProfileId
    viewModelScope.launch {
      dao.deleteMidiCcMappingByTarget(profileId, target.name)
      persistentConfigManager.notifyConfigDirty("clear_cc_midi")
      _uiState.update { state ->
        state.copy(
          midiCcMappings = state.midiCcMappings.filterNot { it.target == target },
          statusMessage = "Cleared CC mapping for ${target.label}"
        )
      }
    }
  }

  fun refreshMidiDevices() {
    midiController.refreshDevices()
  }

  fun startBleMidiScan() {
    midiController.startBleScan()
  }

  fun stopBleMidiScan() {
    midiController.stopBleScan()
  }

  fun connectBleMidiDevice(address: String) {
    midiController.connectBleDevice(address)
  }

  private fun handleIncomingMidiEvent(event: MidiEvent) {
    val state = _uiState.value
    val learnTarget = state.midiLearnTarget

    if (learnTarget != null) {
      when {
        learnTarget is MidiLearnTarget.PadNote && event is MidiEvent.NoteOn -> {
          val note = event.note
          viewModelScope.launch {
            dao.updatePadMidiNote(learnTarget.padId, note)
            persistentConfigManager.notifyConfigDirty("midi_learn_pad")
            _uiState.update { cur ->
              val updated = cur.pads.map { pad ->
                if (pad.id == learnTarget.padId) pad.copy(midiNote = note) else pad
              }
              audioEngine.updatePlaylist(updated)
              cur.copy(
                pads = updated,
                midiLearnTarget = null,
                statusMessage = "Mapped ${MidiMessageParser.formatNoteName(note)} → \"${learnTarget.padName}\""
              )
            }
          }
        }
        learnTarget is MidiLearnTarget.CcControl && event is MidiEvent.ControlChange -> {
          val profileId = state.settings.activeProfileId
          val mapping = MidiCcMapping(
            id = "cc_${profileId}_${learnTarget.target.name}",
            profileId = profileId,
            target = learnTarget.target,
            ccNumber = event.controller,
            channel = event.channel
          )
          viewModelScope.launch {
            dao.upsertMidiCcMapping(
              MidiCcMappingEntity(
                id = mapping.id,
                profileId = mapping.profileId,
                target = mapping.target.name,
                ccNumber = mapping.ccNumber,
                channel = mapping.channel
              )
            )
            persistentConfigManager.notifyConfigDirty("midi_learn_cc")
            _uiState.update { cur ->
              val filtered = cur.midiCcMappings.filterNot { it.target == learnTarget.target }
              cur.copy(
                midiCcMappings = filtered + mapping,
                midiLearnTarget = null,
                statusMessage = "Mapped CC #${event.controller} (Ch ${event.channel + 1}) → ${learnTarget.target.label}"
              )
            }
          }
        }
        learnTarget is MidiLearnTarget.ProgramChangeBank && event is MidiEvent.ProgramChange -> {
          val profileId = state.settings.activeProfileId
          val mapping = MidiProgramBankMapping(
            id = "pc_${profileId}_b${learnTarget.bankIndex}",
            profileId = profileId,
            programNumber = event.program,
            bankIndex = learnTarget.bankIndex,
            channel = event.channel
          )
          viewModelScope.launch {
            dao.upsertMidiPcMapping(
              MidiPcBankMappingEntity(
                id = mapping.id,
                profileId = mapping.profileId,
                programNumber = mapping.programNumber,
                bankIndex = mapping.bankIndex,
                channel = mapping.channel
              )
            )
            persistentConfigManager.notifyConfigDirty("midi_learn_pc")
            _uiState.update { cur ->
              val filtered = cur.midiPcMappings.filterNot { it.bankIndex == learnTarget.bankIndex }
              cur.copy(
                midiPcMappings = filtered + mapping,
                midiLearnTarget = null,
                statusMessage = "Mapped Program Change #${event.program} → ${learnTarget.bankLabel}"
              )
            }
          }
        }
        learnTarget is MidiLearnTarget.SequencerPlaylistNote && event is MidiEvent.NoteOn -> {
          val note = event.note
          viewModelScope.launch {
            val existing = state.sequencerPlaylists.firstOrNull { it.id == learnTarget.playlistId }
              ?: return@launch
            val updated = existing.copy(triggerMidiNote = note, updatedAtMs = System.currentTimeMillis())
            dao.upsertSequencerPlaylist(updated.toEntity())
            _uiState.update { cur ->
              cur.copy(
                midiLearnTarget = null,
                statusMessage = "Mapped ${MidiMessageParser.formatNoteName(note)} → Playlist \"${learnTarget.playlistName}\""
              )
            }
          }
        }
        learnTarget is MidiLearnTarget.FocusModeNote && event is MidiEvent.NoteOn -> {
          val note = event.note
          viewModelScope.launch {
            settingsRepo.setFocusModeMidiNote(note)
            _uiState.update { cur ->
              cur.copy(
                midiLearnTarget = null,
                statusMessage = "Mapped ${MidiMessageParser.formatNoteName(note)} → Toggle Fokus Mode"
              )
            }
          }
        }
      }
      return
    }

    when (event) {
      is MidiEvent.NoteOn -> {
        if (state.settings.focusModeMidiNote == event.note) {
          toggleFocusMode()
        }
        val matchingPads = state.pads.filter { it.midiNote == event.note }
        matchingPads.forEach { pad ->
          if (pad.settings.triggerMode == PadTriggerMode.HOLD) {
            audioEngine.triggerHoldStart(pad)
          } else {
            audioEngine.triggerPad(pad)
          }
        }
        val matchingSeqs = state.sequencerPlaylists.filter { it.triggerMidiNote == event.note }
        if (matchingSeqs.isNotEmpty()) {
          sequencerEngine.updateAvailablePads(state.pads)
          matchingSeqs.forEach { pl -> sequencerEngine.togglePlayPause(pl) }
        }
      }
      is MidiEvent.NoteOff -> {
        state.pads
          .filter { it.midiNote == event.note && it.settings.triggerMode == PadTriggerMode.HOLD }
          .forEach { pad -> audioEngine.triggerHoldRelease(pad.id) }
      }
      is MidiEvent.ControlChange -> {
        val matches = state.midiCcMappings.filter {
          it.ccNumber == event.controller && (it.channel < 0 || it.channel == event.channel)
        }
        if (matches.isEmpty()) return
        val normalized = (event.value.coerceIn(0, 127) / 127f)
        matches.forEach { mapping ->
          when (mapping.target) {
            MidiCcTarget.MASTER_VOLUME -> setMasterVolume(normalized)
            MidiCcTarget.PAD_SPEED -> audioEngine.setActivePadsSpeed(0.5f + normalized * 1.5f)
            MidiCcTarget.PAD_PITCH -> audioEngine.setActivePadsPitch(0.5f + normalized * 1.5f)
            MidiCcTarget.CROSSFADE_DURATION -> {
              val ms = (normalized * 6000f).toLong().coerceIn(0L, 6000L)
              setCrossfadeDurationMs(ms)
            }
            MidiCcTarget.STOP_ALL -> if (event.value >= 64) stopAll()
            MidiCcTarget.TOGGLE_FOCUS_MODE -> if (event.value >= 64) toggleFocusMode()
          }
        }
      }
      is MidiEvent.ProgramChange -> {
        val customMatch = state.midiPcMappings.firstOrNull {
          it.programNumber == event.program && (it.channel < 0 || it.channel == event.channel)
        }
        val pages = state.bankPages
        if (pages.isNotEmpty()) {
          val targetPageIdx = if (customMatch != null) {
            pages.indexOfFirst { it.bankKey == customMatch.bankIndex }
              .takeIf { it >= 0 }
              ?: customMatch.bankIndex.coerceIn(0, pages.lastIndex)
          } else {
            event.program.coerceIn(0, pages.lastIndex)
          }
          _uiState.update {
            it.copy(
              requestedBankPageIndex = targetPageIdx,
              statusMessage = "MIDI Program #${event.program} → ${pages[targetPageIdx].title}"
            )
          }
        }
      }
    }
  }

  fun consumeBankSwitchRequest() {
    if (_uiState.value.requestedBankPageIndex != null) {
      _uiState.update { it.copy(requestedBankPageIndex = null) }
    }
  }

  fun armMidiLearnForBankPc(bankIndex: Int, bankLabel: String) {
    _uiState.update {
      it.copy(
        isMidiLearnMode = true,
        isEditMode = false,
        midiLearnTarget = MidiLearnTarget.ProgramChangeBank(bankIndex, bankLabel),
        statusMessage = "Kirim MIDI Program Change untuk \"$bankLabel\"..."
      )
    }
  }

  fun clearMidiPcMappingForBank(bankIndex: Int) {
    val profileId = _uiState.value.settings.activeProfileId
    viewModelScope.launch {
      dao.deleteMidiPcMappingByBank(profileId, bankIndex)
      persistentConfigManager.notifyConfigDirty("clear_pc_midi")
      _uiState.update { state ->
        state.copy(
          midiPcMappings = state.midiPcMappings.filterNot { it.bankIndex == bankIndex },
          statusMessage = "Hapus mapping Program Change Bank ${bankIndex + 1}"
        )
      }
    }
  }

  // ---- Master Output Recording (Internal Mix WAV) ----

  fun openMasterRecordSheet() {
    _uiState.update { it.copy(isMasterRecordSheetOpen = true) }
  }

  fun closeMasterRecordSheet() {
    _uiState.update { it.copy(isMasterRecordSheetOpen = false) }
  }

  fun setMasterRecordSampleRate(rate: MasterRecordSampleRate) {
    _uiState.update {
      it.copy(masterRecordState = it.masterRecordState.copy(sampleRateHz = rate.hz))
    }
  }

  fun setMasterRecordOutputFolder(folderUri: Uri) {
    val name = scanner.resolveFolderDisplayName(folderUri)
    _uiState.update {
      it.copy(
        masterRecordState = it.masterRecordState.copy(
          outputFolderUri = folderUri.toString(),
          outputFolderName = name
        ),
        statusMessage = "Folder simpan Master REC: $name"
      )
    }
  }

  fun setMasterRecordAutoAddAsPad(autoAdd: Boolean) {
    _uiState.update {
      it.copy(masterRecordState = it.masterRecordState.copy(autoAddAsPad = autoAdd))
    }
  }

  fun startMasterRecording() {
    val recState = _uiState.value.masterRecordState
    val result = masterRecorder.startRecording(
      sampleRateHz = recState.sampleRateHz,
      safFolderUri = recState.outputFolderUri
    )
    result.onFailure { err ->
      _uiState.update { it.copy(errorMessage = "Gagal mulai rekam master: ${err.localizedMessage}") }
    }
  }

  fun stopAndSaveMasterRecording() {
    viewModelScope.launch {
      val recState = _uiState.value.masterRecordState
      val saved = masterRecorder.stopRecordingAndSave()
      if (saved == null) {
        _uiState.update { it.copy(statusMessage = "Rekaman Master kosong / terlalu singkat.") }
        return@launch
      }
      _uiState.update {
        it.copy(
          masterRecordState = it.masterRecordState.copy(
            lastSavedUriString = saved.uriString,
            lastSavedName = saved.displayName
          )
        )
      }
      if (recState.autoAddAsPad) {
        val currentPads = _uiState.value.pads
        val activeBankKey = _uiState.value.bankPages.firstOrNull()?.bankKey ?: 0
        val newPad = SoundPad(
          id = "masterrec_${UUID.randomUUID()}",
          uriString = saved.uriString,
          fileName = saved.fileName,
          displayName = saved.displayName,
          extension = "wav",
          durationMs = saved.durationMs,
          sizeBytes = saved.sizeBytes,
          positionIndex = currentPads.size,
          bankIndex = activeBankKey
        )
        addCreatedPadToBoard(newPad, "Rekaman Master \"${saved.displayName}\" tersimpan & ditambahkan sebagai Pad")
      } else {
        _uiState.update { it.copy(statusMessage = "Rekaman Master tersimpan: ${saved.fileName}") }
      }
    }
  }

  fun cancelMasterRecording() {
    masterRecorder.cancelRecording()
    _uiState.update { it.copy(isMasterRecordSheetOpen = false) }
  }

  // ---- Scheduler Pad ----

  fun openSchedulerSheet() {
    _uiState.update { it.copy(isSchedulerSheetOpen = true) }
  }

  fun closeSchedulerSheet() {
    _uiState.update { it.copy(isSchedulerSheetOpen = false) }
  }

  fun saveScheduledTask(task: ScheduledPadTask) {
    viewModelScope.launch {
      val nextMs = if (task.enabled) PadSchedulerManager.scheduleTask(app, task) else 0L
      val finalized = task.copy(nextTriggerAtMs = nextMs)
      dao.upsertScheduledTask(finalized.toEntity())
      if (!task.enabled) {
        PadSchedulerManager.cancelTask(app, task.id)
      }
      _uiState.update {
        it.copy(statusMessage = "Jadwal \"${finalized.title}\" disimpan")
      }
    }
  }

  fun toggleScheduledTaskEnabled(task: ScheduledPadTask, enabled: Boolean) {
    saveScheduledTask(task.copy(enabled = enabled))
  }

  fun deleteScheduledTask(taskId: String) {
    viewModelScope.launch {
      PadSchedulerManager.cancelTask(app, taskId)
      dao.deleteScheduledTaskById(taskId)
      _uiState.update { it.copy(statusMessage = "Jadwal dihapus") }
    }
  }

  fun testTriggerScheduledTask(task: ScheduledPadTask) {
    val pads = _uiState.value.pads
    when (task.targetType) {
      ScheduleTargetType.PAD -> {
        val targetPad = pads.firstOrNull { it.id == task.targetId } ?: return
        audioEngine.triggerScheduledPad(targetPad, task.volume, task.fadeInMs, task.loopPlayback)
      }
      ScheduleTargetType.BANK -> {
        val bankIdx = task.targetId.toIntOrNull() ?: 0
        pads.filter { it.bankIndex == bankIdx }.forEach { pad ->
          audioEngine.triggerScheduledPad(pad, task.volume, task.fadeInMs, task.loopPlayback)
        }
      }
      ScheduleTargetType.SEQUENCER -> {
        val pl = _uiState.value.sequencerPlaylists.firstOrNull { it.id == task.targetId } ?: return
        sequencerEngine.updateAvailablePads(pads)
        setMasterVolume(task.volume)
        sequencerEngine.startPlaylist(pl)
      }
    }
    _uiState.update { it.copy(statusMessage = "Menjalankan jadwal \"${task.title}\"") }
  }

  // ---- Sequencer / Playlist Pad ----

  fun openSequencerSheet() {
    _uiState.update { it.copy(isSequencerSheetOpen = true) }
  }

  fun closeSequencerSheet() {
    _uiState.update { it.copy(isSequencerSheetOpen = false) }
  }

  fun saveSequencerPlaylist(playlist: SequencerPlaylist) {
    viewModelScope.launch {
      val updated = playlist.copy(updatedAtMs = System.currentTimeMillis())
      dao.upsertSequencerPlaylist(updated.toEntity())
      _uiState.update { it.copy(statusMessage = "Playlist \"${updated.name}\" diperbarui") }
    }
  }

  fun deleteSequencerPlaylist(playlistId: String) {
    viewModelScope.launch {
      if (_uiState.value.sequencerPlayback.activePlaylistId == playlistId) {
        sequencerEngine.stop()
      }
      dao.deleteSequencerPlaylistById(playlistId)
      _uiState.update { it.copy(statusMessage = "Playlist dihapus") }
    }
  }

  fun startSequencerPlaylist(playlist: SequencerPlaylist, startAtIndex: Int = 0) {
    sequencerEngine.updateAvailablePads(_uiState.value.pads)
    sequencerEngine.startPlaylist(playlist, startAtIndex)
  }

  fun toggleSequencerPlayPause(playlist: SequencerPlaylist? = null) {
    sequencerEngine.updateAvailablePads(_uiState.value.pads)
    sequencerEngine.togglePlayPause(playlist)
  }

  fun sequencerNext() {
    sequencerEngine.updateAvailablePads(_uiState.value.pads)
    sequencerEngine.nextItem()
  }

  fun sequencerPrev() {
    sequencerEngine.updateAvailablePads(_uiState.value.pads)
    sequencerEngine.prevItem()
  }

  fun stopSequencer() {
    sequencerEngine.stop()
  }

  fun armMidiLearnForSequencer(playlist: SequencerPlaylist) {
    _uiState.update {
      it.copy(
        isMidiLearnMode = true,
        isEditMode = false,
        midiLearnTarget = MidiLearnTarget.SequencerPlaylistNote(playlist.id, playlist.name),
        statusMessage = "Tekan tuts MIDI untuk memicu playlist \"${playlist.name}\"..."
      )
    }
  }

  fun clearSequencerMidiNote(playlist: SequencerPlaylist) {
    saveSequencerPlaylist(playlist.copy(triggerMidiNote = null))
  }

  // ---- Global Settings & Playback Controls ----

  fun toggleEditMode() {
    if (_uiState.value.settings.isFocusMode) return
    if (_uiState.value.settings.isLocked) {
      _uiState.update { it.copy(statusMessage = "Board is locked. Unlock Stage Mode to edit.") }
      return
    }
    _uiState.update {
      val nextEdit = !it.isEditMode
      it.copy(
        isEditMode = nextEdit,
        isMultiSelectMode = if (nextEdit) it.isMultiSelectMode else false,
        selectedPadIds = if (nextEdit) it.selectedPadIds else emptySet(),
        isMidiLearnMode = false,
        midiLearnTarget = null,
        editingPadId = null
      )
    }
  }

  fun toggleMultiSelectMode() {
    if (!_uiState.value.isEditMode || _uiState.value.settings.isLocked) return
    _uiState.update {
      val next = !it.isMultiSelectMode
      it.copy(
        isMultiSelectMode = next,
        selectedPadIds = if (next) it.selectedPadIds else emptySet()
      )
    }
  }

  fun togglePadSelection(padId: String) {
    _uiState.update { state ->
      val updated = if (padId in state.selectedPadIds) {
        state.selectedPadIds - padId
      } else {
        state.selectedPadIds + padId
      }
      state.copy(selectedPadIds = updated)
    }
  }

  fun selectAllPadsInBank(padIds: List<String>) {
    if (padIds.isEmpty()) return
    _uiState.update { state ->
      val allSelected = padIds.all { it in state.selectedPadIds }
      val nextSet = if (allSelected) {
        state.selectedPadIds - padIds.toSet()
      } else {
        state.selectedPadIds + padIds
      }
      state.copy(selectedPadIds = nextSet)
    }
  }

  fun clearPadSelection() {
    _uiState.update { it.copy(selectedPadIds = emptySet(), isMultiSelectMode = false) }
  }

  fun bulkDeleteSelectedPads() {
    val state = _uiState.value
    if (state.settings.isLocked || state.selectedPadIds.isEmpty()) return
    val selectedIds = state.selectedPadIds
    val count = state.pads.count { it.id in selectedIds }
    if (count == 0) return

    recordEditSnapshot(state.pads, "$count pad dihapus")
    selectedIds.forEach { audioEngine.stopPad(it) }
    val remaining = state.pads
      .filterNot { it.id in selectedIds }
      .mapIndexed { idx, p -> p.copy(positionIndex = idx) }
    audioEngine.updatePlaylist(remaining)
    _uiState.update {
      it.copy(
        pads = remaining,
        selectedPadIds = emptySet(),
        isMultiSelectMode = false,
        editingPadId = null
      )
    }
    viewModelScope.launch {
      selectedIds.forEach { dao.deletePadById(it) }
      dao.updateAllPadPositions(remaining.map { it.id to it.positionIndex })
      persistentConfigManager.notifyConfigDirty("bulk_delete_pads")
    }
  }

  fun bulkSetColorSelectedPads(colorArgb: Long?) {
    val state = _uiState.value
    if (state.settings.isLocked || state.selectedPadIds.isEmpty()) return
    val selectedIds = state.selectedPadIds
    val count = state.pads.count { it.id in selectedIds }
    if (count == 0) return

    recordEditSnapshot(state.pads, "Warna $count pad diubah")
    val updatedPads = state.pads.map { p ->
      if (p.id in selectedIds) {
        p.copy(colorArgb = colorArgb, glowColorArgb = colorArgb ?: p.glowColorArgb)
      } else {
        p
      }
    }
    audioEngine.updatePlaylist(updatedPads)
    _uiState.update {
      it.copy(
        pads = updatedPads,
        selectedPadIds = emptySet(),
        isMultiSelectMode = false
      )
    }
    val profileId = state.settings.activeProfileId
    viewModelScope.launch {
      dao.upsertPads(updatedPads.filter { it.id in selectedIds }.map { it.toPadEntity(profileId) })
      persistentConfigManager.notifyConfigDirty("bulk_color_pads")
    }
  }

  fun bulkMoveSelectedPadsToBank(targetBankIndex: Int) {
    val state = _uiState.value
    if (state.settings.isLocked || state.selectedPadIds.isEmpty()) return
    val selectedIds = state.selectedPadIds
    val before = state.pads
    val perBank = state.settings.gridConfig.padsPerBank.coerceAtLeast(1)
    val promoted = if (!state.isBankExplicitMode) {
      before.mapIndexed { idx, p -> p.copy(bankIndex = idx / perBank) }
    } else {
      before
    }
    val createNew = targetBankIndex == -1
    val resolvedTargetBank = if (createNew) {
      ((promoted.maxOfOrNull { it.bankIndex } ?: 0) + 1)
    } else {
      targetBankIndex.coerceAtLeast(0)
    }
    val targetSubName = state.activeBankNames[resolvedTargetBank]
      ?: promoted.firstOrNull { it.bankIndex == resolvedTargetBank }?.subfolderName
      ?: "Bank ${resolvedTargetBank + 1}"

    val count = promoted.count { it.id in selectedIds }
    if (count == 0) return
    recordEditSnapshot(before, "$count pad dipindah ke $targetSubName")

    val updatedPads = promoted.map { p ->
      if (p.id in selectedIds) {
        p.copy(bankIndex = resolvedTargetBank, subfolderName = targetSubName)
      } else {
        p
      }
    }
    audioEngine.updatePlaylist(updatedPads)
    _uiState.update {
      it.copy(
        pads = updatedPads,
        selectedPadIds = emptySet(),
        isMultiSelectMode = false
      )
    }
    val profileId = state.settings.activeProfileId
    viewModelScope.launch {
      val existingProfile = dao.getProfileById(profileId)
      if (existingProfile != null && (!state.isBankExplicitMode || createNew)) {
        val existingOrder = ProfileJsonSerializer.decodeBankOrderString(existingProfile.bankOrderJson)
        val newOrder = (existingOrder + (0..resolvedTargetBank)).distinct()
        val namesMap = ProfileJsonSerializer.decodeBankNamesString(existingProfile.bankNamesJson).toMutableMap()
        if (!namesMap.containsKey(resolvedTargetBank)) {
          namesMap[resolvedTargetBank] = targetSubName
        }
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(namesMap),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(newOrder),
          bankFolderUrisJson = existingProfile.bankFolderUrisJson
        )
      }
      dao.upsertPads(updatedPads.map { it.toPadEntity(profileId) })
      persistentConfigManager.notifyConfigDirty("bulk_move_pads")
    }
  }

  fun bulkDuplicateSelectedPads() {
    val state = _uiState.value
    if (state.settings.isLocked || state.selectedPadIds.isEmpty()) return
    val selectedIds = state.selectedPadIds
    val selectedPads = state.pads.filter { it.id in selectedIds }
    if (selectedPads.isEmpty()) return

    recordEditSnapshot(state.pads, "${selectedPads.size} pad diduplikat")
    val existingNames = state.pads.map { it.displayName }.toMutableSet()
    val clonedPairs = selectedPads.map { src ->
      val base = src.displayName.removeSuffix(" (Clone)").trim().ifEmpty { src.displayName }
      var candidate = "$base (Clone)"
      var counter = 2
      while (candidate in existingNames) {
        candidate = "$base (Clone $counter)"
        counter++
      }
      existingNames.add(candidate)
      src.copy(
        id = "pad_clone_${UUID.randomUUID()}",
        displayName = candidate,
        isClone = true,
        midiNote = null
      )
    }
    val combined = (state.pads + clonedPairs).mapIndexed { idx, p -> p.copy(positionIndex = idx) }
    audioEngine.updatePlaylist(combined)
    _uiState.update {
      it.copy(
        pads = combined,
        selectedPadIds = emptySet(),
        isMultiSelectMode = false
      )
    }
    val profileId = state.settings.activeProfileId
    val insertedClones = combined.takeLast(clonedPairs.size)
    viewModelScope.launch {
      dao.upsertPads(combined.map { it.toPadEntity(profileId) })
      dao.upsertPadSettings(insertedClones.map { it.settings.toEntity(it.id) })
      persistentConfigManager.notifyConfigDirty("bulk_duplicate_pads")
    }
  }

  fun toggleLockMode() {
    val nextLocked = !_uiState.value.settings.isLocked
    viewModelScope.launch {
      settingsRepo.setLocked(nextLocked)
      if (nextLocked) {
        _uiState.update {
          it.copy(
            isEditMode = false,
            isMultiSelectMode = false,
            selectedPadIds = emptySet(),
            isMidiLearnMode = false,
            midiLearnTarget = null,
            editingPadId = null,
            statusMessage = "Stage Lock enabled (layout protected)"
          )
        }
      } else {
        _uiState.update { it.copy(statusMessage = "Stage Lock disabled") }
      }
    }
  }

  private fun recordEditSnapshot(padsBefore: List<SoundPad>, undoMessage: String? = null) {
    undoStack.addLast(padsBefore)
    while (undoStack.size > 25) undoStack.removeFirst()
    redoStack.clear()
    _uiState.update { state ->
      state.copy(
        canUndoEdit = undoStack.isNotEmpty(),
        canRedoEdit = false,
        undoSnackbarVersion = if (undoMessage != null) state.undoSnackbarVersion + 1 else state.undoSnackbarVersion,
        undoSnackbarMessage = undoMessage ?: state.undoSnackbarMessage
      )
    }
  }

  fun undoEdit() {
    if (undoStack.isEmpty() || _uiState.value.settings.isLocked) return
    val previous = undoStack.removeLast()
    redoStack.addLast(_uiState.value.pads)
    applyPadSnapshot(previous, "Perubahan dibatalkan (Undo)")
  }

  fun redoEdit() {
    if (redoStack.isEmpty() || _uiState.value.settings.isLocked) return
    val next = redoStack.removeLast()
    undoStack.addLast(_uiState.value.pads)
    applyPadSnapshot(next, "Perubahan diterapkan ulang (Redo)")
  }

  private fun applyPadSnapshot(snapshotPads: List<SoundPad>, notice: String) {
    val normalized = snapshotPads.sortedBy { it.positionIndex }.mapIndexed { idx, p -> p.copy(positionIndex = idx) }
    val profileId = _uiState.value.settings.activeProfileId
    audioEngine.updatePlaylist(normalized)
    _uiState.update { state ->
      val keepEditingId = state.editingPadId?.takeIf { id -> normalized.any { it.id == id } }
      state.copy(
        pads = normalized,
        editingPadId = keepEditingId,
        selectedPadIds = emptySet(),
        canUndoEdit = undoStack.isNotEmpty(),
        canRedoEdit = redoStack.isNotEmpty(),
        statusMessage = notice
      )
    }
    viewModelScope.launch {
      val existingProfile = dao.getProfileById(profileId)
      val gs = _uiState.value.settings
      val grid = gs.gridConfig
      val profile = existingProfile?.copy(updatedAtMs = System.currentTimeMillis()) ?: PadProfileEntity(
        id = profileId,
        name = DEFAULT_PROFILE_NAME,
        folderUri = gs.folderUri,
        gridPreset = grid.preset.name,
        gridRows = grid.rows,
        gridCols = grid.cols,
        masterEqEnabled = gs.masterEqEnabled,
        masterEqPreset = gs.masterEqPreset.name,
        masterEqBandsJson = ProfileJsonSerializer.encodeEqBandsString(gs.masterEqBandsDb),
        createdAtMs = System.currentTimeMillis(),
        updatedAtMs = System.currentTimeMillis(),
        isActive = true
      )
      dao.syncProfilePadsPreservingSettings(profile, normalized.map { it.toPadEntity(profileId) })
      dao.upsertPadSettings(normalized.map { it.settings.toEntity(it.id) })
      persistentConfigManager.notifyConfigDirty("undo_redo_snapshot")
    }
  }

  fun toggleFavoritesFilter() {
    _uiState.update { it.copy(showFavoritesOnly = !it.showFavoritesOnly) }
  }

  fun selectCategoryTagFilter(tag: String?) {
    _uiState.update { cur ->
      val next = if (cur.selectedCategoryTag == tag) null else tag
      cur.copy(selectedCategoryTag = next)
    }
  }

  fun renameBank(bankIndex: Int, newName: String) {
    val profileId = _uiState.value.settings.activeProfileId
    val trimmed = newName.trim().take(20)
    viewModelScope.launch {
      val existing = dao.getProfileById(profileId) ?: return@launch
      val map = ProfileJsonSerializer.decodeBankNamesString(existing.bankNamesJson).toMutableMap()
      if (trimmed.isEmpty()) {
        map.remove(bankIndex)
      } else {
        map[bankIndex] = trimmed
      }
      dao.updateProfileBankNames(profileId, ProfileJsonSerializer.encodeBankNamesString(map))
      _uiState.update { cur ->
        cur.copy(
          profiles = cur.profiles.map { p ->
            if (p.id == profileId) p.copy(bankNames = map) else p
          },
          statusMessage = if (trimmed.isEmpty()) "Reset Bank ${bankIndex + 1} name" else "Bank ${bankIndex + 1} renamed to \"$trimmed\""
        )
      }
    }
  }

  fun addNewBank() {
    val state = _uiState.value
    val profileId = state.settings.activeProfileId
    val existingKeys = state.bankPages.map { it.bankKey }.distinct()
    val maxExistingKey = (existingKeys + state.activeBankOrder + state.activeBankNames.keys + state.activeBankFolderUris.keys)
      .maxOrNull() ?: -1
    val newBankKey = maxExistingKey + 1
    val newBankLabel = "B${newBankKey + 1}"

    viewModelScope.launch {
      val existing = dao.getProfileById(profileId) ?: return@launch
      val map = ProfileJsonSerializer.decodeBankNamesString(existing.bankNamesJson).toMutableMap()
      if (!map.containsKey(newBankKey)) {
        map[newBankKey] = newBankLabel
      }

      if (!state.isBankExplicitMode && state.pads.isNotEmpty()) {
        val perBank = state.settings.gridConfig.padsPerBank.coerceAtLeast(1)
        val promoted = state.pads.mapIndexed { idx, p -> p.copy(bankIndex = idx / perBank) }
        promoted.forEach { dao.updatePadBankIndex(it.id, it.bankIndex, it.subfolderName) }
        val promotedKeys = promoted.map { it.bankIndex }.distinct().sorted().ifEmpty { listOf(0) }
        val updatedOrder = (promotedKeys + newBankKey).distinct()
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(map),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(updatedOrder),
          bankFolderUrisJson = existing.bankFolderUrisJson
        )
        _uiState.update { cur ->
          val updatedProfiles = cur.profiles.map { p ->
            if (p.id == profileId) p.copy(bankNames = map, bankOrder = updatedOrder) else p
          }
          val tempState = cur.copy(pads = promoted, profiles = updatedProfiles)
          val targetIdx = tempState.bankPages.indexOfFirst { it.bankKey == newBankKey }
            .takeIf { it >= 0 } ?: (tempState.bankPages.size - 1).coerceAtLeast(0)
          tempState.copy(
            requestedBankPageIndex = targetIdx,
            statusMessage = "Bank \"$newBankLabel\" ditambahkan"
          )
        }
      } else {
        val baseOrder = if (state.activeBankOrder.isNotEmpty()) {
          state.activeBankOrder
        } else {
          existingKeys.ifEmpty { listOf(0) }
        }
        val updatedOrder = (baseOrder + newBankKey).distinct()
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(map),
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(updatedOrder),
          bankFolderUrisJson = existing.bankFolderUrisJson
        )
        _uiState.update { cur ->
          val updatedProfiles = cur.profiles.map { p ->
            if (p.id == profileId) p.copy(bankNames = map, bankOrder = updatedOrder) else p
          }
          val tempState = cur.copy(profiles = updatedProfiles)
          val targetIdx = tempState.bankPages.indexOfFirst { it.bankKey == newBankKey }
            .takeIf { it >= 0 } ?: (tempState.bankPages.size - 1).coerceAtLeast(0)
          tempState.copy(
            requestedBankPageIndex = targetIdx,
            statusMessage = "Bank \"$newBankLabel\" ditambahkan"
          )
        }
      }
    }
  }

  fun moveBankOrder(bankKey: Int, delta: Int) {
    val state = _uiState.value
    val profileId = state.settings.activeProfileId
    val currentKeys = state.bankPages.map { it.bankKey }.distinct()
    if (currentKeys.size <= 1) return

    viewModelScope.launch {
      val existing = dao.getProfileById(profileId) ?: return@launch
      if (!state.isBankExplicitMode) {
        // Materialize chunked banks into explicit bankIndices before reordering
        val perBank = state.settings.gridConfig.padsPerBank.coerceAtLeast(1)
        val promoted = state.pads.mapIndexed { idx, p -> p.copy(bankIndex = idx / perBank) }
        val idx = currentKeys.indexOf(bankKey)
        val targetIdx = (idx + delta).coerceIn(0, currentKeys.lastIndex)
        if (idx < 0 || idx == targetIdx) return@launch
        val reordered = currentKeys.toMutableList().apply {
          val removed = removeAt(idx)
          add(targetIdx, removed)
        }
        promoted.forEach { dao.updatePadBankIndex(it.id, it.bankIndex, it.subfolderName) }
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = existing.bankNamesJson,
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(reordered),
          bankFolderUrisJson = existing.bankFolderUrisJson
        )
        _uiState.update { it.copy(pads = promoted, statusMessage = "Urutan bank diperbarui") }
      } else {
        val idx = currentKeys.indexOf(bankKey)
        val targetIdx = (idx + delta).coerceIn(0, currentKeys.lastIndex)
        if (idx < 0 || idx == targetIdx) return@launch
        val reordered = currentKeys.toMutableList().apply {
          val removed = removeAt(idx)
          add(targetIdx, removed)
        }
        dao.updateProfileBankConfig(
          profileId = profileId,
          bankNamesJson = existing.bankNamesJson,
          bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(reordered),
          bankFolderUrisJson = existing.bankFolderUrisJson
        )
        _uiState.update { it.copy(statusMessage = "Urutan bank diperbarui") }
      }
    }
  }

  fun rescanBank(bankKey: Int) {
    val state = _uiState.value
    val folderUriStr = state.settings.folderUri ?: return
    val bankFolderUri = state.activeBankFolderUris[bankKey] ?: run {
      rescanCurrentFolder()
      return
    }
    val treeUri = Uri.parse(folderUriStr)
    if (!scanner.hasPersistedReadPermission(treeUri)) {
      _uiState.update {
        it.copy(
          hasFolderPermissionError = true,
          errorMessage = "Izin akses folder hilang. Silakan pilih ulang folder musik."
        )
      }
      return
    }

    val bankName = state.activeBankNames[bankKey] ?: "Bank ${bankKey + 1}"
    val profileId = state.settings.activeProfileId

    viewModelScope.launch {
      _uiState.update { it.copy(isScanning = true) }
      val existingEntities = dao.getPadsForProfileOnce(profileId)
      val existingSettings = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

      val scanPair = runCatching {
        scanner.scanSingleBankFolder(treeUri, bankFolderUri, bankKey, bankName)
      }.getOrElse { err ->
        logErrorInternal(
          fileName = bankName,
          uriString = bankFolderUri,
          type = PadErrorType.PLAYBACK_ERROR,
          message = "Gagal rescan bank \"$bankName\": ${err.localizedMessage ?: err.javaClass.simpleName}",
          suggestion = PadErrorType.PLAYBACK_ERROR.defaultSuggestion,
          stackTrace = err.stackTraceToString().take(2000)
        )
        _uiState.update { it.copy(isScanning = false, errorMessage = "Gagal rescan bank \"$bankName\"") }
        return@launch
      }

      val (scannedPads, errors) = scanPair
      if (errors.isNotEmpty()) {
        dao.insertErrorLogs(errors.map { it.toEntity() })
      }

      val existingBankByUri = existingEntities.filter { it.bankIndex == bankKey }.groupBy { it.uriString }
      val otherBankPads = existingEntities.filter { it.bankIndex != bankKey }.map { entity ->
        entityToDomain(entity, existingSettings[entity.id].toDomain())
      }

      var nextPos = (existingEntities.maxOfOrNull { it.positionIndex } ?: -1) + 1
      val mergedBankPads = mutableListOf<SoundPad>()
      for (scanned in scannedPads) {
        val matches = existingBankByUri[scanned.uriString]
        if (!matches.isNullOrEmpty()) {
          matches.forEach { entity ->
            mergedBankPads.add(
              mergeScannedWithEntity(scanned, entity, existingSettings[entity.id].toDomain(), false)
            )
          }
        } else {
          mergedBankPads.add(scanned.copy(positionIndex = nextPos++))
        }
      }

      val combined = (otherBankPads + mergedBankPads)
        .sortedBy { it.positionIndex }
        .mapIndexed { idx, pad -> pad.copy(positionIndex = idx) }
      val preloadedIds = if (state.settings.preloadShortAudio) {
        ramCache.preloadShortSamples(combined)
      } else {
        emptySet()
      }
      val ready = combined.map { it.copy(isPreloadedInRam = it.id in preloadedIds || ramCache.isCached(it.uriString)) }

      val existingProfile = dao.getProfileById(profileId)
      if (existingProfile != null) {
        dao.syncProfilePadsPreservingSettings(existingProfile.copy(updatedAtMs = System.currentTimeMillis()), ready.map { it.toPadEntity(profileId) })
      }
      audioEngine.updatePlaylist(ready)
      _uiState.update {
        it.copy(
          pads = ready,
          isScanning = false,
          preloadedCount = ready.count { p -> p.isPreloadedInRam },
          statusMessage = "Bank \"$bankName\" diperbarui (${mergedBankPads.size} file)"
        )
      }
      scheduleMiniWaveformExtraction(ready, state.settings.miniWaveformEnabled)
    }
  }

  fun setMasterLimiterEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setMasterLimiterEnabled(enabled) }
  }

  fun setDuckingEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setDuckingEnabled(enabled) }
  }

  fun setDuckingAttenuation(attenuation: Float) {
    viewModelScope.launch { settingsRepo.setDuckingAttenuation(attenuation) }
  }

  fun setAutoNormalizeEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setAutoNormalizeEnabled(enabled) }
  }

  fun analyzeLoudnessForAllPads() {
    val padsToAnalyze = _uiState.value.pads
    if (padsToAnalyze.isEmpty() || _uiState.value.isNormalizingAll) return
    viewModelScope.launch {
      _uiState.update { it.copy(isNormalizingAll = true) }
      val computed = withContext(Dispatchers.Default) {
        padsToAnalyze.associate { pad ->
          pad.id to AudioWaveformAndCutEngine.computeLoudnessGain(app, pad.uriString)
        }
      }
      computed.forEach { (padId, gain) ->
        dao.updatePadNormalizedGain(padId, gain)
      }
      settingsRepo.setAutoNormalizeEnabled(true)
      persistentConfigManager.notifyConfigDirty("normalize_loudness")
      _uiState.update { state ->
        val updated = state.pads.map { pad ->
          val gain = computed[pad.id] ?: pad.settings.normalizedGain
          pad.copy(settings = pad.settings.copy(normalizedGain = gain))
        }
        audioEngine.updatePlaylist(updated)
        state.copy(
          pads = updated,
          isNormalizingAll = false,
          statusMessage = "Normalized loudness across ${updated.size} pad(s)"
        )
      }
    }
  }

  fun refreshOutputDevices() {
    _uiState.update { it.copy(outputDevices = AudioOutputRouter.listOutputDevices(app)) }
  }

  fun selectMainOutputDevice(deviceId: Int?) {
    audioEngine.setPreferredOutputDeviceId(deviceId)
    _uiState.update { it.copy(selectedMainOutputId = deviceId) }
    val devName = _uiState.value.outputDevices.firstOrNull { it.id == deviceId }?.name
    viewModelScope.launch { settingsRepo.setPreferredMainOutput(deviceId, devName) }
  }

  fun selectCueOutputDevice(deviceId: Int?) {
    audioEngine.setCueOutputDeviceId(deviceId)
    _uiState.update { it.copy(selectedCueOutputId = deviceId) }
    val devName = _uiState.value.outputDevices.firstOrNull { it.id == deviceId }?.name
    viewModelScope.launch { settingsRepo.setPreferredCueOutput(deviceId, devName) }
  }

  fun toggleCuePreview(pad: SoundPad) {
    if (_uiState.value.cuePreviewPadId == pad.id) {
      audioEngine.stopCuePreview()
    } else {
      audioEngine.startCuePreview(pad)
    }
  }

  fun stopCuePreview() {
    audioEngine.stopCuePreview()
  }

  fun toggleSearchVisibility() {
    _uiState.update { state ->
      val nextVisible = !state.isSearchVisible
      state.copy(
        isSearchVisible = nextVisible,
        searchQuery = if (nextVisible) state.searchQuery else ""
      )
    }
  }

  fun setSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun stopAll() {
    audioEngine.stopAll()
  }

  fun fadeOutAll(durationMs: Long = 1500L) {
    audioEngine.fadeOutAll(durationMs)
  }

  fun setPlayMode(mode: PlayMode) {
    viewModelScope.launch { settingsRepo.setPlayMode(mode) }
  }

  fun setGridConfig(config: GridConfig) {
    viewModelScope.launch {
      settingsRepo.setGridConfig(config)
      val profileId = _uiState.value.settings.activeProfileId
      val profile = dao.getProfileById(profileId)
      if (profile != null) {
        dao.upsertProfile(
          profile.copy(
            gridPreset = config.preset.name,
            gridRows = config.rows,
            gridCols = config.cols,
            updatedAtMs = System.currentTimeMillis()
          )
        )
      }
    }
  }

  fun setMasterVolume(volume: Float) {
    audioEngine.setMasterVolume(volume)
    viewModelScope.launch { settingsRepo.setMasterVolume(volume) }
  }

  fun setMasterPan(pan: Float) {
    val clamped = pan.coerceIn(-1f, 1f)
    audioEngine.setMasterPan(clamped)
    viewModelScope.launch { settingsRepo.setMasterPan(clamped) }
  }

  fun setMasterEqEnabled(enabled: Boolean) {
    val gs = _uiState.value.settings
    applyMasterEqInternal(enabled, gs.masterEqPreset, gs.masterEqBandsDb)
  }

  fun setMasterEqPreset(preset: EqPreset) {
    val gs = _uiState.value.settings
    val bands = if (preset == EqPreset.CUSTOM) gs.masterEqBandsDb else preset.gainsDb
    val nextEnabled = if (preset == EqPreset.FLAT) gs.masterEqEnabled else true
    applyMasterEqInternal(nextEnabled, preset, bands)
  }

  fun setMasterEqBand(bandIndex: Int, gainDb: Float) {
    if (bandIndex !in 0..4) return
    val gs = _uiState.value.settings
    val updatedBands = EqPreset.sanitizeBands(gs.masterEqBandsDb).toMutableList().apply {
      this[bandIndex] = gainDb.coerceIn(-12f, 12f)
    }
    applyMasterEqInternal(true, EqPreset.CUSTOM, updatedBands)
  }

  fun resetMasterEq() {
    applyMasterEqInternal(false, EqPreset.FLAT, EqPreset.FLAT.gainsDb)
  }

  private fun applyMasterEqInternal(enabled: Boolean, preset: EqPreset, bandsDb: List<Float>) {
    val sanitized = EqPreset.sanitizeBands(bandsDb)
    val profileId = _uiState.value.settings.activeProfileId
    audioEngine.setMasterEq(enabled, sanitized)
    viewModelScope.launch {
      settingsRepo.setMasterEq(enabled, preset, sanitized)
      dao.updateProfileMasterEq(
        profileId = profileId,
        enabled = enabled,
        preset = preset.name,
        bandsJson = ProfileJsonSerializer.encodeEqBandsString(sanitized)
      )
    }
  }

  fun setMiniWaveformEnabled(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.setMiniWaveformEnabled(enabled)
      if (enabled) {
        scheduleMiniWaveformExtraction(_uiState.value.pads, true)
      }
    }
  }

  fun dismissPendingCrashDialog() {
    _uiState.update { it.copy(pendingCrashLog = null) }
  }

  fun openPadDiagnostic(padId: String) {
    _uiState.update { it.copy(diagnosticPadId = padId) }
  }

  fun dismissPadDiagnostic() {
    _uiState.update { it.copy(diagnosticPadId = null) }
  }

  fun deleteErrorLogById(id: String) {
    viewModelScope.launch {
      dao.deleteErrorLogById(id)
    }
  }

  fun clearErrorLogs() {
    viewModelScope.launch {
      dao.clearErrorLogs()
      _uiState.update { it.copy(statusMessage = "Error log dibersihkan") }
    }
  }

  fun exportErrorLogsToUri(targetUri: Uri) {
    viewModelScope.launch {
      runCatching {
        val logs = dao.getErrorLogsOnce().map { entity ->
          val type = runCatching { PadErrorType.valueOf(entity.errorType) }.getOrDefault(PadErrorType.PLAYBACK_ERROR)
          AppErrorLog(
            id = entity.id,
            timestampMs = entity.timestampMs,
            fileName = entity.fileName,
            uriString = entity.uriString,
            errorType = type,
            message = entity.message,
            suggestion = entity.suggestion,
            stackTrace = entity.stackTrace,
            padId = entity.padId
          )
        }
        val txt = CrashLogManager.formatErrorLogsAsTxt(logs)
        withContext(Dispatchers.IO) {
          app.contentResolver.openOutputStream(targetUri)?.use { out ->
            out.write(txt.toByteArray(Charsets.UTF_8))
          } ?: error("Tidak dapat menulis ke file tujuan")
        }
      }.onSuccess {
        _uiState.update { it.copy(pendingCrashLog = null, statusMessage = "Error log diekspor ke .txt") }
      }.onFailure { err ->
        _uiState.update { it.copy(errorMessage = err.localizedMessage ?: "Gagal mengekspor log") }
      }
    }
  }

  private fun recordPlaybackError(
    pad: SoundPad,
    errorType: PadErrorType,
    message: String,
    stackTrace: String
  ) {
    viewModelScope.launch {
      dao.updatePadErrorState(pad.id, errorType.name, message)
      logErrorInternal(
        fileName = pad.fileName,
        uriString = pad.uriString,
        type = errorType,
        message = message,
        suggestion = errorType.defaultSuggestion,
        stackTrace = stackTrace,
        padId = pad.id
      )
      _uiState.update { state ->
        val updated = state.pads.map { p ->
          if (p.id == pad.id) p.copy(errorType = errorType, errorMessage = message) else p
        }
        state.copy(pads = updated, errorMessage = message)
      }
    }
  }

  private suspend fun logErrorInternal(
    fileName: String,
    uriString: String?,
    type: PadErrorType,
    message: String,
    suggestion: String,
    stackTrace: String = "",
    padId: String? = null
  ) {
    val entity = ErrorLogEntity(
      id = "err_${UUID.randomUUID()}",
      timestampMs = System.currentTimeMillis(),
      fileName = fileName,
      uriString = uriString,
      errorType = type.name,
      message = message,
      suggestion = suggestion,
      stackTrace = stackTrace,
      padId = padId
    )
    dao.insertErrorLog(entity)
  }

  private fun AppErrorLog.toEntity(): ErrorLogEntity {
    return ErrorLogEntity(
      id = id,
      timestampMs = timestampMs,
      fileName = fileName,
      uriString = uriString,
      errorType = errorType.name,
      message = message,
      suggestion = suggestion,
      stackTrace = stackTrace,
      padId = padId
    )
  }

  private fun scheduleMiniWaveformExtraction(pads: List<SoundPad>, enabled: Boolean) {
    if (!enabled) return
    val pendingByUri = pads
      .filter { it.miniWaveform.isEmpty() && it.errorMessage == null && it.uriString.isNotBlank() }
      .distinctBy { it.uriString }
    if (pendingByUri.isEmpty()) return

    miniWaveformJob?.cancel()
    miniWaveformJob = viewModelScope.launch {
      for (pad in pendingByUri) {
        val bars = withContext(Dispatchers.Default) {
          AudioWaveformAndCutEngine.extractMiniWaveform(app, pad.uriString, barCount = 24)
        }
        if (bars.isNotEmpty()) {
          val encoded = ProfileJsonSerializer.encodeMiniWaveform(bars)
          dao.updatePadMiniWaveformByUri(pad.uriString, encoded)
          _uiState.update { state ->
            val updated = state.pads.map { p ->
              if (p.uriString == pad.uriString) p.copy(miniWaveform = bars) else p
            }
            state.copy(pads = updated)
          }
        }
      }
    }
  }

  fun setPreloadShortAudio(enabled: Boolean) {
    viewModelScope.launch {
      settingsRepo.setPreloadShortAudio(enabled)
      if (enabled) {
        val preloadedIds = ramCache.preloadShortSamples(_uiState.value.pads)
        updatePreloadedBadges(preloadedIds)
      } else {
        ramCache.clear()
        updatePreloadedBadges(emptySet())
      }
    }
  }

  fun setKeepScreenOn(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setKeepScreenOn(enabled) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { settingsRepo.setThemeMode(mode) }
  }

  fun setUseDynamicColor(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setUseDynamicColor(enabled) }
  }

  fun setHapticIntensity(intensity: HapticIntensity) {
    viewModelScope.launch { settingsRepo.setHapticIntensity(intensity) }
  }

  fun setSwipePadVolumeEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setSwipePadVolumeEnabled(enabled) }
  }

  fun setDoubleTapToStopEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setDoubleTapToStopEnabled(enabled) }
  }

  fun setHasCompletedOnboarding(completed: Boolean) {
    viewModelScope.launch { settingsRepo.setHasCompletedOnboarding(completed) }
  }

  fun setShowFeatureTooltips(show: Boolean) {
    viewModelScope.launch { settingsRepo.setShowFeatureTooltips(show) }
  }

  fun setAppLanguage(language: AppLanguage) {
    viewModelScope.launch { settingsRepo.setAppLanguage(language) }
  }

  fun setAudioEffectsEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setAudioEffectsEnabled(enabled) }
  }

  fun setVisualEffectsEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setVisualEffectsEnabled(enabled) }
  }

  fun setAppAccentColor(colorArgb: Long) {
    viewModelScope.launch { settingsRepo.setAppAccentColor(colorArgb) }
  }

  fun setPadGridSpacingDp(spacingDp: Int) {
    viewModelScope.launch { settingsRepo.setPadGridSpacingDp(spacingDp.coerceIn(2, 16)) }
  }

  fun toggleGlobalCue() {
    val next = !_uiState.value.settings.isGlobalCueEnabled
    if (!next) {
      audioEngine.stopCuePreview()
    }
    viewModelScope.launch {
      settingsRepo.setGlobalCueEnabled(next)
      _uiState.update {
        it.copy(
          statusMessage = if (next) {
            "Mode Cue Headphone AKTIF: tap pad untuk preview di headphone"
          } else {
            "Mode Cue Headphone dimatikan"
          }
        )
      }
    }
  }

  fun setDefaultPadShape(shape: PadShapeStyle) {
    viewModelScope.launch { settingsRepo.setDefaultPadShape(shape) }
  }

  fun setDefaultCornerGlow(colorArgb: Long) {
    viewModelScope.launch { settingsRepo.setDefaultCornerGlow(colorArgb) }
  }

  fun setDefaultPadColorMode(mode: DefaultPadColorMode) {
    viewModelScope.launch { settingsRepo.setDefaultPadColorMode(mode) }
  }

  fun setDefaultPadColorArgb(colorArgb: Long) {
    viewModelScope.launch { settingsRepo.setDefaultPadColorArgb(colorArgb) }
  }

  fun resetAllPadsToGray() {
    val profileId = _uiState.value.settings.activeProfileId
    val currentPads = _uiState.value.pads
    if (currentPads.isNotEmpty()) {
      recordEditSnapshot(currentPads)
    }
    viewModelScope.launch {
      settingsRepo.setDefaultPadColorMode(DefaultPadColorMode.GRAY)
      val resetPads = currentPads.map { pad ->
        pad.copy(
          colorArgb = null,
          glowColorArgb = null,
          glowIntensity = 0f,
          borderColorArgb = null,
          textColorArgb = null
        )
      }
      if (resetPads.isNotEmpty()) {
        dao.upsertPads(resetPads.map { it.toPadEntity(profileId) })
        audioEngine.updatePlaylist(resetPads)
        persistentConfigManager.notifyConfigDirty("reset_pads_gray")
      }
      _uiState.update { state ->
        state.copy(
          pads = resetPads,
          statusMessage = "Semua pad direset ke abu-abu netral"
        )
      }
    }
  }

  fun setFocusMode(enabled: Boolean) {
    viewModelScope.launch {
      if (enabled) {
        _uiState.update {
          it.copy(
            isEditMode = false,
            isMultiSelectMode = false,
            selectedPadIds = emptySet(),
            isSearchVisible = false,
            searchQuery = ""
          )
        }
      }
      settingsRepo.setFocusMode(enabled)
    }
  }

  fun toggleFocusMode() {
    setFocusMode(!_uiState.value.settings.isFocusMode)
  }

  fun setStartInFocusMode(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setStartInFocusMode(enabled) }
  }

  fun setFocusCornerPanicEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.setFocusCornerPanicEnabled(enabled) }
  }

  fun setBottomBarVisible(visible: Boolean) {
    viewModelScope.launch { settingsRepo.setBottomBarVisible(visible) }
  }

  fun toggleFullscreen() {
    val next = !_uiState.value.settings.isFullscreen
    viewModelScope.launch { settingsRepo.setFullscreen(next) }
  }

  fun toggleGlobalCrossfade() {
    val next = !_uiState.value.settings.globalCrossfadeEnabled
    viewModelScope.launch { settingsRepo.setGlobalCrossfadeEnabled(next) }
  }

  fun setCrossfadeDurationMs(durationMs: Long) {
    viewModelScope.launch { settingsRepo.setCrossfadeDurationMs(durationMs) }
  }

  fun setCrossfadeCurve(curve: FadeCurve) {
    viewModelScope.launch { settingsRepo.setCrossfadeCurve(curve) }
  }

  fun setGlobalFadeInMs(durationMs: Long) {
    viewModelScope.launch { settingsRepo.setGlobalFadeInMs(durationMs) }
  }

  fun setGlobalFadeOutMs(durationMs: Long) {
    viewModelScope.launch { settingsRepo.setGlobalFadeOutMs(durationMs) }
  }

  fun setGlobalFadeCurve(curve: FadeCurve) {
    viewModelScope.launch { settingsRepo.setGlobalFadeCurve(curve) }
  }

  fun dismissNewFilesNotice() {
    _uiState.update { it.copy(newlyDetectedCount = 0) }
  }

  fun showLockUnlockHint() {
    _uiState.update { it.copy(statusMessage = "Tahan tombol kunci selama 2 detik untuk membuka Stage Lock") }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  fun clearError() {
    _uiState.update { it.copy(errorMessage = null, hasFolderPermissionError = false) }
  }

  // ---- Persistent Config SAF Auto-Save & Snapshot Hooks (Stage b) ----

  fun onPersistentBackupFolderSelected(treeUri: Uri) {
    viewModelScope.launch {
      val ok = persistentConfigManager.onBackupFolderSelected(treeUri)
      _uiState.update {
        if (ok) {
          it.copy(statusMessage = "Folder backup config terhubung & tersimpan")
        } else {
          it.copy(errorMessage = "Folder backup tidak dapat diakses. Pastikan memberi izin tulis folder.")
        }
      }
    }
  }

  fun dismissBackupFolderPrompt() {
    viewModelScope.launch {
      persistentConfigManager.dismissFirstLaunchPrompt()
    }
  }

  fun verifyBackupFolderAccess() {
    viewModelScope.launch {
      persistentConfigManager.verifyBackupFolderAccess()
    }
  }

  fun setPersistentAutoSyncEnabled(enabled: Boolean) {
    viewModelScope.launch {
      persistentConfigManager.setAutoSyncEnabled(enabled)
    }
  }

  fun triggerManualBackupSync() {
    viewModelScope.launch {
      val res = persistentConfigManager.syncNow(createSnapshot = true, trigger = SnapshotTrigger.MANUAL)
      _uiState.update {
        if (res.success) {
          it.copy(statusMessage = "Config & snapshot tersimpan ke folder backup")
        } else {
          it.copy(errorMessage = res.errorMessage ?: "Folder backup tidak dapat diakses")
        }
      }
    }
  }

  fun onAppStopping() {
    persistentConfigManager.onAppExiting()
  }

  // ---- Persistent Config Restore, Snapshot Picker, ZIP Export/Import/Share & Reset (Stage c & d) ----

  fun setPersistentIncludeWaveforms(include: Boolean) {
    viewModelScope.launch {
      persistentConfigManager.setIncludeWaveforms(include)
    }
  }

  fun setRestoreRelinkMusicFolder(treeUri: Uri) {
    scanner.takePersistablePermission(treeUri)
    val folderName = scanner.resolveFolderDisplayName(treeUri)
    _uiState.update { state ->
      state.copy(
        restoreUiState = state.restoreUiState.copy(
          selectedRelinkMusicUri = treeUri.toString(),
          selectedRelinkMusicName = folderName
        )
      )
    }
  }

  fun inspectRestoreFolderUri(treeUri: Uri) {
    SafPersistentConfigStorage.takePersistableRwPermission(app, treeUri)
    scanner.takePersistablePermission(treeUri)
    viewModelScope.launch {
      _uiState.update {
        it.copy(restoreUiState = it.restoreUiState.copy(isInspecting = true))
      }
      val res = PersistentConfigRestoreEngine.inspectTreeUri(app, treeUri)
      res.onSuccess { summary ->
        val folderName = scanner.resolveFolderDisplayName(treeUri)
        val relinkUri = summary.detectedMusicFolderUri
          ?: _uiState.value.restoreUiState.selectedRelinkMusicUri
          ?: _uiState.value.settings.folderUri?.takeIf { it != DemoTrackManager.DEMO_FOLDER_URI }
        val relinkName = if (summary.detectedMusicFolderUri != null) {
          folderName
        } else {
          _uiState.value.restoreUiState.selectedRelinkMusicName ?: _uiState.value.settings.folderName
        }
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(
              isInspecting = false,
              pendingInspection = summary,
              selectedRelinkMusicUri = relinkUri,
              selectedRelinkMusicName = relinkName
            )
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(isInspecting = false),
            errorMessage = err.localizedMessage ?: "Tidak ditemukan config backup yang valid di folder ini"
          )
        }
      }
    }
  }

  fun inspectRestoreZipUri(zipUri: Uri) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(restoreUiState = it.restoreUiState.copy(isInspecting = true))
      }
      val displayName = withContext(Dispatchers.IO) {
        runCatching { DocumentFile.fromSingleUri(app, zipUri)?.name }.getOrNull() ?: "Backup.zip"
      }
      val res = ZipBackupArchiver.inspectZipUri(app, zipUri, displayName)
      res.onSuccess { summary ->
        val relinkUri = _uiState.value.settings.folderUri?.takeIf { it != DemoTrackManager.DEMO_FOLDER_URI }
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(
              isInspecting = false,
              pendingInspection = summary,
              selectedRelinkMusicUri = relinkUri,
              selectedRelinkMusicName = _uiState.value.settings.folderName
            )
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(isInspecting = false),
            errorMessage = "Gagal membaca arsip .zip: ${err.localizedMessage ?: "format tidak valid"}"
          )
        }
      }
    }
  }

  fun openSnapshotPicker() {
    viewModelScope.launch {
      val list = withContext(Dispatchers.IO) {
        persistentConfigManager.listCurrentSnapshots()
      }
      _uiState.update { state ->
        state.copy(
          restoreUiState = state.restoreUiState.copy(
            availableSnapshots = list,
            isSnapshotPickerOpen = true
          )
        )
      }
    }
  }

  fun closeSnapshotPicker() {
    _uiState.update { state ->
      state.copy(restoreUiState = state.restoreUiState.copy(isSnapshotPickerOpen = false))
    }
  }

  fun inspectSnapshotBackup(snapshot: SnapshotFileInfo) {
    val backupUriStr = _uiState.value.settings.persistentBackupFolderUri ?: return
    val backupUri = Uri.parse(backupUriStr)
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          restoreUiState = it.restoreUiState.copy(
            isSnapshotPickerOpen = false,
            isInspecting = true
          )
        )
      }
      val res = PersistentConfigRestoreEngine.inspectSnapshotFromTree(app, backupUri, snapshot)
      res.onSuccess { summary ->
        val relinkUri = _uiState.value.settings.folderUri?.takeIf { it != DemoTrackManager.DEMO_FOLDER_URI }
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(
              isInspecting = false,
              pendingInspection = summary,
              selectedRelinkMusicUri = relinkUri,
              selectedRelinkMusicName = _uiState.value.settings.folderName
            )
          )
        }
      }.onFailure { err ->
        _uiState.update {
          it.copy(
            restoreUiState = it.restoreUiState.copy(isInspecting = false),
            errorMessage = err.localizedMessage ?: "Snapshot korup atau tidak bisa dibaca"
          )
        }
      }
    }
  }

  fun dismissRestorePreview() {
    _uiState.update { state ->
      state.copy(
        restoreUiState = state.restoreUiState.copy(
          pendingInspection = null,
          selectedRelinkMusicUri = null,
          selectedRelinkMusicName = null
        )
      )
    }
  }

  fun confirmApplyRestore(options: RestoreSelectionOptions) {
    val inspection = _uiState.value.restoreUiState.pendingInspection ?: return
    val relinkMusicUriStr = _uiState.value.restoreUiState.selectedRelinkMusicUri
      ?: inspection.detectedMusicFolderUri
      ?: _uiState.value.settings.folderUri?.takeIf { it != DemoTrackManager.DEMO_FOLDER_URI }
    val musicTreeUri = relinkMusicUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }

    viewModelScope.launch {
      audioEngine.stopAll()
      audioEngine.stopCuePreview()
      sequencerEngine.stop()

      _uiState.update {
        it.copy(
          restoreUiState = it.restoreUiState.copy(
            pendingInspection = null,
            isRestoreInProgress = true,
            restoreProgress = 0.05f,
            restoreStageText = "Memulai pemulihan config…"
          )
        )
      }

      val outcome = persistentConfigManager.pauseSyncDuring {
        PersistentConfigRestoreEngine.applyRestore(
          context = app,
          dao = dao,
          settingsRepo = settingsRepo,
          inspection = inspection,
          options = options,
          musicFolderUri = musicTreeUri,
          onProgress = { pct, label ->
            _uiState.update { cur ->
              cur.copy(
                restoreUiState = cur.restoreUiState.copy(
                  restoreProgress = pct,
                  restoreStageText = label
                )
              )
            }
          }
        )
      }

      if (outcome.success) {
        undoStack.clear()
        redoStack.clear()
        val activeProfileId = dao.getAllProfilesOnce().firstOrNull { it.isActive }?.id
          ?: DEFAULT_PROFILE_ID
        lastLoadedProfileId = activeProfileId
        val preload = _uiState.value.settings.preloadShortAudio
        loadProfilePads(activeProfileId, preload)
        val restoredGs = inspection.bundle.settings.settings
        if (options.restoreSettings) {
          audioEngine.setMasterEq(restoredGs.masterEqEnabled, restoredGs.masterEqBandsDb)
        }
        val activeMusicStr = musicTreeUri?.toString()
          ?: restoredGs.folderUri?.takeIf { it != DemoTrackManager.DEMO_FOLDER_URI }
        if (!activeMusicStr.isNullOrBlank()) {
          lastObservedFolderUri = activeMusicStr
        }

        val summaryStatus = buildString {
          append("Config dipulihkan: ${outcome.restoredProfileCount} profil, ${outcome.restoredPadCount} pad")
          if (outcome.relinkedPadCount > 0) {
            append(" · ${outcome.relinkedPadCount} file ditautkan")
          }
          if (outcome.missingFilePadCount > 0) {
            append(" · ${outcome.missingFilePadCount} file hilang (lihat Log Error)")
          }
        }
        _uiState.update {
          it.copy(
            canUndoEdit = false,
            canRedoEdit = false,
            statusMessage = summaryStatus,
            restoreUiState = RestoreUiState(lastRestoreOutcome = outcome)
          )
        }
        persistentConfigManager.notifyConfigDirty("restore_completed")
      } else {
        _uiState.update {
          it.copy(
            errorMessage = outcome.errorMessage ?: "Gagal memulihkan config",
            restoreUiState = RestoreUiState(lastRestoreOutcome = outcome)
          )
        }
      }
    }
  }

  fun exportConfigZipToUri(targetUri: Uri) {
    viewModelScope.launch {
      val gs = _uiState.value.settings
      val bundle = PersistentConfigSnapshotAssembler.captureFromDatabase(
        dao = dao,
        settings = gs,
        context = app,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        includeWaveforms = gs.persistentIncludeWaveforms,
        computeFileHashes = true,
        outputDeviceNameResolver = { devId ->
          if (devId == null) null else AudioOutputRouter.listOutputDevices(app).firstOrNull { it.id == devId }?.name
        }
      )
      ZipBackupArchiver.exportBundleToUri(app, bundle, targetUri, gs.persistentIncludeWaveforms)
        .onSuccess { count ->
          _uiState.update {
            it.copy(statusMessage = "Arsip backup .zip tersimpan ($count file config)")
          }
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(errorMessage = "Export .zip gagal: ${err.localizedMessage}")
          }
        }
    }
  }

  fun prepareAndShareConfigZip(onIntentReady: (Intent) -> Unit) {
    viewModelScope.launch {
      val gs = _uiState.value.settings
      val bundle = PersistentConfigSnapshotAssembler.captureFromDatabase(
        dao = dao,
        settings = gs,
        context = app,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE,
        includeWaveforms = gs.persistentIncludeWaveforms,
        computeFileHashes = true,
        outputDeviceNameResolver = { devId ->
          if (devId == null) null else AudioOutputRouter.listOutputDevices(app).firstOrNull { it.id == devId }?.name
        }
      )
      ZipBackupArchiver.createShareableZipFile(app, bundle, gs.persistentIncludeWaveforms)
        .onSuccess { (_, contentUri) ->
          val chooser = ZipBackupArchiver.buildShareBackupIntent(app, contentUri)
          onIntentReady(chooser)
        }
        .onFailure { err ->
          _uiState.update {
            it.copy(errorMessage = "Gagal menyiapkan file backup untuk dibagikan: ${err.localizedMessage}")
          }
        }
    }
  }

  fun deleteAllConfig(alsoClearSafBackupFolder: Boolean) {
    viewModelScope.launch {
      audioEngine.stopAll()
      audioEngine.stopCuePreview()
      sequencerEngine.stop()
      folderObserverJob?.cancel()

      persistentConfigManager.pauseSyncDuring {
        if (alsoClearSafBackupFolder) {
          persistentConfigManager.clearBackupFolderFilesIfConfigured()
        }
        dao.clearAllConfigTables()
        val now = System.currentTimeMillis()
        dao.upsertProfile(
          PadProfileEntity(
            id = DEFAULT_PROFILE_ID,
            name = DEFAULT_PROFILE_NAME,
            folderUri = null,
            gridPreset = GridPreset.GRID_4X4.name,
            gridRows = 4,
            gridCols = 4,
            createdAtMs = now,
            updatedAtMs = now,
            isActive = true
          )
        )
        settingsRepo.resetAllSettingsToDefaults(clearBackupFolder = alsoClearSafBackupFolder)
      }

      runCatching { PadSchedulerManager.rescheduleAllActive(app) }
      undoStack.clear()
      redoStack.clear()
      lastObservedFolderUri = null
      lastLoadedProfileId = DEFAULT_PROFILE_ID
      audioEngine.updatePlaylist(emptyList())
      sequencerEngine.updateAvailablePads(emptyList())
      audioEngine.setMasterEq(false, listOf(0f, 0f, 0f, 0f, 0f))

      _uiState.update {
        it.copy(
          pads = emptyList(),
          midiCcMappings = emptyList(),
          midiPcMappings = emptyList(),
          preloadedCount = 0,
          canUndoEdit = false,
          canRedoEdit = false,
          statusMessage = "Semua config berhasil dihapus & dikembalikan ke awal"
        )
      }
    }
  }

  private suspend fun loadProfilePads(profileId: String, preloadRam: Boolean) {
    val cachedEntities = dao.getPadsForProfileOnce(profileId)
    val cachedSettings = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

    val domainPads = cachedEntities.map { entity ->
      entityToDomain(entity, cachedSettings[entity.id].toDomain()).copy(
        isPreloadedInRam = ramCache.isCached(entity.uriString)
      )
    }

    val preloadedIds = if (preloadRam && domainPads.isNotEmpty()) {
      ramCache.preloadShortSamples(domainPads)
    } else {
      emptySet()
    }
    val readyPads = domainPads.map { it.copy(isPreloadedInRam = it.id in preloadedIds) }
    val ccMappings = dao.getMidiCcMappingsOnce(profileId).toDomainMappings()
    val pcMappings = dao.getMidiPcMappingsOnce(profileId).map { entity ->
      MidiProgramBankMapping(
        id = entity.id,
        profileId = entity.profileId,
        programNumber = entity.programNumber,
        bankIndex = entity.bankIndex,
        channel = entity.channel
      )
    }

    audioEngine.updatePlaylist(readyPads)
    sequencerEngine.updateAvailablePads(readyPads)
    _uiState.update {
      it.copy(
        pads = readyPads,
        midiCcMappings = ccMappings,
        midiPcMappings = pcMappings,
        preloadedCount = preloadedIds.size
      )
    }
    scheduleMiniWaveformExtraction(readyPads, _uiState.value.settings.miniWaveformEnabled)
  }

  private suspend fun loadCachedAndScan(profileId: String, treeUri: Uri, preloadRam: Boolean) {
    loadProfilePads(profileId, preloadRam)
    if (treeUri.toString() == DemoTrackManager.DEMO_FOLDER_URI) return
    if (!scanner.hasPersistedReadPermission(treeUri)) {
      logErrorInternal(
        fileName = scanner.resolveFolderDisplayName(treeUri),
        uriString = treeUri.toString(),
        type = PadErrorType.MISSING_PERMISSION,
        message = "Izin akses SAF untuk folder musik hilang atau dicabut.",
        suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion
      )
      _uiState.update {
        it.copy(
          hasFolderPermissionError = true,
          errorMessage = "Folder permission is required to read audio files."
        )
      }
      return
    }
    scanAndPersist(
      profileId = profileId,
      treeUri = treeUri,
      preloadRam = preloadRam,
      showAutoDetectBadge = _uiState.value.pads.isNotEmpty()
    )
  }

  private fun mergeScannedWithEntity(
    scanned: SoundPad,
    entity: SoundPadEntity,
    settings: PadSettings,
    isPreloadedInRam: Boolean
  ): SoundPad {
    return scanned.copy(
      id = entity.id,
      displayName = entity.displayName,
      positionIndex = entity.positionIndex,
      bankIndex = scanned.bankIndex,
      subfolderName = scanned.subfolderName,
      colorArgb = entity.colorArgb,
      glowColorArgb = entity.glowColorArgb,
      glowIntensity = entity.glowIntensity,
      shapeStyle = entity.shapeStyle?.let { s -> runCatching { PadShapeStyle.valueOf(s) }.getOrNull() },
      borderColorArgb = entity.borderColorArgb,
      borderWidthDp = entity.borderWidthDp,
      cornerRadiusDp = entity.cornerRadiusDp,
      textColorArgb = entity.textColorArgb,
      surfaceFinish = runCatching { PadSurfaceFinish.valueOf(entity.surfaceFinish) }.getOrDefault(PadSurfaceFinish.CLASSIC),
      badgeIcon = runCatching { PadBadgeIcon.valueOf(entity.badgeIcon) }.getOrDefault(PadBadgeIcon.NONE),
      showSlotNumber = entity.showSlotNumber,
      showDurationLabel = entity.showDurationLabel,
      isClone = entity.isClone,
      midiNote = entity.midiNote,
      isFavorite = entity.isFavorite,
      categoryTag = entity.categoryTag,
      miniWaveform = ProfileJsonSerializer.decodeMiniWaveform(entity.miniWaveformJson),
      errorType = scanned.errorType,
      errorMessage = scanned.errorMessage,
      isPreloadedInRam = isPreloadedInRam,
      settings = settings
    )
  }

  private suspend fun scanAndPersist(
    profileId: String,
    treeUri: Uri,
    preloadRam: Boolean,
    showAutoDetectBadge: Boolean
  ) {
    _uiState.update { it.copy(isScanning = true, errorMessage = null, hasFolderPermissionError = false) }
    val existingEntities = dao.getPadsForProfileOnce(profileId)
    val existingByUri = existingEntities.groupBy { it.uriString }
    val existingSettings = dao.getPadSettingsForProfileOnce(profileId).associateBy { it.padId }

    val result = runCatching {
      scanner.scanFolder(treeUri, emptySet())
    }.getOrElse { err ->
      logErrorInternal(
        fileName = scanner.resolveFolderDisplayName(treeUri),
        uriString = treeUri.toString(),
        type = if (err is SecurityException) PadErrorType.MISSING_PERMISSION else PadErrorType.PLAYBACK_ERROR,
        message = "Gagal memindai folder: ${err.localizedMessage ?: err.javaClass.simpleName}",
        suggestion = PadErrorType.MISSING_PERMISSION.defaultSuggestion,
        stackTrace = err.stackTraceToString().take(2000)
      )
      _uiState.update {
        it.copy(
          isScanning = false,
          errorMessage = err.localizedMessage ?: "Failed to scan folder."
        )
      }
      return
    }

    if (result.detectedErrors.isNotEmpty()) {
      dao.insertErrorLogs(result.detectedErrors.map { it.toEntity() })
    }

    val validPads = result.pads.filter { it.errorMessage == null }
    val preloadedIds = if (preloadRam) {
      ramCache.preloadShortSamples(validPads)
    } else {
      emptySet()
    }

    var nextPos = (existingEntities.maxOfOrNull { it.positionIndex } ?: -1) + 1
    var newFilesCount = 0
    val mergedPads = mutableListOf<SoundPad>()
    val relinkedScannedUris = mutableSetOf<String>()
    val preservedEntityIds = mutableSetOf<String>()

    // Langkah 1: Cocokkan lebih dulu pad "File hilang" (hasil restore) ke file musik hasil scan baru
    val missingRestoredEntities = existingEntities.filter {
      it.errorMessage?.startsWith(PersistentConfigRestoreEngine.MISSING_FILE_ERROR_PREFIX) == true
    }
    for (missingEntity in missingRestoredEntities) {
      val matchedScanned = AudioFileRelinker.matchScannedPadToMissingEntity(result.pads, missingEntity)
      if (matchedScanned != null) {
        preservedEntityIds.add(missingEntity.id)
        relinkedScannedUris.add(matchedScanned.uriString)
        mergedPads.add(
          mergeScannedWithEntity(
            scanned = matchedScanned,
            entity = missingEntity,
            settings = existingSettings[missingEntity.id].toDomain(),
            isPreloadedInRam = matchedScanned.id in preloadedIds || ramCache.isCached(matchedScanned.uriString)
          )
        )
      } else {
        // File tetap belum ditemukan -> pertahankan posisi slot & penanda "File hilang"
        preservedEntityIds.add(missingEntity.id)
        mergedPads.add(
          entityToDomain(missingEntity, existingSettings[missingEntity.id].toDomain()).copy(
            isPreloadedInRam = false
          )
        )
      }
    }

    val scannedUris = result.pads.map { it.uriString }.toSet()
    for (scanned in result.pads) {
      val matchingExisting = existingByUri[scanned.uriString].orEmpty()
        .filter { it.id !in preservedEntityIds }
      if (matchingExisting.isNotEmpty()) {
        for (entity in matchingExisting) {
          preservedEntityIds.add(entity.id)
          mergedPads.add(
            mergeScannedWithEntity(
              scanned = scanned,
              entity = entity,
              settings = existingSettings[entity.id].toDomain(),
              isPreloadedInRam = scanned.id in preloadedIds || ramCache.isCached(scanned.uriString)
            )
          )
        }
      } else if (scanned.uriString !in relinkedScannedUris) {
        newFilesCount++
        mergedPads.add(
          scanned.copy(
            positionIndex = nextPos++,
            isPreloadedInRam = scanned.id in preloadedIds
          )
        )
      }
    }

    existingEntities.filter {
      it.id !in preservedEntityIds &&
        it.uriString !in scannedUris &&
        (it.uriString.startsWith("file://") || it.uriString.startsWith("demo://"))
    }.forEach { localEntity ->
      mergedPads.add(
        entityToDomain(localEntity, existingSettings[localEntity.id].toDomain()).copy(
          isPreloadedInRam = ramCache.isCached(localEntity.uriString)
        )
      )
    }

    val normalized = mergedPads.sortedBy { it.positionIndex }.mapIndexed { idx, pad ->
      pad.copy(positionIndex = idx)
    }

    val now = System.currentTimeMillis()
    val existingProfile = dao.getProfileById(profileId)
    val grid = _uiState.value.settings.gridConfig
    val existingBankNames = ProfileJsonSerializer.decodeBankNamesString(existingProfile?.bankNamesJson)
    val finalBankNames = if (result.hasSubfolderBanks) {
      result.bankNames.toMutableMap().apply {
        existingBankNames.forEach { (k, v) -> if (k in result.bankNames && v.isNotBlank()) put(k, v) }
      }
    } else {
      existingBankNames
    }
    val finalBankOrder = if (result.hasSubfolderBanks) {
      val existingOrder = ProfileJsonSerializer.decodeBankOrderString(existingProfile?.bankOrderJson)
        .filter { it in result.bankNames.keys }
      (existingOrder + result.bankNames.keys.sorted()).distinct()
    } else {
      emptyList()
    }
    val gs = _uiState.value.settings
    val profile = PadProfileEntity(
      id = profileId,
      name = existingProfile?.name ?: DEFAULT_PROFILE_NAME,
      folderUri = result.folderUri,
      gridPreset = grid.preset.name,
      gridRows = grid.rows,
      gridCols = grid.cols,
      bankNamesJson = ProfileJsonSerializer.encodeBankNamesString(finalBankNames),
      bankOrderJson = ProfileJsonSerializer.encodeBankOrderString(finalBankOrder),
      bankFolderUrisJson = ProfileJsonSerializer.encodeBankFolderUrisString(result.bankFolderUris),
      masterEqEnabled = existingProfile?.masterEqEnabled ?: gs.masterEqEnabled,
      masterEqPreset = existingProfile?.masterEqPreset ?: gs.masterEqPreset.name,
      masterEqBandsJson = existingProfile?.masterEqBandsJson
        ?: ProfileJsonSerializer.encodeEqBandsString(gs.masterEqBandsDb),
      createdAtMs = existingProfile?.createdAtMs ?: now,
      updatedAtMs = now,
      isActive = true
    )
    val entities = normalized.map { it.toPadEntity(profileId) }
    dao.syncProfilePadsPreservingSettings(profile, entities)
    persistentConfigManager.notifyConfigDirty("scan_folder")

    audioEngine.updatePlaylist(normalized)
    _uiState.update {
      it.copy(
        pads = normalized,
        isScanning = false,
        preloadedCount = preloadedIds.size,
        newlyDetectedCount = if (showAutoDetectBadge && existingEntities.isNotEmpty()) newFilesCount else 0
      )
    }
    scheduleMiniWaveformExtraction(normalized, _uiState.value.settings.miniWaveformEnabled)
  }

  private fun startObservingFolder(treeUri: Uri) {
    folderObserverJob?.cancel()
    folderObserverJob = viewModelScope.launch {
      scanner.observeFolderChanges(treeUri)
        .debounce(1200L)
        .collect {
          if (!_uiState.value.isScanning && scanner.hasPersistedReadPermission(treeUri)) {
            scanAndPersist(
              profileId = _uiState.value.settings.activeProfileId,
              treeUri = treeUri,
              preloadRam = _uiState.value.settings.preloadShortAudio,
              showAutoDetectBadge = true
            )
          }
        }
    }
  }

  private fun updatePreloadedBadges(preloadedIds: Set<String>) {
    _uiState.update { state ->
      val updated = state.pads.map { pad ->
        pad.copy(isPreloadedInRam = pad.id in preloadedIds)
      }
      audioEngine.updatePlaylist(updated)
      state.copy(pads = updated, preloadedCount = preloadedIds.size)
    }
  }

  private fun entityToDomain(entity: SoundPadEntity, settings: PadSettings): SoundPad {
    val style = entity.shapeStyle?.let { runCatching { PadShapeStyle.valueOf(it) }.getOrNull() }
    val finish = runCatching { PadSurfaceFinish.valueOf(entity.surfaceFinish) }.getOrDefault(PadSurfaceFinish.CLASSIC)
    val badge = runCatching { PadBadgeIcon.valueOf(entity.badgeIcon) }.getOrDefault(PadBadgeIcon.NONE)
    val errType = entity.errorType?.let { runCatching { PadErrorType.valueOf(it) }.getOrNull() }
    return SoundPad(
      id = entity.id,
      uriString = entity.uriString,
      fileName = entity.fileName,
      displayName = entity.displayName,
      extension = entity.extension,
      durationMs = entity.durationMs,
      sizeBytes = entity.sizeBytes,
      positionIndex = entity.positionIndex,
      bankIndex = entity.bankIndex,
      subfolderName = entity.subfolderName,
      colorArgb = entity.colorArgb,
      glowColorArgb = entity.glowColorArgb,
      glowIntensity = entity.glowIntensity,
      shapeStyle = style,
      borderColorArgb = entity.borderColorArgb,
      borderWidthDp = entity.borderWidthDp,
      cornerRadiusDp = entity.cornerRadiusDp,
      textColorArgb = entity.textColorArgb,
      surfaceFinish = finish,
      badgeIcon = badge,
      showSlotNumber = entity.showSlotNumber,
      showDurationLabel = entity.showDurationLabel,
      isClone = entity.isClone,
      midiNote = entity.midiNote,
      isFavorite = entity.isFavorite,
      categoryTag = entity.categoryTag,
      miniWaveform = ProfileJsonSerializer.decodeMiniWaveform(entity.miniWaveformJson),
      errorType = errType,
      errorMessage = entity.errorMessage,
      settings = settings
    )
  }

  private fun SoundPad.toPadEntity(profileId: String): SoundPadEntity {
    return SoundPadEntity(
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
  }

  private fun PadSettingEntity?.toDomain(): PadSettings {
    if (this == null) return PadSettings()
    val mode = runCatching { PadTriggerMode.valueOf(triggerMode) }.getOrDefault(PadTriggerMode.TOGGLE)
    val curve = runCatching { FadeCurve.valueOf(fadeCurve) }.getOrDefault(FadeCurve.EQUAL_POWER)
    val parsedEqPreset = runCatching { EqPreset.valueOf(eqPreset) }.getOrDefault(EqPreset.FLAT)
    val parsedFxPreset = runCatching { FxPreset.valueOf(fxPreset) }.getOrDefault(FxPreset.OFF)
    return PadSettings(
      volume = volume.coerceIn(0f, 1f),
      speed = speed.coerceIn(0.5f, 2.0f),
      pitch = pitch.coerceIn(0.5f, 2.0f),
      pan = pan.coerceIn(-1.0f, 1.0f),
      isLooping = isLooping,
      triggerMode = mode,
      chokeGroup = chokeGroup.coerceIn(0, 4),
      fadeInMs = fadeInMs.coerceIn(0L, 10000L),
      fadeOutMs = fadeOutMs.coerceIn(0L, 10000L),
      fadeCurve = curve,
      crossfadeEnabled = crossfadeEnabled,
      trimStartMs = trimStartMs.coerceAtLeast(0L),
      trimEndMs = trimEndMs.coerceAtLeast(0L),
      isDuckingSource = isDuckingSource,
      normalizedGain = normalizedGain.coerceIn(0.35f, 1.60f),
      eqEnabled = eqEnabled,
      eqPreset = parsedEqPreset,
      eqBandsDb = ProfileJsonSerializer.decodeEqBandsString(eqBandsJson),
      isReversed = isReversed,
      fxPreset = parsedFxPreset,
      echoEnabled = echoEnabled,
      echoDelayMs = echoDelayMs.coerceIn(60f, 800f),
      echoFeedback = echoFeedback.coerceIn(0f, 0.75f),
      echoMix = echoMix.coerceIn(0f, 0.85f),
      reverbEnabled = reverbEnabled,
      reverbRoomSize = reverbRoomSize.coerceIn(0f, 1f),
      reverbDamping = reverbDamping.coerceIn(0f, 1f),
      reverbMix = reverbMix.coerceIn(0f, 0.85f)
    )
  }

  private fun PadSettings.toEntity(padId: String): PadSettingEntity {
    return PadSettingEntity(
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

  private fun List<MidiCcMappingEntity>.toDomainMappings(): List<MidiCcMapping> {
    return mapNotNull { entity ->
      val target = runCatching { MidiCcTarget.valueOf(entity.target) }.getOrNull() ?: return@mapNotNull null
      MidiCcMapping(
        id = entity.id,
        profileId = entity.profileId,
        target = target,
        ccNumber = entity.ccNumber,
        channel = entity.channel
      )
    }
  }

  object AppHolder {
    @Volatile
    var application: Application? = null
  }
}

fun SoundPadEntity.toDomainPad(setting: PadSettingEntity?, isPreloaded: Boolean = false): SoundPad {
  val style = shapeStyle?.let { runCatching { PadShapeStyle.valueOf(it) }.getOrNull() }
  val finish = runCatching { PadSurfaceFinish.valueOf(surfaceFinish) }.getOrDefault(PadSurfaceFinish.CLASSIC)
  val badge = runCatching { PadBadgeIcon.valueOf(badgeIcon) }.getOrDefault(PadBadgeIcon.NONE)
  val errType = errorType?.let { runCatching { PadErrorType.valueOf(it) }.getOrNull() }
  val mode = setting?.let { runCatching { PadTriggerMode.valueOf(it.triggerMode) }.getOrDefault(PadTriggerMode.TOGGLE) } ?: PadTriggerMode.TOGGLE
  val curve = setting?.let { runCatching { FadeCurve.valueOf(it.fadeCurve) }.getOrDefault(FadeCurve.EQUAL_POWER) } ?: FadeCurve.EQUAL_POWER
  val eqPreset = setting?.let { runCatching { EqPreset.valueOf(it.eqPreset) }.getOrDefault(EqPreset.FLAT) } ?: EqPreset.FLAT
  val fxPreset = setting?.let { runCatching { FxPreset.valueOf(it.fxPreset) }.getOrDefault(FxPreset.OFF) } ?: FxPreset.OFF
  return SoundPad(
    id = id,
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
    shapeStyle = style,
    borderColorArgb = borderColorArgb,
    borderWidthDp = borderWidthDp,
    cornerRadiusDp = cornerRadiusDp,
    textColorArgb = textColorArgb,
    surfaceFinish = finish,
    badgeIcon = badge,
    showSlotNumber = showSlotNumber,
    showDurationLabel = showDurationLabel,
    isClone = isClone,
    midiNote = midiNote,
    isPreloadedInRam = isPreloaded,
    isFavorite = isFavorite,
    categoryTag = categoryTag,
    miniWaveform = ProfileJsonSerializer.decodeMiniWaveform(miniWaveformJson),
    errorType = errType,
    errorMessage = errorMessage,
    settings = if (setting == null) PadSettings() else PadSettings(
      volume = setting.volume.coerceIn(0f, 1f),
      speed = setting.speed.coerceIn(0.5f, 2.0f),
      pitch = setting.pitch.coerceIn(0.5f, 2.0f),
      pan = setting.pan.coerceIn(-1.0f, 1.0f),
      isLooping = setting.isLooping,
      triggerMode = mode,
      chokeGroup = setting.chokeGroup.coerceIn(0, 4),
      fadeInMs = setting.fadeInMs.coerceIn(0L, 10000L),
      fadeOutMs = setting.fadeOutMs.coerceIn(0L, 10000L),
      fadeCurve = curve,
      crossfadeEnabled = setting.crossfadeEnabled,
      trimStartMs = setting.trimStartMs.coerceAtLeast(0L),
      trimEndMs = setting.trimEndMs.coerceAtLeast(0L),
      isDuckingSource = setting.isDuckingSource,
      normalizedGain = setting.normalizedGain.coerceIn(0.35f, 1.60f),
      eqEnabled = setting.eqEnabled,
      eqPreset = eqPreset,
      eqBandsDb = ProfileJsonSerializer.decodeEqBandsString(setting.eqBandsJson),
      isReversed = setting.isReversed,
      fxPreset = fxPreset,
      echoEnabled = setting.echoEnabled,
      echoDelayMs = setting.echoDelayMs.coerceIn(60f, 800f),
      echoFeedback = setting.echoFeedback.coerceIn(0f, 0.75f),
      echoMix = setting.echoMix.coerceIn(0f, 0.85f),
      reverbEnabled = setting.reverbEnabled,
      reverbRoomSize = setting.reverbRoomSize.coerceIn(0f, 1f),
      reverbDamping = setting.reverbDamping.coerceIn(0f, 1f),
      reverbMix = setting.reverbMix.coerceIn(0f, 0.85f)
    )
  )
}
