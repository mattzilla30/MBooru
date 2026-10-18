package com.mattzilla30.mbooru.ui.compose.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R

@Composable
fun <T> ImageSearchScreen(results: List<T>, loading: Boolean, error: String?,
    add: () -> Unit, retry: () -> Unit = add, key: (T, Int) -> String, row: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
            item { if (loading) LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                if (!error.isNullOrBlank() && !loading) Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.msg_cloudflare_challenges), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = retry) { Text(stringResource(R.string.action_retry)) }
                } else if (!loading && results.isEmpty()) Text(stringResource(R.string.browse_empty), Modifier.padding(16.dp))
            }
            itemsIndexed(results, key = { index, item -> key(item, index) }) { _, item -> row(item) }
        }
        FloatingActionButton(onClick = add, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Default.Search, stringResource(R.string.title_search))
        }
    }
}
