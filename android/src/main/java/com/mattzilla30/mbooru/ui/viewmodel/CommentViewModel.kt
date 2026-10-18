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
import kotlinx.coroutines.launch
import com.mattzilla30.mbooru.data.action.ActionComment
import com.mattzilla30.mbooru.data.repository.comment.CommentRepository
import com.mattzilla30.mbooru.extension.NetResult

data class CommentReply(val postId: Int, val text: String)

class CommentViewModel(private val repository: CommentRepository) : androidx.lifecycle.ViewModel() {
    private val actions = MutableStateFlow<ActionComment?>(null)
    val action = actions.asStateFlow()
    private val refresh = MutableStateFlow(0)
    private val results = Channel<NetResult<Boolean>>(Channel.BUFFERED)
    val mutationResults = results.receiveAsFlow()
    private val replyState = MutableStateFlow<CommentReply?>(null)
    val reply = replyState.asStateFlow()
    val comments = combine(actions, refresh) { action, _ -> action }.flatMapLatest { action ->
        if (action == null) flowOf(PagingData.empty()) else repository.getComments(action)
    }.cachedIn(viewModelScope)
    fun show(action: ActionComment): Boolean {
        val snapshot = action.copy(booru = action.booru.copy())
        if (actions.value == snapshot) return false
        actions.value = snapshot; return true
    }
    fun reply(postId: Int, text: String = "") { replyState.value = CommentReply(postId, text) }
    fun editReply(text: String) { replyState.update { it?.copy(text = text) } }
    fun dismissReply() { replyState.value = null }
    fun sendReply() {
        val draft = reply.value ?: return
        val action = actions.value ?: return
        if (draft.text.isBlank()) return
        createCommment(action.copy(postId = draft.postId, body = draft.text.trim()))
        dismissReply()
    }
    fun createCommment(action: ActionComment) {
        viewModelScope.launch {
            val result = repository.createComment(action)
            if (result is NetResult.Success) refresh.value++
            results.send(result)
        }
    }
    fun deleteComment(action: ActionComment) {
        viewModelScope.launch {
            val result = repository.destroyComment(action)
            if (result is NetResult.Success) refresh.value++
            results.send(result)
        }
    }
}
