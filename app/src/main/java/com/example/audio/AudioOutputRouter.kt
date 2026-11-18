package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.example.domain.model.AudioOutputDeviceUi

object AudioOutputRouter {

  fun listOutputDevices(context: Context): List<AudioOutputDeviceUi> {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    return devices
      .filter { it.isSink }
      .distinctBy { "${it.type}_${it.productName}" }
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
          isHeadphoneLike = isHeadphone
        )
      }
  }

  fun findOutputDeviceById(context: Context, deviceId: Int?): AudioDeviceInfo? {
    if (deviceId == null) return null
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
    return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == deviceId }
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
