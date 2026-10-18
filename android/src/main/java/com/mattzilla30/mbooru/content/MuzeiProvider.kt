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

package com.mattzilla30.mbooru.content

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.executeBlocking
import coil3.imageLoader
import coil3.request.*
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.android.apps.muzei.api.provider.Artwork
import com.google.android.apps.muzei.api.provider.MuzeiArtProvider
import com.mattzilla30.mbooru.worker.MuzeiArtWorker
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

class MuzeiProvider : MuzeiArtProvider() {

    override fun onLoadRequested(initial: Boolean) {
        context?.let { MuzeiArtWorker.enqueueLoad(it) }
    }

    @Throws(IOException::class)
    override fun openFile(artwork: Artwork): InputStream {
        val uri = artwork.persistentUri
        val context = context
        return if (
            context != null && uri != null && (uri.scheme == "http" || uri.scheme == "https")
        ) {
            val key = uri.toString()
            val request =
                ImageRequest.Builder(context)
                    .data(uri)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCacheKey(key)
                    .allowConversionToBitmap(false)
                    .decoderFactory { _, _, _ ->
                        Decoder { DecodeResult(ColorDrawable(Color.BLACK).asImage(), false) }
                    }
                    .build()
            val result = context.imageLoader.executeBlocking(request)
            if (result is SuccessResult) {
                val snapshot = context.imageLoader.diskCache?.openSnapshot(key)
                val file = snapshot?.data?.toFile()
                if (file != null && file.exists()) {
                    try {
                        object : java.io.FilterInputStream(FileInputStream(file)) {
                            override fun close() {
                                try {
                                    super.close()
                                } finally {
                                    snapshot.close()
                                }
                            }
                        }
                    } catch (failure: IOException) {
                        snapshot.close()
                        throw failure
                    }
                } else {
                    snapshot?.close()
                    super.openFile(artwork)
                }
            } else {
                super.openFile(artwork)
            }
        } else {
            super.openFile(artwork)
        }
    }
}
