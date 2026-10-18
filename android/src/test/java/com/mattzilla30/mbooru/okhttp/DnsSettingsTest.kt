package com.mattzilla30.mbooru.okhttp

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.data.api.SettingsDns
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class DnsSettingsTest {
    @Test
    fun `transport matches the settings screen default and follows changes on its existing resolver`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("dns-settings", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val settings = SettingsRepository(preferences)
        assertFalse(
            "DNS over HTTPS must match the disabled settings-screen default",
            settings.isDohEnable,
        )
        val systemAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        val encryptedAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 2))
        var systemCalls = 0
        var encryptedCalls = 0
        val resolver =
            SettingsDns(
                settings,
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        systemCalls++
                        return listOf(systemAddress)
                    }
                },
            ) {
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        encryptedCalls++
                        return listOf(encryptedAddress)
                    }
                }
            }
        assertEquals(listOf(systemAddress), resolver.lookup("media.test"))
        preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, true).commit()
        assertEquals(listOf(encryptedAddress), resolver.lookup("media.test"))
        preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, false).commit()
        assertEquals(listOf(systemAddress), resolver.lookup("media.test"))
        assertEquals(2, systemCalls)
        assertEquals(1, encryptedCalls)
    }

    @Test
    fun `explicit encrypted DNS never silently falls back to system DNS`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("dns-failure", Context.MODE_PRIVATE)
        preferences.edit().putBoolean(Settings.DNS_OVER_HTTPS, true).commit()
        val resolver =
            SettingsDns(
                SettingsRepository(preferences),
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        throw AssertionError("Unexpected plaintext fallback")
                },
            ) {
                object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> =
                        throw UnknownHostException("Resolver unavailable")
                }
            }
        try {
            resolver.lookup("media.test")
            fail("Expected the selected resolver failure")
        } catch (_: UnknownHostException) {}
    }
}
