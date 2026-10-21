package com.mattzilla30.mbooru.ui.compose.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.Settings
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [28], manifest = Config.NONE)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferences: SharedPreferences
    private lateinit var store: SettingsStore
    private val owner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        preferences =
            ApplicationProvider.getApplicationContext<Context>()
                .getSharedPreferences("compose-settings-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
    }

    @After
    fun teardown() {
        owner.viewModelStore.clear()
        if (::store.isInitialized) store.close()
        Dispatchers.resetMain()
    }

    private fun createModel(
        saved: SavedStateHandle = SavedStateHandle(),
        clear: suspend () -> Unit = {},
    ): SettingsViewModel {
        store = SettingsStore(preferences)
        return ViewModelProvider(
            owner,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(store, saved, clear) as T
            },
        )[SettingsViewModel::class.java]
    }

    @Test
    fun `remaining legacy controls retain keys defaults labels and choice arrays`() {
        store = SettingsStore(preferences)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stream = checkNotNull(javaClass.getResourceAsStream("/legacy_settings.xml"))
        val document = stream.use {
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
        }
        val controls = document.getElementsByTagName("PreferenceCategory")
        val keys = mutableSetOf<String>()
        for (i in 0 until controls.length) {
            val children = controls.item(i).childNodes
            for (j in 0 until children.length) {
                val element = children.item(j) as? Element ?: continue
                val key = element.getAttribute("app:key")
                if (key.startsWith("settings_muzei") || key == "settings_auto_hide_bottom_bar")
                    continue
                keys += key
                val spec = checkNotNull(settingsByKey[key])
                assertEquals(
                    element.getAttribute("app:title").substringAfter('/'),
                    context.resources.getResourceEntryName(spec.title),
                )
                when (spec) {
                    is SettingSpec.Toggle ->
                        assertEquals(
                            element.getAttribute("app:defaultValue").toBoolean(),
                            preferences.getBoolean(key, !spec.defaultValue),
                        )
                    is SettingSpec.Choice -> {
                        assertEquals(
                            element.getAttribute("app:defaultValue"),
                            preferences.getString(key, null),
                        )
                        assertEquals(
                            element.getAttribute("app:entries").substringAfter('/'),
                            context.resources.getResourceEntryName(spec.entries),
                        )
                        assertEquals(
                            element.getAttribute("app:entryValues").substringAfter('/'),
                            context.resources.getResourceEntryName(spec.entryValues),
                        )
                        val values = context.resources.getStringArray(spec.entryValues)
                        assertTrue(spec.defaultValue in values)
                        assertEquals(
                            values.size,
                            context.resources.getStringArray(spec.entries).size,
                        )
                    }
                    is SettingSpec.Action -> Unit
                }
            }
        }
        assertFalse(settingsByKey.containsKey("settings_auto_hide_bottom_bar"))
        assertEquals(keys, settingsByKey.keys)
    }

    @Test
    fun `existing preferences survive migration and changes use the existing storage`() {
        preferences
            .edit()
            .putString(Settings.GRID_MODE_KEY, "fixed")
            .putBoolean(Settings.SAFE_MODE_KEY, false)
            .commit()
        val model = createModel()
        assertEquals("fixed", model.state.value.values[Settings.GRID_MODE_KEY])
        assertEquals("false", model.state.value.values[Settings.SAFE_MODE_KEY])
        model.toggle(Settings.SAFE_MODE_KEY, true)
        assertTrue(preferences.getBoolean(Settings.SAFE_MODE_KEY, false))
        preferences.edit().putString(Settings.DOWNLOAD_PATH_KEY, "content://selected/tree").commit()
        assertEquals(
            "content://selected/tree",
            model.state.value.values[Settings.DOWNLOAD_PATH_KEY],
        )
    }

    @Test
    fun `grid ratio and DNS controls follow their dependencies`() {
        val model = createModel()
        fun visible(key: String) =
            model.state.value.isVisible(settingsByKey.getValue(key), night = false)
        assertFalse(visible(Settings.GRID_RATIO_KEY))
        assertFalse(visible(Settings.DNS_OVER_HTTPS_PROVIDER))
        assertFalse(visible(Settings.DISABLE_SNI_KEY))
        model.choose(Settings.GRID_MODE_KEY, "fixed", listOf("fixed", "staggered"))
        model.toggle(Settings.DNS_OVER_HTTPS, true)
        assertTrue(visible(Settings.GRID_RATIO_KEY))
        assertTrue(visible(Settings.DNS_OVER_HTTPS_PROVIDER))
        assertTrue(visible(Settings.DISABLE_SNI_KEY))
        assertFalse(visible(Settings.NIGHT_THEME_KEY))
        assertTrue(
            model.state.value.isVisible(
                settingsByKey.getValue(Settings.NIGHT_THEME_KEY),
                night = true,
            )
        )
    }

    @Test
    fun `expansion and restart notices survive recreation`() {
        val saved = SavedStateHandle()
        val model = createModel(saved)
        assertEquals(
            5,
            model.state.value.rows(false).filterIsInstance<SettingsRow.Preference>().size,
        )
        assertTrue(model.state.value.rows(false).last() is SettingsRow.Expand)
        model.expand()
        model.toggle(Settings.DNS_OVER_HTTPS, true)
        owner.viewModelStore.clear()
        val restored = createModel(saved)
        assertTrue(restored.state.value.expanded)
        assertTrue(Settings.DNS_OVER_HTTPS in restored.state.value.restartKeys)
        assertFalse(restored.state.value.rows(false).any { it is SettingsRow.Expand })
    }

    @Test
    fun `night changes emit one effect and do not replay on collection`() =
        runTest(dispatcher) {
            val model = createModel()
            model.choose(Settings.NIGHT_MODE_KEY, "on", listOf("on", "off", "system", "battery"))
            assertEquals(
                SettingsEffect.NightMode(AppCompatDelegate.MODE_NIGHT_YES),
                model.effects.first(),
            )
            val next = backgroundScope.async { model.effects.first() }
            runCurrent()
            assertFalse(next.isCompleted)
            model.choose(Settings.NIGHT_THEME_KEY, "dark", listOf("dark", "black"))
            assertEquals(SettingsEffect.NightTheme, next.await())
        }

    @Test
    fun `cache clear is single flight and exposes failures`() =
        runTest(dispatcher) {
            var calls = 0
            val pending = CompletableDeferred<Unit>()
            val model = createModel {
                calls++
                pending.await()
                error("cache unavailable")
            }
            model.confirmClearCache()
            model.confirmClearCache()
            runCurrent()
            assertEquals(1, calls)
            assertTrue(model.state.value.clearingCache)
            pending.complete(Unit)
            advanceUntilIdle()
            assertFalse(model.state.value.clearingCache)
            assertTrue(model.state.value.cacheClearFailed)
        }

    @Test
    fun `cache cancellation is not reported as an operation failure`() =
        runTest(dispatcher) {
            val model = createModel { throw kotlinx.coroutines.CancellationException() }
            model.confirmClearCache()
            advanceUntilIdle()
            assertFalse(model.state.value.cacheClearFailed)
            assertFalse(model.state.value.clearingCache)
        }

    @Test
    fun `clearing the viewmodel releases its preference listener`() {
        val model = createModel()
        val before = model.state.value
        owner.viewModelStore.clear()
        preferences.edit().putBoolean(Settings.SAFE_MODE_KEY, false).commit()
        assertEquals(before, model.state.value)
    }
}
