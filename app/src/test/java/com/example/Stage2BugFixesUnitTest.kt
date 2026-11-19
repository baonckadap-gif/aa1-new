package com.example

import android.media.AudioFormat
import com.example.audio.AudioWaveformAndCutEngine
import com.example.domain.model.SoundPad
import com.example.ui.BankPageUi
import com.example.ui.PadGridSlotManager
import com.example.ui.components.buildSchedulerBankFilters
import com.example.ui.components.filterSchedulerPads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Stage2BugFixesUnitTest {

  private fun makePad(
    id: String,
    name: String = "Track $id",
    bankIndex: Int = 0,
    positionIndex: Int = 0,
    subfolderName: String = "",
    extension: String = "WAV",
    categoryTag: String = ""
  ): SoundPad {
    return SoundPad(
      id = id,
      uriString = "content://test/$id.$extension",
      fileName = "$id.${extension.lowercase()}",
      displayName = name,
      extension = extension,
      durationMs = 2000L,
      sizeBytes = 4096L,
      positionIndex = positionIndex,
      bankIndex = bankIndex,
      subfolderName = subfolderName,
      categoryTag = categoryTag
    )
  }

  @Test
  fun `resolveBankOrderAfterScan preserves manual order without subfolders and appends new banks`() {
    val result = PadGridSlotManager.resolveBankOrderAfterScan(
      existingOrder = listOf(2, 0, 1),
      hasSubfolderBanks = false,
      scannedBankKeys = emptySet(),
      padBankKeys = setOf(0, 1, 2, 3),
      existingBankNames = mapOf(0 to "Main", 1 to "FX", 2 to "Vocals", 3 to "Stingers"),
      previousSubfolderKeys = emptySet()
    )

    assertEquals(listOf(2, 0, 1, 3), result)
  }

  @Test
  fun `resolveBankOrderAfterScan keeps manual empty banks and removes only deleted subfolder banks`() {
    val result = PadGridSlotManager.resolveBankOrderAfterScan(
      existingOrder = listOf(2, 1, 0),
      hasSubfolderBanks = false,
      scannedBankKeys = emptySet(),
      padBankKeys = setOf(0),
      // Bank 1 was previously a subfolder bank (now deleted and has 0 pads),
      // whereas Bank 2 is a manual empty bank created by the user.
      existingBankNames = mapOf(0 to "Root", 1 to "OldSubfolder", 2 to "ManualEmpty"),
      previousSubfolderKeys = setOf(0, 1)
    )

    assertEquals(listOf(2, 0), result)

    val cleanedNames = PadGridSlotManager.resolveBankNamesAfterScan(
      hasSubfolderBanks = false,
      scannedBankNames = emptyMap(),
      existingBankNames = mapOf(0 to "Root", 1 to "OldSubfolder", 2 to "ManualEmpty"),
      padBankKeys = setOf(0),
      previousSubfolderKeys = setOf(0, 1)
    )
    assertEquals(mapOf(0 to "Root", 2 to "ManualEmpty"), cleanedNames)
  }

  @Test
  fun `resolveBankOrderAfterScan keeps implicit empty order only when no manual banks exist`() {
    val implicit = PadGridSlotManager.resolveBankOrderAfterScan(
      existingOrder = emptyList(),
      hasSubfolderBanks = false,
      scannedBankKeys = emptySet(),
      padBankKeys = setOf(0),
      existingBankNames = emptyMap(),
      previousSubfolderKeys = emptySet()
    )
    assertEquals(emptyList<Int>(), implicit)
  }

  @Test
  fun `scheduler pad filter returns all pads beyond 24 and supports search plus bank filter`() {
    val allPads = (0 until 60).map { idx ->
      val bank = idx / 20
      val sub = when (bank) {
        0 -> "Drums"
        1 -> "Synth"
        else -> "FX"
      }
      makePad(
        id = "pad_$idx",
        name = "$sub Sound $idx",
        bankIndex = bank,
        positionIndex = idx % 20,
        subfolderName = sub,
        extension = if (idx % 2 == 0) "WAV" else "MP3"
      )
    }

    val bankPages = listOf(
      BankPageUi(bankKey = 0, title = "Drums (1)", pads = allPads.slice(0..15), canRescanSubfolder = true, subPageIndex = 0, totalSubPages = 2),
      BankPageUi(bankKey = 0, title = "Drums (2)", pads = allPads.slice(16..19), canRescanSubfolder = true, subPageIndex = 1, totalSubPages = 2),
      BankPageUi(bankKey = 1, title = "Synth (1)", pads = allPads.slice(20..35), canRescanSubfolder = true, subPageIndex = 0, totalSubPages = 2),
      BankPageUi(bankKey = 1, title = "Synth (2)", pads = allPads.slice(36..39), canRescanSubfolder = true, subPageIndex = 1, totalSubPages = 2),
      BankPageUi(bankKey = 2, title = "FX (1)", pads = allPads.slice(40..55), canRescanSubfolder = true, subPageIndex = 0, totalSubPages = 2),
      BankPageUi(bankKey = 2, title = "FX (2)", pads = allPads.slice(56..59), canRescanSubfolder = true, subPageIndex = 1, totalSubPages = 2)
    )

    val allResults = filterSchedulerPads(allPads, searchQuery = "", bankFilter = null)
    assertEquals(60, allResults.size)
    assertEquals("pad_59", allResults.last().id)

    val filters = buildSchedulerBankFilters(allPads, bankPages)
    assertEquals(listOf("Drums", "Synth", "FX"), filters.map { it.label })

    val fxOnly = filterSchedulerPads(allPads, searchQuery = "", bankFilter = filters[2])
    assertEquals(20, fxOnly.size)
    assertTrue(fxOnly.all { it.bankIndex == 2 })

    val searchAndBank = filterSchedulerPads(allPads, searchQuery = "58", bankFilter = filters[2])
    assertEquals(listOf("pad_58"), searchAndBank.map { it.id })
  }

  @Test
  fun `streaming wav export writes valid RIFF header via RandomAccessFile and deletes temp file`() {
    val tempFile = File.createTempFile("test_stream_wav_", ".wav")
    assertTrue(tempFile.exists())

    // Produce > 64 KB of PCM across multiple chunks (PCM_16BIT + PCM_FLOAT)
    val pcm16ChunkSamples = 40_000 // 80,000 bytes (> 64 KB buffer)
    val pcm16Buf = ByteBuffer.allocate(pcm16ChunkSamples * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until pcm16ChunkSamples) {
      pcm16Buf.putShort(((i % 1000) - 500).toShort())
    }
    pcm16Buf.flip()

    val floatChunkSamples = 4_100 // 8,200 bytes when converted to PCM16
    val floatBuf = ByteBuffer.allocate(floatChunkSamples * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until floatChunkSamples) {
      floatBuf.putFloat(if (i % 2 == 0) 0.5f else -0.5f)
    }
    floatBuf.flip()

    val destination = ByteArrayOutputStream()
    val durationMs = AudioWaveformAndCutEngine.exportStreamingWavWithTempFile(
      tempWavFile = tempFile,
      sampleRate = 44_100,
      channels = 1,
      destinationOutput = destination
    ) { writer ->
      writer.writePcmBuffer(pcm16Buf, AudioFormat.ENCODING_PCM_16BIT)
      writer.writePcmBuffer(floatBuf, AudioFormat.ENCODING_PCM_FLOAT)
    }

    // Temp file must be cleaned up in finally
    assertFalse(tempFile.exists())

    val expectedPcmBytes = (pcm16ChunkSamples + floatChunkSamples) * 2
    val bytes = destination.toByteArray()
    assertEquals(44 + expectedPcmBytes, bytes.size)
    assertEquals((expectedPcmBytes * 1000L) / (44_100L * 2L), durationMs)

    val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    val riff = ByteArray(4).also { header.get(it) }.toString(Charsets.US_ASCII)
    val chunkSize = header.int
    val wave = ByteArray(4).also { header.get(it) }.toString(Charsets.US_ASCII)
    val fmt = ByteArray(4).also { header.get(it) }.toString(Charsets.US_ASCII)
    val subchunk1Size = header.int
    val audioFormat = header.short.toInt()
    val channels = header.short.toInt()
    val sampleRate = header.int
    header.int // byteRate
    header.short // blockAlign
    val bitsPerSample = header.short.toInt()
    val dataTag = ByteArray(4).also { header.get(it) }.toString(Charsets.US_ASCII)
    val dataSize = header.int

    assertEquals("RIFF", riff)
    assertEquals(36 + expectedPcmBytes, chunkSize)
    assertEquals("WAVE", wave)
    assertEquals("fmt ", fmt)
    assertEquals(16, subchunk1Size)
    assertEquals(1, audioFormat)
    assertEquals(1, channels)
    assertEquals(44_100, sampleRate)
    assertEquals(16, bitsPerSample)
    assertEquals("data", dataTag)
    assertEquals(expectedPcmBytes, dataSize)

    // Verify converted float sample at start of second chunk
    val payload = ByteBuffer.wrap(bytes, 44, expectedPcmBytes).slice().order(ByteOrder.LITTLE_ENDIAN)
    assertEquals((-500).toShort(), payload.getShort(0))
    val floatStartOffset = pcm16ChunkSamples * 2
    val firstConvertedFloat = payload.getShort(floatStartOffset).toInt()
    assertTrue("Expected ~16383, got $firstConvertedFloat", firstConvertedFloat in 16380..16384)
  }

  @Test
  fun `streaming wav export deletes temp file in finally even when destination stream fails`() {
    val tempFile = File.createTempFile("test_stream_fail_", ".wav")
    assertTrue(tempFile.exists())

    val failingOutput = object : OutputStream() {
      override fun write(b: Int) {
        throw IOException("Simulated SAF write failure")
      }
    }

    val pcmBuf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
    repeat(512) { pcmBuf.putShort(1234) }
    pcmBuf.flip()

    var caught: IOException? = null
    try {
      AudioWaveformAndCutEngine.exportStreamingWavWithTempFile(
        tempWavFile = tempFile,
        sampleRate = 44_100,
        channels = 2,
        destinationOutput = failingOutput
      ) { writer ->
        writer.writePcmBuffer(pcmBuf, AudioFormat.ENCODING_PCM_16BIT)
      }
    } catch (e: IOException) {
      caught = e
    }

    assertTrue(caught != null)
    assertFalse("Temp WAV file must be deleted in finally even on stream failure", tempFile.exists())
  }
}
