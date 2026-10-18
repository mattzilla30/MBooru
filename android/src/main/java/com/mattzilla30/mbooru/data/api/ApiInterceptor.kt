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

import androidx.core.text.HtmlCompat
import com.mattzilla30.mbooru.app.Keys
import com.mattzilla30.mbooru.app.Values
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class ApiInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requests =
            chain
                .request()
                .newBuilder()
                .header(Keys.HEADER_USER_AGENT, Values.MOBILE_USER_AGENT)
                .build()
        val response = chain.proceed(requests)
        val body = response.body
        val pahealPosts =
            requests.url.host == "rule34.paheal.net" &&
                requests.url.encodedPath == "/api/danbooru/find_posts"
        if (!pahealPosts || !response.isSuccessful || body == null) return response
        // The inner BoundedApiResponseInterceptor bounds this document before it is read.
        return response
            .newBuilder()
            .removeHeader("Content-Length")
            .body(normalizePahealXml(body.string()).toResponseBody(body.contentType()))
            .build()
    }
}

private val namedEntity = Regex("&[A-Za-z][A-Za-z0-9]+;")
private val xmlEntities = setOf("&amp;", "&lt;", "&gt;", "&quot;", "&apos;")

/** Paheal emits HTML named entities inside XML; numeric references work in XML parsers. */
internal fun normalizePahealXml(xml: String): String =
    namedEntity.replace(xml) { match ->
        if (match.value in xmlEntities) match.value
        else {
            val decoded =
                HtmlCompat.fromHtml(match.value, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            if (decoded == match.value) match.value
            else
                buildString {
                    var offset = 0
                    while (offset < decoded.length) {
                        val point = decoded.codePointAt(offset)
                        append("&#").append(point).append(';')
                        offset += Character.charCount(point)
                    }
                }
        }
    }

class ApiSankakuInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val requests =
            chain
                .request()
                .newBuilder()
                .header(Keys.HEADER_USER_AGENT, Values.PC_USER_AGENT)
                .header(Keys.HEADER_ORIGIN, Values.SANKAKU_ORIGIN)
                .header(Keys.HEADER_REFERER, Values.SANKAKU_REFERER)
                .build()
        return chain.proceed(requests)
    }
}
