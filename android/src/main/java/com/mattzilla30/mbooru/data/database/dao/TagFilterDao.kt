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

import kotlinx.coroutines.flow.Flow
import androidx.room.*
import com.mattzilla30.mbooru.data.model.common.TagFilter

@Dao
interface TagFilterDao {
    @Query("SELECT * FROM `tags_filter` WHERE `booru_uid` = :booruUid")
    suspend fun getTagByBooruUid(booruUid: Long): List<TagFilter>?

    @Query("SELECT * FROM `tags_filter` WHERE `uid` = :uid")
    suspend fun getTagByTagUid(uid: Long): TagFilter?

    @Query("SELECT * FROM `tags_filter` ORDER BY `uid` ASC")
    suspend fun getAll(): List<TagFilter>?

    @Query("SELECT 1 FROM `tags_filter` LIMIT 1")
    suspend fun isNotEmpty(): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagFilter): Long

    @Update
    suspend fun update(tag: TagFilter): Int

    @Query("DELETE FROM `tags_filter` WHERE `uid` = :uid")
    suspend fun delete(uid: Long): Int

    @Delete
    suspend fun delete(tags: List<TagFilter>)

    @Query("DELETE FROM `tags_filter`")
    suspend fun deleteAll(): Int


    @Query("SELECT * FROM `tags_filter` ORDER BY `name` ASC")
    fun observeTagFilter(): Flow<List<TagFilter>>
}
