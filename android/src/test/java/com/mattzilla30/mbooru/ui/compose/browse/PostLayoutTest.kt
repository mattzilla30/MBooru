package com.mattzilla30.mbooru.ui.compose.browse

import androidx.paging.PagingData
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.post.PostRepository
import com.mattzilla30.mbooru.ui.viewmodel.PostViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class PostLayoutTest {
    @Test
    fun `stable post identity includes site and search query`() {
        val first = testPost(10)
        assertNotEquals(postKey(first), postKey(first.copy(booruUid = 2)))
        assertNotEquals(postKey(first), postKey(first.copy(query = "another")))
        assertEquals(postKey(first), postKey(first.copy(index = 99)))
    }

    @Test
    fun `image ratios retain fixed and staggered bounds without division by zero`() {
        assertEquals(4f / 3f, postAspectRatio(testPost(1), "4:3"), .0001f)
        assertEquals(21f / 9f, postAspectRatio(testPost(1, 10000, 1), null), .0001f)
        assertEquals(9f / 21f, postAspectRatio(testPost(1, 1, 10000), null), .0001f)
        assertTrue(postAspectRatio(testPost(1, 0, 0), "0:0").isFinite())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `mutating the request object produces a new paging request without changing the previous one`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val received = mutableListOf<ActionPost>()
                val repository =
                    object : PostRepository {
                        override fun getPosts(
                            action: ActionPost
                        ): Flow<PagingData<com.mattzilla30.mbooru.data.model.common.Post>> = flow {
                            received += action
                            emit(PagingData.empty())
                        }
                    }
                val model = PostViewModel(repository)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    model.posts.collect {}
                }
                val action =
                    ActionPost(
                        Booru(
                            name = "test",
                            scheme = "https",
                            host = "example.org",
                            type = Values.BOORU_TYPE_DAN,
                        ),
                        limit = 30,
                        date = ActionPost.Date(2026, 1, 1, 2026, 1, 2),
                    )
                model.show(action)
                runCurrent()
                action.limit = 50
                action.date.dayEnd = 5
                model.show(action)
                runCurrent()
                assertEquals(listOf(30, 50), received.map { it.limit })
                assertEquals(2, received.first().date.dayEnd)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
