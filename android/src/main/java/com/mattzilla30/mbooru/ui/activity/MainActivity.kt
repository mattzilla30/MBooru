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

package com.mattzilla30.mbooru.ui.activity

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.database.BooruPresets
import com.mattzilla30.mbooru.data.database.dao.BooruDao
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.browse.BrowseNavigation
import com.mattzilla30.mbooru.ui.compose.navigation.*
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme
import com.mattzilla30.mbooru.ui.helper.isNightEnable
import com.mattzilla30.mbooru.ui.viewmodel.getBooruViewModel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : PathActivity(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val preferences by inject<SharedPreferences>()
    private val siteManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()
    private val booruDao by inject<BooruDao>()
    private val boorus by lazy { getBooruViewModel(booruDao, siteManager) }
    private val shell by viewModels<MainShellViewModel>()
    private val updater by inject<com.mattzilla30.mbooru.data.api.AppUpdaterRepository>()
    internal var activeBrowse: BrowseNavigation? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            BooruPresets.installOnce(booruDao, preferences) {
                siteManager.deleteBooru(it)
                Unit
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                boorus.loadBoorus().collect { list ->
                    val available = list
                    if (available.none { it.uid == settings.activatedBooruUid }) {
                        settings.activatedBooruUid = available.firstOrNull()?.uid ?: -1
                    }
                }
            }
        }
        boorus.loadBooru(settings.activatedBooruUid)
        setContent {
            MBooruTheme {
                val sites by boorus.boorusState.collectAsStateWithLifecycle()
                val site by boorus.booruState.collectAsStateWithLifecycle()
                val visible by shell.navVisible.collectAsStateWithLifecycle()
                val revision by shell.preferencesRevision.collectAsStateWithLifecycle()
                val selectedUid = remember(revision) { settings.activatedBooruUid }
                val type = site?.type ?: Values.BOORU_TYPE_UNKNOWN
                val tabs = remember(type) { browseTabs(type) }
                // A capability change resets the graph, as the former XML graphs did.
                key("typed_navigation_v1", tabs.map { it.id }) {
                    val navigation = rememberNavController()
                    val entry by navigation.currentBackStackEntryAsState()
                    val selected = entry?.destination.browseTab()
                    MainShell(
                        sites,
                        selectedUid,
                        tabs,
                        selected,
                        visible,
                        shell.drawerRequests,
                        serverRequests = shell.serverRequests,
                        onBooru = { settings.activatedBooruUid = it },
                        onAction = ::openDestination,
                        onEditServer = { server ->
                            startActivity(
                                Intent(this, BooruConfigActivity::class.java)
                                    .putExtra(BooruConfigActivity.EXTRA_BOORU_UID, server.uid)
                            )
                        },
                        onRemoveServer = { id ->
                            lifecycleScope.launch { siteManager.deleteBooru(id) }
                        },
                        onTab = { id ->
                            if (selected == id) currentBrowse()?.toListTop()
                            else navigation.openBrowseTab(id)
                        },
                    ) {
                        MainBrowseNavHost(navigation, tabs)
                    }
                }
                if (settings.latestVersionCode > BuildConfig.VERSION_CODE) {
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.update_found_update)) },
                        text = {
                            Text(
                                stringResource(R.string.update_version, settings.latestVersionName)
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    launchUrl(settings.latestVersionUrl)
                                    finish()
                                }
                            ) {
                                Text(stringResource(R.string.dialog_update))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.dialog_exit))
                            }
                        },
                    )
                }
            }
        }
        lifecycleScope.launch { updater.checkUpdate() }
        if (Build.VERSION.SDK_INT >= 33)
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun currentBrowse(): BrowseNavigation? = activeBrowse

    private fun openDestination(key: DrawerDestination) {
        val booru = boorus.currentBooru
        val capabilities =
            com.mattzilla30.mbooru.core.model.BooruEngine.fromLegacyId(booru?.type ?: -1)
                .capabilities
        val destination =
            when (key) {
                DrawerDestination.Sites -> BooruActivity::class.java
                DrawerDestination.Account -> {
                    if (booru == null) return
                    if (!capabilities.accounts) return notSupported()
                    if (booru.user == null) AccountConfigActivity::class.java
                    else AccountActivity::class.java
                }
                DrawerDestination.Comments ->
                    if (capabilities.comments) CommentActivity::class.java
                    else return notSupported()
                DrawerDestination.History ->
                    if (booru != null) HistoryActivity::class.java else return
                DrawerDestination.Blacklist ->
                    if (capabilities.tagFilters) TagBlacklistActivity::class.java
                    else return notSupported()
                DrawerDestination.Settings -> SettingsActivity::class.java
                DrawerDestination.About -> AboutActivity::class.java
                DrawerDestination.Downloads -> DownloadsActivity::class.java
                DrawerDestination.Favorites -> FavoritesActivity::class.java
                DrawerDestination.Exit -> {
                    finishAndRemoveTask()
                    return
                }
                DrawerDestination.Theme -> {
                    val next =
                        if (resources.configuration.isNightEnable()) Settings.NIGHT_MODE_OFF
                        else Settings.NIGHT_MODE_ON
                    preferences.edit().putString(Settings.NIGHT_MODE_KEY, next).apply()
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settings.nightMode)
                    return
                }
            }
        startActivity(Intent(this, destination))
    }

    private fun notSupported() {
        Toast.makeText(this, R.string.msg_not_supported, Toast.LENGTH_SHORT).show()
    }

    fun openDrawer() = shell.openDrawer()

    fun chooseServer() = shell.chooseServer()

    fun forceShowNavBar() = shell.navigationVisible(true)

    fun onContentScroll(down: Boolean) {
        if (settings.autoHideBottomBar) shell.navigationVisible(!down)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        shell.preferencesChanged()
        when (key) {
            Settings.BOORU_UID_ACTIVATED_KEY -> boorus.loadBooru(settings.activatedBooruUid)
            Settings.NIGHT_THEME_KEY -> if (resources.configuration.isNightEnable()) recreate()
            Settings.AUTO_HIDE_BOTTOM_BAR_KEY -> forceShowNavBar()
        }
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }
}
