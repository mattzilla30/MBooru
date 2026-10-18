/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.mattzilla30.mbooru.worker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.disk.DiskCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.InterruptedIOException
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import okhttp3.internal.closeQuietly
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.okhttp.ProgressInterceptor
import java.io.FileInputStream
import java.io.IOException
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.receiver.DownloadNotificationClickReceiver
import com.mattzilla30.mbooru.okhttp.OkHttp3Downloader
import com.mattzilla30.mbooru.ui.base.PathActivity
import java.io.InputStream
import java.io.OutputStream

class DownloadWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    companion object {
        private const val URL_KEY = "url"
        private const val HOST_KEY = "host"
        private const val POST_ID_KEY = "post_id"

        private const val BOORU_UID_KEY = "booru_uid"
        private const val TYPE_KEY = "type"
        private const val TYPE_POST = "type_post"
        private const val TYPE_POOL = "type_pool"
        private const val SCHEME_KEY = "scheme"
        private const val POOL_ID_KEY = "pool_id"
        private const val POOL_DOWNLOAD_TYPE_KEY = "pool_download_type"
        const val POOL_DOWNLOAD_TYPE_JPGS = 0
        const val POOL_DOWNLOAD_TYPE_PNGS = 1
        private const val USERNAME_KEY = "username"
        private const val PASSWORD_HASH_KEY = "password_hash"

        const val DOC_ID_KEY = "doc_id"
        const val INPUT_DATA_KEY = "input_data"

        fun downloadPost(post: Post?, host: String, activity: PathActivity) {
            if (post == null) return
            val url = when (Settings.downloadSize) {
                Settings.POST_SIZE_SAMPLE -> post.sample
                Settings.POST_SIZE_LARGER -> post.medium
                else -> post.origin
            }
            if (url.isEmpty()) return
            var fileName = url.fileName()
            if (!fileName.contains(' ')) fileName = "${post.id} - $fileName"
            val docUri = activity.getDownloadUri(host, fileName) ?: return
            val docId = DocumentsContract.getDocumentId(docUri)
            val data = workDataOf(
                URL_KEY to url,
                HOST_KEY to host,
                POST_ID_KEY to post.id,
                TYPE_KEY to TYPE_POST,
                DOC_ID_KEY to docId
            )
            runWork(data)
        }

        fun download(url: String, postId: Int, host: String, activity: PathActivity) {
            if (url.isEmpty()) return
            var fileName = url.fileName()
            if (!fileName.contains(' ')) fileName = "$postId - $fileName"
            val docUri = activity.getDownloadUri(host, fileName) ?: return
            val docId = DocumentsContract.getDocumentId(docUri)
            val data = workDataOf(
                URL_KEY to url,
                HOST_KEY to host,
                POST_ID_KEY to postId,
                TYPE_KEY to TYPE_POST,
                DOC_ID_KEY to docId
            )
            runWork(data)
        }

        fun downloadPool(
            activity: PathActivity,
            poolId: Int,
            type: Int,
            booru: Booru
        ) {
            val scheme = booru.scheme
            val host = booru.host
            val fileName = when (type) {
                POOL_DOWNLOAD_TYPE_JPGS -> {
                    "$host - $poolId.jpg.zip"
                }
                else -> {
                    "$host - $poolId.png.zip"
                }
            }
            val docUri = activity.getPoolUri(fileName) ?: return
            val docId = DocumentsContract.getDocumentId(docUri)
            val data = workDataOf(
                SCHEME_KEY to scheme,
                HOST_KEY to host,
                POOL_ID_KEY to poolId,
                POOL_DOWNLOAD_TYPE_KEY to type,
                TYPE_KEY to TYPE_POOL,
                BOORU_UID_KEY to booru.uid,
                DOC_ID_KEY to docId
            )
            runWork(data)
        }
        fun runWork(data: Data) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(App.app).enqueueUniqueWork(
                "download:${data.getString(DOC_ID_KEY)}", ExistingWorkPolicy.KEEP, work)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            when (inputData.getString(TYPE_KEY)) {
                TYPE_POST -> downloadPost()
                TYPE_POOL -> downloadPool()
                else -> Result.failure()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IllegalStateException) {
            // A background restart can be denied foreground-service access. Let the user retry.
            Result.failure()
        } catch (_: SecurityException) {
            Result.failure()
        }
    }

    private suspend fun downloadPost(): Result {
        val url = inputData.getString(URL_KEY)
        val id = inputData.getInt(POST_ID_KEY, -1)
        val host = inputData.getString(HOST_KEY)
        val docId =  inputData.getString(DOC_ID_KEY)
        if (url == null || id < 0 || host == null || docId == null) return Result.failure()
        val desUri = applicationContext.contentResolver.getFileUriByDocId(docId) ?: return Result.failure()
        val channelId = applicationContext.packageName + ".download"
        val notificationManager = getNotificationManager(
            channelId,
            applicationContext.getString(R.string.post_download))
        val title = "$host - $id"
        setForeground(createDownloadingInfo(title, channelId, id, 0))
        var snapshot: DiskCache.Snapshot? = null
        try {
            ProgressInterceptor.bindUrlWithInterval(url, 500L) { progress ->
                setForegroundAsync(createDownloadingInfo(title, channelId, id, progress))
            }
            snapshot = applicationContext.imageLoader.diskCache?.openSnapshot(url)
            val file = snapshot?.data?.toFile()
            if (file != null && file.exists()) {
                runInterruptible(Dispatchers.IO) {
                    FileInputStream(file).use { input ->
                        val output = applicationContext.contentResolver.openOutputStream(desUri, "wt")
                            ?: throw IOException("Download destination is unavailable")
                        output.use { copyDownload(input, it) }
                    }
                }
            } else transfer(url, desUri)
            notifyDownload(notificationManager, id + 1000000,
                getDownloadedNotification(title, channelId, desUri))
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            notifyDownload(notificationManager, id + 1000000, getDownloadErrorNotification(title, channelId))
            return Result.failure()
        } finally {
            ProgressInterceptor.removeListener(url)
            snapshot?.closeQuietly()
        }
    }

    private suspend fun downloadPool(): Result {
        val id = inputData.getInt(POOL_ID_KEY, -1)
        val scheme = inputData.getString(SCHEME_KEY)
        val host = inputData.getString(HOST_KEY)
        val type = inputData.getInt(
            POOL_DOWNLOAD_TYPE_KEY,
            POOL_DOWNLOAD_TYPE_JPGS
        )
        val selectedSite = com.mattzilla30.mbooru.data.database.BooruManager.getBooruByUid(
            inputData.getLong(BOORU_UID_KEY, -1))?.takeIf { it.host == host && it.scheme == scheme }
        val username = selectedSite?.user?.name ?: inputData.getString(USERNAME_KEY)
        val passwordHash = selectedSite?.user?.token ?: inputData.getString(PASSWORD_HASH_KEY)
        val docId =  inputData.getString(DOC_ID_KEY)
        if (id < 0 || scheme.isNullOrEmpty() ||
            host.isNullOrEmpty() || username.isNullOrEmpty() ||
            passwordHash.isNullOrEmpty() || docId == null)
            return Result.failure()
        var url = when (type) {
            POOL_DOWNLOAD_TYPE_JPGS -> {
                applicationContext.getString(R.string.pool_download_jpgs_url_format, scheme, host, id)
            }
            else -> {
                applicationContext.getString(R.string.pool_download_pngs_url_format, scheme, host, id)
            }
        }
        url = "$url&login=$username&password_hash=$passwordHash"
        val desUri = applicationContext.contentResolver.getFileUriByDocId(docId) ?: return Result.failure()
        val channelId = applicationContext.packageName + ".download_pool"
        val notificationManager = getNotificationManager(
            channelId,
            applicationContext.getString(R.string.pool_download))
        val title = "$host - pool: $id"
        setForeground(createDownloadingInfo(title, channelId, id, 0))
        try {
            ProgressInterceptor.bindUrlWithInterval(url, 500L) { progress ->
                setForegroundAsync(createDownloadingInfo(title, channelId, id, progress))
            }
            transfer(url, desUri)
            notifyDownload(notificationManager, id + 1000000,
                getDownloadedNotification(title, channelId, desUri))
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            notifyDownload(notificationManager, id + 1000000, getDownloadErrorNotification(title, channelId))
            return Result.failure()
        } finally {
            ProgressInterceptor.removeListener(url)
        }
    }

    private suspend fun transfer(url: String, destination: Uri) = runInterruptible(Dispatchers.IO) {
        // Streaming directly to SAF also supports files larger than the image-cache budget.
        val downloader = OkHttp3Downloader(applicationContext, useDiskCache = false)
        try {
            downloader.load(url).use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
                val body = response.body ?: throw IOException("Empty download response")
                body.byteStream().use { input ->
                    val output = applicationContext.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("Download destination is unavailable")
                    output.use { copyDownload(input, it) }
                }
            }
        } finally { downloader.shutdown() }
    }

    private fun notifyDownload(manager: NotificationManager, id: Int, notification: Notification) {
        // Permission denial must not turn an otherwise successful download into a failed worker.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        manager.notify(id, notification)
    }

    private fun getNotificationManager(channelId: String, channelName: String): NotificationManager {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }
        return notificationManager
    }

    private fun createDownloadingInfo(title: String, channelId: String, notificationId: Int, progress: Int): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentTitle(title)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_delete, applicationContext.getString(R.string.dialog_cancel), cancelIntent)
            .setProgress(100, progress, false)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getDownloadedNotification(title: String, channelId: String, desUri: Uri): Notification {
        // Open the chooser directly: notification-to-broadcast-to-activity trampolines are blocked.
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(desUri, desUri.toString().getMimeType())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri("download", desUri)
        }
        val chooser = Intent.createChooser(viewIntent, applicationContext.getString(R.string.share_via))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(applicationContext,
            System.currentTimeMillis().toInt(), chooser, flags)
        return NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.msg_download_complete))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getDownloadErrorNotification(title: String, channelId: String): Notification {
        val intent = Intent(applicationContext, DownloadNotificationClickReceiver::class.java)
        intent.putExtra(INPUT_DATA_KEY, inputData.toByteArray())
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getBroadcast(
                applicationContext,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(
                applicationContext,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT)
        }
        return NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.msg_download_failed))
            .setOngoing(false)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.stat_sys_download,
                applicationContext.getString(R.string.action_retry),
                pendingIntent
            )
            .build()
    }
}
/** Check interruption between chunks so cancellation/FGS timeout releases file handles promptly. */
internal fun copyDownload(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Download stopped")
        val count = input.read(buffer)
        if (count < 0) return
        output.write(buffer, 0, count)
    }
}
