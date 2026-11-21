package com.example.util

import android.app.ActivityManager
import android.content.Context

data class DeviceMemoryInfo(
  val isLowRamDevice: Boolean = false,
  val memoryClassMb: Int = 256,
  val isSystemLowMemory: Boolean = false
) {
  companion object {
    const val LOW_MEMORY_CLASS_THRESHOLD_MB = 128
    const val LOW_RAM_ACTIVE_PAD_WARNING_THRESHOLD = 12
  }

  val isLowRamProfile: Boolean
    get() = isLowRamDevice || memoryClassMb <= LOW_MEMORY_CLASS_THRESHOLD_MB || isSystemLowMemory

  fun shouldWarnActivePads(activePadCount: Int): Boolean {
    return isLowRamProfile && activePadCount > LOW_RAM_ACTIVE_PAD_WARNING_THRESHOLD
  }
}

object DeviceMemoryHelper {
  fun inspect(context: Context): DeviceMemoryInfo {
    return runCatching {
      val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        ?: return@runCatching DeviceMemoryInfo()
      val memInfo = ActivityManager.MemoryInfo()
      am.getMemoryInfo(memInfo)
      DeviceMemoryInfo(
        isLowRamDevice = am.isLowRamDevice,
        memoryClassMb = am.memoryClass.coerceAtLeast(16),
        isSystemLowMemory = memInfo.lowMemory
      )
    }.getOrDefault(DeviceMemoryInfo())
  }

  fun shouldWarnLowRamActivePads(
    isLowRamDevice: Boolean,
    memoryClassMb: Int,
    activePadCount: Int,
    isSystemLowMemory: Boolean = false
  ): Boolean {
    return DeviceMemoryInfo(
      isLowRamDevice = isLowRamDevice,
      memoryClassMb = memoryClassMb,
      isSystemLowMemory = isSystemLowMemory
    ).shouldWarnActivePads(activePadCount)
  }
}
