package com.example.data.backup

import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.GlobalSettings
import com.example.domain.model.MidiCcMapping
import com.example.domain.model.MidiProgramBankMapping
import com.example.domain.model.PadProfile
import com.example.domain.model.ScheduledPadTask
import com.example.domain.model.SequencerPlaylist
import com.example.domain.model.SoundPad
import org.json.JSONObject

object PersistentConfigSchema {
  const val CURRENT_SCHEMA_VERSION = 2
  const val MIN_SUPPORTED_SCHEMA_VERSION = 1

  const val SUGGESTED_BACKUP_RELATIVE_PATH = "Documents/SoundPadPro"
  const val MUSIC_SUBFOLDER_NAME = ".soundpadpro"
  const val MAX_ROTATED_SNAPSHOTS = 10

  const val FILE_CONFIG_META = "config_meta.json"
  const val FILE_SETTINGS = "settings.json"
  const val FILE_MIDI_MAPPING = "midi_mapping.json"
  const val FILE_SCHEDULER = "scheduler.json"
  const val FILE_SEQUENCER = "sequencer.json"
  const val FILE_COMBINED_CONFIG = "config.json"

  const val DIR_PROFILES = "profiles"
  const val DIR_LAYOUTS = "layouts"
  const val DIR_PRESETS = "presets"
  const val DIR_BACKUPS = "backups"

  const val FILE_PRESETS_EQ = "presets/eq_presets.json"
  const val FILE_PRESETS_FX = "presets/fx_presets.json"
  const val FILE_PRESETS_CURVES = "presets/fade_curves.json"
  const val FILE_PRESETS_COMBINED = "presets/presets.json"
}

data class ConfigMetaJson(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val appName: String = "SoundPad Pro",
  val appVersionName: String = "1.0",
  val appVersionCode: Int = 1,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
  val lastSyncedAtMs: Long = System.currentTimeMillis(),
  val profileCount: Int = 0,
  val bankCount: Int = 0,
  val padCount: Int = 0,
  val layoutCount: Int = 0,
  val schedulerCount: Int = 0,
  val sequencerCount: Int = 0,
  val includesWaveforms: Boolean = false,
  val isEncrypted: Boolean = false,
  val backupSource: String = "saf_folder",
  val includedSections: List<String> = emptyList()
)

data class AudioOutputConfigBackup(
  val mainOutputDeviceId: Int? = null,
  val mainOutputDeviceName: String? = null,
  val cueOutputDeviceId: Int? = null,
  val cueOutputDeviceName: String? = null,
  val audioBufferSizeMs: Int = 50
)

data class PersistentSyncConfigBackup(
  val backupFolderUri: String? = null,
  val backupFolderName: String? = null,
  val autoSyncEnabled: Boolean = true,
  val lastSyncAtMs: Long = 0L,
  val includeWaveforms: Boolean = false
)

data class SettingsBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val settings: GlobalSettings = GlobalSettings(),
  val audioOutput: AudioOutputConfigBackup = AudioOutputConfigBackup(
    mainOutputDeviceId = settings.preferredMainOutputId,
    mainOutputDeviceName = settings.preferredMainOutputName,
    cueOutputDeviceId = settings.preferredCueOutputId,
    cueOutputDeviceName = settings.preferredCueOutputName,
    audioBufferSizeMs = settings.audioBufferSizeMs
  ),
  val syncConfig: PersistentSyncConfigBackup = PersistentSyncConfigBackup(
    backupFolderUri = settings.persistentBackupFolderUri,
    backupFolderName = settings.persistentBackupFolderName,
    autoSyncEnabled = settings.persistentAutoSyncEnabled,
    lastSyncAtMs = settings.persistentLastSyncAtMs,
    includeWaveforms = settings.persistentIncludeWaveforms
  )
)

data class PadFileMetadata(
  val padId: String,
  val uriString: String,
  val relativePath: String = "",
  val fileName: String,
  val sizeBytes: Long = 0L,
  val sha1Hash: String = "",
  val durationMs: Long = 0L,
  val extension: String = ""
)

data class BankBackupEntry(
  val bankIndex: Int,
  val name: String,
  val folderUri: String? = null,
  val orderPosition: Int = bankIndex
)

data class PadBackupEntry(
  val pad: SoundPad,
  val slotIndex: Int = pad.positionIndex,
  val fileMetadata: PadFileMetadata = PadFileMetadata(
    padId = pad.id,
    uriString = pad.uriString,
    relativePath = if (pad.subfolderName.isNotBlank()) "${pad.subfolderName}/${pad.fileName}" else pad.fileName,
    fileName = pad.fileName,
    sizeBytes = pad.sizeBytes,
    sha1Hash = "",
    durationMs = pad.durationMs,
    extension = pad.extension
  ),
  val isFileMissing: Boolean = false
)

data class ProfileBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val profile: PadProfile,
  val banks: List<BankBackupEntry> = emptyList(),
  val pads: List<PadBackupEntry> = emptyList(),
  val ccMappings: List<MidiCcMapping> = emptyList(),
  val pcMappings: List<MidiProgramBankMapping> = emptyList()
)

data class LayoutSlotEntry(
  val padId: String,
  val uriString: String,
  val relativePath: String = "",
  val fileName: String,
  val sizeBytes: Long = 0L,
  val sha1Hash: String = "",
  val bankIndex: Int = 0,
  val slotIndex: Int = 0,
  val colorArgb: Long? = null,
  val displayName: String = ""
)

data class PadLayoutBackup(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val layoutId: String,
  val profileId: String,
  val name: String,
  val gridRows: Int = 4,
  val gridCols: Int = 4,
  val isDefault: Boolean = true,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
  val slots: List<LayoutSlotEntry> = emptyList()
)

data class EqPresetBackupEntry(
  val name: String,
  val label: String,
  val gainsDb: List<Float>,
  val isCustom: Boolean = false,
  val profileId: String? = null
)

data class FxPresetBackupEntry(
  val name: String,
  val label: String,
  val echoEnabled: Boolean,
  val echoDelayMs: Float,
  val echoFeedback: Float,
  val echoMix: Float,
  val reverbEnabled: Boolean,
  val reverbRoomSize: Float,
  val reverbDamping: Float,
  val reverbMix: Float,
  val isCustom: Boolean = false
)

data class FadeCurveBackupEntry(
  val name: String,
  val label: String,
  val shortLabel: String,
  val defaultFadeInMs: Long = 0L,
  val defaultFadeOutMs: Long = 0L,
  val isGlobalFadeCurve: Boolean = false,
  val isGlobalCrossfadeCurve: Boolean = false
)

data class PresetsBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val eqPresets: List<EqPresetBackupEntry> = EqPreset.entries.map { preset ->
    EqPresetBackupEntry(
      name = preset.name,
      label = preset.label,
      gainsDb = preset.gainsDb,
      isCustom = preset == EqPreset.CUSTOM
    )
  },
  val fxPresets: List<FxPresetBackupEntry> = FxPreset.entries.map { preset ->
    FxPresetBackupEntry(
      name = preset.name,
      label = preset.label,
      echoEnabled = preset.echoEnabled,
      echoDelayMs = preset.echoDelayMs,
      echoFeedback = preset.echoFeedback,
      echoMix = preset.echoMix,
      reverbEnabled = preset.reverbEnabled,
      reverbRoomSize = preset.reverbRoomSize,
      reverbDamping = preset.reverbDamping,
      reverbMix = preset.reverbMix,
      isCustom = preset == FxPreset.CUSTOM
    )
  },
  val fadeCurves: List<FadeCurveBackupEntry> = FadeCurve.entries.map { curve ->
    FadeCurveBackupEntry(
      name = curve.name,
      label = curve.label,
      shortLabel = curve.shortLabel
    )
  }
)

data class PadMidiNoteEntry(
  val profileId: String,
  val padId: String,
  val padName: String,
  val fileName: String,
  val slotIndex: Int,
  val bankIndex: Int,
  val midiNote: Int
)

data class SequencerMidiNoteEntry(
  val playlistId: String,
  val playlistName: String,
  val midiNote: Int
)

data class MidiMappingBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val focusModeMidiNote: Int? = null,
  val ccMappings: List<MidiCcMapping> = emptyList(),
  val pcMappings: List<MidiProgramBankMapping> = emptyList(),
  val padNoteMappings: List<PadMidiNoteEntry> = emptyList(),
  val sequencerNoteMappings: List<SequencerMidiNoteEntry> = emptyList()
)

data class SchedulerBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val tasks: List<ScheduledPadTask> = emptyList()
)

data class SequencerBackupBundle(
  val schemaVersion: Int = PersistentConfigSchema.CURRENT_SCHEMA_VERSION,
  val playlists: List<SequencerPlaylist> = emptyList()
)

data class PersistentConfigBundle(
  val meta: ConfigMetaJson = ConfigMetaJson(),
  val settings: SettingsBackupBundle = SettingsBackupBundle(),
  val profiles: List<ProfileBackupBundle> = emptyList(),
  val layouts: List<PadLayoutBackup> = emptyList(),
  val presets: PresetsBackupBundle = PresetsBackupBundle(),
  val midiMapping: MidiMappingBackupBundle = MidiMappingBackupBundle(),
  val scheduler: SchedulerBackupBundle = SchedulerBackupBundle(),
  val sequencer: SequencerBackupBundle = SequencerBackupBundle(),
  val extensionSections: Map<String, JSONObject> = emptyMap()
)

data class PersistentConfigRestoreResult(
  val bundle: PersistentConfigBundle,
  val migratedFromVersion: Int,
  val warnings: List<String> = emptyList(),
  val restoredSectionKeys: Set<String> = emptySet()
)
