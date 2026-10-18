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

import androidx.room.*
import com.mattzilla30.mbooru.data.model.common.History
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(history: History): Long

    @Query("SELECT * FROM `history` WHERE `booru_uid` = :booruUid ORDER BY `uid` DESC")
    suspend fun getHistoryByBooruUid(booruUid: Long): List<History>?

    @Query("SELECT * FROM `history` WHERE `uid` = :uid")
    suspend fun getHistoryByUid(uid: Long): History?

    @Delete suspend fun delete(history: History): Int

    @Query("DELETE FROM `history` WHERE `uid` = :uid") suspend fun deleteByUid(uid: Long): Int

    @Query("DELETE FROM `history` WHERE `booru_uid` = :booruUid")
    suspend fun deleteByBooruUid(booruUid: Long): Int

    @Query("SELECT * FROM `history` WHERE `booru_uid` = :booruUid ORDER BY `uid` DESC")
    fun observeHistory(booruUid: Long): Flow<List<History>>
}
