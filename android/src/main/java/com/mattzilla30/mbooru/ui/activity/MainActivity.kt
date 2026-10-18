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
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.BuildConfig
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.AppUpdaterApi
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
    private val booruDao by inject<BooruDao>()
    private val boorus by lazy { getBooruViewModel(booruDao) }
    private val shell by viewModels<MainShellViewModel>()
    private val updater by inject<com.mattzilla30.mbooru.data.api.AppUpdaterRepository>()
    internal var activeBrowse: BrowseNavigation? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onSettingsReady(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch { BooruPresets.installOnce(booruDao, preferences) }
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
                key(tabs.map { it.id }) {
                    val navigation = rememberNavController()
                    val entry by navigation.currentBackStackEntryAsState()
                    val selected = tabs.firstOrNull { browseRoute(it.id) == entry?.destination?.route }?.id ?: R.id.nav_posts
                    MainShell(sites, selectedUid, tabs, selected,
                        visible, shell.drawerRequests,
                        onBooru = { settings.activatedBooruUid = it }, onAction = ::openDestination,
                        onTab = { id ->
                            if (selected == id) currentBrowse()?.toListTop()
                            else navigation.navigate(browseRoute(id)) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(browseRoute(R.id.nav_posts)) { saveState = true }
                            }
                        }) {
                        MainBrowseNavHost(navigation, tabs)
                    }
                }
                if (settings.latestVersionCode > BuildConfig.VERSION_CODE) {
                    AlertDialog(onDismissRequest = {}, title = { Text(stringResource(R.string.update_found_update)) },
                        text = { Text(stringResource(R.string.update_version, settings.latestVersionName)) },
                        confirmButton = { TextButton(onClick = {
                            launchUrl(settings.latestVersionUrl)
                            finish()
                        }) { Text(stringResource(R.string.dialog_update)) } },
                        dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.dialog_exit)) } })
                }
            }
        }
        lifecycleScope.launch { updater.checkUpdate() }
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun currentBrowse(): BrowseNavigation? = activeBrowse

    private fun openDestination(key: String) {
        val booru = boorus.currentBooru
        val destination = when (key) {
            "boorus" -> BooruActivity::class.java
            "account" -> {
                if (booru == null) return
                if (booru.type == Values.BOORU_TYPE_SHIMMIE) return notSupported()
                if (booru.user == null) AccountConfigActivity::class.java else AccountActivity::class.java
            }
            "comments" -> if (booru?.type != Values.BOORU_TYPE_SHIMMIE) CommentActivity::class.java else return notSupported()
            "history" -> if (booru != null) HistoryActivity::class.java else return
            "blacklist" -> if (booru?.type in arrayOf(Values.BOORU_TYPE_MOE, Values.BOORU_TYPE_DAN,
                Values.BOORU_TYPE_DAN1, Values.BOORU_TYPE_GEL, Values.BOORU_TYPE_GEL_LEGACY)) TagBlacklistActivity::class.java else return notSupported()
            "muzei" -> MuzeiActivity::class.java
            "saucenao" -> SauceNaoActivity::class.java
            "tracemoe" -> WhatAnimeActivity::class.java
            "settings" -> SettingsActivity::class.java
            "about" -> AboutActivity::class.java
            "downloads" -> DownloadsActivity::class.java
            else -> return
        }
        startActivity(Intent(this, destination))
    }

    private fun notSupported() { Toast.makeText(this, R.string.msg_not_supported, Toast.LENGTH_SHORT).show() }
    fun openDrawer() = shell.openDrawer()
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
