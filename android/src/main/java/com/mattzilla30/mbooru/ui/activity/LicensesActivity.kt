// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.activity

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.licenses.LicenseCatalog
import com.mattzilla30.mbooru.data.licenses.OpenSourceLicense
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LicensesActivity : BaseActivity() {
    private var selected by mutableStateOf<String?>(null)

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        selected = savedInstanceState?.getString("license")
        setScreenTitle(R.string.about_open_source_licenses)
        setComposeContent {
            var query by rememberSaveable { mutableStateOf("") }
            val listState = rememberLazyListState()
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            var attempt by remember { mutableIntStateOf(0) }
            val catalog by
                produceState<List<OpenSourceLicense>?>(null, attempt) {
                    value =
                        withContext(Dispatchers.IO) {
                            try {
                                LicenseCatalog.load(this@LicensesActivity)
                            } catch (_: java.io.IOException) {
                                emptyList()
                            } catch (_: IllegalArgumentException) {
                                emptyList()
                            }
                        }
                }
            val entry = catalog?.firstOrNull { it.name == selected }
            SideEffect {
                screenTitle = entry?.name ?: getString(R.string.about_open_source_licenses)
            }
            BackHandler(selected != null) { selected = null }
            when {
                catalog == null ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                catalog!!.isEmpty() ->
                    TextButton(onClick = { attempt++ }) {
                        Text(stringResource(R.string.action_retry))
                    }
                entry != null -> LicenseText(entry)
                else ->
                    LicenseList(catalog!!, query, { query = it }, listState) {
                        focus.clearFocus(force = true)
                        keyboard?.hide()
                        selected = it.name
                    }
            }
        }
    }

    @Composable
    @Suppress("FunctionNaming")
    private fun LicenseList(
        catalog: List<OpenSourceLicense>,
        query: String,
        onQuery: (String) -> Unit,
        listState: LazyListState,
        open: (OpenSourceLicense) -> Unit,
    ) {
        Column {
            OutlinedTextField(
                query,
                onQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.title_search)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("license-search"),
            )
            LazyColumn(Modifier.weight(1f).testTag("license-list"), state = listState) {
                items(
                    catalog.filter { it.name.contains(query, ignoreCase = true) },
                    key = { it.name },
                ) { license ->
                    Text(
                        license.name,
                        Modifier.fillMaxWidth().clickable { open(license) }.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    @Composable
    @Suppress("FunctionNaming")
    private fun LicenseText(entry: OpenSourceLicense) {
        val text = entry.text.trim()
        val url = text.takeIf {
            !it.contains('\n') && (it.startsWith("https://") || it.startsWith("http://"))
        }
        SelectionContainer {
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .testTag("license-text")
            ) {
                Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                if (url != null) TextButton(onClick = { launchUrl(url) }) { Text(url) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("license", selected)
        super.onSaveInstanceState(outState)
    }

    override fun onAction(action: AppActionKey): Boolean =
        if (action == AppActionKey.Back && selected != null) {
            selected = null
            true
        } else super.onAction(action)
}
