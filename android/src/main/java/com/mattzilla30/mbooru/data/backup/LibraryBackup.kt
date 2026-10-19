// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.backup

import androidx.room.withTransaction
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.rule34.Rule34Adapter
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.database.dao.validateCollectionName
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.CollectionMembership
import com.mattzilla30.mbooru.data.model.common.LocalLike
import com.mattzilla30.mbooru.data.model.common.NamedCollection
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.SavedPost
import com.mattzilla30.mbooru.data.model.common.TagBase
import com.mattzilla30.mbooru.data.model.common.User
import java.io.InputStream
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable data class LibrarySite(val sourceUid: Long, val site: Booru)

@Serializable
data class LibraryBackup(
    val format: String = "MBooru-library",
    val version: Int = 1,
    val sites: List<LibrarySite>,
    val artwork: List<SavedPost>,
    val likes: List<LocalLike>,
    val collections: List<NamedCollection>,
    val memberships: List<CollectionMembership>,
)

@Serializable
data class M34Artwork(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val tags: String = "",
    val score: Int = 0,
    val preview: String = "",
    val original: String = "",
    val rating: String = "unknown",
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class M34Library(
    val format: String,
    val version: Int,
    val artwork: List<M34Artwork>,
    val likes: List<String> = emptyList(),
    val collections: Map<String, List<String>> = emptyMap(),
    val unresolvedLikes: List<String> = emptyList(),
)

data class LibraryImportPreview(val backup: LibraryBackup, val description: String)

/**
 * Portable snapshots only. No account fields, vault references or raw preference files are
 * exported.
 */
class LibraryBackups(private val database: MyDatabase) {
    private val library = database.libraryDao()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun export(): String = database.withTransaction {
        val likes = library.likes()
        val members = library.memberships()
        val referenced = (likes.map { it.site to it.id } + members.map { it.site to it.id }).toSet()
        val data =
            LibraryBackup(
                sites =
                    database.booruDao().getAll().map {
                        LibrarySite(it.uid, SiteBackups.withoutCredentials(it))
                    },
                artwork =
                    library
                        .snapshots()
                        .filter { it.isSaved || (it.post.booruUid to it.post.id) in referenced }
                        .map { it.copy(post = portablePost(it.post)) },
                likes = likes,
                collections = library.collections(),
                memberships = members,
            )
        validate(data)
        json.encodeToString(data).also {
            require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Library export exceeds the supported file size"
            }
        }
    }

    fun preview(input: InputStream): LibraryImportPreview {
        val text = readBounded(input)
        val element =
            try {
                json.parseToJsonElement(text).jsonObject
            } catch (_: Exception) {
                throw IllegalArgumentException("Unreadable library backup")
            }
        val backup =
            try {
                if (element["format"]?.jsonPrimitive?.content == "M34-library")
                    convertM34(json.decodeFromString<M34Library>(text))
                else json.decodeFromString<LibraryBackup>(text)
            } catch (_: Exception) {
                throw IllegalArgumentException("Unsupported or malformed library backup")
            }
        validate(backup)
        return LibraryImportPreview(
            backup,
            "${backup.artwork.size} artwork records, ${backup.likes.size} local likes, " +
                "${backup.collections.size} collections. Existing sites and accounts are " +
                "preserved. Matching collection names merge memberships. Artwork with missing " +
                "metadata remains unavailable until its metadata is restored.",
        )
    }

    suspend fun import(preview: LibraryImportPreview): String {
        val data = preview.backup
        validate(data)
        database.withTransaction {
            val sites = mutableMapOf<Long, Long>()
            data.sites.forEach { imported ->
                val candidate = SiteBackups.withoutCredentials(imported.site)
                val existing =
                    database.booruDao()[candidate.scheme, candidate.host]
                        ?: database.booruDao().getAll().firstOrNull {
                            candidate.host == "api.rule34.xxx" && it.host == "rule34.xxx"
                        }
                val uid = existing?.uid ?: database.booruDao().createUnique(candidate)
                sites[imported.sourceUid] = uid
            }
            data.artwork.forEach { imported ->
                val post =
                    portablePost(imported.post)
                        .copy(booruUid = sites.getValue(imported.post.booruUid), uid = 0)
                val previous = library.artwork(post.booruUid, post.id)
                library.upsertArtwork(
                    imported.copy(
                        post = if (previous?.post?.origin.isNullOrBlank()) post else previous.post,
                        savedAt = previous?.savedAt ?: imported.savedAt,
                        isSaved = imported.isSaved || previous?.isSaved == true,
                    )
                )
            }
            data.likes.forEach { library.insertLike(it.copy(site = sites.getValue(it.site))) }
            val groups = mutableMapOf<Long, Long>()
            data.collections.forEach { group ->
                val existing = library.collections().firstOrNull { it.nameKey == group.nameKey }
                groups[group.uid] = existing?.uid ?: library.createCollection(group.name)
            }
            data.memberships.forEach {
                library.insertMembership(
                    it.copy(
                        site = sites.getValue(it.site),
                        collection = groups.getValue(it.collection),
                    )
                )
            }
        }
        return "Imported ${data.artwork.size} artwork records, ${data.likes.size} likes and " +
            "${data.collections.size} collections. Accounts were not imported; reconnect " +
            "sites when needed."
    }

    private fun validate(data: LibraryBackup) {
        require(data.format == "MBooru-library" && data.version == 1) {
            "Unsupported library backup version"
        }
        require(
            data.artwork.size <= MAX_ARTWORK &&
                data.memberships.size <= MAX_MEMBERSHIPS &&
                data.likes.size <= MAX_ARTWORK &&
                data.collections.size <= MAX_COLLECTIONS
        ) {
            "Library backup exceeds supported limits"
        }
        SiteBackups.encode(data.sites.map { it.site })
        require(data.sites.map { it.sourceUid }.toSet().size == data.sites.size) {
            "Duplicate site identities"
        }
        val sites = data.sites.map { it.sourceUid }.toSet()
        val keys = data.artwork.map { it.post.booruUid to it.post.id }.toSet()
        require(keys.size == data.artwork.size) { "Duplicate artwork identities" }
        data.artwork.forEach {
            require(
                it.post.booruUid in sites &&
                    it.post.id > 0 &&
                    it.post.width in 0..MAX_DIMENSION &&
                    it.post.height in 0..MAX_DIMENSION
            ) {
                "Invalid artwork metadata"
            }
            require(
                it.post.tags.size <= MAX_TAGS &&
                    it.post.tags.all { tag -> tag.name.length <= MAX_TAG_LENGTH }
            ) {
                "Invalid artwork tags"
            }
            require(
                listOf(it.post.preview, it.post.sample, it.post.medium, it.post.origin).all { url ->
                    url.isEmpty() || safeMedia(url) == url
                }
            ) {
                "Unsafe artwork URL"
            }
        }
        data.collections.forEach {
            require(validateCollectionName(it.name).lowercase(Locale.ROOT) == it.nameKey) {
                "Invalid collection name"
            }
        }
        require(
            data.collections.map { it.nameKey }.toSet().size == data.collections.size &&
                data.collections.map { it.uid }.toSet().size == data.collections.size
        ) {
            "Duplicate collections"
        }
        val groups = data.collections.map { it.uid }.toSet()
        require(data.likes.all { (it.site to it.id) in keys }) { "Like refers to missing artwork" }
        require(data.memberships.all { it.collection in groups && (it.site to it.id) in keys }) {
            "Membership refers to missing artwork"
        }
    }

    private fun convertM34(data: M34Library): LibraryBackup {
        require(data.version == 1 && data.format == "M34-library")
        require(data.artwork.size <= MAX_ARTWORK && data.collections.size <= MAX_COLLECTIONS)
        val originals = data.artwork.associateBy { remoteId(it.id) }
        require(originals.size == data.artwork.size)
        val liked = (data.likes + data.unresolvedLikes).map(::remoteId).toSet()
        val members = data.collections.values.flatten().map(::remoteId)
        val ids = originals.keys + liked + members
        require(ids.size <= MAX_ARTWORK)
        val posts = ids.map { id -> m34Artwork(id, originals[id]) }
        val collectionMap =
            data.collections.entries.associate { validateCollectionName(it.key) to it.value }
        require(collectionMap.size == data.collections.size)
        val groups =
            collectionMap.keys.mapIndexed { index, name ->
                val valid = validateCollectionName(name)
                NamedCollection(
                    index.toLong() + 1,
                    valid,
                    valid.lowercase(Locale.ROOT),
                    System.currentTimeMillis(),
                )
            }
        val membership =
            groups
                .flatMap { group ->
                    collectionMap.getValue(group.name).map {
                        CollectionMembership(group.uid, 1, remoteId(it), System.currentTimeMillis())
                    }
                }
                .distinctBy { Triple(it.collection, it.site, it.id) }
        return LibraryBackup(
            sites =
                listOf(
                    LibrarySite(
                        1,
                        Booru(
                            name = "Rule34",
                            host = "api.rule34.xxx",
                            type = Values.BOORU_TYPE_RULE34,
                        ),
                    )
                ),
            artwork = posts,
            likes = liked.map { LocalLike(1, it, System.currentTimeMillis()) },
            collections = groups,
            memberships = membership,
        )
    }

    private fun m34Artwork(id: Int, original: M34Artwork?): SavedPost {
        val preview = original?.preview?.let(Rule34Adapter::mediaUrl).orEmpty()
        val origin = original?.original?.let(Rule34Adapter::mediaUrl).orEmpty()
        val post =
            Post(
                booruUid = 1,
                id = id,
                width = original?.width ?: 0,
                height = original?.height ?: 0,
                size = 0,
                score = original?.score ?: 0,
                rating = original?.rating ?: "unknown",
                time = null,
                tags =
                    original
                        ?.tags
                        .orEmpty()
                        .split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                        .map { TagBase(it) },
                preview = preview.ifBlank { origin },
                sample = origin.ifBlank { preview },
                medium = origin.ifBlank { preview },
                origin = origin,
                source = null,
                uploader = User(name = original?.artist.orEmpty()),
            )
        return SavedPost(post, System.currentTimeMillis(), original != null)
    }

    private fun remoteId(value: String): Int =
        value.removePrefix("r34-").toIntOrNull()?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Unsupported legacy artwork ID")

    private fun portablePost(post: Post) =
        post.copy(
            uid = 0,
            query = "",
            isFavored = false,
            preview = safeMedia(post.preview),
            sample = safeMedia(post.sample),
            medium = safeMedia(post.medium),
            origin = safeMedia(post.origin),
            uploader = User(id = post.uploader.id, name = post.uploader.name),
            source =
                post.source?.let { source ->
                    if (source.contains("://")) safeMedia(source).takeIf { it.isNotEmpty() }
                    else source.take(MAX_SOURCE_LENGTH)
                },
        )

    private fun safeMedia(value: String): String {
        val url = value.toHttpUrlOrNull() ?: return ""
        val privateAddress = url.username.isNotEmpty() || url.password.isNotEmpty()
        val privateParameters =
            url.queryParameterNames.any {
                it.lowercase(Locale.ROOT) in PRIVATE_URL_PARAMETERS
            }
        return if (url.scheme in setOf("http", "https") && !privateAddress && !privateParameters)
            url.toString()
        else ""
    }

    private fun readBounded(input: InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES) { "Library backup is too large" }
            output.write(buffer, 0, count)
        }
        return output.toString("UTF-8")
    }

    companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
        const val MAX_ARTWORK = 10000
        const val MAX_MEMBERSHIPS = 100000
        const val MAX_COLLECTIONS = 1000
    }
}

private const val MAX_DIMENSION = 100000
private const val MAX_TAGS = 10000
private const val MAX_TAG_LENGTH = 1024
private const val MAX_SOURCE_LENGTH = 8192
private const val READ_BUFFER_SIZE = 8192
private val PRIVATE_URL_PARAMETERS =
    setOf("api_key", "password", "token", "auth", "key", "access_token")
