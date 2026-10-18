package com.mattzilla30.mbooru.data.repository.download

import android.content.Context
import androidx.work.*
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import com.mattzilla30.mbooru.worker.DownloadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadQueueRepository(private val context: Context, private val dao: DownloadDao) {
    private val manager = WorkManager.getInstance(context)
    val entries = combine(dao.observeAll(), manager.getWorkInfosByTagFlow(DownloadWorker.DOWNLOAD_TAG)) { records, work ->
        val states = work.associateBy { it.id.toString() }
        records.filter { it.state != "replaced" }.map { record ->
            val state = when (states[record.workId]?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "queued"
                WorkInfo.State.RUNNING -> "running"
                WorkInfo.State.SUCCEEDED -> "completed"
                WorkInfo.State.FAILED -> "failed"
                WorkInfo.State.CANCELLED -> "cancelled"
                null -> record.state
            }
            record.copy(state = state)
        }
    }
    suspend fun cancel(record: DownloadRecord) = withContext(Dispatchers.IO) {
        manager.cancelWorkById(UUID.fromString(record.workId)).result.get()
        dao.state(record.workId, "cancelled", "interrupted")
    }
    suspend fun retry(record: DownloadRecord) {
        DownloadWorker.runWork(context, Data.fromByteArray(record.request), record)
    }
}
