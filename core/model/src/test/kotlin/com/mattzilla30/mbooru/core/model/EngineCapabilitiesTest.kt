package com.mattzilla30.mbooru.core.model

import org.junit.Assert.*
import org.junit.Test

class EngineCapabilitiesTest {
    @Test
    fun `persisted engine identifiers remain unique and unknown values are conservative`() {
        assertEquals(BooruEngine.entries.size, BooruEngine.entries.map { it.legacyId }.toSet().size)
        assertEquals(BooruEngine.UNKNOWN, BooruEngine.fromLegacyId(999))
        assertFalse(BooruEngine.fromLegacyId(999).capabilities.accounts)
    }

    @Test
    fun `archive and browse capabilities reflect engine protocols`() {
        assertTrue(BooruEngine.MOEBOORU.capabilities.poolArchives)
        assertFalse(BooruEngine.DANBOORU.capabilities.poolArchives)
        assertFalse(BooruEngine.SHIMMIE.capabilities.tags)
        assertTrue(BooruEngine.GELBOORU.capabilities.tags)
        assertFalse(BooruEngine.GELBOORU.capabilities.artists)
        assertFalse(BooruEngine.SANKAKU.capabilities.artists)
    }

    @Test
    fun `Rule34 has native tags and score discovery but no fictional pool or artist API`() {
        val capabilities = BooruEngine.RULE34.capabilities
        assertTrue(capabilities.tags)
        assertTrue(capabilities.popular)
        assertFalse(capabilities.pools)
        assertFalse(capabilities.artists)
        assertFalse(capabilities.favoriteWrites)
    }
}
