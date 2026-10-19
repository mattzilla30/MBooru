package com.mattzilla30.mbooru.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.SavedPost
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SavedPostDao {
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM saved_posts WHERE is_saved = 1 ORDER BY saved_at DESC")
    abstract fun paging(): PagingSource<Int, Post>

    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM saved_posts WHERE is_saved = 1 ORDER BY saved_at DESC")
    abstract fun observe(): Flow<List<Post>>

    @Query(
        "SELECT EXISTS(SELECT 1 FROM saved_posts WHERE booru_uid = :site AND id = :id AND is_saved = 1)"
    )
    abstract suspend fun contains(site: Long, id: Int): Boolean

    @androidx.room.Upsert abstract suspend fun insert(entry: SavedPost)

    @Query("UPDATE saved_posts SET is_saved = 0 WHERE booru_uid = :site AND id = :id")
    abstract suspend fun delete(site: Long, id: Int)

    @Transaction
    open suspend fun toggle(post: Post): Boolean {
        if (contains(post.booruUid, post.id)) {
            delete(post.booruUid, post.id)
            return false
        }
        insert(SavedPost(post.copy(uid = 0), System.currentTimeMillis()))
        return true
    }
}
