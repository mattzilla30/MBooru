package com.mattzilla30.mbooru.ui.compose.common

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.*
import com.mattzilla30.mbooru.data.api.HttpTransport
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.settings.DataStorePreferences
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@OptIn(coil3.annotation.DelicateCoilApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = SearchFixtureApp::class, sdk = [28], qualifiers = "en-rUS-notnight")
@LooperMode(LooperMode.Mode.PAUSED)
class SearchResultsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Before
    fun clearImageLoader() {
        coil3.SingletonImageLoader.reset()
    }

    @After
    fun close() {
        coil3.SingletonImageLoader.reset()
        stopKoin()
    }

    @Test
    fun initialSearchHasOneLoadingIndicatorUntilPostsArrive() {
        val context = ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        context.delayPosts = true
        val intent =
            Intent(context, SearchActivity::class.java).putExtra(Keys.POST_QUERY, "landscape")
        try {
            ActivityScenario.launch<SearchActivity>(intent).use {
                val loading =
                    SemanticsMatcher.expectValue(
                        androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                        androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate,
                    )
                compose.waitUntil(10000) {
                    val nodes =
                        compose
                            .onAllNodes(loading)
                            .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    nodes.isNotEmpty() && context.requests.any { it.encodedPath == "/post.json" }
                }
                compose.onAllNodes(loading).assertCountEquals(1)
                context.releasePosts.countDown()
                val description = context.getString(R.string.post_accessibility, 2468)
                compose.waitUntil(10000) {
                    compose
                        .onAllNodesWithContentDescription(description)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .isNotEmpty()
                }
                compose.onNodeWithContentDescription(description).assertIsDisplayed()
                compose.onAllNodes(loading).assertCountEquals(0)
            }
        } finally {
            context.releasePosts.countDown()
        }
    }

    @Test
    fun successfulEmptySearchShowsNoResults() {
        val context = ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        context.emptyResults = true
        val intent =
            Intent(context, SearchActivity::class.java).putExtra(Keys.POST_QUERY, "muscular")
        ActivityScenario.launch<SearchActivity>(intent).use {
            val empty = context.getString(R.string.browse_empty)
            compose.waitUntil(10000) {
                val visible =
                    compose
                        .onAllNodesWithText(empty)
                        .fetchSemanticsNodes(atLeastOneRootRequired = false)
                        .isNotEmpty()
                visible &&
                    context.requests.any { it.queryParameter("tags") == "muscular rating:safe" }
            }
            compose.onNodeWithText(empty).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.action_retry)).assertDoesNotExist()
        }
    }

    @Test
    fun failedSearchShowsErrorAndCanBeRetried() {
        val context = ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        context.postStatus = 503
        val intent =
            Intent(context, SearchActivity::class.java).putExtra(Keys.POST_QUERY, "landscape")
        ActivityScenario.launch<SearchActivity>(intent).use {
            val error = "The site is temporarily unavailable. Please try again later."
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithText(error)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithText(context.getString(R.string.browse_empty)).assertDoesNotExist()
            context.postStatus = 200
            compose
                .onAllNodesWithText(context.getString(R.string.action_retry))
                .onFirst()
                .performClick()
            val description = context.getString(R.string.post_accessibility, 2468)
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }

    @Test
    fun freshSearchLoadsAndDisplaysPostsWithAutoRefreshDisabled() {
        val context = ApplicationProvider.getApplicationContext<SearchFixtureApp>()
        val intent =
            Intent(context, SearchActivity::class.java).putExtra(Keys.POST_QUERY, "landscape")
        ActivityScenario.launch<SearchActivity>(intent).use { scenario ->
            val description = context.getString(R.string.post_accessibility, 2468)
            compose.waitUntil(10000) {
                compose
                    .onAllNodesWithContentDescription(description)
                    .fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty()
            }
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
            Assert.assertTrue(
                context.requests.any {
                    it.encodedPath == "/post.json" &&
                        it.queryParameter("tags") == "landscape rating:safe"
                }
            )
            scenario.recreate()
            compose.onNodeWithContentDescription(description).assertIsDisplayed()
            compose.onNodeWithTag("browse-query-toggle").assertTextEquals("landscape")
        }
    }
}

class SearchFixtureApp : App() {
    val requests = CopyOnWriteArrayList<HttpUrl>()
    @Volatile var postStatus = 200
    @Volatile var emptyResults = false
    @Volatile var delayPosts = false
    val releasePosts = java.util.concurrent.CountDownLatch(1)

    override fun environmentModules() =
        listOf(
            module {
                single {
                    HttpTransport(
                        this@SearchFixtureApp,
                        get(),
                        get(),
                        listOf(
                            Interceptor { chain ->
                                val request = chain.request()
                                requests += request.url
                                if (delayPosts && request.url.encodedPath == "/post.json") {
                                    check(
                                        releasePosts.await(
                                            20,
                                            java.util.concurrent.TimeUnit.SECONDS,
                                        )
                                    )
                                }
                                val body =
                                    if (request.url.encodedPath == "/post.json") {
                                        if (emptyResults) """{"posts":[],"tags":{},"votes":{}}"""
                                        else SEARCH_POST
                                    } else "{}"
                                Response.Builder()
                                    .request(request)
                                    .protocol(Protocol.HTTP_1_1)
                                    .code(
                                        if (request.url.encodedPath == "/post.json") postStatus
                                        else 200
                                    )
                                    .message("Search fixture")
                                    .body(body.toResponseBody("application/json".toMediaType()))
                                    .build()
                            }
                        ),
                    )
                }
            }
        )

    override fun onCreate() {
        super.onCreate()
        runBlocking(Dispatchers.IO) {
            val koin = GlobalContext.get()
            val preferences = koin.get<DataStorePreferences>()
            preferences.awaitReady()
            val uid =
                koin
                    .get<MyDatabase>()
                    .booruDao()
                    .insert(
                        Booru(
                            name = "Search fixture",
                            host = "search.invalid",
                            type = Values.BOORU_TYPE_MOE,
                        )
                    )
            koin.get<SettingsRepository>().activatedBooruUid = uid
            preferences
                .edit()
                .putBoolean(Settings.AUTO_REFRESH_KEY, false)
                .putBoolean(Settings.SAFE_MODE_KEY, true)
                .putBoolean("featured_boorus_v1_installed", true)
                .commit()
        }
    }
}

private const val SEARCH_POST =
    """{"posts":[{"id":2468,"tags":"landscape","created_at":1700000000,"author":"fixture","source":null,"score":1,"file_size":1,"preview_url":"https://search.invalid/image.png","preview_width":100,"preview_height":100,"sample_width":100,"sample_height":100,"sample_file_size":1,"jpeg_width":100,"jpeg_height":100,"jpeg_file_size":1,"rating":"s","has_children":false,"parent_id":null,"status":"active","width":100,"height":100}],"tags":{"landscape":"general"},"votes":{}}"""
