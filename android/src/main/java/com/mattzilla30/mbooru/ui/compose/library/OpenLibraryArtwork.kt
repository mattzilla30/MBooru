// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.library

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import com.mattzilla30.mbooru.app.Keys
import com.mattzilla30.mbooru.core.model.ArtworkRating
import com.mattzilla30.mbooru.core.model.normalizedRating
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.ui.activity.DetailActivity
import com.mattzilla30.mbooru.ui.base.BaseActivity
import kotlinx.coroutines.launch

fun openLibraryArtwork(host: BaseActivity, database: MyDatabase, post: Post) {
    if (host.settings.safeMode && normalizedRating(post.rating) != ArtworkRating.SAFE) return
    if (post.origin.isBlank() && post.preview.isBlank()) {
        androidx.appcompat.app.AlertDialog.Builder(host)
            .setTitle("Media unavailable")
            .setMessage(
                "This imported artwork has no usable media URL. Its local identity and " +
                    "collection memberships are retained. Reconnect its source site and search " +
                    "for post #${post.id} to recover metadata."
            )
            .setPositiveButton(android.R.string.ok, null)
            .show()
        return
    }
    host.lifecycleScope.launch {
        val query = "mbooru:library:${post.id}"
        database.postDao().insert(post.copy(uid = 0, query = query, index = 0))
        host.startActivity(
            Intent(host, DetailActivity::class.java)
                .putExtra("source_site_uid", post.booruUid)
                .putExtra(Keys.POST_QUERY, query)
                .putExtra(Keys.POST_POSITION, 0)
        )
    }
}
