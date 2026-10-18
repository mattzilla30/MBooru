package com.mattzilla30.mbooru.okhttp

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class WafBoundaryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `challenge headers exclude credentials and sanitize referers`() {
        val interceptor = CloudflareInterceptor(context, AndroidCookieJar(context))
        val headers =
            interceptor.parseHeaders(
                Headers.Builder()
                    .add("Authorization", "Bearer fixture-secret")
                    .add("Cookie", "session=fixture")
                    .add("X-Api-Key", "fixture-key")
                    .add("User-Agent", "MBooru-test")
                    .add("Referer", "https://user:secret@example.org/post?token=fixture#fragment")
                    .build()
            )
        assertEquals(setOf("User-Agent", "Referer"), headers.keys)
        assertEquals("https://example.org/post", headers["Referer"])
    }

    @Test
    fun `account writes are never replayed after a cloudflare response`() {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(503)
                    .setHeader("Server", "cloudflare")
                    .setBody("challenge")
            )
            val interceptor = CloudflareInterceptor(context, AndroidCookieJar(context))
            val client =
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .addInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        interceptor.intercept(chain, chain.request(), response)
                    }
                    .build()
            client
                .newCall(
                    Request.Builder()
                        .url(server.url("/favorite"))
                        .post("fixture".toRequestBody())
                        .build()
                )
                .execute()
                .use {
                    assertEquals(503, it.code)
                }
            assertEquals(1, server.requestCount)
        }
    }
}
