package com.mattzilla30.mbooru.ui.compose.common

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.base.BaseActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityAppBar(activity: BaseActivity) {
    val menu =
        remember(activity, activity.menuRevision) {
            PopupMenu(activity, android.view.View(activity)).menu.apply {
                activity.onCreateOptionsMenu(this)
                activity.onPrepareOptionsMenu(this)
            }
        }
    val items = (0 until menu.size()).map(menu::getItem).filter { it.isVisible }
    var expanded by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf<Menu?>(null) }
    fun select(item: MenuItem) {
        if (item.hasSubMenu()) submenu = item.subMenu
        else {
            activity.onOptionsItemSelected(item)
            activity.invalidateOptionsMenu()
        }
    }
    val actionIds =
        remember(activity, activity.appBarMenuResource) {
            menuActions(activity, activity.appBarMenuResource)
                .filter { it.direct }
                .map { it.item.itemId }
                .toSet()
        }
    val direct = items.filter { it.icon != null && it.itemId in actionIds }
    val overflow = items - direct.toSet()
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
            IconButton(onClick = { activity.onBackPressedDispatcher.onBackPressed() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                )
            }
        },
        actions = {
            direct.forEach { item ->
                IconButton(onClick = { select(item) }, enabled = item.isEnabled) {
                    Icon(
                        remember(item.icon) { item.icon!!.toBitmap().asImageBitmap() },
                        item.title.toString(),
                    )
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
                                text = { Text(item.title.toString()) },
                                enabled = item.isEnabled,
                                onClick = {
                                    expanded = false
                                    select(item)
                                },
                            )
                        }
                    }
                }
            DropdownMenu(submenu != null, { submenu = null }) {
                submenu?.let { sub ->
                    repeat(sub.size()) { index ->
                        val item = sub.getItem(index)
                        if (item.isVisible)
                            DropdownMenuItem(
                                text = { Text(item.title.toString()) },
                                enabled = item.isEnabled,
                                onClick = {
                                    submenu = null
                                    select(item)
                                },
                            )
                    }
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
