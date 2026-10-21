/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.mattzilla30.mbooru.ui.compose.browse

import android.annotation.SuppressLint
import android.app.SharedElementCallback
import android.content.*
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.PAGE_TYPE
import com.mattzilla30.mbooru.app.Keys.POST_POSITION
import com.mattzilla30.mbooru.app.Keys.POST_QUERY
import com.mattzilla30.mbooru.app.Settings.PAGE_LIMIT_KEY
import com.mattzilla30.mbooru.app.Settings.SAFE_MODE_KEY
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SANKAKU
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POPULAR
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POSTS
import com.mattzilla30.mbooru.data.action.ActionPost
import com.mattzilla30.mbooru.data.database.MyDatabase
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.TagFilter
import com.mattzilla30.mbooru.data.repository.post.PostRepositoryImpl
import com.mattzilla30.mbooru.data.repository.tagfilter.TagFilterRepositoryImpl
import com.mattzilla30.mbooru.extension.downloadByAdm
import com.mattzilla30.mbooru.extension.isVideo
import com.mattzilla30.mbooru.ui.activity.DetailActivity
import com.mattzilla30.mbooru.ui.activity.SearchActivity
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.viewmodel.getPostViewModel
import com.mattzilla30.mbooru.ui.viewmodel.getTagFilterViewModel
import com.mattzilla30.mbooru.worker.DownloadWorker
import java.util.*
import kotlinx.coroutines.flow.filter
import org.koin.core.component.inject

private const val SCALE_DAY = "day"
private const val SCALE_WEEK = "week"
private const val SCALE_MONTH = "month"
private const val PERIOD_DAY = "1d"
private const val PERIOD_WEEK = "1w"
private const val PERIOD_MONTH = "1m"
private const val PERIOD_YEAR = "1y"

private const val ROTATION_DEGREE = 135f

class PostScreenController(
    host: com.mattzilla30.mbooru.ui.base.BaseActivity,
    owner: androidx.navigation.NavBackStackEntry,
    arguments: Bundle = Bundle.EMPTY,
) : BrowseController(host, owner, arguments), org.koin.core.component.KoinComponent {

    private val db by inject<MyDatabase>()
    private val postViewModel by lazy {
        getPostViewModel(PostRepositoryImpl(db, booruApis, settings))
    }
    private val tagFilterViewModel by lazy {
        getTagFilterViewModel(TagFilterRepositoryImpl(db.tagFilterDao()))
    }
    private val filters by viewModels<PostFiltersViewModel>()
    private lateinit var date: ActionPost.Date
    private var action: ActionPost? = null
    private var restoredScale = "day"
    private var restoredPeriod = "1d"
    private var currentPageType = PAGE_TYPE_POSTS
    private var query = ""
    private var sharedElement: View? = null
    private var returnPosition = -1
    private val visibleImages = mutableMapOf<Int, View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPageType = arguments?.getInt(PAGE_TYPE, PAGE_TYPE_POSTS) ?: PAGE_TYPE_POSTS
        query =
            if (isPopularPage()) "order:popular" else host.intent?.getStringExtra(POST_QUERY) ?: ""
        initDate()
        savedInstanceState
            ?.getIntArray("popular_date")
            ?.takeIf { it.size == 6 }
            ?.let { date = ActionPost.Date(it[0], it[1], it[2], it[3], it[4], it[5]) }
        restoredScale = savedInstanceState?.getString("popular_scale") ?: "day"
        restoredPeriod = savedInstanceState?.getString("popular_period") ?: "1d"
        setSearchBarTitle(
            if (isPopularPage()) getString(R.string.title_popular)
            else query.ifEmpty { getString(R.string.app_name) }
        )
        if (savedInstanceState == null) setSearchBarText(query)
        tagFilterViewModel.loadTags()
    }

    private fun isPopularPage() = currentPageType == PAGE_TYPE_POPULAR

    @Composable
    override fun BrowseContent(modifier: Modifier) {
        val posts = postViewModel.posts.collectAsLazyPagingItems()
        val state by browse.state.collectAsStateWithLifecycle()
        val options =
            remember(state.preferencesRevision) {
                PostDisplayOptions(
                    settings.isRoundedGrid,
                    if (settings.gridMode == "fixed") settings.gridRatio else null,
                    settings.showInfoBar,
                    settings.isLargeWidth,
                    host.resources.getDimension(settings.gridWidthResId) /
                        host.resources.displayMetrics.density,
                )
            }
        PostGrid(
            posts,
            browse.scrollPositions,
            options,
            onClick = { _, position, image ->
                sharedElement = image
                returnPosition = position
                host.let {
                    DetailActivity.start(it, query, position, image, image.transitionName)
                }
            },
            onLongClick = ::handleLongClick,
            modifier = modifier,
            safeOnly = settings.safeMode,
            onImage = { index, view ->
                if (view == null) visibleImages.remove(index) else visibleImages[index] = view
            },
        )
    }

    @Composable
    override fun Filters() {
        val allTags by tagFilterViewModel.tags.collectAsStateWithLifecycle()
        val revision by filters.state.collectAsStateWithLifecycle()
        val state by browse.state.collectAsStateWithLifecycle()
        val booru = state.booru ?: return
        val selected = remember(revision) { filters.selected }
        val selections =
            remember(revision) {
                listOf(filters.get("order"), filters.get("rating"), filters.get("threshold"))
            }
        val tags = allTags.filter { settings.isShowAllTags || it.booruUid == booru.uid }
        val orders =
            host.resources
                .getStringArray(
                    when (booru.type) {
                        BOORU_TYPE_DAN -> R.array.filter_order_danbooru
                        BOORU_TYPE_SANKAKU -> R.array.filter_order_sankaku
                        else -> R.array.filter_order
                    }
                )
                .toList()
        val ratings =
            host.resources
                .getStringArray(
                    if (booru.type == BOORU_TYPE_DAN) R.array.filter_rating_danbooru
                    else R.array.filter_rating
                )
                .toList()
        val thresholds =
            if (booru.type == BOORU_TYPE_SANKAKU)
                host.resources.getStringArray(R.array.filter_threshold).toList()
            else emptyList()
        PostFilters(
            tags,
            selected,
            listOf(
                getString(R.string.order) to orders,
                getString(R.string.rating) to ratings,
                getString(R.string.threshold) to thresholds,
            ),
            selections,
            filters::toggle,
            ::deleteTagFilter,
            onSelect = { index, value ->
                filters.set(listOf("order", "rating", "threshold")[index], value)
            },
            onAdd = ::createTagFilter,
            onSearch = {
                filters.query(tags, booru.type).takeIf { it.isNotBlank() }?.let(::search)
            },
        )
    }

    override fun onBooruLoaded(booru: Booru?) {
        super.onBooruLoaded(booru)
        if (booru == null) {
            action = null
            return
        }
        if (isPopularPage() && booru.type == com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34) {
            setSearchBarTitle(getString(R.string.title_top_rated))
        }
        filters.bind(booru.uid)
        action =
            (action
                    ?: ActionPost(
                        booru = booru,
                        pageType = currentPageType,
                        limit = settings.pageLimit,
                        date = date,
                        isSafeMode = settings.safeMode,
                        query = query,
                        scale = restoredScale,
                        period = restoredPeriod,
                    ))
                .copy(booru = booru)
        postViewModel.show(action!!)
        setSearchBarMenu(
            if (isPopularPage()) {
                when (booru.type) {
                    com.mattzilla30.mbooru.app.Values.BOORU_TYPE_RULE34 -> AppMenu.None
                    BOORU_TYPE_SANKAKU -> AppMenu.PopularSankaku
                    else -> AppMenu.PopularDan
                }
            } else AppMenu.Post
        )
    }

    override fun getSearchBarHint(): CharSequence = getString(R.string.search_bar_hint_search_posts)

    private fun createTagFilter() {
        val uid = action?.booru?.uid
        val text = getEditQuery()
        if (uid == null || text.isEmpty()) {
            return
        }
        mutations.addFilter(uid, text)
    }

    private fun deleteTagFilter(tag: TagFilter) {
        val activity = host
        if (activity.isFinishing) {
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.tag_delete_title))
            .setMessage(String.format(getString(R.string.tag_delete_content), tag.name))
            .setPositiveButton(R.string.dialog_yes) { _, _ ->
                mutations.removeFilter(tag)
            }
            .setNegativeButton(R.string.dialog_no, null)
            .create()
            .show()
    }

    private fun initDate() {
        val calendar =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = MaterialDatePicker.todayInUtcMilliseconds()
            }
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)
        calendar.add(Calendar.DATE, -1)
        val beforeDay = calendar.get(Calendar.DAY_OF_MONTH)
        val beforeMonth = calendar.get(Calendar.MONTH)
        val beforeYear = calendar.get(Calendar.YEAR)
        date =
            ActionPost.Date(
                dayStart = beforeDay,
                monthStart = beforeMonth,
                yearStart = beforeYear,
                dayEnd = currentDay,
                monthEnd = currentMonth,
                yearEnd = currentYear,
            )
    }

    private fun handleLongClick(post: Post) {
        val activity = host as? PathActivity
        if (activity == null || activity.isFinishing) {
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("Post ${post.id}")
            .setItems(host.resources.getTextArray(R.array.post_item_action)) { _, which ->
                when (which) {
                    0 -> {
                        action?.apply {
                            if (post.origin.isVideo()) {
                                host.downloadByAdm(post.origin)
                            } else {
                                DownloadWorker.downloadPost(post, booru.host, activity)
                            }
                        }
                    }
                    1 -> mutations.favorite(post)
                }
            }
            .create()
            .show()
    }

    override fun onMenuAction(menuItem: AppActionKey) {
        when (menuItem) {
            AppActionKey.ExpandOrClear -> {
                if (browse.state.value.mode == 0) toExpandState() else clearSearchBarText()
            }
            AppActionKey.Date -> {
                pickDate()
            }
            AppActionKey.DateRange -> {
                if (action?.booru?.type == BOORU_TYPE_SANKAKU) {
                    pickDateRange()
                }
            }
            AppActionKey.Day -> {
                action?.let {
                    it.scale = SCALE_DAY
                    it.period = PERIOD_DAY
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.Week -> {
                action?.let {
                    it.scale = SCALE_WEEK
                    it.period = PERIOD_WEEK
                    updateActionAndRefresh(it)
                }
            }
            AppActionKey.Month -> {
                action?.let {
                    it.scale = SCALE_MONTH
                    it.period = PERIOD_MONTH
                    updateActionAndRefresh(it)
                }
            }
            else -> Unit
        }
    }

    private fun pickDate() {
        val calendar =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.YEAR, date.yearEnd)
                set(Calendar.MONTH, date.monthEnd)
                set(Calendar.DAY_OF_MONTH, date.dayEnd)
            }
        val selectedTime = calendar.timeInMillis
        val calendarConstraints =
            CalendarConstraints.Builder()
                .setOpenAt(selectedTime)
                .setValidator(DateValidatorPointBackward.now())
                .build()
        val dialog =
            MaterialDatePicker.Builder.datePicker()
                .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                .setCalendarConstraints(calendarConstraints)
                .setSelection(selectedTime)
                .build()
        dialog.addOnPositiveButtonClickListener { time ->
            calendar.timeInMillis = time
            date.yearEnd = calendar.get(Calendar.YEAR)
            date.monthEnd = calendar.get(Calendar.MONTH)
            date.dayEnd = calendar.get(Calendar.DAY_OF_MONTH)
            action?.let {
                it.date = date
                updateActionAndRefresh(it)
            }
        }
        dialog.show(host.supportFragmentManager, "date_picker")
    }

    private fun pickDateRange() {
        val calendar =
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(Calendar.YEAR, date.yearStart)
                set(Calendar.MONTH, date.monthStart)
                set(Calendar.DAY_OF_MONTH, date.dayStart)
            }
        val selectionStart = calendar.timeInMillis
        calendar.apply {
            set(Calendar.YEAR, date.yearEnd)
            set(Calendar.MONTH, date.monthEnd)
            set(Calendar.DAY_OF_MONTH, date.dayEnd)
        }
        val selectionEnd = calendar.timeInMillis
        val selection = androidx.core.util.Pair(selectionStart, selectionEnd)
        val calendarConstraints =
            CalendarConstraints.Builder()
                .setOpenAt(selectionEnd)
                .setValidator(DateValidatorPointBackward.now())
                .build()
        val picker =
            MaterialDatePicker.Builder.dateRangePicker()
                .setTheme(R.style.MaterialCalendarTheme)
                .setInputMode(MaterialDatePicker.INPUT_MODE_CALENDAR)
                .setCalendarConstraints(calendarConstraints)
                .setSelection(selection)
                .build()
        picker.addOnPositiveButtonClickListener { times ->
            calendar.timeInMillis = times.first
            date.yearStart = calendar.get(Calendar.YEAR)
            date.monthStart = calendar.get(Calendar.MONTH)
            date.dayStart = calendar.get(Calendar.DAY_OF_MONTH)
            calendar.timeInMillis = times.second
            date.yearEnd = calendar.get(Calendar.YEAR)
            date.monthEnd = calendar.get(Calendar.MONTH)
            date.dayEnd = calendar.get(Calendar.DAY_OF_MONTH)
            action?.let {
                it.date = date
                updateActionAndRefresh(it)
            }
        }
        picker.show(host.supportFragmentManager, "date_range_picker")
    }

    private fun updateActionAndRefresh(action: ActionPost) {
        postViewModel.show(action)
    }

    override fun onApplySearch(query: String) {
        search(query)
    }

    private fun search(query: String) {
        val context = host
        SearchActivity.startSearch(context, query)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        val current = action ?: return
        if (key == PAGE_LIMIT_KEY || key == SAFE_MODE_KEY) {
            current.limit = settings.pageLimit
            current.isSafeMode = settings.safeMode
            postViewModel.show(current)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(
            "popular_date",
            intArrayOf(
                date.yearStart,
                date.monthStart,
                date.dayStart,
                date.yearEnd,
                date.monthEnd,
                date.dayEnd,
            ),
        )
        outState.putString("popular_scale", action?.scale ?: restoredScale)
        outState.putString("popular_period", action?.period ?: restoredPeriod)
    }

    override fun onDispose() {
        visibleImages.clear()
        sharedElement = null
        super.onDispose()
    }

    private val broadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (query != intent?.getStringExtra(POST_QUERY)) return
                val position = intent.getIntExtra(POST_POSITION, -1)
                if (position >= 0) {
                    returnPosition = position
                    browse.scrollTo(position)
                }
            }
        }
    private val sharedElementCallback =
        object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>?,
                sharedElements: MutableMap<String, View>?,
            ) {
                if (names == null || sharedElements == null) return
                names.clear()
                sharedElements.clear()
                (visibleImages[returnPosition] ?: sharedElement)
                    ?.takeIf { it.isAttachedToWindow }
                    ?.let { view ->
                        view.transitionName?.let { name ->
                            names.add(name)
                            sharedElements[name] = view
                        }
                    }
            }
        }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        setActivityExitSharedElementCallback(sharedElementCallback)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            host.registerReceiver(
                broadcastReceiver,
                IntentFilter(DetailActivity.ACTION_DETAIL_POST_POSITION),
                Context.RECEIVER_NOT_EXPORTED,
            )
        } else {
            host.registerReceiver(
                broadcastReceiver,
                IntentFilter(DetailActivity.ACTION_DETAIL_POST_POSITION),
            )
        }
    }

    override fun onStop() {
        super.onStop()
        setActivityExitSharedElementCallback(null)
        host.unregisterReceiver(broadcastReceiver)
    }

    private fun setActivityExitSharedElementCallback(callback: SharedElementCallback?) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            host.setExitSharedElementCallback(callback)
        }
    }
}
