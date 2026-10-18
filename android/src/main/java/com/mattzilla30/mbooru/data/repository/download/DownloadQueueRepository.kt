package com.mattzilla30.mbooru.data.repository.download

import android.content.Context
import androidx.work.*
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import com.mattzilla30.mbooru.extension.getFileUriByDocId
import com.mattzilla30.mbooru.worker.DownloadWorker
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class DownloadQueueRepository(
    private val context: Context,
    private val dao: DownloadDao,
    private val vault: com.mattzilla30.mbooru.data.credentials.CredentialRepository,
) {
    private val manager = WorkManager.getInstance(context)
    val entries =
        combine(dao.observeAll(), manager.getWorkInfosByTagFlow(DownloadWorker.DOWNLOAD_TAG)) {
            records,
            work ->
            val states = work.associateBy { it.id.toString() }
            records
                .filter { it.state != "replaced" }
                .map { record ->
                    val state =
                        when (states[record.workId]?.state) {
                            WorkInfo.State.ENQUEUED,
                            WorkInfo.State.BLOCKED -> "queued"
                            WorkInfo.State.RUNNING -> "running"
                            WorkInfo.State.SUCCEEDED -> "completed"
                            WorkInfo.State.FAILED -> "failed"
                            WorkInfo.State.CANCELLED -> "cancelled"
                            null -> record.state
                        }
                    record.copy(state = state)
                }
        }

    suspend fun cancel(record: DownloadRecord) =
        withContext(Dispatchers.IO) {
            manager.cancelWorkById(UUID.fromString(record.workId)).result.get()
            dao.state(record.workId, "cancelled", "interrupted")
        }

    suspend fun remove(record: DownloadRecord) =
        withContext(Dispatchers.IO) {
            val request = Data.fromByteArray(record.request)
            // Only app-owned staging files may be deleted; legacy final files remain user-owned.
            if (record.state != "completed" && request.getString("final_name") != null) {
                val uri = context.contentResolver.getFileUriByDocId(record.documentId)
                if (
                    uri != null &&
                        !android.provider.DocumentsContract.deleteDocument(
                            context.contentResolver,
                            uri,
                        )
                )
                    throw java.io.IOException("Could not discard partial file")
            }
            request.getString("url")?.takeIf(vault::isReference)?.let(vault::remove)
            dao.delete(record.workId)
        }

    suspend fun retry(record: DownloadRecord) {
        DownloadWorker.runWork(context, Data.fromByteArray(record.request), record)
    }
}
