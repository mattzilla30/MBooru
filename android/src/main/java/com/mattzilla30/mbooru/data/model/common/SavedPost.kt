package com.mattzilla30.mbooru.data.model.common

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey

/** Independent snapshot: refreshing a timeline must never erase a local favorite. */
@Entity(
    tableName = "saved_posts",
    primaryKeys = ["booru_uid", "id"],
    foreignKeys =
        [
            ForeignKey(
                entity = Booru::class,
                parentColumns = ["uid"],
                childColumns = ["booru_uid"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
@kotlinx.serialization.Serializable
data class SavedPost(
    @Embedded val post: Post,
    @ColumnInfo(name = "saved_at") val savedAt: Long,
    @ColumnInfo(name = "is_saved", defaultValue = "1") val isSaved: Boolean = true,
)
