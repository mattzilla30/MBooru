// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import android.app.Application
import android.content.Context
import androidx.paging.*
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.api.HttpTransport
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.*
import com.mattzilla30.mbooru.data.repository.post.PostRemoteMediator
import com.mattzilla30.mbooru.okhttp.AndroidCookieJar
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalPagingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class Rule34PagingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: MyDatabase
    private lateinit var transport: HttpTransport
    private lateinit var action: ActionPost
    private lateinit var settings: SettingsRepository
    private val requests = mutableListOf<Int>()
    private val queries = mutableListOf<String>()
    private val state = PagingState<Int, Post>(emptyList(), null, PagingConfig(2), 0)

    @Before
    fun open() = runBlocking {
        database =
            MyDatabase.create(
                context,
                object : CredentialRepository {
                    private val values = mutableMapOf<String, String>()

                    override fun store(payload: String) =
                        (CredentialRepository.PREFIX + java.util.UUID.randomUUID()).also {
                            values[it] = payload
                        }

                    override fun read(reference: String) = values[reference]

                    override fun remove(reference: String) {
                        values.remove(reference)
                    }
                },
                "paging-${java.util.UUID.randomUUID()}.db",
            )
        settings =
            SettingsRepository(context.getSharedPreferences("paging-fixture", Context.MODE_PRIVATE))
        transport =
            HttpTransport(
                context,
                settings,
                AndroidCookieJar(context),
                listOf(
                    Interceptor { chain ->
                        val page = chain.request().url.queryParameter("pid")?.toInt() ?: -1
                        synchronized(requests) {
                            requests += page
                            queries += chain.request().url.queryParameter("tags").orEmpty()
                        }
                        val body =
                            when {
                                chain.request().url.queryParameter("s") == "tag" ->
                                    if (page == 0)
                                        """[{"id":1,"name":"landscape","count":42,"type":0},{"id":2,"name":"sky","count":10,"type":0}]"""
                                    else "[]"
                                page == -1 -> MOE_POST_SNAPSHOT
                                page == 0 -> "[${post(1, "g")},${post(2, "e")}]"
                                page == 1 -> "[${post(3, "s")},${post(4, "s")}]"
                                else -> "[]"
                            }
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("Fixture")
                            .body(body.toResponseBody("application/json".toMediaType()))
                            .build()
                    }
                ),
            )
        val site =
            Booru(
                name = "Rule34 fixture",
                host = "api.rule34.xxx",
                type = 7,
                user = User(name = "42", token = "fixture"),
            )
        site.uid = database.booruDao().insert(site)
        action =
            ActionPost(
                site,
                limit = 2,
                query = "landscape",
                date = ActionPost.Date(2026, 10, 19, 2026, 10, 19),
            )
    }

    @After
    fun close() {
        transport.close()
        database.close()
    }

    @Test
    fun cachedPartialMoePageDoesNotReloadOnARecreatedPager() = runBlocking {
        val site = action.booru.copy(host = "moe.invalid", type = 1, user = null)
        database.booruDao().update(site)
        val query = action.copy(booru = site)
        val apis = BooruApis(transport)
        settings.autoRefresh = true
        val first = PostRemoteMediator(query, database, apis, settings)
        val loaded = first.load(LoadType.REFRESH, state)
        assertTrue(loaded is RemoteMediator.MediatorResult.Success && loaded.endOfPaginationReached)
        val restored = PostRemoteMediator(query, database, apis, settings, allowAutoRefresh = false)
        assertEquals(RemoteMediator.InitializeAction.SKIP_INITIAL_REFRESH, restored.initialize())
        val append = restored.load(LoadType.APPEND, state)
        assertTrue(append is RemoteMediator.MediatorResult.Success && append.endOfPaginationReached)
        assertEquals(listOf(-1), requests)
        val refreshed =
            PostRemoteMediator(
                query,
                database,
                apis,
                settings,
                forceRefresh = true,
                allowAutoRefresh = false,
            )
        assertEquals(RemoteMediator.InitializeAction.LAUNCH_INITIAL_REFRESH, refreshed.initialize())
        refreshed.load(LoadType.REFRESH, state)
        assertEquals(listOf(-1, -1), requests)
    }

    @Test
    fun filteredRecordsDoNotRepeatOrSkipServerPages() = runBlocking {
        val mediator = PostRemoteMediator(action, database, BooruApis(transport), settings)
        val first = mediator.load(LoadType.REFRESH, state)
        assertTrue(first is RemoteMediator.MediatorResult.Success && !first.endOfPaginationReached)
        assertEquals("1", database.nextDao().getNext(action.booru.uid, action.query)!!.next)
        mediator.load(LoadType.APPEND, state)
        val last = mediator.load(LoadType.APPEND, state)
        assertTrue(last is RemoteMediator.MediatorResult.Success && last.endOfPaginationReached)
        assertEquals(listOf(0, 1, 2), requests)
        val posts = database.postDao().getPostsRaw(action.booru.uid, action.query, 10)
        assertEquals(listOf(1, 3, 4), posts.map { it.id })
        assertEquals(listOf(0, 1, 2), posts.map { it.index })
    }

    @Test
    fun cursorAndPreviousCacheSurviveAnAbortedRefresh() = runBlocking {
        val existing =
            Rule34Adapter.parse(
                    "[${post(77, "s")}]",
                    action.booru.uid,
                    ArtworkQuery(action.booru.uid, action.query),
                )
                .posts
                .single()
        database.postDao().insert(existing)
        database
            .nextDao()
            .insert(Next(booruUid = action.booru.uid, query = action.query, next = "9"))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_page BEFORE INSERT ON posts BEGIN SELECT RAISE(ABORT, 'fixture abort'); END"
        )
        val mediator = PostRemoteMediator(action, database, BooruApis(transport), settings)
        assertTrue(mediator.load(LoadType.REFRESH, state) is RemoteMediator.MediatorResult.Error)
        assertEquals("9", database.nextDao().getNext(action.booru.uid, action.query)!!.next)
        assertEquals(
            77,
            database.postDao().getPostsRaw(action.booru.uid, action.query, 10).single().id,
        )
    }

    @Test
    fun topRatedUsesScoreSortAndKeepsItsOwnPagedCache() = runBlocking {
        val topRated =
            action.copy(
                pageType = com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POPULAR,
                query = "order:popular",
            )
        val mediator = PostRemoteMediator(topRated, database, BooruApis(transport), settings)
        assertTrue(mediator.load(LoadType.REFRESH, state) is RemoteMediator.MediatorResult.Success)
        mediator.load(LoadType.APPEND, state)
        assertTrue(queries.all { it.contains("sort:score:desc") && !it.contains("order:popular") })
        assertEquals(listOf(0, 1), requests)
        assertTrue(database.postDao().getPostsRaw(action.booru.uid, action.query, 10).isEmpty())
        assertEquals(
            listOf(1, 3, 4),
            database.postDao().getPostsRaw(action.booru.uid, "order:popular", 10).map { it.id },
        )
    }

    @Test
    fun tagPagingUsesRule34AdapterAndStopsAtEmptyPage() = runBlocking {
        val tagAction =
            com.mattzilla30.mbooru.data.action.ActionTag(action.booru, order = "count", limit = 2)
        val source =
            com.mattzilla30.mbooru.data.repository.tag.TagPagingSource(
                tagAction,
                BooruApis(transport),
            )
        val first =
            source.load(PagingSource.LoadParams.Refresh(0, 2, false))
                as PagingSource.LoadResult.Page
        assertEquals(listOf("landscape", "sky"), first.data.map { it.name })
        assertNull(first.prevKey)
        assertEquals(1, first.nextKey)
        val last =
            source.load(PagingSource.LoadParams.Append(1, 2, false)) as PagingSource.LoadResult.Page
        assertTrue(last.data.isEmpty())
        assertNull(last.nextKey)
        assertEquals(listOf(0, 1), requests)
    }

    private fun post(id: Int, rating: String) =
        """{"id":$id,"rating":"$rating","width":100,"height":100,"tags":"landscape","file_url":"https://images.rule34.xxx/$id.jpg"}"""
}

private const val MOE_POST_SNAPSHOT =
    """{"posts":[{"id":2468,"tags":"landscape","created_at":1700000000,"author":"fixture","source":null,"score":1,"file_size":1,"preview_url":"https://search.invalid/image.png","preview_width":100,"preview_height":100,"sample_width":100,"sample_height":100,"sample_file_size":1,"jpeg_width":100,"jpeg_height":100,"jpeg_file_size":1,"rating":"s","has_children":false,"parent_id":null,"status":"active","width":100,"height":100}],"tags":{"landscape":"general"},"votes":{}}"""
