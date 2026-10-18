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

package com.mattzilla30.mbooru.ui.base

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.data.settings.DataStorePreferences
import com.mattzilla30.mbooru.extension.applySafeDrawingInsets
import com.mattzilla30.mbooru.ui.activity.*
import com.mattzilla30.mbooru.ui.compose.common.*
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.helper.isNightEnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.koin.android.ext.android.inject

abstract class BaseActivity : AppCompatActivity() {
    val settings by inject<com.mattzilla30.mbooru.app.SettingsRepository>()
    private val durablePreferences by inject<DataStorePreferences>()

    protected open fun onSettingsReady(savedInstanceState: Bundle?) = Unit

    var screenTitle: CharSequence by mutableStateOf("")
    var screenSubtitle: CharSequence by mutableStateOf("")
    val appMenu: AppMenu
        get() =
            when (this) {
                is HistoryActivity -> AppMenu.History
                is MuzeiActivity -> AppMenu.Muzei
                is SauceNaoActivity -> AppMenu.SauceNao
                is BooruConfigActivity -> AppMenu.BooruConfig
                is BooruActivity -> AppMenu.Booru
                is AccountActivity -> AppMenu.Account
                is CommentActivity -> AppMenu.Comment
                else -> AppMenu.None
            }

    val hasComposeAppBar: Boolean
        get() =
            this !is MainActivity &&
                this !is SearchActivity &&
                this !is DetailActivity &&
                this !is AccountConfigActivity

    fun setScreenTitle(resource: Int) {
        screenTitle = getString(resource)
    }

    @Composable open fun appActions(): List<AppAction> = appMenu.actions()

    internal var renderedActions: List<AppAction> = emptyList()

    open fun onAction(action: AppActionKey): Boolean =
        if (action == AppActionKey.Back) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else false

    override fun onKeyShortcut(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (
            event.isCtrlPressed &&
                event.keyCode in android.view.KeyEvent.KEYCODE_A..android.view.KeyEvent.KEYCODE_Z
        ) {
            val character = ('a'.code + event.keyCode - android.view.KeyEvent.KEYCODE_A).toChar()
            fun find(items: List<AppAction>): AppAction? =
                items
                    .filter { it.visible && it.enabled }
                    .firstNotNullOfOrNull {
                        if (it.shortcut == character) it else find(it.children)
                    }
            find(renderedActions)?.let {
                return onAction(it.key)
            }
        }
        return super.onKeyShortcut(keyCode, event)
    }

    private fun getThemeRes(): Int =
        when (this) {
            is MainActivity -> R.style.AppTheme_Black_NoActionBar_Scrim_Main
            is SearchActivity -> R.style.AppTheme_Black_NoActionBar_Scrim_NoAnimation
            else -> R.style.AppTheme_Black_NoActionBar_Animation
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (this !is DetailActivity) {
            if (resources.configuration.isNightEnable() && !settings.isNightThemeDark) {
                setTheme(getThemeRes())
            }
        }
        if (this !is MainActivity && this !is SearchActivity && this !is DetailActivity) {
            if (!resources.configuration.isNightEnable() || settings.isNightThemeDark)
                setTheme(R.style.AppTheme_NoActionBar_Animation)
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            yield() // Subclasses register result launchers before STARTED, including cold restores.
            try {
                durablePreferences.awaitReady()
                if (!isFinishing && !isDestroyed) {
                    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settings.nightMode)
                    onSettingsReady(savedInstanceState)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (!isFinishing && !isDestroyed)
                    androidx.appcompat.app.AlertDialog.Builder(this@BaseActivity)
                        .setTitle(R.string.settings_load_failed)
                        .setMessage(R.string.settings_load_recovery)
                        .setCancelable(false)
                        .setPositiveButton(R.string.action_retry) { _, _ ->
                            durablePreferences.retryInitialization()
                            recreate()
                        }
                        .setNegativeButton(R.string.dialog_cancel) { _, _ -> finish() }
                        .show()
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // These screens place controls over edge-to-edge content and handle their own insets.
        if (this !is MainActivity && this !is SearchActivity && this !is DetailActivity) {
            val content = findViewById<View>(android.R.id.content)
            // Include the retained AppCompat action bar in the safe area.
            (content.parent as View).applySafeDrawingInsets()
        }
    }
}
