package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.example.domain.model.EqPreset
import com.example.domain.model.FadeCurve
import com.example.domain.model.FxPreset
import com.example.domain.model.PadBadgeIcon
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PadSettings
import com.example.domain.model.PadShapeStyle
import com.example.domain.model.PadSurfaceFinish
import com.example.domain.model.PadTriggerMode
import com.example.domain.model.PadVisualConfig
import com.example.domain.model.SoundPad
import com.example.domain.model.toVisualConfig
import com.example.ui.theme.SoundPadColorPalette
import com.example.ui.theme.SoundPadTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val PRESET_PAD_COLORS = listOf<Long?>(null) + SoundPadColorPalette.PAD_PRESETS_12.map { it.argb }
private val PRESET_GLOW_COLORS = SoundPadColorPalette.GLOW_PRESETS_9
private val PRESET_BORDER_COLORS = SoundPadColorPalette.BORDER_PRESETS_9
private val PRESET_TEXT_COLORS = SoundPadColorPalette.TEXT_PRESETS_8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadSettingsSheet(
  pad: SoundPad,
  allPads: List<SoundPad>,
  defaultPadShape: PadShapeStyle,
  defaultCornerGlowColor: Color,
  isCuePlaying: Boolean = false,
  isPreparingReverse: Boolean = false,
  onSelectPad: (String) -> Unit,
  onSaveAppearance: (PadVisualConfig) -> Unit,
  onApplyStyleToAll: (PadVisualConfig) -> Unit,
  onSettingsChange: (PadSettings) -> Unit,
  onToggleFavorite: () -> Unit = {},
  onCategoryTagChange: (String) -> Unit = {},
  onToggleCuePreview: () -> Unit = {},
  onOpenAudioEditor: () -> Unit,
  onDuplicatePad: () -> Unit,
  onDuplicateToBank: () -> Unit = {},
  onDeletePad: () -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Design, 1 = Audio, 2 = EQ & FX
  var previewPlaying by remember { mutableStateOf(false) }

  var visualConfig by remember(pad.id) { mutableStateOf(pad.toVisualConfig()) }
  var settings by remember(pad.id) { mutableStateOf(pad.settings) }
  var categoryText by remember(pad.id) { mutableStateOf(pad.categoryTag) }

  var fillHue by remember(pad.id) {
    val hsv = FloatArray(3)
    val initialColor = pad.colorArgb?.let { Color(it.toInt()) }
    if (initialColor != null) {
      android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
    } else {
      hsv[0] = 190f
      hsv[2] = 0.95f
    }
    mutableFloatStateOf(hsv[0])
  }

  var fillBrightness by remember(pad.id) {
    val hsv = FloatArray(3)
    val initialColor = pad.colorArgb?.let { Color(it.toInt()) }
    if (initialColor != null) {
      android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
    } else {
      hsv[2] = 0.95f
    }
    mutableFloatStateOf(hsv[2].coerceIn(0.25f, 1f))
  }

  var glowHue by remember(pad.id) {
    val hsv = FloatArray(3)
    val initialGlow = pad.glowColorArgb?.let { Color(it.toInt()) }
    if (initialGlow != null) {
      android.graphics.Color.colorToHSV(initialGlow.toArgb(), hsv)
    } else {
      hsv[0] = 190f
    }
    mutableFloatStateOf(hsv[0])
  }

  var borderHue by remember(pad.id) {
    val hsv = FloatArray(3)
    val initialBorder = pad.borderColorArgb?.let { Color(it.toInt()) }
    if (initialBorder != null) {
      android.graphics.Color.colorToHSV(initialBorder.toArgb(), hsv)
    } else {
      hsv[0] = 190f
    }
    mutableFloatStateOf(hsv[0])
  }

  fun commitVisual(next: PadVisualConfig) {
    visualConfig = next
    onSaveAppearance(next)
  }

  val currentIdx = allPads.indexOfFirst { it.id == pad.id }.coerceAtLeast(0)
  val hasPrev = allPads.size > 1 && currentIdx > 0
  val hasNext = allPads.size > 1 && currentIdx < allPads.lastIndex

  val previewPad = pad.copy(
    displayName = visualConfig.displayName.ifBlank { pad.displayName },
    colorArgb = visualConfig.colorArgb,
    glowColorArgb = visualConfig.glowColorArgb,
    glowIntensity = visualConfig.glowIntensity,
    shapeStyle = visualConfig.shapeStyle,
    borderColorArgb = visualConfig.borderColorArgb,
    borderWidthDp = visualConfig.borderWidthDp,
    cornerRadiusDp = visualConfig.cornerRadiusDp,
    textColorArgb = visualConfig.textColorArgb,
    surfaceFinish = visualConfig.surfaceFinish,
    badgeIcon = visualConfig.badgeIcon,
    showSlotNumber = visualConfig.showSlotNumber,
    showDurationLabel = visualConfig.showDurationLabel,
    settings = settings
  )

  ModalBottomSheet(
    onDismissRequest = {
      onSaveAppearance(visualConfig)
      onDismiss()
    },
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Song Switcher Header (navigate between all tracks directly)
      if (allPads.size > 1) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(
            enabled = hasPrev,
            onClick = {
              onSaveAppearance(visualConfig)
              onSelectPad(allPads[currentIdx - 1].id)
            },
            modifier = Modifier
              .size(40.dp)
              .testTag("customizer_prev_track_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Previous song"
            )
          }

          LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            itemsIndexed(allPads, key = { _, item -> item.id }) { idx, item ->
              val isCurrent = item.id == pad.id
              val itemTint = item.colorArgb?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.primary
              Surface(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .testTag("customizer_track_pill_$idx")
                  .clickable {
                    onSaveAppearance(visualConfig)
                    onSelectPad(item.id)
                  },
                shape = RoundedCornerShape(10.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                  width = if (isCurrent) 1.5.dp else 1.dp,
                  color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                  Box(
                    modifier = Modifier
                      .size(8.dp)
                      .clip(CircleShape)
                      .background(itemTint)
                  )
                  Text(
                    text = "${idx + 1}. ${item.displayName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                      fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }
            }
          }

          IconButton(
            enabled = hasNext,
            onClick = {
              onSaveAppearance(visualConfig)
              onSelectPad(allPads[currentIdx + 1].id)
            },
            modifier = Modifier
              .size(40.dp)
              .testTag("customizer_next_track_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = "Next song"
            )
          }
        }
      }

      // Track Title & Icon-only Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Pad #${pad.positionIndex + 1}${if (pad.isClone) " · Clone" else ""}",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = pad.fileName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Row(
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.testTag("toggle_favorite_button")
          ) {
            Icon(
              imageVector = if (pad.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
              contentDescription = stringResource(R.string.cd_favorite_pad),
              tint = if (pad.isFavorite) SoundPadTheme.colors.favoriteGold else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          OutlinedIconButton(
            onClick = onToggleCuePreview,
            modifier = Modifier.testTag("cue_preview_button")
          ) {
            Icon(
              imageVector = Icons.Filled.Headphones,
              contentDescription = stringResource(R.string.cd_cue_preview),
              tint = if (isCuePlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
            )
          }
          OutlinedIconButton(
            onClick = {
              onSaveAppearance(visualConfig)
              onOpenAudioEditor()
            },
            modifier = Modifier.testTag("open_audio_editor_button")
          ) {
            Icon(
              Icons.Filled.ContentCut,
              contentDescription = stringResource(R.string.cd_audio_editor),
              tint = MaterialTheme.colorScheme.primary
            )
          }
          OutlinedIconButton(
            onClick = {
              onSaveAppearance(visualConfig)
              onDuplicatePad()
            },
            modifier = Modifier.testTag("duplicate_pad_button")
          ) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate pad with independent settings")
          }
          OutlinedIconButton(
            onClick = {
              onSaveAppearance(visualConfig)
              onDuplicateToBank()
            },
            modifier = Modifier.testTag("duplicate_pad_to_bank_button")
          ) {
            Icon(
              Icons.Filled.DriveFileMove,
              contentDescription = "Duplicate pad to another bank",
              tint = MaterialTheme.colorScheme.tertiary
            )
          }
          OutlinedIconButton(
            onClick = {
              onSaveAppearance(visualConfig)
              onApplyStyleToAll(visualConfig)
            },
            modifier = Modifier.testTag("apply_style_all_pads_button")
          ) {
            Icon(
              Icons.Filled.SelectAll,
              contentDescription = "Apply this button style to all songs",
              tint = MaterialTheme.colorScheme.secondary
            )
          }
          IconButton(
            onClick = onDeletePad,
            modifier = Modifier.testTag("delete_pad_button")
          ) {
            Icon(
              Icons.Filled.Delete,
              contentDescription = "Remove pad from board",
              tint = MaterialTheme.colorScheme.error
            )
          }
          FilledIconButton(
            onClick = {
              onSaveAppearance(visualConfig)
              onDismiss()
            },
            modifier = Modifier.testTag("save_pad_settings_button")
          ) {
            Icon(Icons.Filled.Check, contentDescription = "Save pad settings")
          }
        }
      }

      // Live Button Preview for This Specific Song
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          PadCell(
            pad = previewPad,
            slotNumber = pad.positionIndex + 1,
            playbackState = if (previewPlaying) {
              PadPlaybackState(
                padId = pad.id,
                isPlaying = true,
                isFadingOut = false,
                positionMs = (pad.durationMs * 0.45f).toLong(),
                durationMs = pad.durationMs.coerceAtLeast(1000L),
                progress = 0.45f
              )
            } else null,
            density = PadDensity.STANDARD,
            isEditMode = false,
            isDragging = false,
            isDropTarget = false,
            globalCrossfadeEnabled = false,
            defaultPadShape = defaultPadShape,
            defaultCornerGlowColor = defaultCornerGlowColor,
            onClick = { previewPlaying = !previewPlaying },
            onHoldStart = {},
            onHoldRelease = {},
            modifier = Modifier
              .size(width = 118.dp, height = 104.dp)
              .testTag("live_pad_preview")
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "LIVE BUTTON PREVIEW",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Tap preview to toggle Idle / Playing glow. Customize shape, glow, border & finish uniquely for this song.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedIconButton(
              onClick = {
                val reset = PadVisualConfig(
                  displayName = pad.fileName.substringBeforeLast('.')
                )
                commitVisual(reset)
              },
              modifier = Modifier
                .size(40.dp)
                .testTag("reset_pad_visual_button")
            ) {
              Icon(
                Icons.Filled.RestartAlt,
                contentDescription = "Reset visual style for this song",
                modifier = Modifier.size(18.dp)
              )
            }
          }
        }
      }

      // Section Switcher: Dasar | Audio | Efek | Lanjutan
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        FilterChip(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          leadingIcon = {
            Icon(Icons.Filled.Palette, contentDescription = null, modifier = Modifier.size(15.dp))
          },
          label = { Text(stringResource(R.string.pad_tab_basic), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
          modifier = Modifier
            .weight(1f)
            .testTag("tab_button_design")
        )
        FilterChip(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          leadingIcon = {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, modifier = Modifier.size(15.dp))
          },
          label = { Text(stringResource(R.string.pad_tab_audio), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
          modifier = Modifier
            .weight(1f)
            .testTag("tab_audio_fade")
        )
        FilterChip(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          leadingIcon = {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
          },
          label = { Text(stringResource(R.string.pad_tab_fx), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
          modifier = Modifier
            .weight(1f)
            .testTag("tab_eq_fx")
        )
        FilterChip(
          selected = selectedTab == 3,
          onClick = { selectedTab = 3 },
          leadingIcon = {
            Icon(Icons.Filled.SettingsApplications, contentDescription = null, modifier = Modifier.size(15.dp))
          },
          label = { Text(stringResource(R.string.pad_tab_advanced), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
          modifier = Modifier
            .weight(1f)
            .testTag("tab_advanced_settings")
        )
      }

      if (selectedTab == 0) {
        // ---- TAB 0: SPECIFIC PER-SONG BUTTON CUSTOMIZATION ----
        OutlinedTextField(
          value = visualConfig.displayName,
          onValueChange = { name ->
            visualConfig = visualConfig.copy(displayName = name)
          },
          label = { Text("Song Button Label") },
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pad_rename_input"),
          trailingIcon = {
            IconButton(onClick = {
              val resetName = pad.fileName.substringBeforeLast('.')
              commitVisual(visualConfig.copy(displayName = resetName))
            }) {
              Icon(Icons.Filled.RestartAlt, contentDescription = "Reset label")
            }
          }
        )

        // Category Tag & Preset Pills
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "CATEGORY TAG",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = categoryText,
            onValueChange = { next ->
              categoryText = next.take(24)
              onCategoryTagChange(categoryText)
            },
            placeholder = { Text("Beat, Vocal, SFX, Intro...") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("pad_category_tag_input"),
            trailingIcon = {
              if (categoryText.isNotEmpty()) {
                IconButton(onClick = {
                  categoryText = ""
                  onCategoryTagChange("")
                }) {
                  Icon(Icons.Filled.Close, contentDescription = "Clear category tag")
                }
              }
            }
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val tagPresets = listOf("Beat", "Bass", "Vocal", "SFX", "Loop", "Intro")
            itemsIndexed(tagPresets) { _, preset ->
              val selected = categoryText.equals(preset, ignoreCase = true)
              FilterChip(
                selected = selected,
                onClick = {
                  val next = if (selected) "" else preset
                  categoryText = next
                  onCategoryTagChange(next)
                },
                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
              )
            }
          }
        }

        // Label Visibility Switches
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          FilterChip(
            selected = visualConfig.showSlotNumber,
            onClick = { commitVisual(visualConfig.copy(showSlotNumber = !visualConfig.showSlotNumber)) },
            label = { Text("Show #Slot", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
              .weight(1f)
              .testTag("toggle_slot_number")
          )
          FilterChip(
            selected = visualConfig.showDurationLabel,
            onClick = { commitVisual(visualConfig.copy(showDurationLabel = !visualConfig.showDurationLabel)) },
            label = { Text("Show Duration", style = MaterialTheme.typography.labelSmall) },
            modifier = Modifier
              .weight(1f)
              .testTag("toggle_duration_label")
          )
        }

        // Pad Button Style (Global / Kotak / Segitiga / Bintang / Prisma)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = "BUTTON SHAPE STYLE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val shapeOptions: List<PadShapeStyle?> = listOf(null) + PadShapeStyle.entries
            shapeOptions.forEach { option ->
              val isSelected = visualConfig.shapeStyle == option
              Surface(
                modifier = Modifier
                  .weight(1f)
                  .clip(RoundedCornerShape(10.dp))
                  .testTag("pad_shape_${option?.name?.lowercase() ?: "default"}")
                  .clickable { commitVisual(visualConfig.copy(shapeStyle = option)) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
              ) {
                Column(
                  modifier = Modifier.padding(vertical = 8.dp),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  if (option == null) {
                    Icon(
                      imageVector = Icons.Filled.RestartAlt,
                      contentDescription = "Use global button style",
                      tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                  } else {
                    PadShapePreviewGlyph(
                      style = option,
                      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                  }
                  Text(
                    text = option?.label ?: "Global",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }

        // Corner Roundness (effective for Kotak / Global-Kotak)
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("CORNER ROUNDNESS (${visualConfig.cornerRadiusDp.roundToInt()} dp)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(
              onClick = { commitVisual(visualConfig.copy(cornerRadiusDp = 14f)) },
              modifier = Modifier.size(32.dp).testTag("reset_pad_corner_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = visualConfig.cornerRadiusDp,
            onValueChange = { r ->
              commitVisual(visualConfig.copy(cornerRadiusDp = r.roundToInt().toFloat().coerceIn(2f, 28f)))
            },
            valueRange = 2f..28f,
            modifier = Modifier.testTag("pad_corner_radius_slider")
          )
        }

        // Surface Finish
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "SURFACE FINISH",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            PadSurfaceFinish.entries.forEach { finish ->
              FilterChip(
                selected = visualConfig.surfaceFinish == finish,
                onClick = { commitVisual(visualConfig.copy(surfaceFinish = finish)) },
                label = { Text(finish.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("pad_finish_${finish.name.lowercase()}")
              )
            }
          }
        }

        // Song Badge Icon
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "SONG BADGE ICON",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(PadBadgeIcon.entries) { _, badge ->
              val isSelected = visualConfig.badgeIcon == badge
              val iconVec = padBadgeVector(badge) ?: Icons.Filled.Block
              Surface(
                modifier = Modifier
                  .clip(RoundedCornerShape(10.dp))
                  .testTag("pad_badge_${badge.name.lowercase()}")
                  .clickable { commitVisual(visualConfig.copy(badgeIcon = badge)) },
                shape = RoundedCornerShape(10.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    imageVector = iconVec,
                    contentDescription = badge.label,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                  )
                  Text(
                    text = badge.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }

        // Pad Fill Color Presets + Hue + Brightness
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "BUTTON FILL COLOR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          @OptIn(ExperimentalLayoutApi::class)
          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            PRESET_PAD_COLORS.forEachIndexed { index, colorVal ->
              val isSelected = visualConfig.colorArgb == colorVal
              val displayColor = colorVal?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(displayColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                  )
                  .testTag("color_preset_$index")
                  .clickable { commitVisual(visualConfig.copy(colorArgb = colorVal)) },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected pad color",
                    tint = if (colorVal == null) MaterialTheme.colorScheme.onSurface else Color.Black,
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
            Text("Hue", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = fillHue,
              onValueChange = { hue ->
                fillHue = hue
                val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.85f, fillBrightness))
                commitVisual(visualConfig.copy(colorArgb = argb.toLong() and 0xFFFFFFFFL))
              },
              valueRange = 0f..360f,
              modifier = Modifier.weight(1f)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text("Tone", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = fillBrightness,
              onValueChange = { bright ->
                fillBrightness = bright
                val argb = android.graphics.Color.HSVToColor(floatArrayOf(fillHue, 0.85f, bright))
                commitVisual(visualConfig.copy(colorArgb = argb.toLong() and 0xFFFFFFFFL))
              },
              valueRange = 0.25f..1.0f,
              modifier = Modifier.weight(1f)
            )
          }
        }

        // Corner Glow Color + Intensity
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CORNER GLOW COLOR & POWER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${(visualConfig.glowIntensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            PRESET_GLOW_COLORS.forEachIndexed { index, colorVal ->
              val isSelected = visualConfig.glowColorArgb == colorVal
              val displayColor = colorVal?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
              Box(
                modifier = Modifier
                  .size(30.dp)
                  .clip(CircleShape)
                  .background(displayColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                  )
                  .testTag("glow_preset_$index")
                  .clickable { commitVisual(visualConfig.copy(glowColorArgb = colorVal)) },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected corner glow color",
                    tint = if (colorVal == null) MaterialTheme.colorScheme.onSurface else Color.Black,
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
            Text("Glow Hue", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = glowHue,
              onValueChange = { hue ->
                glowHue = hue
                val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.90f, 1.0f))
                commitVisual(visualConfig.copy(glowColorArgb = argb.toLong() and 0xFFFFFFFFL))
              },
              valueRange = 0f..360f,
              modifier = Modifier.weight(1f)
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text("Power", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(50.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = visualConfig.glowIntensity,
              onValueChange = { intensity ->
                val snapped = (intensity * 20f).roundToInt() / 20f
                commitVisual(visualConfig.copy(glowIntensity = snapped.coerceIn(0f, 1.8f)))
              },
              valueRange = 0f..1.8f,
              modifier = Modifier
                .weight(1f)
                .testTag("pad_glow_intensity_slider")
            )
            IconButton(
              onClick = { commitVisual(visualConfig.copy(glowIntensity = 1.0f)) },
              modifier = Modifier.size(32.dp).testTag("reset_pad_glow_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
            }
          }
        }

        // Border Color + Width
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BORDER COLOR & THICKNESS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = if (visualConfig.borderWidthDp < 0.1f) "Off" else String.format(Locale.US, "%.1f dp", visualConfig.borderWidthDp),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            PRESET_BORDER_COLORS.forEachIndexed { idx, colorVal ->
              val isSelected = visualConfig.borderColorArgb == colorVal
              val displayColor = colorVal?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(displayColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                  )
                  .testTag("border_preset_$idx")
                  .clickable { commitVisual(visualConfig.copy(borderColorArgb = colorVal)) },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected border color",
                    tint = if (colorVal == null) MaterialTheme.colorScheme.onSurface else Color.Black,
                    modifier = Modifier.size(14.dp)
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
            Text("Hue", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = borderHue,
              onValueChange = { hue ->
                borderHue = hue
                val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.90f, 1.0f))
                commitVisual(visualConfig.copy(borderColorArgb = argb.toLong() and 0xFFFFFFFFL))
              },
              valueRange = 0f..360f,
              modifier = Modifier.weight(1f)
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text("Width", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(54.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
              value = visualConfig.borderWidthDp,
              onValueChange = { w ->
                val snapped = (w * 4f).roundToInt() / 4f
                commitVisual(visualConfig.copy(borderWidthDp = snapped.coerceIn(0f, 4f)))
              },
              valueRange = 0f..4f,
              modifier = Modifier
                .weight(1f)
                .testTag("pad_border_width_slider")
            )
          }
        }

        // Label Text Color
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "TEXT LABEL COLOR",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            PRESET_TEXT_COLORS.forEachIndexed { idx, colorVal ->
              val isSelected = visualConfig.textColorArgb == colorVal
              val displayColor = colorVal?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceVariant
              Box(
                modifier = Modifier
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(displayColor)
                  .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                  )
                  .testTag("text_color_preset_$idx")
                  .clickable { commitVisual(visualConfig.copy(textColorArgb = colorVal)) },
                contentAlignment = Alignment.Center
              ) {
                if (isSelected) {
                  Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected text color",
                    tint = if (colorVal == 0xFF0B1120L) Color.White else if (colorVal == null) MaterialTheme.colorScheme.onSurface else Color.Black,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }
          }
        }
      } else if (selectedTab == 1) {
        // ---- TAB 1: AUDIO, TRIGGER & FADE SETTINGS ----
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "TRIGGER MODE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            PadTriggerMode.entries.forEach { mode ->
              val selected = settings.triggerMode == mode
              FilterChip(
                selected = selected,
                onClick = {
                  val updated = settings.copy(
                    triggerMode = mode,
                    isLooping = if (mode == PadTriggerMode.LOOP) true else settings.isLooping
                  )
                  settings = updated
                  onSettingsChange(updated)
                },
                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("trigger_mode_${mode.name.lowercase()}")
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Loop Playback",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Repeat track continuously until stopped",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = settings.isLooping,
            onCheckedChange = { checked ->
              val updated = settings.copy(isLooping = checked)
              settings = updated
              onSettingsChange(updated)
            },
            modifier = Modifier.testTag("pad_loop_switch")
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "CHOKE GROUP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val groups = listOf(0 to "Off", 1 to "A", 2 to "B", 3 to "C", 4 to "D")
            groups.forEach { (grpId, label) ->
              FilterChip(
                selected = settings.chokeGroup == grpId,
                onClick = {
                  val updated = settings.copy(chokeGroup = grpId)
                  settings = updated
                  onSettingsChange(updated)
                },
                label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier.weight(1f)
              )
            }
          }
        }

        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PAD VOLUME (${(settings.volume * 100).roundToInt()}%)",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(
              onClick = {
                val updated = settings.copy(volume = 1.0f)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier.size(32.dp).testTag("reset_pad_volume_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.volume,
            onValueChange = { v ->
              val updated = settings.copy(volume = v)
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = 0f..1f,
            modifier = Modifier.testTag("pad_volume_slider")
          )
        }

        Column {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .pointerInput(pad.id) {
                detectTapGestures(
                  onDoubleTap = {
                    val updated = settings.copy(pan = 0f)
                    settings = updated
                    onSettingsChange(updated)
                  }
                )
              },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PAN KIRI / KANAN (${StereoPanMath.formatPanLabel(settings.pan)})",
              style = MaterialTheme.typography.labelMedium,
              color = if (abs(settings.pan) >= 0.04f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
              onClick = {
                val updated = settings.copy(pan = 0f)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier
                .size(32.dp)
                .testTag("reset_pad_pan_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = "Reset pan to center", modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.pan,
            onValueChange = { raw ->
              val snapped = if (abs(raw) < 0.04f) 0f else (raw * 20f).roundToInt() / 20f
              val updated = settings.copy(pan = snapped.coerceIn(-1f, 1f))
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = -1f..1f,
            modifier = Modifier.testTag("pad_pan_slider")
          )
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("L 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Double-tap untuk Center", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("R 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        if (pad.isClone) {
          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
          ) {
            Text(
              text = "Pad duplikat (CLONE): memiliki setting volume, speed, pan & cut independen. Menghapus pad ini tidak menghapus file audio asli.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.padding(10.dp)
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
              text = "SPEED (${String.format(Locale.US, "%.2fx", settings.speed)})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(
              onClick = {
                val updated = settings.copy(speed = 1.0f)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = "Reset speed to 1.0x", modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.speed,
            onValueChange = { spd ->
              val snapped = (spd * 20f).roundToInt() / 20f
              val updated = settings.copy(speed = snapped.coerceIn(0.5f, 2.0f))
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.testTag("pad_speed_slider")
          )
        }

        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "PITCH (${String.format(Locale.US, "%.2fx", settings.pitch)})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary
            )
            IconButton(
              onClick = {
                val updated = settings.copy(pitch = 1.0f)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier.size(32.dp)
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = "Reset pitch to 1.0x", modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.pitch,
            onValueChange = { p ->
              val snapped = (p * 20f).roundToInt() / 20f
              val updated = settings.copy(pitch = snapped.coerceIn(0.5f, 2.0f))
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = 0.5f..2.0f,
            modifier = Modifier.testTag("pad_pitch_slider")
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
              text = "Ducking Source (Priority Mic/Vox)",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Attenuates background pads while this pad plays (when Auto-Ducking is On)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = settings.isDuckingSource,
            onCheckedChange = { checked ->
              val updated = settings.copy(isDuckingSource = checked)
              settings = updated
              onSettingsChange(updated)
            },
            modifier = Modifier.testTag("pad_ducking_source_switch")
          )
        }

        Text(
          text = "LOUDNESS GAIN: ${String.format(Locale.US, "%.2fx", settings.normalizedGain)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary
        )
      } else if (selectedTab == 2) {
        // ---- TAB 2: REVERSE PLAY, 5-BAND EQ & REVERB/ECHO FX ----
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = if (settings.isReversed) 1.5.dp else 1.dp,
            color = if (settings.isReversed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier
                  .weight(1f)
                  .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.Replay,
                  contentDescription = null,
                  tint = if (settings.isReversed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp)
                )
                Column {
                  Text(
                    text = "Reverse Play",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Putar audio mundur (decode PCM & cache di storage app)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              if (isPreparingReverse) {
                CircularProgressIndicator(
                  modifier = Modifier
                    .size(22.dp)
                    .padding(end = 4.dp),
                  strokeWidth = 2.dp
                )
              }
              Switch(
                checked = settings.isReversed,
                onCheckedChange = { checked ->
                  val updated = settings.copy(isReversed = checked)
                  settings = updated
                  onSettingsChange(updated)
                },
                modifier = Modifier.testTag("pad_reverse_switch")
              )
            }
          }
        }

        EqFiveBandPanel(
          title = "Equalizer Pad (5-Band)",
          subtitle = "Digabung live dengan Master EQ (-12dB s/d +12dB)",
          enabled = settings.eqEnabled,
          preset = settings.eqPreset,
          bandsDb = settings.eqBandsDb,
          testTagPrefix = "pad",
          onEnabledChange = { enabled ->
            val updated = settings.copy(eqEnabled = enabled)
            settings = updated
            onSettingsChange(updated)
          },
          onPresetSelected = { preset ->
            val nextBands = if (preset == EqPreset.CUSTOM) settings.eqBandsDb else preset.gainsDb
            val nextEnabled = if (preset == EqPreset.FLAT) settings.eqEnabled else true
            val updated = settings.copy(
              eqEnabled = nextEnabled,
              eqPreset = preset,
              eqBandsDb = nextBands
            )
            settings = updated
            onSettingsChange(updated)
          },
          onBandChange = { idx, gainDb ->
            val updatedBands = EqPreset.sanitizeBands(settings.eqBandsDb).toMutableList().apply {
              this[idx] = gainDb.coerceIn(-12f, 12f)
            }
            val updated = settings.copy(
              eqEnabled = true,
              eqPreset = EqPreset.CUSTOM,
              eqBandsDb = updatedBands
            )
            settings = updated
            onSettingsChange(updated)
          },
          onReset = {
            val updated = settings.copy(
              eqEnabled = false,
              eqPreset = EqPreset.FLAT,
              eqBandsDb = EqPreset.FLAT.gainsDb
            )
            settings = updated
            onSettingsChange(updated)
          }
        )

        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            width = if (settings.echoEnabled || settings.reverbEnabled) 1.5.dp else 1.dp,
            color = if (settings.echoEnabled || settings.reverbEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant
          )
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(
                  imageVector = Icons.Filled.AutoFixHigh,
                  contentDescription = null,
                  tint = if (settings.echoEnabled || settings.reverbEnabled) {
                    MaterialTheme.colorScheme.secondary
                  } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                  },
                  modifier = Modifier.size(20.dp)
                )
                Column {
                  Text(
                    text = "Reverb & Echo FX",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Preset cepat atau atur parameter Echo & Room secara manual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              IconButton(
                onClick = {
                  val off = FxPreset.OFF
                  val updated = settings.copy(
                    fxPreset = off,
                    echoEnabled = off.echoEnabled,
                    echoDelayMs = off.echoDelayMs,
                    echoFeedback = off.echoFeedback,
                    echoMix = off.echoMix,
                    reverbEnabled = off.reverbEnabled,
                    reverbRoomSize = off.reverbRoomSize,
                    reverbDamping = off.reverbDamping,
                    reverbMix = off.reverbMix
                  )
                  settings = updated
                  onSettingsChange(updated)
                },
                modifier = Modifier.testTag("reset_pad_fx_button")
              ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset Reverb and Echo FX")
              }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              items(FxPreset.entries, key = { it.name }) { preset ->
                FilterChip(
                  selected = settings.fxPreset == preset,
                  onClick = {
                    val updated = if (preset == FxPreset.CUSTOM) {
                      settings.copy(fxPreset = FxPreset.CUSTOM)
                    } else {
                      settings.copy(
                        fxPreset = preset,
                        echoEnabled = preset.echoEnabled,
                        echoDelayMs = preset.echoDelayMs,
                        echoFeedback = preset.echoFeedback,
                        echoMix = preset.echoMix,
                        reverbEnabled = preset.reverbEnabled,
                        reverbRoomSize = preset.reverbRoomSize,
                        reverbDamping = preset.reverbDamping,
                        reverbMix = preset.reverbMix
                      )
                    }
                    settings = updated
                    onSettingsChange(updated)
                  },
                  label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) },
                  modifier = Modifier.testTag("fx_preset_${preset.name.lowercase()}")
                )
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Echo Controls
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("ECHO DELAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
              Switch(
                checked = settings.echoEnabled,
                onCheckedChange = { checked ->
                  val updated = settings.copy(
                    echoEnabled = checked,
                    fxPreset = if (!checked && !settings.reverbEnabled) FxPreset.OFF else FxPreset.CUSTOM
                  )
                  settings = updated
                  onSettingsChange(updated)
                },
                modifier = Modifier.testTag("pad_echo_switch")
              )
            }

            if (settings.echoEnabled) {
              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Delay Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${settings.echoDelayMs.roundToInt()} ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                  value = settings.echoDelayMs,
                  onValueChange = { v ->
                    val snapped = ((v / 10f).roundToInt() * 10f).coerceIn(60f, 800f)
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, echoDelayMs = snapped)
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 60f..800f,
                  modifier = Modifier.testTag("pad_echo_delay_slider")
                )
              }

              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Feedback", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${(settings.echoFeedback * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                  value = settings.echoFeedback,
                  onValueChange = { v ->
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, echoFeedback = v.coerceIn(0f, 0.75f))
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 0f..0.75f,
                  modifier = Modifier.testTag("pad_echo_feedback_slider")
                )
              }

              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Echo Mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${(settings.echoMix * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                  value = settings.echoMix,
                  onValueChange = { v ->
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, echoMix = v.coerceIn(0f, 0.85f))
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 0f..0.85f,
                  modifier = Modifier.testTag("pad_echo_mix_slider")
                )
              }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Reverb Controls
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("REVERB RUANGAN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
              Switch(
                checked = settings.reverbEnabled,
                onCheckedChange = { checked ->
                  val updated = settings.copy(
                    reverbEnabled = checked,
                    fxPreset = if (!checked && !settings.echoEnabled) FxPreset.OFF else FxPreset.CUSTOM
                  )
                  settings = updated
                  onSettingsChange(updated)
                },
                modifier = Modifier.testTag("pad_reverb_switch")
              )
            }

            if (settings.reverbEnabled) {
              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Room Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${(settings.reverbRoomSize * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                  value = settings.reverbRoomSize,
                  onValueChange = { v ->
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, reverbRoomSize = v.coerceIn(0f, 1f))
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 0f..1f,
                  modifier = Modifier.testTag("pad_reverb_room_slider")
                )
              }

              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Damping", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${(settings.reverbDamping * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                  value = settings.reverbDamping,
                  onValueChange = { v ->
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, reverbDamping = v.coerceIn(0f, 1f))
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 0f..1f,
                  modifier = Modifier.testTag("pad_reverb_damping_slider")
                )
              }

              Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Reverb Mix", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("${(settings.reverbMix * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                Slider(
                  value = settings.reverbMix,
                  onValueChange = { v ->
                    val updated = settings.copy(fxPreset = FxPreset.CUSTOM, reverbMix = v.coerceIn(0f, 0.85f))
                    settings = updated
                    onSettingsChange(updated)
                  },
                  valueRange = 0f..0.85f,
                  modifier = Modifier.testTag("pad_reverb_mix_slider")
                )
              }
            }
          }
        }
      } else {
        // ---- TAB 3: LANJUTAN (FADE / CROSSFADE & AKSI PAD) ----
        Text(
          text = "FADE & CROSSFADE (PER PAD)",
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
              text = "Include in Crossfade",
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Crossfades when global crossfade is enabled",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = settings.crossfadeEnabled,
            onCheckedChange = { checked ->
              val updated = settings.copy(crossfadeEnabled = checked)
              settings = updated
              onSettingsChange(updated)
            },
            modifier = Modifier.testTag("pad_crossfade_switch")
          )
        }

        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "FADE IN (${if (settings.fadeInMs == 0L) "Global" else "${settings.fadeInMs} ms"})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.primary
            )
            IconButton(
              onClick = {
                val updated = settings.copy(fadeInMs = 0L)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier.size(32.dp).testTag("reset_pad_fade_in_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.fadeInMs.toFloat(),
            onValueChange = { ms ->
              val snapped = ((ms / 100f).roundToInt() * 100L).coerceIn(0L, 5000L)
              val updated = settings.copy(fadeInMs = snapped)
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = 0f..5000f,
            modifier = Modifier.testTag("pad_fade_in_slider")
          )
        }

        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "FADE OUT (${if (settings.fadeOutMs == 0L) "Global" else "${settings.fadeOutMs} ms"})",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.secondary
            )
            IconButton(
              onClick = {
                val updated = settings.copy(fadeOutMs = 0L)
                settings = updated
                onSettingsChange(updated)
              },
              modifier = Modifier.size(32.dp).testTag("reset_pad_fade_out_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(18.dp))
            }
          }
          Slider(
            value = settings.fadeOutMs.toFloat(),
            onValueChange = { ms ->
              val snapped = ((ms / 100f).roundToInt() * 100L).coerceIn(0L, 5000L)
              val updated = settings.copy(fadeOutMs = snapped)
              settings = updated
              onSettingsChange(updated)
            },
            valueRange = 0f..5000f,
            modifier = Modifier.testTag("pad_fade_out_slider")
          )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "FADE CURVE",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          FadeCurveCanvas(curve = settings.fadeCurve, showCrossfadePair = false)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            FadeCurve.entries.forEach { curve ->
              FilterChip(
                selected = settings.fadeCurve == curve,
                onClick = {
                  val updated = settings.copy(fadeCurve = curve)
                  settings = updated
                  onSettingsChange(updated)
                },
                label = { Text(curve.shortLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("pad_fade_curve_${curve.name.lowercase()}")
              )
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
          text = "AKSI CEPAT PAD",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .testTag("adv_action_edit_audio")
              .clickable {
                onSaveAppearance(visualConfig)
                onOpenAudioEditor()
              },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(modifier = Modifier.weight(1f)) {
                Text("Edit Waveform / Potong Audio", style = MaterialTheme.typography.titleSmall)
                Text("Trim start/end & zoom waveform milidetik", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .testTag("adv_action_cue_preview")
              .clickable { onToggleCuePreview() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, if (isCuePlaying) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
              Column(modifier = Modifier.weight(1f)) {
                Text(if (isCuePlaying) "Stop Cue Headphone" else "Preview Cue ke Headphone", style = MaterialTheme.typography.titleSmall)
                Text("Dengar pad ke monitor pribadi sebelum live", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Surface(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .testTag("adv_action_duplicate")
                .clickable {
                  onSaveAppearance(visualConfig)
                  onDuplicatePad()
                },
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text("Duplikat", style = MaterialTheme.typography.labelLarge, maxLines = 1)
              }
            }

            Surface(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .testTag("adv_action_duplicate_bank")
                .clickable {
                  onSaveAppearance(visualConfig)
                  onDuplicateToBank()
                },
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Icon(Icons.Filled.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text("Ke Bank Lain", style = MaterialTheme.typography.labelLarge, maxLines = 1)
              }
            }
          }

          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .testTag("adv_action_delete_pad")
              .clickable { onDeletePad() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
              Column(modifier = Modifier.weight(1f)) {
                Text("Hapus Pad dari Papan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text("File asli di penyimpanan perangkat tidak ikut terhapus", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}
