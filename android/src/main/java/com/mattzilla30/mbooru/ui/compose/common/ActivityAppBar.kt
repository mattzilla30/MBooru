package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.base.BaseActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityAppBar(activity: BaseActivity) {
    val actions = activity.appActions().filter { it.visible }
    SideEffect { activity.renderedActions = actions }
    var expanded by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf<AppAction?>(null) }
    fun select(action: AppAction) {
        if (action.children.isNotEmpty()) submenu = action
        else {
            activity.onAction(action.key)
        }
    }
    val direct = actions.filter { it.direct && it.icon != 0 }
    val overflow = actions - direct.toSet()
    TopAppBar(
        title = {
            Column {
                Text(
                    activity.screenTitle.toString(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (activity.screenSubtitle.isNotEmpty())
                    Text(
                        activity.screenSubtitle.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
        },
        navigationIcon = {
            IconButton(onClick = { activity.onAction(AppActionKey.Back) }) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back_24dp),
                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                )
            }
        },
        actions = {
            direct.forEach { item ->
                IconButton(onClick = { select(item) }, enabled = item.enabled) {
                    Icon(painterResource(item.icon), stringResource(item.title))
                }
            }
            if (overflow.isNotEmpty())
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(
                                androidx.appcompat.R.string.abc_action_menu_overflow_description
                            ),
                        )
                    }
                    DropdownMenu(expanded, { expanded = false }) {
                        overflow.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(stringResource(item.title)) },
                                enabled = item.enabled,
                                onClick = {
                                    expanded = false
                                    select(item)
                                },
                            )
                        }
                    }
                }
            DropdownMenu(submenu != null, { submenu = null }) {
                submenu
                    ?.children
                    ?.filter { it.visible }
                    ?.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(stringResource(item.title)) },
                            enabled = item.enabled,
                            onClick = {
                                submenu = null
                                select(item)
                            },
                        )
                    }
            }
        },
        expandedHeight =
            (if (activity.screenSubtitle.isEmpty()) 56 else 80).dp *
                LocalDensity.current.fontScale.coerceAtLeast(1f),
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            ),
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}
