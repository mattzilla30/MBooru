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
            val post = requireNotNull(device.wait(Until.findObject(By.desc("Post 1")), 15000))
            post.click()
            device.waitForIdle()
            device
                .wait(
                    Until.findObject(By.clazz("com.github.chrisbanes.photoview.PhotoView")),
                    10000,
                )
                ?.let {
                    it.pinchOpen(0.5f)
                    it.pinchClose(0.5f)
                }
            // The second fixture is a local video; swiping exercises player construction/release.
            device.swipe(
                device.displayWidth * 8 / 10,
                device.displayHeight / 2,
                device.displayWidth * 2 / 10,
                device.displayHeight / 2,
                30,
            )
            device.waitForIdle()
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
