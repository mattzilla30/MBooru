package com.mattzilla30.mbooru.ui.compose.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.action.ActionVote
import com.mattzilla30.mbooru.data.database.MuzeiManager
import com.mattzilla30.mbooru.data.database.TagFilterManager
import com.mattzilla30.mbooru.data.model.common.Muzei
import com.mattzilla30.mbooru.data.model.common.TagFilter
import com.mattzilla30.mbooru.data.repository.favorite.VoteRepository
import com.mattzilla30.mbooru.extension.NetResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Mutations belong to the destination lifetime, rather than an Activity or remembered callback. */
class BrowseActionsViewModel(
    private val wallpapers: MuzeiManager,
    private val filters: TagFilterManager,
    private val votes: VoteRepository,
) : ViewModel() {
    private val failures = Channel<Unit>(Channel.CONFLATED)
    val errors = failures.receiveAsFlow()

    fun addWallpaper(uid: Long, query: String) = perform {
        wallpapers.createMuzei(Muzei(booruUid = uid, query = query))
    }

    fun addFilter(uid: Long, query: String) = perform {
        filters.createTagFilter(TagFilter(booruUid = uid, name = query))
    }

    fun removeFilter(tag: TagFilter) = perform { filters.deleteTagFilter(tag) }

    fun favorite(action: ActionVote) = perform {
        if (votes.addFav(action) is NetResult.Error) failures.trySend(Unit)
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
