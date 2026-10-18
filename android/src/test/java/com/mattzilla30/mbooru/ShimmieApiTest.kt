package com.mattzilla30.mbooru

import com.mattzilla30.mbooru.data.api.ShimmieApi
import com.mattzilla30.mbooru.data.api.createApi
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ShimmieApiTest {
    @get:Rule val server = MockWebServer()

    @Test
    fun `awaited offline XML response maps dimensions tags and relative media URLs`() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/xml")
                    .setBody(
                        """
                        <posts count="1" offset="0"><post id="42" preview_url="/preview.jpg" preview_width="120" preview_height="80"
                            file_url="/image.jpg" file_name="image.jpg" width="1600" height="900" score="3" tags="landscape sky"
                            date="2026-10-18 00:00:00" author="fixture" rating="s" /></posts>
                        """
                            .trimIndent()
                    )
            )
            val response =
                createApi<ShimmieApi>(OkHttpClient())
                    .getPosts(server.url("/api/danbooru/find_posts?limit=2"))
            assertTrue(response.isSuccessful)
            val post =
                response
                    .body()!!
                    .post!!
                    .single()
                    .toPost(1, "fixture", "https", "fixture.invalid", 0)
            assertEquals(42, post.id)
            assertEquals(1600, post.width)
            assertEquals(900, post.height)
            assertEquals(listOf("landscape", "sky"), post.tags.map { it.name })
            assertEquals("https://fixture.invalid/image.jpg", post.origin)
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("limit"))
        }
}
