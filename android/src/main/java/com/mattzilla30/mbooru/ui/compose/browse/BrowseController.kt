package com.mattzilla30.mbooru.ui.compose.browse

import android.content.ComponentCallbacks
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.navigation.NavBackStackEntry
import androidx.savedstate.SavedStateRegistryOwner
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_DAN1
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_MOE
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_SHIMMIE
import com.mattzilla30.mbooru.data.action.ActionTag
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.repository.suggestion.SuggestionRepositoryImpl
import com.mattzilla30.mbooru.ui.activity.MainActivity
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.viewmodel.*
import kotlinx.coroutines.launch
import org.koin.core.component.inject

/** Activity-bound actions with models owned by the Compose destination, never a fragment. */
abstract class BrowseController(
    protected val host: BaseActivity,
    val owner: NavBackStackEntry,
    val arguments: Bundle,
) :
    ComponentCallbacks by host,
    ViewModelStoreOwner by owner,
    LifecycleOwner by owner,
    HasDefaultViewModelProviderFactory by owner,
    SavedStateRegistryOwner by owner,
    BrowseNavigation,
    SharedPreferences.OnSharedPreferenceChangeListener,
    org.koin.core.component.KoinComponent {
    protected val settings
        get() = host.settings

    protected val mutations by lazy {
        ViewModelProvider(
            owner,
            androidx.lifecycle.viewmodel.viewModelFactory {
                initializer {
                    BrowseActionsViewModel(getKoin().get(), getKoin().get())
                }
            },
        )[BrowseActionsViewModel::class.java]
    }

    override val lifecycle: Lifecycle
        get() = owner.lifecycle

    fun getString(id: Int, vararg args: Any): String = host.getString(id, *args)

    fun startActivity(intent: Intent) = host.startActivity(intent)

    protected inline fun <reified T : ViewModel> viewModels(): Lazy<T> = lazy {
        ViewModelProvider(this)[T::class.java]
    }

    protected val booruApis by inject<BooruApis>()
    protected val preferences by inject<SharedPreferences>()
    private val siteManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()
    private val booruDao by inject<BooruDao>()
    protected val browse by viewModels<BrowseStateViewModel>()
    private val suggestions by lazy { getSuggestionViewModel(SuggestionRepositoryImpl(booruApis)) }
    private val boorus by lazy { getBooruViewModel(booruDao, siteManager) }
    private val saved = owner.savedStateHandle
    protected var currentBooru: Booru? = null

    abstract fun getSearchBarHint(): CharSequence

    open fun onCreate(savedInstanceState: Bundle?) {}

    open fun onSaveInstanceState(outState: Bundle) {}

    open fun onStart() {}

    open fun onStop() {}

    open fun onDispose() {}

    open fun onApplySearch(query: String) {}

    open fun onMenuAction(item: AppActionKey) {}

    open fun onBooruLoaded(booru: Booru?) {
        currentBooru = booru
        browse.booru(booru)
    }

    @Composable protected abstract fun BrowseContent(modifier: Modifier)

    @Composable protected open fun Filters() {}

    fun initialize() {
        onCreate(saved["browse_request_state"])
    }

    @Composable
    fun Content() {
        LaunchedEffect(this) {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { boorus.booruState.collect(::onBooruLoaded) }
                launch {
                    mutations.errors.collect {
                        android.widget.Toast.makeText(
                                host,
                                com.mattzilla30.mbooru.R.string.booru_config_save_failed,
                                android.widget.Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                }
            }
        }
        DisposableEffect(this) {
            saved.setSavedStateProvider("browse_request_state") {
                Bundle().apply(::onSaveInstanceState)
            }
            boorus.loadBooru(settings.activatedBooruUid)
            preferences.registerOnSharedPreferenceChangeListener(this@BrowseController)
            (host as? MainActivity)?.activeBrowse = this@BrowseController
            val observer =
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = this@BrowseController.onStart()

                    override fun onStop(owner: LifecycleOwner) = this@BrowseController.onStop()
                }
            owner.lifecycle.addObserver(observer)
            onDispose {
                saved["browse_request_state"] = Bundle().apply(::onSaveInstanceState)
                saved.clearSavedStateProvider("browse_request_state")
                owner.lifecycle.removeObserver(observer)
                // Removing an observer does not deliver stop to a still-started destination.
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStop()
                preferences.unregisterOnSharedPreferenceChangeListener(this@BrowseController)
                if ((host as? MainActivity)?.activeBrowse === this@BrowseController)
                    host.activeBrowse = null
                this@BrowseController.onDispose()
            }
        }
        val state by browse.state.collectAsStateWithLifecycle()
        val options by suggestions.suggestionState.collectAsStateWithLifecycle()
        BrowseScreen(
            state,
            getSearchBarHint().toString(),
            options,
            onQuery = { text ->
                browse.query(text)
                currentBooru
                    ?.takeUnless { it.type == BOORU_TYPE_SHIMMIE }
                    ?.let { booru ->
                        val query =
                            if (
                                booru.type in
                                    arrayOf(BOORU_TYPE_MOE, BOORU_TYPE_DAN, BOORU_TYPE_DAN1)
                            )
                                "$text*"
                            else text
                        suggestions.fetchSuggestions(
                            ActionTag(booru, limit = 6, order = "count", query = query)
                        )
                    }
            },
            onMode = {
                browse.mode(it)
                (host as? MainActivity)?.forceShowNavBar()
            },
            onSearch = {
                onApplySearch(it)
                browse.mode(0)
            },
            onNavigate = { host.onBackPressedDispatcher.onBackPressed() },
            onMenu = ::onMenuAction,
            filters = { Filters() },
            content = { BrowseContent(it) },
        )
    }

    protected fun setSearchBarTitle(value: CharSequence) = browse.title(value.toString())

    protected fun setSearchBarMenu(menu: AppMenu) = browse.menu(menu)

    protected fun setSearchBarText(text: CharSequence) = browse.query(text.toString())

    protected fun getEditQuery() = browse.state.value.query

    protected fun toNormalState() = browse.mode(0)

    protected fun toExpandState() = browse.mode(2)

    protected fun clearSearchBarText() = browse.query("")

    override fun toListTop() {
        browse.scrollTo(0)
        browse.mode(0)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Settings.BOORU_UID_ACTIVATED_KEY) boorus.loadBooru(settings.activatedBooruUid)
        browse.preferencesChanged()
    }
}
