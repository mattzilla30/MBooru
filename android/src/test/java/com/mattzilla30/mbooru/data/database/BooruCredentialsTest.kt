package com.mattzilla30.mbooru.data.database

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.User
import com.mattzilla30.mbooru.okhttp.AndroidCookieJar
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BooruCredentialsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val payloads = mutableMapOf<String, String>()
    private val vault =
        object : CredentialRepository {
            override fun store(payload: String) =
                (CredentialRepository.PREFIX + UUID.randomUUID()).also { payloads[it] = payload }

            override fun read(reference: String) = payloads[reference]

            override fun remove(reference: String) {
                payloads.remove(reference)
            }
        }
    private lateinit var database: MyDatabase
    private lateinit var manager: BooruManager

    @Before
    fun setup() {
        database = MyDatabase.create(context, vault, "site-credentials.db")
        manager = BooruManager(database.booruDao(), vault, AndroidCookieJar(context))
    }

    @After
    fun close() {
        database.close()
        context.deleteDatabase("site-credentials.db")
    }

    @Test
    fun `creating a duplicate cannot erase an existing login`() = runBlocking {
        val site =
            manager.createBooru(
                Booru(
                    name = "Original",
                    host = "fixture.invalid",
                    type = 1,
                    user = User(name = "fixture", token = "private-fixture"),
                )
            )
        try {
            manager.createBooru(Booru(name = "Duplicate", host = "fixture.invalid", type = 1))
            fail("Duplicate creation must be rejected")
        } catch (_: IllegalStateException) {}
        val retained = database.booruDao().getBooruByUid(site.uid)!!
        assertEquals("Original", retained.name)
        assertEquals("private-fixture", retained.user!!.token)
    }

    @Test
    fun `site deletion removes its unshared credential payload`() = runBlocking {
        val site =
            manager.createBooru(
                Booru(
                    name = "Original",
                    host = "fixture.invalid",
                    type = 1,
                    user = User(name = "fixture", token = "private-fixture"),
                )
            )
        val reference = database.booruDao().getBooruByUid(site.uid)!!.user!!.credentialReference!!
        assertNotNull(vault.read(reference))
        assertTrue(manager.deleteBooru(site.uid))
        assertNull(database.booruDao().getBooruByUid(site.uid))
        assertNull(vault.read(reference))
    }
}
