package com.mattzilla30.mbooru.ui.compose.account


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.user.UserRepository
import com.mattzilla30.mbooru.extension.NetResult
import com.mattzilla30.mbooru.extension.sha1
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class AccountConfigState(val username: String = "", val token: String = "",
    val busy: Boolean = false, val error: String? = null, val errorResource: Int? = null)

class AccountConfigViewModel(private val repository: UserRepository, private val booru: Booru, private val booruManager: BooruManager) : ViewModel() {

    private val mutable = MutableStateFlow(AccountConfigState())
    val state = mutable.asStateFlow()
    private val completed = Channel<Unit>(Channel.BUFFERED)
    val success = completed.receiveAsFlow()
    fun username(value: String) { mutable.update { it.copy(username = value, error = null, errorResource = null) } }
    fun token(value: String) { mutable.update { it.copy(token = value, error = null, errorResource = null) } }
    fun submit() {
        val input = mutable.value
        if (input.busy) return
        if (input.username.isBlank() || input.token.isBlank()) {
            mutable.update { it.copy(errorResource = R.string.account_config_msg_tip_empty) }; return
        }
        mutable.update { it.copy(busy = true, error = null, errorResource = null) }
        viewModelScope.launch {
            try {
                val token = if (booru.type == Values.BOORU_TYPE_DAN1 && booru.hashSalt.isNotBlank())
                    booru.hashSalt.replace(Values.HASH_SALT_CONTAINED, input.token).sha1() else input.token
                val result = when (booru.type) {
                    Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY -> repository.gelLogin(input.username, token, booru)
                    Values.BOORU_TYPE_SANKAKU -> repository.sankakuLogin(input.username, token, booru)
                    Values.BOORU_TYPE_MOE -> repository.moeCheck(input.username, token, booru)
                    else -> repository.findUserByName(input.username, booru)
                }
                when (result) {
                    is NetResult.Success -> {
                        val user = result.data
                        if (booru.type in arrayOf(Values.BOORU_TYPE_DAN, Values.BOORU_TYPE_DAN1)) user.token = token
                        withContext(Dispatchers.IO) { booruManager.updateBooru(booru.copy(user = user)) }
                        completed.send(Unit)
                    }
                    is NetResult.Error -> mutable.update { it.copy(error = result.errorMsg) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(error = error.message.orEmpty()) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
