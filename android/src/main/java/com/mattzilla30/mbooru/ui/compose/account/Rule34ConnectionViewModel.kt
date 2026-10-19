// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.data.api.rule34.ArtworkQuery
import com.mattzilla30.mbooru.data.api.rule34.Rule34Adapter
import com.mattzilla30.mbooru.data.api.rule34.Rule34Credentials
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.extension.safeNetworkMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Rule34ConnectionState(
    val userId: String = "",
    val keyInput: String = "",
    val connected: Boolean = false,
    val busy: Boolean = false,
    val message: String = "",
) {
    override fun toString() = "Rule34ConnectionState(connected=$connected, busy=$busy)"
}

class Rule34ConnectionViewModel(
    private val manager: BooruManager,
    private val adapter: Rule34Adapter,
    private var site: Booru,
) : ViewModel() {
    private val mutable =
        MutableStateFlow(
            Rule34ConnectionState(userId = site.user?.name.orEmpty(), connected = site.user != null)
        )
    val state = mutable.asStateFlow()

    fun userId(value: String) {
        mutable.update { it.copy(userId = value, message = "") }
    }

    fun key(value: String) {
        mutable.update { it.copy(keyInput = value, message = "") }
    }

    private fun credentials() =
        Rule34Credentials.parse(
            state.value.userId,
            state.value.keyInput,
            site.user?.token.orEmpty(),
        )

    fun save() = operation {
        val credentials = credentials()
        val updated =
            site.copy(
                user = credentials.asUser(),
                type = com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34,
            )
        withContext(Dispatchers.IO) { check(manager.updateBooru(updated)) }
        site = updated
        mutable.update {
            it.copy(
                userId = credentials.userId,
                keyInput = "",
                connected = true,
                message = "Credentials saved securely. Test the connection to verify access.",
            )
        }
    }

    fun test() = operation {
        val candidate = site.copy(user = credentials().asUser())
        val result = adapter.fetch(candidate, ArtworkQuery(site.uid), 0, 1)
        mutable.update { it.copy(message = "Connection succeeded. " + result.summary()) }
    }

    fun disconnect() = operation {
        withContext(Dispatchers.IO) { check(manager.updateBooru(site.copy(user = null))) }
        site = site.copy(user = null)
        mutable.update {
            it.copy(
                keyInput = "",
                connected = false,
                message = "Disconnected. Local artwork and collections are retained.",
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, message = "") }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutable.update { it.copy(message = failure.safeNetworkMessage()) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
