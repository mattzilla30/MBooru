package com.mattzilla30.mbooru.data.database

import com.mattzilla30.mbooru.data.database.dao.NextDao
import com.mattzilla30.mbooru.data.model.common.Next

class NextManager(private val nextDao: NextDao) {

    suspend fun create(next: Next) {
        nextDao.insert(next)
    }

    suspend fun delete(booruUid: Long, query: String) {
        nextDao.delete(booruUid, query)
    }

    suspend fun getNext(booruUid: Long, query: String): Next? {
        return nextDao.getNext(booruUid, query)
    }
}