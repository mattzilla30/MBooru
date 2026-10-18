package com.mattzilla30.mbooru.compose

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.activity.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationSmokeTest {
    val compose = createAndroidComposeRule<MainActivity>()
    private val notificationPermission =
        org.junit.rules.TestRule { statement, description ->
            if (android.os.Build.VERSION.SDK_INT >= 33)
                androidx.test.rule.GrantPermissionRule.grant(
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                    .apply(statement, description)
            else statement
        }
    @get:Rule
    val rules: org.junit.rules.TestRule =
        org.junit.rules.RuleChain.outerRule(notificationPermission).around(compose)

    @Test
    fun drawerSettingsAndActivityRecreationKeepWorking() {
        awaitDrawer()
        screenshot("sphere-home")
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .performClick()
        screenshot("sphere-drawer")
        val settings = compose.onNodeWithText(compose.activity.getString(R.string.title_settings))
        try {
            settings.assertIsDisplayed()
        } catch (_: AssertionError) {
            settings.performScrollTo()
        }
        settings.performClick()
        screenshot("sphere-settings")
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.GRID_ROUNDED_KEY))
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertExists().performClick()
        Espresso.pressBack()
        compose.activityRule.scenario.recreate()
        awaitDrawer()
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val instrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        val directory =
            androidx.test.platform.app.InstrumentationRegistry.getArguments()
                .getString("additionalTestOutputDir") ?: return
        val output =
            java.io.File(directory, "screenshots/$name.png").apply { parentFile!!.mkdirs() }
        output.outputStream().use {
            compose
                .onRoot()
                .captureToImage()
                .asAndroidBitmap()
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun awaitDrawer() {
        val description = compose.activity.getString(R.string.navigation_drawer_open)
        compose.waitUntil(20000) {
            compose
                .onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }
}
