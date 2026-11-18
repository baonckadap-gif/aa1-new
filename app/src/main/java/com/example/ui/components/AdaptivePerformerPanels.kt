package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SoundPadAudioEngine
import com.example.audio.StereoPanMath
import com.example.domain.model.EqPreset
import com.example.domain.model.PadPlaybackState
import com.example.domain.model.PlayMode
import com.example.ui.BankPageUi
import com.example.ui.theme.SoundPadTheme
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AdaptiveWidthClass {
  COMPACT,
  MEDIUM,
  EXPANDED
}

fun resolveAdaptiveWidthClass(maxWidth: Dp): AdaptiveWidthClass = when {
  maxWidth >= 840.dp -> AdaptiveWidthClass.EXPANDED
  maxWidth >= 600.dp -> AdaptiveWidthClass.MEDIUM
  else -> AdaptiveWidthClass.COMPACT
}

@Composable
fun LandscapeBankRail(
  bankPages: List<BankPageUi>,
  currentPageIndex: Int,
  playbackStates: Map<String, PadPlaybackState>,
  playMode: PlayMode,
  isEditMode: Boolean,
  isLocked: Boolean,
  isGlobalCueEnabled: Boolean,
  onSelectPage: (Int) -> Unit,
  onPlayModeSelected: (PlayMode) -> Unit,
  onRenameBankRequest: (Int) -> Unit,
  onRescanBankRequest: (Int) -> Unit,
  onReorderBanksRequest: () -> Unit,
  onToggleGlobalCue: () -> Unit,
  onToggleEditMode: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentPage = bankPages.getOrNull(currentPageIndex)
  val distinctBankCount = bankPages.map { it.bankKey }.distinct().size
  val extColors = SoundPadTheme.colors

  Surface(
    modifier = modifier
      .width(168.dp)
      .fillMaxHeight()
      .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
      .testTag("landscape_bank_rail"),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .padding(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "BANKS (${bankPages.size})",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
          if (currentPage?.canRescanSubfolder == true && !isLocked) {
            IconButton(
              onClick = { onRescanBankRequest(currentPage.bankKey) },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.cd_rescan_bank),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
            }
          }
          if (distinctBankCount > 1 && !isLocked) {
            IconButton(
              onClick = onReorderBanksRequest,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.SwapVert,
                contentDescription = stringResource(R.string.cd_reorder_banks),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          }
          if (!isLocked) {
            IconButton(
              onClick = { onRenameBankRequest(currentPage?.bankKey ?: currentPageIndex) },
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.DriveFileRenameOutline,
                contentDescription = stringResource(R.string.cd_rename_bank),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }

      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
      ) {
        itemsIndexed(bankPages, key = { index, page -> "${page.bankKey}_$index" }) { index, page ->
          val isCurrent = currentPageIndex == index
          val hasActive = page.pads.any { playbackStates[it.id]?.isPlaying == true }
          Surface(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .testTag("landscape_bank_item_$index")
              .clickable { onSelectPage(index) },
            shape = RoundedCornerShape(10.dp),
            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
              width = if (isCurrent) 1.5.dp else 1.dp,
              color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                hasActive -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.outlineVariant
              }
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              if (hasActive) {
                Box(
                  modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(extColors.ramEmerald)
                )
              }
              Text(
                text = page.title,
                style = MaterialTheme.typography.labelMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
              )
              Text(
                text = "${page.pads.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Compact PlayMode selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        PlayMode.entries.forEach { mode ->
          val selected = playMode == mode
          Surface(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(8.dp))
              .clickable(enabled = !isLocked) { onPlayModeSelected(mode) },
            shape = RoundedCornerShape(8.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
          ) {
            Text(
              text = mode.shortLabel,
              style = MaterialTheme.typography.labelSmall,
              color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
            )
          }
        }
      }

      // Cue & Edit toggles
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        OutlinedIconButton(
          onClick = onToggleGlobalCue,
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, if (isGlobalCueEnabled) extColors.cueMonitor else MaterialTheme.colorScheme.outline)
        ) {
          Icon(
            imageVector = Icons.Filled.Headphones,
            contentDescription = stringResource(R.string.cd_global_cue_toggle),
            tint = if (isGlobalCueEnabled) extColors.cueMonitor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
        OutlinedIconButton(
          enabled = !isLocked,
          onClick = onToggleEditMode,
          modifier = Modifier
            .weight(1f)
            .height(44.dp),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, if (isEditMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        ) {
          Icon(
            imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
            contentDescription = stringResource(R.string.cd_toggle_edit_mode),
            tint = if (isEditMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@Composable
fun LandscapeMixerSidePanel(
  activeVoices: Int,
  masterVolume: Float,
  masterPan: Float,
  masterEqEnabled: Boolean,
  masterEqPreset: EqPreset,
  onMasterVolumeChange: (Float) -> Unit,
  onMasterPanChange: (Float) -> Unit,
  onMasterEqEnabledChange: (Boolean) -> Unit,
  onMasterEqPresetSelect: (EqPreset) -> Unit,
  onStopAll: () -> Unit,
  onFadeOutAll: () -> Unit,
  onOpenFullAudioMixer: () -> Unit,
  modifier: Modifier = Modifier
) {
  val extColors = SoundPadTheme.colors

  Surface(
    modifier = modifier
      .width(214.dp)
      .fillMaxHeight()
      .padding(end = 8.dp, top = 4.dp, bottom = 4.dp)
      .testTag("landscape_mixer_panel"),
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(
      modifier = Modifier
        .fillMaxHeight()
        .verticalScroll(rememberScrollState())
        .padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "LIVE MIXER",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.primary
        )
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = if (activeVoices > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, if (activeVoices > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
        ) {
          Text(
            text = "$activeVoices/${SoundPadAudioEngine.MAX_PLAYER_POOL}",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (activeVoices > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }

      // Master Volume Fader
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text("VOLUME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Text("${(masterVolume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
          value = masterVolume,
          onValueChange = onMasterVolumeChange,
          valueRange = 0f..1f,
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
          )
        )
      }

      // Master Pan
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "PAN (${StereoPanMath.formatPanLabel(masterPan)})",
            style = MaterialTheme.typography.labelSmall,
            color = if (abs(masterPan) >= 0.04f) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
          )
          IconButton(
            onClick = { onMasterPanChange(0f) },
            modifier = Modifier.size(28.dp)
          ) {
            Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.action_reset), modifier = Modifier.size(16.dp))
          }
        }
        Slider(
          value = masterPan,
          onValueChange = { raw ->
            val snapped = if (abs(raw) < 0.04f) 0f else (raw * 20f).roundToInt() / 20f
            onMasterPanChange(snapped.coerceIn(-1f, 1f))
          },
          valueRange = -1f..1f
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

      // Master EQ Quick Control
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Equalizer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
            Text("MASTER EQ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
          }
          Switch(
            checked = masterEqEnabled,
            onCheckedChange = onMasterEqEnabledChange
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          listOf(EqPreset.FLAT, EqPreset.BASS_BOOST).forEach { preset ->
            FilterChip(
              selected = masterEqPreset == preset && masterEqEnabled,
              onClick = { onMasterEqPresetSelect(preset) },
              label = { Text(preset.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
              modifier = Modifier.weight(1f)
            )
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          listOf(EqPreset.VOCAL, EqPreset.TREBLE).forEach { preset ->
            FilterChip(
              selected = masterEqPreset == preset && masterEqEnabled,
              onClick = { onMasterEqPresetSelect(preset) },
              label = { Text(preset.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
              modifier = Modifier.weight(1f)
            )
          }
        }
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenFullAudioMixer),
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
          Text(
            text = "Open Full 5-Band EQ & Routing",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
          )
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      // Panic Stop All + Fade Out All
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedIconButton(
          enabled = activeVoices > 0,
          onClick = onFadeOutAll,
          modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, if (activeVoices > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = stringResource(R.string.cd_fade_out_all), tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
            Text("FADE ALL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
          }
        }

        FilledTonalButton(
          onClick = onStopAll,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("landscape_stop_all_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (activeVoices > 0) extColors.panicRed else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (activeVoices > 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
          )
        ) {
          Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.cd_stop_all), modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("STOP ALL", style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }
}
