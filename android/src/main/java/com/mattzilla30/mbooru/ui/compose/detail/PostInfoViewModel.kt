package com.mattzilla30.mbooru.ui.compose.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.database.dao.PostDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class PostInfoViewModel(dao: PostDao, booruUid: Long, postId: Int) : ViewModel() {
    val post = dao.getFirstPostLiveData(booruUid, postId).asFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
