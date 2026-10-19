package com.mattzilla30.mbooru.data.licenses

import org.junit.Assert.*
import org.junit.Test

class LicenseCatalogTest {
    @Test
    fun byteRangesSupportUnicodeAndKeepMultipleLicensesForOneDependency() {
        val first = "Copyright é\nLicense one"
        val second = "License two"
        val bytes = (first + second).toByteArray(Charsets.UTF_8)
        val offset = first.toByteArray(Charsets.UTF_8).size
        val metadata =
            "0:$offset Library A\n$offset:${second.length} Library A\n0:$offset Library A\n"
        val license = LicenseCatalog.parse(metadata, bytes).single()
        assertEquals("Library A", license.name)
        assertEquals("$first\n\n$second", license.text)
    }

    @Test
    fun malformedAndOutOfRangeMetadataCannotReadUnrelatedBytes() {
        listOf(
                "bad",
                "-1:4 Library",
                "0:999 Library",
                "0:0 Library",
                "2147483647:4 Library",
                "0:4 ",
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) {
                    LicenseCatalog.parse(it, "text".toByteArray())
                }
            }
    }
}
