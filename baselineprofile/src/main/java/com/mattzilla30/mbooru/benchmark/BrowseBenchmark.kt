package com.mattzilla30.mbooru.benchmark

import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Use a fixed physical device; emulator timings are not phone performance evidence. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BrowseBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()
    private val packageName = "com.mattzilla30.mbooru"

    @Test fun coldStartupAndTabs() = startup(StartupMode.COLD)

    @Test fun warmStartupAndTabs() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) =
        benchmark.measureRepeated(
            packageName = packageName,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = mode,
            iterations = 5,
            setupBlock = {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    device.executeShellCommand(
                        "pm grant $packageName android.permission.POST_NOTIFICATIONS"
                    )
                if (mode == StartupMode.WARM) startActivityAndWait()
                pressHome()
            },
        ) {
            startActivityAndWait()
            requireNotNull(device.wait(Until.findObject(By.res("browse-tab-tags")), 10000)).click()
            device.waitForIdle()
            requireNotNull(device.wait(Until.findObject(By.res("browse-tab-posts")), 10000)).click()
        }

    @Test
    fun sustainedGridScrolling() =
        benchmark.measureRepeated(
            packageName = packageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            setupBlock = {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    device.executeShellCommand(
                        "pm grant $packageName android.permission.POST_NOTIFICATIONS"
                    )
                startActivityAndWait()
                requireNotNull(device.wait(Until.findObject(By.res("post_grid")), 10000))
                repeat(12) {
                    device.swipe(
                        device.displayWidth / 2,
                        device.displayHeight / 4,
                        device.displayWidth / 2,
                        device.displayHeight * 3 / 4,
                        12,
                    )
                }
                device.waitForIdle()
            },
        ) {
            repeat(10) {
                device.swipe(
                    device.displayWidth / 2,
                    device.displayHeight * 3 / 4,
                    device.displayWidth / 2,
                    device.displayHeight / 4,
                    20,
                )
            }
            device.waitForIdle()
        }
}
