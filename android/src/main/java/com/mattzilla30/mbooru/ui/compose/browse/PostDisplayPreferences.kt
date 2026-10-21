// SPDX-License-Identifier: GPL-3.0-or-later
package com.mattzilla30.mbooru.ui.compose.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.dimensionResource
import com.mattzilla30.mbooru.app.SettingsRepository

@Composable
fun rememberPostDisplayOptions(
    settings: SettingsRepository,
    revision: Int = 0,
): PostDisplayOptions {
    val widthResource = remember(revision) { settings.gridWidthResId }
    val width = dimensionResource(widthResource).value
    return remember(revision, width) {
        PostDisplayOptions(
            settings.isRoundedGrid,
            if (settings.gridMode == "fixed") settings.gridRatio else null,
            settings.showInfoBar,
            settings.isLargeWidth,
            width,
        )
    }
}
