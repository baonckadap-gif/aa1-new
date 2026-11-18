package com.example.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object MiuiOptimizationHelper {

  fun isXiaomiDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    val brand = Build.BRAND.lowercase()
    return manufacturer.contains("xiaomi") ||
      manufacturer.contains("redmi") ||
      manufacturer.contains("poco") ||
      brand.contains("xiaomi") ||
      brand.contains("redmi") ||
      brand.contains("poco") ||
      getSystemProperty("ro.miui.ui.version.name").isNotBlank() ||
      getSystemProperty("ro.mi.os.version.name").isNotBlank()
  }

  fun detectOsLabel(): String {
    val hyperOs = getSystemProperty("ro.mi.os.version.name")
    val miui = getSystemProperty("ro.miui.ui.version.name")
    return when {
      hyperOs.isNotBlank() -> "HyperOS ($hyperOs)"
      miui.isNotBlank() -> "MIUI ($miui)"
      isXiaomiDevice() -> "Xiaomi (${Build.MODEL})"
      else -> "Android ${Build.VERSION.RELEASE}"
    }
  }

  fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
  }

  fun openAutostartSettings(context: Context) {
    val candidates = listOf(
      Intent().apply {
        component = ComponentName(
          "com.miui.securitycenter",
          "com.miui.permcenter.autostart.AutoStartManagementActivity"
        )
      },
      Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)
    )
    launchFirstValidOrAppDetails(context, candidates)
  }

  fun openBatterySaverSettings(context: Context) {
    val candidates = listOf(
      Intent().apply {
        component = ComponentName(
          "com.miui.powerkeeper",
          "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
        )
        putExtra("package_name", context.packageName)
        putExtra("package_label", "SoundPad Pro")
      },
      Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
      }
    )
    launchFirstValidOrAppDetails(context, candidates)
  }

  fun openOtherPermissionsSettings(context: Context) {
    val candidates = listOf(
      Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        setClassName(
          "com.miui.securitycenter",
          "com.miui.permcenter.permissions.PermissionsEditorActivity"
        )
        putExtra("extra_pkgname", context.packageName)
      },
      Intent("miui.intent.action.APP_PERM_EDITOR").apply {
        putExtra("extra_pkgname", context.packageName)
      }
    )
    launchFirstValidOrAppDetails(context, candidates)
  }

  fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
      data = Uri.fromParts("package", context.packageName, null)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
  }

  private fun launchFirstValidOrAppDetails(context: Context, intents: List<Intent>) {
    for (intent in intents) {
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      val launched = runCatching {
        context.startActivity(intent)
        true
      }.getOrDefault(false)
      if (launched) return
    }
    openAppDetailsSettings(context)
  }

  private fun getSystemProperty(propName: String): String {
    return runCatching {
      val systemProperties = Class.forName("android.os.SystemProperties")
      val getMethod = systemProperties.getMethod("get", String::class.java)
      getMethod.invoke(null, propName) as? String ?: ""
    }.getOrDefault("")
  }
}
