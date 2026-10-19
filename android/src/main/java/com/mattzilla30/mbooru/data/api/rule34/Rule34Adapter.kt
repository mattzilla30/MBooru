// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.core.model.ArtworkRating
import com.mattzilla30.mbooru.core.model.normalizedRating
import com.mattzilla30.mbooru.data.api.defaultJson
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.TagBase
import com.mattzilla30.mbooru.data.model.common.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

interface Rule34Api {
    @GET
    @Headers("Referer: https://rule34.xxx/", "Cache-Control: no-store")
    suspend fun posts(@Url url: HttpUrl): Response<ResponseBody>
}

data class Rule34Page(
    val posts: List<Post>,
    val received: Int,
    val ratingHidden: Int,
    val invalid: Int,
    val mismatched: Int,
) {
    fun summary() =
        "$received received · ${posts.size} displayable · $ratingHidden hidden by rating · " +
            "$invalid unusable · $mismatched unrelated."
}

class Rule34Adapter(private val api: Rule34Api) {
    suspend fun fetch(
        site: Booru,
        query: ArtworkQuery,
        page: Int,
        limit: Int,
        index: Int = 0,
    ): Rule34Page {
        val credentials = Rule34Credentials.from(site.user)
        val response = api.posts(requestUrl(credentials, query, page, limit))
        if (!response.isSuccessful) throw HttpException(response)
        return withContext(Dispatchers.IO) {
            val text = response.body()?.use { it.string() }?.trim()?.removePrefix("\uFEFF")?.trim()
            parse(text, site.uid, query, index)
        }
    }

    suspend fun fetchTags(action: com.mattzilla30.mbooru.data.action.ActionTag, page: Int) =
        withContext(Dispatchers.IO) {
            val response = api.posts(Rule34Tags.requestUrl(action, page))
            if (!response.isSuccessful) throw HttpException(response)
            Rule34Tags.parse(response.body()?.use { it.string() })
        }

    companion object {
        fun requestUrl(
            credentials: Rule34Credentials,
            query: ArtworkQuery,
            page: Int,
            limit: Int,
        ): HttpUrl {
            require(page >= 0 && limit in 1..MAX_PAGE_SIZE)
            return "https://api.rule34.xxx/index.php"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("page", "dapi")
                .addQueryParameter("s", "post")
                .addQueryParameter("q", "index")
                .addQueryParameter("json", "1")
                .addQueryParameter("pid", page.toString())
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("tags", query.serverTags())
                .addQueryParameter("user_id", credentials.userId)
                .addQueryParameter("api_key", credentials.key)
                .build()
        }

        fun mediaUrl(value: String): String {
            val address =
                when {
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("http://") -> "https://" + value.removePrefix("http://")
                    else -> value
                }.toHttpUrlOrNull() ?: return ""
            val hostAllowed = address.host == "rule34.xxx" || address.host.endsWith(".rule34.xxx")
            val containsCredentials =
                address.username.isNotEmpty() ||
                    address.password.isNotEmpty() ||
                    address.queryParameterNames.any { it.lowercase() in PRIVATE_MEDIA_PARAMETERS }
            return if (address.scheme == "https" && hostAllowed && !containsCredentials)
                address.toString()
            else ""
        }

        fun parse(text: String?, site: Long, query: ArtworkQuery, index: Int = 0): Rule34Page =
            PageParser(site, query, index).parse(postArray(text))

        private fun postArray(text: String?): JsonArray {
            if (text.isNullOrBlank() || text == "null")
                throw Rule34Exception(
                    "Rule34 returned a blank or null response, not a post list. Test the connection and retry."
                )
            val array =
                try {
                    defaultJson.parseToJsonElement(text) as? JsonArray
                } catch (_: IllegalArgumentException) {
                    null
                }
                    ?: throw Rule34Exception(
                        "Rule34 returned an unsupported response or browser verification challenge. " +
                            "Test the connection or open the site in a browser."
                    )
            return checkedPageSize(array)
        }

        private fun checkedPageSize(array: JsonArray): JsonArray {
            if (array.size > MAX_PAGE_SIZE)
                throw Rule34Exception(
                    "Rule34 returned too many records. Reduce the page size and retry."
                )
            return array
        }

        private class PageParser(val site: Long, val query: ArtworkQuery, val index: Int) {
            var hidden = 0
            var invalid = 0
            var mismatched = 0

            fun parse(array: JsonArray): Rule34Page {
                val posts =
                    array.mapNotNull(::verified).mapIndexed { offset, post ->
                        post.copy(index = index + offset)
                    }
                return Rule34Page(posts, array.size, hidden, invalid, mismatched)
            }

            private fun verified(element: JsonElement): Post? {
                val item = element as? JsonObject
                if (item == null) {
                    invalid++
                    return null
                }
                val rawRating = item.text("rating")
                return when {
                    (query.safeOnly && normalizedRating(rawRating) != ArtworkRating.SAFE) ||
                        (!query.safeOnly &&
                            !matchesRule34Rating(rawRating, query.normalizedTags)) -> {
                        hidden++
                        null
                    }
                    else -> verifyTags(map(item, site, query.normalizedTags, index))
                }
            }

            private fun verifyTags(post: Post?): Post? =
                when {
                    post == null -> {
                        invalid++
                        null
                    }
                    !matchesRule34Tags(post.tags.map { it.name }.toSet(), query.normalizedTags) -> {
                        mismatched++
                        null
                    }
                    else -> post
                }
        }

        @Suppress("ReturnCount") // Each required field has an independent fail-closed guard.
        private fun map(item: JsonObject, site: Long, query: String, index: Int): Post? {
            val id = item.text("id").toIntOrNull()?.takeIf { it > 0 } ?: return null
            val width =
                item.text("width").toIntOrNull()?.takeIf { it in 1..MAX_DIMENSION } ?: return null
            val height =
                item.text("height").toIntOrNull()?.takeIf { it in 1..MAX_DIMENSION } ?: return null
            val original = mediaUrl(item.text("file_url"))
            val preview =
                mediaUrl(item.text("preview_url"))
                    .ifBlank { mediaUrl(item.text("sample_url")) }
                    .ifBlank { original }
            if (preview.isBlank() || original.isBlank()) return null
            val sample = mediaUrl(item.text("sample_url")).ifBlank { original }
            return Post(
                booruUid = site,
                query = query,
                index = index,
                id = id,
                width = width,
                height = height,
                size = item.text("file_size").toIntOrNull() ?: 0,
                score = item.text("score").toIntOrNull() ?: 0,
                rating = item.text("rating"),
                time = null,
                tags =
                    item
                        .text("tags")
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .distinct()
                        .map { TagBase(it, -1) },
                preview = preview,
                sample = sample,
                medium = sample,
                origin = original,
                source = item.text("source").takeIf { it.isNotBlank() },
                uploader = User(name = item.text("owner").ifBlank { item.text("uploader") }),
            )
        }

        private fun JsonObject.text(key: String) =
            (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
    }
}

private const val MAX_PAGE_SIZE = 1000
private const val MAX_DIMENSION = 100000
private val PRIVATE_MEDIA_PARAMETERS =
    setOf("api_key", "user_id", "token", "access_token", "password", "auth", "key")
