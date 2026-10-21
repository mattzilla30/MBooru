// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.api.rule34

import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.api.defaultJson
import com.mattzilla30.mbooru.data.model.common.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Rule34's authenticated tag DAPI is separate from the generic Gelbooru transport. */
object Rule34Tags {
    fun requestUrl(action: ActionTag, page: Int): HttpUrl {
        require(page >= 0 && action.limit in 1..MAX_TAGS)
        val credentials = Rule34Credentials.from(action.booru.user)
        return "https://api.rule34.xxx/index.php"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", "dapi")
            .addQueryParameter("s", "tag")
            .addQueryParameter("q", "index")
            .addQueryParameter("json", "1")
            .addQueryParameter("pid", page.toString())
            .addQueryParameter("limit", action.limit.toString())
            .addQueryParameter("name_pattern", action.query)
            .addQueryParameter("orderby", action.order)
            .addQueryParameter("user_id", credentials.userId)
            .addQueryParameter("api_key", credentials.key)
            .build()
    }

    fun parse(body: String?): List<Tag> {
        val text = body?.trim()?.removePrefix("\uFEFF")?.trim().orEmpty()
        if (text.isEmpty() || text.contains("<!DOCTYPE", true) || text.contains("<!ENTITY", true))
            throw Rule34Exception(
                "Rule34 returned an invalid tag list. Test the connection and retry."
            )
        val tags =
            try {
                if (text.startsWith("<?xml") || text.startsWith("<tags")) {
                    tagXml.decodeFromString(TagList.serializer(), text).tags.map { it.toTag() }
                } else {
                    parseJson(text)
                }
            } catch (_: IllegalArgumentException) {
                throw Rule34Exception(
                    "Rule34 returned an unsupported tag response. Test the connection or open the site in a browser."
                )
            }
        validateTags(tags)
        return tags.distinctBy { it.id }
    }

    private fun parseJson(text: String): List<Tag> {
        val element = defaultJson.parseToJsonElement(text)
        val array =
            (element as? JsonArray)
                ?: ((element as? JsonObject)?.get("tag") as? JsonArray)
                ?: throw IllegalArgumentException("Not a tag list")
        return array.map { element ->
            val item = element as? JsonObject ?: throw IllegalArgumentException("Invalid tag")
            TagRecord(
                    id = item.number("id"),
                    name = (item["name"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                    count = item.number("count"),
                    type = item.number("type"),
                )
                .toTag()
        }
    }

    private fun validateTags(tags: List<Tag>) {
        val invalid = tags.any { it.id <= 0 || it.name.isBlank() || it.count < 0 }
        if (tags.size > MAX_TAGS || invalid)
            throw Rule34Exception("Rule34 returned invalid tag records. Please retry.")
    }

    private fun JsonObject.number(key: String) =
        (get(key) as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid tag field")
}

@Serializable
@SerialName("tags")
private data class TagList(
    val type: String = "",
    @SerialName("tag") val tags: List<TagRecord> = emptyList(),
)

@Serializable
@SerialName("tag")
private data class TagRecord(val id: Int, val name: String, val count: Int, val type: Int) {
    fun toTag() = Tag(Values.BOORU_TYPE_RULE34, id, name, type, count)
}

private const val MAX_TAGS = 1000
private val tagXml = XML {
    defaultPolicy {
        unknownChildHandler = XmlConfig.IGNORING_UNKNOWN_CHILD_HANDLER
    }
}
