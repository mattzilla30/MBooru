package com.mattzilla30.mbooru.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.activity.AboutActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<AboutActivity>()

    @Test
    fun aboutOpensSearchableLicensesAndBackReturnsToList() {
        val label = compose.activity.getString(R.string.about_open_source_licenses)
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("about-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("about-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("license-search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("license-search").performTextReplacement("PhotoView (Apache")
        compose.onNodeWithText("PhotoView (Apache 2.0)").performClick()
        compose.onNodeWithTag("license-text").assertIsDisplayed()
        compose.onNode(hasText("TERMS AND CONDITIONS", substring = true)).assertExists()
        Espresso.pressBack()
        compose.onNodeWithTag("license-list").assertIsDisplayed()
        compose.onNodeWithText("PhotoView (Apache 2.0)").assertExists()
    }
}
