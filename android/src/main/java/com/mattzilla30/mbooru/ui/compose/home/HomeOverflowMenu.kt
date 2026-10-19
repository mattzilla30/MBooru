// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.mattzilla30.mbooru.R

@Composable
@Suppress("FunctionNaming")
fun HomeOverflowMenu(onSettings: () -> Unit, onSwitch: () -> Unit, onTools: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun select(action: () -> Unit) {
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.testTag("home-overflow")) {
            Icon(painterResource(R.drawable.ic_more_vert_24dp), "More options")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Settings") },
                modifier = Modifier.testTag("home-settings"),
                onClick = { select(onSettings) },
            )
            DropdownMenuItem(text = { Text("Switch site") }, onClick = { select(onSwitch) })
            DropdownMenuItem(text = { Text("Tools") }, onClick = { select(onTools) })
        }
    }
}
