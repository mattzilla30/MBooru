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

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.common.tracemoe.api.TraceMoeApi
import com.mattzilla30.mbooru.extension.copyText
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.base.BaseBottomSheetDialog
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.compose.detail.VideoPreview
import com.mattzilla30.mbooru.ui.compose.search.ImageSearchScreen
import com.mattzilla30.mbooru.ui.helper.OpenFileLifecycleObserver
import com.mattzilla30.mbooru.ui.viewmodel.getTraceMoeViewModel
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

private const val PREVIEW_VIDEO_URL_KEY = "preview_video_url"

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class WhatAnimeActivity : BaseActivity() {
    private val api by inject<TraceMoeApi>()
    private val model by lazy { getTraceMoeViewModel(api) }
    private lateinit var picker: OpenFileLifecycleObserver
    private var preparing by mutableStateOf(false)
    private var readError by mutableStateOf<String?>(null)

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picker = OpenFileLifecycleObserver(activityResultRegistry, ::search)
        lifecycle.addObserver(picker)
    }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.title_what_anime)
        setComposeContent {
            val response by model.data.collectAsStateWithLifecycle()
            val loading by model.isLoading.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            ImageSearchScreen(
                response?.results.orEmpty(),
                preparing || loading,
                readError ?: error,
                add = { picker.openDocument("image/*") },
                retry = {
                    if (readError != null) picker.openDocument("image/*") else model.retry()
                },
                key = { item, index -> "${item.filename}:${item.from}:$index" },
            ) { item ->
                Card(
                    Modifier.fillMaxWidth()
                        .padding(8.dp)
                        .combinedClickable(
                            onClick = {
                                AnimePlayerDialog()
                                    .apply {
                                        arguments =
                                            Bundle().apply {
                                                putString(PREVIEW_VIDEO_URL_KEY, item.video)
                                            }
                                    }
                                    .show(supportFragmentManager, "player")
                            },
                            onLongClick = { copyText(item.filename) },
                        )
                ) {
                    Row(Modifier.padding(12.dp)) {
                        AsyncImage(
                            item.image,
                            null,
                            placeholder = ColorPainter(colorResource(R.color.rating_s)),
                            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f),
                        )
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.filename, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${formatTime(item.from)} - ${formatTime(item.to)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                item.similarity.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun search(uri: Uri) {
        preparing = true
        readError = null
        lifecycleScope.launch {
            try {
                val bytes =
                    withContext(Dispatchers.IO) {
                        val loader =
                            imageLoader
                                .newBuilder()
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28)
                                        add(ImageDecoderDecoder.Factory())
                                    else add(GifDecoder.Factory())
                                }
                                .build()
                        try {
                            val result =
                                loader.execute(
                                    ImageRequest.Builder(this@WhatAnimeActivity)
                                        .data(uri)
                                        .allowHardware(false)
                                        .build()
                                ) as? SuccessResult
                            result?.drawable?.toBitmap()?.let { bitmap ->
                                ByteArrayOutputStream().use { stream ->
                                    bitmap.compress(
                                        Bitmap.CompressFormat.JPEG,
                                        if (bitmap.width > 1000 || bitmap.height > 1000) 50
                                        else 100,
                                        stream,
                                    )
                                    stream.toByteArray()
                                }
                            }
                        } finally {
                            loader.shutdown()
                        }
                    }
                if (bytes != null) model.fetch(bytes)
                else readError = getString(R.string.image_search_read_failed)
            } finally {
                preparing = false
            }
        }
    }

    override fun onAction(action: AppActionKey): Boolean {
        if (action == AppActionKey.Back) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onAction(action)
    }

    class AnimePlayerDialog : BaseBottomSheetDialog() {
        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?,
        ): View = composeView {
            arguments?.getString(PREVIEW_VIDEO_URL_KEY)?.let {
                VideoPreview(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            }
        }

        override fun onStart() {
            super.onStart()
            (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }
}

private fun formatTime(time: Float): String =
    "%02d:%02d.%02d".format((time / 60).toInt(), (time % 60).toInt(), ((time % 1) * 100).toInt())
