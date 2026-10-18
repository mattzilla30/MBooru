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

package com.mattzilla30.mbooru.data.api

import com.mattzilla30.mbooru.app.Values
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

val defaultJson
    get() = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

inline fun <reified T> createApi(client: OkHttpClient): T {
    val classJava = T::class
    val baseUrl =
        when (classJava) {
            AppUpdaterApi::class -> "https://raw.githubusercontent.com"
            else -> Values.BASE_URL
        }
    val converterFactory =
        when (classJava) {
            GelbooruApi::class,
            ShimmieApi::class -> {
                XML {
                        defaultPolicy {
                            pedantic = false
                            autoPolymorphic = true
                            unknownChildHandler = XmlConfig.IGNORING_UNKNOWN_CHILD_HANDLER
                        }
                    }
                    .asConverterFactory("application/xml".toMediaType())
            }
            else -> {
                defaultJson.asConverterFactory("application/json".toMediaType())
            }
        }
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(converterFactory)
        .build()
        .create(classJava.java)
}
