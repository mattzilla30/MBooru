package com.mattzilla30.mbooru.ui.compose.settings

import android.content.SharedPreferences

/** Uses the same SharedPreferences as the XML screens and the networking/data layers. */
class SettingsStore(private val preferences: SharedPreferences) : AutoCloseable {
    val persistenceFailed: kotlinx.coroutines.flow.StateFlow<Boolean> =
        (preferences as? com.mattzilla30.mbooru.data.settings.DataStorePreferences)?.persistenceFailed
            ?: kotlinx.coroutines.flow.MutableStateFlow(false)
    fun retryPersistence() { (preferences as? com.mattzilla30.mbooru.data.settings.DataStorePreferences)?.retryPersistence() }
    var onChanged: ((String) -> Unit)? = null

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key in settingsByKey) onChanged?.invoke(key)
    }

    init {
        // Preference inflation used to persist these defaults, including hidden controls.
        val editor = preferences.edit()
        settingsByKey.values.filterNot { preferences.contains(it.key) }.forEach { spec ->
            when (spec) {
                is SettingSpec.Choice -> editor.putString(spec.key, spec.defaultValue)
                is SettingSpec.Toggle -> editor.putBoolean(spec.key, spec.defaultValue)
                is SettingSpec.Action -> Unit
            }
        }
        editor.apply()
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun snapshot(): Map<String, String> = settingsByKey.mapValues { (_, spec) ->
        when (spec) {
            is SettingSpec.Toggle -> preferences.getBoolean(spec.key, spec.defaultValue).toString()
            is SettingSpec.Choice -> preferences.getString(spec.key, spec.defaultValue) ?: spec.defaultValue
            is SettingSpec.Action -> preferences.getString(spec.key, "") ?: ""
        }
    }

    fun setToggle(key: String, checked: Boolean) {
        require(settingsByKey[key] is SettingSpec.Toggle)
        preferences.edit().putBoolean(key, checked).apply()
    }

    fun setChoice(key: String, value: String, allowedValues: List<String>) {
        require(settingsByKey[key] is SettingSpec.Choice && value in allowedValues)
        preferences.edit().putString(key, value).apply()
    }

    override fun close() {
        onChanged = null
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
