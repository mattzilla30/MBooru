package com.mattzilla30.mbooru.worker

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.mattzilla30.mbooru.core.model.ResumePolicy
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import com.mattzilla30.mbooru.okhttp.OkHttp3Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/** Range writes are allowed only after validating both the server and the destination. */
internal suspend fun transferDownload(context: Context, queue: DownloadDao, workId: String, url: String, destination: Uri, client: okhttp3.OkHttpClient) = withContext(Dispatchers.IO) {
    val record = queue.get(workId)
    val validator = ResumePolicy.validator(record?.etag, record?.lastModified)
    var descriptor: ParcelFileDescriptor? = try { context.contentResolver.openFileDescriptor(destination, "rw") }
        catch (_: IOException) { null } catch (_: SecurityException) { null }
    val seekable = descriptor?.let {
        try { Os.lseek(it.fileDescriptor, 0, OsConstants.SEEK_CUR); true } catch (_: Exception) { false }
    } ?: false
    var offset = if (seekable && validator != null && record != null && record.bytesWritten > 0 &&
        runCatching { Os.fstat(descriptor!!.fileDescriptor).st_size }.getOrDefault(-1) >= record.bytesWritten) record.bytesWritten else 0L
    if (!seekable) { descriptor?.close(); descriptor = null }
    val downloader = OkHttp3Downloader(client)
    try {
        fun request(resume: Boolean): Request = Request.Builder().url(url).apply {
            if (resume) { header("Range", "bytes=$offset-"); header("If-Range", validator!!) }
            // Transparent decompression makes byte offsets ambiguous. Request the wire representation.
            header("Accept-Encoding", "identity")
        }.build()
        var response = downloader.load(request(offset > 0))
        if (offset > 0) {
            val actualValidator = if (record?.etag?.let { ResumePolicy.validator(it, null) } != null)
                response.header("ETag") else response.header("Last-Modified")
            if (response.code != 206 || !ResumePolicy.acceptsPartial(offset, response.header("Content-Range"), validator, actualValidator)) {
                offset = 0
                if (response.code != 200) { response.close(); response = downloader.load(request(false)) }
            }
        }
        response.use {
            if (!it.isSuccessful || (offset == 0L && it.code == 206)) throw IOException("Invalid download response")
            if (it.header("Content-Encoding")?.let { encoding -> encoding != "identity" } == true) throw IOException("Encoded byte ranges are unsupported")
            val body = it.body ?: throw IOException("Empty download response")
            val expected = body.contentLength()
            val total = if (expected < 0) -1L else offset + expected
            val etag = if (seekable) it.header("ETag") else null
            val modified = if (seekable) it.header("Last-Modified") else null
            val rawFd = descriptor?.fileDescriptor
            val output: OutputStream = if (descriptor != null) {
                Os.ftruncate(descriptor!!.fileDescriptor, offset)
                Os.lseek(descriptor!!.fileDescriptor, offset, OsConstants.SEEK_SET)
                ParcelFileDescriptor.AutoCloseOutputStream(descriptor).also { descriptor = null }
            } else context.contentResolver.openOutputStream(destination, "wt") ?: throw IOException("Destination unavailable")
            queue.progress(workId, offset, total, etag, modified)
            var written = offset
            var checkpoint = android.os.SystemClock.elapsedRealtime()
            body.byteStream().use { input ->
                output.use { target ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        written += count
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - checkpoint >= 500) {
                            target.flush()
                            rawFd?.sync()
                            queue.progress(workId, written, total, etag, modified)
                            checkpoint = now
                        }
                    }
                    target.flush()
                    rawFd?.sync()
                }
            }
            if (expected >= 0 && written - offset != expected) throw IOException("Incomplete download response")
            queue.progress(workId, written, if (total < 0) written else total, etag, modified)
        }
    } finally { descriptor?.close(); downloader.shutdown() }
}
