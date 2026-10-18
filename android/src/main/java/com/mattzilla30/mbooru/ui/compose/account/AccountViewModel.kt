package com.mattzilla30.mbooru.ui.compose.account


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.User
import com.mattzilla30.mbooru.data.repository.user.UserRepository
import com.mattzilla30.mbooru.extension.NetResult
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class AccountViewModel(private val repository: UserRepository, private val booru: Booru, initial: User?, private val booruManager: BooruManager) : ViewModel() {

    private val mutable = MutableStateFlow(initial)
    val user = mutable.asStateFlow()
    private val message = MutableStateFlow<String?>(null)
    val error = message.asStateFlow()
    private val removed = Channel<Unit>(Channel.BUFFERED)
    val removal = removed.receiveAsFlow()
    init {
        if (initial != null && initial.name.isBlank()) viewModelScope.launch {
            when (val result = repository.findUserById(initial.id, booru)) {
                is NetResult.Success -> mutable.value = initial.copy(name = result.data.name)
                is NetResult.Error -> message.value = result.errorMsg
            }
        }
    }
    fun remove() {
        viewModelScope.launch {
            try {
            withContext(Dispatchers.IO) { check(booruManager.updateBooru(booru.copy(user = null, auth = null))) }
            com.mattzilla30.mbooru.okhttp.AndroidCookieJar.remove(
                okhttp3.HttpUrl.Builder().scheme(booru.scheme).host(booru.host).build())
            removed.send(Unit)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message.value = "Unable to remove this account. Please retry." }
        }
    }
}
