package com.mattzilla30.mbooru.ui.compose.common

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.compose.theme.MBooruTheme

fun Fragment.composeView(content: @Composable () -> Unit): View =
    ComposeView(requireContext()).apply {
        id = R.id.compose_fragment_view
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent { MBooruTheme(content) }
    }
