package com.mattzilla30.mbooru.data.api

import com.mattzilla30.mbooru.app.SettingsRepository

class AppUpdaterRepository(
    private val settings: SettingsRepository,
    private val transport: HttpTransport,
) {
    suspend fun checkUpdate() {
        try {
            val response = createApi<AppUpdaterApi>(transport.apiClient(false)).checkUpdate()
            val data = response.body()
            if (response.isSuccessful && data != null) {
                settings.latestVersionUrl = data.url
                settings.latestVersionName = data.versionName
                settings.latestVersionCode = data.versionCode
                settings.isAvailableOnStore = data.isAvailableStore
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            /* An unavailable optional update check must not interrupt browsing. */
        }
    }
}
