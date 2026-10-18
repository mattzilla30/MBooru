package com.mattzilla30.mbooru.data.backup

import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.User
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SiteBackupsTest {
    private val site = Booru(name = "My site", host = "example.invalid", type = 0,
        blacklists = listOf("spoiler"), user = User(name = "user", token = "api-secret",
            accessToken = "access-secret", refreshToken = "refresh-secret"), auth = com.mattzilla30.mbooru.data.model.common.AuthCredential("http-secret"))

    @Test fun `portable backup excludes all account and HTTP credentials`() {
        val encoded = SiteBackups.encode(listOf(site))
        listOf("api-secret", "access-secret", "refresh-secret", "http-secret").forEach { assertFalse(encoded.contains(it)) }
        val restored = SiteBackups.decode(encoded.byteInputStream()).single()
        assertEquals(site.name, restored.name)
        assertEquals(site.blacklists, restored.blacklists)
        assertNull(restored.user)
        assertNull(restored.auth)
    }

    @Test fun `legacy backup imports configuration while discarding portable credentials`() {
        val restored = SiteBackups.decode(Json.encodeToString(listOf(site)).byteInputStream()).single()
        assertNull(restored.user)
        assertNull(restored.auth)
        assertEquals(site.host, restored.host)
    }

    @Test fun `corrupt duplicate future and oversized backups are rejected before import`() {
        listOf("{", """{"format":"MBooru-sites","version":2,"sites":[]}""",
            Json.encodeToString(listOf(site, site.copy(name = "Duplicate"))),
            " ".repeat(SiteBackups.MAX_BYTES + 1)).forEach { data ->
            try { SiteBackups.decode(data.byteInputStream()); fail("Invalid backup accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
