package com.mattzilla30.mbooru.ui.compose.browse

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.ui.activity.MainActivity

private fun Context.mainActivity(): MainActivity? =
    when (this) {
        is MainActivity -> this
        is ContextWrapper -> baseContext.mainActivity()
        else -> null
    }

@Composable
fun ObserveBrowseScroll(position: () -> Pair<Int, Int>) {
    val activity = LocalContext.current.mainActivity()
    LaunchedEffect(activity) {
        var previous = position()
        snapshotFlow(position).collect { current ->
            if (current != previous)
                activity?.onContentScroll(
                    current.first > previous.first ||
                        (current.first == previous.first && current.second > previous.second)
                )
            previous = current
        }
    }
}

@Composable
fun BrowseScrollTop(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    androidx.compose.animation.AnimatedVisibility(visible, modifier = modifier) {
        androidx.compose.material3.SmallFloatingActionButton(
            onClick = onClick,
            modifier = androidx.compose.ui.Modifier.padding(16.dp),
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.ui.res.painterResource(
                    com.mattzilla30.mbooru.R.drawable.ic_baseline_vertical_align_top_24
                ),
                androidx.compose.ui.res.stringResource(
                    com.mattzilla30.mbooru.R.string.action_to_top
                ),
            )
        }
    }
}
