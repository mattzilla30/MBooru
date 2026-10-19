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
                        val page = chain.request().url.queryParameter("pid")!!.toInt()
                        synchronized(requests) { requests += page }
                        val body =
                            when (page) {
                                0 -> "[${post(1, "g")},${post(2, "e")}]"
                                1 -> "[${post(3, "s")},${post(4, "s")}]"
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

    private fun post(id: Int, rating: String) =
        """{"id":$id,"rating":"$rating","width":100,"height":100,"tags":"landscape","file_url":"https://images.rule34.xxx/$id.jpg"}"""
}
