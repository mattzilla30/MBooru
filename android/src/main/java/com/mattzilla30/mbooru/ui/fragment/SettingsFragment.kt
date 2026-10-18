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

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.extension.getTreeUri
import com.mattzilla30.mbooru.extension.toDecodedString
import com.mattzilla30.mbooru.extension.trim
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.settings.SettingsEffect
import com.mattzilla30.mbooru.ui.compose.settings.SettingsScreen
import com.mattzilla30.mbooru.ui.compose.settings.SettingsStore
import com.mattzilla30.mbooru.ui.compose.settings.SettingsViewModel
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import com.mattzilla30.mbooru.ui.helper.isNightEnable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/** Compose content inside the existing activity/navigation and storage-picker boundary. */
class SettingsFragment : Fragment() {
    private val postDao by inject<PostDao>()
    private val preferences by inject<SharedPreferences>()
    private val model by viewModels<SettingsViewModel> {
        val application = requireContext().applicationContext
        val dao = postDao
        viewModelFactory {
            initializer {
                SettingsViewModel(SettingsStore(preferences), createSavedStateHandle()) {
                    withContext(Dispatchers.IO) {
                        dao.deleteAll()
                        application.cacheDir.trim()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Settings.downloadDirPath = requireContext().contentResolver.getTreeUri()?.toDecodedString()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View = ComposeView(requireContext()).apply {
        id = R.id.compose_settings_view
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MBooruTheme {
                val state by model.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onToggle = model::toggle,
                    onChoose = model::choose,
                    onExpand = model::expand,
                    onPickDirectory = { (activity as? PathActivity)?.pickDir() },
                    onClearCache = model::confirmClearCache,
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.effects.collect { effect ->
                    when (effect) {
                        is SettingsEffect.NightMode -> AppCompatDelegate.setDefaultNightMode(effect.mode)
                        SettingsEffect.NightTheme -> if (resources.configuration.isNightEnable()) activity?.recreate()
                    }
                }
            }
        }
    }
}
