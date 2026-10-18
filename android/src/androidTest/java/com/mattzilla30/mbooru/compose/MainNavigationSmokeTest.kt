package com.mattzilla30.mbooru.compose

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
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.title_settings)).performClick()
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertExists().performClick()
        Espresso.pressBack()
        compose.activityRule.scenario.recreate()
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .assertIsDisplayed()
    }
}
