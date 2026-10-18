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

import com.mattzilla30.mbooru.data.api.BooruApis
import com.mattzilla30.mbooru.data.database.MyDatabase
import kotlinx.coroutines.cancel
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module
import org.koin.dsl.onClose

val appModules = module {
    single {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
    } onClose { it?.cancel() }
    single {
        com.mattzilla30.mbooru.data.settings.DataStorePreferences(
            androidApplication(),
            get(),
            get(),
        )
    }
    single<android.content.SharedPreferences> {
        get<com.mattzilla30.mbooru.data.settings.DataStorePreferences>()
    }
    single<com.mattzilla30.mbooru.data.credentials.CredentialRepository> {
        com.mattzilla30.mbooru.data.credentials.DeviceCredentialRepository(
            androidApplication(),
            com.mattzilla30.mbooru.data.credentials.AndroidCredentialCipher(androidApplication()),
        )
    }
    single { MyDatabase.create(androidApplication(), get()) } onClose { it?.close() }
    single { get<MyDatabase>().booruDao() }
    single { get<MyDatabase>().downloadDao() }
    single { get<MyDatabase>().postDao() }
    single { get<MyDatabase>().tagFilterDao() }
    single { get<MyDatabase>().muzeiDao() }
    single { get<MyDatabase>().historyDao() }
    single { get<MyDatabase>().nextDao() }
    single { SettingsRepository(get()) }
    single { com.mattzilla30.mbooru.okhttp.AndroidCookieJar(androidApplication()) }
    single {
        com.mattzilla30.mbooru.data.api.HttpTransport(androidApplication(), get(), get())
    } onClose { it?.close() }
    single { BooruApis(get()) }
    single { com.mattzilla30.mbooru.player.MediaResources(androidApplication(), get()) } onClose
        {
            it?.close()
        }
    single { com.mattzilla30.mbooru.data.api.AppUpdaterRepository(get(), get()) }
    single {
        com.mattzilla30.mbooru.data.repository.download.DownloadQueueRepository(
            androidApplication(),
            get(),
            get(),
        )
    }
    single { com.mattzilla30.mbooru.data.database.BooruManager(get(), get(), get()) }
    single { com.mattzilla30.mbooru.data.database.HistoryManager(get()) }
    single { com.mattzilla30.mbooru.data.database.MuzeiManager(get()) }
    single { com.mattzilla30.mbooru.data.database.TagFilterManager(get()) }
    single { com.mattzilla30.mbooru.data.database.NextManager(get()) }
}
