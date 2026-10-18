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

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.LinearLayout
import android.widget.Toast
import androidx.annotation.IntRange
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.appcompat.widget.TooltipCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import androidx.paging.LoadState
import coil.executeBlocking
import coil.imageLoader
import coil.request.ImageRequest

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.POST_POSITION
import com.mattzilla30.mbooru.app.Keys.POST_QUERY
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_LARGER
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_SAMPLE
import com.mattzilla30.mbooru.app.Settings.activatedBooruUid
import com.mattzilla30.mbooru.app.Settings.detailSize
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SANKAKU
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE
import com.mattzilla30.mbooru.data.action.ActionVote
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.repository.favorite.VoteRepository
import com.mattzilla30.mbooru.data.repository.favorite.VoteRepositoryImpl
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.ui.compose.detail.DetailScreen
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import com.mattzilla30.mbooru.player.PlayerHolder
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.fragment.InfoDialog
import com.mattzilla30.mbooru.ui.helper.CreateFileLifecycleObserver
import com.mattzilla30.mbooru.ui.viewmodel.DetailViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getDetailViewModel
import com.mattzilla30.mbooru.widget.DismissFrameLayout
import com.mattzilla30.mbooru.worker.DownloadWorker
import java.io.*

private const val ALPHA_MAX = 0xFF
private const val ALPHA_MIN = 0x00

private const val ACTION_SAVE = 11
private const val ACTION_SAVE_AS = 12
private const val ACTION_SET_AS = 13
private const val ACTION_SEND = 14

class DetailActivity : PathActivity(), Toolbar.OnMenuItemClickListener, DismissFrameLayout.OnDismissListener , org.koin.core.component.KoinComponent {
    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    companion object {
        const val ACTION_DETAIL_POST_POSITION = "detail_post_position"

        fun start(activity: Activity, query: String?, position: Int, view: View, tranName: String) {
            val intent = Intent(activity, DetailActivity::class.java).apply {
                putExtra(POST_QUERY, query)
                putExtra(POST_POSITION, position)
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    activity,
                    view,
                    tranName
                ).toBundle()
                activity.startActivity(intent, options)
            } else {
                activity.startActivity(intent)
            }
        }
    }

    private val postDao by inject<PostDao>()
    private val booruApis by inject<BooruApis>()
    private val voteRepository: VoteRepository by lazy { VoteRepositoryImpl(booruApis, postDao) }
    private lateinit var booru: Booru
    private lateinit var query: String
    private lateinit var actionVote: ActionVote
    private lateinit var detailViewModel: DetailViewModel
    private lateinit var createFileObserver: CreateFileLifecycleObserver
    private var tmpFile: File? = null
    private var visiblePost: Post? = null
    private val currentPost: Post?
        get() = visiblePost?.let { it.copy(isFavored = detailViewModel.state.value.favorites[it.id] ?: it.isFavored) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createFileObserver = CreateFileLifecycleObserver(activityResultRegistry, ::saveFile)
        lifecycle.addObserver(createFileObserver)
    }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            val site = booruManager.getBooruByUid(activatedBooruUid)
            if (isFinishing || isDestroyed) return@launch
            if (site == null) { finish(); return@launch }
            initializeContent(site)
        }
    }
    private fun initializeContent(site: Booru) {
        booru = site
        query = intent.getStringExtra(POST_QUERY).orEmpty()
        actionVote = ActionVote(booru, -1)
        detailViewModel = getDetailViewModel(postDao, booru.uid, query)
        if (detailViewModel.currentPosition < 0) detailViewModel.currentPosition = intent.getIntExtra(POST_POSITION, 0)
        if (Build.VERSION.SDK_INT > 21) postponeEnterTransition()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val selectedSize = detailSize
        setContent {
            MBooruTheme {
                val posts = detailViewModel.posts.collectAsLazyPagingItems()
                val state by detailViewModel.state.collectAsStateWithLifecycle()
                SideEffect { window.isShowBar = state.barsVisible }
                DetailScreen(posts, state, booru, selectedSize, this,
                    select = { post, position ->
                        visiblePost = post
                        detailViewModel.currentPosition = position
                        sendBroadcast(Intent(ACTION_DETAIL_POST_POSITION).apply {
                            putExtra(POST_QUERY, post.query); putExtra(POST_POSITION, position); setPackage(BuildConfig.APPLICATION_ID)
                        })
                    }, ready = { startPostponedEnterTransition() }, back = { finishAfterTransition() },
                    toggleBars = detailViewModel::toggleBars,
                    longActions = { detailViewModel.longActions(true) }, closeLongActions = { detailViewModel.longActions(false) },
                    action = ::handleMenuAction, info = ::createInfoDialog,
                    download = { currentPost?.let(::download) }, save = { currentPost?.let { saveAndAction(it, ACTION_SAVE) } },
                    favorite = ::vote, longAction = { index -> currentPost?.let { post -> when (index) {
                        0 -> saveAndAction(post, ACTION_SAVE_AS)
                        1 -> download(post)
                        2 -> openBrowser(post)
                    } } })
            }
        }
    }
    private fun saveFile(uri: Uri) {
        val file = tmpFile ?: return
        lifecycleScope.launch {
            if (copyFile(file, uri)) {
                showToast(getString(R.string.msg_file_save_success, DocumentsContract.getDocumentId(uri)))
            }
            tmpFile = null
        }
    }

    private fun createInfoDialog() {
        if (isFinishing) {
            return
        }
        currentPost?.let {
            InfoDialog.create(it.id).show(supportFragmentManager, "info")
        }
    }

    private fun vote() {
        if (booru.type == BOORU_TYPE_SHIMMIE) {
            showToast(getString(R.string.msg_not_supported))
            return
        }
        if (booru.user == null) {
            startActivity(Intent(this, AccountConfigActivity::class.java))
            finish()
            return
        }
        val post = currentPost ?: return
        actionVote.postId = post.id
        lifecycleScope.launch {
            val result = if (post.isFavored) {
                voteRepository.removeFav(actionVote)
            } else {
                voteRepository.addFav(actionVote)
            }
            when {
                result is NetResult.Error -> showToast(result.errorMsg)
                currentPost?.id == post.id -> detailViewModel.favorite(post.id, !post.isFavored)
            }
        }
    }

    override fun onDismissStart() { detailViewModel.dismissing(true) }
    override fun onDismissProgress(progress: Float) {}
    override fun onDismissed() { finishAfterTransition() }
    override fun onDismissCancel() { detailViewModel.dismissing(false) }
    private fun handleMenuAction(id: Int) {
        val post = currentPost ?: return
        when (id) {
            R.id.action_browse_comment -> CommentActivity.startActivity(this, postId = post.id)
            R.id.action_browse_set_as -> saveAndAction(post, ACTION_SET_AS)
            R.id.action_browse_send -> saveAndAction(post, ACTION_SEND)
            R.id.action_browse_share -> shareLink(post)
            R.id.action_browse_open_browser -> openBrowser(post)
        }
    }
    override fun onMenuItemClick(item: MenuItem?): Boolean { item?.let { handleMenuAction(it.itemId) }; return true }
    private fun download(post: Post) {
        if (post.origin.isVideo()) {
            downloadByAdm(post.origin)
        } else {
            DownloadWorker.downloadPost(post, booru.host, this)
        }
    }

    private fun openBrowser(post: Post) {
        val url = getLink(post)
        if (!url.isNullOrEmpty()) {
            launchUrl(url)
        }
    }

    private fun getLink(post: Post): String? {
        return when (booru.type) {
            BOORU_TYPE_DAN -> String.format("%s://%s/posts/%d", booru.scheme, booru.host, post.id)
            BOORU_TYPE_DAN1 -> String.format("%s://%s/post/show/%d", booru.scheme, booru.host, post.id)
            BOORU_TYPE_MOE -> String.format("%s://%s/post/show/%d", booru.scheme, booru.host, post.id)
            in arrayOf(BOORU_TYPE_GEL, BOORU_TYPE_GEL_LEGACY) -> String.format("%s://%s/index.php?page=post&s=view&id=%d", booru.scheme, booru.host, post.id)
            BOORU_TYPE_SANKAKU -> String.format("%s://%s/post/show/%d", booru.scheme, booru.host.replace("capi-v2.", "beta."), post.id)
            BOORU_TYPE_SHIMMIE -> {
                if (booru.path.isNullOrBlank()) {
                    String.format("%s://%s/post/view/%d", booru.scheme, booru.host, post.id)
                } else {
                    String.format("%s://%s/%s/post/view/%d", booru.scheme, booru.host, booru.path, post.id)
                }
            }
            else -> null
        }
    }

    private fun shareLink(post: Post) {
        val url = getLink(post)
        if (!url.isNullOrEmpty()) {
            startActivity(Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                },
                getString(R.string.share_via)
            ))
        }
    }

    private val mutex = Mutex()

    private fun saveAndAction(post: Post, @IntRange(from = ACTION_SAVE.toLong(), to = ACTION_SEND.toLong()) action: Int) {
        val url = when (detailSize) {
            POST_SIZE_SAMPLE -> post.sample
            POST_SIZE_LARGER -> post.medium
            else -> post.origin
        }
        if (url.isBlank()) return
        lifecycleScope.launch {
            mutex.withLock {
                when (action) {
                    ACTION_SAVE -> saveFile(loadFile(url), url.fileName())
                    ACTION_SAVE_AS -> saveFileAs(loadFile(url), url.fileName())
                    ACTION_SET_AS -> setFileAs(loadFile(url), url.fileName())
                    ACTION_SEND -> sendFile(loadFile(url), url.fileName())
                }
            }
        }
    }

    private suspend fun saveFile(source: File?, fileName: String) {
        if (source == null) {
            return
        }
        val uri = getSaveUri(fileName) ?: return
        if (copyFile(file = source, desUri = uri)) {
            showToast(getString(R.string.msg_file_save_success, DocumentsContract.getDocumentId(uri)))
        }
    }

    private fun saveFileAs(source: File?, fileName: String) {
        if (source == null) {
            return
        }
        tmpFile = source
        createFileObserver.createDocument(fileName)
    }

    private suspend fun setFileAs(source: File?, fileName: String) {
        if (source == null) {
            return
        }
        val cacheFile = File(externalCacheDir, fileName)
        val desUri = cacheFile.toUri()
        if (copyFile(source, desUri)) {
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_ATTACH_DATA).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_MIME_TYPES, fileName.getMimeType())
                    data = getUriForFile(cacheFile)
                },
                getString(R.string.share_via)
            ))
        }
    }

    private fun sendFile(source: File?, fileName: String) {
        if (source == null) {
            return
        }
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = fileName.getMimeType()
                putExtra(Intent.EXTRA_STREAM, getUriForFile(source))
            },
            getString(R.string.share_via)
        ))
    }

    private suspend fun loadFile(url: String): File? =
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(this@DetailActivity)
                    .data(url)
                    .memoryCacheKey(url)
                    .diskCacheKey(url)
                    .build()
                imageLoader.executeBlocking(request)
                imageLoader.diskCache?.openSnapshot(url)?.data?.toFile()
            } catch (_: Exception) {
                null
            }
        }

    private suspend fun copyFile(file: File, desUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            var outputSteam: OutputStream? = null
            try {
                inputStream = FileInputStream(file)
                outputSteam = contentResolver.openOutputStream(desUri)
                inputStream.copyTo(outputSteam)
                true
            } catch (_: IOException) {
                false
            } finally {
                inputStream?.safeCloseQuietly()
                outputSteam?.safeCloseQuietly()
            }
        }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private var Window.isShowBar: Boolean
        get() = isStatusBarShown
        set(value) {
            if (value) {
                showSystemBars()
            } else {
                hideSystemBars()
            }
        }
}
