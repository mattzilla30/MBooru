package com.mattzilla30.mbooru.data.utils

import org.junit.Assert.*
import org.junit.Test

class MediaUrlTest {
    @Test
    fun `engine URLs resolve relative and protocol relative network addresses`() {
        assertEquals(
            "https://example.org/image.png",
            "/image.png".toSafeUrl("https", "example.org"),
        )
        assertEquals(
            "https://cdn.example.org/image.png",
            "//cdn.example.org/image.png".toSafeUrl("https", "example.org"),
        )
        assertEquals("https://example.org/image.png", "image.png".toSafeUrl("https", "example.org"))
    }

    @Test
    fun `remote media cannot select files intents credentials or malformed schemes`() {
        for (url in
            listOf(
                "file:///data/private.png",
                "content://private/image.png",
                "javascript:alert(1)",
                "intent://viewer",
                "https://user:secret@example.org/image.png",
                "\nhttps://example.org/\u0000.png",
            )) {
            assertEquals("", url.toSafeUrl("https", "example.org"))
        }
    }
}
