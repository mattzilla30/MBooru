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

import android.database.sqlite.SQLiteCantOpenDatabaseException
import com.mattzilla30.mbooru.data.database.dao.TagFilterDao
import com.mattzilla30.mbooru.data.model.common.TagFilter
import java.io.IOException
import java.sql.SQLException

class TagFilterManager(private val tagFilterDao: TagFilterDao) {

    @Throws(SQLException::class)
    suspend fun createTagFilter(tag: TagFilter): TagFilter {
        tag.uid = 0
        tag.uid = tagFilterDao.insert(tag)
        return tag
    }

    @Throws(SQLException::class)
    suspend fun updateTagFilter(tag: TagFilter): Boolean = tagFilterDao.update(tag) == 1

    @Throws(IOException::class)
    suspend fun getTagFilterByBooruUid(booruUid: Long): List<TagFilter>? = try {
        tagFilterDao.getTagByBooruUid(booruUid)
    } catch (ex: SQLiteCantOpenDatabaseException) {
        throw IOException(ex)
    } catch (ex: SQLException) {
        ex.printStackTrace()
        null
    }

    @Throws(IOException::class)
    suspend fun getTagFilterByTagFilterUid(uid: Long): TagFilter? = try {
        tagFilterDao.getTagByTagUid(uid)
    } catch (ex: SQLiteCantOpenDatabaseException) {
        throw IOException(ex)
    } catch (ex: SQLException) {
        ex.printStackTrace()
        null
    }

    @Throws(SQLException::class)
    suspend fun deleteTagFilter(tag: TagFilter) {
        tagFilterDao.delete(tag.uid)
    }

    @Throws(SQLException::class)
    suspend fun deleteTagsFilter(tags: List<TagFilter>) {
        tagFilterDao.delete(tags)
    }

    @Throws(SQLException::class)
    suspend fun deleteAll() = tagFilterDao.deleteAll()

    @Throws(IOException::class)
    suspend fun getAllTagFilters(): List<TagFilter>? = try {
        tagFilterDao.getAll()
    } catch (ex: SQLiteCantOpenDatabaseException) {
        throw IOException(ex)
    } catch (ex: SQLException) {
        ex.printStackTrace()
        null
    }
}