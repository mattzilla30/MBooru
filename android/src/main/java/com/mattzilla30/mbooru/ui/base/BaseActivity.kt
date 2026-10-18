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
import android.view.WindowManager
import androidx.compose.runtime.*
import android.view.View
import androidx.core.view.WindowCompat
import com.mattzilla30.mbooru.extension.applySafeDrawingInsets
import androidx.appcompat.app.AppCompatActivity
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.activity.*
import com.mattzilla30.mbooru.ui.helper.isNightEnable

abstract class BaseActivity : AppCompatActivity() {

    var screenTitle: CharSequence by mutableStateOf("")
    var screenSubtitle: CharSequence by mutableStateOf("")
    val appBarMenuResource: Int get() = when (this) {
        is HistoryActivity -> R.menu.history
        is MuzeiActivity -> R.menu.muzei
        is SauceNaoActivity -> R.menu.sauce_nao
        is BooruConfigActivity -> R.menu.booru_config
        is BooruActivity -> R.menu.booru
        is AccountActivity -> R.menu.account
        is CommentActivity -> R.menu.comment
        else -> 0
    }
    var menuRevision by mutableStateOf(0)
        private set
    val hasComposeAppBar: Boolean get() = this !is MainActivity && this !is SearchActivity &&
        this !is DetailActivity && this !is AccountConfigActivity
    fun setScreenTitle(resource: Int) { screenTitle = getString(resource) }
    override fun invalidateOptionsMenu() {
        menuRevision++
        super.invalidateOptionsMenu()
    }

    private fun getThemeRes(): Int = when (this) {
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
            if (resources.configuration.isNightEnable() && !Settings.isNightThemeDark) {
                setTheme(getThemeRes())
            }
        }
        if (this !is MainActivity && this !is SearchActivity && this !is DetailActivity) {
            if (!resources.configuration.isNightEnable() || Settings.isNightThemeDark)
                setTheme(R.style.AppTheme_NoActionBar_Animation)
        }
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
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