package com.mattzilla30.mbooru.ui.compose.browse

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.dispose
import coil3.load
import coil3.request.*
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.extension.isStillImage
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

fun postKey(post: Post) = "${post.booruUid}:${post.query}:${post.id}"

fun postAspectRatio(post: Post, fixed: String?): Float {
    if (fixed != null) {
        val parts = fixed.split(':').mapNotNull { it.toFloatOrNull() }
        if (parts.size == 2 && parts[0] > 0 && parts[1] > 0) return parts[0] / parts[1]
    }
    return (post.width.coerceAtLeast(1).toFloat() / post.height.coerceAtLeast(1)).coerceIn(
        9f / 21f,
        21f / 9f,
    )
}

data class PostDisplayOptions(
    val rounded: Boolean,
    val ratio: String?,
    val info: Boolean,
    val large: Boolean,
    val itemWidthDp: Float,
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun PostGrid(
    posts: LazyPagingItems<Post>,
    scrollPositions: Flow<Int>,
    options: PostDisplayOptions,
    onClick: (Post, Int, View) -> Unit,
    onLongClick: (Post) -> Unit,
    modifier: Modifier = Modifier,
    onImage: (Int, View?) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val actionsLabel = stringResource(R.string.post_actions)
    val grid = rememberLazyStaggeredGridState()
    ObserveBrowseScroll { grid.firstVisibleItemIndex to grid.firstVisibleItemScrollOffset }
    val rounded = options.rounded
    val ratio = options.ratio
    val info = options.info
    val large = options.large
    val width = options.itemWidthDp.coerceAtLeast(1f).dp
    val scope = rememberCoroutineScope()
    val refresh = posts.loadState.refresh is LoadState.Loading
    val pull = rememberPullRefreshState(refresh, posts::refresh)
    LaunchedEffect(scrollPositions) {
        scrollPositions.collect { position ->
            if (posts.itemCount > 0) grid.scrollToItem(position.coerceAtMost(posts.itemCount - 1))
        }
    }
    BoxWithConstraints(modifier.pullRefresh(pull)) {
        val columns = (maxWidth / width).roundToInt().coerceAtLeast(1)
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = grid,
            modifier = Modifier.fillMaxSize().testTag("post_grid"),
            contentPadding = PaddingValues(if (rounded) 4.5.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(if (rounded) 9.dp else 0.dp),
            verticalItemSpacing = if (rounded) 9.dp else 0.dp,
        ) {
            items(
                count = posts.itemCount,
                key = posts.itemKey(::postKey),
                contentType = { "post" },
            ) { index ->
                val post = posts[index]
                if (post != null) {
                    val image = remember(postKey(post)) { arrayOfNulls<AppCompatImageView>(1) }
                    Column(
                        Modifier.clip(RoundedCornerShape(if (rounded) 8.dp else 0.dp))
                            .semantics(mergeDescendants = true) {
                                contentDescription =
                                    context.getString(R.string.post_accessibility, post.id)
                            }
                            .combinedClickable(
                                onLongClickLabel = actionsLabel,
                                onClick = { image[0]?.let { onClick(post, index, it) } },
                                onLongClick = { onLongClick(post) },
                            )
                    ) {
                        // Keep a platform image only for the existing activity shared-element
                        // transition.
                        // Paging, sizing, layout and interaction are owned by Compose.
                        val url =
                            if (large && post.sample.isStillImage()) post.sample else post.preview
                        AndroidView(
                            factory = { context ->
                                AppCompatImageView(context).also { image[0] = it }
                            },
                            modifier =
                                Modifier.fillMaxWidth().aspectRatio(postAspectRatio(post, ratio)),
                            onReset = {
                                it.dispose()
                                it.tag = null
                            },
                            onRelease = {
                                it.dispose()
                                image[0] = null
                                onImage(index, null)
                            },
                            update = { view ->
                                image[0] = view
                                onImage(index, view)
                                view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                view.transitionName = "post_${post.id}"
                                view.scaleType =
                                    if (ratio != null)
                                        android.widget.ImageView.ScaleType.CENTER_CROP
                                    else android.widget.ImageView.ScaleType.FIT_CENTER
                                if (view.tag != url) {
                                    view.tag = url
                                    view.load(url) {
                                        placeholder(
                                            when (post.rating) {
                                                "s" -> R.drawable.background_rating_s
                                                "q" -> R.drawable.background_rating_q
                                                "g" -> R.drawable.background_rating_g
                                                else -> R.drawable.background_rating_e
                                            }
                                        )
                                    }
                                }
                            },
                        )
                        if (info)
                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text("#${post.id}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${post.width} x ${post.height}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                    }
                }
            }
            item(key = "append", span = StaggeredGridItemSpan.FullLine) {
                PagingStatus(posts.loadState.append, posts::retry)
            }
        }
        if (posts.itemCount == 0)
            PagingStatus(
                posts.loadState.refresh,
                posts::retry,
                Modifier.align(Alignment.Center),
                empty = true,
            )
        BrowseScrollTop(
            grid.firstVisibleItemIndex > 0,
            { scope.launch { grid.animateScrollToItem(0) } },
            Modifier.align(Alignment.BottomEnd),
        )
        PullRefreshIndicator(
            refresh,
            pull,
            Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
fun PagingStatus(
    state: LoadState,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
    empty: Boolean = false,
) {
    if (state is LoadState.NotLoading && !empty) return
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Error -> {
                Text(
                    stringResource(R.string.msg_cloudflare_challenges),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = retry) { Text(stringResource(R.string.action_retry)) }
            }
            is LoadState.NotLoading -> Text(stringResource(R.string.browse_empty))
        }
    }
}
