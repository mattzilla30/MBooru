package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.mattzilla30.mbooru.data.licenses.LicenseCatalog
import com.mattzilla30.mbooru.ui.activity.LicensesActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = SearchFixtureApp::class, sdk = [28], qualifiers = "en-rUS-notnight")
@LooperMode(LooperMode.Mode.PAUSED)
class LicensesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<LicensesActivity>()

    @After
    fun close() {
        compose.activityRule.scenario.close()
        stopKoin()
    }

    @Test
    fun bundledCatalogOpensFullTextAndRestoresDetailAndBackToSearch() {
        val catalog = LicenseCatalog.load(compose.activity)
        // The OSS plugin deliberately bundles a placeholder in debug builds.
        assertTrue(catalog.any { it.name == "Debug License Info" })
        assertTrue(catalog.any { it.name.contains("Flexbooru") })
        assertTrue(catalog.any { it.name.contains("Boorusphere") })
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("license-search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("license-search").performTextReplacement("PhotoView (Apache")
        compose.onNodeWithText("PhotoView (Apache 2.0)").performClick()
        compose.onNodeWithTag("license-text").assertIsDisplayed()
        compose.onNode(hasText("TERMS AND CONDITIONS", substring = true)).assertExists()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("license-text").fetchSemanticsNodes().isNotEmpty()
        }
        compose.runOnIdle { compose.activity.onAction(AppActionKey.Back) }
        compose.onNodeWithTag("license-list").assertIsDisplayed()
        compose.onNodeWithTag("license-search").assertTextContains("PhotoView (Apache")
    }
}
