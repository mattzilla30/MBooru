package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.activity.SettingsActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [28], qualifiers = "en-rUS-notnight")
@LooperMode(LooperMode.Mode.PAUSED)
class FragmentHostTest {
    @org.junit.After
    fun closeDependencies() {
        org.koin.core.context.stopKoin()
    }

    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test
    fun `fragment content is attached after activity recreation`() {
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.GRID_ROUNDED_KEY))
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertIsDisplayed()
    }

    @Test
    fun `compose up button finishes settings`() {
        val activity = compose.activity
        val up = activity.getString(androidx.appcompat.R.string.abc_action_bar_up_description)
        compose.onNodeWithContentDescription(up).assertIsDisplayed().performClick()
        assertTrue(activity.isFinishing)
    }
}
