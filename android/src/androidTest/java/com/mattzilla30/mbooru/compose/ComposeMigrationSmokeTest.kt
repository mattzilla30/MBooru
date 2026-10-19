package com.mattzilla30.mbooru.compose

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.User
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.compose.detail.PostMedia
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import com.mattzilla30.mbooru.widget.DismissFrameLayout
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposeMigrationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val booru =
        Booru(
            name = "Offline fixture",
            scheme = "https",
            host = "example.org",
            type = Values.BOORU_TYPE_MOE,
        )
    private val dismiss =
        object : DismissFrameLayout.OnDismissListener {
            override fun onDismissStart() {}

            override fun onDismissProgress(progress: Float) {}

            override fun onDismissed() {}

            override fun onDismissCancel() {}
        }

    @Test
    fun imageErrorPlaceholderDrawsAtSmallFaviconSize() {
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val drawable =
            requireNotNull(
                androidx.appcompat.content.res.AppCompatResources.getDrawable(
                    context,
                    com.mattzilla30.mbooru.R.drawable.ic_placeholder_error,
                )
            )
        val bitmap =
            android.graphics.Bitmap.createBitmap(21, 21, android.graphics.Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, 21, 21)
        drawable.draw(android.graphics.Canvas(bitmap))
        val pixels = IntArray(21 * 21)
        bitmap.getPixels(pixels, 0, 21, 0, 0, 21, 21)
        org.junit.Assert.assertTrue(
            "The fallback icon must render at favicon size",
            pixels.any { android.graphics.Color.alpha(it) > 0 },
        )
        bitmap.recycle()
    }

    @Test
    fun safeOnlyChangeRemovesExplicitAndUnknownCardsBeforeImageRendering() {
        val safe = mutableStateOf(false)
        val data =
            MutableStateFlow(
                PagingData.from(
                    listOf(
                        sample(1, "").copy(rating = "e"),
                        sample(2, "").copy(rating = "general"),
                        sample(3, "").copy(rating = "unknown"),
                    )
                )
            )
        compose.setContent {
            MBooruTheme {
                val posts = data.collectAsLazyPagingItems()
                PostGrid(
                    posts,
                    emptyFlow(),
                    PostDisplayOptions(true, "1:1", true, false, 90f),
                    { _, _, _ -> },
                    {},
                    modifier = Modifier.fillMaxSize(),
                    safeOnly = safe.value,
                )
            }
        }
        compose.onNodeWithText("#1").assertExists()
        compose.runOnIdle { safe.value = true }
        compose.onNodeWithText("#1").assertDoesNotExist()
        compose.onNodeWithText("#3").assertDoesNotExist()
        compose.onNodeWithText("#2").assertExists()
    }

    @Test
    fun gridScrollRestoresAndUsesStablePostKeys() {
        val data = MutableStateFlow(PagingData.from((1..80).map { sample(it, "") }))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MBooruTheme {
                val posts = data.collectAsLazyPagingItems()
                PostGrid(
                    posts,
                    remember { emptyFlow() },
                    PostDisplayOptions(true, "1:1", true, false, 130f),
                    onClick = { _, _, _ -> },
                    onLongClick = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithTag("post_grid").performScrollToIndex(50)
        compose.onNodeWithText("#51").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("#51").assertIsDisplayed()
    }

    @Test
    fun imagePinchDisablesParentPagingUntilZoomResets() {
        val file = imageFile("compose-photo.png", 1600, 900)
        var zoomed = false
        compose.setContent {
            MBooruTheme {
                PostMedia(
                    sample(1, file.toUri().toString()),
                    booru,
                    "sample",
                    true,
                    dismiss,
                    {},
                    {},
                    { zoomed = it },
                    Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitUntil(20000) {
            var ready = false
            compose.runOnUiThread {
                ready = compose.activity.window.decorView.find<PhotoView>()?.drawable != null
            }
            ready
        }
        compose.onRoot().performTouchInput {
            val center = center
            // Android's minimum scaling span is physical (27 mm by default). Fixed-pixel
            // endpoints can remain below it on dense emulators and never start a pinch.
            val start = Offset(center.x * .4f, 0f)
            val end = Offset(center.x * .84f, 0f)
            pinch(
                start0 = center - start,
                end0 = center - end,
                start1 = center + start,
                end1 = center + end,
                durationMillis = 500,
            )
        }
        compose.runOnIdle {
            val photo = checkNotNull(compose.activity.window.decorView.find<PhotoView>())
            assertTrue(
                "The gesture must change the native image scale",
                photo.scale > photo.minimumScale + .01f,
            )
            assertTrue(
                "Native zoom should disable paging/dismiss gestures (scale=${photo.scale}, min=${photo.minimumScale}, size=${photo.width}x${photo.height})",
                zoomed,
            )
            photo.setScale(photo.minimumScale, false)
            assertFalse("Resetting zoom should restore paging/dismiss gestures", zoomed)
        }
    }

    @Test
    fun fullResolutionLocalImageUsesTiledDecoder() {
        val file = imageFile("compose-large.png", 6000, 4000)
        compose.setContent {
            MBooruTheme {
                PostMedia(
                    sample(2, file.toUri().toString()),
                    booru,
                    "origin",
                    true,
                    dismiss,
                    {},
                    {},
                    {},
                    Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitUntil(30000) {
            var ready = false
            compose.runOnUiThread {
                ready =
                    compose.activity.window.decorView.find<SubsamplingScaleImageView>()?.isReady ==
                        true
            }
            ready
        }
        compose.runOnIdle {
            val view =
                checkNotNull(compose.activity.window.decorView.find<SubsamplingScaleImageView>())
            assertEquals(6000, view.sWidth)
            assertEquals(4000, view.sHeight)
        }
    }

    @Test
    fun localVideoStopsOffscreenAndResumesWithoutRetainingAPlayer() {
        val file = fixtureFile("viewer-fixture.mp4")
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .context
            .assets
            .open("viewer-fixture.mp4")
            .use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        var active by mutableStateOf(true)
        compose.setContent {
            MBooruTheme {
                PostMedia(
                    sample(3, file.toUri().toString()),
                    booru,
                    "sample",
                    active,
                    dismiss,
                    {},
                    {},
                    {},
                    Modifier.fillMaxSize(),
                )
            }
        }
        fun ready(): Boolean {
            var ready = false
            compose.runOnUiThread {
                val player =
                    compose.activity.window.decorView.find<androidx.media3.ui.PlayerView>()?.player
                ready =
                    player?.playbackState == androidx.media3.common.Player.STATE_READY &&
                        player.isPlaying
            }
            return ready
        }
        compose.waitUntil(30000, ::ready)
        compose.runOnIdle {
            val player =
                checkNotNull(
                    compose.activity.window.decorView.find<androidx.media3.ui.PlayerView>()?.player
                )
            assertEquals(320, player.videoSize.width)
            active = false
        }
        compose.waitUntil(10000) {
            var released = false
            compose.runOnUiThread {
                released =
                    compose.activity.window.decorView
                        .find<androidx.media3.ui.PlayerView>()
                        ?.player == null
            }
            released
        }
        compose.runOnIdle { active = true }
        compose.waitUntil(30000, ::ready)
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        compose.runOnUiThread {
            assertNull(
                compose.activity.window.decorView.find<androidx.media3.ui.PlayerView>()?.player
            )
        }
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.waitUntil(30000, ::ready)
    }

    @Test
    fun jpegWebpTransparencyAndAnimatedGifUseTheSharedLoader() {
        val files = mutableListOf<File>()
        val bitmap = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        Canvas(bitmap)
            .drawRect(
                32f,
                16f,
                224f,
                112f,
                android.graphics.Paint().apply { color = Color.rgb(60, 120, 170) },
            )
        @Suppress("DEPRECATION")
        for ((extension, format) in
            listOf(
                "jpg" to Bitmap.CompressFormat.JPEG,
                "png" to Bitmap.CompressFormat.PNG,
                "webp" to Bitmap.CompressFormat.WEBP,
            )) {
            files +=
                fixtureFile("format-fixture.$extension").also { file ->
                    file.outputStream().use { bitmap.compress(format, 90, it) }
                }
        }
        bitmap.recycle()
        files +=
            fixtureFile("format-fixture.gif").also { file ->
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                    .context
                    .assets
                    .open("viewer-fixture.gif")
                    .use { input -> file.outputStream().use { input.copyTo(it) } }
            }
        var current by mutableStateOf(sample(1000, files.first().toUri().toString()))
        compose.setContent {
            MBooruTheme {
                PostMedia(
                    current,
                    booru,
                    "sample",
                    true,
                    dismiss,
                    {},
                    {},
                    {},
                    Modifier.fillMaxSize(),
                )
            }
        }
        var animation: android.graphics.drawable.Animatable? = null
        files.forEachIndexed { index, file ->
            val id = 1000 + index
            compose.runOnIdle { current = sample(id, file.toUri().toString()) }
            compose.waitUntil(20000) {
                var ready = false
                compose.runOnUiThread {
                    val photo = compose.activity.window.decorView.find<PhotoView>()
                    ready =
                        photo != null &&
                            photo.transitionName == "post_$id" &&
                            photo.drawable != null
                    if (file.extension == "gif")
                        ready =
                            ready &&
                                (photo?.drawable as? android.graphics.drawable.Animatable)
                                    ?.isRunning == true
                }
                ready
            }
            compose.runOnIdle {
                val drawable =
                    checkNotNull(compose.activity.window.decorView.find<PhotoView>()?.drawable)
                assertTrue(
                    "Small media must keep a bounded decoded size",
                    drawable.intrinsicWidth in
                        1..compose.activity.resources.displayMetrics.widthPixels,
                )
                if (file.extension == "png" || file.extension == "webp") {
                    val decoded = drawable.toBitmap()
                    assertEquals(
                        "Transparency must survive decoding",
                        0,
                        Color.alpha(decoded.getPixel(0, 0)),
                    )
                }
                if (file.extension == "gif")
                    animation = drawable as android.graphics.drawable.Animatable
            }
        }
        compose.runOnIdle { current = sample(2000, files.first().toUri().toString()) }
        compose.waitUntil(10000) {
            var stopped = false
            compose.runOnUiThread { stopped = animation?.isRunning == false }
            stopped
        }
    }

    // App/cache maintenance may remove cacheDir while an asynchronous decoder opens a fixture.
    private fun fixtureFile(name: String): File {
        val directory = File(compose.activity.filesDir, "compose-media-fixtures")
        check(directory.isDirectory || directory.mkdirs())
        return File(directory, name)
    }

    private fun imageFile(name: String, width: Int, height: Int): File {
        val file = fixtureFile(name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.rgb(60, 120, 170))
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }
}

private inline fun <reified T : View> View.find(): T? = findView(T::class.java)

private fun <T : View> View.findView(type: Class<T>): T? {
    if (type.isInstance(this)) return type.cast(this)
    if (this is ViewGroup)
        for (index in 0 until childCount) {
            getChildAt(index).findView(type)?.let {
                return it
            }
        }
    return null
}

private fun sample(id: Int, url: String) =
    Post(
        id = id,
        uid = id.toLong(),
        index = id - 1,
        booruUid = 1,
        query = "fixture",
        width = 1600,
        height = 900,
        size = 100,
        score = 0,
        rating = "s",
        time = 0,
        tags = emptyList(),
        preview = "",
        sample = url,
        medium = url,
        origin = url,
        source = null,
        uploader = User(name = "fixture"),
    )
