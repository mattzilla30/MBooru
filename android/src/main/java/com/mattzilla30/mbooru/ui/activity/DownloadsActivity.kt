package com.mattzilla30.mbooru.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            Column(Modifier.fillMaxSize()) {
                Text(
                    stringResource(R.string.download_resume_policy),
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (error)
                    Text(
                        stringResource(R.string.msg_download_failed),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                if (entries.isEmpty())
                    Text(stringResource(R.string.download_queue_empty), Modifier.padding(16.dp))
                LazyColumn(Modifier.weight(1f)) {
                    items(entries, key = { it.workId }) { item ->
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(
                                    when (item.state) {
                                        "running" -> R.string.download_state_running
                                        "completed" -> R.string.download_state_completed
                                        "failed" -> R.string.download_state_failed
                                        "cancelled" -> R.string.download_state_cancelled
                                        else -> R.string.download_state_queued
                                    }
                                )
                            )
                            if (item.state == "running") {
                                if (item.totalBytes > 0)
                                    LinearProgressIndicator(
                                        progress = {
                                            (item.bytesWritten.toFloat() / item.totalBytes)
                                                .coerceIn(0f, 1f)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                else LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                            Text(
                                if (item.totalBytes < 0)
                                    stringResource(
                                        R.string.download_bytes_unknown,
                                        item.bytesWritten,
                                    )
                                else
                                    stringResource(
                                        R.string.download_bytes_total,
                                        item.bytesWritten,
                                        item.totalBytes,
                                    ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                when (item.state) {
                                    "queued",
                                    "running" ->
                                        TextButton(onClick = { model.cancel(item) }) {
                                            Text(stringResource(R.string.dialog_cancel))
                                        }
                                    "failed",
                                    "cancelled" ->
                                        TextButton(onClick = { model.retry(item) }) {
                                            Text(stringResource(R.string.action_retry))
                                        }
                                    "completed" ->
                                        TextButton(onClick = { open(item) }) {
                                            Text(stringResource(R.string.download_open_file))
                                        }
                                }
                                if (item.state in listOf("failed", "cancelled", "completed"))
                                    TextButton(onClick = { model.remove(item) }) {
                                        Text(stringResource(R.string.booru_delete))
                                    }
                            }
                        }
                        HorizontalDivider()
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
