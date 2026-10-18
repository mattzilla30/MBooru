package com.mattzilla30.mbooru.worker

import android.app.Application
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.data.credentials.CredentialRepository
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.DownloadRecord
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DownloadStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var provider: OpaqueProvider
    private lateinit var database: MyDatabase
    private val server = MockWebServer()
    private val key = "fixture-work"

    @Before
    fun setup() {
        provider = OpaqueProvider()
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "fixture.documents"
                exported = true
                grantUriPermissions = true
                readPermission = "android.permission.MANAGE_DOCUMENTS"
                writePermission = readPermission
            },
        )
        ShadowContentResolver.registerProviderInternal("fixture.documents", provider)
        database =
            MyDatabase.create(
                context,
                object : CredentialRepository {
                    override fun store(payload: String) = error("No credentials in this fixture")

                    override fun read(reference: String): String? = null

                    override fun remove(reference: String) {}
                },
                "download-test.db",
            )
        server.start()
    }

    @After
    fun close() {
        server.shutdown()
        database.close()
        context.deleteDatabase("download-test.db")
    }

    @Test
    fun `opaque destinations survive transfers and publication preserves existing downloads`() =
        runBlocking {
            val oldId = provider.createDocument("root", "image/png", "image.png")
            provider.files.getValue(oldId).writeText("previous download")
            val id =
                provider.createDocument("root", "application/octet-stream", "image.png.uuid.part")
            val uri = DocumentsContract.buildDocumentUri("fixture.documents", id)
            database
                .downloadDao()
                .insert(DownloadRecord(key, uri.toString(), "test", byteArrayOf(), 0))
            server.enqueue(
                MockResponse().setBody("new complete download").setHeader("ETag", "\"v1\"")
            )
            transferDownload(
                context,
                database.downloadDao(),
                key,
                server.url("/image.png").toString(),
                uri,
                OkHttpClient(),
            )
            assertEquals("new complete download", provider.files.getValue(id).readText())
            val published = publishDownload(context, uri, "image.png")
            assertEquals(
                "image.png (1)",
                provider.names.getValue(DocumentsContract.getDocumentId(published)),
            )
            assertEquals("previous download", provider.files.getValue(oldId).readText())
        }

    @Test
    fun `matching validators append only the validated remaining bytes`() = runBlocking {
        val id = provider.createDocument("root", "application/octet-stream", "download.part")
        val uri = DocumentsContract.buildDocumentUri("fixture.documents", id)
        provider.files.getValue(id).writeText("abc")
        database
            .downloadDao()
            .insert(
                DownloadRecord(
                    key,
                    uri.toString(),
                    "test",
                    byteArrayOf(),
                    0,
                    bytesWritten = 3,
                    totalBytes = 6,
                    etag = "\"v1\"",
                )
            )
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("ETag", "\"v1\"")
                .setHeader("Content-Range", "bytes 3-5/6")
                .setBody("def")
        )
        transferDownload(
            context,
            database.downloadDao(),
            key,
            server.url("/file").toString(),
            uri,
            OkHttpClient(),
        )
        assertEquals("bytes=3-", server.takeRequest().getHeader("Range"))
        assertEquals("abcdef", provider.files.getValue(id).readText())
        assertEquals(6, database.downloadDao().get(key)!!.bytesWritten)
    }

    @Test
    fun `servers ignoring ranges trigger a clean restart`() = runBlocking {
        val id = provider.createDocument("root", "application/octet-stream", "download.part")
        val uri = DocumentsContract.buildDocumentUri("fixture.documents", id)
        provider.files.getValue(id).writeText("stale")
        database
            .downloadDao()
            .insert(
                DownloadRecord(
                    key,
                    uri.toString(),
                    "test",
                    byteArrayOf(),
                    0,
                    bytesWritten = 5,
                    etag = "\"v1\"",
                )
            )
        server.enqueue(MockResponse().setHeader("ETag", "\"v2\"").setBody("fresh"))
        transferDownload(
            context,
            database.downloadDao(),
            key,
            server.url("/file").toString(),
            uri,
            OkHttpClient(),
        )
        assertEquals("fresh", provider.files.getValue(id).readText())
    }
}

/** Document IDs deliberately have no relationship to their display names or parent paths. */
private class OpaqueProvider : DocumentsProvider() {
    val files = mutableMapOf<String, File>()
    val names = mutableMapOf<String, String>()

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: emptyArray())

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val columns =
            projection
                ?: arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_FLAGS,
                    DocumentsContract.Document.COLUMN_SIZE,
                )
        return MatrixCursor(columns).apply {
            addRow(
                columns
                    .map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> names[documentId]
                            DocumentsContract.Document.COLUMN_MIME_TYPE ->
                                "application/octet-stream"
                            DocumentsContract.Document.COLUMN_FLAGS ->
                                DocumentsContract.Document.FLAG_SUPPORTS_WRITE or
                                    DocumentsContract.Document.FLAG_SUPPORTS_RENAME
                            DocumentsContract.Document.COLUMN_SIZE ->
                                files[documentId]?.length() ?: 0
                            else -> null
                        }
                    }
                    .toTypedArray()
            )
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor = MatrixCursor(projection ?: emptyArray())

    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String,
    ): String {
        val id = UUID.randomUUID().toString()
        names[id] = displayName
        files[id] = File(context!!.cacheDir, id).apply { createNewFile() }
        return id
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val unique = if (names.values.contains(displayName)) "$displayName (1)" else displayName
        val newId = UUID.randomUUID().toString()
        files[newId] = files.remove(documentId)!!
        names.remove(documentId)
        names[newId] = unique
        return newId
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor =
        ParcelFileDescriptor.open(files.getValue(documentId), ParcelFileDescriptor.parseMode(mode))
}
