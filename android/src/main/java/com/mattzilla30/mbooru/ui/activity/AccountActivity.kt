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

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.model.common.User
import com.mattzilla30.mbooru.data.repository.user.UserRepositoryImpl
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.account.AccountViewModel
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class AccountActivity : PathActivity() , org.koin.core.component.KoinComponent {
    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    companion object {
        const val USER_ID_KEY = "user_id"
        const val USER_NAME_KEY = "user_name"
        const val USER_AVATAR_KEY = "user_avatar"
    }

    private val apis by inject<BooruApis>()
    private var booru: com.mattzilla30.mbooru.data.model.common.Booru? = null
    private var confirmRemoval by mutableStateOf(false)
    private val model by
        viewModels<AccountViewModel> {
            val site = checkNotNull(booru)
            val requested = intent.getIntExtra(USER_ID_KEY, -1)
            val user =
                if (requested > 0)
                    User(
                        id = requested,
                        name = intent.getStringExtra(USER_NAME_KEY).orEmpty(),
                        avatar = intent.getStringExtra(USER_AVATAR_KEY),
                    )
                else site.user
            viewModelFactory {
                initializer { AccountViewModel(UserRepositoryImpl(apis), site, user, booruManager) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            booru = booruManager.getBooruByUid(Settings.activatedBooruUid)
            if (isFinishing || isDestroyed) return@launch
            initializeContent()
        }
    }
    private fun initializeContent() {
        val site = booru ?: return finish()
        screenTitle = getString(R.string.title_account_and_booru, site.name)
        setComposeContent {
            val user by model.user.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val item = user
            if (item != null)
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val avatar =
                        if (site.type == Values.BOORU_TYPE_MOE)
                            stringResource(
                                R.string.account_user_avatars,
                                site.scheme,
                                site.host,
                                item.id,
                            )
                        else item.avatar
                    AsyncImage(
                        avatar,
                        item.name,
                        placeholder = painterResource(R.drawable.avatar_account),
                        error = painterResource(R.drawable.avatar_account),
                        modifier = Modifier.size(100.dp),
                    )
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(stringResource(R.string.account_user_id, item.id), Modifier.padding(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AccountAction(R.string.account_favorites, R.drawable.ic_favorite_24dp) {
                            action("favorites")
                        }
                        AccountAction(R.string.account_posts, R.drawable.ic_photo_24dp) {
                            action("posts")
                        }
                        AccountAction(R.string.account_comments, R.drawable.ic_comment_24dp) {
                            action("comments")
                        }
                    }
                    if (site.type == Values.BOORU_TYPE_SANKAKU)
                        AccountAction(
                            R.string.account_recommended,
                            R.drawable.ic_sentiment_very_satisfied_24dp,
                        ) {
                            action("recommended")
                        }
                    error?.let {
                        Text(
                            stringResource(R.string.msg_cloudflare_challenges),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            if (confirmRemoval)
                AlertDialog(
                    onDismissRequest = { confirmRemoval = false },
                    title = { Text(stringResource(R.string.account_user_dialog_title_remove)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                model.remove()
                                confirmRemoval = false
                            }
                        ) {
                            Text(stringResource(R.string.dialog_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRemoval = false }) {
                            Text(stringResource(R.string.dialog_no))
                        }
                    },
                )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.removal.collect { finish() } }
        }
    }

    @Composable
    private fun AccountAction(title: Int, icon: Int, click: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            FilledTonalIconButton(onClick = click) {
                Icon(painterResource(icon), stringResource(title))
            }
            Text(stringResource(title), style = MaterialTheme.typography.bodyMedium)
        }
    }

    private fun action(action: String) {
        val site = booru ?: return
        val user = model.user.value ?: return
        if (
            action != "favorites" &&
                site.type in arrayOf(Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)
        ) {
            Toast.makeText(this, R.string.msg_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        when (action) {
            "favorites" -> {
                if (site.type == Values.BOORU_TYPE_GEL_LEGACY)
                    launchUrl(
                        "${site.scheme}://${site.host}/index.php?page=favorites&s=view&id=${user.id}"
                    )
                else
                    SearchActivity.startSearch(
                        this,
                        when (site.type) {
                            Values.BOORU_TYPE_MOE -> "vote:3:${user.name} order:vote"
                            Values.BOORU_TYPE_GEL -> "fav:${user.id}"
                            else -> "fav:${user.name}"
                        },
                    )
            }
            "posts" -> SearchActivity.startSearch(this, "user:${user.name}")
            "comments" ->
                CommentActivity.startActivity(
                    this,
                    query =
                        if (site.type == Values.BOORU_TYPE_DAN) user.name else "user:${user.name}",
                )
            "recommended" -> SearchActivity.startSearch(this, "recommended_for:${user.name}")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (booru?.user != null) menuInflater.inflate(R.menu.account, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_account_remove -> {
                confirmRemoval = true
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
}
