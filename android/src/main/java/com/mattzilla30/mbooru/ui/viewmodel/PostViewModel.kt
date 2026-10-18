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

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.repository.post.PostRepository

class PostViewModel(private val repository: PostRepository) : androidx.lifecycle.ViewModel() {
    private val actionState = MutableStateFlow<ActionPost?>(null)
    val action = actionState.asStateFlow()
    val posts = actionState.flatMapLatest { action ->
        if (action == null) flowOf(PagingData.empty()) else repository.getPosts(action)
    }.cachedIn(viewModelScope)

    fun show(action: ActionPost) {
        // Snapshot mutable action objects so preference/date changes invalidate paging reliably.
        actionState.value = action.copy(booru = action.booru.copy(), date = action.date.copy())
    }
}
