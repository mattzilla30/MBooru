package com.mattzilla30.mbooru.ui.compose.settings

import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings

/** Resource-backed labels and persisted keys retain the original settings contract. */
sealed interface SettingSpec {
    val key: String
    val title: Int
    val icon: Int get() = 0
    val summary: Int get() = 0

    data class Choice(
        override val key: String,
        override val title: Int,
        val defaultValue: String,
        val entries: Int,
        val entryValues: Int,
        override val icon: Int = 0,
    ) : SettingSpec

    data class Toggle(
        override val key: String,
        override val title: Int,
        val defaultValue: Boolean,
        override val summary: Int = 0,
        val summaryOn: Int = 0,
        val summaryOff: Int = 0,
        override val icon: Int = 0,
    ) : SettingSpec

    data class Action(
        override val key: String,
        override val title: Int,
        override val summary: Int = 0,
        override val icon: Int = 0,
    ) : SettingSpec
}

data class SettingsSection(val title: Int, val preferences: List<SettingSpec>)

val settingsSections = listOf(
    SettingsSection(R.string.settings_display, listOf(
        SettingSpec.Choice("settings_night", R.string.settings_night_mode, "system", R.array.settings_night_mode_entries, R.array.settings_night_mode_entries_values, icon = R.drawable.ic_brightness_2_outline_24dp),
        SettingSpec.Choice("settings_night_theme", R.string.settings_night_theme, "black", R.array.settings_night_theme_entries, R.array.settings_night_theme_entries_values),
        SettingSpec.Toggle("settings_grid_rounded", R.string.settings_grid_rounded, true, summaryOn = R.string.switch_yes, summaryOff = R.string.switch_no),
        SettingSpec.Choice("settings_grid_mode", R.string.settings_grid_mode, "staggered", R.array.settings_grid_mode_entries, R.array.settings_grid_mode_entries_values),
        SettingSpec.Choice("settings_grid_ratio", R.string.settings_grid_ratio, "1:1", R.array.settings_grid_ratio_entries, R.array.settings_grid_ratio_entries_values),
        SettingSpec.Choice("settings_grid_width", R.string.settings_grid_width, "small", R.array.settings_grid_width_entries, R.array.settings_grid_width_entries_values),
        SettingSpec.Toggle("settings_show_info_bar", R.string.settings_show_info_bar, false, summaryOn = R.string.switch_yes, summaryOff = R.string.switch_no),
        SettingSpec.Toggle("settings_show_all_tags", R.string.settings_show_all_tags, false, summaryOn = R.string.switch_yes, summaryOff = R.string.switch_no),
        SettingSpec.Toggle("settings_auto_hide_bottom_bar", R.string.settings_auto_hide_bottom_bar, false, summaryOn = R.string.switch_yes, summaryOff = R.string.switch_no),
        SettingSpec.Toggle("settings_auto_refresh", R.string.settings_auto_refresh, false, summaryOn = R.string.switch_yes, summaryOff = R.string.switch_no)
    )),
    SettingsSection(R.string.settings_options, listOf(
        SettingSpec.Toggle("settings_dns_over_https", R.string.settings_dns_over_https, false, summary = R.string.settings_dns_over_https_summary),
        SettingSpec.Choice("settings_dns_over_https_dns", R.string.settings_dns_over_https_dns, "cloudflare", R.array.settings_dns_over_https_dns_entries, R.array.settings_dns_over_https_dns_entries_values),
        SettingSpec.Toggle("settings_disable_sni", R.string.settings_disable_sni, false, summary = R.string.settings_disable_sni_summary),
        SettingSpec.Toggle("settings_bypass_waf", R.string.settings_bypass_waf, false, summary = R.string.settings_bypass_waf_summary),
        SettingSpec.Toggle("settings_safe_mode", R.string.settings_safe_mode, true, summaryOn = R.string.switch_on, summaryOff = R.string.switch_off),
        SettingSpec.Choice("settings_page_limit", R.string.settings_page_limit, "30", R.array.settings_page_limit_entries, R.array.settings_page_limit_entries_values),
        SettingSpec.Choice("settings_detail_size", R.string.settings_detail_size, "sample", R.array.settings_post_size_entries, R.array.settings_post_size_entries_values),
        SettingSpec.Choice("settings_download_size", R.string.settings_download_size, "larger", R.array.settings_post_size_entries, R.array.settings_post_size_entries_values)
    )),
    SettingsSection(R.string.settings_muzei, listOf(
        SettingSpec.Choice("settings_muzei_limit", R.string.settings_muzei_limit, "20", R.array.settings_muzei_limit_entries, R.array.settings_muzei_limit_entries_values),
        SettingSpec.Choice("settings_muzei_size", R.string.settings_muzei_size, "larger", R.array.settings_post_size_entries, R.array.settings_post_size_entries_values)
    )),
    SettingsSection(R.string.settings_storage, listOf(
        SettingSpec.Action("settings_download_path", R.string.settings_download_path),
        SettingSpec.Action("settings_clear_cache", R.string.settings_clear_cache, summary = R.string.settings_clear_cache_summery)
    ))
)

val settingsByKey = settingsSections.flatMap { it.preferences }.associateBy { it.key }

data class SettingsUiState(
    val values: Map<String, String> = emptyMap(),
    val expanded: Boolean = false,
    val restartKeys: Set<String> = emptySet(),
    val clearingCache: Boolean = false,
    val cacheClearFailed: Boolean = false,
) {
    fun value(spec: SettingSpec): String = values[spec.key] ?: when (spec) {
        is SettingSpec.Choice -> spec.defaultValue
        is SettingSpec.Toggle -> spec.defaultValue.toString()
        is SettingSpec.Action -> ""
    }

    fun isVisible(spec: SettingSpec, night: Boolean): Boolean = when (spec.key) {
        Settings.NIGHT_THEME_KEY -> night
        Settings.GRID_RATIO_KEY -> values[Settings.GRID_MODE_KEY] == "fixed"
        Settings.DNS_OVER_HTTPS_PROVIDER, Settings.DISABLE_SNI_KEY ->
            values[Settings.DNS_OVER_HTTPS] == "true"
        else -> true
    }
}

sealed interface SettingsRow {
    data class Category(val title: Int) : SettingsRow
    data class Preference(val spec: SettingSpec) : SettingsRow
    data class Expand(val hiddenTitles: List<Int>) : SettingsRow
}

/** Match PreferenceScreen's five visible controls and grouped expansion summary. */
fun SettingsUiState.rows(night: Boolean): List<SettingsRow> {
    val rows = mutableListOf<SettingsRow>()
    val hiddenTitles = mutableListOf<Int>()
    var count = 0
    settingsSections.forEach { section ->
        val visible = section.preferences.filter { isVisible(it, night) }
        if (visible.isNotEmpty()) {
            val categoryHidden = !expanded && count >= 5
            if (categoryHidden) hiddenTitles += section.title
            else rows += SettingsRow.Category(section.title)
            visible.forEach { spec ->
                if (expanded || count < 5) rows += SettingsRow.Preference(spec)
                else if (!categoryHidden) hiddenTitles += spec.title
                count++
            }
        }
    }
    if (hiddenTitles.isNotEmpty()) rows += SettingsRow.Expand(hiddenTitles)
    return rows
}
