package com.mattzilla30.mbooru.ui.compose.comments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Comment
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.compose.common.RowMenu
import com.mattzilla30.mbooru.widget.CommentView

@Composable
fun CommentRow(
    comment: Comment,
    booru: Booru,
    date: String,
    onPost: () -> Unit,
    onUser: () -> Unit,
    onReply: () -> Unit,
    onQuote: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val body = remember(comment.id) { arrayOfNulls<CommentView>(1) }
    Column(Modifier.fillMaxWidth().clickable(onClick = onPost)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatar =
                if (booru.type == Values.BOORU_TYPE_MOE)
                    stringResource(
                        R.string.account_user_avatars,
                        booru.scheme,
                        booru.host,
                        comment.creatorId,
                    )
                else comment.creatorAvatar
            AsyncImage(
                avatar,
                comment.creatorName,
                placeholder = painterResource(R.drawable.avatar_account),
                error = painterResource(R.drawable.avatar_account),
                modifier = Modifier.size(48.dp).clickable(onClick = onUser),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(comment.creatorName, style = MaterialTheme.typography.bodyLarge)
                Text("Post ${comment.postId}", style = MaterialTheme.typography.bodySmall)
                Text(date, style = MaterialTheme.typography.bodySmall)
            }
            val user = booru.user
            if (
                user != null &&
                    booru.type !in arrayOf(Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)
            ) {
                RowMenu(
                    if (user.id == comment.creatorId) AppMenu.CommentItemMe else AppMenu.CommentItem
                ) { action ->
                    when (action) {
                        AppActionKey.CommentReply -> onReply()
                        AppActionKey.CommentQuote ->
                            onQuote(
                                "[quote]${comment.creatorName} said:\r\n${body[0]?.getLastCommentText() ?: comment.body}[/quote]"
                            )
                        AppActionKey.CommentDelete -> onDelete()
                        else -> Unit
                    }
                }
            }
        }
        // Preserve selectable rich links and nested quote rendering in the existing specialized
        // view.
        AndroidView(
            factory = { context ->
                CommentView(context).also {
                    it.orientation = android.widget.LinearLayout.VERTICAL
                    body[0] = it
                }
            },
            modifier = Modifier.fillMaxWidth(),
            onReset = {
                it.setComment("")
                it.tag = null
            },
            onRelease = { body[0] = null },
            update = { view ->
                body[0] = view
                if (view.tag != comment.body) {
                    view.tag = comment.body
                    view.setComment(comment.body)
                }
            },
        )
        HorizontalDivider()
    }
}
