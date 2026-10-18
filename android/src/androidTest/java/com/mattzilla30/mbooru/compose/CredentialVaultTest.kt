package com.mattzilla30.mbooru.compose

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.data.credentials.*
import com.mattzilla30.mbooru.data.database.MyConverters
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CredentialVaultTest {
    @Test fun credentialsRoundTripOutsideRoomAndMissingPayloadRequiresSignIn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = "mbooru.test.${UUID.randomUUID()}"
        val repository = DeviceCredentialRepository(context, AndroidCredentialCipher(context, alias))
        val database = Room.inMemoryDatabaseBuilder(context, MyDatabase::class.java)
            .addTypeConverter(MyConverters(repository)).build()
        var reference: String? = null
        try {
            val uid = database.booruDao().insert(Booru(name = "Fixture", host = "fixture.invalid", type = 0,
                user = User(name = "fixture", token = "private-api-key")))
            database.openHelper.readableDatabase.query("SELECT user FROM boorus WHERE uid = $uid").use {
                assertTrue(it.moveToFirst())
                reference = it.getString(0)
            }
            assertTrue(repository.isReference(reference!!))
            val encrypted = File(context.noBackupFilesDir, "credential-vault/${reference!!.removePrefix(CredentialRepository.PREFIX)}").readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains("private-api-key"))
            assertEquals("private-api-key", database.booruDao().getBooruByUid(uid)!!.user!!.token)
            repository.remove(reference!!)
            assertNull(database.booruDao().getBooruByUid(uid)!!.user)
        } finally {
            reference?.let(repository::remove)
            database.close()
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
            File(context.noBackupFilesDir, "$alias.wrapped").delete()
        }
    }
}
