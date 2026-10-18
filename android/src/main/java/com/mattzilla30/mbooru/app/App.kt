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

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.google.android.material.color.DynamicColors
import okhttp3.OkHttpClient
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings.nightMode
import com.mattzilla30.mbooru.common.di.commonModules
import com.mattzilla30.mbooru.okhttp.AndroidCookieJar
import com.mattzilla30.mbooru.okhttp.CloudflareInterceptor
import com.mattzilla30.mbooru.okhttp.ProgressInterceptor
import com.mattzilla30.mbooru.okhttp.RequestHeaderInterceptor
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application(), ImageLoaderFactory {

    val preferences: android.content.SharedPreferences by inject()

    companion object {
        lateinit var app: App
        private const val CACHE_MAX_BYTES = 256L * 1024L * 1024L
    }

    override fun onCreate() {
        super.onCreate()
        app = this
        startKoin {
            androidContext(app)
            modules(appModules + commonModules)
        }
        initial()
    }

    private fun initial() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        val scope by inject<kotlinx.coroutines.CoroutineScope>()
        scope.launch {
            val store by inject<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
            try {
                store.awaitReady()
                withContext(Dispatchers.Main) { AppCompatDelegate.setDefaultNightMode(nightMode) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { /* The foreground activity offers retry without clearing data. */ }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val builder = OkHttpClient.Builder()
            .cookieJar(AndroidCookieJar)
            .addNetworkInterceptor(RequestHeaderInterceptor())
            .addInterceptor(ProgressInterceptor())
        if (Settings.isBypassWAF) {
            builder.addInterceptor(CloudflareInterceptor(this))
        }
        if (Settings.isDohEnable) {
            builder.dns(Settings.doh)
        }
        val diskCache = DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(CACHE_MAX_BYTES)
            .build()
        return ImageLoader.Builder(this)
            .diskCache(diskCache)
            .allowHardware(false)
            .okHttpClient(builder.build())
            .crossfade(true)
            .error(R.drawable.ic_placeholder_error)
            .build()
    }
}
