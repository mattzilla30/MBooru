package com.mattzilla30.mbooru.ui.compose.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.ui.compose.theme.PreferenceSpacing

/** State and callbacks only; persistence, networking and cache work stay outside composition. */
// Host-specific controls share the category headings and the same scrolling list.
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onToggle: (String, Boolean) -> Unit,
    onChoose: (String, String, List<String>) -> Unit,
    onExpand: () -> Unit,
    onPickDirectory: () -> Unit,
    onClearCache: () -> Unit,
    onRetrySave: () -> Unit = {},
    categoryContent: Map<Int, @Composable () -> Unit> = emptyMap(),
) {
    var dialogKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val preferenceRows = state.copy(expanded = true).rows(isSystemInDarkTheme())
    val categories =
        preferenceRows.filterIsInstance<SettingsRow.Category>().map { it.title }.toSet()
    val rows =
        preferenceRows +
            categoryContent.keys.filterNot { it in categories }.map { SettingsRow.Category(it) }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.testTag("settings_list")) {
            if (state.persistenceFailed)
                item(key = "persistence-error") {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            stringResource(R.string.settings_write_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetrySave) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            items(
                rows,
                key = { row ->
                    when (row) {
                        is SettingsRow.Category -> "category-${row.title}"
                        is SettingsRow.Preference -> row.spec.key
                        is SettingsRow.Expand -> "expand"
                    }
                },
                contentType = { it::class },
            ) { row ->
                when (row) {
                    is SettingsRow.Category ->
                        Column {
                            Text(
                                text = stringResource(row.title),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .testTag("settings-category-${row.title}")
                                        .semantics { heading() }
                                        .padding(
                                            start = 16.dp,
                                            end = PreferenceSpacing.horizontal,
                                            top = 12.dp,
                                            bottom = 12.dp,
                                        ),
                            )
                            categoryContent[row.title]?.invoke()
                        }
                    is SettingsRow.Expand -> {
                        val titles = row.hiddenTitles.map { stringResource(it) }
                        val summary = titles.reduceOrNull { acc, title ->
                            context.getString(
                                R.string.settings_summary_collapsed_preferences,
                                acc,
                                title,
                            )
                        }
                        PreferenceRow(
                            key = "settings_expand",
                            title = stringResource(R.string.settings_expand_button_title),
                            summary = summary,
                            onClick = onExpand,
                        )
                    }
                    is SettingsRow.Preference -> {
                        val spec = row.spec
                        val summary = preferenceSummary(spec, state)
                        PreferenceRow(
                            key = spec.key,
                            title = stringResource(spec.title),
                            summary = summary,
                            checked =
                                if (spec is SettingSpec.Toggle) state.value(spec).toBoolean()
                                else null,
                            enabled = spec.key != Settings.CLEAR_CACHE_KEY || !state.clearingCache,
                            onToggle = { onToggle(spec.key, it) },
                            onClick = {
                                when (spec) {
                                    is SettingSpec.Choice -> dialogKey = spec.key
                                    is SettingSpec.Toggle -> Unit
                                    is SettingSpec.Action ->
                                        when (spec.key) {
                                            Settings.DOWNLOAD_PATH_KEY -> onPickDirectory()
                                            Settings.CLEAR_CACHE_KEY -> dialogKey = spec.key
                                        }
                                }
                            },
                        )
                        if (spec.key == Settings.CLEAR_CACHE_KEY && state.cacheClearFailed) {
                            Text(
                                stringResource(R.string.settings_cache_clear_failed),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier =
                                    Modifier.padding(start = 72.dp, end = 16.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    val selected = settingsByKey[dialogKey]
    if (selected is SettingSpec.Choice) {
        val entries = stringArrayResource(selected.entries)
        val values = stringArrayResource(selected.entryValues).toList()
        AlertDialog(
            onDismissRequest = { dialogKey = null },
            title = { Text(stringResource(selected.title)) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp).selectableGroup()) {
                    items(values, key = { it }) { value ->
                        val checked = state.value(selected) == value
                        Row(
                            Modifier.fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    checked,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onChoose(selected.key, value, values)
                                        dialogKey = null
                                    },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = checked, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                entries[values.indexOf(value)],
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { dialogKey = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    } else if (dialogKey == Settings.CLEAR_CACHE_KEY) {
        AlertDialog(
            onDismissRequest = { dialogKey = null },
            title = { Text(stringResource(R.string.settings_clear_cache)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_content)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dialogKey = null
                        onClearCache()
                    }
                ) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { dialogKey = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

@Composable
private fun preferenceSummary(spec: SettingSpec, state: SettingsUiState): String? {
    if (spec.key in state.restartKeys)
        return stringResource(R.string.settings_summary_restart_required)
    return when (spec) {
        is SettingSpec.Choice -> {
            val entries = stringArrayResource(spec.entries)
            val index = stringArrayResource(spec.entryValues).indexOf(state.value(spec))
            entries.getOrNull(index) ?: stringResource(R.string.settings_value_not_set)
        }
        is SettingSpec.Toggle -> {
            val summary = if (state.value(spec).toBoolean()) spec.summaryOn else spec.summaryOff
            (summary.takeIf { it != 0 } ?: spec.summary)
                .takeIf { it != 0 }
                ?.let { stringResource(it) }
        }
        is SettingSpec.Action ->
            when (spec.key) {
                Settings.DOWNLOAD_PATH_KEY ->
                    state.value(spec).ifEmpty {
                        stringResource(R.string.settings_download_path_not_set)
                    }
                else -> spec.summary.takeIf { it != 0 }?.let { stringResource(it) }
            }
    }
}

@Composable
@Suppress("FunctionNaming", "LongParameterList")
fun PreferenceRow(
    key: String,
    title: String,
    summary: String?,
    checked: Boolean? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit = {},
) {
    val interaction =
        if (checked != null)
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle,
            )
        else Modifier.clickable(enabled = enabled, onClick = onClick)
    Row(
        Modifier.fillMaxWidth()
            .testTag(key)
            .heightIn(min = 72.dp)
            .then(interaction)
            .padding(
                horizontal = PreferenceSpacing.horizontal,
                vertical = PreferenceSpacing.vertical,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            )
            if (!summary.isNullOrEmpty())
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        if (checked != null) {
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}
