package com.mattzilla30.mbooru.visual

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ContextThemeWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.compose.settings.*
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Goldens deliberately use one fixed API/density/font renderer, not cross-OS pixel comparisons. */
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28, maxSdkVersion = 28)
class SettingsVisualTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phoneLight() = capture("settings-phone-light")

    @Test fun phoneDark() = capture("settings-phone-dark", dark = true)

    @Test fun largeTextRtl() = capture("settings-large-text-rtl", font = 2f, rtl = true)

    @Test fun tablet() = capture("settings-tablet", width = 720)

    private fun capture(
        name: String,
        dark: Boolean = false,
        font: Float = 1f,
        rtl: Boolean = false,
        width: Int = 360,
    ) {
        val config =
            Configuration(compose.activity.resources.configuration).apply {
                uiMode =
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (dark) Configuration.UI_MODE_NIGHT_YES
                        else Configuration.UI_MODE_NIGHT_NO
            }
        val themed =
            ContextThemeWrapper(
                compose.activity.createConfigurationContext(config),
                R.style.AppTheme,
            )
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides themed,
                LocalConfiguration provides config,
                LocalDensity provides Density(1f, font),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MBooruTheme {
                    Surface(Modifier.requiredSize(width.dp, 640.dp).testTag("golden-frame")) {
                        SettingsScreen(
                            SettingsUiState(expanded = true),
                            onToggle = { _, _ -> },
                            onChoose = { _, _, _ -> },
                            onExpand = {},
                            onPickDirectory = {},
                            onClearCache = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val image = compose.onNodeWithTag("golden-frame").captureToImage().asAndroidBitmap()
        val output =
            File(compose.activity.getExternalFilesDir(null), "screenshots/$name.png").apply {
                parentFile!!.mkdirs()
            }
        output.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val expected =
            instrumentation.context.assets
                .open("goldens/$name.png")
                .use(BitmapFactory::decodeStream)
        assertEquals("Golden width", expected.width, image.width)
        assertEquals("Golden height", expected.height, image.height)
        val pixels = IntArray(image.width * image.height)
        val golden = IntArray(pixels.size)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        expected.getPixels(golden, 0, image.width, 0, 0, image.width, image.height)
        val differing = pixels.indices.count { pixels[it] != golden[it] }
        assertTrue(
            "Screenshot differs: $differing/${pixels.size}; review the actual PNG before updating the golden",
            differing.toFloat() / pixels.size < .005f,
        )
        // Large text and RTL must retain the final actionable setting through scrolling.
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.CLEAR_CACHE_KEY))
        compose.onNodeWithTag(Settings.CLEAR_CACHE_KEY).assertIsDisplayed().assertHasClickAction()
    }
}
