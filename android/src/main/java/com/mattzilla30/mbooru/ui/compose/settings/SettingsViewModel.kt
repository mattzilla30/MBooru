package com.mattzilla30.mbooru.ui.compose.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.app.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SettingsEffect {
    data class NightMode(val mode: Int) : SettingsEffect
    data object NightTheme : SettingsEffect
}

class SettingsViewModel(
    private val store: SettingsStore,
    private val savedState: SavedStateHandle,
    private val clearCache: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState(
        values = store.snapshot(),
        expanded = savedState["expanded"] ?: false,
        restartKeys = savedState.get<ArrayList<String>>("restartKeys")?.toSet().orEmpty(),
    ))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()
    private val effectChannel = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { store.persistenceFailed.collect { failed -> _state.update { it.copy(persistenceFailed = failed) } } }
        store.onChanged = { key ->
            val values = store.snapshot()
            val restartKeys = when (key) {
                Settings.DNS_OVER_HTTPS, Settings.DNS_OVER_HTTPS_PROVIDER -> setOf(Settings.DNS_OVER_HTTPS)
                Settings.DISABLE_SNI_KEY, Settings.BYPASS_WAF_KEY -> setOf(key)
                else -> emptySet()
            }
            _state.update { it.copy(values = values, restartKeys = it.restartKeys + restartKeys) }
            savedState["restartKeys"] = ArrayList(_state.value.restartKeys)
            when (key) {
                Settings.NIGHT_MODE_KEY -> effectChannel.trySend(SettingsEffect.NightMode(
                    when (values[key]) {
                        "on" -> AppCompatDelegate.MODE_NIGHT_YES
                        "off" -> AppCompatDelegate.MODE_NIGHT_NO
                        "battery" -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    }
                ))
                Settings.NIGHT_THEME_KEY -> effectChannel.trySend(SettingsEffect.NightTheme)
            }
        }
    }

    fun retrySave() = store.retryPersistence()

    fun toggle(key: String, checked: Boolean) = store.setToggle(key, checked)

    fun choose(key: String, value: String, allowedValues: List<String>) =
        store.setChoice(key, value, allowedValues)

    fun expand() {
        savedState["expanded"] = true
        _state.update { it.copy(expanded = true) }
    }

    fun confirmClearCache() {
        if (_state.value.clearingCache) return
        _state.update { it.copy(clearingCache = true, cacheClearFailed = false) }
        viewModelScope.launch {
            try {
                clearCache()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(cacheClearFailed = true) }
            } finally {
                _state.update { it.copy(clearingCache = false) }
            }
        }
    }

    override fun onCleared() {
        store.close()
        effectChannel.close()
    }
}
