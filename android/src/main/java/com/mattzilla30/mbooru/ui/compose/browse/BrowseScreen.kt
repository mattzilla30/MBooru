/* Boorusphere HomeSearchBar layout adapted to Compose; Chaldeaprjkt, BSD-3-Clause.
 * Original notice: assets/licenses/boorusphere.txt. MBooru modifications © 2026 mattzilla30. */
package com.mattzilla30.mbooru.ui.compose.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    bottomPadding: Boolean,
    onServers: () -> Unit = onNavigate,
    autoHideBottomBar: Boolean = false,
    filters: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler(state.mode != 0) { onMode(0) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var overflow by remember { mutableStateOf(false) }
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
                .imePadding()
        ) {
            // Content begins at the top, with the reference app's search controls anchored below
            // it.
            com.mattzilla30.mbooru.ui.compose.common.ConnectivityBanner()
            if (state.mode == 2)
                Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) { filters() }
            else content(Modifier.weight(1f).fillMaxWidth().statusBarsPadding())
            if (state.mode == 1 && suggestions.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 280.dp).fillMaxWidth()) {
                    items(suggestions.distinct(), key = { it }) { tag ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    onQuery(tag)
                                    onSearch(tag)
                                }
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_search_24dp),
                                null,
                                Modifier.size(20.dp),
                            )
                            Text(
                                tag,
                                Modifier.padding(start = 24.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible =
                    !autoHideBottomBar ||
                        com.mattzilla30.mbooru.ui.compose.navigation.LocalNavigationVisible
                            .current ||
                        state.mode != 0
            ) {
                Surface(
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .97f),
                    modifier = Modifier.fillMaxWidth().testTag("sphere-bottom-search"),
                ) {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (state.mode == 1)
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { ratings = true }) {
                                    Text(stringResource(R.string.rating))
                                }
                                TextButton(onClick = { onMode(2) }) {
                                    Text(stringResource(R.string.sphere_filters))
                                }
                                TextButton(onClick = { onQuery("") }) {
                                    Text(stringResource(R.string.sphere_clear))
                                }
                            }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .5f),
                            modifier =
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    .fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.heightIn(min = 56.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton(
                                    onClick = {
                                        if (state.mode != 0) onMode(0)
                                        else if (bottomPadding) onServers() else onNavigate()
                                    }
                                ) {
                                    if (state.mode == 0 && bottomPadding)
                                        Box(Modifier.size(32.dp)) {
                                            coil3.compose.AsyncImage(
                                                state.booru?.let {
                                                    "${it.scheme}://${it.host}/favicon.ico"
                                                },
                                                stringResource(R.string.sphere_select_server),
                                                modifier =
                                                    Modifier.size(21.dp).align(Alignment.Center),
                                            )
                                            Surface(
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.align(Alignment.BottomEnd),
                                            ) {
                                                Text(
                                                    if (state.query.contains("rating:explicit")) "e"
                                                    else if (
                                                        state.query.contains("rating:questionable")
                                                    )
                                                        "q"
                                                    else "s",
                                                    Modifier.padding(horizontal = 4.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color =
                                                        MaterialTheme.colorScheme
                                                            .onPrimaryContainer,
                                                )
                                            }
                                        }
                                    else
                                        Icon(
                                            painterResource(R.drawable.ic_arrow_back_24dp),
                                            stringResource(
                                                androidx.appcompat.R.string
                                                    .abc_action_bar_up_description
                                            ),
                                        )
                                }
                                if (state.mode == 1) {
                                    TextField(
                                        state.query,
                                        onValueChange = onQuery,
                                        singleLine = true,
                                        placeholder = {
                                            Text(hint, style = MaterialTheme.typography.bodyLarge)
                                        },
                                        modifier =
                                            Modifier.weight(1f)
                                                .testTag("browse-query-input")
                                                .focusRequester(focus),
                                        keyboardOptions =
                                            KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions =
                                            KeyboardActions(onSearch = { onSearch(state.query) }),
                                        colors =
                                            TextFieldDefaults.colors(
                                                focusedContainerColor =
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                unfocusedContainerColor =
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                focusedIndicatorColor =
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                                unfocusedIndicatorColor =
                                                    androidx.compose.ui.graphics.Color.Transparent,
                                            ),
                                    )
                                    IconButton(onClick = { onQuery("") }) {
                                        Icon(
                                            painterResource(R.drawable.ic_close_24dp),
                                            stringResource(R.string.sphere_clear),
                                        )
                                    }
                                    IconButton(onClick = { onSearch(state.query) }) {
                                        Icon(
                                            painterResource(R.drawable.ic_search_24dp),
                                            stringResource(android.R.string.search_go),
                                        )
                                    }
                                } else {
                                    Text(
                                        state.query.ifBlank {
                                            state.booru?.name
                                                ?: state.title.ifEmpty {
                                                    stringResource(R.string.app_name)
                                                }
                                        },
                                        Modifier.weight(1f)
                                            .testTag("browse-query-toggle")
                                            .clickable { onMode(1) }
                                            .padding(vertical = 16.dp),
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (bottomPadding)
                                        IconButton(onClick = onNavigate) {
                                            Icon(
                                                painterResource(R.drawable.ic_menu_24dp),
                                                stringResource(R.string.navigation_drawer_open),
                                            )
                                        }
                                    Box {
                                        IconButton(onClick = { overflow = true }) {
                                            Icon(
                                                painterResource(R.drawable.ic_more_vert_24dp),
                                                stringResource(
                                                    androidx.appcompat.R.string
                                                        .abc_action_menu_overflow_description
                                                ),
                                            )
                                        }
                                        DropdownMenu(overflow, { overflow = false }) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.sphere_filters))
                                                },
                                                onClick = {
                                                    overflow = false
                                                    onMode(2)
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.rating)) },
                                                onClick = {
                                                    overflow = false
                                                    ratings = true
                                                },
                                            )
                                            actions
                                                .filter {
                                                    it.visible &&
                                                        it.key != AppActionKey.ExpandOrClear
                                                }
                                                .flatMap { it.children.ifEmpty { listOf(it) } }
                                                .forEach { item ->
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(item.title)) },
                                                        enabled = item.enabled,
                                                        onClick = {
                                                            overflow = false
                                                            onMenu(item.key)
                                                        },
                                                    )
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
