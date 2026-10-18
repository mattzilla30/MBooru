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

/** Run on a physical device; emulator timings are not release performance evidence. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BrowseBenchmark {
    @get:Rule val benchmark = MacrobenchmarkRule()

    @Test
    fun coldStartupAndTabs() =
        benchmark.measureRepeated(
            packageName = "com.mattzilla30.mbooru",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    device.executeShellCommand(
                        "pm grant com.mattzilla30.mbooru android.permission.POST_NOTIFICATIONS"
                    )
                pressHome()
            },
        ) {
            startActivityAndWait()
            requireNotNull(device.wait(Until.findObject(By.res("browse-tab-tags")), 10000)).click()
            device.waitForIdle()
            requireNotNull(device.wait(Until.findObject(By.res("browse-tab-posts")), 10000)).click()
        }
}
