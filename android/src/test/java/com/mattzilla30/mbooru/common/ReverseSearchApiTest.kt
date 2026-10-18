package com.mattzilla30.mbooru.common

import com.mattzilla30.mbooru.common.saucenao.api.SauceNaoApiService
import com.mattzilla30.mbooru.common.tracemoe.api.TraceMoeApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException

class ReverseSearchApiTest {
    @get:Rule val server = MockWebServer()
    private val client = OkHttpClient()
    private val sauceResponse = """{"header":{"user_id":"1","account_type":"1","short_limit":"4","long_limit":"100","long_remaining":90,"short_remaining":3,"status":0,"results_requested":"6"},"results":[]}"""

    @Test fun `trace uploads a single named image part and decodes the response`() = runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
            .setBody("""{"error":"","frameCount":42,"result":[]}"""))
        val result = TraceMoeApiService(client, server.url("/").toString()).fetch("test-image".toByteArray())
        assertEquals(42, result.frameCount)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/search", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"image\"; filename=\"image.jpg\""))
        assertEquals(1, Regex("name=\"image\"").findAll(body).count())
        assertTrue(body.contains("test-image"))
    }

    @Test fun `sauce query preserves special characters without adding parameters`() = runBlocking {
        server.enqueue(MockResponse().setBody(sauceResponse))
        val url = "https://example.invalid/image?a=1&b=2"
        SauceNaoApiService(client, server.url("/").toString()).searchByUrl(url, "key+&=value")
        val request = server.takeRequest().requestUrl!!
        assertEquals(url, request.queryParameter("url"))
        assertEquals("key+&=value", request.queryParameter("api_key"))
        assertEquals("999", request.queryParameter("db"))
        assertEquals("2", request.queryParameter("output_type"))
        assertEquals(4, request.querySize)
    }

    @Test fun `failed image upload reports HTTP status without retrying the write`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        try {
            SauceNaoApiService(client, server.url("/").toString()).searchByImage("secret", byteArrayOf(1,2,3), "png")
            fail("Expected rate limit")
        } catch (error: HttpException) {
            assertEquals(429, error.code())
            assertEquals("60", error.response()!!.headers()["Retry-After"])
        }
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("Content-Type: image/png"))
    }
}
