package com.mattzilla30.mbooru.data.database.dao

import androidx.room.*
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadRecord>>

    @Query("SELECT * FROM downloads WHERE workId = :id")
    suspend fun get(id: String): DownloadRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(record: DownloadRecord)

    @Query("UPDATE downloads SET state = :state, error = :error WHERE workId = :id")
    suspend fun state(id: String, state: String, error: String? = null)

    @Query(
        "UPDATE downloads SET bytesWritten = :bytes, totalBytes = :total, etag = :etag, lastModified = :modified WHERE workId = :id"
    )
    suspend fun progress(id: String, bytes: Long, total: Long, etag: String?, modified: String?)

    @Query("UPDATE downloads SET documentId = :uri WHERE workId = :id")
    suspend fun destination(id: String, uri: String)

    @Query("DELETE FROM downloads WHERE workId = :id") suspend fun delete(id: String)
}
