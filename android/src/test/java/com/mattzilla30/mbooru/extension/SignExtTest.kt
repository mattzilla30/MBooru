package com.mattzilla30.mbooru.extension

import android.app.Application
import android.content.pm.PackageInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28], manifest = Config.NONE)
class SignExtTest {
    @Test fun missingModernSigningInformationReturnsNoFingerprint() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = context.packageName })
        assertNull(context.getSignMd5())
    }

    @Suppress("DEPRECATION")
    @Config(sdk = [27])
    @Test fun legacyCertificateKeepsItsExistingFingerprint() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        })
        assertEquals("5289df737df57326fcdd22597afb1fac", context.getSignMd5())
    }

    @Suppress("DEPRECATION")
    @Config(sdk = [27])
    @Test fun emptyLegacyCertificateListReturnsNoFingerprint() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            signatures = emptyArray()
        })
        assertNull(context.getSignMd5())
    }
    @Test fun noConfiguredPlayCertificateCannotMatch() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        org.junit.Assert.assertFalse(context.matchesPlaySigningCertificate(""))
        org.junit.Assert.assertFalse(context.matchesPlaySigningCertificate("invalid"))
    }

    @Test
    @Config(sdk = [27])
    @Suppress("DEPRECATION")
    fun configuredPlayFingerprintMatchesCurrentCertificateOnly() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = context.packageName
            signatures = arrayOf(Signature(byteArrayOf(1, 2, 3)))
        })
        org.junit.Assert.assertTrue(context.matchesPlaySigningCertificate(
            "03:90:58:c6:f2:c0:cb:49:2c:53:3b:0a:4d:14:ef:77:cc:0f:78:ab:cc:ce:d5:28:7d:84:a1:a2:01:1c:fb:81"))
        org.junit.Assert.assertFalse(context.matchesPlaySigningCertificate("0".repeat(64)))
    }

}
