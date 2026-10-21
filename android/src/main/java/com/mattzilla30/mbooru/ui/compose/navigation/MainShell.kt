/* MBooru Compose adaptation of Boorusphere's HomeDrawer (Chaldeaprjkt, BSD-3-Clause).
 * See assets/licenses/boorusphere.txt. MBooru modifications © 2026 mattzilla30. */
package com.mattzilla30.mbooru.ui.compose.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import coil3.compose.AsyncImage
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.model.common.Booru
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class DrawerDestination {
    Sites,
    Account,
    Comments,
    History,
    Blacklist,
    Settings,
    About,
    Downloads,
    Favorites,
    Theme,
    Exit,
}

data class DrawerAction(val title: Int, val icon: Int, val action: DrawerDestination)

data class BrowseTab(
    val id: Int,
    val title: Int,
    val icon: Int,
    val selectedIcon: Int,
    val website: String? = null,
)

fun browseTabs(type: Int): List<BrowseTab> {
    val posts =
        BrowseTab(
            R.id.nav_posts,
            R.string.title_posts,
            R.drawable.ic_photo_outline_24dp,
            R.drawable.ic_photo_24dp,
        )
    val capabilities = com.mattzilla30.mbooru.core.model.BooruEngine.fromLegacyId(type).capabilities
    return buildList {
        add(posts)
        if (capabilities.popular)
            add(
                BrowseTab(
                    R.id.nav_popular,
                    if (type == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34)
                        R.string.title_top_rated
                    else R.string.title_popular,
                    R.drawable.ic_whatshot_outline_24dp,
                    R.drawable.ic_whatshot_24dp,
                )
            )
        if (capabilities.pools)
            add(
                BrowseTab(
                    R.id.nav_pools,
                    R.string.title_pools,
                    R.drawable.ic_photo_album_outline_24dp,
                    R.drawable.ic_photo_album_24dp,
                )
            )
        if (capabilities.tags)
            add(
                BrowseTab(
                    R.id.nav_tags,
                    R.string.title_tags,
                    R.drawable.ic_bookmark_border_24dp,
                    R.drawable.ic_bookmark_24dp,
                )
            )
        if (type == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34) addAll(rule34WebTools())
        if (capabilities.artists)
            add(
                BrowseTab(
                    R.id.nav_artists,
                    R.string.title_artists,
                    R.drawable.ic_people_outline_24dp,
                    R.drawable.ic_people_24dp,
                )
            )
    }
}

class MainShellViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val drawer = Channel<Unit>(Channel.CONFLATED)
    val drawerRequests = drawer.receiveAsFlow()
    private val servers = Channel<Unit>(Channel.CONFLATED)
    val serverRequests = servers.receiveAsFlow()

    fun chooseServer() {
        servers.trySend(Unit)
    }

    val requestedBrowse = saved.getStateFlow<Int?>("requested_browse", null)

    fun requestBrowse(id: Int) {
        if (id == R.id.nav_posts) {
            section(0)
            saved["requested_browse"] = null
            return
        }
        advanced()
        saved["requested_browse"] = id
    }

    fun browseHandled() {
        saved["requested_browse"] = null
    }

    val homeSection = saved.getStateFlow("home_section", 0)
    val secondary = saved.getStateFlow("secondary", false)

    fun section(value: Int) {
        saved["home_section"] = value
        saved["secondary"] = false
    }

    fun advanced() {
        saved["home_section"] = 0
        saved["secondary"] = true
    }

    val navVisible = saved.getStateFlow("navigationVisible", true)
    private val revision = MutableStateFlow(0)
    val preferencesRevision = revision.asStateFlow()

    fun openDrawer() {
        drawer.trySend(Unit)
    }

    fun navigationVisible(show: Boolean) {
        saved["navigationVisible"] = show
    }

    fun preferencesChanged() {
        revision.value++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun MainShell(
    boorus: List<Booru>,
    selectedUid: Long,
    navigationVisible: Boolean,
    drawerRequests: kotlinx.coroutines.flow.Flow<Unit>,
    onBooru: (Long) -> Unit,
    onAction: (DrawerDestination) -> Unit,
    serverRequests: kotlinx.coroutines.flow.Flow<Unit> = kotlinx.coroutines.flow.emptyFlow(),
    onEditServer: (Booru) -> Unit = {},
    onRemoveServer: (Long) -> Unit = {},
    homeBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val compactDrawer =
        LocalConfiguration.current.screenHeightDp < COMPACT_DRAWER_HEIGHT_DP ||
            LocalDensity.current.fontScale > COMPACT_DRAWER_FONT_SCALE
    var siteMenu by remember { mutableStateOf<Long?>(null) }
    var exit by remember { mutableStateOf(false) }
    var selectingServer by remember { mutableStateOf(false) }
    LaunchedEffect(serverRequests) { serverRequests.collect { selectingServer = true } }
    LaunchedEffect(drawerRequests) { drawerRequests.collect { drawer.open() } }
    BackHandler(!drawer.isOpen && homeBack != null) { homeBack?.invoke() }
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
    fun action(destination: DrawerDestination) {
        onAction(destination)
        scope.launch { drawer.close() }
    }
    ModalNavigationDrawer(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                Modifier.widthIn(max = 326.dp),
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
            ) {
                Column(
                    Modifier.fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .testTag("sphere-drawer-content")
                        .then(
                            if (compactDrawer) Modifier.verticalScroll(rememberScrollState())
                            else Modifier
                        )
                ) {
                    Row(
                        Modifier.fillMaxWidth()
                            .testTag("main-drawer-profile")
                            .padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "MBooru!",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        ThemeButton { onAction(DrawerDestination.Theme) }
                    }
                    LazyColumn(
                        (if (compactDrawer) Modifier.height(280.dp) else Modifier.weight(1f))
                            .testTag("main-drawer-list")
                    ) {
                        items(boorus, key = { it.uid }) { site ->
                            Surface(
                                color =
                                    if (site.uid == selectedUid)
                                        MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                                modifier = Modifier.padding(end = 16.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clickable {
                                            onBooru(site.uid)
                                            scope.launch { drawer.close() }
                                        }
                                        .padding(start = 24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        "${site.scheme}://${site.host}/favicon.ico",
                                        null,
                                        modifier = Modifier.size(21.dp),
                                    )
                                    Text(
                                        site.name,
                                        Modifier.weight(1f).padding(start = 24.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Box {
                                        IconButton(onClick = { siteMenu = site.uid }) {
                                            Icon(
                                                painterResource(R.drawable.ic_more_vert_24dp),
                                                stringResource(R.string.sphere_server_options),
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        DropdownMenu(siteMenu == site.uid, { siteMenu = null }) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.sphere_edit))
                                                },
                                                onClick = {
                                                    siteMenu = null
                                                    onEditServer(site)
                                                    scope.launch { drawer.close() }
                                                },
                                            )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.sphere_remove))
                                                },
                                                onClick = {
                                                    siteMenu = null
                                                    onRemoveServer(site.uid)
                                                },
                                            )
                                            if (
                                                site.uid == selectedUid &&
                                                    com.mattzilla30.mbooru.core.model.BooruEngine
                                                        .fromLegacyId(site.type)
                                                        .capabilities
                                                        .accounts
                                            )
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(stringResource(R.string.title_account))
                                                    },
                                                    onClick = {
                                                        siteMenu = null
                                                        onBooru(site.uid)
                                                        action(DrawerDestination.Account)
                                                    },
                                                )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    listOf(
                            DrawerAction(
                                R.string.title_downloads,
                                R.drawable.ic_file_download_24dp,
                                DrawerDestination.Downloads,
                            ),
                            DrawerAction(
                                R.string.sphere_favorites,
                                R.drawable.ic_favorite_24dp,
                                DrawerDestination.Favorites,
                            ),
                            DrawerAction(
                                R.string.title_tag_blacklist,
                                R.drawable.ic_visibility_off_outline_24dp,
                                DrawerDestination.Blacklist,
                            ),
                            DrawerAction(
                                R.string.title_settings,
                                R.drawable.ic_settings_outline_24dp,
                                DrawerDestination.Settings,
                            ),
                        )
                        .forEach { row ->
                            SphereDrawerRow(row.title, row.icon) { action(row.action) }
                        }
                    SphereDrawerRow(R.string.dialog_exit, R.drawable.ic_arrow_back_24dp) {
                        exit = true
                    }
                    SphereDrawerRow(
                        R.string.app_name,
                        R.drawable.ic_info_outline_24dp,
                        summary = BuildConfig.VERSION_NAME,
                    ) {
                        action(DrawerDestination.About)
                    }
                }
            }
        },
        content = {
            CompositionLocalProvider(LocalNavigationVisible provides navigationVisible) {
                content()
            }
        },
    )
    if (selectingServer)
        ModalBottomSheet(onDismissRequest = { selectingServer = false }) {
            Text(
                stringResource(R.string.sphere_select_server),
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(boorus, key = { it.uid }) { server ->
                    ListItem(
                        headlineContent = { Text(server.name) },
                        leadingContent = {
                            AsyncImage(
                                "${server.scheme}://${server.host}/favicon.ico",
                                null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            RadioButton(selected = selectedUid == server.uid, onClick = null)
                        },
                        modifier =
                            Modifier.clickable {
                                onBooru(server.uid)
                                selectingServer = false
                            },
                    )
                }
                item {
                    SphereDrawerRow(
                        R.string.sphere_add_server,
                        R.drawable.ic_add_circle_outline_24dp,
                    ) {
                        selectingServer = false
                        action(DrawerDestination.Sites)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    if (exit)
        AlertDialog(
            onDismissRequest = { exit = false },
            title = { Text(stringResource(R.string.dialog_exit)) },
            text = { Text(stringResource(R.string.sphere_exit_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exit = false
                        action(DrawerDestination.Exit)
                    }
                ) {
                    Text(stringResource(R.string.dialog_exit))
                }
            },
            dismissButton = {
                TextButton(onClick = { exit = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
}

@Composable
@Suppress("FunctionNaming")
private fun SphereDrawerRow(title: Int, icon: Int, summary: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, Modifier.size(22.dp))
        Text(
            stringResource(title) + (summary?.let { " $it" } ?: ""),
            Modifier.padding(start = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val COMPACT_DRAWER_HEIGHT_DP = 640
private const val COMPACT_DRAWER_FONT_SCALE = 1.3f

val LocalNavigationVisible = compositionLocalOf { true }

@Composable
@Suppress("FunctionNaming")
private fun ThemeButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(
                if (isSystemInDarkTheme()) R.drawable.ic_brightness_2_outline_24dp
                else R.drawable.ic_light_mode_outline_24dp
            ),
            stringResource(R.string.sphere_theme),
        )
    }
}

private fun rule34WebTools(): List<BrowseTab> = buildList {
    add(
        BrowseTab(
            R.id.nav_pools,
            R.string.title_pools_website,
            R.drawable.ic_photo_album_outline_24dp,
            R.drawable.ic_photo_album_24dp,
            "https://rule34.xxx/index.php?page=pool&s=list",
        )
    )
    add(
        BrowseTab(
            R.id.nav_artists,
            R.string.title_artists_website,
            R.drawable.ic_people_outline_24dp,
            R.drawable.ic_people_24dp,
            "https://rule34.xxx/index.php?page=artist&s=list",
        )
    )
}
