package com.mattzilla30.mbooru.ui.compose.detail

import com.mattzilla30.mbooru.data.model.common.TagBase
import org.junit.Assert.assertEquals
import org.junit.Test

class RelatedPostsTest {
    @Test
    fun relatedSearchKeepsIdentifyingTagsAndExcludesMetadata() {
        val tags =
            listOf(
                TagBase("landscape", 0),
                TagBase("highres", 5),
                TagBase("creator", 1),
                TagBase("hero", 4),
                TagBase("series", 3),
                TagBase("blue_sky", 0),
                TagBase("clouds", 0),
                TagBase("sun", 0),
                TagBase("creator", 1),
                TagBase(" ", 1),
            )
        assertEquals("creator hero series landscape blue_sky clouds", relatedPostQuery(tags))
    }

    @Test
    fun uncategorizedAnonymousPostsCanStillFindRelatedContent() {
        assertEquals(
            "landscape sky",
            relatedPostQuery(listOf(TagBase("landscape"), TagBase("sky"))),
        )
        assertEquals("", relatedPostQuery(emptyList()))
    }
}
