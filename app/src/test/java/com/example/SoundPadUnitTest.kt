package com.example

import com.example.data.saf.SafAudioScanner
import com.example.domain.model.GridConfig
import com.example.domain.model.GridPreset
import com.example.domain.model.SoundPad
import com.example.ui.SoundPadUiState
import com.example.ui.components.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPadUnitTest {

  @Test
  fun `grid presets calculate pads per bank accurately`() {
    assertEquals(16, GridConfig(GridPreset.GRID_4X4, 4, 4).padsPerBank)
    assertEquals(100, GridConfig(GridPreset.GRID_10X10, 10, 10).padsPerBank)
    assertEquals(225, GridConfig(GridPreset.GRID_15X15, 15, 15).padsPerBank)
    assertEquals(48, GridConfig(GridPreset.CUSTOM, 6, 8).padsPerBank)
  }

  @Test
  fun `banks automatically paginate pads exceeding grid capacity`() {
    val pads = (0 until 35).map { idx ->
      SoundPad(
        id = "pad_$idx",
        uriString = "content://test/$idx",
        fileName = "track_$idx.wav",
        displayName = "Track $idx",
        extension = "WAV",
        durationMs = 2500L,
        sizeBytes = 1024L,
        positionIndex = idx
      )
    }
    val state = SoundPadUiState(pads = pads)
    assertEquals(3, state.banks.size)
    assertEquals(16, state.banks[0].size)
    assertEquals(16, state.banks[1].size)
    assertEquals(3, state.banks[2].size)
  }

  @Test
  fun `supported audio extensions match requirements`() {
    assertTrue(
      SafAudioScanner.SUPPORTED_EXTENSIONS.containsAll(
        listOf("mp3", "wav", "ogg", "m4a", "flac")
      )
    )
  }

  @Test
  fun `formatDuration formats milliseconds accurately`() {
    assertEquals("0:00", formatDuration(0L))
    assertEquals("0:05", formatDuration(5000L))
    assertEquals("1:23", formatDuration(83000L))
  }

  @Test
  fun `search query filters pads by displayName fileName or extension`() {
    val pads = listOf(
      SoundPad("1", "u1", "kick_808.wav", "Punchy Kick", "WAV", 1000L, 100L, 0),
      SoundPad("2", "u2", "snare_crack.mp3", "Snare Hit", "MP3", 1000L, 100L, 1),
      SoundPad("3", "u3", "pad_ambient.flac", "Warm Pad", "FLAC", 2000L, 200L, 2)
    )
    val byName = SoundPadUiState(pads = pads, searchQuery = "snare").filteredPads
    assertEquals(1, byName.size)
    assertEquals("2", byName.first().id)

    val byExt = SoundPadUiState(pads = pads, searchQuery = "flac").filteredPads
    assertEquals(1, byExt.size)
    assertEquals("3", byExt.first().id)
  }

  @Test
  fun `fade curves satisfy boundary and equal power invariants`() {
    com.example.domain.model.FadeCurve.entries.forEach { curve ->
      assertEquals(0f, com.example.audio.FadeCurveMath.gainIn(curve, 0f), 0.001f)
      assertEquals(1f, com.example.audio.FadeCurveMath.gainIn(curve, 1f), 0.001f)
      assertEquals(1f, com.example.audio.FadeCurveMath.gainOut(curve, 0f), 0.001f)
      assertEquals(0f, com.example.audio.FadeCurveMath.gainOut(curve, 1f), 0.001f)
    }

    // Equal-power law: sin^2 + cos^2 == 1.0 at midpoint
    val (outMid, inMid) = com.example.audio.FadeCurveMath.equalPowerCrossfadePair(
      com.example.domain.model.FadeCurve.EQUAL_POWER,
      0.5f
    )
    assertEquals(1.0f, outMid * outMid + inMid * inMid, 0.001f)
  }

  @Test
  fun `eq math combines master and pad bands within bounds and computes biquad bypass`() {
    val sanitized = com.example.domain.model.EqPreset.sanitizeBands(listOf(-20f, 5f, 15f))
    assertEquals(listOf(-12f, 5f, 12f, 0f, 0f), sanitized)

    val combined = com.example.audio.FiveBandEqMath.combineEqBands(
      masterEnabled = true,
      masterBandsDb = listOf(8f, -10f, 2f, 0f, 10f),
      padEnabled = true,
      padBandsDb = listOf(12f, -10f, 3f, -4f, 0f)
    )
    assertEquals(18f, combined[0], 0.001f)
    assertEquals(-18f, combined[1], 0.001f)
    assertEquals(5f, combined[2], 0.001f)
    assertEquals(-4f, combined[3], 0.001f)
    assertEquals(10f, combined[4], 0.001f)

    val masterOnly = com.example.audio.FiveBandEqMath.combineEqBands(
      masterEnabled = false,
      masterBandsDb = listOf(8f, 8f, 8f, 8f, 8f),
      padEnabled = true,
      padBandsDb = listOf(4f, 0f, 0f, 0f, 0f)
    )
    assertEquals(4f, masterOnly[0], 0.001f)
    assertEquals(0f, masterOnly[1], 0.001f)

    assertTrue(com.example.audio.FiveBandEqMath.computePeakingBiquad(44100, 910f, 0f).isBypass)
    val boostCoeff = com.example.audio.FiveBandEqMath.computePeakingBiquad(44100, 910f, 6f)
    assertTrue(!boostCoeff.isBypass)
  }

  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  @Test
  fun `five band eq processor boosts target frequency and bypasses when flat`() {
    val sampleRate = 44100
    val frames = 512
    val inputShorts = ShortArray(frames * 2)
    for (i in 0 until frames) {
      val s = (kotlin.math.sin(2.0 * Math.PI * 910.0 * i / sampleRate) * 4000.0).toInt().toShort()
      inputShorts[i * 2] = s
      inputShorts[i * 2 + 1] = s
    }
    val inputBytes = java.nio.ByteBuffer.allocate(inputShorts.size * 2)
      .order(java.nio.ByteOrder.LITTLE_ENDIAN)
      .apply { inputShorts.forEach { putShort(it) }; flip() }

    val processor = com.example.audio.FiveBandEqAudioProcessor()
    val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
      sampleRate,
      2,
      androidx.media3.common.C.ENCODING_PCM_16BIT
    )
    processor.configure(format)
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)

    // Bypass mode check
    processor.setBandsDb(floatArrayOf(0f, 0f, 0f, 0f, 0f))
    val bypassInput = inputBytes.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    processor.queueInput(bypassInput)
    val bypassOut = processor.getOutput().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    assertEquals(frames * 4, bypassOut.remaining())
    for (i in inputShorts.indices) {
      assertEquals(inputShorts[i], bypassOut.short)
    }

    // +10 dB boost at 910Hz check
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)
    processor.setBandsDb(floatArrayOf(0f, 0f, 10f, 0f, 0f))
    val boostInput = inputBytes.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    processor.queueInput(boostInput)
    val boostOut = processor.getOutput().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    assertEquals(frames * 4, boostOut.remaining())

    var inEnergy = 0.0
    var outEnergy = 0.0
    for (i in inputShorts.indices) {
      val inS = inputShorts[i].toDouble()
      val outS = boostOut.short.toDouble()
      inEnergy += inS * inS
      outEnergy += outS * outS
    }
    assertTrue("Expected boosted RMS energy ($outEnergy) > input ($inEnergy)", outEnergy > inEnergy * 2.0)
  }

  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  @Test
  fun `reverb echo processor preserves dry signal when bypassed and produces wet tail when active`() {
    val sampleRate = 44100
    val frames = 1024
    val inputShorts = ShortArray(frames * 2)
    // Impulse at frame 0, silence after
    inputShorts[0] = 16000
    inputShorts[1] = 16000

    val processor = com.example.audio.ReverbEchoAudioProcessor()
    val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
      sampleRate,
      2,
      androidx.media3.common.C.ENCODING_PCM_16BIT
    )
    processor.configure(format)
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)

    val bypassBuf = java.nio.ByteBuffer.allocate(inputShorts.size * 2)
      .order(java.nio.ByteOrder.LITTLE_ENDIAN)
      .apply { inputShorts.forEach { putShort(it) }; flip() }
    processor.queueInput(bypassBuf)
    val bypassOut = processor.getOutput().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    assertEquals(16000.toShort(), bypassOut.short)
    assertEquals(16000.toShort(), bypassOut.short)
    assertEquals(0.toShort(), bypassOut.short)

    // Enable reverb + short echo and feed 4410 silent frames after impulse to observe wet tail
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)
    processor.setParams(
      com.example.audio.ReverbEchoParams(
        echoEnabled = true,
        echoDelayMs = 60f,
        echoFeedback = 0.5f,
        echoMix = 0.5f,
        reverbEnabled = true,
        reverbRoomSize = 0.8f,
        reverbDamping = 0.3f,
        reverbMix = 0.5f
      )
    )
    val wetFrames = 4410
    val wetBuf = java.nio.ByteBuffer.allocate(wetFrames * 4)
      .order(java.nio.ByteOrder.LITTLE_ENDIAN)
    wetBuf.putShort(16000)
    wetBuf.putShort(16000)
    for (i in 1 until wetFrames) {
      wetBuf.putShort(0)
      wetBuf.putShort(0)
    }
    wetBuf.flip()

    processor.queueInput(wetBuf)
    val wetOut = processor.getOutput().order(java.nio.ByteOrder.LITTLE_ENDIAN)
    assertEquals(wetFrames * 4, wetOut.remaining())
    var tailNonZeroCount = 0
    for (f in 0 until wetFrames) {
      val l = wetOut.short.toInt()
      val r = wetOut.short.toInt()
      if (f > 100 && (l != 0 || r != 0)) {
        tailNonZeroCount++
      }
    }
    assertTrue("Expected reverb/echo tail samples > 0", tailNonZeroCount > 100)
  }

  @Test
  fun `reversePcm16Frames reverses frame order while preserving stereo L R pair per frame`() {
    val pcm = ByteArray(12)
    val buf = java.nio.ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    // Frame 0: L=100, R=200
    buf.putShort(100)
    buf.putShort(200)
    // Frame 1: L=300, R=400
    buf.putShort(300)
    buf.putShort(400)
    // Frame 2: L=500, R=600
    buf.putShort(500)
    buf.putShort(600)

    val reversed = com.example.audio.ReverseAudioCacheManager.reversePcm16Frames(pcm, channels = 2)
    val out = java.nio.ByteBuffer.wrap(reversed).order(java.nio.ByteOrder.LITTLE_ENDIAN)

    // Frame 2 first
    assertEquals(500.toShort(), out.short)
    assertEquals(600.toShort(), out.short)
    // Frame 1
    assertEquals(300.toShort(), out.short)
    assertEquals(400.toShort(), out.short)
    // Frame 0 last
    assertEquals(100.toShort(), out.short)
    assertEquals(200.toShort(), out.short)
  }

  @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
  @Test
  fun `reverb echo processor skips buffer allocation while bypassed and allocates lazily when enabled`() {
    val processor = com.example.audio.ReverbEchoAudioProcessor()
    val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(
      44100,
      2,
      androidx.media3.common.C.ENCODING_PCM_16BIT
    )
    processor.configure(format)
    processor.flush(androidx.media3.common.audio.AudioProcessor.StreamMetadata.DEFAULT)

    val silence = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    repeat(32) { silence.putShort(0) }
    silence.flip()
    processor.queueInput(silence)
    assertTrue(!processor.hasAllocatedBuffers())

    processor.setParams(com.example.audio.ReverbEchoParams(reverbEnabled = true, reverbMix = 0.4f))
    val activeBuf = java.nio.ByteBuffer.allocate(64).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    repeat(32) { activeBuf.putShort(1000) }
    activeBuf.flip()
    processor.queueInput(activeBuf)
    assertTrue(processor.hasAllocatedBuffers())
  }

  @Test
  fun `global effects settings default to enabled and toggle cleanly`() {
    val defaults = com.example.domain.model.GlobalSettings()
    assertTrue(defaults.audioEffectsEnabled)
    assertTrue(defaults.visualEffectsEnabled)

    val disabled = defaults.copy(
      audioEffectsEnabled = false,
      visualEffectsEnabled = false
    )
    assertTrue(!disabled.audioEffectsEnabled)
    assertTrue(!disabled.visualEffectsEnabled)
  }

  @Test
  fun `explicit bank order keeps newly added empty bank pages visible`() {
    val profile = com.example.domain.model.PadProfile(
      id = "default_profile",
      name = "Default Session",
      folderUri = null,
      gridConfig = GridConfig(GridPreset.GRID_4X4, 4, 4),
      padCount = 2,
      createdAtMs = 1L,
      updatedAtMs = 1L,
      isActive = true,
      bankNames = mapOf(0 to "Intro", 1 to "B2"),
      bankOrder = listOf(0, 1)
    )
    val pads = listOf(
      SoundPad("1", "u1", "a.wav", "A", "WAV", 1000L, 100L, 0, bankIndex = 0),
      SoundPad("2", "u2", "b.wav", "B", "WAV", 1000L, 100L, 1, bankIndex = 0)
    )
    val state = SoundPadUiState(
      settings = com.example.domain.model.GlobalSettings(activeProfileId = "default_profile"),
      profiles = listOf(profile),
      pads = pads
    )
    assertTrue(state.isBankExplicitMode)
    assertEquals(2, state.bankPages.size)
    assertEquals("Intro", state.bankPages[0].title)
    assertEquals(2, state.bankPages[0].pads.size)
    assertEquals("B2", state.bankPages[1].title)
    assertEquals(0, state.bankPages[1].pads.size)
  }

  @Test
  fun `soundPad and globalSettings defaults enforce neutral gray pads and focus mode defaults`() {
    val pad = SoundPad(
      id = "pad_default",
      uriString = "content://test/default.wav",
      fileName = "default.wav",
      displayName = "Default",
      extension = "WAV",
      durationMs = 1200L,
      sizeBytes = 4096L,
      positionIndex = 0
    )
    assertEquals(null, pad.colorArgb)
    assertEquals(null, pad.glowColorArgb)
    assertEquals(0f, pad.glowIntensity, 0.0001f)

    val settings = com.example.domain.model.GlobalSettings()
    assertEquals(com.example.domain.model.DefaultPadColorMode.GRAY, settings.defaultPadColorMode)
    assertEquals(0xFF4A4F57L, settings.defaultPadColorArgb)
    assertEquals(false, settings.isFocusMode)
    assertEquals(false, settings.startInFocusMode)
    assertEquals(true, settings.focusCornerPanicEnabled)
    assertEquals(null, settings.focusModeMidiNote)
  }

  @Test
  fun `defaultPadColorMode and midiCcTarget include Stage A entries`() {
    assertEquals(
      listOf(
        com.example.domain.model.DefaultPadColorMode.GRAY,
        com.example.domain.model.DefaultPadColorMode.AUTO,
        com.example.domain.model.DefaultPadColorMode.CUSTOM
      ),
      com.example.domain.model.DefaultPadColorMode.entries
    )
    assertTrue(
      com.example.domain.model.MidiCcTarget.entries.contains(
        com.example.domain.model.MidiCcTarget.TOGGLE_FOCUS_MODE
      )
    )
  }
}

