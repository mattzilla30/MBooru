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

import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.data.action.ActionArtist
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.artist.ArtistRepositoryImpl
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.viewmodel.ArtistViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getArtistViewModel

private const val ORDER_DATE = "date"
private const val ORDER_UPDATED_AT = "updated_at"
private const val ORDER_NAME = "name"
private const val ORDER_COUNT = "post_count"

class ArtistScreenController(
    host: com.mattzilla30.mbooru.ui.base.BaseActivity,
    owner: androidx.navigation.NavBackStackEntry,
    arguments: Bundle = Bundle.EMPTY,
) : BrowseController(host, owner, arguments) {

    private var action: ActionArtist? = null
    private var restoredRequest: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredRequest = savedInstanceState?.getBundle("browse_request")
        setSearchBarTitle(getString(R.string.title_artists))
        artistViewModel = getArtistViewModel(ArtistRepositoryImpl(booruApis))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(
            "browse_request",
            Bundle().apply {
                putString("query", action?.query ?: restoredRequest?.getString("query"))
                putString("order", action?.order ?: restoredRequest?.getString("order"))
            },
        )
    }

    private lateinit var artistViewModel: ArtistViewModel

    override fun getSearchBarHint(): CharSequence =
        getString(R.string.search_bar_hint_search_artists)

    @Composable
    override fun BrowseContent(modifier: Modifier) {
        val data = artistViewModel.artists.collectAsLazyPagingItems()
        PagingList(
            data,
            key = { it.id },
            positions = browse.scrollPositions,
            modifier = modifier,
        ) { item ->
            ArtistRow(
                item,
                { SearchActivity.startSearch(requireContext(), item.name) },
                { requireContext().copyText(item.name) },
                { requireContext().launchUrl(it) },
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
                ActionArtist(
                    booru = booru,
                    limit = artistLimit(booru.type),
                    order = restoredRequest?.getString("order") ?: ORDER_COUNT,
                    query = restoredRequest?.getString("query").orEmpty(),
                )
            setSearchBarMenu(
                when (booru.type) {
                    BOORU_TYPE_DAN -> AppMenu.ArtistDan
                    else -> AppMenu.ArtistMoe
                }
            )
        } else {
            action?.let {
                it.booru = booru
                it.limit = artistLimit(booru.type)
            }
        }
        action?.let { artistViewModel.show(it) }
    }

    private fun artistLimit(booruType: Int): Int {
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
            artistViewModel.show(it)
        }
    }

    private fun updateActionAndRefresh(action: ActionArtist) {
        artistViewModel.show(action)
    }

    override fun onMenuAction(menuItem: AppActionKey) {
        super.onMenuAction(menuItem)
        when (menuItem) {
            AppActionKey.ArtistOrderDate -> {
                action?.let {
                    it.order =
                        if (it.booru.type == BOORU_TYPE_DAN) {
                            ORDER_UPDATED_AT
                        } else {
                            ORDER_DATE
                        }
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.ArtistOrderName -> {
                action?.let {
                    it.order = ORDER_NAME
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.ArtistOrderCount -> {
                action?.let {
                    it.order = ORDER_COUNT
                    updateActionAndRefresh(it)
                }
            }
            else -> Unit
        }
    }
}
