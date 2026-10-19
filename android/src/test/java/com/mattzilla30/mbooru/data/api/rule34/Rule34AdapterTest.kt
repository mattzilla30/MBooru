package com.mattzilla30.mbooru.data.api.rule34

import org.junit.*
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Rule34AdapterTest {
    @Test
    fun copiedCredentialsBlankKeyPreservationAndEncoding() {
        val credentials = Rule34Credentials.parse("", "&api_key=a%2Bb%2F%3D&user_id=123")
        assertEquals("a+b/=", credentials.key)
        assertEquals("a+b/=", Rule34Credentials.parse("123", "", credentials.key).key)
        val url =
            Rule34Adapter.requestUrl(
                credentials,
                ArtworkQuery(1, "landscape, -sky", sort = DiscoverySort.RANDOM),
                2,
                40,
            )
        assertEquals("123", url.queryParameter("user_id"))
        assertEquals("a+b/=", url.queryParameter("api_key"))
        assertEquals("2", url.queryParameter("pid"))
        assertTrue(url.queryParameter("tags")!!.contains("sort:random"))
        assertFalse(credentials.toString().contains(credentials.key))
        listOf("0", "name", "-1").forEach { value ->
            assertThrows(Rule34Exception::class.java) { Rule34Credentials.parse(value, "key") }
        }
        assertThrows(Rule34Exception::class.java) { Rule34Credentials.parse("1", "") }
        assertThrows(Rule34Exception::class.java) { Rule34Credentials.parse("1", "&api_key=%ZZ") }
    }

    @Test
    fun ratingsMetadataAndTagMatchingAreVerifiedIndependently() {
        val json =
            "[" +
                listOf(
                        post(1, "g"),
                        post(2, "s"),
                        post(3, "e"),
                        post(4, "q"),
                        post(5, "unknown"),
                        post(6, "s", tags = "sky"),
                        post(7, "s", url = "file:///etc/passwd"),
                    )
                    .joinToString(",") +
                "]"
        val page = Rule34Adapter.parse(json, 99, ArtworkQuery(99, "land* -cloud"))
        assertEquals(listOf(1, 2), page.posts.map { it.id })
        assertEquals(3, page.ratingHidden)
        assertEquals(1, page.invalid)
        assertEquals(1, page.mismatched)
        assertTrue(page.posts.all { it.booruUid == 99L })
        assertEquals("g", page.posts.first().rating)
        assertEquals(0, Rule34Adapter.parse("[]", 1, ArtworkQuery(1)).received)
        listOf(null, "", "null", "{}", "<html>challenge</html>", "[").forEach { text ->
            assertThrows(Rule34Exception::class.java) {
                Rule34Adapter.parse(text, 1, ArtworkQuery(1))
            }
        }
    }

    @Test
    fun safeModeOverridesExplicitQueriesAndComplexOperatorsStayServerOwned() {
        assertFalse(ArtworkQuery(1, "rating:explicit").serverTags().contains("rating:explicit "))
        assertTrue(ArtworkQuery(1, "rating:explicit").serverTags().contains("-rating:explicit"))
        assertTrue(matchesRule34Tags(setOf("sky"), "(landscape ~sky)"))
        assertFalse(matchesRule34Tags(setOf("sky"), "landscape"))
        assertEquals("", Rule34Adapter.mediaUrl("https://rule34.xxx@evil.invalid/a.jpg"))
        assertEquals("", Rule34Adapter.mediaUrl("https://localhost/a.jpg"))
    }

    private fun post(
        id: Int,
        rating: String,
        tags: String = "landscape",
        url: String = "https://images.rule34.xxx/image.jpg",
    ) =
        """{"id":$id,"rating":"$rating","width":100,"height":200,"tags":"$tags","file_url":"$url","preview_url":"https://images.rule34.xxx/preview.jpg"}"""
}
