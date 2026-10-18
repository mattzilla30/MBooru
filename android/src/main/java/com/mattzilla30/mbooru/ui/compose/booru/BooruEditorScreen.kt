package com.mattzilla30.mbooru.ui.compose.booru

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.ui.compose.settings.PreferenceRow

@Composable
fun BooruEditorScreen(state: BooruEditorState, field: (String, String) -> Unit) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf("") }
    val types = stringArrayResource(R.array.booru_type_entries_values).toList()
    val typeLabels = stringArrayResource(R.array.booru_type_entries).toList()
    val schemes = stringArrayResource(R.array.booru_scheme_entries_values).toList()
    val schemeLabels = stringArrayResource(R.array.booru_scheme_entries).toList()
    val fields =
        listOf(
            "name" to R.string.booru_config_name,
            "type" to R.string.booru_config_type,
            "scheme" to R.string.booru_config_scheme,
            "host" to R.string.booru_config_host,
            "path" to R.string.booru_config_path,
            "hashSalt" to R.string.booru_config_hash_salt,
        )
    LazyColumn {
        fields
            .filter { (key, _) ->
                when (key) {
                    "hashSalt" -> state.values["type"] == "danbooru1"
                    "path" -> state.values["type"] == "shimmie"
                    else -> true
                }
            }
            .forEach { (key, title) ->
                if (key == "type")
                    item {
                        Text(
                            stringResource(R.string.booru_config_info),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 72.dp, top = 24.dp, bottom = 8.dp),
                        )
                    }
                item(key = key) {
                    val value = state.values[key].orEmpty()
                    val summary =
                        when (key) {
                            "type" -> typeLabels.getOrNull(types.indexOf(value)) ?: value
                            "scheme" -> schemeLabels.getOrNull(schemes.indexOf(value)) ?: value
                            else -> value
                        }.ifEmpty { stringResource(R.string.settings_value_not_set) }
                    PreferenceRow(
                        key,
                        stringResource(title),
                        summary,
                        icon =
                            when (key) {
                                "type" -> R.drawable.ic_class_24dp
                                "scheme" -> R.drawable.ic_https_24dp
                                "host" -> R.drawable.ic_domain_24dp
                                "hashSalt" -> R.drawable.ic_enhanced_encryption_24dp
                                else -> 0
                            },
                        enabled = !state.busy,
                        onClick = {
                            editing = key
                            draft = value
                        },
                    )
                }
            }
        item {
            if (state.busy) CircularProgressIndicator(Modifier.padding(16.dp))
            state.error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
    val key = editing
    if (key != null) {
        val title = fields.first { it.first == key }.second
        val options =
            when (key) {
                "type" -> types
                "scheme" -> schemes
                else -> emptyList()
            }
        val labels =
            when (key) {
                "type" -> typeLabels
                "scheme" -> schemeLabels
                else -> emptyList()
            }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(title)) },
            text = {
                if (options.isEmpty())
                    OutlinedTextField(
                        draft,
                        { draft = it },
                        singleLine = true,
                        placeholder = { if (key == "host") Text("example.com") },
                    )
                else
                    Column {
                        options.forEachIndexed { index, value ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable {
                                        field(key, value)
                                        editing = null
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = value == state.values[key],
                                    onClick = {
                                        field(key, value)
                                        editing = null
                                    },
                                )
                                Text(labels[index], modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
            },
            confirmButton = {
                if (options.isEmpty())
                    TextButton(
                        onClick = {
                            field(key, draft)
                            editing = null
                        }
                    ) {
                        Text(stringResource(R.string.dialog_ok))
                    }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
