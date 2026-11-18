package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.SoundPad
import com.example.domain.model.WaveformData
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorSheet(
  pad: SoundPad,
  waveform: WaveformData?,
  isLoadingWaveform: Boolean,
  isExporting: Boolean,
  previewPositionMs: Long?,
  onStartPreview: (uriString: String, startMs: Long, endMs: Long) -> Unit,
  onStopPreview: () -> Unit,
  onSaveNonDestructiveTrim: (padId: String, startMs: Long, endMs: Long) -> Unit,
  onExportCutToNewPad: (pad: SoundPad, startMs: Long, endMs: Long) -> Unit,
  onExportCutToFile: (pad: SoundPad, startMs: Long, endMs: Long) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val totalMs = (waveform?.durationMs?.takeIf { it > 0L } ?: pad.durationMs).coerceAtLeast(500L)

  val initialStart = (pad.settings.trimStartMs.coerceIn(0L, totalMs - 100L)).toFloat() / totalMs.toFloat()
  val initialEnd = if (pad.settings.trimEndMs > pad.settings.trimStartMs) {
    (pad.settings.trimEndMs.coerceIn(pad.settings.trimStartMs + 100L, totalMs)).toFloat() / totalMs.toFloat()
  } else {
    1f
  }

  var startFraction by remember(pad.id, totalMs) { mutableFloatStateOf(initialStart.coerceIn(0f, 0.95f)) }
  var endFraction by remember(pad.id, totalMs) { mutableFloatStateOf(initialEnd.coerceIn(0.05f, 1f)) }
  var zoomFactor by remember(pad.id) { mutableFloatStateOf(1f) }
  var panOffsetFraction by remember(pad.id) { mutableFloatStateOf(0f) }

  val visibleSpan = (1f / zoomFactor.coerceIn(1f, 8f)).coerceIn(0.125f, 1f)
  val maxPan = (1f - visibleSpan).coerceAtLeast(0f)
  val clampedPan = panOffsetFraction.coerceIn(0f, maxPan)
  val viewStartFrac = clampedPan
  val viewEndFrac = (clampedPan + visibleSpan).coerceAtMost(1f)

  val startMs = (startFraction * totalMs).toLong().coerceIn(0L, totalMs - 50L)
  val endMs = (endFraction * totalMs).toLong().coerceIn(startMs + 50L, totalMs)
  val isFullRange = startMs <= 20L && endMs >= totalMs - 20L
  val isPreviewPlaying = previewPositionMs != null

  DisposableEffect(pad.id) {
    onDispose {
      onStopPreview()
    }
  }

  ModalBottomSheet(
    onDismissRequest = {
      onStopPreview()
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
        .padding(horizontal = 20.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Waveform & Non-Destructive Cut",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "${pad.displayName} · ${formatMillis(totalMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          IconButton(
            onClick = {
              onStopPreview()
              onDismiss()
            },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(Icons.Filled.Close, contentDescription = "Close audio editor")
          }
          FilledIconButton(
            onClick = {
              onStopPreview()
              onSaveNonDestructiveTrim(
                pad.id,
                if (isFullRange) 0L else startMs,
                if (isFullRange) 0L else endMs
              )
              onDismiss()
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("save_trim_metadata_button")
          ) {
            Icon(Icons.Filled.Check, contentDescription = "Save non-destructive cut metadata")
          }
        }
      }

      // Zoom toolbar row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Pinch waveform untuk zoom · Tarik garis IN / OUT",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.weight(1f)
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IconButton(
            enabled = zoomFactor > 1.05f,
            onClick = {
              val nextZoom = (zoomFactor / 1.5f).coerceIn(1f, 8f)
              zoomFactor = nextZoom
              val nextSpan = 1f / nextZoom
              panOffsetFraction = panOffsetFraction.coerceIn(0f, (1f - nextSpan).coerceAtLeast(0f))
            },
            modifier = Modifier
              .size(36.dp)
              .testTag("waveform_zoom_out_button")
          ) {
            Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out waveform", modifier = Modifier.size(18.dp))
          }
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Text(
              text = String.format(Locale.US, "%.1fx", zoomFactor),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
          IconButton(
            enabled = zoomFactor < 7.95f,
            onClick = {
              val nextZoom = (zoomFactor * 1.5f).coerceIn(1f, 8f)
              val nextSpan = 1f / nextZoom
              val mid = ((startFraction + endFraction) * 0.5f)
              zoomFactor = nextZoom
              panOffsetFraction = (mid - nextSpan * 0.5f).coerceIn(0f, (1f - nextSpan).coerceAtLeast(0f))
            },
            modifier = Modifier
              .size(36.dp)
              .testTag("waveform_zoom_in_button")
          ) {
            Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in waveform", modifier = Modifier.size(18.dp))
          }
          if (zoomFactor > 1.05f) {
            IconButton(
              onClick = {
                zoomFactor = 1f
                panOffsetFraction = 0f
              },
              modifier = Modifier
                .size(36.dp)
                .testTag("waveform_zoom_reset_button")
            ) {
              Icon(Icons.Filled.RestartAlt, contentDescription = "Reset waveform zoom", modifier = Modifier.size(18.dp))
            }
          }
        }
      }

      // Waveform Canvas Surface with Pinch-to-Zoom + Pan + Handle Drag
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(164.dp)
            .padding(12.dp),
          contentAlignment = Alignment.Center
        ) {
          if (isLoadingWaveform || waveform == null) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
          } else {
            val activeColor = MaterialTheme.colorScheme.primary
            val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
            val startHandleColor = MaterialTheme.colorScheme.secondary
            val endHandleColor = MaterialTheme.colorScheme.error
            val playheadColor = MaterialTheme.colorScheme.tertiary

            Canvas(
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.65f))
                .pointerInput(totalMs) {
                  awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val curSpan = (1f / zoomFactor.coerceIn(1f, 8f)).coerceIn(0.125f, 1f)
                    val curPan = panOffsetFraction.coerceIn(0f, (1f - curSpan).coerceAtLeast(0f))
                    val tapAudioFrac = (curPan + (down.position.x / size.width.toFloat()) * curSpan).coerceIn(0f, 1f)
                    val draggingStart = abs(tapAudioFrac - startFraction) <= abs(tapAudioFrac - endFraction)
                    var pinchDetected = false

                    while (true) {
                      val event = awaitPointerEvent()
                      val activePointers = event.changes.count { it.pressed }
                      if (activePointers == 0) break

                      if (activePointers >= 2) {
                        pinchDetected = true
                        val zoomDelta = event.calculateZoom()
                        val panDelta = event.calculatePan()
                        val oldSpan = 1f / zoomFactor.coerceIn(1f, 8f)
                        val newZoom = (zoomFactor * zoomDelta).coerceIn(1f, 8f)
                        val newSpan = 1f / newZoom
                        val maxP = (1f - newSpan).coerceAtLeast(0f)
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val shifted = panOffsetFraction + (oldSpan - newSpan) * 0.5f - (panDelta.x / widthPx) * newSpan
                        zoomFactor = newZoom
                        panOffsetFraction = shifted.coerceIn(0f, maxP)
                        event.changes.forEach { it.consume() }
                      } else if (!pinchDetected) {
                        val change = event.changes.firstOrNull { it.pressed } ?: break
                        val dx = change.position.x - change.previousPosition.x
                        if (abs(dx) > 0.2f) {
                          change.consume()
                          val span = 1f / zoomFactor.coerceIn(1f, 8f)
                          val deltaFrac = (dx / size.width.toFloat().coerceAtLeast(1f)) * span
                          if (draggingStart) {
                            startFraction = (startFraction + deltaFrac).coerceIn(0f, endFraction - 0.01f)
                          } else {
                            endFraction = (endFraction + deltaFrac).coerceIn(startFraction + 0.01f, 1f)
                          }
                        }
                      }
                    }
                  }
                }
            ) {
              val w = size.width
              val h = size.height
              val midY = h / 2f
              val span = (viewEndFrac - viewStartFrac).coerceAtLeast(0.05f)

              fun audioFracToX(audioFrac: Float): Float = ((audioFrac - viewStartFrac) / span) * w

              // Selected region backdrop
              val sx = audioFracToX(startFraction)
              val ex = audioFracToX(endFraction)
              val visSx = sx.coerceIn(0f, w)
              val visEx = ex.coerceIn(0f, w)
              if (visEx > visSx) {
                drawRect(
                  color = activeColor.copy(alpha = 0.14f),
                  topLeft = Offset(visSx, 0f),
                  size = Size((visEx - visSx).coerceAtLeast(2f), h)
                )
              }

              // Amplitude bars inside zoomed viewport
              val bars = waveform.amplitudes
              if (bars.isNotEmpty()) {
                val barW = ((w / (bars.size * span)) * 0.68f).coerceIn(2f, 12f)
                bars.forEachIndexed { idx, amp ->
                  val frac = (idx + 0.5f) / bars.size.toFloat()
                  if (frac in (viewStartFrac - 0.02f)..(viewEndFrac + 0.02f)) {
                    val x = audioFracToX(frac)
                    val inside = frac in startFraction..endFraction
                    val barH = (amp * h * 0.84f).coerceAtLeast(4f)
                    drawRoundRect(
                      color = if (inside) activeColor else mutedColor,
                      topLeft = Offset(x - barW * 0.5f, midY - barH / 2f),
                      size = Size(barW, barH),
                      cornerRadius = CornerRadius(2f, 2f)
                    )
                  }
                }
              }

              // Start handle line
              if (sx in -12f..(w + 12f)) {
                val clampedSx = sx.coerceIn(0f, w)
                drawLine(
                  color = startHandleColor,
                  start = Offset(clampedSx, 0f),
                  end = Offset(clampedSx, h),
                  strokeWidth = 3.dp.toPx()
                )
                drawCircle(color = startHandleColor, radius = 6.dp.toPx(), center = Offset(clampedSx, 10.dp.toPx()))
              }

              // End handle line
              if (ex in -12f..(w + 12f)) {
                val clampedEx = ex.coerceIn(0f, w)
                drawLine(
                  color = endHandleColor,
                  start = Offset(clampedEx, 0f),
                  end = Offset(clampedEx, h),
                  strokeWidth = 3.dp.toPx()
                )
                drawCircle(color = endHandleColor, radius = 6.dp.toPx(), center = Offset(clampedEx, h - 10.dp.toPx()))
              }

              // Live preview playhead
              if (previewPositionMs != null && totalMs > 0L) {
                val pFrac = (previewPositionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                if (pFrac in viewStartFrac..viewEndFrac) {
                  val px = audioFracToX(pFrac)
                  drawLine(
                    color = playheadColor,
                    start = Offset(px, 0f),
                    end = Offset(px, h),
                    strokeWidth = 2.dp.toPx()
                  )
                }
              }
            }
          }
        }
      }

      // Start / End Exact Millisecond Timestamps & Range Slider
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "IN: ${formatMillis(startMs)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.secondary
        )
        Text(
          text = "LEN: ${formatMillis(endMs - startMs)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "OUT: ${formatMillis(endMs)}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.error
        )
      }

      RangeSlider(
        value = startFraction..endFraction,
        onValueChange = { range ->
          val s = range.start.coerceIn(0f, 0.98f)
          val e = range.endInclusive.coerceIn(s + 0.01f, 1f)
          startFraction = s
          endFraction = e
        },
        valueRange = 0f..1f,
        modifier = Modifier.testTag("waveform_trim_range_slider")
      )

      // Icon-only Action Row: Preview, Reset Cut, Export to New Pad, Export to WAV File
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilledIconButton(
          onClick = {
            if (isPreviewPlaying) {
              onStopPreview()
            } else {
              onStartPreview(pad.uriString, startMs, endMs)
            }
          },
          modifier = Modifier
            .size(52.dp)
            .testTag("editor_preview_button"),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isPreviewPlaying) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
          )
        ) {
          Icon(
            imageVector = if (isPreviewPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (isPreviewPlaying) "Stop preview" else "Preview trimmed section"
          )
        }

        OutlinedIconButton(
          onClick = {
            onStopPreview()
            startFraction = 0f
            endFraction = 1f
            zoomFactor = 1f
            panOffsetFraction = 0f
          },
          modifier = Modifier
            .size(52.dp)
            .testTag("editor_reset_trim_button")
        ) {
          Icon(Icons.Filled.RestartAlt, contentDescription = "Reset cut range")
        }

        OutlinedIconButton(
          enabled = !isExporting,
          onClick = {
            onStopPreview()
            onExportCutToNewPad(pad, startMs, endMs)
          },
          modifier = Modifier
            .size(52.dp)
            .testTag("editor_export_new_pad_button")
        ) {
          if (isExporting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
          } else {
            Icon(
              Icons.Filled.LibraryAdd,
              contentDescription = "Export cut as new pad",
              tint = MaterialTheme.colorScheme.primary
            )
          }
        }

        OutlinedIconButton(
          enabled = !isExporting,
          onClick = {
            onStopPreview()
            onExportCutToFile(pad, startMs, endMs)
          },
          modifier = Modifier
            .size(52.dp)
            .testTag("editor_export_wav_file_button")
        ) {
          Icon(
            Icons.Filled.Download,
            contentDescription = "Export cut audio to new WAV file",
            tint = MaterialTheme.colorScheme.secondary
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

private fun formatMillis(ms: Long): String {
  val safe = ms.coerceAtLeast(0L)
  val totalSec = safe / 1000L
  val min = totalSec / 60L
  val sec = totalSec % 60L
  val millis = safe % 1000L
  return String.format(Locale.US, "%d:%02d.%03d", min, sec, millis)
}
