package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.ui.activity.MainActivity
import org.junit.After
import org.junit.Before
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

    @Before
    fun selectFullCapabilitySite() {
        kotlinx.coroutines.runBlocking {
            org.koin.core.context.GlobalContext.get()
                .get<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
                .awaitReady()
        }
        compose.runOnUiThread {
            kotlinx.coroutines.runBlocking {
                val dao =
                    org.koin.core.context.GlobalContext.get()
                        .get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
                val uid =
                    dao.getAll()
                        .firstOrNull { it.type == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE }
                        ?.uid
                        ?: dao.insert(
                            com.mattzilla30.mbooru.data.model.common.Booru(
                                name = "All tabs",
                                scheme = "https",
                                host = "moe.invalid",
                                type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE,
                            )
                        )
                compose.activity.settings.activatedBooruUid = uid
                compose.activity.forceShowNavBar()
            }
        }
        compose.waitUntil(10000) {
            compose.onAllNodesWithTag("sphere-bottom-search").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun explore(title: Int) {
        compose.runOnUiThread { compose.activity.openDrawer() }
        val list = compose.onNodeWithTag("main-drawer-list")
        list.performScrollToNode(hasText(compose.activity.getString(R.string.sphere_explore)))
        val target = compose.activity.getString(title)
        if (compose.onAllNodesWithText(target).fetchSemanticsNodes().isEmpty()) {
            compose
                .onNodeWithText(compose.activity.getString(R.string.sphere_explore))
                .performClick()
        }
        list.performScrollToNode(hasText(target))
        compose.onNodeWithText(target).performClick()
        compose.waitForIdle()
    }

    @After
    fun closeDependencies() {
        stopKoin()
    }

    @Test
    fun `navigation view and drawer remain usable after recreation`() {
        val description = compose.activity.getString(R.string.navigation_drawer_open)
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
        compose
            .onNodeWithText(compose.activity.getString(R.string.title_settings))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `tab search survives switching away and recreating the activity`() {
        explore(R.string.title_tags)
        compose.onNodeWithTag("browse-query-toggle").performClick()
        compose.onNodeWithTag("browse-query-input").performTextReplacement("saved search")
        explore(R.string.title_pools)
        explore(R.string.title_tags)
        compose.onNodeWithTag("browse-query-input").assertTextEquals("saved search")
        compose.activityRule.scenario.recreate()
        org.junit.Assert.assertTrue(
            compose.activity.activeBrowse
                is com.mattzilla30.mbooru.ui.compose.browse.TagScreenController
        )
        compose.onNodeWithTag("browse-query-input").assertTextEquals("saved search")
    }

    @Test
    fun `back from a secondary tab returns to posts`() {
        explore(R.string.title_tags)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        org.junit.Assert.assertTrue(
            compose.activity.activeBrowse
                is com.mattzilla30.mbooru.ui.compose.browse.PostScreenController
        )
    }

    @Test
    fun `switching to a posts-only site removes unsupported tabs and restores safely`() {
        explore(R.string.title_tags)
        compose.runOnUiThread {
            kotlinx.coroutines.runBlocking {
                val dao =
                    org.koin.core.context.GlobalContext.get()
                        .get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
                val uid =
                    dao.insert(
                        com.mattzilla30.mbooru.data.model.common.Booru(
                            name = "Posts only",
                            scheme = "https",
                            host = "shimmie.invalid",
                            type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE,
                        )
                    )
                compose.activity.settings.activatedBooruUid = uid
            }
        }
        compose.waitUntil(10000) {
            compose.activity.activeBrowse is
                com.mattzilla30.mbooru.ui.compose.browse.PostScreenController
        }
        org.junit.Assert.assertTrue(
            compose.activity.activeBrowse
                is com.mattzilla30.mbooru.ui.compose.browse.PostScreenController
        )
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            compose.activity.activeBrowse is
                com.mattzilla30.mbooru.ui.compose.browse.PostScreenController
        }
        org.junit.Assert.assertTrue(
            compose.activity.activeBrowse
                is com.mattzilla30.mbooru.ui.compose.browse.PostScreenController
        )
    }

    @Test
    fun `paging view model is retained when switching tabs`() {
        explore(R.string.title_tags)
        lateinit var original: com.mattzilla30.mbooru.ui.viewmodel.TagViewModel
        compose.runOnIdle {
            val owner =
                (compose.activity.activeBrowse
                        as com.mattzilla30.mbooru.ui.compose.browse.BrowseController)
                    .owner
            original =
                androidx.lifecycle
                    .ViewModelProvider(owner)[
                        com.mattzilla30.mbooru.ui.viewmodel.TagViewModel::class.java]
        }
        explore(R.string.title_pools)
        explore(R.string.title_tags)
        compose.runOnIdle {
            val owner =
                (compose.activity.activeBrowse
                        as com.mattzilla30.mbooru.ui.compose.browse.BrowseController)
                    .owner
            org.junit.Assert.assertSame(
                original,
                androidx.lifecycle
                    .ViewModelProvider(owner)[
                        com.mattzilla30.mbooru.ui.viewmodel.TagViewModel::class.java],
            )
        }
    }

    @Test
    fun `free drawer has no purchase action and can switch beyond three sites`() {
        compose
            .onNodeWithContentDescription(
                compose.activity.getString(R.string.navigation_drawer_open)
            )
            .performClick()
        compose
            .onNodeWithText(compose.activity.getString(R.string.title_settings))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Remove ads & feature unlock").assertDoesNotExist()
        compose.onNodeWithTag("main-drawer-list").performScrollToNode(hasText("Safebooru"))
        compose.onNodeWithText("Safebooru").performSemanticsAction(SemanticsActions.OnClick) {
            it()
        }
        compose.waitUntil(10000) {
            kotlinx.coroutines.runBlocking {
                compose.activity.settings.activatedBooruUid.let { uid ->
                    org.koin.core.context.GlobalContext.get()
                        .get<com.mattzilla30.mbooru.data.database.dao.BooruDao>()
                        .getBooruByUid(uid)
                        ?.host == "safebooru.org"
                }
            }
        }
        explore(R.string.title_tags)
        org.junit.Assert.assertTrue(
            compose.activity.activeBrowse
                is com.mattzilla30.mbooru.ui.compose.browse.TagScreenController
        )
    }
}
