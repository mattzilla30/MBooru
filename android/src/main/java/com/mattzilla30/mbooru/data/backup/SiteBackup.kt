// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.backup

import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.core.model.BooruEngine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.InputStream

/** Portable configuration only. Account tokens and HTTP credentials never leave the device. */
@Serializable
private data class SiteBackup(val format: String = "MBooru-sites", val version: Int = 1, val sites: List<Booru>)

object SiteBackups {
    const val MAX_BYTES = 2 * 1024 * 1024
    const val MAX_SITES = 500
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun withoutCredentials(site: Booru): Booru = site.copy(uid = 0, user = null, auth = null)

    fun encode(sites: List<Booru>): String {
        validate(sites)
        return json.encodeToString(SiteBackup(sites = sites.map(::withoutCredentials)))
    }

    fun decode(input: InputStream): List<Booru> {
        val bytes = input.takeBoundedBytes()
        val text = bytes.toString(Charsets.UTF_8)
        val element = json.parseToJsonElement(text)
        val sites = if (element is JsonArray) json.decodeFromString<List<Booru>>(text) else {
            val backup = json.decodeFromString<SiteBackup>(text)
            require(backup.format == "MBooru-sites" && backup.version == 1) { "Unsupported backup format" }
            backup.sites
        }
        validate(sites)
        return sites.map(::withoutCredentials)
    }

    private fun validate(sites: List<Booru>) {
        require(sites.size <= MAX_SITES) { "Too many sites" }
        require(sites.map { it.scheme.lowercase() to it.host.lowercase() }.toSet().size == sites.size) { "Duplicate sites" }
        sites.forEach {
            require(it.name.isNotBlank() && it.name.length <= 256) { "Invalid site name" }
            val url = "${it.scheme}://${it.host}/".toHttpUrlOrNull()
            require(url != null && url.username.isEmpty() && url.password.isEmpty() &&
                url.host.equals(it.host, ignoreCase = true) && it.scheme in setOf("http", "https")) { "Invalid site address" }
            require(BooruEngine.fromLegacyId(it.type) != BooruEngine.UNKNOWN) { "Unsupported engine" }
            require(it.path.orEmpty().length <= 2048 && it.blacklists.size <= 1000 &&
                it.blacklists.all { tag -> tag.length <= 1024 }) { "Invalid site configuration" }
        }
    }

    private fun InputStream.takeBoundedBytes(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Backup is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
