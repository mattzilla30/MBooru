// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.User
import com.mattzilla30.mbooru.extension.safeNetworkMessage
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.*
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit

class Rule34TransportTest {
    private val site =
        Booru(
            name = "Fixture",
            host = "api.rule34.xxx",
            type = 7,
            uid = 8,
            user = User(name = "42", token = "fixture-secret"),
        )

    private fun adapter(interceptor: Interceptor) =
        Rule34Adapter(
            Retrofit.Builder()
                .baseUrl("https://api.rule34.xxx/")
                .client(OkHttpClient.Builder().addInterceptor(interceptor).build())
                .build()
                .create(Rule34Api::class.java)
        )

    private fun response(request: Request, code: Int, body: String) =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Fixture")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    @Test
    fun `requests carry required headers and encoded credentials without live traffic`() =
        runBlocking {
            var request: Request? = null
            val adapter = adapter { chain ->
                request = chain.request()
                response(chain.request(), 200, "[]")
            }
            assertEquals(
                0,
                adapter.fetch(site, ArtworkQuery(8, "landscape -cloud"), 3, 20).received,
            )
            assertEquals("https://rule34.xxx/", request!!.header("Referer"))
            assertEquals("no-store", request!!.header("Cache-Control"))
            assertEquals("3", request!!.url.queryParameter("pid"))
            assertEquals("42", request!!.url.queryParameter("user_id"))
            assertEquals("fixture-secret", request!!.url.queryParameter("api_key"))
        }

    @Test
    fun `HTTP errors and transport failures have safe distinct messages`() = runBlocking {
        for (status in listOf(401, 403, 429, 503)) {
            val adapter = adapter { chain ->
                response(chain.request(), status, "fixture-secret")
                    .newBuilder()
                    .header("Retry-After", "27")
                    .build()
            }
            try {
                adapter.fetch(site, ArtworkQuery(8), 0, 1)
                fail("Error response accepted")
            } catch (error: retrofit2.HttpException) {
                val message = error.safeNetworkMessage()
                assertFalse(message.contains("fixture-secret"))
                assertFalse(message.contains("api_key"))
                if (status == 429) assertTrue(message.contains("27 seconds"))
                if (status == 401 || status == 403) assertTrue(message.contains("Sign in"))
                if (status == 503) assertTrue(message.contains("temporarily unavailable"))
            }
        }
        for (failure in
            listOf(
                SocketTimeoutException("fixture-secret"),
                SSLHandshakeException("fixture-secret"),
            )) {
            val adapter = adapter { throw failure }
            try {
                adapter.fetch(site, ArtworkQuery(8), 0, 1)
                fail("Transport failure accepted")
            } catch (error: java.io.IOException) {
                assertFalse(error.safeNetworkMessage().contains("fixture-secret"))
            }
        }
    }

    @Test
    fun `obsolete request cancellation does not become an empty successful page`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val adapter =
            Rule34Adapter(
                object : Rule34Api {
                    override suspend fun posts(url: HttpUrl): retrofit2.Response<ResponseBody> {
                        entered.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.set(true)
                        }
                    }
                }
            )
        val job = launch {
            adapter.fetch(site, ArtworkQuery(8, "obsolete"), 0, 20)
            fail("Cancelled result escaped")
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(cancelled.get())
    }
}
