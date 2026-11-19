package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.StereoPanMath
import com.example.data.backup.BackupSyncStatus
import com.example.data.backup.PersistentBackupUiState
import com.example.domain.model.AppLanguage
import com.example.domain.model.DefaultPadColorMode
import com.example.domain.model.FadeCurve
import com.example.domain.model.GlobalSettings
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.HapticIntensity
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.ThemeMode
import com.example.ui.theme.SoundPadColorPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridSettingsSheet(
  settings: GlobalSettings,
  preloadedCount: Int,
  onGridConfigChange: (GridConfig) -> Unit,
  onThemeModeChange: (ThemeMode) -> Unit,
  onUseDynamicColorChange: (Boolean) -> Unit = {},
  onAppAccentColorChange: (Long) -> Unit,
  onDefaultPadShapeChange: (PadShapeStyle) -> Unit,
  onDefaultCornerGlowChange: (Long) -> Unit,
  onPadGridSpacingChange: (Int) -> Unit = {},
  onBottomBarVisibleChange: (Boolean) -> Unit,
  onLoadDemoTracks: () -> Unit,
  onExportDemoToMusicFolder: () -> Unit,
  onPreloadToggle: (Boolean) -> Unit,
  onAudioEffectsToggle: (Boolean) -> Unit = {},
  onVisualEffectsToggle: (Boolean) -> Unit = {},
  onKeepScreenOnToggle: (Boolean) -> Unit,
  onGlobalCrossfadeToggle: (Boolean) -> Unit,
  onCrossfadeDurationChange: (Long) -> Unit,
  onCrossfadeCurveChange: (FadeCurve) -> Unit,
  onGlobalFadeInChange: (Long) -> Unit,
  onGlobalFadeOutChange: (Long) -> Unit,
  onGlobalFadeCurveChange: (FadeCurve) -> Unit,
  onMasterPanChange: (Float) -> Unit = {},
  onMiniWaveformToggle: (Boolean) -> Unit = {},
  onHapticIntensityChange: (HapticIntensity) -> Unit = {},
  onSwipePadVolumeToggle: (Boolean) -> Unit = {},
  onDoubleTapToStopToggle: (Boolean) -> Unit = {},
  onAppLanguageChange: (AppLanguage) -> Unit = {},
  onShowFeatureTooltipsToggle: (Boolean) -> Unit = {},
  onDefaultPadColorModeChange: (DefaultPadColorMode) -> Unit = {},
  onDefaultPadColorArgbChange: (Long) -> Unit = {},
  onResetAllPadsToGray: () -> Unit = {},
  onEnterFocusMode: () -> Unit = {},
  onStartInFocusModeToggle: (Boolean) -> Unit = {},
  onFocusCornerPanicToggle: (Boolean) -> Unit = {},
  onReplayOnboarding: () -> Unit = {},
  onOpenMiuiGuide: () -> Unit = {},
  backupState: PersistentBackupUiState = PersistentBackupUiState(),
  onPickBackupFolderClick: () -> Unit = {},
  onSetAutoSyncEnabled: (Boolean) -> Unit = {},
  onSetIncludeWaveforms: (Boolean) -> Unit = {},
  onSyncBackupNow: () -> Unit = {},
  onInspectRestoreFolderClick: () -> Unit = {},
  onOpenSnapshotPickerClick: () -> Unit = {},
  onExportZipClick: () -> Unit = {},
  onImportZipClick: () -> Unit = {},
  onShareZipClick: () -> Unit = {},
  onRequestDeleteAllConfig: () -> Unit = {},
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val gridConfig = settings.gridConfig
  var selectedSection by remember { mutableIntStateOf(0) } // 0=Tampilan, 1=Audio, 2=Grid, 3=Interaksi, 4=Backup, 5=Tentang

  var appHue by remember(settings.appAccentColorArgb) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(Color(settings.appAccentColorArgb.toInt()).toArgb(), hsv)
    mutableFloatStateOf(hsv[0])
  }

  var glowHue by remember(settings.defaultCornerGlowArgb) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(Color(settings.defaultCornerGlowArgb.toInt()).toArgb(), hsv)
    mutableFloatStateOf(hsv[0])
  }

  var defaultPadHue by remember(settings.defaultPadColorArgb) {
    val hsv = FloatArray(3)
    val baseColor = if (settings.defaultPadColorArgb == SoundPadColorPalette.DEFAULT_PAD_GRAY_ARGB) {
      SoundPadColorPalette.DEFAULT_CUSTOM_PAD_COLOR_ARGB
    } else {
      settings.defaultPadColorArgb
    }
    android.graphics.Color.colorToHSV(Color(baseColor.toInt()).toArgb(), hsv)
    mutableFloatStateOf(hsv[0])
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Text(
        text = stringResource(R.string.settings_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface
      )

      PersistentSyncStatusStrip(
        backupState = backupState,
        onPickBackupFolderClick = onPickBackupFolderClick,
        onSyncBackupNow = onSyncBackupNow
      )

      // 5 Grouped Section Tabs: Tampilan | Audio | Grid | Interaksi | Tentang
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterChip(
          selected = selectedSection == 0,
          onClick = { selectedSection = 0 },
          leadingIcon = { Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_display), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_display")
        )
        FilterChip(
          selected = selectedSection == 1,
          onClick = { selectedSection = 1 },
          leadingIcon = { Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_audio), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_audio")
        )
        FilterChip(
          selected = selectedSection == 2,
          onClick = { selectedSection = 2 },
          leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_grid), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_grid")
        )
        FilterChip(
          selected = selectedSection == 3,
          onClick = { selectedSection = 3 },
          leadingIcon = { Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_interaction), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_interaction")
        )
        FilterChip(
          selected = selectedSection == 4,
          onClick = { selectedSection = 4 },
          leadingIcon = { Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_backup), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_backup")
        )
        FilterChip(
          selected = selectedSection == 5,
          onClick = { selectedSection = 5 },
          leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp)) },
          label = { Text(stringResource(R.string.settings_tab_about), style = MaterialTheme.typography.labelMedium) },
          modifier = Modifier.testTag("settings_tab_about")
        )
      }

      when (selectedSection) {
        0 -> {
          // ---- TAB 0: TAMPILAN ----
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.settings_language_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.settings_default_pad_color_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              DefaultPadColorMode.entries.forEach { mode ->
                FilterChip(
                  selected = settings.defaultPadColorMode == mode,
                  onClick = {
                    onDefaultPadColorModeChange(mode)
                    if (mode == DefaultPadColorMode.CUSTOM &&
                      settings.defaultPadColorArgb == SoundPadColorPalette.DEFAULT_PAD_GRAY_ARGB
                    ) {
                      onDefaultPadColorArgbChange(SoundPadColorPalette.DEFAULT_CUSTOM_PAD_COLOR_ARGB)
                    }
                  },
                  label = {
                    Text(
                      text = mode.label,
                      style = MaterialTheme.typography.labelSmall,
                      maxLines = 1
                    )
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("default_pad_color_${mode.name.lowercase()}")
                )
              }
            }

            if (settings.defaultPadColorMode == DefaultPadColorMode.CUSTOM) {
              @OptIn(ExperimentalLayoutApi::class)
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                SoundPadColorPalette.PAD_PRESETS_12.forEachIndexed { idx, preset ->
                  val isSelected = settings.defaultPadColorArgb == preset.argb
                  Box(
                    modifier = Modifier
                      .size(30.dp)
                      .clip(CircleShape)
                      .background(Color(preset.argb.toInt()))
                      .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                      )
                      .testTag("default_pad_custom_color_$idx")
                      .clickable { onDefaultPadColorArgbChange(preset.argb) },
                    contentAlignment = Alignment.Center
                  ) {
                    if (isSelected) {
                      Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = preset.name,
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp)
                      )
                    }
                  }
                }
              }
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text("Pad Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                  value = defaultPadHue,
                  onValueChange = { hue ->
                    defaultPadHue = hue
                    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, 0.95f))
                    onDefaultPadColorArgbChange(argb.toLong() and 0xFFFFFFFFL)
                  },
                  valueRange = 0f..360f,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            OutlinedButton(
              onClick = onResetAllPadsToGray,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("reset_all_pads_gray_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.settings_default_pad_color_reset_all))
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              AppLanguage.entries.forEach { lang ->
                FilterChip(
                  selected = settings.appLanguage == lang,
                  onClick = { onAppLanguageChange(lang) },
                  label = { Text(lang.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_language_${lang.name.lowercase()}")
                )
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "APPEARANCE THEME",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ThemeMode.entries.chunked(2).forEach { rowModes ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                rowModes.forEach { mode ->
                  FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { onThemeModeChange(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("theme_mode_${mode.name.lowercase()}")
                  )
                }
              }
            }
          }

          val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = stringResource(R.string.theme_dynamic_color_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = if (dynamicSupported) {
                  stringResource(R.string.theme_dynamic_color_subtitle)
                } else {
                  "Membutuhkan Android 12+ (perangkat ini memakai palet studio)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              enabled = dynamicSupported,
              checked = settings.useDynamicColor && dynamicSupported,
              onCheckedChange = onUseDynamicColorChange,
              modifier = Modifier.testTag("settings_dynamic_color_switch")
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "APP ACCENT COLOR",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              SoundPadColorPalette.APP_ACCENT_PRESETS_8.forEachIndexed { idx, colorVal ->
                val isSelected = settings.appAccentColorArgb == colorVal
                Box(
                  modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(colorVal.toInt()))
                    .border(
                      width = if (isSelected) 2.5.dp else 1.dp,
                      color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                      shape = CircleShape
                    )
                    .testTag("app_color_preset_$idx")
                    .clickable { onAppAccentColorChange(colorVal) },
                  contentAlignment = Alignment.Center
                ) {
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = "Selected app accent color",
                      tint = Color.Black,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Text("App Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Slider(
                value = appHue,
                onValueChange = { hue ->
                  appHue = hue
                  val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.88f, 0.96f))
                  onAppAccentColorChange(argb.toLong() and 0xFFFFFFFFL)
                },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f)
              )
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "PAD BUTTON STYLE",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              PadShapeStyle.entries.forEach { style ->
                val isSelected = settings.defaultPadShape == style
                Surface(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .testTag("global_pad_shape_${style.name.lowercase()}")
                    .clickable { onDefaultPadShapeChange(style) },
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                  border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                  )
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                  ) {
                    PadShapePreviewGlyph(
                      style = style,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(22.dp)
                    )
                    Text(
                      text = style.label,
                      style = MaterialTheme.typography.labelSmall,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                  }
                }
              }
            }
          }

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "DEFAULT CORNER GLOW COLOR",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              SoundPadColorPalette.APP_ACCENT_PRESETS_8.forEachIndexed { idx, colorVal ->
                val isSelected = settings.defaultCornerGlowArgb == colorVal
                Box(
                  modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(colorVal.toInt()))
                    .border(
                      width = if (isSelected) 2.5.dp else 1.dp,
                      color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                      shape = CircleShape
                    )
                    .testTag("global_glow_preset_$idx")
                    .clickable { onDefaultCornerGlowChange(colorVal) },
                  contentAlignment = Alignment.Center
                ) {
                  if (isSelected) {
                    Icon(
                      imageVector = Icons.Filled.Check,
                      contentDescription = "Selected corner glow color",
                      tint = Color.Black,
                      modifier = Modifier.size(15.dp)
                    )
                  }
                }
              }
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Text("Glow Hue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Slider(
                value = glowHue,
                onValueChange = { hue ->
                  glowHue = hue
                  val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.92f, 1.0f))
                  onDefaultCornerGlowChange(argb.toLong() and 0xFFFFFFFFL)
                },
                valueRange = 0f..360f,
                modifier = Modifier.weight(1f)
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = stringResource(R.string.settings_visual_effects_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(R.string.settings_visual_effects_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.visualEffectsEnabled,
              onCheckedChange = onVisualEffectsToggle,
              modifier = Modifier.testTag("settings_visual_effects_switch")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Show Bottom Console Bar",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Hide lower bar for maximum pad matrix space",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.isBottomBarVisible,
              onCheckedChange = onBottomBarVisibleChange,
              modifier = Modifier.testTag("settings_bottom_bar_switch")
            )
          }
        }

        1 -> {
          // ---- TAB 1: AUDIO ----
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = stringResource(R.string.settings_audio_effects_title),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = stringResource(R.string.settings_audio_effects_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = settings.audioEffectsEnabled,
                onCheckedChange = onAudioEffectsToggle,
                modifier = Modifier.testTag("settings_audio_effects_switch")
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Text(
            text = "GLOBAL CROSSFADE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Crossfade Between Pads",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Smoothly blends outgoing & incoming pads in Single/Continuous mode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.globalCrossfadeEnabled,
              onCheckedChange = onGlobalCrossfadeToggle,
              modifier = Modifier.testTag("global_crossfade_switch")
            )
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "CROSSFADE DURATION (${String.format(Locale.US, "%.1fs", settings.crossfadeDurationMs / 1000f)})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              IconButton(
                onClick = { onCrossfadeDurationChange(2000L) },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
              }
            }
            Slider(
              value = settings.crossfadeDurationMs.toFloat(),
              onValueChange = { ms ->
                val snapped = ((ms / 200f).roundToInt() * 200L).coerceIn(200L, 10000L)
                onCrossfadeDurationChange(snapped)
              },
              valueRange = 200f..10000f,
              modifier = Modifier.testTag("global_crossfade_duration_slider")
            )
          }

          FadeCurveCanvas(curve = settings.crossfadeCurve, showCrossfadePair = true)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            FadeCurve.entries.forEach { curve ->
              FilterChip(
                selected = settings.crossfadeCurve == curve,
                onClick = { onCrossfadeCurveChange(curve) },
                label = { Text(curve.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("global_crossfade_curve_${curve.name.lowercase()}")
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Text(
            text = "GLOBAL FADE IN / OUT DEFAULTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
          )

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("DEFAULT FADE IN (${settings.globalFadeInMs} ms)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              IconButton(onClick = { onGlobalFadeInChange(0L) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
              }
            }
            Slider(
              value = settings.globalFadeInMs.toFloat(),
              onValueChange = { ms ->
                onGlobalFadeInChange(((ms / 100f).roundToInt() * 100L).coerceIn(0L, 5000L))
              },
              valueRange = 0f..5000f
            )
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("DEFAULT FADE OUT (${settings.globalFadeOutMs} ms)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
              IconButton(onClick = { onGlobalFadeOutChange(0L) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
              }
            }
            Slider(
              value = settings.globalFadeOutMs.toFloat(),
              onValueChange = { ms ->
                onGlobalFadeOutChange(((ms / 100f).roundToInt() * 100L).coerceIn(0L, 5000L))
              },
              valueRange = 0f..5000f
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            FadeCurve.entries.forEach { curve ->
              FilterChip(
                selected = settings.globalFadeCurve == curve,
                onClick = { onGlobalFadeCurveChange(curve) },
                label = { Text(curve.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Column {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                  detectTapGestures(onDoubleTap = { onMasterPanChange(0f) })
                },
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "MASTER PAN (${StereoPanMath.formatPanLabel(settings.masterPan)})",
                  style = MaterialTheme.typography.labelMedium,
                  color = if (abs(settings.masterPan) >= 0.04f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "Double-tap label or icon to reset to Center",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              IconButton(
                onClick = { onMasterPanChange(0f) },
                modifier = Modifier
                  .size(36.dp)
                  .testTag("reset_master_pan_button")
              ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset master pan to center", modifier = Modifier.size(18.dp))
              }
            }
            Slider(
              value = settings.masterPan,
              onValueChange = { raw ->
                val snapped = if (abs(raw) < 0.04f) 0f else (raw * 20f).roundToInt() / 20f
                onMasterPanChange(snapped.coerceIn(-1f, 1f))
              },
              valueRange = -1f..1f,
              modifier = Modifier.testTag("master_pan_slider")
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
              Text("L 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("Center", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("R 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Preload Short Audio to RAM",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Caches files ≤ 4 MB in RAM for ultra-low tap latency ($preloadedCount cached)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.preloadShortAudio,
              onCheckedChange = onPreloadToggle
            )
          }
        }

        2 -> {
          // ---- TAB 2: GRID ----
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "GRID MATRIX PRESET (${gridConfig.padsPerBank} PADS / BANK)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              GridPreset.entries.forEach { preset ->
                val isSelected = gridConfig.preset == preset
                Surface(
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .testTag("grid_preset_${preset.name.lowercase()}")
                    .clickable {
                      val nextConfig = if (preset == GridPreset.CUSTOM) {
                        gridConfig.copy(preset = GridPreset.CUSTOM)
                      } else {
                        GridConfig(preset = preset, rows = preset.rows, cols = preset.cols)
                      }
                      onGridConfigChange(nextConfig)
                    },
                  shape = RoundedCornerShape(10.dp),
                  color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                  border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                  )
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    Text(
                      text = preset.label,
                      style = MaterialTheme.typography.labelLarge,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                      text = if (preset == GridPreset.CUSTOM) "Custom" else "${preset.rows * preset.cols} pads",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }

          if (gridConfig.preset == GridPreset.CUSTOM) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "ROWS: ${gridConfig.rows}   ×   COLS: ${gridConfig.cols}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              Slider(
                value = gridConfig.rows.toFloat(),
                onValueChange = { value ->
                  onGridConfigChange(
                    gridConfig.copy(preset = GridPreset.CUSTOM, rows = value.roundToInt().coerceIn(2, 16))
                  )
                },
                valueRange = 2f..16f,
                steps = 13
              )
              Slider(
                value = gridConfig.cols.toFloat(),
                onValueChange = { value ->
                  onGridConfigChange(
                    gridConfig.copy(preset = GridPreset.CUSTOM, cols = value.roundToInt().coerceIn(2, 16))
                  )
                },
                valueRange = 2f..16f,
                steps = 13
              )
            }
          }

          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${stringResource(R.string.pad_grid_spacing_label)} (${settings.padGridSpacingDp} dp)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              IconButton(
                onClick = { onPadGridSpacingChange(8) },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
              }
            }
            Slider(
              value = settings.padGridSpacingDp.toFloat(),
              onValueChange = { dpVal ->
                onPadGridSpacingChange(dpVal.roundToInt().coerceIn(2, 16))
              },
              valueRange = 2f..16f,
              steps = 13,
              modifier = Modifier.testTag("settings_pad_spacing_slider")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Mini Waveform Preview di Pad",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Tampilkan bentuk gelombang + playhead di pad 4×4 (otomatis nonaktif di 10×10 / 15×15 demi performa)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.miniWaveformEnabled,
              onCheckedChange = onMiniWaveformToggle,
              modifier = Modifier.testTag("settings_mini_waveform_switch")
            )
          }
        }

        3 -> {
          // ---- TAB 3: INTERAKSI ----
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = stringResource(R.string.settings_haptic_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              HapticIntensity.entries.forEach { level ->
                FilterChip(
                  selected = settings.hapticIntensity == level,
                  onClick = { onHapticIntensityChange(level) },
                  label = { Text(level.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_haptic_${level.name.lowercase()}")
                )
              }
            }
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = stringResource(R.string.settings_swipe_volume_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(R.string.settings_swipe_volume_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.swipePadVolumeEnabled,
              onCheckedChange = onSwipePadVolumeToggle,
              modifier = Modifier.testTag("settings_swipe_volume_switch")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = stringResource(R.string.settings_double_tap_stop_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(R.string.settings_double_tap_stop_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.doubleTapToStopEnabled,
              onCheckedChange = onDoubleTapToStopToggle,
              modifier = Modifier.testTag("settings_double_tap_stop_switch")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = stringResource(R.string.settings_tooltips_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = stringResource(R.string.settings_tooltips_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.showFeatureTooltips,
              onCheckedChange = onShowFeatureTooltipsToggle,
              modifier = Modifier.testTag("settings_tooltips_switch")
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = "Keep Screen Awake",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Prevents screen timeout during live soundboard performance",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = settings.keepScreenOn,
              onCheckedChange = onKeepScreenOnToggle
            )
          }

          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = stringResource(R.string.settings_focus_section_title),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )

            OutlinedButton(
              onClick = {
                onDismiss()
                onEnterFocusMode()
              },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("settings_enter_focus_mode_button")
            ) {
              Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(stringResource(R.string.settings_focus_enter_now))
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = stringResource(R.string.settings_focus_start_title),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = stringResource(R.string.settings_focus_start_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = settings.startInFocusMode,
                onCheckedChange = onStartInFocusModeToggle,
                modifier = Modifier.testTag("settings_start_in_focus_switch")
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = stringResource(R.string.settings_focus_panic_title),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = stringResource(R.string.settings_focus_panic_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = settings.focusCornerPanicEnabled,
                onCheckedChange = onFocusCornerPanicToggle,
                modifier = Modifier.testTag("settings_focus_corner_panic_switch")
              )
            }
          }
        }

        4 -> {
          // ---- TAB 4: BACKUP & RESTORE ----
          BackupSettingsTabContent(
            backupState = backupState,
            currentMusicFolderName = settings.folderName,
            onPickBackupFolderClick = onPickBackupFolderClick,
            onSetAutoSyncEnabled = onSetAutoSyncEnabled,
            onSetIncludeWaveforms = onSetIncludeWaveforms,
            onSyncNowClick = onSyncBackupNow,
            onInspectRestoreFolderClick = onInspectRestoreFolderClick,
            onOpenSnapshotPickerClick = onOpenSnapshotPickerClick,
            onExportZipClick = onExportZipClick,
            onImportZipClick = onImportZipClick,
            onShareZipClick = onShareZipClick,
            onRequestDeleteAllConfig = {
              onDismiss()
              onRequestDeleteAllConfig()
            }
          )
        }

        else -> {
          // ---- TAB 5: TENTANG & DEMO ----
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                  text = "Sample Test Songs (6 Tracks)",
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Load demo loops onto board or save WAV copies to Music/SoundPad Demo",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(
                  onClick = {
                    onLoadDemoTracks()
                    onDismiss()
                  },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_load_demo_button")
                ) {
                  Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = stringResource(R.string.cd_load_demo_tracks)
                  )
                }
                OutlinedIconButton(
                  onClick = onExportDemoToMusicFolder,
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_export_demo_music_button")
                ) {
                  Icon(
                    Icons.Filled.Download,
                    contentDescription = "Save sample tracks to phone Music folder",
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }

          OutlinedButton(
            onClick = {
              onDismiss()
              onReplayOnboarding()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_replay_onboarding_button")
          ) {
            Icon(Icons.Filled.School, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_replay_onboarding))
          }

          OutlinedButton(
            onClick = {
              onDismiss()
              onOpenMiuiGuide()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("settings_open_miui_button")
          ) {
            Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.settings_open_miui_guide))
          }

          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Text(
                text = "SoundPad Pro Studio Engine",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                text = "Media3 Low-Latency Polyphonic Mixer · 5-Band Biquad EQ · Reverb/Echo DSP · Stereo Pan · Headphone Cue Monitor · USB/BLE MIDI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun PersistentSyncStatusStrip(
  backupState: PersistentBackupUiState,
  onPickBackupFolderClick: () -> Unit,
  onSyncBackupNow: () -> Unit
) {
  val isError = backupState.status == BackupSyncStatus.ERROR_INACCESSIBLE
  val isUnconfigured = backupState.status == BackupSyncStatus.UNCONFIGURED
  val containerColor = when {
    isError -> MaterialTheme.colorScheme.errorContainer
    isUnconfigured -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
  }
  val contentColor = when {
    isError -> MaterialTheme.colorScheme.onErrorContainer
    isUnconfigured -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
  }
  val iconTint = when {
    isError -> MaterialTheme.colorScheme.error
    isUnconfigured -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.primary
  }
  val statusIcon = when (backupState.status) {
    BackupSyncStatus.ERROR_INACCESSIBLE -> Icons.Filled.CloudOff
    BackupSyncStatus.SAVING, BackupSyncStatus.PENDING -> Icons.Filled.CloudSync
    BackupSyncStatus.SAVED, BackupSyncStatus.IDLE -> Icons.Filled.CloudDone
    BackupSyncStatus.UNCONFIGURED -> Icons.Filled.FolderOpen
  }
  val formattedTime = remember(backupState.lastSyncAtMs) {
    if (backupState.lastSyncAtMs <= 0L) null
    else SimpleDateFormat("HH:mm · dd MMM", Locale.getDefault()).format(Date(backupState.lastSyncAtMs))
  }
  val primaryLabel = when (backupState.status) {
    BackupSyncStatus.SAVED -> if (formattedTime != null) {
      "${stringResource(R.string.backup_status_saved)} · $formattedTime"
    } else {
      stringResource(R.string.backup_status_saved)
    }
    BackupSyncStatus.SAVING -> stringResource(R.string.backup_status_saving)
    BackupSyncStatus.PENDING -> stringResource(R.string.backup_status_pending)
    BackupSyncStatus.ERROR_INACCESSIBLE -> stringResource(R.string.backup_status_inaccessible)
    BackupSyncStatus.IDLE -> if (formattedTime != null) {
      "${stringResource(R.string.backup_status_saved)} · $formattedTime"
    } else {
      stringResource(R.string.backup_status_pending)
    }
    BackupSyncStatus.UNCONFIGURED -> stringResource(R.string.backup_status_unconfigured)
  }

  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("settings_backup_status_strip"),
    shape = RoundedCornerShape(12.dp),
    color = containerColor,
    border = BorderStroke(
      width = 1.dp,
      color = if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
      else MaterialTheme.colorScheme.outlineVariant
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = statusIcon,
        contentDescription = primaryLabel,
        tint = iconTint,
        modifier = Modifier.size(18.dp)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = primaryLabel,
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = contentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (!backupState.folderName.isNullOrBlank()) {
          Text(
            text = backupState.folderName,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
      if (isError || isUnconfigured) {
        TextButton(
          onClick = onPickBackupFolderClick,
          modifier = Modifier.testTag("settings_pick_backup_folder_btn")
        ) {
          Text(
            text = if (isError) stringResource(R.string.backup_action_repick)
            else stringResource(R.string.backup_pick_folder_short),
            style = MaterialTheme.typography.labelMedium
          )
        }
      } else {
        TextButton(
          onClick = onSyncBackupNow,
          modifier = Modifier.testTag("settings_sync_backup_now_btn")
        ) {
          Text(
            text = stringResource(R.string.backup_sync_now_short),
            style = MaterialTheme.typography.labelMedium
          )
        }
      }
    }
  }
}
