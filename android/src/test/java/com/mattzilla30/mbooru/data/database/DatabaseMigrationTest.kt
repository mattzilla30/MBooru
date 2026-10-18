package com.mattzilla30.mbooru.data.database

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.UUID

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DatabaseMigrationTest(private val from: Int) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "schema {0} to 10")
        fun versions() = (1..9).map { arrayOf(it) }
    }

    @Test fun `all supported schemas retain user data and isolate legacy credentials`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-$from.db"
        context.deleteDatabase(name)
        val schema = javaClass.classLoader!!.getResourceAsStream("com.mattzilla30.mbooru.data.database.MyDatabase/$from.json")!!
            .use { JSONObject(it.readBytes().toString(Charsets.UTF_8)).getJSONObject("database") }
        val entities = schema.getJSONArray("entities")
        val tableNames = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }.toSet()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { legacy ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                legacy.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) legacy.execSQL(setup.getString(i))
            val columns = nextBooruColumns(entities)
            val values: Map<String, Any?> = mapOf("uid" to 7, "name" to "Customized site", "scheme" to "https",
                "host" to "custom.invalid", "hash_salt" to "custom-salt", "type" to 1, "blacklists" to "[\"spoiler\"]",
                "user" to """{"id":42,"name":"legacy","token":"legacy-api-secret"}""", "path" to null, "auth" to "legacy-http-secret")
            legacy.execSQL("INSERT INTO boorus (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})",
                columns.map { values[it] }.toTypedArray())
            if ("history" in tableNames) legacy.execSQL("INSERT INTO history(uid,booru_uid,query) VALUES(9,7,'saved query')")
            if ("tags_filter" in tableNames) legacy.execSQL("INSERT INTO tags_filter(uid,booru_uid,name,type) VALUES(11,7,'spoiler',0)")
            if ("muzei" in tableNames) legacy.execSQL("INSERT INTO muzei(uid,booru_uid,query) VALUES(13,7,'wallpaper')")
            if ("next" in tableNames) legacy.execSQL("INSERT INTO `next`(uid,booru_uid,query,`next`) VALUES(15,7,'saved','cursor')")
            legacy.version = from
        }
        val payloads = Collections.synchronizedMap(mutableMapOf<String,String>())
        val credentials = object : CredentialRepository {
            override fun store(payload: String) = (CredentialRepository.PREFIX + UUID.randomUUID()).also { payloads[it] = payload }
            override fun read(reference: String) = payloads[reference]
            override fun remove(reference: String) { payloads.remove(reference) }
        }
        val database = MyDatabase.create(context, credentials, name)
        try {
            val site = database.booruDao().getBooruByUid(7)!!
            assertEquals("Customized site", site.name)
            assertEquals("custom.invalid", site.host)
            assertEquals("custom-salt", site.hashSalt)
            assertEquals(listOf("spoiler"), site.blacklists)
            assertEquals("legacy-api-secret", site.user!!.token)
            if (from >= 7) assertEquals("legacy-http-secret", site.auth!!.value)
            if ("history" in tableNames) assertEquals("saved query", database.historyDao().getHistoryByUid(9)!!.query)
            if ("tags_filter" in tableNames) assertEquals("spoiler", database.tagFilterDao().getTagByTagUid(11)!!.name)
            if ("muzei" in tableNames) assertEquals("wallpaper", database.muzeiDao().getMuzeiByUid(13)!!.query)
            if ("next" in tableNames) assertEquals("cursor", database.nextDao().getNext(7, "saved")!!.next)
            assertTrue(database.downloadDao().observeAll().first().isEmpty())
            database.openHelper.readableDatabase.query("SELECT user FROM boorus WHERE uid=7").use {
                assertTrue(it.moveToFirst())
                assertTrue(credentials.isReference(it.getString(0)))
                assertFalse(it.getString(0).contains("legacy-api-secret"))
            }
            database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        } finally { database.close(); context.deleteDatabase(name) }
    }
    private fun nextBooruColumns(entities: org.json.JSONArray): List<String> {
        val booru = (0 until entities.length()).map { entities.getJSONObject(it) }.first { it.getString("tableName") == "boorus" }
        val fields = booru.getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }
    }
}
