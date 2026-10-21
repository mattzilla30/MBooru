package com.mattzilla30.mbooru.worker

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.mattzilla30.mbooru.core.model.ResumePolicy
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import java.io.IOException
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Range writes are allowed only after validating both the server and the destination. */
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
internal suspend fun transferDownload(
    context: Context,
    queue: DownloadDao,
    workId: String,
    url: String,
    destination: Uri,
    client: okhttp3.OkHttpClient,
    maximumBytes: Long = com.mattzilla30.mbooru.core.model.DownloadLimits.DEFAULT_BYTES,
) =
    withContext(Dispatchers.IO) {
        val record = queue.get(workId)
        val validator = ResumePolicy.validator(record?.etag, record?.lastModified)
        var seekableOutput: ParcelFileDescriptor.AutoCloseOutputStream? =
            try {
                context.contentResolver.openFileDescriptor(destination, "rw")?.let { descriptor ->
                    ParcelFileDescriptor.AutoCloseOutputStream(descriptor).also { output ->
                        try {
                            val size = output.channel.size()
                            output.channel.position(output.channel.position())
                            output.channel.truncate(size)
                        } catch (failure: Exception) {
                            output.close()
                            throw failure
                        }
                    }
                }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
        val seekable = seekableOutput != null
        var offset =
            if (
                seekable &&
                    validator != null &&
                    record != null &&
                    record.bytesWritten > 0 &&
                    seekableOutput!!.channel.size() >= record.bytesWritten
            )
                record.bytesWritten
            else 0L
        val activeCall = java.util.concurrent.atomic.AtomicReference<okhttp3.Call?>()
        val cancellation =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    activeCall.get()?.cancel()
                }
            }
        val transferContext = coroutineContext
        fun load(request: Request): okhttp3.Response {
            transferContext.ensureActive()
            val call = client.newCall(request)
            activeCall.set(call)
            transferContext.ensureActive()
            return call.execute()
        }
        try {
            fun request(resume: Boolean): Request =
                Request.Builder()
                    .url(url)
                    .apply {
                        if (resume) {
                            header("Range", "bytes=$offset-")
                            header("If-Range", validator!!)
                        }
                        // Transparent decompression makes byte offsets ambiguous. Request the wire
                        // representation.
                        header("Accept-Encoding", "identity")
                    }
                    .build()
            var response = load(request(offset > 0))
            if (offset > 0) {
                val actualValidator =
                    if (record?.etag?.let { ResumePolicy.validator(it, null) } != null)
                        response.header("ETag")
                    else response.header("Last-Modified")
                if (
                    response.code != 206 ||
                        !ResumePolicy.acceptsPartial(
                            offset,
                            response.header("Content-Range"),
                            validator,
                            actualValidator,
                        )
                ) {
                    offset = 0
                    if (response.code != 200) {
                        response.close()
                        response = load(request(false))
                    }
                }
            }
            response.use {
                if (!it.isSuccessful || (offset == 0L && it.code == 206))
                    throw IOException("Invalid download response")
                if (
                    it.header("Content-Encoding")?.let { encoding -> encoding != "identity" } ==
                        true
                )
                    throw IOException("Encoded byte ranges are unsupported")
                val body = it.body ?: throw IOException("Empty download response")
                val expected = body.contentLength()
                val total = if (expected < 0) -1L else offset + expected
                if (offset > maximumBytes || total > maximumBytes)
                    throw DownloadSizeLimitException()
                val etag = if (seekable) it.header("ETag") else null
                val modified = if (seekable) it.header("Last-Modified") else null
                val channel = seekableOutput?.channel
                val output: OutputStream =
                    seekableOutput?.also {
                        try {
                            it.channel.truncate(offset)
                            it.channel.position(offset)
                        } catch (failure: Exception) {
                            throw IOException("Destination no longer supports resume", failure)
                        }
                    }
                        ?: context.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("Destination unavailable")
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
                            if (count.toLong() > maximumBytes - written)
                                throw DownloadSizeLimitException()
                            target.write(buffer, 0, count)
                            written += count
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - checkpoint >= 500) {
                                target.flush()
                                channel?.force(true)
                                queue.progress(workId, written, total, etag, modified)
                                checkpoint = now
                            }
                        }
                        target.flush()
                        channel?.force(true)
                    }
                }
                if (expected >= 0 && written - offset != expected)
                    throw IOException("Incomplete download response")
                queue.progress(workId, written, if (total < 0) written else total, etag, modified)
            }
        } finally {
            cancellation.cancel()
            activeCall.get()?.cancel()
            seekableOutput?.close()
        }
    }

internal class DownloadSizeLimitException :
    IOException("Download exceeds the configured size limit")
