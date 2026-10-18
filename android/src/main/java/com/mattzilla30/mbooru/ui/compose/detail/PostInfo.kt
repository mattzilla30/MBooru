package com.mattzilla30.mbooru.ui.compose.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.TagBase

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostMetadata(
    post: Post,
    booru: Booru,
    date: String,
    originalSize: String,
    onUser: () -> Unit,
    onSource: () -> Unit,
    onCopy: (String) -> Unit,
    onDownload: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onUser).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val avatar =
                    if (booru.type == Values.BOORU_TYPE_MOE && post.uploader.id > 0)
                        stringResource(
                            R.string.account_user_avatars,
                            booru.scheme,
                            booru.host,
                            post.uploader.id,
                        )
                    else post.uploader.avatar
                AsyncImage(
                    avatar,
                    null,
                    placeholder = painterResource(R.drawable.avatar_account),
                    error = painterResource(R.drawable.avatar_account),
                    modifier = Modifier.size(48.dp),
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(post.uploader.name, style = MaterialTheme.typography.bodyLarge)
                    Text(post.uploader.id.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = onSource,
                        onLongClick = { onCopy(post.source.orEmpty()) },
                    )
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    stringResource(R.string.browse_info_source),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(post.source.orEmpty(), color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            val rating =
                when (post.rating) {
                    "s" ->
                        if (booru.type == Values.BOORU_TYPE_DAN)
                            R.string.browse_info_rating_sensitive
                        else R.string.browse_info_rating_safe
                    "q" -> R.string.browse_info_rating_questionable
                    "g" -> R.string.browse_info_rating_general
                    else -> R.string.browse_info_rating_explicit
                }
            Text(
                "${stringResource(R.string.browse_info_rating)}: ${stringResource(rating)}",
                Modifier.padding(vertical = 8.dp),
            )
            Text(
                "${stringResource(R.string.browse_info_score)}: ${post.score}",
                Modifier.padding(vertical = 8.dp),
            )
            Text(date, Modifier.padding(vertical = 8.dp))
        }
        listOf(
                R.string.settings_post_size_sample to post.sample,
                R.string.settings_post_size_larger to post.medium,
                R.string.settings_post_size_origin to post.origin,
            )
            .forEachIndexed { index, (title, url) ->
                item(key = title) {
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = {}, onLongClick = { onCopy(url) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (index == 2) originalSize else "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        IconButton(onClick = { onDownload(url) }) {
                            Icon(
                                painterResource(R.drawable.ic_file_download_24dp),
                                stringResource(R.string.browse_info_url_download),
                            )
                        }
                        IconButton(onClick = { onOpen(url) }) {
                            Icon(
                                painterResource(R.drawable.ic_open_in_browser_24dp),
                                stringResource(R.string.browse_info_url_open),
                            )
                        }
                    }
                }
            }
    }
}

fun tagDotColor(type: Int, category: Int): Int =
    when (category) {
        0 -> R.color.tag_type_general
        1 -> R.color.tag_type_artist
        3 -> R.color.tag_type_copyright
        4 -> R.color.tag_type_character
        5 ->
            when (type) {
                Values.BOORU_TYPE_MOE -> R.color.tag_type_circle
                Values.BOORU_TYPE_DAN -> R.color.tag_type_meta
                Values.BOORU_TYPE_SANKAKU -> R.color.tag_type_genre
                else -> R.color.tag_type_unknown
            }
        6 ->
            if (type == Values.BOORU_TYPE_MOE) R.color.tag_type_faults else R.color.tag_type_unknown
        2 ->
            if (type == Values.BOORU_TYPE_SANKAKU) R.color.tag_type_studio
            else R.color.tag_type_unknown
        8 ->
            if (type == Values.BOORU_TYPE_SANKAKU) R.color.tag_type_medium
            else R.color.tag_type_unknown
        9 ->
            if (type == Values.BOORU_TYPE_SANKAKU) R.color.tag_type_meta
            else R.color.tag_type_unknown
        else -> R.color.tag_type_unknown
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PostTags(
    post: Post,
    booru: Booru,
    search: (String) -> Unit,
    copy: (String) -> Unit,
    include: (TagBase, Boolean) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(post.tags.distinctBy { it.name }, key = { it.name }) { tag ->
            Row(
                Modifier.fillMaxWidth()
                    .combinedClickable(
                        onClick = { search(tag.name) },
                        onLongClick = { copy(tag.name) },
                    )
                    .padding(start = 16.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(8.dp)
                        .background(
                            colorResource(tagDotColor(booru.type, tag.category)),
                            CircleShape,
                        )
                )
                Text(
                    tag.name,
                    Modifier.weight(1f).padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                IconButton(onClick = { include(tag, false) }) {
                    Icon(
                        painterResource(R.drawable.ic_remove_circle_outline_24dp),
                        stringResource(R.string.browse_tag_exclude),
                    )
                }
                IconButton(onClick = { include(tag, true) }) {
                    Icon(
                        painterResource(R.drawable.ic_add_circle_outline_24dp),
                        stringResource(R.string.browse_tag_include),
                    )
                }
            }
        }
    }
}
