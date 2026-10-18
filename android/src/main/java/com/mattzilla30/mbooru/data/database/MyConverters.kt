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

import androidx.room.TypeConverter
import androidx.room.ProvidedTypeConverter
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.model.common.AuthCredential
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.mattzilla30.mbooru.data.model.common.TagBase
import com.mattzilla30.mbooru.data.model.common.User

/**
 * room database TypeConverter
 * */
@ProvidedTypeConverter
class MyConverters(private val credentials: CredentialRepository) {

    @TypeConverter
    fun fromStringToStringList(value: String): List<String> =
        Json.decodeFromString(value)

    @TypeConverter
    fun fromStringListToString(list: List<String>): String =
        Json.encodeToString(list)


    @TypeConverter
    fun fromUserToString(user: User?): String? {
        if (user == null) return null
        val payload = Json.encodeToString(User.serializer(), user)
        // Public uploader metadata remains in Room; only actual account credentials use the vault.
        if (user.token.isEmpty() && user.accessToken.isEmpty() && user.refreshToken.isEmpty()) return payload
        return credentials.store(payload).also { user.credentialReference = it }
    }

    @TypeConverter
    fun fromStringToUser(value: String?): User? {
        if (value == null) return null
        val referenced = credentials.isReference(value)
        val payload = if (referenced) credentials.read(value) ?: return null else value
        return Json.decodeFromString(User.serializer(), payload).also { if (referenced) it.credentialReference = value }
    }

    @TypeConverter
    fun fromAuthToString(auth: AuthCredential?): String? = auth?.let { credentials.store(it.value).also { reference -> it.credentialReference = reference } }

    @TypeConverter
    fun fromStringToAuth(value: String?): AuthCredential? {
        if (value == null) return null
        return AuthCredential(if (credentials.isReference(value)) credentials.read(value) ?: return null else value).also { if (credentials.isReference(value)) it.credentialReference = value }
    }

    @TypeConverter
    fun formStringToTagBase(value: String): TagBase =
        Json.decodeFromString(TagBase.serializer(), value)

    @TypeConverter
    fun formTagBaseToString(tagBase: TagBase): String =
        Json.encodeToString(TagBase.serializer(), tagBase)

    @TypeConverter
    fun formStringToTagBaseList(value: String): List<TagBase> =
        Json.decodeFromString(value)

    @TypeConverter
    fun formTagBaseListToString(tags: List<TagBase>) =
        Json.encodeToString(tags)
}
