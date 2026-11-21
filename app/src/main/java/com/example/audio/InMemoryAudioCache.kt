package com.example.audio

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.example.domain.model.RamCacheStats
import com.example.domain.model.SoundPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class InMemoryAudioCache(private val context: Context) {

  companion object {
    const val MAX_PRELOAD_FILE_BYTES = 4L * 1024L * 1024L // 4 MB per short sample
    private const val MAX_CACHE_BYTES = 64 * 1024 * 1024 // 64 MB total RAM budget
  }

  private val evictedCounter = AtomicInteger(0)
  private val skippedCounter = AtomicInteger(0)

  private val cache = object : LruCache<String, ByteArray>(MAX_CACHE_BYTES) {
    override fun sizeOf(key: String, value: ByteArray): Int = value.size

    override fun entryRemoved(
      evicted: Boolean,
      key: String,
      oldValue: ByteArray,
      newValue: ByteArray?
    ) {
      if (evicted && newValue == null) {
        evictedCounter.incrementAndGet()
      }
    }
  }

  private val _statsFlow = MutableStateFlow(computeStatsLocked())
  val statsFlow: StateFlow<RamCacheStats> = _statsFlow.asStateFlow()

  fun getStats(): RamCacheStats = _statsFlow.value

  private fun isLowMemoryDevice(): Boolean {
    return runCatching {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return@runCatching false
      val info = ActivityManager.MemoryInfo()
      am.getMemoryInfo(info)
      am.isLowRamDevice || info.lowMemory
    }.getOrDefault(false)
  }

  private fun computeStatsLocked(): RamCacheStats {
    val snapshot = runCatching { cache.snapshot() }.getOrDefault(emptyMap())
    return RamCacheStats(
      usedBytes = cache.size().toLong().coerceAtLeast(0L),
      maxBytes = cache.maxSize().toLong().coerceAtLeast(1L),
      cachedPadCount = snapshot.size,
      evictedCount = evictedCounter.get(),
      skippedCount = skippedCounter.get(),
      isLowMemoryDevice = isLowMemoryDevice()
    )
  }

  private fun publishStats() {
    _statsFlow.value = computeStatsLocked()
  }

  suspend fun preloadShortSamples(pads: List<SoundPad>): Set<String> = withContext(Dispatchers.IO) {
    val preloadedIds = mutableSetOf<String>()
    val lowMem = isLowMemoryDevice()
    val effectiveBudget = if (lowMem) (MAX_CACHE_BYTES / 2) else MAX_CACHE_BYTES

    for (pad in pads) {
      if (pad.sizeBytes in 1..MAX_PRELOAD_FILE_BYTES) {
        val existing = cache.get(pad.uriString)
        if (existing != null) {
          preloadedIds.add(pad.id)
          continue
        }
        if (lowMem && cache.size() + pad.sizeBytes > effectiveBudget) {
          skippedCounter.incrementAndGet()
          continue
        }
        val bytes = try {
          readBytesSafely(Uri.parse(pad.uriString), pad.sizeBytes)
        } catch (_: OutOfMemoryError) {
          skippedCounter.incrementAndGet()
          continue
        }
        if (bytes != null && bytes.isNotEmpty()) {
          cache.put(pad.uriString, bytes)
          preloadedIds.add(pad.id)
        }
      }
    }
    publishStats()
    preloadedIds
  }

  fun isCached(uriString: String): Boolean = cache.get(uriString) != null

  fun clear() {
    cache.evictAll()
    evictedCounter.set(0)
    skippedCounter.set(0)
    publishStats()
  }

  private fun readBytesSafely(uri: Uri, expectedSize: Long): ByteArray? {
    if (expectedSize > MAX_PRELOAD_FILE_BYTES) return null
    return runCatching {
      if (uri.scheme == "file" && uri.path != null) {
        val file = java.io.File(uri.path!!)
        if (file.exists() && file.length() <= MAX_PRELOAD_FILE_BYTES) file.readBytes() else null
      } else {
        context.contentResolver.openInputStream(uri)?.use { input ->
          val data = input.readBytes()
          if (data.size <= MAX_PRELOAD_FILE_BYTES) data else null
        }
      }
    }.getOrNull()
  }

  @OptIn(UnstableApi::class)
  fun createDataSourceFactory(): DataSource.Factory {
    val fallbackFactory = DefaultDataSource.Factory(context)
    return DataSource.Factory {
      RamFirstDataSource(cache, fallbackFactory.createDataSource())
    }
  }

  @OptIn(UnstableApi::class)
  private class RamFirstDataSource(
    private val cache: LruCache<String, ByteArray>,
    private val fallback: DataSource
  ) : DataSource {

    private var activeDelegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
      fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
      val cachedBytes = cache.get(dataSpec.uri.toString())
      val delegate = if (cachedBytes != null) {
        ByteArrayDataSource(cachedBytes)
      } else {
        fallback
      }
      activeDelegate = delegate
      return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
      return activeDelegate?.read(buffer, offset, length) ?: -1
    }

    override fun getUri(): Uri? = activeDelegate?.uri

    override fun close() {
      try {
        activeDelegate?.close()
      } finally {
        activeDelegate = null
      }
    }
  }
}
