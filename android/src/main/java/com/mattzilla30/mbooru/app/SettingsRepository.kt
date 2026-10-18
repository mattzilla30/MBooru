/*
 * Copyright (C) 2020. by onlymash <fiepi.dev@gmail.com>, All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.mattzilla30.mbooru.app

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.okhttp.DohProviders
import com.mattzilla30.mbooru.okhttp.NoSniFactory

import com.mattzilla30.mbooru.app.Settings.ACTIVE_MUZEI_UID_KEY
import com.mattzilla30.mbooru.app.Settings.AUTO_HIDE_BOTTOM_BAR_KEY
import com.mattzilla30.mbooru.app.Settings.AUTO_REFRESH_KEY
import com.mattzilla30.mbooru.app.Settings.BOORU_UID_ACTIVATED_KEY
import com.mattzilla30.mbooru.app.Settings.BYPASS_WAF_KEY
import com.mattzilla30.mbooru.app.Settings.DETAIL_SIZE_KEY
import com.mattzilla30.mbooru.app.Settings.DISABLE_SNI_KEY
import com.mattzilla30.mbooru.app.Settings.DNS_OVER_HTTPS
import com.mattzilla30.mbooru.app.Settings.DNS_OVER_HTTPS_PROVIDER
import com.mattzilla30.mbooru.app.Settings.DOWNLOAD_PATH_KEY
import com.mattzilla30.mbooru.app.Settings.DOWNLOAD_SIZE_KEY
import com.mattzilla30.mbooru.app.Settings.GRID_MODE_DEFAULT
import com.mattzilla30.mbooru.app.Settings.GRID_MODE_KEY
import com.mattzilla30.mbooru.app.Settings.GRID_RATIO_1_1
import com.mattzilla30.mbooru.app.Settings.GRID_RATIO_KEY
import com.mattzilla30.mbooru.app.Settings.GRID_ROUNDED_KEY
import com.mattzilla30.mbooru.app.Settings.GRID_WIDTH_KEY
import com.mattzilla30.mbooru.app.Settings.GRID_WIDTH_LARGE
import com.mattzilla30.mbooru.app.Settings.GRID_WIDTH_NORMAL
import com.mattzilla30.mbooru.app.Settings.GRID_WIDTH_SMALL
import com.mattzilla30.mbooru.app.Settings.IS_AVAILABLE_STORE
import com.mattzilla30.mbooru.app.Settings.LATEST_VERSION_CODE_KEY
import com.mattzilla30.mbooru.app.Settings.LATEST_VERSION_NAME_KEY
import com.mattzilla30.mbooru.app.Settings.LATEST_VERSION_URL_KEY
import com.mattzilla30.mbooru.app.Settings.MUZEI_LIMIT_KEY
import com.mattzilla30.mbooru.app.Settings.MUZEI_SIZE_KEY
import com.mattzilla30.mbooru.app.Settings.NIGHT_MODE_BATTERY
import com.mattzilla30.mbooru.app.Settings.NIGHT_MODE_KEY
import com.mattzilla30.mbooru.app.Settings.NIGHT_MODE_OFF
import com.mattzilla30.mbooru.app.Settings.NIGHT_MODE_ON
import com.mattzilla30.mbooru.app.Settings.NIGHT_MODE_SYSTEM
import com.mattzilla30.mbooru.app.Settings.NIGHT_THEME_DEFAULT
import com.mattzilla30.mbooru.app.Settings.NIGHT_THEME_KEY
import com.mattzilla30.mbooru.app.Settings.PAGE_LIMIT_KEY
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_LARGER
import com.mattzilla30.mbooru.app.Settings.POST_SIZE_SAMPLE
import com.mattzilla30.mbooru.app.Settings.SAFE_MODE_KEY
import com.mattzilla30.mbooru.app.Settings.SAUCE_NAO_API_KEY_KEY
import com.mattzilla30.mbooru.app.Settings.SHOW_ALL_TAGS_KEY
import com.mattzilla30.mbooru.app.Settings.SHOW_INFO_BAR_KEY

class SettingsRepository(private val sp: SharedPreferences) {
    var activatedBooruUid: Long
        get() = sp.getLong(BOORU_UID_ACTIVATED_KEY, -1L)
        set(value) = sp.edit().putLong(
            BOORU_UID_ACTIVATED_KEY, value).apply()

    var safeMode: Boolean
        get() = sp.getBoolean(SAFE_MODE_KEY, true)
        set(value) = sp.edit().putBoolean(
            SAFE_MODE_KEY, value).apply()

    var pageLimit: Int
        get() = sp.getString(PAGE_LIMIT_KEY, "30")?.toIntOrNull()?.coerceIn(1, 200) ?: 30
        set(value) = sp.edit().putString(
            PAGE_LIMIT_KEY, value.toString()).apply()

    var muzeiLimit: Int
        get() = sp.getString(MUZEI_LIMIT_KEY, "20")?.toIntOrNull()?.coerceIn(1, 100) ?: 20
        set(value) = sp.edit().putString(
            MUZEI_LIMIT_KEY, value.toString()).apply()

    var detailSize: String
        get() = sp.getString(
            DETAIL_SIZE_KEY,
            POST_SIZE_SAMPLE
        ) ?: POST_SIZE_SAMPLE
        set(value) = sp.edit().putString(
            DETAIL_SIZE_KEY, value).apply()

    var downloadSize: String
        get() = sp.getString(
            DOWNLOAD_SIZE_KEY,
            POST_SIZE_SAMPLE
        ) ?: POST_SIZE_LARGER
        set(value) = sp.edit().putString(
            DOWNLOAD_SIZE_KEY, value).apply()

    var muzeiSize: String
        get() = sp.getString(
            MUZEI_SIZE_KEY,
            POST_SIZE_SAMPLE
        ) ?: POST_SIZE_LARGER
        set(value) = sp.edit().putString(
            MUZEI_SIZE_KEY, value).apply()

    private var nightModeString: String
        get() = sp.getString(
            NIGHT_MODE_KEY,
            NIGHT_MODE_SYSTEM
        ) ?: NIGHT_MODE_SYSTEM
        set(value) = sp.edit().putString(
            NIGHT_MODE_KEY, value).apply()

    @AppCompatDelegate.NightMode
    val nightMode: Int
        get() = when (nightModeString) {
            NIGHT_MODE_ON -> AppCompatDelegate.MODE_NIGHT_YES
            NIGHT_MODE_OFF -> AppCompatDelegate.MODE_NIGHT_NO
            NIGHT_MODE_BATTERY -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

    val isNightModeOn: Boolean
        get() = nightModeString == NIGHT_MODE_ON

    private val nightThemeString: String
        get() = sp.getString(
            NIGHT_THEME_KEY,
            NIGHT_THEME_DEFAULT
        ) ?: NIGHT_THEME_DEFAULT

    val isNightThemeDark: Boolean
        get() = nightThemeString == "dark"

    val isRoundedGrid: Boolean
        get() = sp.getBoolean(GRID_ROUNDED_KEY, true)

    val gridMode: String
        get() = sp.getString(
            GRID_MODE_KEY,
            GRID_MODE_DEFAULT
        ) ?: GRID_MODE_DEFAULT

    val gridRatio: String
        get() = sp.getString(
            GRID_RATIO_KEY,
            GRID_RATIO_1_1
        ) ?: GRID_RATIO_1_1

    val gridWidthResId: Int
        get() = when (gridWidthString) {
            GRID_WIDTH_SMALL -> R.dimen.post_item_width_small
            GRID_WIDTH_NORMAL -> R.dimen.post_item_width_normal
            else -> R.dimen.post_item_width_large
        }

    private var gridWidthString: String
        get() = sp.getString(
            GRID_WIDTH_KEY,
            GRID_WIDTH_SMALL
        ) ?: GRID_WIDTH_SMALL
        set(value) = sp.edit().putString(
            GRID_WIDTH_KEY, value).apply()

    val isLargeWidth: Boolean
        get() = gridWidthString == GRID_WIDTH_LARGE

    var activeMuzeiUid: Long
        get() = sp.getLong(ACTIVE_MUZEI_UID_KEY, 0L)
        set(value) = sp.edit().putLong(
            ACTIVE_MUZEI_UID_KEY, value).apply()

    var showInfoBar: Boolean
        get() = sp.getBoolean(SHOW_INFO_BAR_KEY, false)
        set(value) = sp.edit().putBoolean(
            SHOW_INFO_BAR_KEY, value).apply()

    var isShowAllTags: Boolean
        get() = sp.getBoolean(SHOW_ALL_TAGS_KEY, false)
        set(value) = sp.edit().putBoolean(
            SHOW_ALL_TAGS_KEY, value).apply()

    var autoHideBottomBar: Boolean
        get() = sp.getBoolean(AUTO_HIDE_BOTTOM_BAR_KEY, false)
        set(value) = sp.edit().putBoolean(
            AUTO_HIDE_BOTTOM_BAR_KEY, value).apply()

    var autoRefresh: Boolean
        get() = sp.getBoolean(AUTO_REFRESH_KEY, false)
        set(value) = sp.edit().putBoolean(AUTO_REFRESH_KEY, value).apply()

    var downloadDirPath: String?
        get() = sp.getString(DOWNLOAD_PATH_KEY, "")
        set(value) = sp.edit().putString(
            DOWNLOAD_PATH_KEY, value).apply()

    var latestVersionCode: Long
        get() = sp.getLong(LATEST_VERSION_CODE_KEY, -1L)
        set(value) = sp.edit().putLong(
            LATEST_VERSION_CODE_KEY, value).apply()

    var latestVersionName: String
        get() = sp.getString(LATEST_VERSION_NAME_KEY, " ") ?: " "
        set(value) = sp.edit().putString(
            LATEST_VERSION_NAME_KEY, value).apply()

    var latestVersionUrl: String
        get() = sp.getString(LATEST_VERSION_URL_KEY, "") ?: ""
        set(value) = sp.edit().putString(
            LATEST_VERSION_URL_KEY, value).apply()

    var isAvailableOnStore: Boolean
        get() = sp.getBoolean(IS_AVAILABLE_STORE, false)
        set(value) = sp.edit().putBoolean(
            IS_AVAILABLE_STORE, value).apply()

    var sauceNaoApiKey: String
        get() = sp.getString(SAUCE_NAO_API_KEY_KEY, "") ?: ""
        set(value) = sp.edit().putString(
            SAUCE_NAO_API_KEY_KEY, value).apply()

    val isDohEnable: Boolean get() = sp.getBoolean(DNS_OVER_HTTPS, true)

    private val isSniDisable: Boolean get() = sp.getBoolean(DISABLE_SNI_KEY, false)

    val isBypassWAF: Boolean get() = sp.getBoolean(BYPASS_WAF_KEY, false)

    @Volatile private var cachedDns: Pair<Pair<String?, Boolean>, DnsOverHttps>? = null
    val doh: DnsOverHttps get() = synchronized(this) {
        val config = sp.getString(DNS_OVER_HTTPS_PROVIDER, "cloudflare") to isSniDisable
        cachedDns?.takeIf { it.first == config }?.second ?: createDoh().also { cachedDns = config to it }
    }

    private fun createDoh(): DnsOverHttps {
        val clientBuilder = OkHttpClient.Builder()
        if (isSniDisable) {
            clientBuilder.apply {
                connectionSpecs(NoSniFactory.tls)
                sslSocketFactory(NoSniFactory, NoSniFactory.defaultTrustManager)
            }
        }
        val client = clientBuilder.build()
        return when (sp.getString(DNS_OVER_HTTPS_PROVIDER, "cloudflare")) {
            "google" -> DohProviders.buildGoogle(client)
            "dnssb" -> DohProviders.buildDnsSb(client)
            "cleanbrowsing" -> DohProviders.buildCleanBrowsing(client)
            "opendns" -> DohProviders.buildOpenDns(client)
            "quad9" -> DohProviders.buildQuad9(client)
            else -> DohProviders.buildCloudflare(client)
        }
    }

}
