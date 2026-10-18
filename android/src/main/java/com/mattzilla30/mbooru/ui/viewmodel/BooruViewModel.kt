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

class BooruViewModel(private val booruDao: BooruDao) : ScopeViewModel() {

    private val selected = MutableStateFlow(-1L)
    val selectedUid = selected.asStateFlow()
    fun select(uid: Long) { selected.value = uid }

    private val _boorus = booruDao.getAllLiveData()
    val booruType get() = booru.value?.type ?: Values.BOORU_TYPE_UNKNOWN
    private val _uid = MutableLiveData<Long>(-1)
    val booru: LiveData<Booru?> = _uid.switchMap { uid -> booruDao.getBooruByUidLiveData(uid) }
    val booruState = booru.asFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val boorusState = _boorus.asFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val currentBooru: Booru?
        get() = booru.value

    fun loadBoorus(): LiveData<List<Booru>> = _boorus

    fun loadBooru(uid: Long): Boolean {
        if (_uid.value == uid) return false
        _uid.value = uid
        return true
    }

    fun createBooru(booru: Booru): Long {
        return try {
            booruDao.insert(booru)
        } catch (_: Exception) {
            -1L
        }
    }

    fun createBoorus(boorus: List<Booru>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.insert(boorus)
            } catch (_: Exception) {

            }
        }
    }

    fun isNotEmpty() = booruDao.isNotEmpty()

    fun updateBooru(booru: Booru) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.update(booru)
            } catch (_: Exception) {}
        }
    }

    fun deleteBooru(uid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                booruDao.delete(uid)
            } catch (_: Exception) {}
        }
    }
}