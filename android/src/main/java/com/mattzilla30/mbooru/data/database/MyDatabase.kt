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

import android.content.Context
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import com.mattzilla30.mbooru.app.Values.DB_FILE_NAME
import com.mattzilla30.mbooru.data.model.common.*
import com.mattzilla30.mbooru.data.database.dao.*

@Database(
    entities = [
        (Booru::class), (Post::class), (TagFilter::class),
        (Muzei::class), (History::class), (Next::class), (DownloadRecord::class)
    ],
    version = 10,
    autoMigrations = [
        AutoMigration(from = 9, to = 10),
        AutoMigration (
            from = 7,
            to = 8
        ),
        AutoMigration (
            from = 8,
            to = 9,
            spec = MyMigration.DeleteCookiesMigrationSpec::class
        )
    ],
    exportSchema = true)
@TypeConverters(MyConverters::class)
abstract class MyDatabase : RoomDatabase() {

    companion object {
        fun create(context: Context, credentials: CredentialRepository): MyDatabase =
            Room.databaseBuilder(context, MyDatabase::class.java, DB_FILE_NAME)
                .addTypeConverter(MyConverters(credentials))
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Upgrade plaintext legacy columns atomically before Room reads any account.
                        db.beginTransaction()
                        try {
                            db.query("SELECT uid, user, auth FROM boorus").use { cursor ->
                                while (cursor.moveToNext()) {
                                    val uid = cursor.getLong(0)
                                    val user = if (cursor.isNull(1)) null else cursor.getString(1)
                                    val auth = if (cursor.isNull(2)) null else cursor.getString(2)
                                    val userRef = user?.let { if (credentials.isReference(it)) it else credentials.store(it) }
                                    val authRef = auth?.let { if (credentials.isReference(it)) it else credentials.store(it) }
                                    if (user != userRef || auth != authRef) db.execSQL(
                                        "UPDATE boorus SET user = ?, auth = ? WHERE uid = ?", arrayOf(userRef, authRef, uid))
                                }
                            }
                            db.setTransactionSuccessful()
                        } finally { db.endTransaction() }
                    }
                })
                .addMigrations(
                    MyMigration(1, 2),
                    MyMigration(2, 3),
                    MyMigration(3, 4),
                    MyMigration(4, 5),
                    MyMigration(5, 6),
                    MyMigration(6, 7)
                )
                .setQueryExecutor(Dispatchers.IO.asExecutor())
                .setTransactionExecutor(Dispatchers.IO.asExecutor())
                .build()
    }

    abstract fun downloadDao(): DownloadDao
    abstract fun booruDao(): BooruDao
    abstract fun postDao(): PostDao

    abstract fun tagFilterDao(): TagFilterDao
    abstract fun muzeiDao(): MuzeiDao
    abstract fun historyDao(): HistoryDao
    abstract fun nextDao(): NextDao
}
