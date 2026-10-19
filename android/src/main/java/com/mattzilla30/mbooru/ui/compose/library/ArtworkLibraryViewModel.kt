// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.database.dao.LibraryDao
import com.mattzilla30.mbooru.data.model.common.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkLibraryViewModel(private val dao: LibraryDao) : ViewModel() {
    private val selected = MutableStateFlow<Post?>(null)
    val collections =
        dao.observeCollections()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS),
                emptyList(),
            )
    val saved =
        selected
            .flatMapLatest { post ->
                if (post == null) flowOf(false) else dao.observeSaved(post.booruUid, post.id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS), false)

    fun save() = change { selected.value?.let { dao.toggleSaved(it) } }

    val liked =
        selected
            .flatMapLatest { post ->
                if (post == null) flowOf(false) else dao.observeLike(post.booruUid, post.id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS), false)
    val memberships =
        selected
            .flatMapLatest { post ->
                if (post == null) flowOf(emptyList())
                else dao.observeCollectionsFor(post.booruUid, post.id)
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS),
                emptyList(),
            )
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()

    fun select(post: Post) {
        selected.value = post
        mutableMessage.value = ""
    }

    fun like() = change {
        selected.value?.let { dao.toggleLike(it) }
        Unit
    }

    fun membership(collection: Long, add: Boolean) = change {
        selected.value?.let { dao.setMembership(it, collection, add) }
    }

    fun create(name: String, complete: () -> Unit) = change {
        dao.createWithArtwork(name, selected.value)
        complete()
    }

    private fun change(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                mutableMessage.value = ""
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                mutableMessage.value = failure.message ?: "Invalid library operation"
            } catch (failure: IllegalStateException) {
                mutableMessage.value = failure.message ?: "Library operation failed"
            } catch (_: Exception) {
                mutableMessage.value = "Could not update the local library. Please retry."
            }
        }
    }
}

private const val OBSERVATION_TIMEOUT_MS = 5_000L
