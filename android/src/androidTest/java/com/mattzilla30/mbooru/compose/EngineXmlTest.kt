package com.mattzilla30.mbooru.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.data.api.ShimmieApi
import com.mattzilla30.mbooru.data.api.createApi
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual Android pull parser on every supported API checkpoint. */
@RunWith(AndroidJUnit4::class)
class EngineXmlTest {
    @Test
    fun xmlMediaUsesTheAndroidAdapterAndPreservesWireFields() = runBlocking {
        val xml =
            """<posts count="1" offset="0"><post id="42" preview_url="/preview.jpg" preview_width="120" preview_height="80" file_url="/image.jpg" file_name="image.jpg" width="1600" height="900" score="3" tags="landscape sky" date="2026-10-18 00:00:00" author="fixture" rating="s" /></posts>"""
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("Fixture")
                        .body(xml.toResponseBody("application/xml".toMediaType()))
                        .build()
                }
                .build()
        try {
            val wire =
                createApi<ShimmieApi>(client)
                    .getPosts("https://fixture.invalid/api/posts".toHttpUrl())
                    .body()!!
                    .post!!
                    .single()
            val post = wire.toPost(1, "fixture", "https", "fixture.invalid", 0)
            assertEquals(42, post.id)
            assertEquals(1600, post.width)
            assertEquals(listOf("landscape", "sky"), post.tags.map { it.name })
            assertEquals("https://fixture.invalid/image.jpg", post.origin)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
