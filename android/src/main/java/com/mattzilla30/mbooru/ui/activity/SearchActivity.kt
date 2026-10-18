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

import org.koin.core.component.inject

import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.os.Bundle
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import androidx.navigation.compose.rememberNavController
import com.mattzilla30.mbooru.ui.compose.navigation.MainBrowseNavHost
import com.mattzilla30.mbooru.ui.compose.navigation.browseTabs
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.POST_QUERY
import com.mattzilla30.mbooru.app.Settings.activatedBooruUid
import com.mattzilla30.mbooru.data.database.HistoryManager
import com.mattzilla30.mbooru.data.model.common.History
import com.mattzilla30.mbooru.ui.base.PathActivity

class SearchActivity : PathActivity() , org.koin.core.component.KoinComponent {
    private val historyManager by inject<com.mattzilla30.mbooru.data.database.HistoryManager>()


    companion object {
        fun startSearch(context: Context, query: String) {
            context.startActivity(Intent(context, SearchActivity::class.java)
                .putExtra(POST_QUERY, query))
        }
    }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        lifecycleScope.launch { historyManager.createHistory(History(booruUid = activatedBooruUid, query = intent.getStringExtra(POST_QUERY).orEmpty())) }
        setComposeContent { MainBrowseNavHost(rememberNavController(), browseTabs(BOORU_TYPE_SHIMMIE)) }
    }
}
