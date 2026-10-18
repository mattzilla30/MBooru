package com.mattzilla30.mbooru.okhttp

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTest {
    @Test
    fun `unknown and empty lengths do not divide by zero`() {
        assertEquals(0, downloadProgress(100, 0, false))
        assertEquals(0, downloadProgress(100, -1, false))
        assertEquals(100, downloadProgress(100, -1, true))
    }

    @Test
    fun `large transfers do not overflow and invalid progress is clamped`() {
        assertEquals(50, downloadProgress(Long.MAX_VALUE / 2, Long.MAX_VALUE, false))
        assertEquals(0, downloadProgress(-1, 100, false))
        assertEquals(100, downloadProgress(150, 100, false))
    }
}
