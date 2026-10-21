package com.mattzilla30.mbooru.data.api

import com.mattzilla30.mbooru.app.SettingsRepository
import java.net.InetAddress
import okhttp3.Dns

/** Read the current setting even when Retrofit or Coil retains its client between requests. */
class SettingsDns(
    private val settings: SettingsRepository,
    private val system: Dns = Dns.SYSTEM,
    private val encrypted: () -> Dns = { settings.doh },
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        (if (settings.isDohEnable) encrypted() else system).lookup(hostname)
}
