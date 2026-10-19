// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.mattzilla30.mbooru.R

@Composable
@Suppress("FunctionNaming")
fun HomeActions(onFavorites: () -> Unit, onSettings: () -> Unit) {
    Row {
        IconButton(onClick = onFavorites, modifier = Modifier.testTag("home-favorites")) {
            Icon(painterResource(R.drawable.ic_favorite_24dp), "Favorites")
        }
        IconButton(onClick = onSettings, modifier = Modifier.testTag("home-settings")) {
            Icon(painterResource(R.drawable.ic_settings_outline_24dp), "Settings")
        }
    }
}
