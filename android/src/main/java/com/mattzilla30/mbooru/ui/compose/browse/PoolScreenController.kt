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

package com.mattzilla30.mbooru.ui.compose.browse

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.data.action.ActionPool
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.pool.PoolRepositoryImpl
import com.mattzilla30.mbooru.extension.formatDate
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.activity.AccountActivity
import com.mattzilla30.mbooru.ui.activity.AccountConfigActivity
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.viewmodel.PoolViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getPoolViewModel
import okhttp3.HttpUrl

class PoolScreenController(
    host: com.mattzilla30.mbooru.ui.base.BaseActivity,
    owner: androidx.navigation.NavBackStackEntry,
    arguments: Bundle = Bundle.EMPTY,
) : BrowseController(host, owner, arguments) {

    private var action: ActionPool? = null
    private var restoredRequest: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredRequest = savedInstanceState?.getBundle("browse_request")
        setSearchBarTitle(getString(R.string.title_pools))
        poolViewModel = getPoolViewModel(PoolRepositoryImpl(booruApis))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(
            "browse_request",
            Bundle().apply {
                putString("query", action?.query ?: restoredRequest?.getString("query"))
            },
        )
    }

    private lateinit var poolViewModel: PoolViewModel

    override fun getSearchBarHint(): CharSequence = getString(R.string.search_bar_hint_search_pools)

    @Composable
    override fun BrowseContent(modifier: Modifier) {
        val data = poolViewModel.pools.collectAsLazyPagingItems()
        PagingList(
            data,
            key = { it.id },
            positions = browse.scrollPositions,
            modifier = modifier,
        ) { item ->
            PoolRow(
                item,
                host.formatDate(item.time).orEmpty(),
                { SearchActivity.startSearch(host, "pool:${item.id}") },
                { action?.booru?.let { handlePoolDownload(item.id, it) } },
                {
                    if (
                        item.booruType == BOORU_TYPE_MOE ||
                            item.booruType == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SANKAKU
                    )
                        startActivity(
                            Intent(host, AccountActivity::class.java).apply {
                                putExtra(AccountActivity.USER_ID_KEY, item.creatorId)
                                putExtra(AccountActivity.USER_NAME_KEY, item.creatorName)
                                putExtra(AccountActivity.USER_AVATAR_KEY, item.creatorAvatar)
                            }
                        )
                },
            )
        }
    }

    override fun onBooruLoaded(booru: Booru?) {
        super.onBooruLoaded(booru)
        if (booru == null) {
            action = null
            return
        }
        if (action == null) {
            action =
                ActionPool(
                    booru = booru,
                    limit = poolLimit(booru.type),
                    query = restoredRequest?.getString("query").orEmpty(),
                )
        } else {
            action?.let {
                it.booru = booru
                it.limit = poolLimit(booru.type)
            }
        }
        action?.let { poolViewModel.show(it) }
    }

    private fun poolLimit(booruType: Int): Int {
        return when (booruType) {
            BOORU_TYPE_MOE,
            BOORU_TYPE_DAN1 -> 20
            else -> settings.pageLimit
        }
    }

    override fun onApplySearch(query: String) {
        super.onApplySearch(query)
        action?.let {
            it.query = query
            poolViewModel.show(it)
        }
    }

    private fun handlePoolDownload(poolId: Int, booru: Booru) {
        if (booru.type != BOORU_TYPE_MOE) {
            return
        }
        val activity = host as? PathActivity ?: return
        AlertDialog.Builder(activity)
            .setTitle("Pool $poolId")
            .setItems(activity.resources.getStringArray(R.array.pool_item_action)) { _, which ->
                if (booru.user == null) {
                    startActivity(Intent(activity, AccountConfigActivity::class.java))
                    return@setItems
                }
                when (which) {
                    0 -> downloadPool(booru, poolId, "jpeg")
                    1 -> downloadPool(booru, poolId, "png")
                }
            }
            .create()
            .show()
    }

    private fun downloadPool(booru: Booru, poolId: Int, imageType: String) {
        val url =
            HttpUrl.Builder()
                .scheme(booru.scheme)
                .host(booru.host)
                .addPathSegments("/pool/zip/$poolId")
                .addQueryParameter(imageType, "1")
                .addQueryParameter("login", booru.user?.name)
                .addQueryParameter("password_hash", booru.user?.token)
                .build()
        host.launchUrl(url.toString())
    }
}
