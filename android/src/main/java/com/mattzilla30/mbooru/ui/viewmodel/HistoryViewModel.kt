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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mattzilla30.mbooru.data.database.dao.HistoryDao
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.data.model.common.History

class HistoryViewModel(
    private val historyDao: HistoryDao,
    private val postDao: PostDao) : androidx.lifecycle.ViewModel() {

    private val siteUid = MutableStateFlow(-1L)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history = siteUid.flatMapLatest { historyDao.observeHistory(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadHistory(booruUid: Long) { siteUid.value = booruUid }

    fun create(history: History) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.insert(history)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }

    fun deleteByUid(history: History) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.delete(history)
                postDao.deletePosts(booruUid = history.booruUid, query = history.query)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }

    fun deleteAll(booruUid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.deleteByBooruUid(booruUid)
                postDao.deletePosts(booruUid = booruUid)
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure}
        }
    }
}
