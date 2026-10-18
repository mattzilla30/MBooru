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

package com.mattzilla30.mbooru.data.database

import com.mattzilla30.mbooru.data.database.dao.MuzeiDao
import com.mattzilla30.mbooru.data.model.common.Muzei
import java.io.IOException
import java.sql.SQLException

/**
 * Manage [Muzei] table
 * */
class MuzeiManager(private val muzeiDao: MuzeiDao) {
    /**
     * Create a [Muzei]
     * */
    @Throws(SQLException::class)
    suspend fun createMuzei(muzei: Muzei): Muzei {
        muzei.uid = 0L
        muzei.uid = muzeiDao.insert(muzei)
        return muzei
    }
    /**
     * Delete a [Muzei]
     * */
    @Throws(SQLException::class)
    suspend fun deleteMuzei(muzei: Muzei) {
        muzeiDao.delete(muzei)
    }
    /**
     * return [Muzei] list by booru uid
     * */
    @Throws(IOException::class)
    suspend fun getMuzeiByBooruUid(booruUid: Long): List<Muzei>? {
        return muzeiDao.getMuzeiByBooruUid(booruUid)
    }

    @Throws(IOException::class)
    suspend fun getMuzeiByUid(uid: Long): Muzei? {
        return muzeiDao.getMuzeiByUid(uid)
    }
}
