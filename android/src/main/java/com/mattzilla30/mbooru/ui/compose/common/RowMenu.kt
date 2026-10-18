package com.mattzilla30.mbooru.ui.compose.common

import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mattzilla30.mbooru.R

@Composable
fun RowMenu(resource: Int, iconTint: androidx.compose.ui.graphics.Color = LocalContentColor.current, onAction: (MenuItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val menu = remember(context, resource) {
        PopupMenu(context, android.view.View(context)).apply { menuInflater.inflate(resource, menu) }.menu
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert_24dp), stringResource(androidx.appcompat.R.string.abc_action_menu_overflow_description), tint = iconTint)
        }
        DropdownMenu(expanded, { expanded = false }) {
            repeat(menu.size()) { index ->
                val item = menu.getItem(index)
                DropdownMenuItem(text = { Text(item.title.toString()) }, onClick = { expanded = false; onAction(item) })
            }
        }
    }
}
