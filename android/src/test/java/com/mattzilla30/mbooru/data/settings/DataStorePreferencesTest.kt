package com.mattzilla30.mbooru.data.settings

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DataStorePreferencesTest {
    @Test
    fun `legacy values migrate once and credentials stay out of the portable file`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.preferencesDataStoreFile("mbooru")
        file.delete()
        val legacy =
            context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
        legacy
            .edit()
            .clear()
            .putLong("selected", 42)
            .putBoolean("dark", true)
            .putString("sauce_nao_api_key", "private-sauce-key")
            .commit()
        val payloads = mutableMapOf<String, String>()
        val vault =
            object : CredentialRepository {
                override fun store(payload: String): String =
                    (CredentialRepository.PREFIX + java.util.UUID.randomUUID()).also {
                        payloads[it] = payload
                    }

                override fun read(reference: String) = payloads[reference]

                override fun remove(reference: String) {
                    payloads.remove(reference)
                }
            }
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var repository = DataStorePreferences(context, vault, scope)
            repository.awaitReady()
            assertEquals(42L, repository.getLong("selected", -1))
            assertTrue(repository.getBoolean("dark", false))
            assertEquals("private-sauce-key", repository.getString("sauce_nao_api_key", ""))
            assertFalse(file.readText().contains("private-sauce-key"))
            assertFalse(legacy.contains("sauce_nao_api_key"))
            assertTrue(
                withContext(Dispatchers.IO) {
                    repository.edit().putString("layout", "fixed").commit()
                }
            )
            scope.coroutineContext.job.cancelAndJoin()
            legacy.edit().putLong("selected", 999).commit()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            repository = DataStorePreferences(context, vault, scope)
            repository.awaitReady()
            assertEquals(42L, repository.getLong("selected", -1))
            assertEquals("fixed", repository.getString("layout", ""))
            assertTrue(
                withContext(Dispatchers.IO) {
                    repository.edit().remove("sauce_nao_api_key").commit()
                }
            )
            assertTrue(payloads.isEmpty())
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
            file.delete()
            legacy.edit().clear().commit()
        }
    }
}
