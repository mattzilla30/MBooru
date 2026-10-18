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

import com.mattzilla30.mbooru.extension.safeNetworkMessage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.mattzilla30.mbooru.extension.NetResult
import com.mattzilla30.mbooru.common.tracemoe.api.TraceMoeApi
import com.mattzilla30.mbooru.common.tracemoe.model.TraceResponse

class TraceMoeViewModel(private val traceMoeApi: TraceMoeApi) : androidx.lifecycle.ViewModel() {

    private var retryRequest: (() -> Unit)? = null
    fun retry() { if (!_loading.value) retryRequest?.invoke() }

    private val mutex = Mutex()

    private val _data = MutableStateFlow<TraceResponse?>(null)
    val data = _data.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val isLoading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun fetch(imageBlob: ByteArray) {
        retryRequest = { fetch(imageBlob) }
        viewModelScope.launch {
            mutex.withLock {
                _loading.value = true
                _error.value = null
                val result = withContext(Dispatchers.IO) {
                    try {
                        val response = traceMoeApi.fetch(imageBlob)
                        NetResult.Success(response)
                    } catch (cancelled: CancellationException) { throw cancelled } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                        NetResult.Error(e.safeNetworkMessage())
                    }
                }
                _loading.value = false
                when (result) {
                    is NetResult.Success -> {
                        _data.value = result.data
                    }
                    is NetResult.Error -> {
                        _error.value = result.errorMsg
                    }
                }
            }
        }
    }
}