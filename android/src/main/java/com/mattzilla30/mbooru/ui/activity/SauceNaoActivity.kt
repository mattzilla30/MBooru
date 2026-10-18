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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.common.saucenao.api.SauceNaoApi
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.compose.search.ImageSearchScreen
import com.mattzilla30.mbooru.ui.helper.OpenFileLifecycleObserver
import com.mattzilla30.mbooru.ui.viewmodel.getSauceNaoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class SauceNaoActivity : BaseActivity() {
    companion object {
        const val SAUCE_NAO_SEARCH_URL_KEY = "sauce_nao_search_url"

        fun startSearch(context: Context, url: String) {
            context.startActivity(
                Intent(context, SauceNaoActivity::class.java).apply {
                    putExtra(SAUCE_NAO_SEARCH_URL_KEY, url)
                }
            )
        }
    }

    private val api by inject<SauceNaoApi>()
    private val model by lazy { getSauceNaoViewModel(api) }
    private lateinit var picker: OpenFileLifecycleObserver
    private var prompt by mutableStateOf<Int?>(null)
    private var localError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        picker = OpenFileLifecycleObserver(activityResultRegistry) { search(it) }
        lifecycle.addObserver(picker)
    }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.title_sauce_nao)
        setComposeContent {
            val response by model.data.collectAsStateWithLifecycle()
            val loading by model.isLoading.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            var sourceUrls by remember { mutableStateOf<List<String>>(emptyList()) }
            var input by
                remember(prompt) {
                    mutableStateOf(
                        if (prompt == R.string.sauce_nao_change_api_key) settings.sauceNaoApiKey
                        else ""
                    )
                }
            var options by remember { mutableStateOf(false) }
            SideEffect {
                response?.let {
                    screenSubtitle =
                        getString(R.string.sauce_nao_remaining_times_today, it.header.longRemaining)
                }
            }
            ImageSearchScreen(
                response?.results.orEmpty(),
                loading,
                localError ?: error,
                add = { options = true },
                retry = { if (localError != null) options = true else model.retry() },
                key = { item, index -> "${item.header.thumbnail}:$index" },
            ) { item ->
                Card(
                    Modifier.fillMaxWidth().padding(8.dp).clickable {
                        sourceUrls = item.data.extUrls.orEmpty()
                    }
                ) {
                    Row(Modifier.padding(12.dp)) {
                        AsyncImage(item.header.thumbnail, null, modifier = Modifier.size(100.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.header.indexName, style = MaterialTheme.typography.bodyLarge)
                            Text(item.header.similarity)
                            val info = sauceInfo(item)
                            info.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
            if (options)
                AlertDialog(
                    onDismissRequest = { options = false },
                    title = { Text(stringResource(R.string.title_search)) },
                    text = {
                        Column {
                            TextButton(
                                onClick = {
                                    options = false
                                    prompt = R.string.sauce_nao_image_url
                                }
                            ) {
                                Text(stringResource(R.string.sauce_nao_image_url))
                            }
                            TextButton(
                                onClick = {
                                    options = false
                                    picker.openDocument("image/*")
                                }
                            ) {
                                Text(stringResource(R.string.sauce_nao_search_from_file))
                            }
                        }
                    },
                    confirmButton = {},
                )
            prompt?.let { title ->
                AlertDialog(
                    onDismissRequest = { prompt = null },
                    title = { Text(stringResource(title)) },
                    text = {
                        OutlinedTextField(
                            input,
                            { input = it },
                            singleLine = true,
                            visualTransformation =
                                if (title == R.string.sauce_nao_change_api_key)
                                    PasswordVisualTransformation()
                                else VisualTransformation.None,
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (title == R.string.sauce_nao_change_api_key) {
                                    settings.sauceNaoApiKey = input.trim()
                                    localError =
                                        if (input.isBlank())
                                            getString(R.string.sauce_nao_api_key_unset)
                                        else null
                                } else if (input.startsWith("http")) search(input.trim())
                                else localError = getString(R.string.sauce_nao_invalid_image_url)
                                prompt = null
                            }
                        ) {
                            Text(stringResource(R.string.dialog_ok))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { prompt = null }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    },
                )
            }
            if (sourceUrls.isNotEmpty())
                AlertDialog(
                    onDismissRequest = { sourceUrls = emptyList() },
                    title = { Text(stringResource(R.string.sauce_nao_source)) },
                    text = {
                        Column {
                            sourceUrls.forEach { url ->
                                TextButton(
                                    onClick = {
                                        launchUrl(url)
                                        sourceUrls = emptyList()
                                    }
                                ) {
                                    Text(url)
                                }
                            }
                        }
                    },
                    confirmButton = {},
                )
        }
        if (savedInstanceState == null)
            intent
                .getStringExtra(SAUCE_NAO_SEARCH_URL_KEY)
                ?.takeIf { it.isNotEmpty() }
                ?.let(::search)
    }

    private fun search(url: String) {
        val key = settings.sauceNaoApiKey
        if (key.isBlank()) localError = getString(R.string.sauce_nao_api_key_unset)
        else {
            localError = null
            model.searchByUrl(url, key)
        }
    }

    private fun search(uri: Uri) {
        val key = settings.sauceNaoApiKey
        if (key.isBlank()) {
            localError = getString(R.string.sauce_nao_api_key_unset)
            return
        }
        localError = null
        lifecycleScope.launch {
            val bytes =
                withContext(Dispatchers.IO) {
                    runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                        .getOrNull()
                }
            if (bytes != null) model.searchByImage(bytes, uri.toDecodedString().fileExt(), key)
        }
    }

    override fun onAction(action: AppActionKey): Boolean =
        when (action) {
            AppActionKey.Back -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            AppActionKey.SauceNaoChangeApiKey -> {
                prompt = R.string.sauce_nao_change_api_key
                true
            }
            AppActionKey.SauceNaoGetApiKey -> {
                launchUrl("https://saucenao.com/user.php")
                true
            }
            else -> super.onAction(action)
        }
}

private fun sauceInfo(result: com.mattzilla30.mbooru.common.saucenao.model.Result): List<String> =
    with(result.data) {
        when {
            !characters.isNullOrEmpty() ->
                listOf("Material: ${material.orEmpty()}", "Characters: $characters")
            pixivId != null -> listOf("Pixiv ID: $pixivId", "Title: ${title.orEmpty()}")
            anidbAid != null -> listOf("Anidb aid: $anidbAid", "Source: ${source.orEmpty()}")
            seigaId != null -> listOf("Seiga ID: $seigaId", "Title: ${title.orEmpty()}")
            daId != null -> listOf("Da ID: $daId", "Title: ${title.orEmpty()}")
            engName != null -> listOf("Eng name: $engName", "Jp name: ${jpName.orEmpty()}")
            else -> emptyList()
        }
    }
