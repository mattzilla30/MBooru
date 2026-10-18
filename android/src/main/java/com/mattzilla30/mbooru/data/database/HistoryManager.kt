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

import com.mattzilla30.mbooru.data.database.dao.HistoryDao
import com.mattzilla30.mbooru.data.model.common.History
import java.io.IOException
import java.sql.SQLException

class HistoryManager(private val historyDao: HistoryDao) {
    /**
     * Create a [History]
     * */
    @Throws(SQLException::class)
    suspend fun createHistory(history: History): History {
        history.uid = 0L
        history.uid = historyDao.insert(history)
        return history
    }
    /**
     * Delete a [History]
     * */
    @Throws(SQLException::class)
    suspend fun deleteHistory(history: History) {
        historyDao.delete(history)
    }
    /**
     * return [History] list by booru uid
     * */
    @Throws(IOException::class)
    suspend fun getHistoryByBooruUid(booruUid: Long): List<History>? {
        return historyDao.getHistoryByBooruUid(booruUid)
    }

    @Throws(IOException::class)
    suspend fun getHistoryByUid(uid: Long): History? {
        return historyDao.getHistoryByUid(uid)
    }
}
