// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.activity

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.compose.library.ArtworkLibraryViewModel
import com.mattzilla30.mbooru.ui.compose.library.SavedLibraryScreen
import com.mattzilla30.mbooru.ui.compose.library.SavedLibraryViewModel
import com.mattzilla30.mbooru.ui.compose.library.openLibraryArtwork
import org.koin.android.ext.android.inject

/** Preserve the existing Activity entry point while sharing the unified local library. */
class FavoritesActivity : BaseActivity() {
    private val database by inject<MyDatabase>()

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.sphere_favorites)
        setComposeContent {
            val model: SavedLibraryViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer {
                                SavedLibraryViewModel(
                                    database,
                                    contentResolver,
                                    createSavedStateHandle(),
                                )
                            }
                        }
                )
            val actions: ArtworkLibraryViewModel =
                viewModel(
                    factory =
                        viewModelFactory {
                            initializer { ArtworkLibraryViewModel(database.libraryDao()) }
                        }
                )
            val sites by database.booruDao().observeAll().collectAsStateWithLifecycle(emptyList())
            SavedLibraryScreen(
                model,
                actions,
                sites,
                settings.safeMode,
                { openLibraryArtwork(this, database, it) },
                options =
                    com.mattzilla30.mbooru.ui.compose.browse.rememberPostDisplayOptions(settings),
            )
        }
    }
}
