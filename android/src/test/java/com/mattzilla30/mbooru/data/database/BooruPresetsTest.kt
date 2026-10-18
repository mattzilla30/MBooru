package com.mattzilla30.mbooru.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.User
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class BooruPresetsTest {
    private lateinit var database: MyDatabase
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    private val preferences
        get() = context.getSharedPreferences("preset-test", Context.MODE_PRIVATE)

    private val dao
        get() = database.booruDao()

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        database =
            Room.inMemoryDatabaseBuilder(context, MyDatabase::class.java)
                .addTypeConverter(
                    MyConverters(
                        object : com.mattzilla30.mbooru.data.credentials.CredentialRepository {
                            private val values = mutableMapOf<String, String>()

                            override fun store(payload: String): String =
                                (com.mattzilla30.mbooru.data.credentials.CredentialRepository
                                        .PREFIX + java.util.UUID.randomUUID())
                                    .also { values[it] = payload }

                            override fun read(reference: String) = values[reference]

                            override fun remove(reference: String) {
                                values.remove(reference)
                            }
                        }
                    )
                )
                .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `first launch offers the six anonymous HTTPS presets`() =
        kotlinx.coroutines.runBlocking {
            BooruPresets.installOnce(dao, preferences)
            val sites = dao.getAll()
            assertEquals(
                listOf("Yande.re", "Konachan", "Safebooru", "e926", "e621", "Paheal"),
                sites.map { it.name },
            )
            assertEquals(
                listOf(
                    Values.BOORU_TYPE_MOE,
                    Values.BOORU_TYPE_MOE,
                    Values.BOORU_TYPE_GEL_LEGACY,
                    Values.BOORU_TYPE_DAN,
                    Values.BOORU_TYPE_DAN,
                    Values.BOORU_TYPE_SHIMMIE,
                ),
                sites.map { it.type },
            )
            assertTrue(
                sites.all {
                    it.uid > 0 && it.scheme == "https" && it.user == null && it.auth == null
                }
            )
            assertEquals(6, sites.map { it.host }.toSet().size)
        }

    @Test
    fun `upgrade preserves existing login filters identity and custom sites`() =
        kotlinx.coroutines.runBlocking {
            val customized =
                BooruPresets.featured()
                    .first()
                    .copy(
                        name = "My Yande.re",
                        user = User(id = 42, name = "test-user", token = "test-api-key"),
                        blacklists = listOf("spoiler"),
                        auth = com.mattzilla30.mbooru.data.model.common.AuthCredential("test-auth"),
                    )
            customized.uid = dao.insert(customized)
            val custom = customized.copy(uid = 0, name = "Private site", host = "private.invalid")
            custom.uid = dao.insert(custom)
            BooruPresets.installOnce(dao, preferences)
            assertEquals(customized, dao.getBooruByUid(customized.uid))
            assertEquals(custom, dao.getBooruByUid(custom.uid))
            assertEquals(7, dao.getAll().size)
        }

    @Test
    fun `catalog upgrade adds three sites once without replacing customizations or old deletions`() =
        kotlinx.coroutines.runBlocking {
            preferences.edit().putBoolean("featured_boorus_v1_installed", true).commit()
            val custom =
                BooruPresets.featured()
                    .first { it.host == "e621.net" }
                    .copy(
                        name = "My e621",
                        blacklists = listOf("spoiler"),
                        user = User(name = "local", token = "fixture"),
                    )
            val uid = dao.insert(custom)
            BooruPresets.installOnce(dao, preferences)
            assertEquals(custom.copy(uid = uid), dao.getBooruByUid(uid))
            assertEquals(
                setOf("e926.net", "e621.net", "rule34.paheal.net"),
                dao.getAll().map { it.host }.toSet(),
            )
            dao.delete(dao["https", "e926.net"]!!.uid)
            BooruPresets.installOnce(dao, preferences)
            assertNull(dao["https", "e926.net"])
            assertEquals(2, dao.getAll().size)
        }

    @Test
    fun `reopening keeps deletions and restoring one preset neither duplicates nor replaces it`() =
        kotlinx.coroutines.runBlocking {
            BooruPresets.installOnce(dao, preferences)
            val preset = BooruPresets.featured().last()
            val old = dao[preset.scheme, preset.host]!!
            dao.delete(old.uid)
            BooruPresets.installOnce(dao, preferences)
            assertNull(dao[preset.scheme, preset.host])
            val uid = BooruPresets.add(dao, preset)
            assertTrue(uid > 0)
            assertEquals(uid, BooruPresets.add(dao, preset))
            assertEquals(6, dao.getAll().size)
            val customized = dao.getBooruByUid(uid)!!.copy(name = "My Paheal")
            dao.update(customized)
            assertEquals(uid, BooruPresets.add(dao, preset))
            assertEquals(customized, dao.getBooruByUid(uid))
        }

    @Test
    fun `deleting all sites does not force presets back on next launch`() =
        kotlinx.coroutines.runBlocking {
            BooruPresets.installOnce(dao, preferences)
            dao.deleteAll()
            BooruPresets.installOnce(dao, preferences)
            assertTrue(dao.getAll().isEmpty())
            BooruPresets.add(dao, BooruPresets.featured().last())
            assertEquals("Paheal", dao.getAll().single().name)
        }

    @Test
    fun `retire sample after presets were already installed and leave unrelated sites alone`() =
        kotlinx.coroutines.runBlocking {
            BooruPresets.installOnce(dao, preferences)
            val sample =
                com.mattzilla30.mbooru.data.model.common.Booru(
                    name = "Sample",
                    host = "moe.fiepi.com",
                    hashSalt = "onlymash--your-password--",
                    type = Values.BOORU_TYPE_MOE,
                )
            val sampleUid = dao.insert(sample)
            val customUid = dao.insert(sample.copy(host = "custom.invalid"))
            BooruPresets.installOnce(dao, preferences)
            assertNull(dao.getBooruByUid(sampleUid))
            assertNotNull(dao.getBooruByUid(customUid))
            assertEquals(7, dao.getAll().size)
            BooruPresets.installOnce(dao, preferences)
            assertEquals(7, dao.getAll().size)
        }

    @Test
    fun `a custom site on the former sample host is preserved`() =
        kotlinx.coroutines.runBlocking {
            val custom =
                com.mattzilla30.mbooru.data.model.common.Booru(
                    name = "My server",
                    host = "moe.fiepi.com",
                    user = User(name = "test-user", token = "test-token"),
                    type = Values.BOORU_TYPE_MOE,
                )
            custom.uid = dao.insert(custom)
            BooruPresets.installOnce(dao, preferences)
            assertEquals(custom, dao.getBooruByUid(custom.uid))
        }

    @Test
    fun `saving a duplicate address preserves identity history and filters`() =
        kotlinx.coroutines.runBlocking {
            val site = BooruPresets.featured().first()
            val uid = dao.insert(site)
            val history =
                com.mattzilla30.mbooru.data.model.common.History(
                    booruUid = uid,
                    query = "landscape",
                )
            val historyUid = database.historyDao().insert(history)
            val filter =
                com.mattzilla30.mbooru.data.model.common.TagFilter(booruUid = uid, name = "spoiler")
            val filterUid = database.tagFilterDao().insert(filter)
            assertEquals(uid, dao.insert(site.copy(uid = 0, name = "Updated name")))
            assertEquals("Updated name", dao.getBooruByUid(uid)!!.name)
            assertNotNull(database.historyDao().getHistoryByUid(historyUid))
            assertNotNull(database.tagFilterDao().getTagByTagUid(filterUid))
        }

    @Test
    fun `filter presence is independent of site presence`() =
        kotlinx.coroutines.runBlocking {
            val uid = dao.insert(BooruPresets.featured().first())
            assertFalse(database.tagFilterDao().isNotEmpty())
            database
                .tagFilterDao()
                .insert(
                    com.mattzilla30.mbooru.data.model.common.TagFilter(
                        booruUid = uid,
                        name = "spoiler",
                    )
                )
            assertTrue(database.tagFilterDao().isNotEmpty())
        }

    @Test
    fun `configuration restore retains local login and dependent records`() =
        kotlinx.coroutines.runBlocking {
            val local =
                BooruPresets.featured()
                    .first()
                    .copy(user = User(name = "local", token = "local-secret"))
            val uid = dao.insert(local)
            val historyUid =
                database
                    .historyDao()
                    .insert(
                        com.mattzilla30.mbooru.data.model.common.History(
                            booruUid = uid,
                            query = "saved",
                        )
                    )
            dao.importSites(
                listOf(local.copy(uid = 999, name = "Restored configuration", user = null))
            )
            assertEquals(local.user, dao.getBooruByUid(uid)!!.user)
            assertEquals("Restored configuration", dao.getBooruByUid(uid)!!.name)
            assertNotNull(database.historyDao().getHistoryByUid(historyUid))
        }

    @Test
    fun `retired servers are removed even when the old catalog marker exists`() =
        kotlinx.coroutines.runBlocking {
            val hosts = listOf("danbooru.donmai.us", "gelbooru.com", "capi-v2.sankakucomplex.com")
            hosts.forEach {
                dao.insert(
                    com.mattzilla30.mbooru.data.model.common.Booru(
                        name = "Retired",
                        host = it,
                        type = Values.BOORU_TYPE_MOE,
                    )
                )
            }
            val custom =
                dao.insert(
                    com.mattzilla30.mbooru.data.model.common.Booru(
                        name = "Custom",
                        host = "custom.invalid",
                        type = Values.BOORU_TYPE_MOE,
                    )
                )
            preferences.edit().putBoolean("featured_boorus_v1_installed", true).commit()
            val removed = mutableListOf<Long>()
            BooruPresets.installOnce(dao, preferences) { uid ->
                removed += uid
                dao.delete(uid)
                Unit
            }
            assertEquals(3, removed.size)
            assertNotNull(dao.getBooruByUid(custom))
            assertEquals(
                setOf("custom.invalid", "e926.net", "e621.net", "rule34.paheal.net"),
                dao.getAll().map { it.host }.toSet(),
            )
            assertTrue(BooruPresets.isRetiredHost("www.GELBOORU.com"))
        }

    @Test
    fun `anonymous favorites survive timeline refresh and are scoped to their server`() =
        kotlinx.coroutines.runBlocking {
            val first = dao.insert(BooruPresets.featured().first())
            val second = dao.insert(BooruPresets.featured().last())
            val post =
                com.mattzilla30.mbooru.data.model.common.Post(
                    booruUid = first,
                    id = 123,
                    width = 640,
                    height = 480,
                    size = 10,
                    score = 0,
                    rating = "s",
                    time = null,
                    tags = emptyList(),
                    preview = "https://fixture.invalid/preview.png",
                    sample = "https://fixture.invalid/sample.png",
                    medium = "https://fixture.invalid/medium.png",
                    origin = "https://fixture.invalid/origin.png",
                    source = null,
                    uploader = User(name = "anonymous"),
                )
            database.postDao().insert(post)
            val favorites = database.savedPostDao()
            assertTrue(favorites.toggle(post))
            assertTrue(favorites.toggle(post.copy(booruUid = second)))
            database.postDao().deleteAll()
            val saved = favorites.observe().first()
            assertEquals(2, saved.size)
            assertEquals(setOf(first, second), saved.map { it.booruUid }.toSet())
            assertEquals(post.origin, saved.first().origin)
            assertFalse(favorites.toggle(post))
            assertFalse(favorites.contains(first, post.id))
            assertTrue(favorites.contains(second, post.id))
            dao.delete(second)
            assertTrue(favorites.observe().first().isEmpty())
        }
}
