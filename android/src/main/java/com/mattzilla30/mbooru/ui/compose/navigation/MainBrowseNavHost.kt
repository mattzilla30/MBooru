package com.mattzilla30.mbooru.ui.compose.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.bundleOf
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Keys.PAGE_TYPE
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POPULAR
import com.mattzilla30.mbooru.app.Values.PAGE_TYPE_POSTS
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.browse.*
import com.mattzilla30.mbooru.ui.fragment.*

fun browseRoute(id: Int): String =
    when (id) {
        R.id.nav_posts -> "posts"
        R.id.nav_popular -> "popular"
        R.id.nav_pools -> "pools"
        R.id.nav_tags -> "tags"
        R.id.nav_artists -> "artists"
        else -> error("Unknown browsing destination: $id")
    }

@kotlinx.serialization.Serializable data object PostsRoute

@kotlinx.serialization.Serializable data object PopularRoute

@kotlinx.serialization.Serializable data object PoolsRoute

@kotlinx.serialization.Serializable data object TagsRoute

@kotlinx.serialization.Serializable data object ArtistsRoute

fun androidx.navigation.NavDestination?.browseTab(): Int =
    when {
        this == null -> R.id.nav_posts
        hasRoute<PopularRoute>() -> R.id.nav_popular
        hasRoute<PoolsRoute>() -> R.id.nav_pools
        hasRoute<TagsRoute>() -> R.id.nav_tags
        hasRoute<ArtistsRoute>() -> R.id.nav_artists
        else -> R.id.nav_posts
    }

fun NavHostController.openBrowseTab(id: Int) {
    val options: androidx.navigation.NavOptionsBuilder.() -> Unit = {
        launchSingleTop = true
        restoreState = true
        popUpTo<PostsRoute> { saveState = true }
    }
    when (id) {
        R.id.nav_posts -> navigate(PostsRoute, options)
        R.id.nav_popular -> navigate(PopularRoute, options)
        R.id.nav_pools -> navigate(PoolsRoute, options)
        R.id.nav_tags -> navigate(TagsRoute, options)
        R.id.nav_artists -> navigate(ArtistsRoute, options)
    }
}

/** Typed routes carry no account, URL or image payloads. Each tab owns its saved state. */
@Composable
fun MainBrowseNavHost(navigation: NavHostController, tabs: List<BrowseTab>) {
    NavHost(
        navigation,
        startDestination = PostsRoute,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable<PostsRoute> { BrowseDestination(R.id.nav_posts, it) }
        if (tabs.any { it.id == R.id.nav_popular })
            composable<PopularRoute> { BrowseDestination(R.id.nav_popular, it) }
        if (tabs.any { it.id == R.id.nav_pools && it.website == null })
            composable<PoolsRoute> { BrowseDestination(R.id.nav_pools, it) }
        if (tabs.any { it.id == R.id.nav_tags })
            composable<TagsRoute> { BrowseDestination(R.id.nav_tags, it) }
        if (tabs.any { it.id == R.id.nav_artists && it.website == null })
            composable<ArtistsRoute> { BrowseDestination(R.id.nav_artists, it) }
    }
}

@Composable
private fun BrowseDestination(id: Int, entry: androidx.navigation.NavBackStackEntry) {
    val host =
        generateSequence(LocalContext.current) {
                (it as? android.content.ContextWrapper)?.baseContext
            }
            .filterIsInstance<BaseActivity>()
            .first()
    val controller =
        remember(host, entry) {
            when (id) {
                R.id.nav_posts,
                R.id.nav_popular ->
                    PostScreenController(
                        host,
                        entry,
                        bundleOf(
                            PAGE_TYPE to
                                if (id == R.id.nav_popular) PAGE_TYPE_POPULAR else PAGE_TYPE_POSTS
                        ),
                    )
                R.id.nav_pools -> PoolScreenController(host, entry)
                R.id.nav_tags -> TagScreenController(host, entry)
                else -> ArtistScreenController(host, entry)
            }.apply { initialize() }
        }
    controller.Content()
}
