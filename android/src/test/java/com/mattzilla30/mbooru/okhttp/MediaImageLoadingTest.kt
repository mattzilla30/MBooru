package com.mattzilla30.mbooru.okhttp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.mattzilla30.mbooru.app.App
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = App::class, sdk = [28])
class MediaImageLoadingTest {
    @get:Rule val server = MockWebServer()

    @org.junit.Before
    fun resetLoaderFromPreviousApplication() {
        // Each Robolectric application owns a different transport; an earlier Koin
        // teardown closes its executor while Coil's process singleton can survive.
        coil3.SingletonImageLoader.reset()
    }

    @After
    fun close() {
        coil3.SingletonImageLoader.reset()
        org.koin.core.context.stopKoin()
    }

    @Test
    fun `application image loader refetches HTTP images after upgrading from Coil 2`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store =
                org.koin.core.context.GlobalContext.get()
                    .get<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
            store.awaitReady()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                store
                    .edit()
                    .putBoolean(com.mattzilla30.mbooru.app.Settings.DNS_OVER_HTTPS, false)
                    .commit()
            }
            val bytes =
                java.util.Base64.getDecoder()
                    .decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII="
                    )
            server.enqueue(
                MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
            )
            val oldCache =
                coil3.disk.DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .build()
            val editor = checkNotNull(oldCache.openEditor(server.url("/image.png").toString()))
            java.io
                .File(editor.metadata.toString())
                .writeText("1730000000000\n1730000000010\n1\n1\nContent-Type: image/png\n")
            java.io.File(editor.data.toString()).writeBytes(bytes)
            editor.commit()
            oldCache.shutdown()
            val result =
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(server.url("/image.png").toString())
                        .size(128)
                        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                        .build()
                )
            if (result is coil3.request.ErrorResult)
                throw AssertionError("HTTP image failed", result.throwable)
            assertTrue(result is SuccessResult)
            assertEquals(1, server.requestCount)
            val cached =
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(server.url("/image.png").toString())
                        .size(128)
                        .memoryCachePolicy(coil3.request.CachePolicy.DISABLED)
                        .build()
                )
            if (cached is coil3.request.ErrorResult)
                throw AssertionError("Cached HTTP image failed", cached.throwable)
            assertTrue(cached is SuccessResult)
            assertEquals(coil3.decode.DataSource.DISK, (cached as SuccessResult).dataSource)
            assertEquals(1, server.requestCount)
        }
}
