package com.mattzilla30.mbooru.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.database.dao.SavedPostDao
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.browse.PostDisplayOptions
import com.mattzilla30.mbooru.ui.compose.browse.PostGrid
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class FavoritesActivity : BaseActivity() {
    private val favorites by inject<SavedPostDao>()
    private val database by inject<MyDatabase>()

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.sphere_favorites)
        setComposeContent {
            val entries by favorites.observe().collectAsStateWithLifecycle(emptyList())
            val data =
                remember(entries) { flowOf(PagingData.from(entries)) }.collectAsLazyPagingItems()
            if (entries.isEmpty())
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.sphere_empty_favorites),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        stringResource(R.string.sphere_empty_favorites_hint),
                        Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            else
                PostGrid(
                    data,
                    emptyFlow(),
                    PostDisplayOptions(true, null, false, false, FAVORITES_ITEM_WIDTH_DP),
                    onClick = { post, _, _ ->
                        lifecycleScope.launch {
                            // A durable favorite snapshot can be opened even after timeline cache
                            // cleanup.
                            val query = "mbooru:local-favorite:${post.id}"
                            database.postDao().insert(post.copy(uid = 0, query = query, index = 0))
                            settings.activatedBooruUid = post.booruUid
                            startActivity(
                                Intent(this@FavoritesActivity, DetailActivity::class.java)
                                    .putExtra(Keys.POST_QUERY, query)
                                    .putExtra(Keys.POST_POSITION, 0)
                            )
                        }
                    },
                    onLongClick = { post ->
                        lifecycleScope.launch { favorites.delete(post.booruUid, post.id) }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

private const val FAVORITES_ITEM_WIDTH_DP = 160f
