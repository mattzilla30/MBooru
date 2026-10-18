/*
 * Copyright (C) 2026 mattzilla30 <mattzilla30@gmail.com> (MBooru modifications)
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
import com.mattzilla30.mbooru.data.model.common.Booru

@Dao
abstract class BooruDao {

    @Query("SELECT * FROM `boorus` WHERE `scheme` = :scheme AND `host` = :host")
    abstract suspend operator fun get(scheme: String, host: String): Booru?

    @Query("SELECT * FROM `boorus` WHERE `uid` = :uid")
    abstract suspend fun getBooruByUid(uid: Long): Booru?


    @Query("SELECT * FROM `boorus` ORDER BY `uid` ASC")
    abstract suspend fun getAll(): MutableList<Booru>


    @Query("SELECT 1 FROM `boorus` LIMIT 1")
    abstract suspend fun isNotEmpty(): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertNew(booru: Booru): Long

    /** Updating a natural-key match must not cascade-delete its history and filters. */
    @Transaction
    open suspend fun insert(booru: Booru): Long {
        val existing = get(booru.scheme, booru.host)
        if (existing != null) {
            check(booru.uid == 0L || booru.uid == existing.uid) {
                "The site address already belongs to another site"
            }
            update(booru.copy(uid = existing.uid))
            return existing.uid
        }
        return insertNew(booru)
    }

    @Transaction
    open suspend fun insert(boorus: List<Booru>) {
        boorus.forEach { insert(it) }
    }

    /** Validated configuration import preserves local login and applies atomically. */
    @Transaction
    open suspend fun importSites(sites: List<Booru>) {
        sites.forEach { site ->
            val existing = get(site.scheme, site.host)
            insert(site.copy(uid = existing?.uid ?: 0, user = existing?.user, auth = existing?.auth))
        }
    }

    /** IGNORE preserves the UID, user login, filters and dependent rows of existing sites. */
    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMissing(boorus: List<Booru>)

    @Query("SELECT COUNT(*) FROM boorus WHERE user = :reference OR auth = :reference")
    abstract suspend fun credentialReferences(reference: String): Int

    @Update
    abstract suspend fun update(booru: Booru): Int

    @Query("DELETE FROM `boorus` WHERE `uid` = :uid")
    abstract suspend fun delete(uid: Long): Int

    /** Retire only the original factory sample, never an unrelated custom site. */
    @Query("DELETE FROM `boorus` WHERE `name` = 'Sample' AND `scheme` = 'https' " +
        "AND `host` = 'moe.fiepi.com' AND `type` = 1")
    abstract suspend fun removeLegacySample(): Int

    @Query("DELETE FROM `boorus`")
    abstract suspend fun deleteAll(): Int
    @Query("SELECT * FROM `boorus` ORDER BY `uid` ASC")
    abstract fun observeAll(): Flow<List<Booru>>

    @Query("SELECT * FROM `boorus` WHERE `uid` = :uid")
    abstract fun observeByUid(uid: Long): Flow<Booru?>
}
