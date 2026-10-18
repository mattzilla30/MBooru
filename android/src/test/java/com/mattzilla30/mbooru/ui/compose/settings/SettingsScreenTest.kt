package com.mattzilla30.mbooru.ui.compose.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    sdk = [28],
    manifest = Config.NONE,
    qualifiers = "en-rUS-notnight",
)
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `switch clicks update state and expansion reveals the remaining controls`() {
        val state = mutableStateOf(SettingsUiState())
        compose.setContent {
            MBooruTheme {
                SettingsScreen(
                    state.value,
                    onToggle = { key, checked ->
                        state.value =
                            state.value.copy(
                                values = state.value.values + (key to checked.toString())
                            )
                    },
                    onChoose = { _, _, _ -> },
                    onExpand = { state.value = state.value.copy(expanded = true) },
                    onPickDirectory = {},
                    onClearCache = {},
                )
            }
        }
        compose.onNodeWithTag(Settings.GRID_ROUNDED_KEY).assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_expand"))
        compose.onNodeWithTag("settings_expand").performClick()
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.SAFE_MODE_KEY))
        compose.onNodeWithTag(Settings.SAFE_MODE_KEY).assertIsOn().performClick().assertIsOff()
    }

    @Test
    fun `choice dialog survives recreation and sends the selected value`() {
        val restore = StateRestorationTester(compose)
        var selection: String? = null
        restore.setContent {
            MBooruTheme {
                SettingsScreen(
                    SettingsUiState(),
                    onToggle = { _, _ -> },
                    onChoose = { _, value, _ -> selection = value },
                    onExpand = {},
                    onPickDirectory = {},
                    onClearCache = {},
                )
            }
        }
        compose.onNodeWithTag(Settings.GRID_MODE_KEY).performClick()
        compose.onNodeWithText("Fixed ratio").assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Fixed ratio").performClick()
        compose.runOnIdle { assertEquals("fixed", selection) }
    }

    @Test
    fun `directory action and cache confirmation deliver callbacks only when requested`() {
        var picked = 0
        var cleared = 0
        compose.setContent {
            MBooruTheme {
                SettingsScreen(
                    SettingsUiState(expanded = true),
                    onToggle = { _, _ -> },
                    onChoose = { _, _, _ -> },
                    onExpand = {},
                    onPickDirectory = { picked++ },
                    onClearCache = { cleared++ },
                )
            }
        }
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.DOWNLOAD_PATH_KEY))
        compose.onNodeWithTag(Settings.DOWNLOAD_PATH_KEY).performClick()
        compose.runOnIdle { assertEquals(1, picked) }
        compose
            .onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag(Settings.CLEAR_CACHE_KEY))
        compose.onNodeWithTag(Settings.CLEAR_CACHE_KEY).performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, cleared) }
        compose.onNodeWithTag(Settings.CLEAR_CACHE_KEY).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { assertEquals(1, cleared) }
    }
}
