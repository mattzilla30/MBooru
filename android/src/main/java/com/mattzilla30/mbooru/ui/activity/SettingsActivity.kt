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

import android.content.SharedPreferences
import android.os.Bundle
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.activity.viewModels
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
import com.mattzilla30.mbooru.ui.helper.isNightEnable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/** Activity owns Compose state, effects and the storage-picker boundary. */
class SettingsActivity : PathActivity() {
    private val postDao by inject<PostDao>()
    private val preferences by inject<SharedPreferences>()
    private val model by viewModels<SettingsViewModel> {
        val application = applicationContext
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

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        settings.downloadDirPath = contentResolver.getTreeUri()?.toDecodedString()
        setScreenTitle(R.string.title_settings)
        setComposeContent {
            val state by model.state.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onToggle = model::toggle,
                onChoose = model::choose,
                onExpand = model::expand,
                onPickDirectory = ::pickDir,
                onClearCache = model::confirmClearCache,
                onRetrySave = model::retrySave,
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.effects.collect { effect ->
                    when (effect) {
                        is SettingsEffect.NightMode -> AppCompatDelegate.setDefaultNightMode(effect.mode)
                        SettingsEffect.NightTheme -> if (resources.configuration.isNightEnable()) recreate()
                    }
                }
            }
        }
    }
}
