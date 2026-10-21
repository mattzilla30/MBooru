// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.data.model.common

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_likes",
    primaryKeys = ["booru_uid", "id"],
    foreignKeys =
        [
            ForeignKey(
                entity = SavedPost::class,
                parentColumns = ["booru_uid", "id"],
                childColumns = ["booru_uid", "id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
@kotlinx.serialization.Serializable
data class LocalLike(
    @ColumnInfo(name = "booru_uid") val site: Long,
    val id: Int,
    @ColumnInfo(name = "liked_at") val likedAt: Long,
)

@Entity(tableName = "named_collections", indices = [Index(value = ["name_key"], unique = true)])
@kotlinx.serialization.Serializable
data class NamedCollection(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val name: String,
    @ColumnInfo(name = "name_key") val nameKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "collection_memberships",
    primaryKeys = ["collection_uid", "booru_uid", "id"],
    indices = [Index(value = ["booru_uid", "id"])],
    foreignKeys =
        [
            ForeignKey(
                entity = NamedCollection::class,
                parentColumns = ["uid"],
                childColumns = ["collection_uid"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = SavedPost::class,
                parentColumns = ["booru_uid", "id"],
                childColumns = ["booru_uid", "id"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
@kotlinx.serialization.Serializable
data class CollectionMembership(
    @ColumnInfo(name = "collection_uid") val collection: Long,
    @ColumnInfo(name = "booru_uid") val site: Long,
    val id: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

data class LibraryArtwork(@Embedded val saved: SavedPost, val liked: Boolean)
