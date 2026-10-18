package com.mattzilla30.mbooru.data.database

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.User
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
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val preferences get() = context.getSharedPreferences("preset-test", Context.MODE_PRIVATE)
    private val dao get() = database.booruDao()

    @Before fun setUp() {
        preferences.edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, MyDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun tearDown() { database.close() }

    @Test fun `first launch adds all six HTTPS presets with matching engines`() {
        BooruPresets.installOnce(dao, preferences)
        val sites = dao.getAll()
        assertEquals(listOf("Danbooru", "Yande.re", "Konachan", "Gelbooru", "Safebooru", "Sankaku"), sites.map { it.name })
        assertEquals(listOf(Values.BOORU_TYPE_DAN, Values.BOORU_TYPE_MOE, Values.BOORU_TYPE_MOE,
            Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY, Values.BOORU_TYPE_SANKAKU), sites.map { it.type })
        assertTrue(sites.all { it.uid > 0 && it.scheme == "https" && it.user == null && it.auth == null })
        assertEquals(6, sites.map { it.host }.toSet().size)
    }

    @Test fun `upgrade preserves existing login filters identity and custom sites`() {
        val customized = BooruPresets.featured().first().copy(name = "My Danbooru",
            user = User(id = 42, name = "test-user", token = "test-api-key"), blacklists = listOf("spoiler"),
            auth = "test-auth")
        customized.uid = dao.insert(customized)
        val custom = customized.copy(uid = 0, name = "Private site", host = "private.invalid")
        custom.uid = dao.insert(custom)
        BooruPresets.installOnce(dao, preferences)
        assertEquals(customized, dao.getBooruByUid(customized.uid))
        assertEquals(custom, dao.getBooruByUid(custom.uid))
        assertEquals(7, dao.getAll().size)
    }

    @Test fun `reopening keeps deletions and restoring one preset neither duplicates nor replaces it`() {
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
        val customized = dao.getBooruByUid(uid)!!.copy(name = "My Sankaku")
        dao.update(customized)
        assertEquals(uid, BooruPresets.add(dao, preset))
        assertEquals(customized, dao.getBooruByUid(uid))
    }

    @Test fun `deleting all sites does not force presets back on next launch`() {
        BooruPresets.installOnce(dao, preferences)
        dao.deleteAll()
        BooruPresets.installOnce(dao, preferences)
        assertTrue(dao.getAll().isEmpty())
        BooruPresets.add(dao, BooruPresets.featured()[4])
        assertEquals("Safebooru", dao.getAll().single().name)
    }
    @Test fun `retire sample after presets were already installed and leave unrelated sites alone`() {
        BooruPresets.installOnce(dao, preferences)
        val sample = com.mattzilla30.mbooru.data.model.common.Booru(name = "Sample", host = "moe.fiepi.com",
            hashSalt = "onlymash--your-password--", type = Values.BOORU_TYPE_MOE)
        val sampleUid = dao.insert(sample)
        val customUid = dao.insert(sample.copy(host = "custom.invalid"))
        BooruPresets.installOnce(dao, preferences)
        assertNull(dao.getBooruByUid(sampleUid))
        assertNotNull(dao.getBooruByUid(customUid))
        assertEquals(7, dao.getAll().size)
        BooruPresets.installOnce(dao, preferences)
        assertEquals(7, dao.getAll().size)
    }

    @Test fun `a custom site on the former sample host is preserved`() {
        val custom = com.mattzilla30.mbooru.data.model.common.Booru(name = "My server", host = "moe.fiepi.com",
            user = User(name = "test-user", token = "test-token"), type = Values.BOORU_TYPE_MOE)
        custom.uid = dao.insert(custom)
        BooruPresets.installOnce(dao, preferences)
        assertEquals(custom, dao.getBooruByUid(custom.uid))
    }

}
