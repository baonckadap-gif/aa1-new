package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ThemeMode

@Immutable
data class SoundPadSpacing(
  val xxs: Dp = 2.dp,
  val xs: Dp = 4.dp,
  val sm: Dp = 8.dp,
  val md: Dp = 12.dp,
  val lg: Dp = 16.dp,
  val xl: Dp = 24.dp,
  val minTouchTarget: Dp = 48.dp
)

@Immutable
data class SoundPadPadTokens(
  val standardCornerDp: Dp = 14.dp,
  val compactCornerDp: Dp = 8.dp,
  val microCornerDp: Dp = 4.dp,
  val minCornerDp: Float = 2f,
  val maxCornerDp: Float = 28f,
  val compactCornerScale: Float = 0.58f,
  val dashLengthDp: Dp = 6.dp,
  val dashGapDp: Dp = 4.dp,
  val emptyWellBorderDp: Dp = 1.25.dp,
  val emptyWellBorderCompactDp: Dp = 1.dp,
  val emptyIconStandardDp: Dp = 24.dp,
  val emptyIconCompactDp: Dp = 16.dp,
  val emptyIconMicroDp: Dp = 10.dp,
  val metaMinAlpha: Float = 0.85f,
  val waveformIdleAlpha: Float = 0.68f,
  val waveformPlayedAlpha: Float = 1.0f,
  val waveformDimAlpha: Float = 0.24f,
  val waveformHeightDp: Dp = 12.dp,
  val playheadAlpha: Float = 0.92f,
  val playingBorderMinDp: Float = 2.6f,
  val playingBorderExtraDp: Float = 1.35f,
  val playingGlowHaloStrokeDp: Dp = 4.dp,
  val playingGlowHaloAlpha: Float = 0.28f,
  val maxStatusIcons: Int = 3,
  val statusDotDp: Dp = 7.dp,
  val statusIconDp: Dp = 11.dp,
  val statusSpinnerDp: Dp = 12.dp,
  val actionIconDp: Dp = 14.dp,
  val playingEqBoxDp: Dp = 16.dp,
  val playingEqIconDp: Dp = 9.dp,
  val bottomProgressStandardDp: Dp = 3.dp,
  val bottomProgressMicroDp: Dp = 2.dp,
  val padHorizontalPaddingDp: Dp = 8.dp,
  val padVerticalPaddingDp: Dp = 6.dp,
  val statusBadgeRadiusDp: Dp = 4.dp
)

@Immutable
data class SoundPadTopBarTokens(
  val horizontalPaddingDp: Dp = 10.dp,
  val verticalPaddingDp: Dp = 6.dp,
  val contentSpacingDp: Dp = 6.dp,
  val titleSpacingDp: Dp = 8.dp,
  val subtitleDimAlpha: Float = 0.72f,
  val disabledAlpha: Float = 0.45f,
  val iconGroupSpacingDp: Dp = 2.dp,
  val iconSizeDp: Dp = 22.dp,
  val spinnerSizeDp: Dp = 18.dp,
  val spinnerStrokeDp: Dp = 2.dp,
  val badgeDotDp: Dp = 8.dp,
  val playModeHeightDp: Dp = 40.dp,
  val playModeIconDp: Dp = 16.dp,
  val playModeHideSpacingDp: Dp = 6.dp,
  val playModeHideWidthDp: Dp = 44.dp,
  val playModeHideCornerDp: Dp = 20.dp,
  val playModeHideIconDp: Dp = 18.dp,
  val playModeBorderThicknessDp: Dp = 1.dp,
  val bankTabRowHorizontalPaddingDp: Dp = 8.dp,
  val bankTabHeightDp: Dp = 38.dp,
  val bankTabHorizontalPaddingDp: Dp = 12.dp,
  val bankTabIndicatorHeightDp: Dp = 2.5.dp,
  val bankTabDividerDp: Dp = 1.dp,
  val bankActiveDotDp: Dp = 6.dp,
  val bankAddButtonDp: Dp = 38.dp,
  val bankAddIconDp: Dp = 18.dp,
  val pagerDotActiveWidthDp: Dp = 16.dp,
  val pagerDotInactiveWidthDp: Dp = 6.dp,
  val pagerDotHeightDp: Dp = 6.dp,
  val pagerDotSpacingDp: Dp = 3.dp,
  val pagerDotsVerticalPaddingDp: Dp = 4.dp,
  val maxPagerDots: Int = 12
)

@Immutable
data class SoundPadBottomBarTokens(
  val tonalElevationDp: Dp = 4.dp,
  val borderThicknessDp: Dp = 1.dp,
  val horizontalPaddingDp: Dp = 8.dp,
  val verticalPaddingDp: Dp = 4.dp,
  val rowSpacingDp: Dp = 4.dp,
  val row1HeightDp: Dp = 40.dp,
  val row1ItemSpacingDp: Dp = 6.dp,
  val row2HeightDp: Dp = 52.dp,
  val row2ItemSpacingDp: Dp = 4.dp,
  val actionGroupSpacingDp: Dp = 3.dp,
  val maxContentHeightDp: Dp = 112.dp,
  val volumeIconDp: Dp = 18.dp,
  val sliderHeightDp: Dp = 32.dp,
  val sliderThumbDp: Dp = 16.dp,
  val sliderTrackHeightDp: Dp = 4.dp,
  val sliderTrackGapDp: Dp = 0.dp,
  val sliderThumbBorderDp: Dp = 1.5.dp,
  val percentMinWidthDp: Dp = 34.dp,
  val badgeCornerDp: Dp = 6.dp,
  val badgeHorizontalPadDp: Dp = 6.dp,
  val badgeVerticalPadDp: Dp = 3.dp,
  val chevronButtonDp: Dp = 36.dp,
  val chevronIconDp: Dp = 20.dp,
  val stopWidthFraction: Float = 0.30f,
  val actionsWidthFraction: Float = 0.70f,
  val stopCornerDp: Dp = 10.dp,
  val stopIconDp: Dp = 18.dp,
  val stopIdleAlpha: Float = 0.85f,
  val actionCornerDp: Dp = 10.dp,
  val actionIconDp: Dp = 17.dp,
  val actionActiveBgAlpha: Float = 0.20f,
  val actionDisabledAlpha: Float = 0.42f,
  val activeBorderDp: Dp = 1.5.dp,
  val inactiveBorderDp: Dp = 1.dp,
  val blinkMinAlpha: Float = 0.30f,
  val blinkMaxAlpha: Float = 1.0f,
  val blinkDurationMs: Int = 550,
  val actionLabelFontSp: TextUnit = 10.sp,
  val actionLabelLineHeightSp: TextUnit = 10.sp,
  val stopLabelFontSp: TextUnit = 11.sp
)

@Immutable
data class SoundPadExtendedColors(
  val favoriteGold: Color,
  val ramEmerald: Color,
  val cueMonitor: Color,
  val panicRed: Color,
  val onPanicRed: Color,
  val warningAmber: Color,
  val playModeActiveGreen: Color,
  val onPlayModeActiveGreen: Color,
  val playheadWhite: Color,
  val padIdleWell: Color,
  val padDefaultGray: Color,
  val padDefaultGrayBorder: Color,
  val padEmptyWellContainer: Color,
  val padEmptyWellBorder: Color,
  val padDisabledContainer: Color,
  val padDisabledContent: Color,
  val padProgressTrack: Color,
  val padSheenHighlight: Color,
  val padSheenShadow: Color,
  val padStatusChipBg: Color,
  val focusOverlayBg: Color,
  val focusEdgeHintBg: Color,
  val isAmoled: Boolean,
  val isDark: Boolean
)

private val DefaultDarkExtendedColors = SoundPadExtendedColors(
  favoriteGold = FavoriteGold,
  ramEmerald = RamEmerald,
  cueMonitor = CueMonitorTeal,
  panicRed = PanicRed,
  onPanicRed = OnPanicRed,
  warningAmber = WarningAmber,
  playModeActiveGreen = PlayModeActiveGreenDark,
  onPlayModeActiveGreen = OnPlayModeActiveGreenDark,
  playheadWhite = PadPlayheadDark,
  padIdleWell = PadWell,
  padDefaultGray = PadDefaultGrayDark,
  padDefaultGrayBorder = PadDefaultGrayBorderDark,
  padEmptyWellContainer = PadEmptyWellDark,
  padEmptyWellBorder = HairlineBorder,
  padDisabledContainer = PadDisabledContainerDark,
  padDisabledContent = TextMuted,
  padProgressTrack = PadProgressTrackDark,
  padSheenHighlight = PadSheenWhite,
  padSheenShadow = PadSheenBlack,
  padStatusChipBg = PadStatusChipDark,
  focusOverlayBg = FocusOverlayBgDark,
  focusEdgeHintBg = FocusEdgeHintBgDark,
  isAmoled = false,
  isDark = true
)

private val DefaultAmoledExtendedColors = DefaultDarkExtendedColors.copy(
  padIdleWell = AmoledPadWell,
  padEmptyWellContainer = PadEmptyWellAmoled,
  padEmptyWellBorder = AmoledBorder,
  padDisabledContainer = AmoledChassis,
  isAmoled = true
)

private val DefaultLightExtendedColors = SoundPadExtendedColors(
  favoriteGold = LightFavoriteGold,
  ramEmerald = RamEmerald,
  cueMonitor = LightCueTeal,
  panicRed = LightCoralError,
  onPanicRed = OnPanicRed,
  warningAmber = LightAmberSecondary,
  playModeActiveGreen = PlayModeActiveGreenLight,
  onPlayModeActiveGreen = OnPlayModeActiveGreenLight,
  playheadWhite = PadPlayheadLight,
  padIdleWell = LightPadWell,
  padDefaultGray = PadDefaultGrayLight,
  padDefaultGrayBorder = PadDefaultGrayBorderLight,
  padEmptyWellContainer = PadEmptyWellLight,
  padEmptyWellBorder = LightBorder,
  padDisabledContainer = PadDisabledContainerLight,
  padDisabledContent = PadDisabledContentLight,
  padProgressTrack = PadProgressTrackLight,
  padSheenHighlight = PadSheenWhite,
  padSheenShadow = PadSheenBlack,
  padStatusChipBg = PadStatusChipLight,
  focusOverlayBg = FocusOverlayBgLight,
  focusEdgeHintBg = FocusEdgeHintBgLight,
  isAmoled = false,
  isDark = false
)

val LocalSoundPadSpacing = staticCompositionLocalOf { SoundPadSpacing() }
val LocalSoundPadColors = staticCompositionLocalOf { DefaultDarkExtendedColors }
val LocalSoundPadTokens = staticCompositionLocalOf { SoundPadPadTokens() }
val LocalSoundPadTopBarTokens = staticCompositionLocalOf { SoundPadTopBarTokens() }
val LocalSoundPadBottomBarTokens = staticCompositionLocalOf { SoundPadBottomBarTokens() }

val SoundPadShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(18.dp),
  extraLarge = RoundedCornerShape(24.dp)
)

object SoundPadTheme {
  val colors: SoundPadExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSoundPadColors.current

  val spacing: SoundPadSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSoundPadSpacing.current

  val padTokens: SoundPadPadTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalSoundPadTokens.current

  val topBarTokens: SoundPadTopBarTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalSoundPadTopBarTokens.current

  val bottomBarTokens: SoundPadBottomBarTokens
    @Composable
    @ReadOnlyComposable
    get() = LocalSoundPadBottomBarTokens.current
}

@Composable
fun SoundPadProTheme(
  darkTheme: Boolean = true,
  themeMode: ThemeMode = if (darkTheme) ThemeMode.DARK else ThemeMode.LIGHT,
  useDynamicColor: Boolean = false,
  accentColorArgb: Long = 0xFF00E5FFL,
  content: @Composable () -> Unit
) {
  val systemDark = isSystemInDarkTheme()
  val isAmoled = themeMode == ThemeMode.AMOLED
  val isDark = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
  }

  val context = LocalContext.current
  val accent = Color(accentColorArgb.toInt())
  val secondaryAccent = shiftHue(accent, 42f)

  val colorScheme: ColorScheme = if (useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val dynamicBase = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    if (isAmoled) {
      dynamicBase.copy(
        background = AmoledChassis,
        surface = AmoledRackSurface,
        surfaceVariant = AmoledPadWell,
        surfaceContainer = AmoledRackElevated,
        surfaceContainerLow = AmoledChassis,
        surfaceContainerHigh = AmoledRackElevated,
        outline = AmoledBorder,
        outlineVariant = AmoledBorderSubtle
      )
    } else {
      dynamicBase
    }
  } else {
    buildCustomColorScheme(
      isDark = isDark,
      isAmoled = isAmoled,
      accent = accent,
      secondaryAccent = secondaryAccent
    )
  }

  val extendedColors = when {
    isAmoled -> DefaultAmoledExtendedColors
    isDark -> DefaultDarkExtendedColors
    else -> DefaultLightExtendedColors
  }

  CompositionLocalProvider(
    LocalSoundPadColors provides extendedColors,
    LocalSoundPadSpacing provides SoundPadSpacing(),
    LocalSoundPadTokens provides SoundPadPadTokens(),
    LocalSoundPadTopBarTokens provides SoundPadTopBarTokens(),
    LocalSoundPadBottomBarTokens provides SoundPadBottomBarTokens()
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = SoundPadTypography,
      shapes = SoundPadShapes,
      content = content
    )
  }
}

private fun buildCustomColorScheme(
  isDark: Boolean,
  isAmoled: Boolean,
  accent: Color,
  secondaryAccent: Color
): ColorScheme {
  if (!isDark) {
    val lightPrimary = lerp(accent, Color(0xFF071A2B), 0.32f)
    val lightSecondary = lerp(secondaryAccent, Color(0xFF211400), 0.28f)
    val onPrimaryLight = if (lightPrimary.luminance() > 0.50f) Color.Black else Color.White
    return lightColorScheme(
      primary = lightPrimary,
      onPrimary = onPrimaryLight,
      primaryContainer = lerp(Color.White, accent, 0.22f),
      onPrimaryContainer = lerp(lightPrimary, Color.Black, 0.58f),
      secondary = lightSecondary,
      onSecondary = Color.White,
      secondaryContainer = lerp(Color.White, secondaryAccent, 0.22f),
      onSecondaryContainer = lerp(lightSecondary, Color.Black, 0.58f),
      tertiary = Color(0xFF059669),
      onTertiary = Color.White,
      tertiaryContainer = Color(0xFFD1FAE5),
      onTertiaryContainer = Color(0xFF064E3B),
      error = LightCoralError,
      onError = Color.White,
      errorContainer = Color(0xFFFFD9DF),
      onErrorContainer = Color(0xFF540014),
      background = LightChassis,
      onBackground = LightTextPrimary,
      surface = LightRackSurface,
      onSurface = LightTextPrimary,
      surfaceVariant = LightPadWell,
      onSurfaceVariant = LightTextSecondary,
      surfaceContainer = LightRackElevated,
      surfaceContainerLow = LightRackSurface,
      surfaceContainerHigh = Color(0xFFDDE4F0),
      outline = LightBorder,
      outlineVariant = LightBorderSubtle
    )
  }

  val bg = if (isAmoled) AmoledChassis else ObsidianChassis
  val surf = if (isAmoled) AmoledRackSurface else RackSurface
  val surfElevated = if (isAmoled) AmoledRackElevated else RackElevated
  val surfVariant = if (isAmoled) AmoledPadWell else PadWell
  val outline = if (isAmoled) AmoledBorder else HairlineBorder
  val outlineVariant = if (isAmoled) AmoledBorderSubtle else HairlineBorderSubtle

  val onPrimaryColor = if (accent.luminance() > 0.42f) bg else Color.White
  val onSecondaryColor = if (secondaryAccent.luminance() > 0.42f) bg else Color.White

  return darkColorScheme(
    primary = accent,
    onPrimary = onPrimaryColor,
    primaryContainer = lerp(bg, accent, if (isAmoled) 0.20f else 0.24f),
    onPrimaryContainer = lerp(accent, Color.White, 0.40f),
    secondary = secondaryAccent,
    onSecondary = onSecondaryColor,
    secondaryContainer = lerp(bg, secondaryAccent, if (isAmoled) 0.20f else 0.24f),
    onSecondaryContainer = lerp(secondaryAccent, Color.White, 0.35f),
    tertiary = RamEmerald,
    onTertiary = bg,
    tertiaryContainer = if (isAmoled) Color(0xFF09241B) else Color(0xFF103326),
    onTertiaryContainer = Color(0xFF6EE7B7),
    error = SignalCoral,
    onError = Color.White,
    errorContainer = if (isAmoled) Color(0xFF2D0A14) else Color(0xFF3B121D),
    onErrorContainer = Color(0xFFFFB3C1),
    background = bg,
    onBackground = TextPrimary,
    surface = surf,
    onSurface = TextPrimary,
    surfaceVariant = surfVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = surfElevated,
    surfaceContainerLow = bg,
    surfaceContainerHigh = lerp(surfElevated, Color.White, 0.06f),
    outline = outline,
    outlineVariant = outlineVariant
  )
}

private fun shiftHue(color: Color, degrees: Float): Color {
  val hsv = FloatArray(3)
  android.graphics.Color.colorToHSV(color.toArgb(), hsv)
  hsv[0] = (hsv[0] + degrees) % 360f
  hsv[1] = hsv[1].coerceAtLeast(0.65f)
  hsv[2] = hsv[2].coerceAtLeast(0.85f)
  return Color(android.graphics.Color.HSVToColor(hsv))
}
