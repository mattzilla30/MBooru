// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.mattzilla30.mbooru.core.model.BooruEngine
import com.mattzilla30.mbooru.data.action.ActionVote
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.repository.favorite.VoteRepository
import com.mattzilla30.mbooru.extension.NetResult
import com.mattzilla30.mbooru.extension.safeNetworkMessage
import kotlinx.coroutines.launch

/** The only library control that deliberately writes to the selected remote account. */
@Composable
@Suppress(
    "FunctionNaming",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "TooGenericExceptionCaught",
)
fun RemoteFavoriteAction(post: Post, site: Booru, repository: VoteRepository) {
    if (!BooruEngine.fromLegacyId(site.type).capabilities.favoriteWrites) return
    var favored by remember(post.booruUid, post.id) { mutableStateOf(post.isFavored) }
    var busy by remember(post.booruUid, post.id) { mutableStateOf(false) }
    var message by remember(post.booruUid, post.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    TextButton(
        enabled = !busy && site.user != null,
        onClick = {
            busy = true
            message = ""
            scope.launch {
                try {
                    val action = ActionVote(site, post.id)
                    when (
                        val result =
                            if (favored) repository.removeFav(action) else repository.addFav(action)
                    ) {
                        is NetResult.Success ->
                            if (result.data) favored = !favored
                            else message = "The server did not confirm the favorite change."
                        is NetResult.Error -> message = result.errorMsg
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    message = failure.safeNetworkMessage()
                } finally {
                    busy = false
                }
            }
        },
    ) {
        Text(
            if (site.user == null) "Connect an account for remote favorites"
            else if (favored) "Remove remote favorite" else "Favorite on ${site.name}"
        )
    }
    if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.error)
}
