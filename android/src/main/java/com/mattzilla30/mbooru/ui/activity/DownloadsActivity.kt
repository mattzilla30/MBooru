package com.mattzilla30.mbooru.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import com.mattzilla30.mbooru.data.repository.download.DownloadQueueRepository
import com.mattzilla30.mbooru.extension.getFileUriByDocId
import com.mattzilla30.mbooru.extension.getMimeType
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class DownloadsViewModel(private val repository: DownloadQueueRepository) : ViewModel() {
    val entries =
        repository.entries.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList(),
        )
    private val mutableError = MutableStateFlow(false)
    val error = mutableError.asStateFlow()

    fun cancel(record: DownloadRecord) = act { repository.cancel(record) }

    fun remove(record: DownloadRecord) = act { repository.remove(record) }

    fun retry(record: DownloadRecord) = act { repository.retry(record) }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutableError.value = false
            try {
                block()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableError.value = true
            }
        }
    }
}

class DownloadsActivity : BaseActivity() {
    private val repository by inject<DownloadQueueRepository>()
    private val model by
        viewModels<DownloadsViewModel> {
            viewModelFactory { initializer { DownloadsViewModel(repository) } }
        }

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        setScreenTitle(R.string.title_downloads)
        setComposeContent {
            val entries by model.entries.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            var filter by rememberSaveable { mutableStateOf("all") }
            val shown = entries.filter { filter == "all" || it.state == filter }
            Column(Modifier.fillMaxSize()) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(listOf("all", "running", "completed", "failed")) { value ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { filter = value },
                            label = {
                                Text(
                                    stringResource(
                                        when (value) {
                                            "running" -> R.string.download_state_running
                                            "completed" -> R.string.download_state_completed
                                            "failed" -> R.string.download_state_failed
                                            else -> R.string.sphere_all_downloads
                                        }
                                    )
                                )
                            },
                        )
                    }
                }
                if (error)
                    Text(
                        stringResource(R.string.msg_download_failed),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                if (shown.isEmpty())
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.download_queue_empty), Modifier.padding(24.dp))
                    }
                else
                    LazyColumn(Modifier.weight(1f)) {
                        items(shown, key = { it.workId }) { item ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape =
                                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier.size(64.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painterResource(R.drawable.ic_file_download_24dp),
                                            null,
                                        )
                                    }
                                }
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    if (item.error == "size_limit")
                                        Text(
                                            "Download exceeds the configured size limit. " +
                                                "Raise it in Settings before retrying.",
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                    )
                                    Text(
                                        stringResource(
                                            when (item.state) {
                                                "running" -> R.string.download_state_running
                                                "completed" -> R.string.download_state_completed
                                                "failed" -> R.string.download_state_failed
                                                "cancelled" -> R.string.download_state_cancelled
                                                else -> R.string.download_state_queued
                                            }
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (item.state == "running") {
                                        if (item.totalBytes > 0)
                                            LinearProgressIndicator(
                                                progress = {
                                                    (item.bytesWritten.toFloat() / item.totalBytes)
                                                        .coerceIn(0f, 1f)
                                                },
                                                modifier =
                                                    Modifier.fillMaxWidth().padding(top = 8.dp),
                                            )
                                        else
                                            LinearProgressIndicator(
                                                Modifier.fillMaxWidth().padding(top = 8.dp)
                                            )
                                    }
                                }
                                var menu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { menu = true }) {
                                        Icon(
                                            painterResource(R.drawable.ic_more_vert_24dp),
                                            stringResource(
                                                androidx.appcompat.R.string
                                                    .abc_action_menu_overflow_description
                                            ),
                                        )
                                    }
                                    DropdownMenu(menu, { menu = false }) {
                                        if (item.state in listOf("queued", "running"))
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.dialog_cancel))
                                                },
                                                onClick = {
                                                    menu = false
                                                    model.cancel(item)
                                                },
                                            )
                                        if (item.state in listOf("failed", "cancelled"))
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.action_retry))
                                                },
                                                onClick = {
                                                    menu = false
                                                    model.retry(item)
                                                },
                                            )
                                        if (item.state == "completed")
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.download_open_file)
                                                    )
                                                },
                                                onClick = {
                                                    menu = false
                                                    open(item)
                                                },
                                            )
                                        if (
                                            item.state in listOf("completed", "failed", "cancelled")
                                        )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.booru_delete))
                                                },
                                                onClick = {
                                                    menu = false
                                                    model.remove(item)
                                                },
                                            )
                                    }
                                }
                            }
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
            }
        }
    }

    private fun open(item: DownloadRecord) {
        val uri = contentResolver.getFileUriByDocId(item.documentId) ?: return
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(
                        uri,
                        androidx.work.Data.fromByteArray(item.request)
                            .getString("final_name")
                            ?.getMimeType()
                            .orEmpty()
                            .ifBlank { "*/*" },
                    )
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                    this,
                    R.string.msg_download_failed,
                    android.widget.Toast.LENGTH_LONG,
                )
                .show()
        }
    }
}
