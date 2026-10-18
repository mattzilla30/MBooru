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
import androidx.activity.viewModels
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.booru.*
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BooruConfigActivity : BaseActivity() {
    companion object {
        const val EXTRA_BOORU_UID = "extra_booru_uid"
    }

    private val dao by inject<BooruDao>()
    private val model by
        viewModels<BooruEditorViewModel> {
            val uid = intent.getLongExtra(BooruConfigActivity.EXTRA_BOORU_UID, -1)
            viewModelFactory {
                initializer { BooruEditorViewModel(dao, uid, createSavedStateHandle()) }
            }
        }
    private var confirmDelete by mutableStateOf(false)

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        confirmDelete = savedInstanceState?.getBoolean("confirmDelete") ?: false
        setScreenTitle(R.string.title_booru_config)
        setComposeContent {
            val state by model.state.collectAsStateWithLifecycle()
            Surface {
                BooruEditorScreen(state, model::field)
                if (confirmDelete)
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.booru_config_dialog_title_delete)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    confirmDelete = false
                                    model.delete()
                                }
                            ) {
                                Text(stringResource(R.string.dialog_yes))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.dialog_no))
                            }
                        },
                    )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.completion.collect { finish() } }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("confirmDelete", confirmDelete)
        super.onSaveInstanceState(outState)
    }

    override fun onAction(action: AppActionKey): Boolean =
        when (action) {
            AppActionKey.BooruConfigApply -> {
                model.save()
                true
            }
            AppActionKey.BooruConfigDelete -> {
                confirmDelete = true
                true
            }
            else -> super.onAction(action)
        }

    @androidx.compose.runtime.Composable
    override fun appActions(): List<AppAction> {
        val state by model.state.collectAsStateWithLifecycle()
        return AppMenu.BooruConfig.actions().map { it.copy(enabled = !state.busy) }
    }
}
