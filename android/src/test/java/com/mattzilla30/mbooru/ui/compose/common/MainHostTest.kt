package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performTextReplacement
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.ui.activity.MainActivity
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [28], qualifiers = "en-rUS-notnight")
@LooperMode(LooperMode.Mode.PAUSED)
class MainHostTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun selectFullCapabilitySite() {
        compose.runOnUiThread { kotlinx.coroutines.runBlocking {
            val dao = org.koin.core.context.GlobalContext.get().get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
            val uid = dao.getAll().firstOrNull { it.type == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE }?.uid
                ?: dao.insert(com.mattzilla30.mbooru.data.model.common.Booru(
                    name = "All tabs", scheme = "https", host = "moe.invalid",
                    type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE))
            com.mattzilla30.mbooru.app.Settings.activatedBooruUid = uid
            compose.activity.forceShowNavBar()
        } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("browse-tab-tags").fetchSemanticsNodes().isNotEmpty()
        }
    }
    @After fun closeDependencies() { stopKoin() }
    @Test fun `navigation view and drawer remain usable after recreation`() {
        val description = compose.activity.getString(R.string.navigation_drawer_open)
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.title_settings)).assertIsDisplayed()
    }
    @Test fun `tab search survives switching away and recreating the activity`() {
        compose.onNodeWithTag("browse-tab-tags").performClick().assertIsSelected()
        compose.onNodeWithTag("browse-query-toggle").performClick()
        compose.onNodeWithTag("browse-query-input").performTextReplacement("saved search")
        compose.onNodeWithTag("browse-tab-pools").performClick().assertIsSelected()
        compose.onNodeWithTag("browse-tab-tags").performClick().assertIsSelected()
        compose.onNodeWithTag("browse-query-input").assertTextEquals("saved search")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("browse-tab-tags").assertIsSelected()
        compose.onNodeWithTag("browse-query-input").assertTextEquals("saved search")
    }

    @Test fun `back from a secondary tab returns to posts`() {
        compose.onNodeWithTag("browse-tab-tags").performClick().assertIsSelected()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("browse-tab-posts").assertIsSelected()
    }

    @Test fun `switching to a posts-only site removes unsupported tabs and restores safely`() {
        compose.onNodeWithTag("browse-tab-tags").performClick().assertIsSelected()
        compose.runOnUiThread { kotlinx.coroutines.runBlocking {
            val dao = org.koin.core.context.GlobalContext.get().get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
            val uid = dao.insert(com.mattzilla30.mbooru.data.model.common.Booru(
                name = "Posts only", scheme = "https", host = "shimmie.invalid",
                type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE))
            com.mattzilla30.mbooru.app.Settings.activatedBooruUid = uid
        } }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("browse-tab-tags").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("browse-tab-posts").assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("browse-tab-tags").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("browse-tab-posts").assertIsSelected()
    }

    @Test fun `paging view model is retained when switching tabs`() {
        compose.onNodeWithTag("browse-tab-tags").performClick()
        lateinit var original: com.mattzilla30.mbooru.ui.viewmodel.TagViewModel
        compose.runOnIdle {
            val owner = (compose.activity.activeBrowse as com.mattzilla30.mbooru.ui.compose.browse.BrowseController).owner
            original = androidx.lifecycle.ViewModelProvider(owner)[com.mattzilla30.mbooru.ui.viewmodel.TagViewModel::class.java]
        }
        compose.onNodeWithTag("browse-tab-pools").performClick()
        compose.onNodeWithTag("browse-tab-tags").performClick()
        compose.runOnIdle {
            val owner = (compose.activity.activeBrowse as com.mattzilla30.mbooru.ui.compose.browse.BrowseController).owner
            org.junit.Assert.assertSame(original,
                androidx.lifecycle.ViewModelProvider(owner)[com.mattzilla30.mbooru.ui.viewmodel.TagViewModel::class.java])
        }
    }

    @Test fun `free drawer has no purchase action and can switch beyond three sites`() {
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.navigation_drawer_open)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.title_settings)).assertIsDisplayed()
        compose.onNodeWithText("Remove ads & feature unlock").assertDoesNotExist()
        compose.onNodeWithTag("main-drawer-profile").assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithTag("main-drawer-list").performScrollToNode(hasText("Safebooru"))
        compose.onNodeWithText("Safebooru")
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(10000) {
            kotlinx.coroutines.runBlocking { com.mattzilla30.mbooru.app.Settings.activatedBooruUid.let { uid ->
                org.koin.core.context.GlobalContext.get().get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
                    .getBooruByUid(uid)?.host == "safebooru.org"
            } }
        }
        compose.onNodeWithTag("browse-tab-tags").assertIsDisplayed()
        compose.onNodeWithTag("browse-tab-artists").assertDoesNotExist()
    }

}
