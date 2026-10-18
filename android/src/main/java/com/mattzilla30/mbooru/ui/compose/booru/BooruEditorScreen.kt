/* Guided server form adapted from Boorusphere's server editor; BSD-3-Clause notice in assets. */
package com.mattzilla30.mbooru.ui.compose.booru

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionNaming", "LongMethod")
fun BooruEditorScreen(state: BooruEditorState, field: (String, String) -> Unit) {
    val types = stringArrayResource(R.array.booru_type_entries_values)
    val labels = stringArrayResource(R.array.booru_type_entries)
    var engines by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.sphere_anonymous_servers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                state.values["name"].orEmpty(),
                { field("name", it) },
                label = { Text(stringResource(R.string.booru_config_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.busy && state.available,
            )
        }
        item {
            ExposedDropdownMenuBox(expanded = engines, onExpandedChange = { engines = it }) {
                OutlinedTextField(
                    labels.getOrElse(types.indexOf(state.values["type"])) { "" },
                    {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.booru_config_type)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(engines) },
                    modifier =
                        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    enabled = !state.busy && state.available,
                )
                ExposedDropdownMenu(engines, { engines = false }) {
                    types.forEachIndexed { index, value ->
                        DropdownMenuItem(
                            text = { Text(labels[index]) },
                            onClick = {
                                field("type", value)
                                engines = false
                            },
                        )
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                state.values["host"].orEmpty(),
                { field("host", it) },
                label = { Text(stringResource(R.string.booru_config_host)) },
                prefix = { Text("${state.values["scheme"] ?: "https"}://") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy && state.available,
            )
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("https", "http").forEachIndexed { index, scheme ->
                    SegmentedButton(
                        selected = state.values["scheme"] == scheme,
                        onClick = { field("scheme", scheme) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) {
                        Text(scheme.uppercase())
                    }
                }
            }
        }
        if (state.values["type"] == "shimmie")
            item {
                OutlinedTextField(
                    state.values["path"].orEmpty(),
                    { field("path", it) },
                    label = { Text(stringResource(R.string.booru_config_path)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        if (state.values["type"] == "danbooru1")
            item {
                OutlinedTextField(
                    state.values["hashSalt"].orEmpty(),
                    { field("hashSalt", it) },
                    label = { Text(stringResource(R.string.booru_config_hash_salt)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        if (state.error != null)
            item { Text(stringResource(state.error), color = MaterialTheme.colorScheme.error) }
    }
}
