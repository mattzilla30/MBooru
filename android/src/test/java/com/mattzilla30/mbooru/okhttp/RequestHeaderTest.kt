package com.mattzilla30.mbooru.okhttp

import com.mattzilla30.mbooru.app.Values
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RequestHeaderTest {
    @get:Rule val server = MockWebServer()

    @Test
    fun `Sankaku requests keep the desktop agent and site referer`() {
        verify(
            "https://capi-v2.sankakucomplex.com/image.jpg",
            Values.PC_USER_AGENT,
            Values.SANKAKU_REFERER,
        )
    }

    @Test
    fun `other sites keep their mobile agent and original referer`() {
        verify(
            "https://example.invalid/image.jpg",
            Values.MOBILE_USER_AGENT,
            "https://example.invalid/post",
        )
    }

    @Test
    fun `Rule34 media uses the site root referer without duplicate player headers`() {
        verify("https://wimg.rule34.xxx/image.jpg", Values.MOBILE_USER_AGENT, "https://rule34.xxx/")
    }

    private fun verify(source: String, agent: String, referer: String) {
        server.enqueue(MockResponse().setBody("image bytes"))
        val client =
            OkHttpClient.Builder()
                .addInterceptor(RequestHeaderInterceptor())
                // Route the resulting request to a local fixture; never contact the real service.
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().url(server.url("/image")).build())
                }
                .build()
        client
            .newCall(
                Request.Builder()
                    .url(source)
                    .header("User-Agent", "old")
                    .header("Referer", "https://old.invalid/")
                    .build()
            )
            .execute()
            .use {
                assertEquals("image bytes", it.body!!.string())
            }
        val received = server.takeRequest()
        assertEquals(agent, received.getHeader("User-Agent"))
        assertEquals(referer, received.getHeader("Referer"))
        assertEquals(1, received.headers.values("Referer").size)
    }
}
