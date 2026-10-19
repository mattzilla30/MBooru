package com.mattzilla30.mbooru.data.database

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.data.backup.LibraryBackups
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.SavedPost
import com.mattzilla30.mbooru.data.model.common.TagBase
import com.mattzilla30.mbooru.data.model.common.User
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class LibraryTest {
    private lateinit var database: MyDatabase

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            MyDatabase.create(
                context,
                object : CredentialRepository {
                    private val entries = mutableMapOf<String, String>()

                    override fun store(payload: String): String =
                        (CredentialRepository.PREFIX + java.util.UUID.randomUUID()).also {
                            entries[it] = payload
                        }

                    override fun read(reference: String) = entries[reference]

                    override fun remove(reference: String) {
                        entries.remove(reference)
                    }
                },
                "library-${java.util.UUID.randomUUID()}.db",
            )
    }

    @After
    fun close() {
        database.close()
    }

    private suspend fun site(host: String) =
        database.booruDao().insert(Booru(name = host, host = host, type = 1))

    private fun post(site: Long, id: Int = 7) =
        Post(
            booruUid = site,
            id = id,
            width = 100,
            height = 100,
            size = 0,
            score = 2,
            rating = "s",
            time = null,
            tags = listOf(TagBase("landscape")),
            preview = "https://images.rule34.xxx/p.jpg",
            sample = "https://images.rule34.xxx/a.jpg",
            medium = "https://images.rule34.xxx/a.jpg",
            origin = "https://images.rule34.xxx/a.jpg",
            source = null,
            uploader = User(name = "fixture"),
        )

    @Test
    fun backupExcludesCredentialsAndImportRollsBackEveryTableOnWriteFailure() = runBlocking {
        val site = site("one.invalid")
        val dao = database.libraryDao()
        val safe =
            post(site)
                .copy(
                    source = "artist attribution",
                    uploader =
                        User(
                            name = "artist",
                            token = "private-uploader-key",
                            accessToken = "private-access-token",
                        ),
                )
        dao.toggleLike(safe)
        val orphan = post(site, 8)
        dao.toggleLike(orphan)
        dao.toggleLike(orphan)
        val group = dao.createCollection("Rollback fixture")
        dao.setMembership(safe, group, true)
        val backups = LibraryBackups(database)
        val document = backups.export()
        assertFalse(document.contains("private-uploader-key"))
        assertFalse(document.contains("private-access-token"))
        val preview = backups.preview(document.byteInputStream())
        assertEquals(1, preview.backup.artwork.size)
        assertEquals("artist attribution", preview.backup.artwork.single().post.source)
        database.booruDao().deleteAll()
        dao.deleteCollection(group)
        assertTrue(dao.snapshots().isEmpty())
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_import BEFORE INSERT ON collection_memberships BEGIN SELECT RAISE(ABORT, 'fixture abort'); END"
        )
        try {
            backups.import(preview)
            fail("Import should fail atomically")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertTrue(database.booruDao().getAll().isEmpty())
        assertTrue(dao.snapshots().isEmpty())
        assertTrue(dao.likes().isEmpty())
        assertTrue(dao.collections().isEmpty())
        assertTrue(dao.memberships().isEmpty())
    }

    @Test
    fun siteAwareLikesCollectionsAndExplicitSavesAreIndependent() = runBlocking {
        val one = post(site("one.invalid"))
        val two = post(site("two.invalid"))
        val dao = database.libraryDao()
        val saved = database.savedPostDao()
        saved.insert(SavedPost(one, 1))
        dao.toggleLike(one)
        dao.toggleLike(two)
        val a = dao.createCollection("Walls")
        val b = dao.createCollection("Inspiration")
        dao.setMembership(one, a, true)
        dao.setMembership(one, b, true)
        saved.delete(one.booruUid, one.id)
        assertTrue(dao.isLiked(one.booruUid, one.id))
        assertFalse(saved.contains(one.booruUid, one.id))
        assertEquals(2, dao.memberships().size)
        saved.insert(SavedPost(one, 2))
        assertEquals(2, dao.memberships().size)
        dao.renameCollection(a, "Landscapes")
        dao.deleteCollection(a)
        assertEquals(listOf(b), dao.memberships().map { it.collection })
        assertTrue(dao.isLiked(two.booruUid, two.id))
        assertEquals(2, dao.snapshots().size)
    }

    @Test
    fun invalidNamesAndDuplicateSafeBackupRestore() = runBlocking {
        val dao = database.libraryDao()
        val one = post(site("one.invalid"))
        val collection = dao.createCollection("Walls")
        for (name in listOf("", "All saved", "walls", "a".repeat(41))) {
            try {
                dao.createCollection(name)
                fail("Accepted invalid name")
            } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        }
        dao.setMembership(one, collection, true)
        dao.toggleLike(one)
        val backups = LibraryBackups(database)
        val output = backups.export()
        assertFalse(output.contains("api_key"))
        val preview = backups.preview(output.byteInputStream())
        backups.import(preview)
        backups.import(preview)
        assertEquals(1, dao.snapshots().size)
        assertEquals(1, dao.collections().size)
        assertEquals(1, dao.likes().size)
        assertEquals(1, dao.memberships().size)
        try {
            backups.preview(output.replace("\"id\": 7", "\"id\": -1").byteInputStream())
            fail("Accepted invalid post ID")
        } catch (_: IllegalArgumentException) {}
        assertEquals(1, dao.snapshots().size)
    }

    @Test
    fun m34ImportMapsRealPostIdentitiesAndPreservesUnresolvedLikes() = runBlocking {
        val json =
            """{"format":"M34-library","version":1,"artwork":[{"id":"r34-42","tags":"landscape","rating":"g","width":100,"height":200,"preview":"https://images.rule34.xxx/p.jpg","original":"https://images.rule34.xxx/a.jpg"}],"likes":["r34-42","r34-99"],"collections":{"Walls":["r34-42"]}}"""
        val backups = LibraryBackups(database)
        val preview = backups.preview(json.byteInputStream())
        backups.import(preview)
        backups.import(preview)
        assertEquals(1, database.booruDao().getAll().size)
        assertEquals(setOf(42, 99), database.libraryDao().snapshots().map { it.post.id }.toSet())
        assertEquals(2, database.libraryDao().likes().size)
        assertEquals(1, database.libraryDao().memberships().size)
    }
}
