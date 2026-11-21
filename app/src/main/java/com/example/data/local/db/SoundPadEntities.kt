package com.example.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.FadeCurve
import com.example.domain.model.GridPreset
import com.example.domain.model.PadTriggerMode

@Entity(tableName = "pad_profiles")
data class PadProfileEntity(
  @PrimaryKey val id: String,
  val name: String,
  val folderUri: String?,
  val gridPreset: String = GridPreset.GRID_4X4.name,
  val gridRows: Int,
  val gridCols: Int,
  val bankNamesJson: String = "{}",
  val bankOrderJson: String = "[]",
  val bankFolderUrisJson: String = "{}",
  val masterEqEnabled: Boolean = false,
  val masterEqPreset: String = "FLAT",
  val masterEqBandsJson: String = "[0.0,0.0,0.0,0.0,0.0]",
  val focusModeMidiNote: Int? = null,
  val createdAtMs: Long,
  val updatedAtMs: Long,
  val isActive: Boolean = false
)

@Entity(
  tableName = "sound_pads",
  foreignKeys = [
    ForeignKey(
      entity = PadProfileEntity::class,
      parentColumns = ["id"],
      childColumns = ["profileId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("profileId")]
)
data class SoundPadEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val uriString: String,
  val fileName: String,
  val displayName: String,
  val extension: String,
  val durationMs: Long,
  val sizeBytes: Long,
  val positionIndex: Int,
  val bankIndex: Int = 0,
  val subfolderName: String = "",
  val colorArgb: Long? = null,
  val glowColorArgb: Long? = null,
  val glowIntensity: Float = 1.0f,
  val shapeStyle: String? = null,
  val borderColorArgb: Long? = null,
  val borderWidthDp: Float = 1.25f,
  val cornerRadiusDp: Float = 14f,
  val textColorArgb: Long? = null,
  val surfaceFinish: String = "CLASSIC",
  val badgeIcon: String = "NONE",
  val showSlotNumber: Boolean = true,
  val showDurationLabel: Boolean = true,
  val isClone: Boolean = false,
  val midiNote: Int? = null,
  val isFavorite: Boolean = false,
  val categoryTag: String = "",
  val miniWaveformJson: String = "",
  val errorType: String? = null,
  val errorMessage: String? = null
)

@Entity(
  tableName = "pad_settings",
  foreignKeys = [
    ForeignKey(
      entity = SoundPadEntity::class,
      parentColumns = ["id"],
      childColumns = ["padId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("padId")]
)
data class PadSettingEntity(
  @PrimaryKey val padId: String,
  val volume: Float = 1.0f,
  val speed: Float = 1.0f,
  val pitch: Float = 1.0f,
  val pan: Float = 0.0f,
  val isLooping: Boolean = false,
  val triggerMode: String = PadTriggerMode.TOGGLE.name,
  val chokeGroup: Int = 0,
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  val fadeCurve: String = FadeCurve.EQUAL_POWER.name,
  val crossfadeEnabled: Boolean = true,
  val trimStartMs: Long = 0L,
  val trimEndMs: Long = 0L,
  val isDuckingSource: Boolean = false,
  val normalizedGain: Float = 1.0f,
  val eqEnabled: Boolean = false,
  val eqPreset: String = "FLAT",
  val eqBandsJson: String = "[0.0,0.0,0.0,0.0,0.0]",
  val isReversed: Boolean = false,
  val fxPreset: String = "OFF",
  val echoEnabled: Boolean = false,
  val echoDelayMs: Float = 220f,
  val echoFeedback: Float = 0.30f,
  val echoMix: Float = 0.25f,
  val reverbEnabled: Boolean = false,
  val reverbRoomSize: Float = 0.35f,
  val reverbDamping: Float = 0.45f,
  val reverbMix: Float = 0.25f,
  val retriggerMode: String? = null,
  val maxVoices: Int = 0
)

@Entity(
  tableName = "midi_cc_mappings",
  foreignKeys = [
    ForeignKey(
      entity = PadProfileEntity::class,
      parentColumns = ["id"],
      childColumns = ["profileId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("profileId")]
)
data class MidiCcMappingEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val target: String,
  val ccNumber: Int,
  val channel: Int = -1
)

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
  @PrimaryKey val id: String,
  val timestampMs: Long,
  val fileName: String,
  val uriString: String? = null,
  val errorType: String,
  val message: String,
  val suggestion: String,
  val stackTrace: String = "",
  val padId: String? = null
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
  @PrimaryKey val id: String,
  val title: String,
  val targetType: String,
  val targetId: String,
  val hour: Int,
  val minute: Int,
  val repeatMode: String,
  val daysOfWeekMask: Int,
  val enabled: Boolean,
  val volume: Float,
  val fadeInMs: Long,
  val autoStopMinutes: Int,
  val loopPlayback: Boolean,
  val requestCode: Int = 0,
  val nextTriggerAtMs: Long,
  val createdAtMs: Long
)

@Entity(tableName = "scheduler_id_counter")
data class SchedulerIdCounterEntity(
  @PrimaryKey val id: Int = 1,
  val nextRequestCode: Int = 100
)

@Entity(tableName = "sequencer_playlists")
data class SequencerPlaylistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val padIdsJson: String = "[]",
  val mode: String = "SEQUENTIAL",
  val gapSeconds: Float = 0f,
  val crossfadeEnabled: Boolean = false,
  val crossfadeDurationMs: Long = 1500L,
  val triggerPadId: String? = null,
  val triggerMidiNote: Int? = null,
  val updatedAtMs: Long
)

@Entity(
  tableName = "midi_pc_bank_mappings",
  foreignKeys = [
    ForeignKey(
      entity = PadProfileEntity::class,
      parentColumns = ["id"],
      childColumns = ["profileId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("profileId")]
)
data class MidiPcBankMappingEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val programNumber: Int,
  val bankIndex: Int,
  val channel: Int = -1
)

@Entity(
  tableName = "pad_layouts",
  foreignKeys = [
    ForeignKey(
      entity = PadProfileEntity::class,
      parentColumns = ["id"],
      childColumns = ["profileId"],
      onDelete = ForeignKey.CASCADE
    )
  ],
  indices = [Index("profileId")]
)
data class PadLayoutEntity(
  @PrimaryKey val id: String,
  val profileId: String,
  val name: String,
  val gridRows: Int = 4,
  val gridCols: Int = 4,
  val isDefault: Boolean = false,
  val slotsJson: String = "[]",
  val createdAtMs: Long,
  val updatedAtMs: Long
)
