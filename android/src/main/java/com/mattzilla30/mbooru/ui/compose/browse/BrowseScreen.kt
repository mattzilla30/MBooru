/* Boorusphere HomeSearchBar layout adapted to Compose; Chaldeaprjkt, BSD-3-Clause.
 * Original notice: assets/licenses/boorusphere.txt. MBooru modifications © 2026 mattzilla30. */
package com.mattzilla30.mbooru.ui.compose.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.actions

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun BrowseScreen(
    state: BrowseUiState,
    hint: String,
    suggestions: List<String>,
    onQuery: (String) -> Unit,
    onMode: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onNavigate: () -> Unit,
    onMenu: (AppActionKey) -> Unit,
    filters: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler(state.mode != 0) { onMode(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var ratings by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val actions = remember(state.menu, context) { state.menu.actions() }
    LaunchedEffect(state.mode) {
        if (state.mode == 1) {
            focus.requestFocus()
            keyboard?.show()
        } else keyboard?.hide()
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            com.mattzilla30.mbooru.ui.compose.common.ConnectivityBanner()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigate) {
                    Icon(painterResource(R.drawable.ic_arrow_back_24dp), "Back")
                }
                if (state.mode == 1) {
                    OutlinedTextField(
                        state.query,
                        onValueChange = onQuery,
                        singleLine = true,
                        placeholder = { Text(hint) },
                        modifier =
                            Modifier.weight(1f).testTag("browse-query-input").focusRequester(focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) }),
                    )
                } else {
                    Text(
                        state.query.ifBlank { hint },
                        Modifier.weight(1f)
                            .testTag("browse-query-toggle")
                            .clickable { onMode(1) }
                            .padding(vertical = 16.dp),
                    )
                }
                IconButton(
                    onClick = { if (state.mode == 1) onSearch(state.query) else onMode(1) }
                ) {
                    Icon(painterResource(R.drawable.ic_search_24dp), "Search")
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = { onMode(if (state.mode == 2) 0 else 2) }) {
                    Text(stringResource(R.string.sphere_filters))
                }
                TextButton(onClick = { ratings = true }) { Text(stringResource(R.string.rating)) }
                com.mattzilla30.mbooru.ui.compose.navigation.BrowseToolButtons()
                actions
                    .filter { it.visible && it.key != AppActionKey.ExpandOrClear }
                    .flatMap { it.children.ifEmpty { listOf(it) } }
                    .forEach { item ->
                        TextButton(onClick = { onMenu(item.key) }, enabled = item.enabled) {
                            Text(stringResource(item.title))
                        }
                    }
            }
            if (state.mode == 1 && suggestions.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 160.dp).fillMaxWidth()) {
                    items(suggestions.distinct(), key = { it }) { tag ->
                        TextButton(
                            onClick = {
                                onQuery(tag)
                                onSearch(tag)
                            }
                        ) {
                            Text(tag)
                        }
                    }
                }
            }
            if (state.mode == 2) Box(Modifier.weight(1f).fillMaxWidth()) { filters() }
            else content(Modifier.weight(1f).fillMaxWidth())
        }
    }
    if (ratings)
        AlertDialog(
            onDismissRequest = { ratings = false },
            title = { Text(stringResource(R.string.rating)) },
            text = {
                Column {
                    listOf(
                            R.string.sphere_rating_all to "",
                            R.string.browse_info_rating_safe to "rating:safe",
                            R.string.browse_info_rating_questionable to "rating:questionable",
                            R.string.browse_info_rating_explicit to "rating:explicit",
                        )
                        .forEach { (label, tag) ->
                            TextButton(
                                onClick = {
                                    val value =
                                        (state.query
                                                .replace(Regex("(?:^|\\s)-?rating:[^\\s]+"), "")
                                                .trim() + " " + tag)
                                            .trim()
                                    ratings = false
                                    onQuery(value)
                                    onSearch(value)
                                }
                            ) {
                                Text(stringResource(label))
                            }
                        }
                }
            },
            confirmButton = {},
        )
}
