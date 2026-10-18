package com.mattzilla30.mbooru.data.database

import com.mattzilla30.mbooru.app.App
import com.mattzilla30.mbooru.data.database.dao.NextDao
import com.mattzilla30.mbooru.data.model.common.Next
import org.koin.android.ext.android.inject

object NextManager {

    private val nextDao by App.app.inject<NextDao>()

    fun create(next: Next) {
        nextDao.insert(next)
    }

    fun delete(booruUid: Long, query: String) {
        nextDao.delete(booruUid, query)
    }

    fun getNext(booruUid: Long, query: String): Next? {
        return nextDao.getNext(booruUid, query)
    }
}