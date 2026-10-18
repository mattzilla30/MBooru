package com.mattzilla30.mbooru.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule val profile = BaselineProfileRule()

    @Test
    fun startupBrowseDetailAndSettings() =
        profile.collect(
            packageName = "com.mattzilla30.mbooru",
            includeInStartupProfile = true,
            filterPredicate = { !it.contains("/mbooru/benchmark/") },
        ) {
            if (android.os.Build.VERSION.SDK_INT >= 33)
                device.executeShellCommand(
                    "pm grant com.mattzilla30.mbooru android.permission.POST_NOTIFICATIONS"
                )
            pressHome()
            startActivityAndWait()
            // Scroll restoration is a product requirement: later collection iterations may
            // legitimately reopen away from post 1. Choose a fully visible still image.
            check(device.wait(Until.hasObject(By.res("post_grid")), 30000))
            check(device.wait(Until.hasObject(By.descStartsWith("Post ")), 30000))
            device.waitForIdle()
            val post =
                device.findObjects(By.descStartsWith("Post ")).firstOrNull {
                    it.contentDescription != "Post 2" &&
                        it.visibleBounds.top > device.displayHeight / 5 &&
                        it.visibleBounds.height() > 60
                }
                    ?: run {
                        val instrumentation =
                            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                        val output =
                            java.io.File(
                                androidx.test.platform.app.InstrumentationRegistry.getArguments()
                                    .getString("additionalTestOutputDir")
                                    ?: instrumentation.context
                                        .getExternalFilesDir(null)!!
                                        .absolutePath
                            )
                        output.mkdirs()
                        device.takeScreenshot(java.io.File(output, "profile-missing-post.png"))
                        val hierarchy = java.io.File(output, "profile-missing-post.xml")
                        device.dumpWindowHierarchy(hierarchy)
                        error("Offline post did not appear: " + hierarchy.readText())
                    }
            device.clickFresh(By.desc(post.contentDescription))
            check(
                device.wait(
                    Until.hasObject(By.res("com.mattzilla30.mbooru", "post_media_image")),
                    15000,
                )
            ) {
                "Still-image detail did not open"
            }
            device.findObject(By.res("com.mattzilla30.mbooru", "post_media_image")).apply {
                pinchOpen(0.5f)
                pinchClose(0.5f)
            }
            device.pressBack()
            check(device.wait(Until.hasObject(By.res("post_grid")), 10000))
            val grid = device.findObject(By.res("post_grid"))
            repeat(5) { grid.scroll(androidx.test.uiautomator.Direction.UP, 1f) }
            device.waitForIdle()
            var video = device.findObject(By.desc("Post 2"))
            var attempts = 0
            while (video == null && attempts++ < 12) {
                if (!grid.scroll(androidx.test.uiautomator.Direction.DOWN, .8f)) break
                device.waitForIdle()
                video = device.findObject(By.desc("Post 2"))
            }
            requireNotNull(video) { "Offline video post was not reachable" }
            device.clickFresh(By.desc("Post 2"))
            check(
                device.wait(Until.hasObject(By.res("com.mattzilla30.mbooru", "player_view")), 15000)
            ) {
                "Video detail did not open"
            }
            device.pressBack()
            device.waitForIdle()
            device.clickFresh(By.res("browse-tab-tags"))
            device.waitForIdle()
            device.clickFresh(By.res("browse-tab-posts"))
            device.waitForIdle()
            device.clickFresh(By.desc("Open navigation drawer"))
            device.clickFresh(By.text("Settings"))
            device.waitForIdle()
            device.pressBack()
        }

    /** Compose can replace a semantics node between a successful query and the click. */
    private fun androidx.test.uiautomator.UiDevice.clickFresh(
        selector: androidx.test.uiautomator.BySelector
    ) {
        val deadline = android.os.SystemClock.uptimeMillis() + 15000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                var action = wait(Until.findObject(selector), 1000) ?: continue
                while (!action.isClickable && action.parent != null) action = action.parent
                action.click()
                return
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                android.os.SystemClock.sleep(100)
            }
        }
        error("Control did not remain available for a click: $selector")
    }
}
