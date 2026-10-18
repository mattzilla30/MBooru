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

import androidx.lifecycle.*
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru

class BooruViewModel(private val booruDao: BooruDao) : androidx.lifecycle.ViewModel() {

    private val selected = MutableStateFlow(-1L)
    val selectedUid = selected.asStateFlow()
    fun select(uid: Long) { selected.value = uid }

    private val uid = MutableStateFlow(-1L)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val booruState = uid.flatMapLatest(booruDao::observeByUid)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val boorusState = booruDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val booruType get() = booruState.value?.type ?: Values.BOORU_TYPE_UNKNOWN
    val currentBooru get() = booruState.value
    fun loadBoorus() = booruDao.observeAll()
    fun loadBooru(value: Long): Boolean {
        if (uid.value == value) return false
        uid.value = value
        return true
    }

    fun createBooru(booru: Booru) {
        viewModelScope.launch { booruDao.insert(booru) }
    }

    fun createBoorus(boorus: List<Booru>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.insert(boorus)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure

            }
        }
    }

    suspend fun isNotEmpty() = booruDao.isNotEmpty()

    fun updateBooru(booru: Booru) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.update(booru)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }

    fun deleteBooru(uid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.delete(uid)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }
}
