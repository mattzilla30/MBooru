package com.mattzilla30.mbooru.okhttp

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.data.api.HttpTransport
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import okhttp3.Dns
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class MediaTransportTest {
    @Test
    fun `media tries the next DNS address while account clients keep no replay policy`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("media-transport", Context.MODE_PRIVATE)
        preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, false).commit()
        val transport =
            HttpTransport(context, SettingsRepository(preferences), AndroidCookieJar(context))
        try {
            assertFalse(transport.apiClient(false).retryOnConnectionFailure)
            assertFalse(transport.apiClient(true).retryOnConnectionFailure)
            assertTrue(transport.mediaClient().retryOnConnectionFailure)
            MockWebServer().use { server ->
                val reachable = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
                val unreachable = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 2))
                server.start(reachable, 0)
                server.enqueue(MockResponse().setBody("image bytes"))
                val request =
                    Request.Builder()
                        .url(server.url("/image.png").newBuilder().host("media.test").build())
                        .build()
                fun client() =
                    transport
                        .mediaClient()
                        .newBuilder()
                        .dns(
                            object : Dns {
                                override fun lookup(hostname: String) =
                                    listOf(unreachable, reachable)
                            }
                        )
                        .proxy(Proxy.NO_PROXY)
                        .build()

                // The old media policy failed immediately on the first refused route.
                try {
                    client()
                        .newBuilder()
                        .retryOnConnectionFailure(false)
                        .build()
                        .newCall(request)
                        .execute()
                        .use { fail("The unreachable first address must fail without recovery") }
                } catch (_: IOException) {
                    assertEquals(0, server.requestCount)
                }
                client().newCall(request).execute().use {
                    assertEquals(200, it.code)
                    assertEquals("image bytes", checkNotNull(it.body).string())
                }
                assertEquals(1, server.requestCount)
            }
        } finally {
            transport.close()
        }
    }
}
