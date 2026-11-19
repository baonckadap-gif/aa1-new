package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFamily = FontFamily(
  Font(R.font.space_grotesk, FontWeight.Normal),
  Font(R.font.space_grotesk, FontWeight.SemiBold),
  Font(R.font.space_grotesk, FontWeight.Bold)
)

val InterFamily = FontFamily(
  Font(R.font.inter, FontWeight.Normal),
  Font(R.font.inter, FontWeight.Medium),
  Font(R.font.inter, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
  Font(R.font.jetbrains_mono, FontWeight.Normal),
  Font(R.font.jetbrains_mono, FontWeight.Medium),
  Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val SoundPadTypography = Typography(
  displaySmall = TextStyle(
    fontFamily = SpaceGroteskFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 26.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.5).sp
  ),
  headlineSmall = TextStyle(
    fontFamily = SpaceGroteskFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.25).sp
  ),
  titleMedium = TextStyle(
    fontFamily = SpaceGroteskFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
  ),
  titleSmall = TextStyle(
    fontFamily = SpaceGroteskFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.1.sp
  ),
  bodyLarge = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp
  ),
  bodyMedium = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 18.sp
  ),
  bodySmall = TextStyle(
    fontFamily = InterFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    lineHeight = 15.sp
  ),
  labelLarge = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.5.sp
  ),
  labelMedium = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.4.sp
  ),
  labelSmall = TextStyle(
    fontFamily = JetBrainsMonoFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.3.sp
  )
)

/**
 * Dynamically scales pad title text from large 4x4 grids down to ultra-dense 15x15 boards.
 */
fun padTitleStyleForGrid(rows: Int, cols: Int, isPlaying: Boolean): TextStyle {
  val maxDim = maxOf(rows, cols)
  val weight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold
  return when {
    maxDim <= 4 -> TextStyle(
      fontFamily = SpaceGroteskFamily,
      fontWeight = weight,
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.sp
    )
    maxDim <= 6 -> TextStyle(
      fontFamily = SpaceGroteskFamily,
      fontWeight = weight,
      fontSize = 11.5.sp,
      lineHeight = 14.sp,
      letterSpacing = 0.sp
    )
    maxDim <= 10 -> TextStyle(
      fontFamily = InterFamily,
      fontWeight = weight,
      fontSize = 9.5.sp,
      lineHeight = 11.5.sp,
      letterSpacing = 0.1.sp
    )
    else -> TextStyle(
      fontFamily = InterFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 8.sp,
      lineHeight = 10.sp,
      letterSpacing = 0.sp
    )
  }
}

/**
 * Minimum font size before AutoSizePadTitle falls back to ellipsis on 2 lines.
 */
fun padTitleMinFontSizeSp(rows: Int, cols: Int): Float {
  val maxDim = maxOf(rows, cols)
  return when {
    maxDim <= 4 -> 9.5f
    maxDim <= 6 -> 8.5f
    maxDim <= 10 -> 7.5f
    else -> 6.5f
  }
}

/**
 * Scales slot numbers & time readouts to remain sharp on dense matrices.
 * Uses at least 11.sp on standard 4x4 / 5x5 grids for legibility.
 */
fun padMetaStyleForGrid(rows: Int, cols: Int): TextStyle {
  val maxDim = maxOf(rows, cols)
  return when {
    maxDim <= 5 -> TextStyle(
      fontFamily = JetBrainsMonoFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 11.sp,
      lineHeight = 13.sp,
      letterSpacing = 0.25.sp
    )
    maxDim <= 10 -> TextStyle(
      fontFamily = JetBrainsMonoFamily,
      fontWeight = FontWeight.Medium,
      fontSize = 9.sp,
      lineHeight = 11.sp,
      letterSpacing = 0.15.sp
    )
    else -> TextStyle(
      fontFamily = JetBrainsMonoFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 7.5.sp,
      lineHeight = 9.sp,
      letterSpacing = 0.sp
    )
  }
}
