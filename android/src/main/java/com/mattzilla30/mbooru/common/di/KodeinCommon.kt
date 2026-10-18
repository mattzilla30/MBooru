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

package com.mattzilla30.mbooru.common.di

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mattzilla30.mbooru.data.api.HttpTransport
import com.mattzilla30.mbooru.common.saucenao.api.SauceNaoApi
import com.mattzilla30.mbooru.common.saucenao.api.SauceNaoApiService
import com.mattzilla30.mbooru.common.tracemoe.api.TraceMoeApi
import com.mattzilla30.mbooru.common.tracemoe.api.TraceMoeApiService
import org.koin.dsl.module

internal inline fun <reified T> reverseSearchApi(client: OkHttpClient, baseUrl: String): T = Retrofit.Builder()
    .baseUrl(baseUrl.trimEnd('/') + "/")
    .client(client)
    .addConverterFactory(Json { ignoreUnknownKeys = true; isLenient = true }
        .asConverterFactory("application/json".toMediaType()))
    .build().create(T::class.java)

val commonModules = module {
    single<OkHttpClient> { get<HttpTransport>().apiClient(false) }
    single<SauceNaoApi> { SauceNaoApiService(get(), "https://saucenao.com") }
    single<TraceMoeApi> { TraceMoeApiService(get(), "https://api.trace.moe") }
}
