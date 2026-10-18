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

package com.mattzilla30.mbooru.ui.activity

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.database.dao.MuzeiDao
import com.mattzilla30.mbooru.data.model.common.Muzei
import com.mattzilla30.mbooru.data.repository.suggestion.SuggestionRepositoryImpl
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.extension.openAppInMarket
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.viewmodel.*
import com.mattzilla30.mbooru.worker.MuzeiArtWorker
import org.koin.android.ext.android.inject

class MuzeiActivity : BaseActivity() {
    private val dao by inject<MuzeiDao>()
    private val apis by inject<BooruApis>()
    private val model by lazy { getMuzeiViewModel(dao) }
    private val suggestions by lazy { getSuggestionViewModel(SuggestionRepositoryImpl(apis)) }
    private val booru by lazy { BooruManager.getBooruByUid(Settings.activatedBooruUid) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val site = booru ?: return finish()
        setScreenTitle(R.string.title_muzei)
        model.loadMuzei(site.uid)
        model.select(Settings.activeMuzeiUid)
        setComposeContent {
            val items by model.entries.collectAsStateWithLifecycle()
            val selected by model.selectedUid.collectAsStateWithLifecycle()
            val query by model.query.collectAsStateWithLifecycle()
            val searching by model.searching.collectAsStateWithLifecycle()
            val options by suggestions.suggestionState.collectAsStateWithLifecycle()
            Box(Modifier.fillMaxSize()) {
                Column {
                    if (searching) {
                        OutlinedTextField(
                            query,
                            onValueChange = { text ->
                                model.query(text)
                                if (site.type != Values.BOORU_TYPE_SHIMMIE && text.isNotBlank()) {
                                    val term =
                                        if (
                                            site.type in
                                                arrayOf(
                                                    Values.BOORU_TYPE_MOE,
                                                    Values.BOORU_TYPE_DAN,
                                                    Values.BOORU_TYPE_DAN1,
                                                )
                                        )
                                            "${text.trim()}*"
                                        else text.trim()
                                    suggestions.fetchSuggestions(
                                        ActionTag(site, order = "count", limit = 6, query = term)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.muzei_search_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { addQuery(query) }),
                        )
                        options.forEach { option ->
                            Text(
                                option,
                                Modifier.fillMaxWidth()
                                    .clickable { addQuery(option) }
                                    .padding(12.dp),
                            )
                        }
                    }
                    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        items(items, key = { it.uid }) { item ->
                            val dismiss =
                                rememberSwipeToDismissBoxState(
                                    confirmValueChange = {
                                        if (it != SwipeToDismissBoxValue.Settled)
                                            model.deleteByUid(item.uid)
                                        it != SwipeToDismissBoxValue.Settled
                                    }
                                )
                            SwipeToDismissBox(dismiss, backgroundContent = {}) {
                                Surface(
                                    color =
                                        if (item.uid == selected)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.background
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth()
                                            .clickable { model.select(item.uid) }
                                            .padding(start = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            item.query,
                                            Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                        )
                                        RowMenu(R.menu.muzei_item) { action ->
                                            when (action.itemId) {
                                                R.id.action_muzei_item_search ->
                                                    SearchActivity.startSearch(
                                                        this@MuzeiActivity,
                                                        item.query,
                                                    )
                                                R.id.action_muzei_item_copy -> copyText(item.query)
                                                R.id.action_muzei_item_delete ->
                                                    model.deleteByUid(item.uid)
                                            }
                                        }
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                FloatingActionButton(
                    onClick = ::openMuzei,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_muzei_24dp),
                        stringResource(R.string.title_muzei),
                    )
                }
            }
        }
    }

    private fun addQuery(query: String) {
        if (query.isNotBlank())
            booru?.let {
                model.create(Muzei(booruUid = it.uid, query = query.trim()))
                model.query("")
            }
    }

    private fun openMuzei() {
        val packageName = "net.nurik.roman.muzei"
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) startActivity(intent) else openAppInMarket(packageName)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.muzei, menu)
        menu.findItem(R.id.action_muzei_search)?.actionView = null
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_muzei_search -> {
                model.searching(!model.searching.value)
                true
            }
            R.id.action_muzei_fetch -> {
                MuzeiArtWorker.enqueueLoad()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
