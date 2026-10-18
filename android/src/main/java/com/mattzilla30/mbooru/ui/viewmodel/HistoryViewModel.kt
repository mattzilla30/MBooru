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

import androidx.lifecycle.asFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mattzilla30.mbooru.data.database.dao.HistoryDao
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.data.model.common.History

class HistoryViewModel(
    private val historyDao: HistoryDao,
    private val postDao: PostDao) : ScopeViewModel() {

    private val _historyOutcome: MediatorLiveData<List<History>> = MediatorLiveData()

    val history = _historyOutcome.asFlow().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadHistory(booruUid: Long): LiveData<List<History>> {
        viewModelScope.launch{
            val data = withContext(Dispatchers.IO) {
                historyDao.getHistoryByBooruUidLiveData(booruUid)
            }
            _historyOutcome.addSource(data) {
                _historyOutcome.postValue(it ?: listOf())
            }
        }
        return _historyOutcome
    }

    fun create(history: History) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.insert(history)
            } catch (_: Exception) {}
        }
    }

    fun deleteByUid(history: History) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.delete(history)
                postDao.deletePosts(booruUid = history.booruUid, query = history.query)
            } catch (_: Exception) {}
        }
    }

    fun deleteAll(booruUid: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.deleteByBooruUid(booruUid)
                postDao.deletePosts(booruUid = booruUid)
            } catch (_: Exception) {}
        }
    }
}