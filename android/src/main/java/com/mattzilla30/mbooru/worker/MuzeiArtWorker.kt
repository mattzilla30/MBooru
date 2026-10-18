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

import org.koin.core.component.inject

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.*
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.ProviderContract
import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.app.Settings.activeMuzeiUid
import com.mattzilla30.mbooru.data.database.MuzeiManager
import com.mattzilla30.mbooru.data.database.dao.PostDao
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_LARGER
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_SAMPLE
import com.mattzilla30.mbooru.app.Settings.muzeiLimit
import com.mattzilla30.mbooru.app.Settings.muzeiSize
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.extension.isStillImage


class MuzeiArtWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) , org.koin.core.component.KoinComponent {
    private val muzeiManager by inject<com.mattzilla30.mbooru.data.database.MuzeiManager>()

    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    companion object {
        internal fun enqueueLoad(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.enqueueUniqueWork("muzei-publish", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MuzeiArtWorker>().build())
        }
    }
    override suspend fun doWork(): Result {
        val uid = activeMuzeiUid
        val muzei = muzeiManager.getMuzeiByUid(uid) ?: return Result.failure()
        val booru = booruManager.getBooruByUid(muzei.booruUid) ?: return Result.failure()
        val postDao by inject<PostDao>()
        val posts = postDao.getPostsRaw(booruUid = muzei.booruUid, query = muzei.query, limit = muzeiLimit).filter { it.origin.isStillImage() }
        if (posts.isEmpty() || isStopped) return Result.success()
        val providerClient = ProviderContract.getProviderClient(
            applicationContext, applicationContext.packageName + ".muzei")
        val attributionString = applicationContext.getString(R.string.muzei_attribution)
        val artworks = posts.map { post ->
            Artwork(
                token = "id:${post.id}",
                title = "Post ${post.id}",
                byline = post.query,
                attribution = attributionString,
                persistentUri = when (muzeiSize) {
                    POST_SIZE_SAMPLE -> post.sample.toUri()
                    POST_SIZE_LARGER -> post.medium.toUri()
                    else -> post.origin.toUri()
                },
                webUri = getWebUri(booru, post.id)
            )
        }
        if (!isStopped) providerClient.setArtwork(artworks)
        return Result.success()
    }

    private fun getWebUri(booru: Booru, postId: Int): Uri {
        return when (booru.type) {
            BOORU_TYPE_DAN -> String.format("%s://%s/posts/%d", booru.scheme, booru.host, postId).toUri()
            in arrayOf(BOORU_TYPE_GEL, BOORU_TYPE_GEL_LEGACY) -> String.format("%s://%s/index.php?page=post&s=view&id=%d", booru.scheme, booru.host, postId).toUri()
            BOORU_TYPE_SHIMMIE -> {
                if (booru.path.isNullOrBlank()) {
                    String.format("%s://%s/post/view/%d", booru.scheme, booru.host, postId).toUri()
                } else {
                    String.format("%s://%s/%s/post/view/%d", booru.scheme, booru.host, booru.path, postId).toUri()
                }
            }
            else -> String.format("%s://%s/post/show/%d",
                booru.scheme,
                if (booru.host.startsWith("capi-v2."))
                    booru.host.replaceFirst("capi-v2.", "chan.")
                else
                    booru.host,
                postId
            ).toUri()
        }
    }
}