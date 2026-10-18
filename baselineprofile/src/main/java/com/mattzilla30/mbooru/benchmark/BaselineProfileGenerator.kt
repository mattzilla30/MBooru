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
            var clickable = post
            while (!clickable.isClickable && clickable.parent != null) clickable = clickable.parent
            clickable.click()
            check(
                device.wait(
                    Until.hasObject(By.clazz("com.github.chrisbanes.photoview.PhotoView")),
                    15000,
                )
            ) {
                "Still-image detail did not open"
            }
            device.findObject(By.clazz("com.github.chrisbanes.photoview.PhotoView")).apply {
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
            var videoAction = requireNotNull(video) { "Offline video post was not reachable" }
            while (!videoAction.isClickable && videoAction.parent != null) videoAction =
                videoAction.parent
            videoAction.click()
            check(device.wait(Until.hasObject(By.clazz("androidx.media3.ui.PlayerView")), 15000)) {
                "Video detail did not open"
            }
            device.pressBack()
            device.waitForIdle()
            val tags =
                requireNotNull(device.wait(Until.findObject(By.res("browse-tab-tags")), 10000))
            tags.click()
            device.waitForIdle()
            requireNotNull(device.wait(Until.findObject(By.res("browse-tab-posts")), 10000)).click()
            device.waitForIdle()
            requireNotNull(device.wait(Until.findObject(By.desc("Open navigation drawer")), 10000))
                .click()
            requireNotNull(device.wait(Until.findObject(By.text("Settings")), 10000)).click()
            device.waitForIdle()
            device.pressBack()
        }
}
