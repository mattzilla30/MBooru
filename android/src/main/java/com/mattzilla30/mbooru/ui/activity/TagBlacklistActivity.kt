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

import com.mattzilla30.mbooru.ui.compose.common.*

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.viewmodel.getBooruViewModel
import org.koin.android.ext.android.inject

class TagBlacklistActivity : BaseActivity() {
    private val booruDao by inject<BooruDao>()
    private val model by lazy { getBooruViewModel(booruDao) }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.title_tag_blacklist)
        model.loadBooru(settings.activatedBooruUid)
        setComposeContent {
            val booru by model.booruState.collectAsStateWithLifecycle()
            var adding by rememberSaveable { mutableStateOf(false) }
            var input by rememberSaveable { mutableStateOf("") }
            Box(Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(booru?.blacklists.orEmpty().distinct(), key = { it }) { tag ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                tag,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(
                                        painterResource(R.drawable.ic_more_vert_24dp),
                                        stringResource(
                                            androidx.appcompat.R.string
                                                .abc_action_menu_overflow_description
                                        ),
                                    )
                                }
                                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.tag_blacklist_copy))
                                        },
                                        onClick = {
                                            copyText(tag)
                                            menu = false
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.tag_blacklist_delete))
                                        },
                                        onClick = {
                                            booru?.let {
                                                model.updateBooru(
                                                    it.copy(
                                                        blacklists =
                                                            it.blacklists.filterNot { value ->
                                                                value == tag
                                                            }
                                                    )
                                                )
                                            }
                                            menu = false
                                        },
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
                FloatingActionButton(
                    onClick = { if (booru != null) adding = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, stringResource(R.string.tag_blacklist_add))
                }
            }
            if (adding)
                AlertDialog(
                    onDismissRequest = { adding = false },
                    title = { Text(stringResource(R.string.tag_blacklist_add)) },
                    text = { OutlinedTextField(input, { input = it }, singleLine = true) },
                    confirmButton = {
                        TextButton(
                            enabled = input.isNotBlank(),
                            onClick = {
                                booru?.let {
                                    model.updateBooru(
                                        it.copy(
                                            blacklists = (it.blacklists + input.trim()).distinct()
                                        )
                                    )
                                }
                                adding = false
                                input = ""
                            },
                        ) {
                            Text(stringResource(R.string.dialog_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { adding = false }) {
                            Text(stringResource(R.string.dialog_no))
                        }
                    },
                )
        }
    }

    override fun onAction(action: AppActionKey): Boolean {
        if (action == AppActionKey.Back) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onAction(action)
    }
}
