package com.mattzilla30.mbooru.extension

import android.app.Application
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [30], manifest = Config.NONE)
class WindowInsetsTest {
    @Test fun `bars cutouts and keyboard share a safe area without accumulating padding`() {
        val view = View(ApplicationProvider.getApplicationContext<Application>())
        view.setPadding(1, 2, 3, 4)
        view.applySafeDrawingInsets()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(80, 0, 0, 0))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 300))
            .build()
        repeat(2) {
            val remaining = ViewCompat.dispatchApplyWindowInsets(view, insets)
            assertEquals(81, view.paddingLeft)
            assertEquals(26, view.paddingTop)
            assertEquals(3, view.paddingRight)
            assertEquals(304, view.paddingBottom)
            assertTrue(remaining.isConsumed)
        }
        val keyboardHidden = WindowInsetsCompat.Builder(insets)
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.NONE).build()
        ViewCompat.dispatchApplyWindowInsets(view, keyboardHidden)
        assertEquals(52, view.paddingBottom)
    }

    @Test fun `rotation removes the previous cutout padding`() {
        val view = View(ApplicationProvider.getApplicationContext<Application>())
        view.applySafeDrawingInsets()
        ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(80, 0, 0, 0)).build())
        assertEquals(80, view.paddingLeft)
        ViewCompat.dispatchApplyWindowInsets(view, WindowInsetsCompat.Builder().build())
        assertEquals(0, view.paddingLeft)
    }
}
