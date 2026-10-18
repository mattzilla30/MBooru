// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.core.model

/** Stable engine IDs are persisted by existing installations. Never reorder or reuse them. */
enum class BooruEngine(val legacyId: Int, val capabilities: EngineCapabilities) {
    DANBOORU(0, EngineCapabilities(popular = true, pools = true, tags = true, artists = true, accounts = true)),
    MOEBOORU(1, EngineCapabilities(popular = true, pools = true, tags = true, artists = true, accounts = true, poolArchives = true)),
    DANBOORU_V1(2, EngineCapabilities(popular = true, pools = true, tags = true, artists = true, accounts = true)),
    GELBOORU(3, EngineCapabilities(tags = true, accounts = true)),
    SANKAKU(4, EngineCapabilities(popular = true, pools = true, tags = true, accounts = true)),
    SHIMMIE(5, EngineCapabilities()),
    GELBOORU_LEGACY(6, EngineCapabilities(tags = true, accounts = true)),
    UNKNOWN(-1, EngineCapabilities());

    companion object {
        fun fromLegacyId(id: Int): BooruEngine = entries.firstOrNull { it.legacyId == id } ?: UNKNOWN
    }
}

data class EngineCapabilities(
    val popular: Boolean = false,
    val pools: Boolean = false,
    val tags: Boolean = false,
    val artists: Boolean = false,
    val accounts: Boolean = false,
    val poolArchives: Boolean = false,
)
