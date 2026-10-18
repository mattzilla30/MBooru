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
import com.mattzilla30.mbooru.data.action.ActionPool
import com.mattzilla30.mbooru.data.repository.pool.PoolRepository

class PoolViewModel(private val repository: PoolRepository) : ScopeViewModel() {
    private val actions = MutableStateFlow<ActionPool?>(null)
    val state = actions.asStateFlow()
    val pools = actions.flatMapLatest { action ->
        if (action == null) flowOf(PagingData.empty()) else repository.getPools(action)
    }.cachedIn(viewModelScope)

    fun show(action: ActionPool): Boolean {
        val snapshot = action.copy(booru = action.booru.copy())
        if (actions.value == snapshot) return false
        actions.value = snapshot
        return true
    }
}
