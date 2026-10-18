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

import kotlinx.coroutines.flow.*
import com.mattzilla30.mbooru.app.Settings
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import com.mattzilla30.mbooru.data.database.dao.MuzeiDao
import com.mattzilla30.mbooru.data.model.common.Muzei

class MuzeiViewModel(private val muzeiDao: MuzeiDao) : androidx.lifecycle.ViewModel() {

    private val siteUid = MutableStateFlow(-1L)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries = siteUid.flatMapLatest { muzeiDao.observeMuzei(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selected = MutableStateFlow(-1L)
    val selectedUid = selected.asStateFlow()
    private val text = MutableStateFlow("")
    val query = text.asStateFlow()
    private val search = MutableStateFlow(false)
    val searching = search.asStateFlow()
    fun query(value: String) { text.value = value }
    fun searching(value: Boolean) { search.value = value }
    fun select(uid: Long) { selected.value = uid; Settings.activeMuzeiUid = uid }

    fun loadMuzei(booruUid: Long) { siteUid.value = booruUid }

    fun create(muzei: Muzei) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                muzeiDao.insert(muzei)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }

    fun deleteByUid(uid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                muzeiDao.deleteByUid(uid)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }
}