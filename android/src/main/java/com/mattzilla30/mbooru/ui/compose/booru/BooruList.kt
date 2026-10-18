package com.mattzilla30.mbooru.ui.compose.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Values
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.ui.compose.common.AppActionKey
import com.mattzilla30.mbooru.ui.compose.common.AppMenu
import com.mattzilla30.mbooru.ui.compose.common.RowMenu

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooruList(
    boorus: List<Booru>,
    selected: Long,
    select: (Long) -> Unit,
    delete: (Long) -> Unit,
    action: (Booru, AppActionKey) -> Unit,
) {
    LazyColumn {
        item {
            Text(
                stringResource(R.string.sphere_anonymous_servers),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(boorus, key = { it.uid }) { booru ->
            val dismiss =
                rememberSwipeToDismissBoxState(
                    confirmValueChange = {
                        if (it != SwipeToDismissBoxValue.Settled) delete(booru.uid)
                        it != SwipeToDismissBoxValue.Settled
                    }
                )
            SwipeToDismissBox(dismiss, backgroundContent = {}) {
                Surface(
                    color =
                        if (booru.uid == selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.background
                ) {
                    Row(
                        Modifier.fillMaxWidth().clickable { select(booru.uid) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(booru.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${booru.scheme}://${booru.host}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(booruTypeLabel(booru.type)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        RowMenu(AppMenu.BooruActionMenu) { action(booru, it) }
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

fun booruTypeLabel(type: Int): Int =
    when (type) {
        Values.BOORU_TYPE_DAN -> R.string.booru_type_danbooru
        Values.BOORU_TYPE_DAN1 -> R.string.booru_type_danbooru_one
        Values.BOORU_TYPE_MOE -> R.string.booru_type_moebooru
        Values.BOORU_TYPE_GEL -> R.string.booru_type_gelbooru
        Values.BOORU_TYPE_GEL_LEGACY -> R.string.booru_type_gelbooru_legacy
        Values.BOORU_TYPE_SHIMMIE -> R.string.booru_type_shimmie
        Values.BOORU_TYPE_SANKAKU -> R.string.booru_type_sankaku
        else -> R.string.booru_type_unknown
    }
