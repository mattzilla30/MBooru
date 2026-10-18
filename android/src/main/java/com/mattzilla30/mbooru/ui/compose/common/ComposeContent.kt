package com.mattzilla30.mbooru.ui.compose.common

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mattzilla30.mbooru.ui.base.BaseActivity
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme

/** Preserve the current activity toolbar and its menus while migrating its content. */
fun BaseActivity.setComposeContent(content: @Composable () -> Unit) {
    setContent {
        MBooruTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize()) {
                    if (hasComposeAppBar) ActivityAppBar(this@setComposeContent)
                    if (hasComposeAppBar) ConnectivityBanner()
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
    }
}
