package com.mattzilla30.mbooru.ui.compose.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.view.ContextThemeWrapper
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import com.mattzilla30.mbooru.R
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28])
class ThemeBridgeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `light palette uses BGSU cream and brown`() = verify(R.style.AppTheme, false)

    @Test fun `dark palette uses warm BGSU surfaces`() = verify(R.style.AppTheme, true)

    @Test fun `black night surfaces remain black`() = verify(R.style.AppTheme_Black, true)

    @Test
    @Config(sdk = [31])
    fun `Android 12 keeps the same BGSU palette`() = verify(R.style.AppTheme, false)

    private fun verify(theme: Int, night: Boolean) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration =
            Configuration(base.resources.configuration).apply {
                uiMode =
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES
                        else Configuration.UI_MODE_NIGHT_NO
            }
        val context = ContextThemeWrapper(base.createConfigurationContext(configuration), theme)
        var colors: ColorScheme? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
            ) {
                MBooruTheme {
                    val current = MaterialTheme.colorScheme
                    SideEffect { colors = current }
                    Text("Theme")
                }
            }
        }
        compose.runOnIdle {
            val palette = requireNotNull(colors)
            assertEquals(parse("#FD5000"), palette.primaryContainer.toArgb())
            assertNotEquals(
                "Selections must remain visible against the page background",
                palette.background.toArgb(),
                palette.secondaryContainer.toArgb(),
            )
            assertEquals(parse(if (night) "#FFB58C" else "#A83300"), palette.primary.toArgb())
            assertEquals(parse(if (night) "#F9DFC0" else "#4F2C1D"), palette.onSurface.toArgb())
            val black = theme == R.style.AppTheme_Black && night
            assertEquals(
                parse(if (black) "#000000" else if (night) "#20150F" else "#F2D6A2"),
                palette.background.toArgb(),
            )
            if (black) assertEquals(AndroidColor.BLACK, palette.surface.toArgb())
            assertEquals(
                themeColor(context, MaterialR.attr.colorSurfaceContainerHigh),
                palette.surfaceContainerHigh.toArgb(),
            )
            assertEquals(
                themeColor(context, MaterialR.attr.colorOnErrorContainer),
                palette.onErrorContainer.toArgb(),
            )
            listOf(
                    "body" to (palette.onSurface to palette.surface),
                    "background" to (palette.onBackground to palette.background),
                    "secondary text" to (palette.onSurfaceVariant to palette.surfaceVariant),
                    "links" to (palette.primary to palette.background),
                    "primary button" to (palette.onPrimary to palette.primary),
                    "orange header" to (palette.onPrimaryContainer to palette.primaryContainer),
                    "selection" to (palette.onSecondaryContainer to palette.secondaryContainer),
                    "dialog" to (palette.onSurface to palette.surfaceContainerHigh),
                    "error" to (palette.onErrorContainer to palette.errorContainer),
                )
                .forEach { (name, pair) -> assertReadable(name, pair.first, pair.second) }
            listOf(R.style.AlertDialog_DayNight, R.style.PopupOverlay).forEach { overlay ->
                val overlayContext = ContextThemeWrapper(context, overlay)
                assertEquals(
                    "Dialog and menu accents follow the activity",
                    palette.primary.toArgb(),
                    themeColor(overlayContext, MaterialR.attr.colorPrimary),
                )
                assertEquals(
                    "Dialog and menu surfaces follow the activity",
                    palette.surface.toArgb(),
                    themeColor(overlayContext, MaterialR.attr.colorSurface),
                )
            }
        }
    }

    private fun themeColor(context: Context, attribute: Int): Int =
        MaterialColors.getColor(context, attribute, 0)

    private fun parse(color: String) = AndroidColor.parseColor(color)

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val contrast = ColorUtils.calculateContrast(foreground.toArgb(), background.toArgb())
        assertTrue("$name contrast was $contrast", contrast >= 4.5)
    }
}
