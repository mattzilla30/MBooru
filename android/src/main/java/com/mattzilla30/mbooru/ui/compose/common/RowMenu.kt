package com.mattzilla30.mbooru.ui.compose.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mattzilla30.mbooru.R

@Composable
fun RowMenu(
    menu: AppMenu,
    iconTint: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    onAction: (AppActionKey) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painterResource(R.drawable.ic_more_vert_24dp),
                stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description),
                tint = iconTint,
            )
        }
        DropdownMenu(expanded, { expanded = false }) {
            menu
                .actions()
                .filter { it.visible }
                .forEach { item ->
                    DropdownMenuItem(
                        text = { Text(stringResource(item.title)) },
                        enabled = item.enabled,
                        onClick = {
                            expanded = false
                            onAction(item.key)
                        },
                    )
                }
        }
    }
}
