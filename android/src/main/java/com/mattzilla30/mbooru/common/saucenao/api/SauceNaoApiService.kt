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

package com.mattzilla30.mbooru.common.saucenao.api

import com.mattzilla30.mbooru.common.di.reverseSearchApi
import com.mattzilla30.mbooru.common.saucenao.model.SauceNaoResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.*

internal interface SauceNaoEndpoint {
    @GET("search.php?db=999&output_type=2")
    suspend fun searchUrl(
        @Query("url") url: String,
        @Query("api_key") apiKey: String,
    ): SauceNaoResponse

    @Multipart
    @POST("search.php?db=999&output_type=2")
    suspend fun searchImage(
        @Query("api_key") apiKey: String,
        @Part file: MultipartBody.Part,
    ): SauceNaoResponse
}

class SauceNaoApiService(client: OkHttpClient, baseUrl: String) : SauceNaoApi {
    private val api = reverseSearchApi<SauceNaoEndpoint>(client, baseUrl)

    override suspend fun searchByUrl(url: String, apiKey: String) = api.searchUrl(url, apiKey)

    override suspend fun searchByImage(
        apiKey: String,
        byteArray: ByteArray,
        fileExt: String,
    ): SauceNaoResponse {
        val extension =
            fileExt.lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") } ?: "jpg"
        val mime = if (extension == "jpg") "jpeg" else extension
        return api.searchImage(
            apiKey,
            MultipartBody.Part.createFormData(
                "file",
                "image.$extension",
                byteArray.toRequestBody("image/$mime".toMediaType()),
            ),
        )
    }
}
