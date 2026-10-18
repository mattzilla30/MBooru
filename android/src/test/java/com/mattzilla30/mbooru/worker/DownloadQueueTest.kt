package com.mattzilla30.mbooru.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.mattzilla30.mbooru.app.appModules
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.database.dao.DownloadDao
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DownloadQueueTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After
    fun close() {
        WorkManagerTestInitHelper.closeWorkDatabase()
        androidx.work.impl.WorkManagerImpl.setDelegate(null)
        stopKoin()
        context.deleteDatabase("mbooru.db")
    }

    @Test
    fun `enqueue is unique constrained persistent and excludes credentials`() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val secrets = mutableMapOf<String, String>()
        val vault =
            object : CredentialRepository {
                override fun store(payload: String) =
                    (CredentialRepository.PREFIX + UUID.randomUUID()).also { secrets[it] = payload }

                override fun read(reference: String) = secrets[reference]

                override fun remove(reference: String) {
                    secrets.remove(reference)
                }
            }
        val koin = startKoin {
            androidContext(context)
            modules(appModules, module { single<CredentialRepository> { vault } })
        }
            .koin
        val request =
            workDataOf(
                "doc_id" to "content://fixture.documents/tree/original/document/opaque",
                "url" to "https://fixture.invalid/file.png?token=private-capability",
                "host" to "fixture.invalid",
                "post_id" to 9,
                "type" to "type_post",
                "username" to "legacy-user",
                "password_hash" to "legacy-secret",
            )
        DownloadWorker.runWork(context, request)
        DownloadWorker.runWork(context, request)
        val records = koin.get<DownloadDao>().observeAll().first()
        assertEquals(1, records.size)
        val data = Data.fromByteArray(records.single().request)
        assertNull(data.getString("username"))
        assertNull(data.getString("password_hash"))
        assertTrue(vault.isReference(data.getString("url")!!))
        assertEquals(request.getString("url"), vault.read(data.getString("url")!!))
        val manager = WorkManager.getInstance(context)
        val jobs =
            manager.getWorkInfosForUniqueWork("download:${request.getString("doc_id")}").get()
        assertEquals(1, jobs.size)
        assertEquals(WorkInfo.State.ENQUEUED, jobs.single().state)
        assertEquals(NetworkType.CONNECTED, jobs.single().constraints.requiredNetworkType)
        manager.cancelWorkById(jobs.single().id).result.get()
        assertEquals(
            WorkInfo.State.CANCELLED,
            checkNotNull(manager.getWorkInfoById(jobs.single().id).get()).state,
        )
    }
}
