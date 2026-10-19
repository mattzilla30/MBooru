// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.mattzilla30.mbooru.data.model.common.CollectionMembership
import com.mattzilla30.mbooru.data.model.common.LibraryArtwork
import com.mattzilla30.mbooru.data.model.common.LocalLike
import com.mattzilla30.mbooru.data.model.common.NamedCollection
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.SavedPost
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Dao
@Suppress("TooManyFunctions")
abstract class LibraryDao {
    @Query(
        "SELECT EXISTS(SELECT 1 FROM saved_posts WHERE booru_uid=:site AND id=:id AND is_saved=1)"
    )
    abstract fun observeSaved(site: Long, id: Int): Flow<Boolean>

    @Query("UPDATE saved_posts SET is_saved=:saved WHERE booru_uid=:site AND id=:id")
    protected abstract suspend fun setSaved(site: Long, id: Int, saved: Boolean)

    @Transaction
    open suspend fun toggleSaved(post: Post) {
        val wasSaved = artwork(post.booruUid, post.id)?.isSaved == true
        retain(post)
        setSaved(post.booruUid, post.id, !wasSaved)
    }

    @Transaction
    open suspend fun createWithArtwork(name: String, post: Post?): Long {
        val uid = createCollection(name)
        post?.let { setMembership(it, uid, true) }
        return uid
    }

    @Query("SELECT EXISTS(SELECT 1 FROM local_likes WHERE booru_uid=:site AND id=:id)")
    abstract fun observeLike(site: Long, id: Int): Flow<Boolean>

    @Query("SELECT collection_uid FROM collection_memberships WHERE booru_uid=:site AND id=:id")
    abstract fun observeCollectionsFor(site: Long, id: Int): Flow<List<Long>>

    @Query(
        "SELECT s.*, EXISTS(SELECT 1 FROM local_likes l WHERE l.booru_uid=s.booru_uid " +
            "AND l.id=s.id) AS liked FROM saved_posts s ORDER BY saved_at DESC"
    )
    abstract fun observeArtwork(): Flow<List<LibraryArtwork>>

    @Query("SELECT * FROM named_collections ORDER BY created_at, uid")
    abstract fun observeCollections(): Flow<List<NamedCollection>>

    @Query("SELECT * FROM collection_memberships ORDER BY added_at DESC")
    abstract fun observeMemberships(): Flow<List<CollectionMembership>>

    @Query("SELECT * FROM saved_posts WHERE booru_uid=:site AND id=:id")
    abstract suspend fun artwork(site: Long, id: Int): SavedPost?

    @Query("SELECT * FROM named_collections ORDER BY created_at, uid")
    abstract suspend fun collections(): List<NamedCollection>

    @Query("SELECT * FROM collection_memberships")
    abstract suspend fun memberships(): List<CollectionMembership>

    @Query("SELECT * FROM saved_posts") abstract suspend fun snapshots(): List<SavedPost>

    @Query("SELECT * FROM local_likes") abstract suspend fun likes(): List<LocalLike>

    @Query("SELECT EXISTS(SELECT 1 FROM local_likes WHERE booru_uid=:site AND id=:id)")
    abstract suspend fun isLiked(site: Long, id: Int): Boolean

    @Upsert abstract suspend fun upsertArtwork(artwork: SavedPost)

    @Insert(onConflict = OnConflictStrategy.IGNORE) abstract suspend fun insertLike(like: LocalLike)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMembership(membership: CollectionMembership)

    @Insert protected abstract suspend fun insertCollection(collection: NamedCollection): Long

    @Update protected abstract suspend fun updateCollection(collection: NamedCollection)

    @Query("DELETE FROM named_collections WHERE uid=:uid")
    abstract suspend fun deleteCollection(uid: Long)

    @Query("DELETE FROM local_likes WHERE booru_uid=:site AND id=:id")
    abstract suspend fun deleteLike(site: Long, id: Int)

    @Query(
        "DELETE FROM collection_memberships WHERE collection_uid=:collection AND booru_uid=:site AND id=:id"
    )
    abstract suspend fun removeMembership(collection: Long, site: Long, id: Int)

    @Transaction
    open suspend fun retain(post: Post): SavedPost {
        val old = artwork(post.booruUid, post.id)
        val entry =
            SavedPost(
                post.copy(uid = 0, query = ""),
                old?.savedAt ?: System.currentTimeMillis(),
                old?.isSaved ?: false,
            )
        upsertArtwork(entry)
        return entry
    }

    @Transaction
    open suspend fun toggleLike(post: Post): Boolean {
        retain(post)
        val liked = isLiked(post.booruUid, post.id)
        if (liked) deleteLike(post.booruUid, post.id)
        else insertLike(LocalLike(post.booruUid, post.id, System.currentTimeMillis()))
        return !liked
    }

    @Transaction
    open suspend fun setMembership(post: Post, collection: Long, add: Boolean) {
        check(collections().any { it.uid == collection }) { "Collection no longer exists" }
        if (add) {
            retain(post)
            insertMembership(
                CollectionMembership(collection, post.booruUid, post.id, System.currentTimeMillis())
            )
        } else removeMembership(collection, post.booruUid, post.id)
    }

    @Transaction
    open suspend fun createCollection(input: String): Long {
        val name = validateCollectionName(input)
        check(collections().none { it.nameKey == name.lowercase(Locale.ROOT) }) {
            "A collection with this name already exists"
        }
        return insertCollection(
            NamedCollection(
                name = name,
                nameKey = name.lowercase(Locale.ROOT),
                createdAt = System.currentTimeMillis(),
            )
        )
    }

    @Transaction
    open suspend fun renameCollection(uid: Long, input: String) {
        val name = validateCollectionName(input)
        val list = collections()
        check(list.none { it.uid != uid && it.nameKey == name.lowercase(Locale.ROOT) }) {
            "A collection with this name already exists"
        }
        val old = checkNotNull(list.firstOrNull { it.uid == uid }) { "Collection no longer exists" }
        updateCollection(old.copy(name = name, nameKey = name.lowercase(Locale.ROOT)))
    }
}

fun validateCollectionName(input: String): String {
    val name = input.trim()
    require(name.isNotEmpty()) { "Enter a collection name" }
    require(name.length <= MAX_COLLECTION_NAME_LENGTH) { "Use at most 40 characters" }
    require(name.lowercase(Locale.ROOT) !in setOf("all saved")) {
        "This collection name is reserved"
    }
    require(name.none { it.isISOControl() }) {
        "Collection names cannot contain control characters"
    }
    return name
}

private const val MAX_COLLECTION_NAME_LENGTH = 40
