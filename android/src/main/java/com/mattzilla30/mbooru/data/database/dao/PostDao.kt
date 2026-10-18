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

package com.mattzilla30.mbooru.data.database.dao

import androidx.paging.PagingSource
import androidx.room.*
import com.mattzilla30.mbooru.data.model.common.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(posts: List<Post>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(post: Post)

    @Query(
        "SELECT * FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query ORDER BY `index` ASC"
    )
    fun getPosts(booruUid: Long, query: String): PagingSource<Int, Post>

    @Query(
        "SELECT * FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query AND `index` = :index"
    )
    suspend fun getPost(booruUid: Long, query: String, index: Int): Post?

    @Query(
        "SELECT * FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query ORDER BY `index` ASC LIMIT :limit"
    )
    suspend fun getPostsRaw(booruUid: Long, query: String, limit: Int): List<Post>

    @Query(
        "SELECT * FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query ORDER BY `index` ASC LIMIT 1"
    )
    suspend fun getFirstPostRaw(booruUid: Long, query: String): Post?

    @Query(
        "SELECT * FROM `posts` WHERE `booru_uid` = :booruUid AND `id` = :postId ORDER BY `index` ASC LIMIT 1"
    )
    fun observeFirstPost(booruUid: Long, postId: Int): Flow<Post?>

    @Query("DELETE FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query")
    suspend fun deletePosts(booruUid: Long, query: String)

    @Query("DELETE FROM `posts` WHERE `booru_uid` = :booruUid")
    suspend fun deletePosts(booruUid: Long)

    @Query(
        "SELECT MAX(`index`) + 1 FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query"
    )
    suspend fun getNextIndex(booruUid: Long, query: String): Int

    @Delete suspend fun deletePost(post: Post)

    @Update suspend fun updatePost(post: Post)

    @Query("DELETE FROM `posts` WHERE `booru_uid` = :booruUid AND `query` = :query AND id = :id")
    suspend fun deletePost(booruUid: Long, query: String, id: Int)

    @Query("DELETE FROM `posts`") suspend fun deleteAll()

    @Query(
        "UPDATE `posts` SET `is_favored` = :isFavored WHERE `booru_uid` = :booruUid AND `id` = :postId"
    )
    suspend fun updateFav(booruUid: Long, postId: Int, isFavored: Boolean)
}
