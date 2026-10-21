package com.mattzilla30.mbooru.compose

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.imageLoader
import coil3.request.*
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.api.HttpTransport
import com.mattzilla30.mbooru.data.settings.DataStorePreferences
import java.io.File
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Opt-in live checks complement deterministic local HTTP tests without depending on a public site
 * in CI.
 */
@RunWith(AndroidJUnit4::class)
class MediaNetworkTest {
    @Test
    fun liveImagesLoadThroughProductionTransport() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveMediaChecks") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = GlobalContext.get().get<DataStorePreferences>()
        preferences.awaitReady()
        val original = preferences.getBoolean(Settings.DNS_OVER_HTTPS, true)
        val results = StringBuilder()
        var successfulImages = 0
        try {
            for (doh in listOf(true, false)) {
                withContext(Dispatchers.IO) {
                    preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, doh).commit()
                }
                val transport = GlobalContext.get().get<HttpTransport>()
                val urls =
                    listOf(
                        "https://www.gstatic.com/webp/gallery3/1.png",
                        "https://cdn.donmai.us/180x180/91/d8/91d8587a7f6476e8c236798de06067c9.jpg",
                    )
                for (url in urls) {
                    val host = url.toHttpUrl().host
                    try {
                        withContext(Dispatchers.IO) {
                            transport
                                .mediaClient()
                                .newCall(Request.Builder().url(url).build())
                                .execute()
                                .use {
                                    results.appendLine(
                                        "doh=$doh host=$host http=${it.code} type=${it.header("Content-Type")} bytes=${it.body?.bytes()?.size}"
                                    )
                                }
                        }
                    } catch (e: Exception) {
                        results.appendLine(
                            "doh=$doh host=$host raw=${e.javaClass.simpleName}: ${e.message}"
                        )
                    }
                    val result =
                        withTimeout(60_000) {
                            context.imageLoader.execute(
                                ImageRequest.Builder(context)
                                    .data(url)
                                    .size(512)
                                    .memoryCachePolicy(CachePolicy.DISABLED)
                                    .diskCachePolicy(CachePolicy.DISABLED)
                                    .build()
                            )
                        }
                    if (result is SuccessResult) {
                        successfulImages++
                        results.appendLine("doh=$doh host=$host coil=success")
                    } else if (result is ErrorResult) {
                        results.appendLine(
                            "doh=$doh host=$host coil=${result.throwable.stackTraceToString()}"
                        )
                    }
                }
            }
        } finally {
            withContext(Dispatchers.IO) {
                preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, original).commit()
            }
            val output =
                File(
                    checkNotNull(
                        InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                    ),
                    "live-media.txt",
                )
            output.parentFile?.mkdirs()
            output.writeText(results.toString())
            println(results)
        }
        assertTrue("No HTTP image decoded; see live-media.txt", successfulImages > 0)
    }
}
