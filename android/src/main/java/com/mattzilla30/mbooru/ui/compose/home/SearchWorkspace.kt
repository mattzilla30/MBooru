// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.rule34.ArtworkQuery
import com.mattzilla30.mbooru.data.api.rule34.DiscoverySort
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.ui.compose.browse.PostGrid
import com.mattzilla30.mbooru.ui.compose.browse.rememberPostDisplayOptions
import kotlinx.coroutines.flow.emptyFlow

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun SearchWorkspace(
    model: UnifiedSearchViewModel,
    site: Booru,
    settings: SettingsRepository,
    discover: Boolean,
    preferencesRevision: Int,
    connect: () -> Unit,
    open: (Post, Int, android.view.View, Boolean) -> Unit,
    actions: (Post) -> Unit,
    modifier: Modifier = Modifier,
    popular: (() -> Unit)? = null,
    menu: @Composable () -> Unit = {},
) {
    val text by model.text.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val debounced by model.debounced.collectAsStateWithLifecycle()
    val suggestions by model.suggestions.collectAsStateWithLifecycle()
    val tags by model.recommendations.collectAsStateWithLifecycle()
    val refreshing by model.refreshing.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle(emptyList())
    val posts = model.posts.collectAsLazyPagingItems()
    LaunchedEffect(site) { model.updateSite(site) }
    LaunchedEffect(preferencesRevision) { if (settings.safeMode) model.safe(true) }
    DisposableEffect(model) {
        val owner = Any()
        model.attach(owner)
        onDispose { model.detach(owner) }
    }
    val globalSafe = remember(preferencesRevision) { settings.safeMode }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val controlsMaximum = maxHeight * CONTROLS_HEIGHT_FRACTION
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.heightIn(max = controlsMaximum).verticalScroll(rememberScrollState())) {
                if (site.type == Values.BOORU_TYPE_RULE34 && site.user == null) {
                    Text(
                        "Connect Rule34 with your user ID and API key to browse. Saved artwork stays " +
                            "available without an account connection.",
                        Modifier.padding(16.dp),
                    )
                    Button(onClick = connect, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text("Connect Rule34")
                    }
                }
                OutlinedTextField(
                    text,
                    model::edit,
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .testTag("unified-query"),
                    label = { Text("Tags on ${site.name}") },
                    singleLine = true,
                    trailingIcon = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    model.edit("")
                                    model.submit("")
                                }
                            ) {
                                Text("Clear")
                            }
                            menu()
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.submit() }),
                )
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            model.submit()
                        },
                        modifier = Modifier.testTag("unified-submit"),
                    ) {
                        Text("Search")
                    }
                    TextButton(onClick = model::refresh) { Text("Refresh") }
                    TextButton(onClick = model::cancel) { Text("Cancel") }
                    if (!discover) {
                        FilterChip(
                            debounced,
                            { model.debounce(!debounced) },
                            label = { Text("Search while typing") },
                        )
                    }
                    FilterChip(
                        query.safeOnly,
                        { if (!globalSafe) model.safe(!query.safeOnly) },
                        enabled = !globalSafe,
                        label = { Text(if (globalSafe) "Safe only (global)" else "Safe only") },
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        query.sort == DiscoverySort.LATEST,
                        { model.sort(DiscoverySort.LATEST) },
                        label = { Text("Latest") },
                    )
                    if (discover && popular != null)
                        TextButton(onClick = popular) { Text("Popular") }
                    if (site.type == Values.BOORU_TYPE_RULE34) {
                        FilterChip(
                            query.sort == DiscoverySort.RANDOM,
                            { model.sort(DiscoverySort.RANDOM) },
                            label = { Text("Random") },
                        )
                        FilterChip(
                            query.sort == DiscoverySort.SCORE,
                            { model.sort(DiscoverySort.SCORE) },
                            label = { Text("Highest score") },
                        )
                    }
                    if (!query.safeOnly) {
                        TextButton(
                            onClick = {
                                model.submit(
                                    ArtworkQuery.normalize(text)
                                        .split(' ')
                                        .filterNot { it.startsWith("rating:") }
                                        .joinToString(" ") + " rating:questionable"
                                )
                            }
                        ) {
                            Text("Questionable")
                        }
                        TextButton(
                            onClick = {
                                model.submit(
                                    ArtworkQuery.normalize(text)
                                        .split(' ')
                                        .filterNot { it.startsWith("rating:") }
                                        .joinToString(" ") + " rating:explicit"
                                )
                            }
                        ) {
                            Text("Explicit")
                        }
                    }
                }
                if (query.normalizedTags.isNotEmpty())
                    Row(
                        Modifier.horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        query.normalizedTags.split(' ').forEach { tag ->
                            InputChip(true, { model.removeTag(tag) }, label = { Text("$tag ×") })
                        }
                    }
                if (text != query.normalizedTags && suggestions.isNotEmpty())
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        suggestions.forEach { tag ->
                            SuggestionChip(
                                onClick = {
                                    model.submit(
                                        ArtworkQuery.normalize(text)
                                            .substringBeforeLast(' ', "")
                                            .let {
                                                if (it.isBlank()) tag else "$it $tag"
                                            }
                                    )
                                },
                                label = { Text(tag) },
                            )
                        }
                    }
                if (!discover && text.isBlank() && history.isNotEmpty())
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        history.take(HISTORY_LIMIT).forEach { entry ->
                            SuggestionChip(
                                onClick = { model.submit(entry.query) },
                                label = { Text(entry.query) },
                            )
                        }
                    }
                if (discover) {
                    TextButton(onClick = model::refreshRecommendations, enabled = !refreshing) {
                        Text(
                            if (site.type == Values.BOORU_TYPE_RULE34) "Refresh discovery tags"
                            else "Tags from recent posts"
                        )
                    }
                    if (tags.isNotEmpty())
                        Row(
                            Modifier.horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                        ) {
                            tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = { model.addTag(tag) },
                                    label = { Text(tag) },
                                )
                            }
                        }
                }
                if (message.isNotBlank()) Text(message, Modifier.padding(horizontal = 16.dp))
            }
            val options = rememberPostDisplayOptions(settings, preferencesRevision)
            key(query.normalizedTags, query.sort, query.safeOnly) {
                PostGrid(
                    posts,
                    emptyFlow(),
                    options,
                    onClick = { post, position, view ->
                        open(post, position, view, query.safeOnly)
                    },
                    onLongClick = actions,
                    modifier = Modifier.weight(1f),
                    safeOnly = query.safeOnly || globalSafe,
                    onPosition = model::recordGridIndex,
                )
            }
        }
    }
}

private const val CONTROLS_HEIGHT_FRACTION = .55f
private const val HISTORY_LIMIT = 8
