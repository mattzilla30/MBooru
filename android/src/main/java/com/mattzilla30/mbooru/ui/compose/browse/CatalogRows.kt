package com.mattzilla30.mbooru.ui.compose.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SANKAKU
import com.mattzilla30.mbooru.app.Values.Tags
import com.mattzilla30.mbooru.data.model.common.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun <T : Any> PagingList(
    items: LazyPagingItems<T>,
    key: (T) -> Any,
    positions: Flow<Int>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    val list = rememberLazyListState()
    ObserveBrowseScroll { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
    val scope = rememberCoroutineScope()
    val refreshing = items.loadState.refresh is LoadState.Loading
    val pull = rememberPullRefreshState(refreshing, items::refresh)
    LaunchedEffect(positions) {
        positions.collect {
            if (items.itemCount > 0) list.scrollToItem(it.coerceAtMost(items.itemCount - 1))
        }
    }
    Box(modifier.pullRefresh(pull)) {
        LazyColumn(Modifier.fillMaxSize(), state = list) {
            items(count = items.itemCount, key = items.itemKey(key)) { index ->
                items[index]?.let { row(it) }
            }
            item(key = "append") { PagingStatus(items.loadState.append, items::retry) }
        }
        if (items.itemCount == 0)
            PagingStatus(
                items.loadState.refresh,
                items::retry,
                Modifier.align(Alignment.Center),
                empty = true,
            )
        BrowseScrollTop(
            list.firstVisibleItemIndex > 0,
            { scope.launch { list.animateScrollToItem(0) } },
            Modifier.align(Alignment.BottomEnd),
        )
        PullRefreshIndicator(
            refreshing,
            pull,
            Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistRow(artist: Artist, search: () -> Unit, copy: () -> Unit, link: (String) -> Unit) {
    var expanded by rememberSaveable(artist.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .combinedClickable(onClick = search, onLongClick = copy)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(artist.name, style = MaterialTheme.typography.bodyLarge)
                Text("#${artist.id}", style = MaterialTheme.typography.bodySmall)
            }
            if (!artist.urls.isNullOrEmpty())
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more_24dp),
                        stringResource(R.string.settings_expand_button_title),
                    )
                }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                artist.urls.orEmpty().reversed().forEach { url ->
                    Text(
                        url,
                        Modifier.heightIn(min = 48.dp)
                            .clickable { link(url) }
                            .padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

fun tagCategoryLabel(tag: Tag): Int =
    when (tag.category) {
        Tags.TYPE_GENERAL -> R.string.tag_type_general
        Tags.TYPE_ARTIST -> R.string.tag_type_artist
        Tags.TYPE_COPYRIGHT -> R.string.tag_type_copyright
        Tags.TYPE_CHARACTER -> R.string.tag_type_character
        2 ->
            if (tag.booruType == BOORU_TYPE_SANKAKU) R.string.tag_type_studio
            else R.string.tag_type_unknown
        5 ->
            when (tag.booruType) {
                BOORU_TYPE_MOE -> R.string.tag_type_circle
                BOORU_TYPE_DAN1 -> R.string.tag_type_model
                BOORU_TYPE_SANKAKU -> R.string.tag_type_genre
                else -> R.string.tag_type_meta
            }
        6 ->
            when (tag.booruType) {
                BOORU_TYPE_MOE -> R.string.tag_type_faults
                BOORU_TYPE_DAN1 -> R.string.tag_type_photo_set
                else -> R.string.tag_type_unknown
            }
        8 ->
            if (tag.booruType == BOORU_TYPE_SANKAKU) R.string.tag_type_medium
            else R.string.tag_type_unknown
        9 ->
            if (tag.booruType == BOORU_TYPE_SANKAKU) R.string.tag_type_meta
            else R.string.tag_type_unknown
        else -> R.string.tag_type_unknown
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagRow(tag: Tag, search: () -> Unit, copy: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 60.dp)
            .combinedClickable(onClick = search, onLongClick = copy)
            .padding(horizontal = 15.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tag.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(stringResource(tagCategoryLabel(tag)), style = MaterialTheme.typography.bodySmall)
            Text(tag.count.toString(), style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoolRow(pool: Pool, date: String, search: () -> Unit, download: () -> Unit, user: () -> Unit) {
    var expanded by rememberSaveable(pool.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = search, onLongClick = download)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatar =
                if (pool.booruType == BOORU_TYPE_MOE)
                    stringResource(
                        R.string.account_user_avatars,
                        pool.scheme,
                        pool.host,
                        pool.creatorId,
                    )
                else pool.creatorAvatar
            AsyncImage(
                avatar,
                pool.creatorName,
                placeholder = painterResource(R.drawable.avatar_account),
                error = painterResource(R.drawable.avatar_account),
                modifier = Modifier.size(48.dp).clickable(onClick = user),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(pool.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.pool_info_id_and_count, pool.id, pool.count),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
            if (pool.description.isNotBlank())
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more_24dp),
                        stringResource(R.string.settings_expand_button_title),
                    )
                }
        }
        AnimatedVisibility(expanded) {
            WebDescription(pool.description)
        }
        HorizontalDivider()
    }
}

/** Keep the previous pool description's selectable text and automatic web links. */
@Composable
private fun WebDescription(text: String) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated =
        remember(text, linkColor) {
            val linked = android.text.SpannableString(text)
            android.text.util.Linkify.addLinks(linked, android.text.util.Linkify.WEB_URLS)
            androidx.compose.ui.text.buildAnnotatedString {
                append(text)
                linked.getSpans(0, linked.length, android.text.style.URLSpan::class.java).forEach {
                    span ->
                    val start = linked.getSpanStart(span)
                    val end = linked.getSpanEnd(span)
                    addStringAnnotation("url", span.url, start, end)
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(
                            color = linkColor,
                            textDecoration =
                                androidx.compose.ui.text.style.TextDecoration.Underline,
                        ),
                        start,
                        end,
                    )
                }
            }
        }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    SelectionContainer {
        androidx.compose.foundation.text.ClickableText(
            annotated,
            Modifier.padding(16.dp),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
            onClick = { position ->
                annotated.getStringAnnotations("url", position, position).firstOrNull()?.let {
                    uri.openUri(it.item)
                }
            },
        )
    }
}
