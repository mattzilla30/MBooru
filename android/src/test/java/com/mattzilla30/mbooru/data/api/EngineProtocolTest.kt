package com.mattzilla30.mbooru.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Offline protocol boundaries. No fixtures depend on a live site's availability. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(application = android.app.Application::class, sdk = [28])
class EngineProtocolTest {
    @get:Rule val server = MockWebServer()
    private val client = OkHttpClient.Builder().retryOnConnectionFailure(false).build()

    private fun json(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun xml() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/xml")
                .setBody("""<?xml version="1.0" encoding="UTF-8"?><posts count="0" offset="0"/>""")
        )
    }

    @Test
    fun `JSON engines accept empty result pages and forward the supplied endpoint`() = runBlocking {
        val endpoint = server.url("/custom/posts?tags=landscape%20rating%3As")
        json("[]")
        assertTrue(createApi<DanbooruApi>(client).getPosts(endpoint).body()!!.isEmpty())
        json("[]")
        assertTrue(createApi<Danbooru1Api>(client).getPosts(endpoint).body()!!.isEmpty())
        json("""{"posts":[],"tags":{},"future_field":true}""")
        assertTrue(createApi<MoebooruApi>(client).getPosts(endpoint).body()!!.posts.isEmpty())
        json("""{"data":[],"meta":{"next":null,"prev":null}}""")
        val result =
            createApi<SankakuApi>(client).getPostsAuth(endpoint, "Bearer fixture-token").body()!!
        assertTrue(result.posts.isEmpty())
        assertNull(result.meta.next)
        repeat(4) { index ->
            val received = server.takeRequest()
            assertEquals(endpoint.encodedPath, received.requestUrl!!.encodedPath)
            assertEquals("landscape rating:s", received.requestUrl!!.queryParameter("tags"))
            if (index == 3)
                assertEquals("Bearer fixture-token", received.getHeader("Authorization"))
        }
    }

    @Test
    fun `XML engine variants retain their empty response contract`() = runBlocking {
        val endpoint = server.url("/custom/index.php?page=dapi&s=post&q=index")
        xml()
        assertNull(createApi<GelbooruApi>(client).getPosts(endpoint).body()!!.posts)
        xml()
        assertNull(createApi<GelbooruApi>(client).getPostsLegacy(endpoint).body()!!.posts)
        xml()
        assertNull(createApi<ShimmieApi>(client).getPosts(endpoint).body()!!.post)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `expired authentication and rate limits remain HTTP failures without a write replay`() =
        runBlocking {
            val api = createApi<SankakuApi>(client)
            server.enqueue(MockResponse().setResponseCode(401))
            assertEquals(401, api.getPostsAuth(server.url("/posts"), "Bearer expired").code())
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
            val response = api.favPost(server.url("/favorite"), "Bearer fixture")
            assertEquals(429, response.code())
            assertEquals("60", response.headers()["Retry-After"])
            assertEquals(2, server.requestCount)
            assertEquals("GET", server.takeRequest().method)
            assertEquals("POST", server.takeRequest().method)
        }
}
