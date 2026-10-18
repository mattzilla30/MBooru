package com.mattzilla30.mbooru.benchmark

import android.graphics.Bitmap
import android.graphics.Color
import com.mattzilla30.mbooru.app.*
import com.mattzilla30.mbooru.data.api.HttpTransport
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.moebooru.PostMoe
import com.mattzilla30.mbooru.data.model.moebooru.PostMoeResponse
import com.mattzilla30.mbooru.data.settings.DataStorePreferences
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/** Compiled only into the profile target; production release has no fixture routing or seeding. */
class FixtureApp : App() {
    override fun environmentModules() =
        listOf(
            module {
                single {
                    HttpTransport(
                        this@FixtureApp,
                        get(),
                        get(),
                        listOf(OfflineCorpus(this@FixtureApp)),
                    )
                }
            }
        )

    override fun onCreate() {
        super.onCreate()
        // Benchmark-only fixture installation completes before the first Activity requests data.
        runBlocking(Dispatchers.IO) {
            val koin = GlobalContext.get()
            val preferences = koin.get<DataStorePreferences>()
            preferences.awaitReady()
            val dao = koin.get<MyDatabase>().booruDao()
            val uid =
                dao.insert(
                    Booru(
                        name = "Offline corpus",
                        host = "fixture.invalid",
                        type = Values.BOORU_TYPE_MOE,
                    )
                )
            koin.get<SettingsRepository>().activatedBooruUid = uid
            preferences.edit().putBoolean("featured_boorus_v1_installed", true).commit()
        }
    }
}

private class OfflineCorpus(context: android.content.Context) : Interceptor {
    private val image =
        ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(60, 120, 170))
                compress(Bitmap.CompressFormat.PNG, 100, output)
                recycle()
            }
            output.toByteArray()
        }
    private val video = context.assets.open("viewer-fixture.mp4").use { it.readBytes() }
    private val posts =
        (1..60).map { id ->
            val media = "https://fixture.invalid/media/$id.${if (id == 2) "mp4" else "png"}"
            PostMoe(
                id = id,
                tags = "offline landscape",
                createdAt = 1700000000,
                author = "fixture",
                source = null,
                score = 1,
                fileSize = image.size,
                fileUrl = media,
                previewUrl = "https://fixture.invalid/preview/$id.png",
                previewWidth = 320,
                previewHeight = 180,
                sampleUrl = media,
                sampleWidth = 1600,
                sampleHeight = 900,
                sampleFileSize = image.size,
                jpegUrl = media,
                jpegWidth = 1600,
                jpegHeight = 900,
                jpegFileSize = image.size,
                rating = "s",
                hasChildren = false,
                parentId = null,
                status = "active",
                width = 1600,
                height = 900,
            )
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val (bytes, mime) =
            when {
                url.encodedPath.endsWith(".mp4") -> video to "video/mp4"
                url.encodedPath.endsWith(".png") || url.encodedPath.endsWith(".ico") ->
                    image to "image/png"
                url.encodedPath.contains("post") && url.encodedPath.endsWith(".json") -> {
                    val page = url.queryParameter("page")?.toIntOrNull() ?: 1
                    Json.encodeToString(
                            PostMoeResponse(
                                if (page == 1) posts else emptyList(),
                                mapOf("offline" to "general", "landscape" to "general"),
                            )
                        )
                        .toByteArray() to "application/json"
                }
                else -> "[]".toByteArray() to "application/json"
            }
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("Offline fixture")
            .body(bytes.toResponseBody(mime.toMediaType()))
            .build()
    }
}
