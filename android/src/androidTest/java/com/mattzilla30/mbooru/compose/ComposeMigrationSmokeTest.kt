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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ComposeMigrationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val booru = Booru(name = "Offline fixture", scheme = "https", host = "example.org", type = Values.BOORU_TYPE_MOE)
    private val dismiss = object : DismissFrameLayout.OnDismissListener {
        override fun onDismissStart() {}
        override fun onDismissProgress(progress: Float) {}
        override fun onDismissed() {}
        override fun onDismissCancel() {}
    }

    @Test fun gridScrollRestoresAndUsesStablePostKeys() {
        val data = MutableStateFlow(PagingData.from((1..80).map { sample(it, "") }))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MBooruTheme {
                val posts = data.collectAsLazyPagingItems()
                PostGrid(posts, remember { emptyFlow() }, PostDisplayOptions(true, "1:1", true, false, 130f),
                    onClick = { _, _, _ -> }, onLongClick = {}, modifier = Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithTag("post_grid").performScrollToIndex(50)
        compose.onNodeWithText("#51").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("#51").assertIsDisplayed()
    }

    @Test fun imagePinchDisablesParentPagingUntilZoomResets() {
        val file = imageFile("compose-photo.png", 1600, 900)
        var zoomed = false
        compose.setContent {
            MBooruTheme { PostMedia(sample(1, file.toUri().toString()), booru, "sample", true,
                dismiss, {}, {}, { zoomed = it }, Modifier.fillMaxSize()) }
        }
        compose.waitUntil(20000) {
            var ready = false
            compose.runOnUiThread { ready = compose.activity.window.decorView.find<PhotoView>()?.drawable != null }
            ready
        }
        compose.onRoot().performTouchInput {
            val center = center
            pinch(center - Offset(50f, 0f), center + Offset(50f, 0f),
                center - Offset(220f, 0f), center + Offset(220f, 0f), 500)
        }
        compose.runOnIdle {
            val photo = checkNotNull(compose.activity.window.decorView.find<PhotoView>())
            assertTrue("Native zoom should disable paging/dismiss gestures (scale=${photo.scale}, min=${photo.minimumScale}, size=${photo.width}x${photo.height})", zoomed)
            photo.setScale(photo.minimumScale, false)
            assertFalse("Resetting zoom should restore paging/dismiss gestures", zoomed)
        }
    }

    @Test fun fullResolutionLocalImageUsesTiledDecoder() {
        val file = imageFile("compose-large.png", 6000, 4000)
        compose.setContent {
            MBooruTheme { PostMedia(sample(2, file.toUri().toString()), booru, "origin", true,
                dismiss, {}, {}, {}, Modifier.fillMaxSize()) }
        }
        compose.waitUntil(30000) {
            var ready = false
            compose.runOnUiThread { ready = compose.activity.window.decorView.find<SubsamplingScaleImageView>()?.isReady == true }
            ready
        }
        compose.runOnIdle {
            val view = checkNotNull(compose.activity.window.decorView.find<SubsamplingScaleImageView>())
            assertEquals(6000, view.sWidth)
            assertEquals(4000, view.sHeight)
        }
    }

    private fun imageFile(name: String, width: Int, height: Int): File {
        val file = File(compose.activity.cacheDir, name)
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
    if (this is ViewGroup) for (index in 0 until childCount) {
        getChildAt(index).findView(type)?.let { return it }
    }
    return null
}

private fun sample(id: Int, url: String) = Post(id = id, uid = id.toLong(), index = id - 1, booruUid = 1,
    query = "fixture", width = 1600, height = 900, size = 100, score = 0, rating = "s", time = 0,
    tags = emptyList(), preview = "", sample = url, medium = url, origin = url,
    source = null, uploader = User(name = "fixture"))
