package com.mattzilla30.mbooru.okhttp

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ApiResponseLimitsTest {
    @get:Rule val server = MockWebServer()
    private val client =
        OkHttpClient.Builder().addInterceptor(BoundedApiResponseInterceptor(1024)).build()

    @Test
    fun `known oversized API documents fail before parsing`() {
        server.enqueue(MockResponse().setBody("x".repeat(2048)))
        assertRejected()
    }

    @Test
    fun `chunked API documents cannot evade the response bound`() {
        server.enqueue(MockResponse().setChunkedBody("x".repeat(2048), 64))
        assertRejected()
    }

    @Test
    fun `ordinary API responses preserve their body and headers`() {
        server.enqueue(MockResponse().setHeader("Retry-After", "60").setBody("{}"))
        client.newCall(Request.Builder().url(server.url("/posts")).build()).execute().use {
            assertEquals("{}", it.body!!.string())
            assertEquals("60", it.header("Retry-After"))
        }
    }

    private fun assertRejected() {
        try {
            client.newCall(Request.Builder().url(server.url("/posts")).build()).execute().use {
                it.body!!.string()
            }
            fail("Oversized response accepted")
        } catch (_: ApiResponseTooLargeException) {}
    }
}
