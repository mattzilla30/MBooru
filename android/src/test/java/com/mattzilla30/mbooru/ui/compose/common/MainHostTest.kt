package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.R
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
@Config(application = SearchFixtureApp::class, sdk = [28], qualifiers = "en-rUS-notnight")
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
                dao.insertMissing(com.mattzilla30.mbooru.data.database.BooruPresets.featured())
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
            compose.onAllNodesWithTag("home-tab-1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun drawerText(text: String) =
        compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("sphere-drawer-content")))

    private fun explore(title: Int) {
        compose.runOnUiThread { compose.activity.openDrawer() }
        val list = compose.onNodeWithTag("main-drawer-list")
        list.performScrollToNode(hasText(compose.activity.getString(R.string.sphere_explore)))
        val target = compose.activity.getString(title)
        if (compose.onAllNodesWithText(target).fetchSemanticsNodes().isEmpty()) {
            drawerText(compose.activity.getString(R.string.sphere_explore)).performClick()
        }
        list.performScrollToNode(hasText(target))
        drawerText(target).performClick()
        compose.waitForIdle()
    }

    @After
    fun closeDependencies() {
        val model =
            androidx.lifecycle
                .ViewModelProvider(compose.activity)[
                    com.mattzilla30.mbooru.ui.viewmodel.BooruViewModel::class.java]
        val job = model.viewModelScope.coroutineContext[kotlinx.coroutines.Job]
        compose.activityRule.scenario.close()
        kotlinx.coroutines.runBlocking { job?.join() }
        stopKoin()
    }

    @Test
    fun `refresh resumes a cancelled cached query and makes a new request`() {
        val fixture =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        compose.waitUntil(10000) { fixture.requests.any { it.encodedPath == "/post.json" } }
        compose.onNodeWithText("Cancel").performClick()
        val before = fixture.requests.count { it.encodedPath == "/post.json" }
        compose.onNodeWithText("Refresh").performClick()
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            fixture.requests.count { it.encodedPath == "/post.json" } > before
        }
    }

    @Test
    fun `grid scroll and cached results survive tab switches and recreation without initial network reloads`() {
        val fixture =
            androidx.test.core.app.ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        fixture.catalogSize = 80
        compose.onNodeWithText("Refresh").performClick()
        compose.waitUntil(10000) {
            kotlinx.coroutines.runBlocking {
                org.koin.core.context.GlobalContext.get()
                    .get<com.mattzilla30.mbooru.data.database.MyDatabase>()
                    .postDao()
                    .getPostsRaw(compose.activity.settings.activatedBooruUid, "", 100)
                    .size == 80
            }
        }
        compose.onNodeWithTag("post_grid").performScrollToIndex(50)
        val target = compose.activity.getString(R.string.post_accessibility, 2518)
        compose.onNodeWithContentDescription(target).assertIsDisplayed()
        compose.runOnIdle { compose.activity.settings.autoRefresh = true }
        // End-of-cache append requests remain valid; returning to cached data must not refresh page
        // 1.
        val requests =
            fixture.requests.count {
                it.encodedPath == "/post.json" && it.queryParameter("page") == "1"
            }
        compose.onNodeWithTag("home-tab-2").performClick()
        compose.onNodeWithTag("home-tab-0").performClick()
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.onAllNodesWithContentDescription(target).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(target).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10000) {
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.onAllNodesWithContentDescription(target).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(target).assertIsDisplayed()
        org.junit.Assert.assertEquals(
            requests,
            fixture.requests.count {
                it.encodedPath == "/post.json" && it.queryParameter("page") == "1"
            },
        )
    }

    @Test
    fun `all primary destinations work and site search survives recreation`() {
        compose.onNodeWithTag("home-tab-1").performClick()
        compose.onNodeWithTag("unified-query").performTextReplacement("landscape -cloud")
        compose.onNodeWithTag("unified-submit").performClick()
        compose.onNodeWithTag("home-tab-2").performClick()
        compose.onNodeWithText("New collection").assertIsDisplayed()
        compose.onNodeWithTag("home-tab-3").performClick()
        compose.onNodeWithText("Add, remove, import or export sites").assertExists()
        compose.onNodeWithTag("home-tab-4").performClick()
        compose.onNodeWithTag("settings_list").assertExists()
        compose.onNodeWithTag("home-tab-0").performClick()
        compose.onNodeWithTag("home-tab-1").performClick()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("unified-query").assert(hasText("landscape -cloud"))
    }

    @Test
    fun `navigation view and drawer remain usable after recreation`() {
        val description = compose.activity.getString(R.string.navigation_drawer_open)
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription(description).assertIsDisplayed().performClick()
        drawerText(compose.activity.getString(R.string.title_settings))
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
        drawerText(compose.activity.getString(R.string.title_settings))
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
