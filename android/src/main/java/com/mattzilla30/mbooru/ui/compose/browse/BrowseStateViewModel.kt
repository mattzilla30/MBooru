package com.mattzilla30.mbooru.ui.compose.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class BrowseUiState(
    val query: String = "",
    val mode: Int = 0,
    val title: String = "",
    val menu: AppMenu = AppMenu.None,
    val booru: Booru? = null,
    val preferencesRevision: Int = 0,
)

interface BrowseNavigation {
    fun toListTop()
}

class BrowseStateViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val mutable =
        MutableStateFlow(
            BrowseUiState(
                query = saved["query"] ?: "",
                mode = saved["mode"] ?: 0,
            )
        )
    val state = mutable.asStateFlow()
    private val positions = Channel<Int>(Channel.CONFLATED)
    val scrollPositions = positions.receiveAsFlow()

    fun query(text: String) {
        saved["query"] = text
        mutable.update { it.copy(query = text) }
    }

    fun mode(value: Int) {
        saved["mode"] = value
        mutable.update { it.copy(mode = value) }
    }

    fun title(value: String) {
        mutable.update { it.copy(title = value) }
    }

    fun menu(value: AppMenu) {
        mutable.update { it.copy(menu = value) }
    }

    fun booru(value: Booru?) {
        mutable.update { it.copy(booru = value) }
    }

    fun preferencesChanged() {
        mutable.update { it.copy(preferencesRevision = it.preferencesRevision + 1) }
    }

    fun scrollTo(position: Int) {
        positions.trySend(position.coerceAtLeast(0))
    }
}
