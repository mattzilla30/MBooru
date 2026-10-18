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

package com.mattzilla30.mbooru.common.tracemoe.api

import com.mattzilla30.mbooru.common.di.reverseSearchApi
import com.mattzilla30.mbooru.common.tracemoe.model.TraceResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

internal interface TraceMoeEndpoint {
    @Multipart @POST("search") suspend fun search(@Part image: MultipartBody.Part): TraceResponse
}

class TraceMoeApiService(client: OkHttpClient, baseUrl: String) : TraceMoeApi {
    private val api = reverseSearchApi<TraceMoeEndpoint>(client, baseUrl)

    override suspend fun fetch(imageBlob: ByteArray): TraceResponse =
        api.search(
            MultipartBody.Part.createFormData(
                "image",
                "image.jpg",
                imageBlob.toRequestBody("image/jpeg".toMediaType()),
            )
        )
}
