package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian Studio Console Palette (Dark - Default)
val ObsidianChassis = Color(0xFF0B0D12)
val RackSurface = Color(0xFF141822)
val RackElevated = Color(0xFF1C2333)
val PadWell = Color(0xFF20283B)
val PadWellActive = Color(0xFF122C38)
val HairlineBorder = Color(0xFF33405C)
val HairlineBorderSubtle = Color(0xFF232C40)

// AMOLED Pure Black OLED Palette
val AmoledChassis = Color(0xFF000000)
val AmoledRackSurface = Color(0xFF05070B)
val AmoledRackElevated = Color(0xFF0C1018)
val AmoledPadWell = Color(0xFF101521)
val AmoledBorder = Color(0xFF2C364D)
val AmoledBorderSubtle = Color(0xFF1A2233)

// Accents & Live Stage Semantics
val ElectricCyan = Color(0xFF00E5FF)
val CyanMuted = Color(0xFF008BA3)
val StudioAmber = Color(0xFFFFB300)
val SignalCoral = Color(0xFFFF4D6D)
val RamEmerald = Color(0xFF10B981)
val FavoriteGold = Color(0xFFFFD54F)
val CueMonitorTeal = Color(0xFF14B8A6)
val PanicRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)

// High-Contrast Dark Text (WCAG AA on #0B0D12 / #141822 / #000000)
val TextPrimary = Color(0xFFF5F8FF)
val TextSecondary = Color(0xFFB3BED6)
val TextMuted = Color(0xFF7D8AA6)

// Light Studio Console Palette (WCAG AA High-Contrast)
val LightChassis = Color(0xFFF2F5FA)
val LightRackSurface = Color(0xFFFFFFFF)
val LightRackElevated = Color(0xFFE7ECF5)
val LightPadWell = Color(0xFFEDF1F8)
val LightTextPrimary = Color(0xFF0F1522)
val LightTextSecondary = Color(0xFF3F4A61)
val LightBorder = Color(0xFFB5C0D6)
val LightBorderSubtle = Color(0xFFD6DEEC)
val LightCyanPrimary = Color(0xFF006D80)
val LightAmberSecondary = Color(0xFFB86B00)
val LightCoralError = Color(0xFFC81E3E)
val LightFavoriteGold = Color(0xFFB45309)
val LightCueTeal = Color(0xFF0F766E)

// Pad Surface & Track Tokens (used by Theme.kt to prevent hardcoded colors in Composables)
val PadEmptyWellDark = Color(0x73141822)
val PadEmptyWellAmoled = Color(0x7305070B)
val PadEmptyWellLight = Color(0x99FFFFFF)
val PadProgressTrackDark = Color(0x59000000)
val PadProgressTrackLight = Color(0x2E0F1522)
val PadSheenWhite = Color(0xFFFFFFFF)
val PadSheenBlack = Color(0xFF000000)
val PadPlayheadDark = Color(0xFFFFFFFF)
val PadPlayheadLight = Color(0xFF0F1522)
val PadDisabledContainerDark = Color(0xFF141822)
val PadDisabledContainerLight = Color(0xFFE4E9F2)
val PadDisabledContentLight = Color(0xFF6B768C)
val PadStatusChipDark = Color(0x33FFFFFF)
val PadStatusChipLight = Color(0x1F0F1522)

// Default Neutral Gray Pad Tokens (no color glow)
val PadDefaultGrayDark = Color(0xFF4A4F57)
val PadDefaultGrayBorderDark = Color(0xFF636A75)
val PadDefaultGrayLight = Color(0xFFD5DCE6)
val PadDefaultGrayBorderLight = Color(0xFF99A5B8)

// Focus Mode Overlay Tokens
val FocusOverlayBgDark = Color(0xD910141E)
val FocusOverlayBgLight = Color(0xEBFFFFFF)
val FocusEdgeHintBgDark = Color(0x99141822)
val FocusEdgeHintBgLight = Color(0xCCE7ECF5)

// Play Mode Segmented Bar Tokens
val PlayModeActiveGreenDark = RamEmerald
val PlayModeActiveGreenLight = Color(0xFF059669)
val OnPlayModeActiveGreenDark = ObsidianChassis
val OnPlayModeActiveGreenLight = Color(0xFFFFFFFF)

// Bottom Console Bar Tokens
val OnPanicRed = Color(0xFFFFFFFF)

data class PadColorPreset(
  val name: String,
  val argb: Long
)

object SoundPadColorPalette {
  const val DEFAULT_PAD_GRAY_ARGB = 0xFF4A4F57L
  const val DEFAULT_CUSTOM_PAD_COLOR_ARGB = 0xFF00E5FFL

  val PAD_PRESETS_12: List<PadColorPreset> = listOf(
    PadColorPreset("Cyan", 0xFF00E5FFL),
    PadColorPreset("Amber", 0xFFFFB300L),
    PadColorPreset("Coral", 0xFFFF4D6DL),
    PadColorPreset("Emerald", 0xFF10B981L),
    PadColorPreset("Violet", 0xFF8B5CF6L),
    PadColorPreset("Cobalt", 0xFF3B82F6L),
    PadColorPreset("Neon Pink", 0xFFEC4899L),
    PadColorPreset("Lime", 0xFF84CC16L),
    PadColorPreset("Sunset", 0xFFF97316L),
    PadColorPreset("Teal", 0xFF14B8A6L),
    PadColorPreset("Indigo", 0xFF6366F1L),
    PadColorPreset("Pearl", 0xFF94A3B8L)
  )

  val APP_ACCENT_PRESETS_8: List<Long> = listOf(
    0xFF00E5FFL, // Cyan
    0xFFFFB300L, // Amber
    0xFFFF4D6DL, // Coral
    0xFF10B981L, // Emerald
    0xFF8B5CF6L, // Violet
    0xFF3B82F6L, // Electric Blue
    0xFFEC4899L, // Neon Pink
    0xFF84CC16L  // Lime
  )

  val GLOW_PRESETS_9: List<Long?> = listOf(
    null,
    0xFF00E5FFL,
    0xFFFFD54FL,
    0xFFFF4D6DL,
    0xFF34D399L,
    0xFFC084FCL,
    0xFFF472B6L,
    0xFF60A5FAL,
    0xFFFFFFFFL
  )

  val BORDER_PRESETS_9: List<Long?> = listOf(
    null,
    0xFF00E5FFL,
    0xFFFFD54FL,
    0xFFFF4D6DL,
    0xFF10B981L,
    0xFFC084FCL,
    0xFFF472B6L,
    0xFFFFFFFFL,
    0xFF94A3B8L
  )

  val TEXT_PRESETS_8: List<Long?> = listOf(
    null,
    0xFFFFFFFFL,
    0xFF0B1120L,
    0xFF00E5FFL,
    0xFFFFD54FL,
    0xFFFF758FL,
    0xFF34D399L,
    0xFFC084FCL
  )
}
