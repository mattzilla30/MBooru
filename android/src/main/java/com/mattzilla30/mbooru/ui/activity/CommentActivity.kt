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
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.action.ActionComment
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.repository.comment.CommentRepositoryImpl
import com.mattzilla30.mbooru.extension.NetResult
import com.mattzilla30.mbooru.extension.formatDate
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.compose.browse.PagingList
import com.mattzilla30.mbooru.ui.compose.comments.CommentRow
import com.mattzilla30.mbooru.ui.viewmodel.getCommentViewModel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class CommentActivity : BaseActivity() , org.koin.core.component.KoinComponent {
    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    companion object {
        private const val POST_ID_KEY = "post_id"
        private const val QUERY_KEY = "query"
        fun startActivity(context: Context, postId: Int = -1, query: String = "") {
            context.startActivity(Intent(context, CommentActivity::class.java).apply {
                putExtra(POST_ID_KEY, postId)
                putExtra(QUERY_KEY, query)
            })
        }
    }

    private val apis by inject<BooruApis>()
    private var booru: com.mattzilla30.mbooru.data.model.common.Booru? = null
    private val model by lazy { getCommentViewModel(CommentRepositoryImpl(apis)) }
    override fun onSettingsReady(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            booru = booruManager.getBooruByUid(Settings.activatedBooruUid)
            if (isFinishing || isDestroyed) return@launch
            initializeContent()
        }
    }
    private fun initializeContent() {
        val site = booru ?: return finish()
        val action = ActionComment(site, limit = when (site.type) {
            Values.BOORU_TYPE_MOE -> 30
            Values.BOORU_TYPE_DAN1, Values.BOORU_TYPE_SANKAKU -> 25
            else -> Settings.pageLimit
        }, postId = intent.getIntExtra(POST_ID_KEY, -1), query = intent.getStringExtra(QUERY_KEY).orEmpty())
        setScreenTitle(R.string.title_comments)
        screenSubtitle = if (action.postId > 0) "Post ${action.postId}" else action.query
        model.show(action)
        setComposeContent {
            val comments = model.comments.collectAsLazyPagingItems()
            PagingList(comments, key = { it.id }, positions = remember { emptyFlow<Int>() }, modifier = Modifier.fillMaxSize()) { item ->
                CommentRow(item, site, formatDate(item.time).orEmpty(),
                    onPost = { if (site.type !in arrayOf(Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)) SearchActivity.startSearch(this, "id:${item.postId}") },
                    onUser = { if (site.type !in arrayOf(Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)) startActivity(Intent(this, AccountActivity::class.java).apply {
                        putExtra(AccountActivity.USER_ID_KEY, item.creatorId); putExtra(AccountActivity.USER_NAME_KEY, item.creatorName); putExtra(AccountActivity.USER_AVATAR_KEY, item.creatorAvatar)
                    }) },
                    onReply = { model.reply(item.postId) }, onQuote = { model.reply(item.postId, it) },
                    onDelete = { model.deleteComment(action.copy(commentId = item.id)) })
            }
            val reply by model.reply.collectAsStateWithLifecycle()
            reply?.let { draft ->
                AlertDialog(onDismissRequest = model::dismissReply, title = { Text("Post ${draft.postId}") },
                    text = { OutlinedTextField(draft.text, model::editReply, minLines = 6, maxLines = 10, label = { Text(stringResource(R.string.comment_hint)) }) },
                    confirmButton = { TextButton(enabled = draft.text.isNotBlank(), onClick = model::sendReply) { Text(stringResource(R.string.comment_send)) } },
                    dismissButton = { TextButton(onClick = model::dismissReply) { Text(stringResource(R.string.comment_cancel)) } })
            }
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.mutationResults.collect { if (it is NetResult.Error) Toast.makeText(this@CommentActivity, R.string.msg_cloudflare_challenges, Toast.LENGTH_LONG).show() }
        } }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val action = model.action.value
        if (action != null && action.postId > 0 && action.booru.user != null && action.booru.type !in arrayOf(Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)) menuInflater.inflate(R.menu.comment, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
        R.id.action_comment_reply -> { model.action.value?.let { model.reply(it.postId) }; true }
        else -> super.onOptionsItemSelected(item)
    }
}
