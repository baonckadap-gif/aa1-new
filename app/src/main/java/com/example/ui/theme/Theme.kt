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
import androidx.compose.ui.unit.dp
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
data class SoundPadExtendedColors(
  val favoriteGold: Color,
  val ramEmerald: Color,
  val cueMonitor: Color,
  val panicRed: Color,
  val warningAmber: Color,
  val playheadWhite: Color,
  val padIdleWell: Color,
  val padDisabledContainer: Color,
  val padDisabledContent: Color,
  val isAmoled: Boolean,
  val isDark: Boolean
)

private val DefaultDarkExtendedColors = SoundPadExtendedColors(
  favoriteGold = FavoriteGold,
  ramEmerald = RamEmerald,
  cueMonitor = CueMonitorTeal,
  panicRed = PanicRed,
  warningAmber = WarningAmber,
  playheadWhite = Color.White,
  padIdleWell = PadWell,
  padDisabledContainer = Color(0xFF141822),
  padDisabledContent = TextMuted,
  isAmoled = false,
  isDark = true
)

private val DefaultAmoledExtendedColors = DefaultDarkExtendedColors.copy(
  padIdleWell = AmoledPadWell,
  padDisabledContainer = AmoledChassis,
  isAmoled = true
)

private val DefaultLightExtendedColors = SoundPadExtendedColors(
  favoriteGold = LightFavoriteGold,
  ramEmerald = Color(0xFF059669),
  cueMonitor = LightCueTeal,
  panicRed = LightCoralError,
  warningAmber = LightAmberSecondary,
  playheadWhite = Color(0xFF0F1522),
  padIdleWell = LightPadWell,
  padDisabledContainer = Color(0xFFE4E9F2),
  padDisabledContent = Color(0xFF6B768C),
  isAmoled = false,
  isDark = false
)

val LocalSoundPadSpacing = staticCompositionLocalOf { SoundPadSpacing() }
val LocalSoundPadColors = staticCompositionLocalOf { DefaultDarkExtendedColors }

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
    LocalSoundPadSpacing provides SoundPadSpacing()
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
