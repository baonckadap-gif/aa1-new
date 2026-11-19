package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class PlayMode(val label: String, val shortLabel: String) {
  SINGLE("Single Play", "SINGLE"),
  MULTI("Multi Play", "MULTI"),
  CONTINUOUS("Continuous", "CONT")
}

enum class PadTriggerMode(val label: String, val description: String) {
  TOGGLE("Toggle", "Tap to start, tap again to stop"),
  ONE_SHOT("One-Shot", "Always retriggers from start on tap"),
  HOLD("Hold", "Plays while pressed, stops on release"),
  LOOP("Loop", "Loops continuously until tapped again")
}

enum class FadeCurve(val label: String, val shortLabel: String) {
  LINEAR("Linear", "LIN"),
  EXPONENTIAL("Exponential", "EXP"),
  LOGARITHMIC("Logarithmic", "LOG"),
  S_CURVE("S-Curve", "S-CRV"),
  EQUAL_POWER("Equal-Power", "EQ-PWR")
}

enum class ThemeMode(val label: String) {
  SYSTEM("System"),
  LIGHT("Light"),
  DARK("Dark"),
  AMOLED("AMOLED")
}

enum class HapticIntensity(val label: String) {
  OFF("Off"),
  SOFT("Lembut"),
  STRONG("Kuat")
}

enum class AppLanguage(val label: String, val tag: String) {
  SYSTEM("Sistem", ""),
  INDONESIAN("Indonesia", "in"),
  ENGLISH("English", "en")
}

enum class PadShapeStyle(val label: String) {
  SQUARE("Kotak"),
  TRIANGLE("Segitiga"),
  STAR("Bintang"),
  PRISM("Prisma")
}

enum class PadSurfaceFinish(val label: String) {
  CLASSIC("Classic"),
  NEON_GRADIENT("Neon"),
  GLASS("Glass"),
  MATTE("Matte")
}

enum class PadBadgeIcon(val label: String) {
  NONE("None"),
  MUSIC("Music"),
  BOLT("Drop"),
  FIRE("Hype"),
  STAR("Lead"),
  MIC("Vocal"),
  DIAMOND("VIP"),
  WAVE("Synth")
}

enum class DefaultPadColorMode(val label: String) {
  GRAY("Abu-abu"),
  AUTO("Otomatis berwarna"),
  CUSTOM("Pilih sendiri")
}

enum class DragDropConflictMode(val label: String) {
  SWAP("Tukar Posisi"),
  SHIFT("Geser Slot")
}

enum class GridPreset(val rows: Int, val cols: Int, val label: String) {
  GRID_4X4(4, 4, "4×4"),
  GRID_10X10(10, 10, "10×10"),
  GRID_15X15(15, 15, "15×15"),
  CUSTOM(6, 6, "Custom")
}

@Immutable
data class GridConfig(
  val preset: GridPreset = GridPreset.GRID_4X4,
  val rows: Int = 4,
  val cols: Int = 4
) {
  val padsPerBank: Int
    get() = (rows.coerceIn(1, 16)) * (cols.coerceIn(1, 16))

  val displayLabel: String
    get() = if (preset == GridPreset.CUSTOM) "${rows}×${cols}" else preset.label
}

enum class PadErrorType(val label: String, val defaultSuggestion: String) {
  CORRUPT_HEADER(
    "Header Rusak",
    "Header file audio tidak valid atau file terpotong. Coba konversi ulang file ke WAV atau MP3 standar."
  ),
  DRM_PROTECTED(
    "DRM / Terenkripsi",
    "File dilindungi DRM atau terenkripsi sehingga tidak bisa diputar. Gunakan file audio bebas DRM."
  ),
  UNSUPPORTED_FORMAT(
    "Format Tidak Didukung",
    "Codec audio tidak didukung oleh perangkat ini. Konversi file ke MP3, WAV, FLAC, M4A, atau OGG."
  ),
  MISSING_PERMISSION(
    "Izin SAF Hilang",
    "Izin akses folder/file hilang atau dicabut sistem. Pilih ulang folder musik Anda melalui ikon folder."
  ),
  PLAYBACK_ERROR(
    "Error Pemutaran",
    "Pemutar gagal memproses file ini. Periksa integritas file atau lakukan Rescan folder."
  ),
  APP_CRASH(
    "Crash Aplikasi",
    "Aplikasi sempat berhenti mendadak pada sesi sebelumnya. Ekspor log ke .txt untuk analisis."
  )
}

data class AppErrorLog(
  val id: String,
  val timestampMs: Long,
  val fileName: String,
  val uriString: String? = null,
  val errorType: PadErrorType,
  val message: String,
  val suggestion: String,
  val stackTrace: String = "",
  val padId: String? = null
)

enum class EqPreset(val label: String, val gainsDb: List<Float>) {
  FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
  BASS_BOOST("Bass Boost", listOf(7f, 4f, 0f, -1f, -2f)),
  VOCAL("Vocal", listOf(-3f, 0f, 5f, 4f, 2f)),
  TREBLE("Treble", listOf(-2f, -1f, 0f, 5f, 8f)),
  CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f));

  companion object {
    val FREQUENCIES_HZ = listOf(60, 230, 910, 3600, 14000)
    val FREQUENCY_LABELS = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k")

    fun sanitizeBands(bands: List<Float>): List<Float> {
      return List(5) { idx ->
        bands.getOrElse(idx) { 0f }.coerceIn(-12f, 12f)
      }
    }
  }
}

enum class FxPreset(
  val label: String,
  val echoEnabled: Boolean,
  val echoDelayMs: Float,
  val echoFeedback: Float,
  val echoMix: Float,
  val reverbEnabled: Boolean,
  val reverbRoomSize: Float,
  val reverbDamping: Float,
  val reverbMix: Float
) {
  OFF("Off", false, 220f, 0.30f, 0.25f, false, 0.35f, 0.45f, 0.25f),
  SMALL_ROOM("Small Room", false, 180f, 0.20f, 0.15f, true, 0.35f, 0.65f, 0.28f),
  HALL("Hall", false, 240f, 0.25f, 0.18f, true, 0.85f, 0.35f, 0.48f),
  SHORT_ECHO("Echo Pendek", true, 160f, 0.32f, 0.36f, false, 0.35f, 0.45f, 0.20f),
  LONG_ECHO("Echo Panjang", true, 440f, 0.54f, 0.46f, true, 0.45f, 0.50f, 0.22f),
  CUSTOM("Custom", false, 220f, 0.30f, 0.25f, false, 0.35f, 0.45f, 0.25f)
}

@Immutable
data class PadSettings(
  val volume: Float = 1.0f,
  val speed: Float = 1.0f,
  val pitch: Float = 1.0f,
  val pan: Float = 0.0f, // -1.0f = Full Left, 0.0f = Center, +1.0f = Full Right
  val isLooping: Boolean = false,
  val triggerMode: PadTriggerMode = PadTriggerMode.TOGGLE,
  val chokeGroup: Int = 0, // 0 = None, 1..4 = Group A..D
  val fadeInMs: Long = 0L,
  val fadeOutMs: Long = 0L,
  val fadeCurve: FadeCurve = FadeCurve.EQUAL_POWER,
  val crossfadeEnabled: Boolean = true,
  val trimStartMs: Long = 0L,
  val trimEndMs: Long = 0L, // 0L = full length / end of file
  val isDuckingSource: Boolean = false,
  val normalizedGain: Float = 1.0f,
  val eqEnabled: Boolean = false,
  val eqPreset: EqPreset = EqPreset.FLAT,
  val eqBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
  val isReversed: Boolean = false,
  val fxPreset: FxPreset = FxPreset.OFF,
  val echoEnabled: Boolean = false,
  val echoDelayMs: Float = 220f,
  val echoFeedback: Float = 0.30f,
  val echoMix: Float = 0.25f,
  val reverbEnabled: Boolean = false,
  val reverbRoomSize: Float = 0.35f,
  val reverbDamping: Float = 0.45f,
  val reverbMix: Float = 0.25f
)

@Immutable
data class SoundPad(
  val id: String,
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
  val glowIntensity: Float = 0f,
  val shapeStyle: PadShapeStyle? = null, // null inherits GlobalSettings.defaultPadShape
  val borderColorArgb: Long? = null,
  val borderWidthDp: Float = 1.25f,
  val cornerRadiusDp: Float = 14f,
  val textColorArgb: Long? = null,
  val surfaceFinish: PadSurfaceFinish = PadSurfaceFinish.CLASSIC,
  val badgeIcon: PadBadgeIcon = PadBadgeIcon.NONE,
  val showSlotNumber: Boolean = true,
  val showDurationLabel: Boolean = true,
  val isClone: Boolean = false,
  val midiNote: Int? = null,
  val isPreloadedInRam: Boolean = false,
  val isFavorite: Boolean = false,
  val categoryTag: String = "",
  val miniWaveform: List<Float> = emptyList(),
  val errorType: PadErrorType? = null,
  val errorMessage: String? = null,
  val settings: PadSettings = PadSettings()
)

@Immutable
data class PadVisualConfig(
  val displayName: String,
  val colorArgb: Long? = null,
  val glowColorArgb: Long? = null,
  val glowIntensity: Float = 1.0f,
  val shapeStyle: PadShapeStyle? = null,
  val borderColorArgb: Long? = null,
  val borderWidthDp: Float = 1.25f,
  val cornerRadiusDp: Float = 14f,
  val textColorArgb: Long? = null,
  val surfaceFinish: PadSurfaceFinish = PadSurfaceFinish.CLASSIC,
  val badgeIcon: PadBadgeIcon = PadBadgeIcon.NONE,
  val showSlotNumber: Boolean = true,
  val showDurationLabel: Boolean = true
)

fun SoundPad.toVisualConfig(): PadVisualConfig = PadVisualConfig(
  displayName = displayName,
  colorArgb = colorArgb,
  glowColorArgb = glowColorArgb,
  glowIntensity = glowIntensity,
  shapeStyle = shapeStyle,
  borderColorArgb = borderColorArgb,
  borderWidthDp = borderWidthDp,
  cornerRadiusDp = cornerRadiusDp,
  textColorArgb = textColorArgb,
  surfaceFinish = surfaceFinish,
  badgeIcon = badgeIcon,
  showSlotNumber = showSlotNumber,
  showDurationLabel = showDurationLabel
)

@Immutable
data class PadProfile(
  val id: String,
  val name: String,
  val folderUri: String?,
  val gridConfig: GridConfig,
  val padCount: Int = 0,
  val bankNames: Map<Int, String> = emptyMap(),
  val bankOrder: List<Int> = emptyList(),
  val bankFolderUris: Map<Int, String> = emptyMap(),
  val masterEqEnabled: Boolean = false,
  val masterEqPreset: EqPreset = EqPreset.FLAT,
  val masterEqBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
  val themeMode: ThemeMode? = null,
  val useDynamicColor: Boolean? = null,
  val appAccentColorArgb: Long? = null,
  val padGridSpacingDp: Int? = null,
  val defaultPadShape: PadShapeStyle? = null,
  val defaultCornerGlowArgb: Long? = null,
  val defaultPadColorMode: DefaultPadColorMode? = null,
  val defaultPadColorArgb: Long? = null,
  val createdAtMs: Long,
  val updatedAtMs: Long,
  val isActive: Boolean,
  val previewColorArgbs: List<Long?> = emptyList()
)

@Immutable
data class PadPlaybackState(
  val padId: String,
  val isPlaying: Boolean,
  val isFadingOut: Boolean = false,
  val positionMs: Long,
  val durationMs: Long,
  val progress: Float
)

data class FolderScanResult(
  val folderName: String,
  val folderUri: String,
  val pads: List<SoundPad>,
  val newlyDetectedCount: Int = 0,
  val hasSubfolderBanks: Boolean = false,
  val bankNames: Map<Int, String> = emptyMap(),
  val bankFolderUris: Map<Int, String> = emptyMap(),
  val detectedErrors: List<AppErrorLog> = emptyList()
)

data class WaveformData(
  val amplitudes: List<Float>,
  val durationMs: Long
)

@Immutable
data class GlobalSettings(
  val activeProfileId: String = "default_profile",
  val folderUri: String? = null,
  val folderName: String? = null,
  val gridConfig: GridConfig = GridConfig(),
  val playMode: PlayMode = PlayMode.SINGLE,
  val masterVolume: Float = 1.0f,
  val masterPan: Float = 0.0f,
  val masterEqEnabled: Boolean = false,
  val masterEqPreset: EqPreset = EqPreset.FLAT,
  val masterEqBandsDb: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
  val miniWaveformEnabled: Boolean = true,
  val preloadShortAudio: Boolean = true,
  val audioEffectsEnabled: Boolean = true,
  val visualEffectsEnabled: Boolean = true,
  val keepScreenOn: Boolean = true,
  val themeMode: ThemeMode = ThemeMode.DARK,
  val useDynamicColor: Boolean = false,
  val appAccentColorArgb: Long = 0xFF00E5FFL,
  val padGridSpacingDp: Int = 8,
  val defaultPadShape: PadShapeStyle = PadShapeStyle.SQUARE,
  val defaultCornerGlowArgb: Long = 0xFF00E5FFL,
  val isBottomBarVisible: Boolean = true,
  val isFullscreen: Boolean = false,
  val globalFadeInMs: Long = 0L,
  val globalFadeOutMs: Long = 0L,
  val globalFadeCurve: FadeCurve = FadeCurve.EQUAL_POWER,
  val globalCrossfadeEnabled: Boolean = false,
  val crossfadeDurationMs: Long = 2000L,
  val crossfadeCurve: FadeCurve = FadeCurve.EQUAL_POWER,
  val masterLimiterEnabled: Boolean = true,
  val duckingEnabled: Boolean = false,
  val duckingAttenuation: Float = 0.30f,
  val autoNormalizeEnabled: Boolean = false,
  val isLocked: Boolean = false,
  val isGlobalCueEnabled: Boolean = false,
  val hapticIntensity: HapticIntensity = HapticIntensity.SOFT,
  val swipePadVolumeEnabled: Boolean = false,
  val doubleTapToStopEnabled: Boolean = true,
  val hasCompletedOnboarding: Boolean = false,
  val showFeatureTooltips: Boolean = true,
  val appLanguage: AppLanguage = AppLanguage.SYSTEM,
  val defaultPadColorMode: DefaultPadColorMode = DefaultPadColorMode.GRAY,
  val defaultPadColorArgb: Long = 0xFF4A4F57L,
  val isFocusMode: Boolean = false,
  val startInFocusMode: Boolean = false,
  val focusCornerPanicEnabled: Boolean = true,
  val focusModeMidiNote: Int? = null,
  val preferredMainOutputId: Int? = null,
  val preferredMainOutputName: String? = null,
  val preferredCueOutputId: Int? = null,
  val preferredCueOutputName: String? = null,
  val audioBufferSizeMs: Int = 50,
  val persistentBackupFolderUri: String? = null,
  val persistentBackupFolderName: String? = null,
  val persistentAutoSyncEnabled: Boolean = true,
  val persistentLastSyncAtMs: Long = 0L,
  val persistentIncludeWaveforms: Boolean = false,
  val dragDropConflictMode: DragDropConflictMode = DragDropConflictMode.SWAP
)

enum class MidiCcTarget(val label: String) {
  MASTER_VOLUME("Master Volume"),
  PAD_SPEED("Live Speed Fader"),
  PAD_PITCH("Live Pitch Fader"),
  CROSSFADE_DURATION("Crossfade Time"),
  STOP_ALL("Panic Stop All"),
  TOGGLE_FOCUS_MODE("Toggle Fokus Mode")
}

data class MidiCcMapping(
  val id: String,
  val profileId: String,
  val target: MidiCcTarget,
  val ccNumber: Int,
  val channel: Int = -1 // -1 = Omni
)

sealed interface MidiLearnTarget {
  data class PadNote(val padId: String, val padName: String) : MidiLearnTarget
  data class CcControl(val target: MidiCcTarget) : MidiLearnTarget
  data class ProgramChangeBank(val bankIndex: Int, val bankLabel: String) : MidiLearnTarget
  data class SequencerPlaylistNote(val playlistId: String, val playlistName: String) : MidiLearnTarget
  data object FocusModeNote : MidiLearnTarget
}

data class MidiProgramBankMapping(
  val id: String,
  val profileId: String,
  val programNumber: Int,
  val bankIndex: Int,
  val channel: Int = -1 // -1 = Omni
)

data class MidiDeviceSummary(
  val id: Int,
  val name: String,
  val typeLabel: String,
  val isConnected: Boolean
)

data class AudioOutputDeviceUi(
  val id: Int,
  val name: String,
  val typeLabel: String,
  val isHeadphoneLike: Boolean
)

enum class MasterRecordSampleRate(val hz: Int, val label: String) {
  RATE_44100(44100, "44.1 kHz"),
  RATE_48000(48000, "48 kHz")
}

data class MasterRecordState(
  val isRecording: Boolean = false,
  val elapsedMs: Long = 0L,
  val level: Float = 0f,
  val sampleRateHz: Int = 44100,
  val outputFolderUri: String? = null,
  val outputFolderName: String? = null,
  val autoAddAsPad: Boolean = true,
  val lastSavedUriString: String? = null,
  val lastSavedName: String? = null
)

enum class ScheduleTargetType(val label: String) {
  PAD("Pad"),
  BANK("Bank"),
  SEQUENCER("Sequencer")
}

enum class ScheduleRepeatMode(val label: String) {
  ONCE("Sekali"),
  DAILY("Harian"),
  CUSTOM_DAYS("Hari Tertentu")
}

data class ScheduledPadTask(
  val id: String,
  val title: String,
  val targetType: ScheduleTargetType = ScheduleTargetType.PAD,
  val targetId: String,
  val hour: Int = 9,
  val minute: Int = 0,
  val repeatMode: ScheduleRepeatMode = ScheduleRepeatMode.ONCE,
  val daysOfWeekMask: Int = 0, // Bit 0 = Sen .. Bit 6 = Min
  val enabled: Boolean = true,
  val volume: Float = 1.0f,
  val fadeInMs: Long = 0L,
  val autoStopMinutes: Int = 0,
  val loopPlayback: Boolean = false,
  val nextTriggerAtMs: Long = 0L,
  val createdAtMs: Long = System.currentTimeMillis()
)

enum class SequencerPlayMode(val label: String) {
  SEQUENTIAL("Sequential"),
  SHUFFLE("Shuffle"),
  REPEAT_ALL("Repeat All"),
  REPEAT_ONE("Repeat One")
}

data class SequencerPlaylist(
  val id: String,
  val name: String,
  val padIds: List<String> = emptyList(),
  val mode: SequencerPlayMode = SequencerPlayMode.SEQUENTIAL,
  val gapSeconds: Float = 0f,
  val crossfadeEnabled: Boolean = false,
  val crossfadeDurationMs: Long = 1500L,
  val triggerPadId: String? = null,
  val triggerMidiNote: Int? = null,
  val updatedAtMs: Long = System.currentTimeMillis()
)

data class SequencerPlaybackState(
  val activePlaylistId: String? = null,
  val activePlaylistName: String? = null,
  val currentIndex: Int = 0,
  val currentPadId: String? = null,
  val currentPadName: String? = null,
  val queueSize: Int = 0,
  val isPlaying: Boolean = false,
  val isPaused: Boolean = false,
  val isInGap: Boolean = false,
  val remainingGapSec: Float = 0f
)

