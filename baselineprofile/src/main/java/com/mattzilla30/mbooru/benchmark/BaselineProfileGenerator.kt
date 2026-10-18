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
    @Test fun startupAndTabNavigation() = profile.collect(
        packageName = "com.mattzilla30.mbooru", includeInStartupProfile = true,
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 33) device.executeShellCommand("pm grant com.mattzilla30.mbooru android.permission.POST_NOTIFICATIONS")
        pressHome()
        startActivityAndWait()
        val tags = requireNotNull(device.wait(Until.findObject(By.res("browse-tab-tags")), 10000))
        tags.click()
        device.waitForIdle()
        requireNotNull(device.wait(Until.findObject(By.res("browse-tab-posts")), 10000)).click()
        device.waitForIdle()
    }
}
