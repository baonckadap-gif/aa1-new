package com.example.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class SoundPadMacrobenchmark {

  @get:Rule
  val benchmarkRule = MacrobenchmarkRule()

  private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
      ?: "com.aistudio.soundpadpro.vqmtxr"

  @Test
  fun startupNoCompilation() = benchmarkStartup(CompilationMode.None())

  @Test
  fun startupWithBaselineProfile() = benchmarkStartup(
    CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)
  )

  private fun benchmarkStartup(compilationMode: CompilationMode) {
    benchmarkRule.measureRepeated(
      packageName = targetPackage,
      metrics = listOf(StartupTimingMetric()),
      compilationMode = compilationMode,
      iterations = 5,
      startupMode = StartupMode.COLD,
      setupBlock = {
        pressHome()
      }
    ) {
      startActivityAndWait()
      device.wait(Until.hasObject(By.res("pad_grid_scroll")), 5_000)
        ?: device.wait(Until.hasObject(By.res("onboarding_load_demo_button")), 2_000)
    }
  }

  @Test
  fun scrollGrid15x15() {
    benchmarkRule.measureRepeated(
      packageName = targetPackage,
      metrics = listOf(FrameTimingMetric()),
      compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
      iterations = 5,
      startupMode = StartupMode.WARM,
      setupBlock = {
        pressHome()
        startActivityAndWait()
        BaselineProfileGenerator.ensureDemoTracksReady(device)
        BaselineProfileGenerator.switchToGrid15x15(device)
      }
    ) {
      val grid = device.wait(Until.findObject(By.res("pad_grid_scroll")), 5_000)
      if (grid != null) {
        grid.setGestureMargin(device.displayWidth / 8)
        grid.fling(Direction.DOWN)
        device.waitForIdle()
        grid.fling(Direction.UP)
        device.waitForIdle()
        grid.fling(Direction.DOWN)
        device.waitForIdle()
      }
    }
  }

  @OptIn(ExperimentalMetricApi::class)
  @Test
  fun tapToSoundLatency() {
    benchmarkRule.measureRepeated(
      packageName = targetPackage,
      metrics = listOf(
        TraceSectionMetric("pad_tap"),
        TraceSectionMetric("engine_trigger"),
        TraceSectionMetric("player_start"),
        FrameTimingMetric()
      ),
      compilationMode = CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
      iterations = 5,
      startupMode = StartupMode.WARM,
      setupBlock = {
        pressHome()
        startActivityAndWait()
        BaselineProfileGenerator.ensureDemoTracksReady(device)
      }
    ) {
      for (idx in 0..3) {
        val pad = device.wait(Until.findObject(By.res("pad_item_$idx")), 2_000)
        pad?.click()
        device.waitForIdle(200)
      }
      device.findObject(By.res("stop_all_button"))?.click()
    }
  }
}
