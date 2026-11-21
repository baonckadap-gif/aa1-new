package com.example.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

  @get:Rule
  val baselineProfileRule = BaselineProfileRule()

  private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
      ?: "com.aistudio.soundpadpro.vqmtxr"

  @Test
  fun generateStartupProfile() {
    baselineProfileRule.collect(
      packageName = targetPackage,
      includeInStartupProfile = true
    ) {
      pressHome()
      startActivityAndWait()
    }
  }

  @Test
  fun generateGridAndAudioTriggerProfile() {
    baselineProfileRule.collect(
      packageName = targetPackage,
      includeInStartupProfile = false
    ) {
      pressHome()
      startActivityAndWait()
      ensureDemoTracksReady(device)
      switchToGrid15x15(device)

      val grid = device.wait(Until.findObject(By.res("pad_grid_scroll")), 5_000)
      if (grid != null) {
        grid.setGestureMargin(device.displayWidth / 8)
        grid.fling(Direction.DOWN)
        device.waitForIdle()
        grid.fling(Direction.UP)
        device.waitForIdle()
      }

      // Exercise tap-to-sound hot path on initial pads
      for (idx in 0..3) {
        val pad = device.findObject(By.res("pad_item_$idx"))
        pad?.click()
        device.waitForIdle(250)
      }
      device.findObject(By.res("stop_all_button"))?.click()
    }
  }

  companion object {
    internal fun ensureDemoTracksReady(device: UiDevice) {
      val existingGrid = device.wait(Until.findObject(By.res("pad_grid_scroll")), 2_000)
      if (existingGrid != null && device.findObject(By.res("pad_item_0")) != null) return

      val loadDemo = device.wait(Until.findObject(By.res("onboarding_load_demo_button")), 2_500)
        ?: device.wait(Until.findObject(By.res("onboarding_wizard_load_demo")), 1_500)
      loadDemo?.click()
      device.wait(Until.findObject(By.res("pad_item_0")), 5_000)
    }

    internal fun switchToGrid15x15(device: UiDevice) {
      val overflow = device.wait(Until.findObject(By.res("overflow_menu_button")), 3_000) ?: return
      overflow.click()
      val settingsItem = device.wait(Until.findObject(By.res("grid_settings_button")), 3_000) ?: return
      settingsItem.click()
      val gridTab = device.wait(Until.findObject(By.res("settings_tab_grid")), 3_000) ?: return
      gridTab.click()
      val preset15 = device.wait(Until.findObject(By.res("grid_preset_grid_15x15")), 3_000)
      if (preset15 != null) {
        preset15.click()
        device.waitForIdle()
      }
      device.pressBack()
      device.wait(Until.findObject(By.res("pad_grid_scroll")), 3_000)
    }
  }
}
