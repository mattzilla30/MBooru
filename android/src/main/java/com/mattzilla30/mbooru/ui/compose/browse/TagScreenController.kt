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
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.app.Values.Tags
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.tag.TagRepositoryImpl
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.viewmodel.TagViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getTagViewModel

private const val ORDER_DATE = "date"
private const val ORDER_NAME = "name"
private const val ORDER_COUNT = "count"

class TagScreenController(
    host: com.mattzilla30.mbooru.ui.base.BaseActivity,
    owner: androidx.navigation.NavBackStackEntry,
    arguments: Bundle = Bundle.EMPTY,
) : BrowseController(host, owner, arguments) {

    private var action: ActionTag? = null
    private var restoredRequest: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredRequest = savedInstanceState?.getBundle("browse_request")
        setSearchBarTitle(getString(R.string.title_tags))
        tagViewModel = getTagViewModel(TagRepositoryImpl(booruApis))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(
            "browse_request",
            Bundle().apply {
                putString("query", action?.query ?: restoredRequest?.getString("query"))
                putString("order", action?.order ?: restoredRequest?.getString("order"))
                putInt("type", action?.type ?: restoredRequest?.getInt("type", -1) ?: -1)
            },
        )
    }

    private lateinit var tagViewModel: TagViewModel

    override fun getSearchBarHint(): CharSequence = getString(R.string.search_bar_hint_search_tags)

    @Composable
    override fun BrowseContent(modifier: Modifier) {
        val data = tagViewModel.tags.collectAsLazyPagingItems()
        PagingList(
            data,
            key = { it.id },
            positions = browse.scrollPositions,
            modifier = modifier,
        ) { item ->
            TagRow(
                item,
                { SearchActivity.startSearch(requireContext(), item.name) },
                { requireContext().copyText(item.name) },
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
                ActionTag(
                    booru = booru,
                    limit = tagLimit(booru.type),
                    order = restoredRequest?.getString("order") ?: ORDER_COUNT,
                    query = restoredRequest?.getString("query").orEmpty(),
                    type = restoredRequest?.getInt("type", -1) ?: -1,
                )
            setSearchBarMenu(
                when (booru.type) {
                    BOORU_TYPE_DAN1 -> AppMenu.TagDanOne
                    BOORU_TYPE_DAN -> AppMenu.TagDan
                    BOORU_TYPE_MOE -> AppMenu.TagMoe
                    in arrayOf(BOORU_TYPE_GEL, BOORU_TYPE_GEL_LEGACY) -> AppMenu.TagGel
                    else -> AppMenu.TagSankaku
                }
            )
        } else {
            action?.let {
                it.booru = booru
                it.limit = tagLimit(booru.type)
            }
        }
        action?.let { tagViewModel.show(it) }
    }

    private fun tagLimit(booruType: Int): Int {
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
            tagViewModel.show(it)
        }
    }

    private fun updateActionAndRefresh(action: ActionTag) {
        tagViewModel.show(action)
    }

    override fun onMenuAction(menuItem: AppActionKey) {
        super.onMenuAction(menuItem)
        when (menuItem) {
            AppActionKey.TagOrderDate -> {
                action?.let {
                    it.order = ORDER_DATE
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagOrderName -> {
                action?.let {
                    it.order = ORDER_NAME
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagOrderCount -> {
                action?.let {
                    it.order = ORDER_COUNT
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeAll -> {
                action?.let {
                    it.type = Tags.TYPE_ALL
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeGeneral -> {
                action?.let {
                    it.type = Tags.TYPE_GENERAL
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeArtist -> {
                action?.let {
                    it.type = Tags.TYPE_ARTIST
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeCopyright -> {
                action?.let {
                    it.type = Tags.TYPE_COPYRIGHT
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeCharacter -> {
                action?.let {
                    it.type = Tags.TYPE_CHARACTER
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeCircle -> {
                action?.let {
                    it.type = Tags.TYPE_CIRCLE
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeFaults -> {
                action?.let {
                    it.type = Tags.TYPE_FAULTS
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeMeta -> {
                action?.let {
                    it.type = Tags.TYPE_META
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeModel -> {
                action?.let {
                    it.type = Tags.TYPE_MODEL
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypePhotoSet -> {
                action?.let {
                    it.type = Tags.TYPE_PHOTO_SET
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeMetaSankaku -> {
                action?.let {
                    it.type = Tags.TYPE_META_SANKAKU
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeGenre -> {
                action?.let {
                    it.type = Tags.TYPE_GENRE
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeStudio -> {
                action?.let {
                    it.type = Tags.TYPE_STUDIO
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.TagTypeMedium -> {
                action?.let {
                    it.type = Tags.TYPE_MEDIUM
                    updateActionAndRefresh(it)
                }
            }
            else -> Unit
        }
    }
}
