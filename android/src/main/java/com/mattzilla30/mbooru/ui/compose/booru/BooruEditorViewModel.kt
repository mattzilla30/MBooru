package com.mattzilla30.mbooru.ui.compose.booru

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.extension.isHost
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

val booruTypes =
    linkedMapOf(
        "danbooru" to Values.BOORU_TYPE_DAN,
        "moebooru" to Values.BOORU_TYPE_MOE,
        "danbooru1" to Values.BOORU_TYPE_DAN1,
        "gelbooru" to Values.BOORU_TYPE_GEL,
        "shimmie" to Values.BOORU_TYPE_SHIMMIE,
        "gelbooru_legacy" to Values.BOORU_TYPE_GEL_LEGACY,
        "sankaku" to Values.BOORU_TYPE_SANKAKU,
    )

data class BooruEditorState(
    val values: Map<String, String>,
    val busy: Boolean = false,
    val error: Int? = null,
)

class BooruEditorViewModel(
    private val dao: BooruDao,
    uid: Long,
    private val saved: SavedStateHandle,
) : ViewModel() {
    private var original =
        Booru(
            name = "",
            scheme = "https",
            host = "",
            type = Values.BOORU_TYPE_MOE,
            hashSalt = "--${Values.HASH_SALT_CONTAINED}--",
        )

    private fun fields(site: Booru) =
        mapOf(
            "name" to site.name,
            "scheme" to site.scheme,
            "host" to site.host,
            "path" to site.path.orEmpty(),
            "hashSalt" to site.hashSalt,
            "type" to (booruTypes.entries.find { it.value == site.type }?.key ?: "moebooru"),
        )

    private val mutable = MutableStateFlow(BooruEditorState(fields(original), busy = true))

    init {
        viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { dao.getBooruByUid(uid) }
                if (loaded != null) original = loaded
                mutable.value =
                    BooruEditorState(
                        saved.get<HashMap<String, String>>("fields") ?: fields(original)
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = R.string.booru_config_save_failed) }
            }
        }
    }

    val state = mutable.asStateFlow()
    private val complete = Channel<Unit>(Channel.BUFFERED)
    val completion = complete.receiveAsFlow()

    fun field(key: String, value: String) {
        if (mutable.value.busy) return
        mutable.update { it.copy(values = it.values + (key to value), error = null) }
        saved["fields"] = HashMap(mutable.value.values)
    }

    fun save() {
        if (mutable.value.busy) return
        val values = mutable.value.values
        val type = booruTypes[values["type"]] ?: Values.BOORU_TYPE_MOE
        val error = validateBooru(values, type)
        if (error != null) {
            mutable.update { it.copy(error = error) }
            return
        }
        val updated =
            original.copy(
                name = values.getValue("name"),
                host = values.getValue("host"),
                scheme = values.getValue("scheme"),
                type = type,
                path = if (type == Values.BOORU_TYPE_SHIMMIE) values["path"] else null,
                hashSalt = if (type == Values.BOORU_TYPE_DAN1) values["hashSalt"].orEmpty() else "",
            )
        persist { if (updated.uid == 0L) dao.insert(updated) else dao.update(updated) }
    }

    fun delete() = persist { dao.delete(original.uid) }

    private fun persist(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                complete.send(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutable.update { it.copy(error = R.string.booru_config_save_failed) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}

fun validateBooru(values: Map<String, String>, type: Int): Int? =
    when {
        values["name"].isNullOrEmpty() -> R.string.booru_config_name_cant_empty
        values["host"].isNullOrEmpty() -> R.string.booru_config_host_cant_empty
        !values.getValue("host").isHost() -> R.string.booru_config_host_invalid
        type == Values.BOORU_TYPE_DAN1 && values["hashSalt"].isNullOrEmpty() ->
            R.string.booru_config_hash_salt_cant_empty
        type == Values.BOORU_TYPE_DAN1 &&
            !values["hashSalt"].orEmpty().contains(Values.HASH_SALT_CONTAINED) ->
            R.string.booru_config_hash_salt_must_contain_yp
        else -> null
    }
