// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.core.model.ArtworkRating
import com.mattzilla30.mbooru.core.model.normalizedRating
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.api.rule34.ArtworkQuery
import com.mattzilla30.mbooru.data.api.rule34.DiscoverySort
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Date
import com.mattzilla30.mbooru.data.model.common.History
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.repository.post.PostRepositoryImpl
import com.mattzilla30.mbooru.data.repository.suggestion.SuggestionRepositoryImpl
import com.mattzilla30.mbooru.extension.safeNetworkMessage
import java.util.Calendar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Suppress("TooManyFunctions")
class UnifiedSearchViewModel(
    private val database: MyDatabase,
    private val apis: BooruApis,
    private val settings: SettingsRepository,
    private val site: Booru,
    private val saved: SavedStateHandle,
) : ViewModel() {
    val text = saved.getStateFlow("text", "")
    val debounced = saved.getStateFlow("debounced", false)
    private val active = MutableStateFlow(false)
    private val consumers = mutableSetOf<Any>()
    private var manuallyCancelled = false
    private val currentSite = MutableStateFlow(site)
    private val refreshRevision = MutableStateFlow(0L)
    private var consumedRefresh = 0L
    private var previousSession: Pair<ArtworkQuery, Booru>? = null
    private val committed =
        MutableStateFlow(
            ArtworkQuery(
                site.uid,
                saved["committed"] ?: "",
                settings.safeMode || (saved["safe_only"] ?: false),
                DiscoverySort.entries.firstOrNull { it.name == saved.get<String>("sort") }
                    ?: DiscoverySort.LATEST,
            )
        )
    val query = committed.asStateFlow()
    private val mutableMessage = MutableStateFlow("")
    val message = mutableMessage.asStateFlow()
    private val mutableRecommendations = MutableStateFlow<List<String>>(emptyList())
    val recommendations = mutableRecommendations.asStateFlow()
    private val mutableRefreshing = MutableStateFlow(false)
    val refreshing = mutableRefreshing.asStateFlow()
    private var recommendationsJob: Job? = null
    private var recommendationRevision = 0L
    val history = database.historyDao().observeHistory(site.uid)
    val posts =
        combine(committed, active, currentSite, refreshRevision) { state, enabled, source, revision
                ->
                WorkspaceRequest(state, enabled, source, revision)
            }
            .distinctUntilChanged()
            .flatMapLatest { request ->
                val state = request.query
                val source = request.source
                val revision = request.revision
                if (!request.enabled) emptyFlow<PagingData<Post>>()
                else {
                    val force = revision != consumedRefresh
                    consumedRefresh = revision
                    val session = state to source
                    val allowAutoRefresh = session != previousSession
                    previousSession = session
                    var tags = state.normalizedTags
                    if (source.type == Values.BOORU_TYPE_RULE34) tags = state.serverTags()
                    val now = Calendar.getInstance()
                    val date =
                        ActionPost.Date(
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH) + 1,
                            now.get(Calendar.DAY_OF_MONTH),
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH) + 1,
                            now.get(Calendar.DAY_OF_MONTH),
                        )
                    PostRepositoryImpl(
                            database,
                            apis,
                            settings,
                            force,
                            saved["grid_index"] ?: 0,
                            allowAutoRefresh = allowAutoRefresh,
                        )
                        .getPosts(
                            ActionPost(
                                source,
                                limit = settings.pageLimit,
                                query = tags,
                                isSafeMode = state.safeOnly,
                                date = date,
                            )
                        )
                }
            }
            .cachedIn(viewModelScope)
    val suggestions =
        combine(text, committed) { value, state -> value to state.safeOnly }
            .debounce(SUGGESTION_DELAY_MS)
            .flatMapLatest { (value, safe) ->
                flow {
                    val prefix =
                        ArtworkQuery.normalize(value).substringAfterLast(' ').removePrefix("-")
                    if (prefix.length < 2) emit(emptyList())
                    else {
                        val cached =
                            database
                                .postDao()
                                .recentMetadata(site.uid, safe, METADATA_SAMPLE_SIZE)
                                .filter {
                                    !safe || normalizedRating(it.rating) == ArtworkRating.SAFE
                                }
                        val local =
                            (cached.flatMap { it.tags }.map { it.name } +
                                    mutableRecommendations.value)
                                .distinct()
                                .filter { it.startsWith(prefix, true) }
                                .take(SUGGESTION_LIMIT)
                        if (
                            local.isNotEmpty() ||
                                site.type in
                                    setOf(Values.BOORU_TYPE_RULE34, Values.BOORU_TYPE_SHIMMIE) ||
                                safe
                        )
                            emit(local)
                        else {
                            val tags =
                                SuggestionRepositoryImpl(apis)
                                    .fetchSuggestions(
                                        ActionTag(
                                            site,
                                            query = "$prefix*",
                                            order = "count",
                                            limit = SUGGESTION_LIMIT,
                                        )
                                    )
                                    .orEmpty()
                                    .map { it.name }
                            emit(tags)
                        }
                    }
                }
            }
            .catch { failure ->
                if (failure is CancellationException) throw failure else emit(emptyList())
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(OBSERVATION_TIMEOUT_MS),
                emptyList(),
            )

    init {
        viewModelScope.launch {
            combine(text, debounced, active) { value, enabled, visible ->
                    Triple(value, enabled, visible)
                }
                .collectLatest { (value, enabled, visible) ->
                    if (
                        enabled &&
                            visible &&
                            ArtworkQuery.normalize(value) != committed.value.normalizedTags
                    ) {
                        delay(SEARCH_DELAY_MS)
                        submit(value)
                    }
                }
        }
    }

    fun edit(value: String) {
        saved["text"] = value
    }

    fun debounce(value: Boolean) {
        saved["debounced"] = value
    }

    fun safe(value: Boolean) {
        val effective = value || settings.safeMode
        saved["safe_only"] = effective
        if (committed.value.safeOnly != effective) {
            saved["grid_index"] = 0
            recommendationsJob?.cancel()
            mutableRecommendations.value = emptyList()
            committed.update { it.copy(safeOnly = effective) }
        }
    }

    fun submit(value: String = text.value) {
        val normalized = ArtworkQuery.normalize(value)
        val sameQuery = normalized == committed.value.normalizedTags
        saved["text"] = normalized
        saved["committed"] = normalized
        if (!sameQuery) saved["grid_index"] = 0
        committed.update { it.copy(tags = normalized) }
        if (sameQuery) refresh()
        else {
            manuallyCancelled = false
            active.value = consumers.isNotEmpty()
        }
        if (normalized.isNotBlank())
            viewModelScope.launch {
                database.historyDao().insert(History(booruUid = site.uid, query = normalized))
            }
    }

    fun recordGridIndex(index: Int) {
        saved["grid_index"] = index
    }

    fun refresh() {
        mutableMessage.value = ""
        manuallyCancelled = false
        refreshRevision.update { it + 1 }
        active.value = consumers.isNotEmpty()
    }

    fun sort(value: DiscoverySort) {
        if (value != DiscoverySort.LATEST && site.type != Values.BOORU_TYPE_RULE34) return
        if (committed.value.sort != value) saved["grid_index"] = 0
        saved["sort"] = value.name
        committed.update { it.copy(sort = value) }
    }

    fun updateSite(value: Booru) {
        currentSite.value = value
    }

    fun attach(owner: Any) {
        consumers.add(owner)
        active.value = !manuallyCancelled
    }

    fun detach(owner: Any) {
        consumers.remove(owner)
        active.value = consumers.isNotEmpty() && !manuallyCancelled
        if (consumers.isEmpty()) recommendationsJob?.cancel()
    }

    fun cancel() {
        manuallyCancelled = true
        active.value = false
        recommendationsJob?.cancel()
        mutableRefreshing.value = false
        mutableMessage.value = "Search canceled. Submit to search again."
    }

    fun addTag(tag: String) {
        val terms =
            ArtworkQuery.normalize(text.value).split(' ').filter { it.isNotBlank() }.toMutableList()
        terms += tag
        submit(terms.distinct().joinToString(" "))
    }

    fun removeTag(tag: String) {
        submit(ArtworkQuery.normalize(text.value).split(' ').filter { it != tag }.joinToString(" "))
    }

    @Suppress("TooGenericExceptionCaught")
    fun refreshRecommendations() {
        val revision = ++recommendationRevision
        recommendationsJob?.cancel()
        recommendationsJob = viewModelScope.launch {
            mutableRefreshing.value = true
            mutableMessage.value = ""
            try {
                val state = committed.value
                val posts =
                    if (site.type == Values.BOORU_TYPE_RULE34)
                        apis.rule34
                            .fetch(
                                currentSite.value,
                                ArtworkQuery(
                                    site.uid,
                                    safeOnly = state.safeOnly,
                                    sort = DiscoverySort.RANDOM,
                                ),
                                0,
                                RECOMMENDATION_SAMPLE_SIZE,
                            )
                            .posts
                    else
                        database
                            .postDao()
                            .recentMetadata(site.uid, state.safeOnly, METADATA_SAMPLE_SIZE)
                            .filter {
                                !state.safeOnly || normalizedRating(it.rating) == ArtworkRating.SAFE
                            }
                val old = mutableRecommendations.value.toSet()
                val tags =
                    posts
                        .flatMap { it.tags }
                        .map { it.name }
                        .filter { it.isNotBlank() && ':' !in it && !it.startsWith('-') }
                        .distinct()
                        .shuffled()
                mutableRecommendations.value =
                    (tags.filter { it !in old } + tags.filter { it in old }).take(
                        RECOMMENDATION_LIMIT
                    )
                if (tags.isEmpty())
                    mutableMessage.value =
                        "No displayable post tags are available yet. Refresh the feed or connect this site."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableMessage.value = error.safeNetworkMessage()
            } finally {
                if (revision == recommendationRevision) mutableRefreshing.value = false
            }
        }
    }
}

private const val OBSERVATION_TIMEOUT_MS = 5_000L

private const val SUGGESTION_DELAY_MS = 250L
private const val SEARCH_DELAY_MS = 700L
private const val METADATA_SAMPLE_SIZE = 100
private const val SUGGESTION_LIMIT = 8
private const val RECOMMENDATION_SAMPLE_SIZE = 20
private const val RECOMMENDATION_LIMIT = 12

private data class WorkspaceRequest(
    val query: ArtworkQuery,
    val enabled: Boolean,
    val source: Booru,
    val revision: Long,
)
