// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mattzilla30.mbooru.core.model.ArtworkRating
import com.mattzilla30.mbooru.core.model.normalizedRating
import com.mattzilla30.mbooru.data.backup.LibraryBackups
import com.mattzilla30.mbooru.data.backup.LibraryImportPreview
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.NamedCollection
import com.mattzilla30.mbooru.data.model.common.Post
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryFilter(
    val site: Long = -1,
    val mode: Long = -1,
    val scoreSort: Boolean = false,
    val safeOnly: Boolean = true,
)

data class SavedLibraryState(
    val posts: List<Post> = emptyList(),
    val collections: List<NamedCollection> = emptyList(),
    val hidden: Int = 0,
)

@Suppress("TooManyFunctions")
class SavedLibraryViewModel(
    private val database: MyDatabase,
    private val resolver: ContentResolver,
    private val saved: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle(),
) : ViewModel() {
    private val dao = database.libraryDao()
    private val filters =
        MutableStateFlow(
            LibraryFilter(
                site = saved["library_site"] ?: -1L,
                mode = saved["library_mode"] ?: -1L,
                scoreSort = saved["library_score"] ?: false,
            )
        )
    val filter = filters.asStateFlow()
    private val snapshot =
        combine(dao.observeArtwork(), dao.observeCollections(), dao.observeMemberships()) {
            artwork,
            collections,
            members ->
            Triple(artwork, collections, members)
        }
    val state =
        combine(snapshot, filters) { (artwork, collections, members), filter ->
                val owned = artwork.filter { row ->
                    val post = row.saved.post
                    val matching = members.filter { it.site == post.booruUid && it.id == post.id }
                    (row.saved.isSaved || row.liked || matching.isNotEmpty()) &&
                        (filter.site < 0 || post.booruUid == filter.site) &&
                        when {
                            filter.mode == LOCAL_LIKES -> row.liked
                            filter.mode > 0 -> matching.any { it.collection == filter.mode }
                            else -> true
                        }
                }
                val visible = owned.filter {
                    !filter.safeOnly || normalizedRating(it.saved.post.rating) == ArtworkRating.SAFE
                }
                val sorted =
                    if (filter.scoreSort) visible.sortedByDescending { it.saved.post.score }
                    else visible.sortedByDescending { it.saved.savedAt }
                SavedLibraryState(
                    sorted.map { it.saved.post },
                    collections,
                    owned.size - visible.size,
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS),
                SavedLibraryState(),
            )
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    private val mutablePreview = MutableStateFlow<LibraryImportPreview?>(null)
    val preview = mutablePreview.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()

    fun safeOnly(value: Boolean) {
        filters.update { it.copy(safeOnly = value) }
    }

    fun site(value: Long) {
        saved["library_site"] = value
        filters.update { it.copy(site = value) }
    }

    fun mode(value: Long) {
        saved["library_mode"] = value
        filters.update { it.copy(mode = value) }
    }

    fun scoreSort(value: Boolean) {
        saved["library_score"] = value
        filters.update { it.copy(scoreSort = value) }
    }

    fun create(name: String, complete: () -> Unit) = operation {
        dao.createCollection(name)
        complete()
    }

    fun rename(uid: Long, name: String, complete: () -> Unit) = operation {
        dao.renameCollection(uid, name)
        complete()
    }

    fun delete(uid: Long) = operation {
        dao.deleteCollection(uid)
        mode(-1)
    }

    fun preview(uri: Uri) = operation {
        val parsed =
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { LibraryBackups(database).preview(it) }
                    ?: error("Cannot open import file")
            }
        mutablePreview.value = parsed
    }

    fun cancelImport() {
        mutablePreview.value = null
    }

    fun applyImport() = operation {
        val candidate = mutablePreview.value ?: return@operation
        mutableMessage.value =
            withContext(Dispatchers.IO) { LibraryBackups(database).import(candidate) }
        mutablePreview.value = null
    }

    fun export(uri: Uri) = operation {
        withContext(Dispatchers.IO) {
            val text = LibraryBackups(database).export()
            require(text.toByteArray(Charsets.UTF_8).size <= LibraryBackups.MAX_BYTES) {
                "Library is too large for this export format"
            }
            resolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                ?: error("Cannot write export")
        }
        mutableMessage.value =
            "Library exported without account credentials. Downloaded media files are not embedded."
    }

    private fun operation(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableMessage.value = ""
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalArgumentException) {
                mutableMessage.value = error.message ?: "Invalid library data"
            } catch (error: IllegalStateException) {
                mutableMessage.value = error.message ?: "Cannot update library"
            } catch (_: Exception) {
                mutableMessage.value =
                    "Could not update the library or access the selected file. Check storage access and retry."
            } finally {
                mutableBusy.value = false
            }
        }
    }
}

private const val OBSERVATION_TIMEOUT_MS = 5_000L

private const val LOCAL_LIKES = -2L
