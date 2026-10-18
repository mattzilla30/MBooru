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
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.google.android.material.color.DynamicColors
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.common.di.commonModules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

open class App : Application(), ImageLoaderFactory {
    private val settings by inject<SettingsRepository>()

    companion object {
        private const val CACHE_MAX_BYTES = 256L * 1024L * 1024L
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@App)
            modules(appModules + commonModules + environmentModules())
        }
        if (com.mattzilla30.mbooru.BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            android.os.StrictMode.setVmPolicy(
                android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
        initial()
    }

    protected open fun environmentModules(): List<org.koin.core.module.Module> = emptyList()

    private fun initial() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }
        val scope by inject<kotlinx.coroutines.CoroutineScope>()
        scope.launch {
            val store by inject<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
            try {
                store.awaitReady()
                withContext(Dispatchers.Main) {
                    AppCompatDelegate.setDefaultNightMode(settings.nightMode)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                /* The foreground activity offers retry without clearing data. */
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val transport by inject<com.mattzilla30.mbooru.data.api.HttpTransport>()
        val diskCache =
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(CACHE_MAX_BYTES)
                .build()
        return ImageLoader.Builder(this)
            .diskCache(diskCache)
            .allowHardware(false)
            .okHttpClient(transport.mediaClient())
            .crossfade(
                if (Build.VERSION.SDK_INT >= 26)
                    android.animation.ValueAnimator.areAnimatorsEnabled()
                else
                    android.provider.Settings.Global.getFloat(
                        contentResolver,
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                        1f,
                    ) > 0f
            )
            .error(R.drawable.ic_placeholder_error)
            .build()
    }
}
