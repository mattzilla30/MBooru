package com.mattzilla30.mbooru.ui.compose.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mattzilla30.mbooru.data.model.common.TagFilter
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL
import com.mattzilla30.mbooru.app.Values.BOORU_TYPE_GEL_LEGACY
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PostFiltersViewModel(private val saved: SavedStateHandle) : ViewModel() {
    private val revision = MutableStateFlow(0)
    val state = revision.asStateFlow()
    var selected: Set<Long>
        get() = saved.get<LongArray>("selected")?.toSet().orEmpty()
        private set(value) { saved["selected"] = value.toLongArray(); revision.value++ }
    fun bind(booruUid: Long) {
        if (saved.get<Long>("booru") != booruUid) {
            saved["booru"] = booruUid; selected = emptySet()
            set("order", ""); set("rating", ""); set("threshold", "")
        }
    }
    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun get(key: String): String = saved[key] ?: ""
    fun set(key: String, value: String) { saved[key] = value; revision.value++ }
    fun query(tags: List<TagFilter>, type: Int): String = buildList {
        addAll(tags.filter { it.uid in selected }.map { it.name }.distinct())
        get("order").takeIf { it.isNotEmpty() }?.let { add("${if (type in arrayOf(BOORU_TYPE_GEL, BOORU_TYPE_GEL_LEGACY)) "sort" else "order"}:$it") }
        get("rating").takeIf { it.isNotEmpty() }?.let { add("rating:$it") }
        get("threshold").takeIf { it.isNotEmpty() }?.let { add("threshold:$it") }
    }.joinToString(" ")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PostFilters(
    tags: List<TagFilter>, selected: Set<Long>,
    groups: List<Pair<String, List<String>>>, selections: List<String>,
    onToggle: (Long) -> Unit, onDelete: (TagFilter) -> Unit,
    onSelect: (Int, String) -> Unit, onAdd: () -> Unit, onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onAdd, label = { Text("+") })
            tags.forEach { tag ->
                Surface(color = if (tag.uid in selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(vertical = 4.dp).combinedClickable(
                        onClick = { onToggle(tag.uid) }, onLongClick = { onDelete(tag) })) {
                    Text(tag.name, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        groups.forEachIndexed { index, (title, values) ->
            if (values.isNotEmpty()) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    values.forEach { value ->
                        FilterChip(selected = selections.getOrNull(index) == value,
                            onClick = { onSelect(index, if (selections.getOrNull(index) == value) "" else value) },
                            label = { Text(value) })
                    }
                }
            }
        }
        Button(onClick = onSearch, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
            Text(androidx.compose.ui.res.stringResource(com.mattzilla30.mbooru.R.string.title_search))
        }
    }
}
