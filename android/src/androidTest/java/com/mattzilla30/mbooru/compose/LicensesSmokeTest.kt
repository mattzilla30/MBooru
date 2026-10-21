package com.mattzilla30.mbooru.compose

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.activity.AboutActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<AboutActivity>()

    private var previousHardwareKeyboardSetting: String? = null

    @Before
    fun enableSoftKeyboard() {
        previousHardwareKeyboardSetting =
            shell("settings get secure show_ime_with_hard_keyboard").trim().takeIf {
                it == "0" || it == "1"
            }
        shell("settings put secure show_ime_with_hard_keyboard 1")
    }

    @After
    fun restoreKeyboardSetting() {
        val previous = previousHardwareKeyboardSetting
        if (previous == null) shell("settings delete secure show_ime_with_hard_keyboard")
        else shell("settings put secure show_ime_with_hard_keyboard $previous")
    }

    @Test
    fun aboutOpensSearchableLicensesAndBackReturnsToList() {
        val label = compose.activity.getString(R.string.about_open_source_licenses)
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("about-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("about-list").performScrollToNode(hasText(label))
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(20000) {
            compose.onAllNodesWithTag("license-search").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("license-search").performClick()
        compose.waitUntil(10000) { keyboardVisible() }
        compose.onNodeWithTag("license-search").performTextReplacement("PhotoView (Apache")
        compose.onNodeWithText("PhotoView (Apache 2.0)").performClick()
        compose.onNodeWithTag("license-text").assertIsDisplayed()
        compose.onNode(hasText("TERMS AND CONDITIONS", substring = true)).assertExists()
        compose.waitUntil(10000) { !keyboardVisible() }
        Espresso.pressBack()
        compose.onNodeWithTag("license-list").assertIsDisplayed()
        compose.onNodeWithText("PhotoView (Apache 2.0)").assertExists()
    }

    // Insets describe the requested end state before Android 16's IME animation completes.
    // The IME service's window visibility tracks the real keyboard window instead.
    private fun keyboardVisible(): Boolean {
        val diagnostic = shell("dumpsys input_method")
        // Older releases expose the actual window on the IME service, and API 28's
        // manager prints symbolic flags rather than the numeric Android 16 field.
        if (android.os.Build.VERSION.SDK_INT < 29) {
            val windows = Regex("mWindowVisible=(true|false)").findAll(diagnostic).toList()
            check(windows.isNotEmpty()) { "IME service window visibility was unavailable" }
            return windows.any { it.groupValues[1] == "true" }
        }
        val visibility =
            Regex("mImeWindowVis=(?:0x)?([0-9a-fA-F]+)")
                .findAll(diagnostic)
                .map { it.groupValues[1].toInt(16) }
                .toList()
        check(visibility.isNotEmpty()) { "IME window visibility was unavailable" }
        return visibility.any { it and 2 != 0 } // InputMethodService.IME_VISIBLE
    }

    private fun shell(command: String): String {
        val automation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand(command)
            )
            .bufferedReader()
            .use { it.readText() }
    }
}
