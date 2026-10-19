// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

data class BrowseTools(val tabs: List<BrowseTab>, val select: (Int) -> Unit)

val LocalBrowseTools = staticCompositionLocalOf { BrowseTools(emptyList(), {}) }

@Composable
@Suppress("FunctionNaming")
fun BrowseToolButtons() {
    val tools = LocalBrowseTools.current
    tools.tabs.forEach { tab ->
        TextButton(
            onClick = { tools.select(tab.id) },
            modifier = Modifier.testTag("browse-tool-${tab.id}"),
        ) {
            Text(stringResource(tab.title))
        }
    }
}
