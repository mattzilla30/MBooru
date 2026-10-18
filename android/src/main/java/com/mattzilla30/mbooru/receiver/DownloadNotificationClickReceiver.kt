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

package com.mattzilla30.mbooru.receiver

import kotlinx.coroutines.launch

import android.content.*
import android.net.Uri
import androidx.work.Data
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.extension.getMimeType
import com.mattzilla30.mbooru.worker.DownloadWorker

class DownloadNotificationClickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        val uri = intent.data
        val data = intent.getByteArrayExtra(DownloadWorker.INPUT_DATA_KEY)
        when {
            uri != null -> openUri(context, uri)
            data != null -> {
                val pending = goAsync()
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                    try { DownloadWorker.runWork(context, Data.fromByteArray(data)) }
                    finally { pending.finish() }
                }
            }
        }
    }

    private fun openUri(context: Context, uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
            return
        }
        val newIntent = Intent().apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            setDataAndType(uri, uri.toString().getMimeType())
        }
        try {
            context.startActivity(
                Intent.createChooser(
                    newIntent,
                    context.getString(R.string.share_via)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {

        } catch (_: RuntimeException) {

        }
    }
}
