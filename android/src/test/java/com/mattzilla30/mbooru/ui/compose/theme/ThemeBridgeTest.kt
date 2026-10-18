package com.mattzilla30.mbooru.ui.compose.theme

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
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

    @Test
    fun `light palette comes from the existing Android theme`() = verify(R.style.AppTheme, false)

    @Test fun `black night background remains black`() = verify(R.style.AppTheme_Black, true)

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
        var background = 0
        var primary = 0
        var onSurface = 0
        var surface = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides configuration,
            ) {
                MBooruTheme {
                    val colors = MaterialTheme.colorScheme
                    SideEffect {
                        background = colors.background.toArgb()
                        primary = colors.primary.toArgb()
                        onSurface = colors.onSurface.toArgb()
                        surface = colors.surface.toArgb()
                    }
                    Text("Theme")
                }
            }
        }
        compose.runOnIdle {
            assertEquals(
                MaterialColors.getColor(context, android.R.attr.colorBackground, 0),
                background,
            )
            assertEquals(
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorPrimary,
                    0,
                ),
                primary,
            )
            assertTrue(
                "Body text contrast",
                androidx.core.graphics.ColorUtils.calculateContrast(onSurface, surface) >= 4.5,
            )
            if (night) assertEquals(android.graphics.Color.BLACK, background)
        }
    }
}
