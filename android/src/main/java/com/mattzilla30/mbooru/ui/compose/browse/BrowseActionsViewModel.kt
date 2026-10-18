package com.mattzilla30.mbooru.ui.compose.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.database.TagFilterManager
import com.mattzilla30.mbooru.data.model.common.TagFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Mutations belong to the destination lifetime, rather than an Activity or remembered callback. */
class BrowseActionsViewModel(
    private val filters: TagFilterManager,
    private val favorites: com.mattzilla30.mbooru.data.database.dao.SavedPostDao,
) : ViewModel() {
    private val failures = Channel<Unit>(Channel.CONFLATED)
    val errors = failures.receiveAsFlow()

    fun addFilter(uid: Long, query: String) = perform {
        filters.createTagFilter(TagFilter(booruUid = uid, name = query))
    }

    fun removeFilter(tag: TagFilter) = perform { filters.deleteTagFilter(tag) }

    fun favorite(post: com.mattzilla30.mbooru.data.model.common.Post) = perform {
        favorites.toggle(post)
    }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures.trySend(Unit)
            }
        }
    }
}
