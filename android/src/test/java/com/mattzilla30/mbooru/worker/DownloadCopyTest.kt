package com.mattzilla30.mbooru.worker

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InterruptedIOException
import org.junit.Assert.*
import org.junit.Test

class DownloadCopyTest {
    @Test fun `copies a large download without dropping the last partial chunk`() {
        val bytes = ByteArray(50003) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        copyDownload(ByteArrayInputStream(bytes), output)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test fun `cancellation between chunks stops writing the download`() {
        val output = object : ByteArrayOutputStream() {
            override fun write(bytes: ByteArray, offset: Int, length: Int) {
                super.write(bytes, offset, length)
                Thread.currentThread().interrupt()
            }
        }
        try {
            try {
                copyDownload(ByteArrayInputStream(ByteArray(50003)), output)
                fail("Interrupted download must stop")
            } catch (_: InterruptedIOException) {
                assertTrue(output.size() in 1 until 50003)
            }
        } finally {
            Thread.interrupted()
        }
    }
}
