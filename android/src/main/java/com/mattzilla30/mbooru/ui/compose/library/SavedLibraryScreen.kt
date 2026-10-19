// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.ui.compose.browse.PostDisplayOptions
import com.mattzilla30.mbooru.ui.compose.browse.PostGrid
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun SavedLibraryScreen(
    model: SavedLibraryViewModel,
    actions: ArtworkLibraryViewModel,
    sites: List<Booru>,
    safeOnly: Boolean,
    onOpen: (Post) -> Unit,
    modifier: Modifier = Modifier,
    options: PostDisplayOptions =
        PostDisplayOptions(true, null, false, false, LIBRARY_CARD_WIDTH_DP),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val filter by model.filter.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val preview by model.preview.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    LaunchedEffect(safeOnly) { model.safeOnly(safeOnly) }
    val data =
        remember(state.posts) { flowOf(PagingData.from(state.posts)) }.collectAsLazyPagingItems()
    var edit by rememberSaveable { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var remove by rememberSaveable { mutableStateOf<Long?>(null) }
    var selected by remember { mutableStateOf<Post?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            it?.let(model::export)
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::preview)
        }
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter.mode == -1L, { model.mode(-1) }, label = { Text("All saved") })
            FilterChip(
                filter.mode == LOCAL_LIKES,
                { model.mode(LOCAL_LIKES) },
                label = { Text("Local likes") },
            )
            state.collections.forEach { group ->
                FilterChip(
                    filter.mode == group.uid,
                    { model.mode(group.uid) },
                    label = { Text(group.name) },
                )
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(filter.site < 0, { model.site(-1) }, label = { Text("All sites") })
            sites.forEach { site ->
                FilterChip(
                    filter.site == site.uid,
                    { model.site(site.uid) },
                    label = { Text(site.name) },
                )
            }
            FilterChip(
                filter.scoreSort,
                { model.scoreSort(!filter.scoreSort) },
                label = { Text(if (filter.scoreSort) "Highest score" else "Recently saved") },
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(
                onClick = {
                    edit = 0
                    name = ""
                },
                enabled = !busy,
            ) {
                Text("New collection")
            }
            if (filter.mode > 0) {
                TextButton(
                    onClick = {
                        edit = filter.mode
                        name =
                            state.collections.firstOrNull { it.uid == filter.mode }?.name.orEmpty()
                    },
                    enabled = !busy,
                ) {
                    Text("Rename")
                }
                TextButton(onClick = { remove = filter.mode }, enabled = !busy) {
                    Text("Delete collection")
                }
            }
            TextButton(onClick = { export.launch("MBooru-library.json") }, enabled = !busy) {
                Text("Export library")
            }
            TextButton(
                onClick = { import.launch(arrayOf("application/json", "text/plain")) },
                enabled = !busy,
            ) {
                Text("Import MBooru / M34")
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, Modifier.padding(12.dp))
        if (state.hidden > 0)
            Text(
                "${state.hidden} artwork records hidden by Safe only.",
                Modifier.padding(horizontal = 16.dp),
            )
        if (state.posts.isEmpty()) {
            Text(
                "No saved artwork in this view.",
                Modifier.padding(24.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Save or like a post, add it to a collection, or import an exported library. " +
                    "Media may require a network connection after cache clearing.",
                Modifier.padding(horizontal = 24.dp),
            )
        } else
            PostGrid(
                data,
                emptyFlow(),
                options,
                onClick = { post, _, _ -> onOpen(post) },
                onLongClick = { selected = it },
                modifier = Modifier.weight(1f),
                safeOnly = safeOnly,
            )
    }
    if (edit != null)
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (edit == 0L) "New collection" else "Rename collection") },
            text = {
                Column {
                    OutlinedTextField(
                        name,
                        { name = it },
                        label = { Text("Collection name") },
                        singleLine = true,
                    )
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = edit ?: return@TextButton
                        if (id == 0L) model.create(name) { edit = null }
                        else model.rename(id, name) { edit = null }
                    },
                    enabled = !busy,
                ) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } },
        )
    if (remove != null)
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text("Delete collection?") },
            text = { Text("Only this collection and its memberships are removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        remove?.let(model::delete)
                        remove = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } },
        )
    if (preview != null)
        AlertDialog(
            onDismissRequest = model::cancelImport,
            title = { Text("Import library?") },
            text = { Text(preview!!.description) },
            confirmButton = {
                TextButton(onClick = model::applyImport, enabled = !busy) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = model::cancelImport) { Text("Cancel") } },
        )
    selected?.let { post ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Artwork #${post.id}") },
            text = { ArtworkLibraryActions(post, actions) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
        )
    }
}

private const val LOCAL_LIKES = -2L
private const val LIBRARY_CARD_WIDTH_DP = 160f
