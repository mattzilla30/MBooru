package com.mattzilla30.mbooru.data.repository.post

import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SANKAKU
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POPULAR
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.api.DanbooruApi
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Next
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.moebooru.PostMoe
import com.mattzilla30.mbooru.data.model.sankaku.RefreshTokenBody
import retrofit2.HttpException
import retrofit2.Response

class PostRemoteMediator(
    private val action: ActionPost,
    private val db: MyDatabase,
    private val booruApis: BooruApis,
    private val settings: com.mattzilla30.mbooru.app.SettingsRepository,
    private val forceRefresh: Boolean = false,
    private val allowAutoRefresh: Boolean = true,
) : RemoteMediator<Int, Post>() {

    private val isFavored = action.isFavoredQuery()
    private var lastResponseSize = action.limit
    private var pendingRule34Next: Next? = null
    private val postDao = db.postDao()

    private suspend fun nextIndex(): Int = db.postDao().getNextIndex(action.booru.uid, action.query)

    private fun nextPage(nextIndex: Int): Int {
        return when (action.booru.type) {
            in arrayOf(BOORU_TYPE_GEL, BOORU_TYPE_GEL_LEGACY) -> nextIndex / action.limit
            else -> nextIndex / action.limit + 1
        }
    }

    private suspend fun nextSankakuKey(): String? =
        db.nextDao().getNext(action.booru.uid, action.query)?.next

    override suspend fun initialize(): InitializeAction {
        val refreshOnOpen = forceRefresh || (allowAutoRefresh && settings.autoRefresh)
        return if (refreshOnOpen || postDao.getFirstPostRaw(action.booru.uid, action.query) == null)
            InitializeAction.LAUNCH_INITIAL_REFRESH
        else InitializeAction.SKIP_INITIAL_REFRESH
    }

    override suspend fun load(loadType: LoadType, state: PagingState<Int, Post>): MediatorResult {
        pendingRule34Next = null
        val posts =
            when (loadType) {
                LoadType.REFRESH -> {
                    try {
                        fetchPosts(0, null)
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        return MediatorResult.Error(e)
                    }
                }
                LoadType.PREPEND -> {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
                LoadType.APPEND -> {
                    val index = nextIndex()
                    if (!hasMore() && index != 0) {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }
                    try {
                        fetchPosts(index, nextSankakuKey())
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        return MediatorResult.Error(e)
                    }
                }
            }
        try {
            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    postDao.deletePosts(action.booru.uid, action.query)
                }
                postDao.insert(posts)
                pendingRule34Next?.let { db.nextDao().insert(it) }
                storePageBoundary()
            }
        } catch (failure: Exception) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            return MediatorResult.Error(failure)
        }
        return MediatorResult.Success(endOfPaginationReached = !hasMore())
    }

    /** Keep terminal pages terminal when a cached pager is recreated after leaving the screen. */
    private suspend fun storePageBoundary() {
        if (action.pageType != Values.PAGE_TYPE_POSTS) return
        if (action.booru.type in setOf(Values.BOORU_TYPE_RULE34, BOORU_TYPE_SANKAKU)) return
        db.nextDao()
            .insert(
                Next(
                    booruUid = action.booru.uid,
                    query = action.query,
                    next = if (lastResponseSize == action.limit) null else CACHED_PAGE_END,
                )
            )
    }

    private suspend fun hasMore(): Boolean {
        return when {
            action.booru.type == Values.BOORU_TYPE_RULE34 -> !nextSankakuKey().isNullOrEmpty()
            action.booru.type == BOORU_TYPE_SANKAKU -> !nextSankakuKey().isNullOrEmpty()
            action.pageType == PAGE_TYPE_POPULAR -> false
            else -> lastResponseSize == action.limit && nextSankakuKey() != CACHED_PAGE_END
        }
    }

    private suspend fun fetchPosts(nextIndex: Int, next: String?): List<Post> {
        return when (action.booru.type) {
            Values.BOORU_TYPE_RULE34 -> fetchPostsRule34(next?.toIntOrNull() ?: 0, nextIndex)
            BOORU_TYPE_DAN -> {
                if (action.booru.host in DanbooruApi.E621_HOSTS) {
                    fetchPostsDanE621(nextPage(nextIndex), nextIndex)
                } else {
                    fetchPostsDan(nextPage(nextIndex), nextIndex)
                }
            }
            BOORU_TYPE_DAN1 -> fetchPostsDan1(nextPage(nextIndex), nextIndex)
            BOORU_TYPE_MOE -> fetchPostsMoe(nextPage(nextIndex), nextIndex)
            BOORU_TYPE_SANKAKU -> fetchPostsSankaku(next, nextIndex)
            BOORU_TYPE_GEL -> fetchPostsGel(nextPage(nextIndex), nextIndex)
            BOORU_TYPE_GEL_LEGACY -> fetchPostsGelLegacy(nextPage(nextIndex), nextIndex)
            else -> fetchPostsShimmie(nextPage(nextIndex), nextIndex)
        }
    }

    private suspend fun fetchPostsRule34(page: Int, index: Int): List<Post> {
        val query =
            com.mattzilla30.mbooru.data.api.rule34.ArtworkQuery(
                action.booru.uid,
                (if (action.pageType == PAGE_TYPE_POPULAR) "" else action.query) +
                    " " +
                    action.booru.getBlacklistsString(),
                action.isSafeMode,
                if (action.pageType == PAGE_TYPE_POPULAR)
                    com.mattzilla30.mbooru.data.api.rule34.DiscoverySort.SCORE
                else com.mattzilla30.mbooru.data.api.rule34.DiscoverySort.LATEST,
            )
        val response = booruApis.rule34.fetch(action.booru, query, page, action.limit, index)
        if (response.received > 0 && response.posts.isEmpty()) {
            throw com.mattzilla30.mbooru.data.api.rule34.Rule34Exception(
                response.summary() + " Change the tags or rating filter and retry."
            )
        }
        lastResponseSize = response.received
        pendingRule34Next =
            Next(
                booruUid = action.booru.uid,
                query = action.query,
                next = if (response.received >= action.limit) (page + 1).toString() else null,
            )
        return response.posts.map { it.copy(query = action.query) }
    }

    private suspend fun fetchPostsDan(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.danApi.getPosts(action.getDanPostsUrl(page))
        val raw = response.checkedBody() ?: listOf()
        lastResponseSize = raw.size
        val posts = raw.filter { it.id != -1 }
        return posts.mapIndexed { index, postDan ->
            postDan.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                scheme = action.booru.scheme,
                host = action.booru.host,
                index = indexInNext + index,
                isFavored = isFavored,
            )
        }
    }

    private suspend fun fetchPostsDanE621(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.danApi.getPostsE621(action.getDanPostsUrl(page))
        val raw = response.checkedBody()?.posts ?: listOf()
        lastResponseSize = raw.size
        val posts = raw.filter { it.id != -1 }
        return posts.mapIndexed { index, post ->
            post.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                index = indexInNext + index,
            )
        }
    }

    private suspend fun fetchPostsDan1(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.dan1Api.getPosts(action.getDan1PostsUrl(page))
        val raw = response.checkedBody()?.toMutableList()
        lastResponseSize = raw?.size ?: 0
        return raw?.mapIndexed { index, post ->
            post.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                scheme = action.booru.scheme,
                host = action.booru.host,
                index = indexInNext + index,
                isFavored = isFavored,
            )
        } ?: listOf()
    }

    private suspend fun fetchPostsMoe(page: Int, indexInNext: Int): List<Post> {
        if (action.pageType == Values.PAGE_TYPE_POSTS) {
            val votes: Map<String, Int>
            val tagTypes: Map<String, String>
            val posts: List<PostMoe>
            if (action.booru.host == "lolibooru.moe") {
                val response =
                    booruApis.moeApi.getPostsLoli(action.getPostsMoeUrl(page)).checkedBody()
                if (response == null) {
                    lastResponseSize = 0
                    return emptyList()
                }
                votes = mapOf()
                tagTypes = response.tags
                posts = response.posts
            } else {
                val response = booruApis.moeApi.getPosts(action.getPostsMoeUrl(page)).checkedBody()
                if (response == null) {
                    lastResponseSize = 0
                    return emptyList()
                }
                votes = response.votes
                tagTypes = response.tags
                posts = response.posts
            }
            lastResponseSize = posts.size
            return posts.mapIndexed { index, postMoe ->
                postMoe.toPost(
                    booruUid = action.booru.uid,
                    query = action.query,
                    scheme = action.booru.scheme,
                    host = action.booru.host,
                    index = indexInNext + index,
                    tagTypes = tagTypes,
                    votes = votes,
                )
            }
        } else {
            val posts = booruApis.moeApi.getPostsPopular(action.getPopularMoeUrl()).checkedBody()
            if (posts == null) {
                lastResponseSize = 0
                return emptyList()
            }
            lastResponseSize = posts.size
            return posts.mapIndexed { index, postMoe ->
                postMoe.toPost(
                    booruUid = action.booru.uid,
                    query = action.query,
                    scheme = action.booru.scheme,
                    host = action.booru.host,
                    index = indexInNext + index,
                    tagTypes = mapOf(),
                    votes = mapOf(),
                )
            }
        }
    }

    private suspend fun fetchPostsGel(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.gelApi.getPosts(action.getGelPostsUrl(page))
        val raw = response.checkedBody()?.posts?.toMutableList()
        lastResponseSize = raw?.size ?: 0
        return raw?.mapIndexed { index, post ->
            post.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                scheme = action.booru.scheme,
                host = action.booru.host,
                index = indexInNext + index,
                isFavored = isFavored,
            )
        } ?: listOf()
    }

    private suspend fun fetchPostsGelLegacy(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.gelApi.getPostsLegacy(action.getGelPostsUrl(page))
        val raw = response.checkedBody()?.posts?.toMutableList()
        lastResponseSize = raw?.size ?: 0
        return raw?.mapIndexed { index, post ->
            post.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                scheme = action.booru.scheme,
                host = action.booru.host,
                index = indexInNext + index,
            )
        } ?: listOf()
    }

    private suspend fun fetchPostsShimmie(page: Int, indexInNext: Int): List<Post> {
        val response = booruApis.shimmieApi.getPosts(action.getShimmiePostsUrl(page))
        val data = response.checkedBody()
        val posts =
            when {
                data?.post != null -> {
                    data.post.mapIndexed { index, post ->
                        post.toPost(
                            booruUid = action.booru.uid,
                            query = action.query,
                            scheme = action.booru.scheme,
                            host = action.booru.host,
                            index = indexInNext + index,
                        )
                    }
                }
                data?.tag != null -> {
                    data.tag.mapIndexed { index, post ->
                        post.toPost(
                            booruUid = action.booru.uid,
                            query = action.query,
                            scheme = action.booru.scheme,
                            host = action.booru.host,
                            index = indexInNext + index,
                        )
                    }
                }
                else -> listOf()
            }
        lastResponseSize = posts.size
        return posts
    }

    private suspend fun fetchPostsSankaku(next: String?, indexInNext: Int): List<Post> {
        val url =
            if (next.isNullOrEmpty()) action.getSankakuPostsUrl()
            else action.getSankakuPostsUrlNext(next)
        val auth = action.booru.user?.getAuth
        val response =
            if (auth.isNullOrBlank()) booruApis.sankakuApi.getPosts(url)
            else booruApis.sankakuApi.getPostsAuth(url, auth)
        if (response.code() == 401) {
            refreshSankakuToken()
            throw (HttpException(response))
        }
        val data = response.checkedBody()
        val raw = data?.posts
        lastResponseSize = raw?.size ?: 0
        db.nextDao()
            .insert(
                Next(booruUid = action.booru.uid, query = action.query, next = data?.meta?.next)
            )
        return raw?.mapIndexed { index, post ->
            post.toPost(
                booruUid = action.booru.uid,
                query = action.query,
                scheme = action.booru.scheme,
                host = action.booru.host,
                index = indexInNext + index,
            )
        } ?: listOf()
    }

    private suspend fun refreshSankakuToken() {
        val refreshToken = action.booru.user?.refreshToken
        if (refreshToken != null) {
            try {
                val user =
                    booruApis.sankakuApi
                        .refreshToken(
                            action.booru.getSankakuTokenUrl(),
                            RefreshTokenBody(refreshToken),
                        )
                        .body()
                        ?.toUser()
                if (user != null) {
                    action.booru.user = user
                    db.booruDao().update(action.booru)
                }
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
            }
        }
    }
}

private const val CACHED_PAGE_END = "mbooru:paging:end"

/** Failed requests must remain retryable instead of being cached as empty search results. */
private fun <T> Response<T>.checkedBody(): T? {
    if (!isSuccessful) throw HttpException(this)
    return body()
}
