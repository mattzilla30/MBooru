// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.model.common

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Persistent transfer state. Requests contain site IDs, never account passwords. */
@Entity(tableName = "downloads")
data class DownloadRecord(
    @PrimaryKey val workId: String,
    val documentId: String,
    val title: String,
    val request: ByteArray,
    val createdAt: Long,
    val state: String = "queued",
    val bytesWritten: Long = 0,
    val totalBytes: Long = -1,
    val etag: String? = null,
    val lastModified: String? = null,
    val error: String? = null,
)
