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

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.database.TagFilterManager
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.data.model.common.TagFilter
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.ui.activity.AccountActivity
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import com.mattzilla30.mbooru.ui.base.BaseBottomSheetDialog
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.common.composeView
import com.mattzilla30.mbooru.ui.compose.detail.*
import com.mattzilla30.mbooru.worker.DownloadWorker
import kotlinx.coroutines.launch
import org.koin.core.component.inject

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class InfoDialog : BaseBottomSheetDialog(), org.koin.core.component.KoinComponent {
    private val settings by inject<com.mattzilla30.mbooru.app.SettingsRepository>()
    private val tagFilterManager by inject<com.mattzilla30.mbooru.data.database.TagFilterManager>()

    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    companion object {
        fun create(postId: Int, sourceSite: Long = -1) =
            InfoDialog().apply {
                arguments =
                    Bundle().apply {
                        putInt(Keys.POST_ID, postId)
                        putLong("source_site_uid", sourceSite)
                    }
            }
    }

    private val sourceSite
        get() =
            arguments?.getLong("source_site_uid", -1)?.takeIf { it > 0 }
                ?: settings.activatedBooruUid

    private val dao by inject<PostDao>()

    private val model by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PostInfoViewModel(
                        dao,
                        sourceSite,
                        requireArguments().getInt(Keys.POST_ID),
                    )
                        as T
            },
        )[PostInfoViewModel::class.java]
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = composeView {
        val loadedSite by
            produceState<com.mattzilla30.mbooru.data.model.common.Booru?>(null) {
                value = booruManager.getBooruByUid(sourceSite)
            }
        val site = loadedSite
        val post by model.post.collectAsStateWithLifecycle()
        val pager = rememberPagerState(pageCount = { 2 })
        val scope = rememberCoroutineScope()
        Surface {
            Column(Modifier.fillMaxHeight()) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                )
                TabRow(selectedTabIndex = pager.currentPage) {
                    listOf(R.string.browse_info, R.string.browse_tags).forEachIndexed { index, title
                        ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(stringResource(title)) },
                        )
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                    val item = post
                    if (item != null && site != null) {
                        if (page == 0)
                            PostMetadata(
                                item,
                                site,
                                requireContext().formatDate(item.time).orEmpty(),
                                "${item.width} x ${item.height} ${Formatter.formatFileSize(requireContext(), item.size.toLong())}",
                                onUser = {
                                    if (
                                        site.type !in
                                            arrayOf(
                                                Values.BOORU_TYPE_GEL,
                                                Values.BOORU_TYPE_GEL_LEGACY,
                                                Values.BOORU_TYPE_SHIMMIE,
                                                Values.BOORU_TYPE_RULE34,
                                            )
                                    )
                                        startActivity(
                                            Intent(requireContext(), AccountActivity::class.java)
                                                .apply {
                                                    putExtra(
                                                        AccountActivity.USER_ID_KEY,
                                                        item.uploader.id,
                                                    )
                                                    putExtra(
                                                        AccountActivity.USER_NAME_KEY,
                                                        item.uploader.name,
                                                    )
                                                    putExtra(
                                                        AccountActivity.USER_AVATAR_KEY,
                                                        item.uploader.avatar,
                                                    )
                                                }
                                        )
                                },
                                onSource = { item.source?.let { requireContext().launchUrl(it) } },
                                onCopy = { requireContext().copyText(it) },
                                onOpen = { requireContext().downloadByAdm(it) },
                                onDownload = { url ->
                                    (activity as? PathActivity)?.let {
                                        DownloadWorker.download(url, item.id, site.host, it)
                                    }
                                },
                            )
                        else
                            PostTags(
                                item,
                                site,
                                search = {
                                    settings.activatedBooruUid = site.uid
                                    SearchActivity.startSearch(requireContext(), it)
                                },
                                copy = { requireContext().copyText(it) },
                                include = { tag, include ->
                                    scope.launch {
                                        tagFilterManager.createTagFilter(
                                            TagFilter(
                                                booruUid = site.uid,
                                                name = if (include) tag.name else "-${tag.name}",
                                                type = tag.category,
                                            )
                                        )
                                    }
                                },
                            )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        view?.layoutParams?.height = resources.displayMetrics.heightPixels
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
