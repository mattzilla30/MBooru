package com.mattzilla30.mbooru.ui.compose.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.ui.compose.browse.postKey
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.compose.common.RowMenu
import com.mattzilla30.mbooru.ui.viewmodel.DetailUiState
import com.mattzilla30.mbooru.widget.DismissFrameLayout

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DetailScreen(
    posts: LazyPagingItems<Post>,
    state: DetailUiState,
    booru: Booru,
    size: String,
    dismiss: DismissFrameLayout.OnDismissListener,
    select: (Post, Int) -> Unit,
    ready: () -> Unit,
    back: () -> Unit,
    toggleBars: () -> Unit,
    longActions: () -> Unit,
    closeLongActions: () -> Unit,
    action: (AppActionKey) -> Unit,
    info: () -> Unit,
    download: () -> Unit,
    save: () -> Unit,
    favorite: () -> Unit,
    longAction: (Int) -> Unit,
) {
    var current by remember { mutableStateOf<Post?>(null) }
    val zoomed = remember { mutableStateMapOf<Int, Boolean>() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = state.backgroundAlpha))) {
        if (posts.itemCount > 0) {
            val pager =
                rememberPagerState(
                    initialPage = state.currentPosition.coerceIn(0, posts.itemCount - 1),
                    pageCount = { posts.itemCount },
                )
            val post = posts[pager.currentPage.coerceIn(0, posts.itemCount - 1)]
            LaunchedEffect(pager.currentPage, post) {
                current = post
                if (post != null) select(post, pager.currentPage)
            }
            LaunchedEffect(Unit) {
                withFrameNanos {}
                ready()
            }
            HorizontalPager(
                state = pager,
                key = posts.itemKey(::postKey),
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = zoomed[pager.currentPage] != true,
            ) { page ->
                val item = posts[page]
                if (item != null)
                    PostMedia(
                        item,
                        booru,
                        size,
                        active =
                            page == pager.currentPage &&
                                !pager.isScrollInProgress &&
                                !state.dismissing,
                        dismiss = dismiss,
                        tap = toggleBars,
                        hold = longActions,
                        zoom = { zoomed[page] = it },
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        } else CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        AnimatedVisibility(state.barsVisible, modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .7f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .statusBarsPadding()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = back) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        tint = Color.White,
                    )
                }
                Text(
                    current?.let { "Post ${it.id}" }.orEmpty(),
                    Modifier.weight(1f),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                RowMenu(
                    if (booru.type == Values.BOORU_TYPE_SHIMMIE) AppMenu.DetailShimmie
                    else AppMenu.Detail,
                    iconTint = Color.White,
                ) {
                    action(it)
                }
            }
        }
        AnimatedVisibility(state.barsVisible, modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = .8f))
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .navigationBarsPadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = info) {
                        Icon(
                            painterResource(R.drawable.ic_info_outline_24dp),
                            stringResource(R.string.browse_info),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = download) {
                        Icon(
                            painterResource(R.drawable.ic_file_download_24dp),
                            stringResource(R.string.browse_download),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = save) {
                        Icon(
                            painterResource(R.drawable.ic_save_outline_24dp),
                            stringResource(R.string.browse_save_file),
                            tint = Color.White,
                        )
                    }
                    val favored = current?.let { state.favorites[it.id] ?: it.isFavored } == true
                    IconButton(onClick = favorite) {
                        Icon(
                            painterResource(
                                if (favored) R.drawable.ic_star_24dp
                                else R.drawable.ic_star_border_24dp
                            ),
                            stringResource(R.string.browse_add_to_fav),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
    if (state.longActions)
        AlertDialog(
            onDismissRequest = closeLongActions,
            title = { Text(current?.let { "Post ${it.id}" }.orEmpty()) },
            text = {
                Column {
                    stringArrayResource(R.array.detail_item_action).forEachIndexed { index, label ->
                        TextButton(
                            onClick = {
                                closeLongActions()
                                longAction(index)
                            }
                        ) {
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {},
        )
}
