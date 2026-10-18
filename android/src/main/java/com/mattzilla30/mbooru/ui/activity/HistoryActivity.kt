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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.database.dao.HistoryDao
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.viewmodel.getHistoryViewModel
import org.koin.android.ext.android.inject

class HistoryActivity : BaseActivity() {
    private val historyDao by inject<HistoryDao>()
    private val postDao by inject<PostDao>()
    private val model by lazy { getHistoryViewModel(historyDao, postDao) }
    private var clearDialog by mutableStateOf(false)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenTitle(R.string.title_history)
        model.loadHistory(Settings.activatedBooruUid)
        setComposeContent {
            val history by model.history.collectAsStateWithLifecycle()
            LazyColumn {
                items(history, key = { it.uid }) { item ->
                    val dismiss =
                        rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it != SwipeToDismissBoxValue.Settled) model.deleteByUid(item)
                                it != SwipeToDismissBoxValue.Settled
                            }
                        )
                    SwipeToDismissBox(state = dismiss, backgroundContent = {}) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            Text(
                                item.query,
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        SearchActivity.startSearch(this@HistoryActivity, item.query)
                                    }
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
            if (clearDialog)
                AlertDialog(
                    onDismissRequest = { clearDialog = false },
                    title = { Text(stringResource(R.string.history_clear_all)) },
                    text = { Text(stringResource(R.string.history_clear_all_content)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.deleteAll(Settings.activatedBooruUid)
                                clearDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.dialog_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { clearDialog = false }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    },
                )
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.history, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_history_clear_all -> {
                if (model.history.value.isNotEmpty()) clearDialog = true
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
