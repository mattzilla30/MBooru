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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import coil.disk.DiskCache
import coil.imageLoader
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.okhttp.ProgressInterceptor
import com.mattzilla30.mbooru.receiver.DownloadNotificationClickReceiver
import com.mattzilla30.mbooru.ui.base.PathActivity
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.internal.closeQuietly
import org.koin.core.component.inject

class DownloadWorker(
    context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters), org.koin.core.component.KoinComponent {
    private val settings by inject<com.mattzilla30.mbooru.app.SettingsRepository>()

    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    private val vault by inject<com.mattzilla30.mbooru.data.credentials.CredentialRepository>()
    private val queue by inject<DownloadDao>()
    private val transport by inject<com.mattzilla30.mbooru.data.api.HttpTransport>()

    companion object {
        const val DOWNLOAD_TAG = "mbooru-download"
        private val enqueueMutex = Mutex()
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

        private const val FINAL_NAME_KEY = "final_name"
        const val DOC_ID_KEY = "doc_id"
        const val INPUT_DATA_KEY = "input_data"

        fun downloadPost(post: Post?, host: String, activity: PathActivity) =
            activity.lifecycleScope.launch {
                val settings = activity.settings
                if (post == null) return@launch
                val url =
                    when (settings.downloadSize) {
                        Settings.POST_SIZE_SAMPLE -> post.sample
                        Settings.POST_SIZE_LARGER -> post.medium
                        else -> post.origin
                    }
                if (url.isEmpty()) return@launch
                var fileName = url.fileName()
                if (!fileName.contains(' ')) fileName = "${post.id} - $fileName"
                val docUri = activity.getDownloadUri(host, fileName) ?: return@launch
                val docId = docUri.toString()
                val data =
                    workDataOf(
                        URL_KEY to url,
                        HOST_KEY to host,
                        POST_ID_KEY to post.id,
                        TYPE_KEY to TYPE_POST,
                        FINAL_NAME_KEY to fileName,
                        DOC_ID_KEY to docId,
                    )
                runWork(activity, data)
            }

        fun download(url: String, postId: Int, host: String, activity: PathActivity) =
            activity.lifecycleScope.launch {
                if (url.isEmpty()) return@launch
                var fileName = url.fileName()
                if (!fileName.contains(' ')) fileName = "$postId - $fileName"
                val docUri = activity.getDownloadUri(host, fileName) ?: return@launch
                val docId = docUri.toString()
                val data =
                    workDataOf(
                        URL_KEY to url,
                        HOST_KEY to host,
                        POST_ID_KEY to postId,
                        TYPE_KEY to TYPE_POST,
                        FINAL_NAME_KEY to fileName,
                        DOC_ID_KEY to docId,
                    )
                runWork(activity, data)
            }

        fun downloadPool(
            activity: PathActivity,
            poolId: Int,
            type: Int,
            booru: Booru,
        ) =
            activity.lifecycleScope.launch {
                val scheme = booru.scheme
                val host = booru.host
                val fileName =
                    when (type) {
                        POOL_DOWNLOAD_TYPE_JPGS -> {
                            "$host - $poolId.jpg.zip"
                        }
                        else -> {
                            "$host - $poolId.png.zip"
                        }
                    }
                val docUri = activity.getPoolUri(fileName) ?: return@launch
                val docId = docUri.toString()
                val data =
                    workDataOf(
                        SCHEME_KEY to scheme,
                        HOST_KEY to host,
                        POOL_ID_KEY to poolId,
                        POOL_DOWNLOAD_TYPE_KEY to type,
                        TYPE_KEY to TYPE_POOL,
                        BOORU_UID_KEY to booru.uid,
                        FINAL_NAME_KEY to fileName,
                        DOC_ID_KEY to docId,
                    )
                runWork(activity, data)
            }

        suspend fun runWork(context: Context, data: Data, previous: DownloadRecord? = null) =
            withContext(Dispatchers.IO) {
                enqueueMutex.withLock {
                    val manager = WorkManager.getInstance(context)
                    val unique = "download:${data.getString(DOC_ID_KEY)}"
                    if (
                        manager.getWorkInfosForUniqueWork(unique).get().any { !it.state.isFinished }
                    )
                        return@withLock
                    val vault =
                        org.koin.core.context.GlobalContext.get()
                            .get<com.mattzilla30.mbooru.data.credentials.CredentialRepository>()
                    val safe =
                        data.keyValueMap
                            .filterKeys { it != USERNAME_KEY && it != PASSWORD_HASH_KEY }
                            .toMutableMap()
                    // A CDN address can itself be an access capability. Persist only a device-vault
                    // reference.
                    (safe[URL_KEY] as? String)?.let {
                        if (!vault.isReference(it)) safe[URL_KEY] = vault.store(it)
                    }
                    val safeData = Data.Builder().putAll(safe).build()
                    val constraints =
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    val work =
                        OneTimeWorkRequestBuilder<DownloadWorker>()
                            .setInputData(safeData)
                            .setConstraints(constraints)
                            .addTag(DOWNLOAD_TAG)
                            .keepResultsForAtLeast(7, java.util.concurrent.TimeUnit.DAYS)
                            .build()
                    val dao = org.koin.core.context.GlobalContext.get().get<DownloadDao>()
                    val record =
                        previous?.copy(
                            workId = work.id.toString(),
                            state = "queued",
                            error = null,
                            createdAt = System.currentTimeMillis(),
                        )
                            ?: DownloadRecord(
                                work.id.toString(),
                                safeData.getString(DOC_ID_KEY).orEmpty(),
                                "${safeData.getString(HOST_KEY).orEmpty()} - ${safeData.getInt(POST_ID_KEY, safeData.getInt(POOL_ID_KEY, -1))}",
                                safeData.toByteArray(),
                                System.currentTimeMillis(),
                            )
                    // Finish this short local transaction/enqueue even if the originating screen
                    // closes.
                    withContext(NonCancellable) {
                        dao.insert(record)
                        try {
                            manager
                                .enqueueUniqueWork(unique, ExistingWorkPolicy.KEEP, work)
                                .result
                                .get()
                            if (previous != null) dao.state(previous.workId, "replaced")
                        } catch (failure: Exception) {
                            dao.state(record.workId, "failed", "enqueue_failed")
                            throw failure
                        }
                    }
                }
            }
    }

    override suspend fun doWork(): Result {
        val preferences by inject<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
        preferences.awaitReady()
        val key = id.toString()
        if (queue.get(key) == null) {
            val values =
                inputData.keyValueMap
                    .filterKeys { it != USERNAME_KEY && it != PASSWORD_HASH_KEY }
                    .toMutableMap()
            (values[URL_KEY] as? String)?.let {
                if (!vault.isReference(it)) values[URL_KEY] = vault.store(it)
            }
            val safe = Data.Builder().putAll(values).build()
            queue.insert(
                DownloadRecord(
                    key,
                    inputData.getString(DOC_ID_KEY).orEmpty(),
                    "${inputData.getString(HOST_KEY).orEmpty()} - ${inputData.getInt(POST_ID_KEY, inputData.getInt(POOL_ID_KEY, -1))}",
                    safe.toByteArray(),
                    System.currentTimeMillis(),
                )
            )
        }
        queue.state(key, "running")
        return try {
            val succeeded =
                when (inputData.getString(TYPE_KEY)) {
                    TYPE_POST -> downloadPost()
                    TYPE_POOL -> downloadPool()
                    else -> false
                }
            queue.state(
                key,
                if (succeeded) "completed" else "failed",
                if (succeeded) null else "transfer_failed",
            )
            if (succeeded) Result.success() else Result.failure()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { queue.state(key, "cancelled", "interrupted") }
            throw cancelled
        } catch (_: SecurityException) {
            queue.state(key, "failed", "storage_permission")
            Result.failure()
        } catch (_: IllegalStateException) {
            queue.state(key, "failed", "background_limit")
            Result.failure()
        } catch (_: IOException) {
            queue.state(key, "failed", "transfer_failed")
            Result.failure()
        }
    }

    private suspend fun downloadPost(): Boolean {
        val storedUrl = inputData.getString(URL_KEY)
        val url = storedUrl?.let { if (vault.isReference(it)) vault.read(it) else it }
        val id = inputData.getInt(POST_ID_KEY, -1)
        val host = inputData.getString(HOST_KEY)
        val docId = inputData.getString(DOC_ID_KEY)
        if (url == null || id < 0 || host == null || docId == null) return false
        val desUri = applicationContext.contentResolver.getFileUriByDocId(docId) ?: return false
        val channelId = applicationContext.packageName + ".download"
        val notificationManager =
            getNotificationManager(
                channelId,
                applicationContext.getString(R.string.post_download),
            )
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
                        val output =
                            applicationContext.contentResolver.openOutputStream(desUri, "wt")
                                ?: throw IOException("Download destination is unavailable")
                        output.use { copyDownload(input, it) }
                    }
                }
            } else transfer(url, desUri)
            notifyDownload(
                notificationManager,
                id + 1000000,
                getDownloadedNotification(title, channelId, publish(desUri)),
            )
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            notifyDownload(
                notificationManager,
                id + 1000000,
                getDownloadErrorNotification(title, channelId),
            )
            return false
        } finally {
            ProgressInterceptor.removeListener(url)
            snapshot?.closeQuietly()
        }
    }

    private suspend fun downloadPool(): Boolean {
        val id = inputData.getInt(POOL_ID_KEY, -1)
        val scheme = inputData.getString(SCHEME_KEY)
        val host = inputData.getString(HOST_KEY)
        val type =
            inputData.getInt(
                POOL_DOWNLOAD_TYPE_KEY,
                POOL_DOWNLOAD_TYPE_JPGS,
            )
        val selectedSite =
            booruManager.getBooruByUid(inputData.getLong(BOORU_UID_KEY, -1))?.takeIf {
                it.host == host && it.scheme == scheme
            }
        val username = selectedSite?.user?.name ?: inputData.getString(USERNAME_KEY)
        val passwordHash = selectedSite?.user?.token ?: inputData.getString(PASSWORD_HASH_KEY)
        val docId = inputData.getString(DOC_ID_KEY)
        if (
            id < 0 ||
                scheme.isNullOrEmpty() ||
                host.isNullOrEmpty() ||
                username.isNullOrEmpty() ||
                passwordHash.isNullOrEmpty() ||
                docId == null
        )
            return false
        var url =
            when (type) {
                POOL_DOWNLOAD_TYPE_JPGS -> {
                    applicationContext.getString(
                        R.string.pool_download_jpgs_url_format,
                        scheme,
                        host,
                        id,
                    )
                }
                else -> {
                    applicationContext.getString(
                        R.string.pool_download_pngs_url_format,
                        scheme,
                        host,
                        id,
                    )
                }
            }
        url = "$url&login=$username&password_hash=$passwordHash"
        val desUri = applicationContext.contentResolver.getFileUriByDocId(docId) ?: return false
        val channelId = applicationContext.packageName + ".download_pool"
        val notificationManager =
            getNotificationManager(
                channelId,
                applicationContext.getString(R.string.pool_download),
            )
        val title = "$host - pool: $id"
        setForeground(createDownloadingInfo(title, channelId, id, 0))
        try {
            ProgressInterceptor.bindUrlWithInterval(url, 500L) { progress ->
                setForegroundAsync(createDownloadingInfo(title, channelId, id, progress))
            }
            transfer(url, desUri)
            notifyDownload(
                notificationManager,
                id + 1000000,
                getDownloadedNotification(title, channelId, publish(desUri)),
            )
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            notifyDownload(
                notificationManager,
                id + 1000000,
                getDownloadErrorNotification(title, channelId),
            )
            return false
        } finally {
            ProgressInterceptor.removeListener(url)
        }
    }

    private suspend fun publish(part: Uri): Uri =
        withContext(Dispatchers.IO) {
            val name =
                inputData.getString(FINAL_NAME_KEY) ?: return@withContext part // Legacy work.
            val final = publishDownload(applicationContext, part, name)
            queue.destination(id.toString(), final.toString())
            final
        }

    private suspend fun transfer(url: String, destination: Uri) =
        transferDownload(
            applicationContext,
            queue,
            id.toString(),
            url,
            destination,
            transport.mediaClient(),
        )

    private fun notifyDownload(manager: NotificationManager, id: Int, notification: Notification) {
        // Permission denial must not turn an otherwise successful download into a failed worker.
        if (
            Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    applicationContext,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            return
        manager.notify(id, notification)
    }

    private fun getNotificationManager(
        channelId: String,
        channelName: String,
    ): NotificationManager {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel =
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW,
                )
            notificationManager.createNotificationChannel(notificationChannel)
        }
        return notificationManager
    }

    private fun createDownloadingInfo(
        title: String,
        channelId: String,
        notificationId: Int,
        progress: Int,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification =
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setColor(ContextCompat.getColor(applicationContext, R.color.colorPrimary))
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentTitle(title)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .addAction(
                    android.R.drawable.ic_delete,
                    applicationContext.getString(R.string.dialog_cancel),
                    cancelIntent,
                )
                .setProgress(100, progress, false)
                .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun getDownloadedNotification(
        title: String,
        channelId: String,
        desUri: Uri,
    ): Notification {
        // Open the chooser directly: notification-to-broadcast-to-activity trampolines are blocked.
        val viewIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    desUri,
                    androidx.documentfile.provider.DocumentFile.fromSingleUri(
                            applicationContext,
                            desUri,
                        )
                        ?.name
                        ?.getMimeType()
                        .orEmpty()
                        .ifBlank { "*/*" },
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newRawUri("download", desUri)
            }
        val chooser =
            Intent.createChooser(viewIntent, applicationContext.getString(R.string.share_via))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags =
            PendingIntent.FLAG_CANCEL_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE
                else 0
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                System.currentTimeMillis().toInt(),
                chooser,
                flags,
            )
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
        val pendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getBroadcast(
                    applicationContext,
                    System.currentTimeMillis().toInt(),
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getBroadcast(
                    applicationContext,
                    System.currentTimeMillis().toInt(),
                    intent,
                    PendingIntent.FLAG_CANCEL_CURRENT,
                )
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
                pendingIntent,
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
