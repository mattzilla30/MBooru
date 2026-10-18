package com.mattzilla30.mbooru.ui.compose.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.PAGE_TYPE
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POSTS
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POPULAR
import com.mattzilla30.mbooru.ui.fragment.*

fun browseRoute(id: Int): String = when (id) {
    R.id.nav_posts -> "posts"
    R.id.nav_popular -> "popular"
    R.id.nav_pools -> "pools"
    R.id.nav_tags -> "tags"
    R.id.nav_artists -> "artists"
    else -> error("Unknown browsing destination: $id")
}

/** Compose destinations own both screen state and cached paging ViewModels. */
@Composable
fun MainBrowseNavHost(navigation: NavHostController, tabs: List<BrowseTab>) {
    NavHost(navigation, startDestination = "posts", modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }) {
        tabs.forEach { tab ->
            composable(browseRoute(tab.id)) { entry ->
                val host = generateSequence(LocalContext.current) { (it as? android.content.ContextWrapper)?.baseContext }
                    .filterIsInstance<BaseActivity>().first()
                val controller = remember(host, entry) {
                    when (tab.id) {
                        R.id.nav_posts, R.id.nav_popular -> PostScreenController(host, entry, bundleOf(PAGE_TYPE to
                            if (tab.id == R.id.nav_popular) PAGE_TYPE_POPULAR else PAGE_TYPE_POSTS))
                        R.id.nav_pools -> PoolScreenController(host, entry)
                        R.id.nav_tags -> TagScreenController(host, entry)
                        else -> ArtistScreenController(host, entry)
                    }.apply { initialize() }
                }
                controller.Content()
            }
        }
    }
}
