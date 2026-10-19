// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mattzilla30.mbooru.ui.base.PathActivity
import com.mattzilla30.mbooru.ui.compose.settings.SettingsEffect
import com.mattzilla30.mbooru.ui.compose.settings.SettingsScreen
import com.mattzilla30.mbooru.ui.compose.settings.SettingsViewModel

@Composable
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
fun HomeSettings(host: PathActivity, model: SettingsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) {
        model.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.NightMode -> AppCompatDelegate.setDefaultNightMode(effect.mode)
                SettingsEffect.NightTheme -> host.recreate()
            }
        }
    }
    var locked by remember { mutableStateOf(host.settings.appLock) }
    var lockMessage by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("App lock") },
            supportingContent = { Text("Require your Android screen lock after leaving MBooru.") },
            trailingContent = {
                Switch(
                    locked,
                    onCheckedChange = { enabled ->
                        val manager =
                            host.getSystemService(android.content.Context.KEYGUARD_SERVICE)
                                as android.app.KeyguardManager
                        if (enabled && !manager.isKeyguardSecure)
                            lockMessage =
                                "Set an Android PIN, password or pattern before enabling app lock."
                        else {
                            host.settings.appLock = enabled
                            locked = enabled
                            com.mattzilla30.mbooru.ui.security.AppLockSession.unlock()
                            if (enabled)
                                host.window.addFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                            else
                                host.window.clearFlags(
                                    android.view.WindowManager.LayoutParams.FLAG_SECURE
                                )
                        }
                    },
                )
            },
        )
        if (lockMessage.isNotEmpty()) Text(lockMessage, Modifier.padding(16.dp))
        var maximum by remember { mutableStateOf(host.settings.downloadMaxBytes) }
        var limits by remember { mutableStateOf(false) }
        ListItem(
            headlineContent = { Text("Maximum download size") },
            supportingContent = {
                Text(
                    "Applies to new transfers and retries. Existing files and the download destination are unchanged."
                )
            },
            trailingContent = {
                Box {
                    TextButton(onClick = { limits = true }) {
                        Text("${maximum/com.mattzilla30.mbooru.core.model.DownloadLimits.MIB} MB")
                    }
                    DropdownMenu(limits, onDismissRequest = { limits = false }) {
                        com.mattzilla30.mbooru.core.model.DownloadLimits.choicesMiB.forEach { mb ->
                            DropdownMenuItem(
                                text = { Text("$mb MB") },
                                onClick = {
                                    maximum =
                                        mb * com.mattzilla30.mbooru.core.model.DownloadLimits.MIB
                                    host.settings.downloadMaxBytes = maximum
                                    limits = false
                                },
                            )
                        }
                    }
                }
            },
        )
        Box(Modifier.weight(1f)) {
            SettingsScreen(
                state,
                model::toggle,
                model::choose,
                model::expand,
                host::pickDir,
                model::confirmClearCache,
                model::retrySave,
            )
        }
    }
}
