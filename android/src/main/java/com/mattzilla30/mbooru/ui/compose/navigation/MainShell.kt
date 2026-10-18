package com.mattzilla30.mbooru.ui.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import coil.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class DrawerAction(val title: Int, val icon: Int, val action: String)
data class BrowseTab(val id: Int, val title: Int, val icon: Int, val selectedIcon: Int)

fun browseTabs(type: Int): List<BrowseTab> {
    val posts = BrowseTab(R.id.nav_posts, R.string.title_posts, R.drawable.ic_photo_outline_24dp, R.drawable.ic_photo_24dp)
    val popular = BrowseTab(R.id.nav_popular, R.string.title_popular, R.drawable.ic_whatshot_outline_24dp, R.drawable.ic_whatshot_24dp)
    val pools = BrowseTab(R.id.nav_pools, R.string.title_pools, R.drawable.ic_photo_album_outline_24dp, R.drawable.ic_photo_album_24dp)
    val tags = BrowseTab(R.id.nav_tags, R.string.title_tags, R.drawable.ic_bookmark_border_24dp, R.drawable.ic_bookmark_24dp)
    val artists = BrowseTab(R.id.nav_artists, R.string.title_artists, R.drawable.ic_people_outline_24dp, R.drawable.ic_people_24dp)
    return when (type) {
        Values.BOORU_TYPE_SHIMMIE -> listOf(posts)
        Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY -> listOf(posts, tags)
        Values.BOORU_TYPE_SANKAKU -> listOf(posts, popular, pools, tags)
        else -> listOf(posts, popular, pools, tags, artists)
    }
}

class MainShellViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val drawer = Channel<Unit>(Channel.CONFLATED)
    val drawerRequests = drawer.receiveAsFlow()
    val navVisible = saved.getStateFlow("navigationVisible", true)
    private val revision = MutableStateFlow(0)
    val preferencesRevision = revision.asStateFlow()
    fun openDrawer() { drawer.trySend(Unit) }
    fun navigationVisible(show: Boolean) { saved["navigationVisible"] = show }
    fun preferencesChanged() { revision.value++ }
}

@Composable
fun MainShell(
    boorus: List<Booru>, selectedUid: Long, tabs: List<BrowseTab>, currentTab: Int,
    navigationVisible: Boolean,
    drawerRequests: kotlinx.coroutines.flow.Flow<Unit>,
    onBooru: (Long) -> Unit, onAction: (String) -> Unit, onTab: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var profilesExpanded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(drawerRequests) { drawerRequests.collect { drawer.open() } }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    val actions = listOf(
        DrawerAction(R.string.title_account, R.drawable.ic_account_circle_outline_24dp, "account"),
        DrawerAction(R.string.title_comments, R.drawable.ic_comment_outline_24dp, "comments"),
        DrawerAction(R.string.title_history, R.drawable.ic_history_24dp, "history"),
        DrawerAction(R.string.title_tag_blacklist, R.drawable.ic_visibility_off_outline_24dp, "blacklist"),
        DrawerAction(R.string.title_muzei, R.drawable.ic_muzei_24dp, "muzei"),
        DrawerAction(R.string.title_sauce_nao, R.drawable.ic_search_24dp, "saucenao"),
        DrawerAction(R.string.title_what_anime, R.drawable.ic_youtube_searched_for_24dp, "tracemoe"),
    )
    ModalNavigationDrawer(modifier = Modifier.semantics { testTagsAsResourceId = true }, drawerState = drawer, drawerContent = {
        ModalDrawerSheet(Modifier.widthIn(max = 320.dp).windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))) {
            LazyColumn(Modifier.weight(1f).statusBarsPadding().testTag("main-drawer-list")) {
                item {
                    val active = boorus.find { it.uid == selectedUid } ?: boorus.firstOrNull()
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).testTag("main-drawer-profile").clickable { profilesExpanded = !profilesExpanded }.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(active?.let { "${it.scheme}://${it.host}/favicon.ico" }, null, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.weight(1f))
                                Icon(painterResource(R.drawable.ic_expand_more_24dp), stringResource(R.string.title_manage_boorus))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(active?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                            Text(active?.let { "${it.scheme}://${it.host}" }.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (profilesExpanded) {
                items(boorus, key = { it.uid }) { booru ->
                    NavigationDrawerItem(label = { Column {
                        Text(booru.name)
                        Text("${booru.scheme}://${booru.host}", style = MaterialTheme.typography.bodySmall)
                    } }, selected = booru.uid == selectedUid,
                        icon = { AsyncImage(if (booru.type == Values.BOORU_TYPE_SANKAKU && booru.host.startsWith("capi-v2."))
                            "https://sankaku.app/images/192x192.png" else "${booru.scheme}://${booru.host}/favicon.ico",
                            null, modifier = Modifier.size(40.dp)) },
                        onClick = { onBooru(booru.uid); profilesExpanded = false })
                }
                item {
                    NavigationDrawerItem(label = { Text(stringResource(R.string.title_manage_boorus)) }, selected = false,
                        icon = { Icon(painterResource(R.drawable.ic_settings_outline_24dp), null) },
                        onClick = { onAction("boorus"); profilesExpanded = false })
                    HorizontalDivider()
                }
                }
                if (!profilesExpanded) {
                items(actions, key = { it.action }) { item ->
                    NavigationDrawerItem(label = { Text(stringResource(item.title)) }, selected = false,
                        icon = { Icon(painterResource(item.icon), null) },
                        onClick = { onAction(item.action); scope.launch { drawer.close() } })
                }
                }
            }
            HorizontalDivider()
            listOf(DrawerAction(R.string.title_settings, R.drawable.ic_settings_outline_24dp, "settings"),
                DrawerAction(R.string.title_about, R.drawable.ic_info_outline_24dp, "about")).forEach { item ->
                NavigationDrawerItem(label = { Text(stringResource(item.title)) }, selected = false,
                    icon = { Icon(painterResource(item.icon), null) },
                    onClick = { onAction(item.action); scope.launch { drawer.close() } })
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            content()
            AnimatedVisibility(navigationVisible, modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)), containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { tab ->
                        val selected = tab.id == currentTab
                        NavigationBarItem(modifier = Modifier.testTag("browse-tab-${browseRoute(tab.id)}"), selected = selected, onClick = { onTab(tab.id) },
                            label = { Text(stringResource(tab.title)) },
                            icon = { Icon(painterResource(if (selected) tab.selectedIcon else tab.icon), null) })
                    }
                }
            }
        }
    }
}
