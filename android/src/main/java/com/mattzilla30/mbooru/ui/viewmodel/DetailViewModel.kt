/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.mattzilla30.mbooru.ui.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.mattzilla30.mbooru.data.database.dao.PostDao

class DetailViewModel(postDao: PostDao, booruUid: Long, query: String) : androidx.lifecycle.ViewModel() {

    val posts = Pager(
        config = PagingConfig(
            pageSize = 30,
            enablePlaceholders = true
        )
    ) {
        postDao.getPosts(booruUid, query)
    }
        .flow
        .cachedIn(viewModelScope)

    private val mutable = MutableStateFlow(DetailUiState())
    val state = mutable.asStateFlow()
    var currentPosition: Int
        get() = mutable.value.currentPosition
        set(value) { mutable.update { it.copy(currentPosition = value) } }
    fun toggleBars() { mutable.update { it.copy(barsVisible = !it.barsVisible) } }
    fun longActions(show: Boolean) { mutable.update { it.copy(longActions = show) } }
    fun dismissing(value: Boolean) { mutable.update { it.copy(dismissing = value, backgroundAlpha = if (value) 0f else 1f) } }
    fun favorite(postId: Int, value: Boolean) { mutable.update { it.copy(favorites = it.favorites + (postId to value)) } }
}

data class DetailUiState(val currentPosition: Int = -1, val barsVisible: Boolean = true,
    val backgroundAlpha: Float = 1f, val dismissing: Boolean = false,
    val longActions: Boolean = false, val favorites: Map<Int, Boolean> = emptyMap())
