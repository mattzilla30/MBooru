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

import org.koin.core.component.inject

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.BooruManager
import com.mattzilla30.mbooru.data.repository.user.UserRepositoryImpl
import com.mattzilla30.mbooru.extension.launchUrl
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.common.setComposeContent
import com.mattzilla30.mbooru.ui.compose.account.*
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class AccountConfigActivity : BaseActivity() , org.koin.core.component.KoinComponent {
    private val booruManager by inject<com.mattzilla30.mbooru.data.database.BooruManager>()

    private val apis by inject<BooruApis>()
    private var booru: com.mattzilla30.mbooru.data.model.common.Booru? = null
    private val model by viewModels<AccountConfigViewModel> {
        viewModelFactory { initializer { AccountConfigViewModel(UserRepositoryImpl(apis), checkNotNull(booru), booruManager) } }
    }
    override fun onSettingsReady(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            booru = booruManager.getBooruByUid(settings.activatedBooruUid)
            if (isFinishing || isDestroyed) return@launch
            initializeContent()
        }
    }
    private fun initializeContent() {
        val site = booru ?: return finish()
        setComposeContent {
            val state by model.state.collectAsStateWithLifecycle()
            AccountConfigScreen(state, site, model::username, model::token, model::submit, ::forgotCredentials)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.success.collect { startActivity(Intent(this@AccountConfigActivity, AccountActivity::class.java)); finish() }
            }
        }
    }
    private fun forgotCredentials() {
        val site = booru ?: return
        val path = when (site.type) {
            Values.BOORU_TYPE_DAN -> "session/new"
            Values.BOORU_TYPE_MOE, Values.BOORU_TYPE_DAN1 -> "user/reset_password"
            Values.BOORU_TYPE_SANKAKU -> "reset_password"
            else -> return
        }
        val host = if (site.type == Values.BOORU_TYPE_SANKAKU) site.host.replaceFirst("capi-v2.", "beta.") else site.host
        launchUrl("${site.scheme}://$host/$path")
    }
}
