package com.mattzilla30.mbooru.ui.compose.browse

import androidx.lifecycle.SavedStateHandle
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.TagFilter
import com.mattzilla30.mbooru.ui.compose.navigation.browseTabs
import org.junit.Assert.*
import org.junit.Test

class BrowseStateTest {
    @Test
    fun `search draft and mode survive restored state`() {
        val saved = SavedStateHandle()
        val first = BrowseStateViewModel(saved)
        first.query("landscape rating:safe")
        first.mode(2)
        val restored = BrowseStateViewModel(saved)
        assertEquals("landscape rating:safe", restored.state.value.query)
        assertEquals(2, restored.state.value.mode)
    }

    @Test
    fun `filters use service specific ordering and ignore removed saved tags`() {
        val model = PostFiltersViewModel(SavedStateHandle())
        model.bind(7)
        val tag = TagFilter(uid = 11, booruUid = 7, name = "landscape")
        model.toggle(11)
        model.set("order", "score")
        model.set("rating", "safe")
        assertEquals(
            "landscape sort:score rating:safe",
            model.query(listOf(tag), Values.BOORU_TYPE_GEL),
        )
        assertEquals(
            "landscape order:score rating:safe",
            model.query(listOf(tag), Values.BOORU_TYPE_DAN),
        )
        assertEquals("order:score rating:safe", model.query(emptyList(), Values.BOORU_TYPE_DAN))
        model.bind(9)
        assertEquals("", model.query(listOf(tag), Values.BOORU_TYPE_DAN))
    }

    @Test
    fun `filter selection restores within a site`() {
        val saved = SavedStateHandle()
        PostFiltersViewModel(saved).apply {
            bind(7)
            toggle(11)
            set("threshold", "3")
        }
        val restored = PostFiltersViewModel(saved)
        restored.bind(7)
        assertEquals(setOf(11L), restored.selected)
        assertEquals("3", restored.get("threshold"))
    }

    @Test
    fun `navigation destinations remain restricted by the selected service`() {
        assertEquals(listOf(R.id.nav_posts), browseTabs(Values.BOORU_TYPE_SHIMMIE).map { it.id })
        assertEquals(
            listOf(R.id.nav_posts, R.id.nav_tags),
            browseTabs(Values.BOORU_TYPE_GEL).map { it.id },
        )
        assertFalse(browseTabs(Values.BOORU_TYPE_SANKAKU).any { it.id == R.id.nav_artists })
        assertEquals(5, browseTabs(Values.BOORU_TYPE_DAN).size)
    }
}
