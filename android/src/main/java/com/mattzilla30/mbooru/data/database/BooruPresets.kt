// Copyright (C) 2026 mattzilla30 <mattzilla30@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.mattzilla30.mbooru.data.database

import android.content.SharedPreferences
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru

/** Ready-to-use public endpoints; accounts and API keys remain owned by each user. */
object BooruPresets {
    private const val INSTALLED_KEY = "featured_boorus_v1_installed"

    // Return fresh instances: Booru contains mutable settings and database IDs.
    fun featured(): List<Booru> =
        listOf(
            Booru(name = "Yande.re", host = "yande.re", type = Values.BOORU_TYPE_MOE),
            Booru(name = "Konachan", host = "konachan.com", type = Values.BOORU_TYPE_MOE),
            Booru(name = "Safebooru", host = "safebooru.org", type = Values.BOORU_TYPE_GEL_LEGACY),
        )

    fun isRetiredHost(host: String): Boolean {
        val name = host.lowercase().removePrefix("www.")
        return name == "danbooru.donmai.us" ||
            name == "gelbooru.com" ||
            name == "sankakucomplex.com" ||
            name.endsWith(".sankakucomplex.com") ||
            name == "sankaku.app"
    }

    /** Runs on first launch after installing/updating; never replaces saved site credentials. */
    suspend fun installOnce(
        dao: BooruDao,
        preferences: SharedPreferences,
        remove: suspend (Long) -> Unit = {
            dao.delete(it)
            Unit
        },
    ) {
        dao.getAll().orEmpty().filter { isRetiredHost(it.host) }.forEach { remove(it.uid) }
        // Run even after presets were installed by an earlier MBooru release.
        dao.removeLegacySample()
        if (preferences.getBoolean(INSTALLED_KEY, false)) return
        dao.insertMissing(featured())
        // Mark only after the database transaction succeeds. Repeating after a crash is harmless.
        preferences.edit().putBoolean(INSTALLED_KEY, true).apply()
    }

    /** Also restores individually deleted presets, leaving an existing configuration untouched. */
    suspend fun add(dao: BooruDao, preset: Booru): Long {
        require(!isRetiredHost(preset.host)) { "This site is not offered by MBooru" }
        dao.insertMissing(listOf(preset.copy(uid = 0)))
        return checkNotNull(dao[preset.scheme, preset.host]).uid
    }
}
