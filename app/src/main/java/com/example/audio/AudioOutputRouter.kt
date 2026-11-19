package com.example.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.domain.model.AudioOutputDeviceUi

object AudioOutputRouter {

  fun listOutputDevices(context: Context): List<AudioOutputDeviceUi> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices
      .filter { it.isSink }
      .distinctBy { "${it.type}_${it.productName}_${extractAddress(it)}" }
      .map { device ->
        val (typeLabel, isHeadphone) = describeDeviceType(device.type)
        val rawName = device.productName?.toString()?.trim().orEmpty()
        val displayName = if (rawName.isNotEmpty() && rawName != Build.MODEL) {
          "$rawName ($typeLabel)"
        } else {
          typeLabel
        }
        AudioOutputDeviceUi(
          id = device.id,
          name = displayName,
          typeLabel = typeLabel,
          isHeadphoneLike = isHeadphone,
          deviceType = device.type,
          productName = rawName,
          address = extractAddress(device)
        )
      }
  }

  fun findOutputDeviceById(context: Context, deviceId: Int?): AudioDeviceInfo? {
    if (deviceId == null) return null
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
  }

  /**
   * Matches a saved audio output device by volatile [preferredId] OR by stable hardware identity
   * ([preferredProductName], [preferredType], [preferredAddress]) so USB/Bluetooth devices
   * automatically reconnect even when Android assigns a new AudioDeviceInfo.id.
   */
  fun findBestMatchingOutputDevice(
    devices: List<AudioOutputDeviceUi>,
    preferredId: Int?,
    preferredProductName: String?,
    preferredType: Int?,
    preferredAddress: String?
  ): AudioOutputDeviceUi? {
    if (devices.isEmpty()) return null
    val cleanAddr = preferredAddress?.trim().orEmpty()
    val cleanProd = preferredProductName?.trim().orEmpty()

    // 1. Exact ID match (verified against saved type if known so recycled IDs don't mismatch hardware)
    if (preferredId != null) {
      val byId = devices.firstOrNull { it.id == preferredId }
      if (byId != null && (preferredType == null || byId.deviceType == preferredType)) {
        return byId
      }
    }

    // 2. Stable type + address match (Android 9+ Bluetooth MAC / USB hardware address)
    if (preferredType != null && cleanAddr.isNotEmpty()) {
      devices.firstOrNull {
        it.deviceType == preferredType && it.address.equals(cleanAddr, ignoreCase = true)
      }?.let { return it }
    }

    // 3. Stable type + product name match
    if (preferredType != null && cleanProd.isNotEmpty()) {
      devices.firstOrNull {
        it.deviceType == preferredType && it.productName.equals(cleanProd, ignoreCase = true)
      }?.let { return it }
    }

    // 4. Unique / first product name match (handles minor driver type transitions)
    if (cleanProd.isNotEmpty()) {
      devices.firstOrNull {
        it.productName.equals(cleanProd, ignoreCase = true)
      }?.let { return it }
    }

    // 5. Same external peripheral type when only a single device of that type is attached
    if (preferredType != null &&
      preferredType != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER &&
      preferredType != AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
    ) {
      val sameType = devices.filter { it.deviceType == preferredType }
      if (sameType.size == 1 && cleanProd.isEmpty() && cleanAddr.isEmpty()) {
        return sameType.first()
      }
    }

    return null
  }

  fun findBestAudioDeviceInfo(
    context: Context,
    preferredId: Int?,
    preferredProductName: String?,
    preferredType: Int?,
    preferredAddress: String?
  ): AudioDeviceInfo? {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
    val matchedUi = findBestMatchingOutputDevice(
      devices = listOutputDevices(context),
      preferredId = preferredId,
      preferredProductName = preferredProductName,
      preferredType = preferredType,
      preferredAddress = preferredAddress
    ) ?: return findOutputDeviceById(context, preferredId)
    return rawDevices.firstOrNull { it.id == matchedUi.id }
  }

  fun registerDeviceCallback(
    context: Context,
    callback: AudioDeviceCallback,
    handler: Handler = Handler(Looper.getMainLooper())
  ): Boolean {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return runCatching {
      audioManager.registerAudioDeviceCallback(callback, handler)
      true
    }.getOrDefault(false)
  }

  fun unregisterDeviceCallback(
    context: Context,
    callback: AudioDeviceCallback
  ) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
  }

  private fun extractAddress(device: AudioDeviceInfo): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      runCatching { device.address.trim() }.getOrDefault("")
    } else {
      ""
    }
  }

  private fun describeDeviceType(type: Int): Pair<String, Boolean> {
    return when (type) {
      AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker" to false
      AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece" to false
      AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset" to true
      AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones" to true
      AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio" to true
      AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Call" to true
      AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio" to true
      AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Accessory" to true
      AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset" to true
      AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI Output" to false
      AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE Audio" to true
      else -> "Audio Output" to false
    }
  }
}
