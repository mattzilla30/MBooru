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
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru
import java.io.IOException
import java.sql.SQLException

class BooruManager(
    private val booruDao: BooruDao,
    private val credentials: com.mattzilla30.mbooru.data.credentials.CredentialRepository,
    private val cookies: com.mattzilla30.mbooru.okhttp.AndroidCookieJar,
) {

    @Throws(SQLException::class)
    suspend fun createBooru(booru: Booru): Booru {
        booru.uid = 0
        val uid = booruDao.createUnique(booru)
        if (uid >= 0) {
            booru.uid = uid
        }
        return booru
    }

    @Throws(SQLException::class)
    suspend fun createBoorus(boorus: List<Booru>) {
        booruDao.insert(boorus)
    }

    @Throws(SQLException::class)
    suspend fun updateBooru(booru: Booru): Boolean =
        kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable
        ) {
            val before = booruDao.getBooruByUid(booru.uid)
            val previous = before?.user?.credentialReference
            val previousAuth = before?.auth?.credentialReference
            val updated = booruDao.update(booru) == 1
            if (
                updated &&
                    previous != null &&
                    previous != booru.user?.credentialReference &&
                    booruDao.credentialReferences(previous) == 0
            ) {
                credentials.remove(previous)
            }
            if (
                updated &&
                    previousAuth != null &&
                    previousAuth != booru.auth?.credentialReference &&
                    booruDao.credentialReferences(previousAuth) == 0
            )
                credentials.remove(previousAuth)
            if (
                updated &&
                    (before?.user != null || before?.auth != null) &&
                    booru.user == null &&
                    booru.auth == null
            )
                cookies.remove(
                    okhttp3.HttpUrl.Builder().scheme(before!!.scheme).host(before.host).build()
                )
            updated
        }

    @Throws(IOException::class)
    suspend fun getBooru(scheme: String, host: String): Booru? =
        try {
            booruDao[scheme, host]
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            null
        }

    @Throws(IOException::class)
    suspend fun getBooruByUid(uid: Long): Booru? =
        try {
            booruDao.getBooruByUid(uid)
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            null
        }

    @Throws(SQLException::class)
    suspend fun deleteBooru(uid: Long): Boolean =
        kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.NonCancellable
        ) {
            val site = booruDao.getBooruByUid(uid) ?: return@withContext false
            if (booruDao.delete(uid) != 1) return@withContext false
            listOfNotNull(site.user?.credentialReference, site.auth?.credentialReference).forEach {
                if (booruDao.credentialReferences(it) == 0) credentials.remove(it)
            }
            cookies.remove(okhttp3.HttpUrl.Builder().scheme(site.scheme).host(site.host).build())
            true
        }

    @Throws(SQLException::class) suspend fun deleteAll() = booruDao.deleteAll()

    /** return [Boolean] */
    @Throws(IOException::class)
    suspend fun isNotEmpty(): Boolean =
        try {
            booruDao.isNotEmpty()
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            false
        }

    /** return [Booru] list */
    @Throws(IOException::class)
    suspend fun getAllBoorus(): MutableList<Booru>? =
        try {
            booruDao.getAll()
        } catch (ex: SQLiteCantOpenDatabaseException) {
            throw IOException(ex)
        } catch (ex: SQLException) {
            null
        }
}
