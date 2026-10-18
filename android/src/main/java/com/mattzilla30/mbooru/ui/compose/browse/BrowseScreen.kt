package com.mattzilla30.mbooru.ui.compose.browse

import android.view.MenuItem
import androidx.activity.compose.BackHandler
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.widget.PopupMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.mattzilla30.mbooru.ui.compose.common.menuActions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R

@Composable
fun BrowseScreen(
    state: BrowseUiState,
    hint: String,
    suggestions: List<String>,
    onQuery: (String) -> Unit,
    onMode: (Int) -> Unit,
    onSearch: (String) -> Unit,
    onNavigate: () -> Unit,
    onMenu: (MenuItem) -> Unit,
    onAddToMuzei: (String) -> Unit,
    bottomPadding: Boolean,
    filters: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    BackHandler(state.mode != 0) { onMode(0) }
    var editing by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(state.query)) }
    LaunchedEffect(state.query) { if (editing.text != state.query) editing = androidx.compose.ui.text.input.TextFieldValue(state.query) }
    val view = androidx.compose.ui.platform.LocalView.current
    val addSelection by rememberUpdatedState { onAddToMuzei(editing.text.substring(editing.selection.min, editing.selection.max)) }
    val textToolbar = remember(view) { SearchTextToolbar(view) { addSelection() } }
    DisposableEffect(textToolbar) { onDispose { textToolbar.hide() } }
    var overflow by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.mode) { if (state.mode == 1) { focus.requestFocus(); keyboard?.show() } }
    val context = LocalContext.current
    val actions = remember(state.menu, context) { menuActions(context, state.menu) }
    var submenu by remember { mutableStateOf<Int?>(null) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).statusBarsPadding().imePadding()) {
            com.mattzilla30.mbooru.ui.compose.common.ConnectivityBanner()
            Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 2.dp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp).fillMaxWidth()) {
                Row(Modifier.heightIn(min = 54.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (state.mode != 0) onMode(0) else onNavigate() }) {
                        Icon(if (state.mode == 0 && bottomPadding) Icons.Default.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(if (state.mode == 0 && bottomPadding) R.string.navigation_drawer_open else androidx.appcompat.R.string.abc_action_bar_up_description))
                    }
                    if (state.mode == 1 || state.mode == 2) {
                        CompositionLocalProvider(androidx.compose.ui.platform.LocalTextToolbar provides textToolbar) {
                        TextField(editing, onValueChange = { editing = it; onQuery(it.text) },
                            placeholder = { Text(hint, style = MaterialTheme.typography.bodyLarge) },
                            singleLine = true, modifier = Modifier.weight(1f).testTag("browse-query-input").focusRequester(focus),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                        }
                    } else Text(state.title.ifEmpty { stringResource(R.string.app_name) },
                        modifier = Modifier.weight(1f).testTag("browse-query-toggle").clickable { onMode(1) }.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyLarge)
                    if (state.query.isNotEmpty() && state.mode != 0 && actions.none { it.item.itemId == R.id.action_expand_or_clear }) IconButton(onClick = { onQuery("") }) {
                        Icon(painterResource(R.drawable.ic_close_24dp), stringResource(androidx.appcompat.R.string.abc_searchview_description_clear))
                    }
                    actions.filter { it.direct && it.item.isVisible }.forEach { action ->
                        Box {
                            IconButton(onClick = {
                                if (action.item.hasSubMenu()) submenu = action.item.itemId else onMenu(action.item)
                            }) {
                                Icon(painterResource(action.icon), action.item.title.toString(),
                                    modifier = if (action.item.itemId == R.id.action_expand_or_clear && state.mode != 0) Modifier.rotate(135f) else Modifier)
                            }
                            val sub = action.item.subMenu
                            DropdownMenu(expanded = submenu == action.item.itemId, onDismissRequest = { submenu = null }) {
                                if (sub != null) repeat(sub.size()) { index ->
                                    val item = sub.getItem(index)
                                    DropdownMenuItem(text = { Text(item.title.toString()) }, onClick = { submenu = null; onMenu(item) })
                                }
                            }
                        }
                    }
                    val overflowItems = actions.filter { !it.direct && it.item.isVisible }.flatMap { action ->
                        action.item.subMenu?.let { sub -> (0 until sub.size()).map { sub.getItem(it) } } ?: listOf(action.item)
                    }
                    if (overflowItems.isNotEmpty()) Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description))
                        }
                        DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                            overflowItems.forEach { item ->
                                DropdownMenuItem(text = { Text(item.title.toString()) }, onClick = { overflow = false; onMenu(item) })
                            }
                        }
                    }
                }
            }
            if (state.mode == 1 && suggestions.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(suggestions.distinct(), key = { it }) { suggestion ->
                        Text(suggestion, Modifier.fillMaxWidth().clickable { onQuery(suggestion); onSearch(suggestion) }
                            .padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (state.mode == 2) Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
                .padding(bottom = if (bottomPadding) 80.dp else 0.dp)) { filters() }
            else content(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()
                .padding(bottom = if (bottomPadding && !com.mattzilla30.mbooru.app.Settings.autoHideBottomBar) 80.dp else 0.dp))
        }
    }
}
