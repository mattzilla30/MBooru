// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.extension.trim
import com.mattzilla30.mbooru.ui.activity.AccountConfigActivity
import com.mattzilla30.mbooru.ui.activity.BooruActivity
import com.mattzilla30.mbooru.ui.activity.BooruConfigActivity
import com.mattzilla30.mbooru.ui.activity.DetailActivity
import com.mattzilla30.mbooru.ui.activity.MainActivity
import com.mattzilla30.mbooru.ui.compose.library.ArtworkLibraryActions
import com.mattzilla30.mbooru.ui.compose.library.ArtworkLibraryViewModel
import com.mattzilla30.mbooru.ui.compose.library.SavedLibraryScreen
import com.mattzilla30.mbooru.ui.compose.library.SavedLibraryViewModel
import com.mattzilla30.mbooru.ui.compose.library.openLibraryArtwork
import com.mattzilla30.mbooru.ui.compose.settings.SettingsStore
import com.mattzilla30.mbooru.ui.compose.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun MainHomeContent(
    host: MainActivity,
    holder: SaveableStateHolder,
    preferencesRevision: Int,
    database: MyDatabase,
    apis: BooruApis,
    preferences: android.content.SharedPreferences,
    site: Booru?,
    sites: List<Booru>,
    section: Int,
    onSection: (Int) -> Unit,
    secondary: Boolean,
    onPopular: () -> Unit,
    legacy: @Composable () -> Unit,
) {
    val settings = host.settings
    val safeMode = remember(preferencesRevision) { settings.safeMode }
    val displayOptions =
        com.mattzilla30.mbooru.ui.compose.browse.rememberPostDisplayOptions(
            settings,
            preferencesRevision,
        )
    val savedModel: SavedLibraryViewModel =
        viewModel(
            key = "unified-saved",
            factory =
                viewModelFactory {
                    initializer {
                        SavedLibraryViewModel(
                            database,
                            host.applicationContext.contentResolver,
                            createSavedStateHandle(),
                        )
                    }
                },
        )
    val libraryActions: ArtworkLibraryViewModel =
        viewModel(
            key = "home-library-actions",
            factory =
                viewModelFactory { initializer { ArtworkLibraryViewModel(database.libraryDao()) } },
        )
    val settingsModel: SettingsViewModel =
        viewModel(
            key = "unified-settings",
            factory =
                viewModelFactory {
                    initializer {
                        SettingsViewModel(SettingsStore(preferences), createSavedStateHandle()) {
                            withContext(Dispatchers.IO) {
                                database.postDao().deleteAll()
                                host.cacheDir.trim()
                                Unit
                            }
                        }
                    }
                },
        )
    var selected by remember { mutableStateOf<Post?>(null) }
    fun connect(source: Booru) {
        settings.activatedBooruUid = source.uid
        host.startActivity(Intent(host, AccountConfigActivity::class.java))
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(Modifier.weight(1f)) {
                holder.SaveableStateProvider("$section:${if (section in 0..1) site?.uid else 0}") {
                    when (section) {
                        0,
                        1 ->
                            if (site == null)
                                Text("Add or select a site to begin.", Modifier.padding(24.dp))
                            else if (section == 0 && secondary) legacy()
                            else
                                key(site.uid, section) {
                                    val search: UnifiedSearchViewModel =
                                        viewModel(
                                            key = "workspace:${site.uid}:$section",
                                            factory =
                                                viewModelFactory {
                                                    initializer {
                                                        UnifiedSearchViewModel(
                                                            database,
                                                            apis,
                                                            settings,
                                                            site,
                                                            createSavedStateHandle(),
                                                        )
                                                    }
                                                },
                                        )
                                    SearchWorkspace(
                                        search,
                                        site,
                                        settings,
                                        section == 0,
                                        preferencesRevision = preferencesRevision,
                                        connect = { connect(site) },
                                        open = { post, position, image, safe ->
                                            DetailActivity.start(
                                                host,
                                                post.query,
                                                position,
                                                image,
                                                image.transitionName,
                                                safeOnly = safe,
                                            )
                                        },
                                        actions = { selected = it },
                                        popular =
                                            onPopular.takeIf {
                                                com.mattzilla30.mbooru.core.model.BooruEngine
                                                    .fromLegacyId(site.type)
                                                    .capabilities
                                                    .popular
                                            },
                                    )
                                }
                        2 ->
                            SavedLibraryScreen(
                                savedModel,
                                libraryActions,
                                sites,
                                safeMode,
                                { openLibraryArtwork(host, database, it) },
                                options = displayOptions,
                            )
                        HOME_SITES_SECTION ->
                            SitesContent(
                                sites,
                                onSelect = {
                                    settings.activatedBooruUid = it
                                    onSection(0)
                                },
                                onEdit = {
                                    host.startActivity(
                                        Intent(host, BooruConfigActivity::class.java)
                                            .putExtra(BooruConfigActivity.EXTRA_BOORU_UID, it)
                                    )
                                },
                                onConnect = { connect(it) },
                                onManage = {
                                    host.startActivity(Intent(host, BooruActivity::class.java))
                                },
                            )
                        else ->
                            HomeSettings(
                                host,
                                settingsModel,
                                onSites = { onSection(HOME_SITES_SECTION) },
                            )
                    }
                }
            }
        }
    }
    selected?.let { post ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Artwork #${post.id}") },
            text = { ArtworkLibraryActions(post, libraryActions) },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
        )
    }
}

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun SitesContent(
    sites: List<Booru>,
    onSelect: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onConnect: (Booru) -> Unit,
    onManage: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text("Sites", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onManage, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Add, remove, import or export sites")
        }
        LazyColumn {
            items(sites, key = { it.uid }) { site ->
                ListItem(
                    headlineContent = { Text(site.name) },
                    supportingContent = { Text(site.host) },
                    trailingContent = {
                        TextButton(onClick = { onSelect(site.uid) }) { Text("Browse") }
                    },
                )
                Row(Modifier.padding(horizontal = 16.dp)) {
                    TextButton(onClick = { onEdit(site.uid) }) { Text("Edit") }
                    if (
                        com.mattzilla30.mbooru.core.model.BooruEngine.fromLegacyId(site.type)
                            .capabilities
                            .accounts
                    )
                        TextButton(onClick = { onConnect(site) }) {
                            Text(if (site.user == null) "Connect" else "Connection")
                        }
                }
                HorizontalDivider()
            }
        }
    }
}

const val HOME_SITES_SECTION = 3
const val HOME_SETTINGS_SECTION = 4
