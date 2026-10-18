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

package com.mattzilla30.mbooru.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.ui.activity.BooruConfigActivity
import com.mattzilla30.mbooru.ui.compose.common.composeView
import com.mattzilla30.mbooru.ui.compose.booru.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class BooruConfigFragment : Fragment(), BooruConfigActivity.MenuListener {
    private val dao by inject<BooruDao>()
    private val model by viewModels<BooruEditorViewModel> {
        val uid = requireActivity().intent.getLongExtra(BooruConfigActivity.EXTRA_BOORU_UID, -1)
        viewModelFactory { initializer { BooruEditorViewModel(dao, uid, createSavedStateHandle()) } }
    }
    private var confirmDelete by mutableStateOf(false)
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = composeView {
        val state by model.state.collectAsStateWithLifecycle()
        Surface {
            BooruEditorScreen(state, model::field)
            if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.booru_config_dialog_title_delete)) },
                confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete() }) { Text(stringResource(R.string.dialog_yes)) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.dialog_no)) } })
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { model.completion.collect { activity?.finish() } }
        }
    }
    override fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_booru_config_apply -> { model.save(); true }
        R.id.action_booru_config_delete -> { confirmDelete = true; true }
        else -> false
    }
}
