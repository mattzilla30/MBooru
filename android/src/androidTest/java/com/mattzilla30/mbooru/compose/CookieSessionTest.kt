package com.mattzilla30.mbooru.compose

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mattzilla30.mbooru.okhttp.AndroidCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CookieSessionTest {
    @Test fun signOutClearsNestedParentDomainCookiesAfterOwnerRecreationWithoutClearingAnotherSite() = runBlocking {
        withContext(Dispatchers.IO) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val domain = "fixture-${UUID.randomUUID()}.invalid"
            val target = "https://site.$domain/api/posts".toHttpUrl()
            val other = "https://other-${UUID.randomUUID()}.invalid/".toHttpUrl()
            val first = AndroidCookieJar(context)
            first.saveFromResponse(target, listOf(Cookie.Builder().domain(domain).path("/api")
                .name("fixture_session").value("target-value").secure().build()))
            first.saveFromResponse(other, listOf(Cookie.Builder().hostOnlyDomain(other.host).path("/")
                .name("fixture_session").value("other-value").secure().build()))
            try {
                assertEquals("target-value", first.get(target).first { it.name == "fixture_session" }.value)
                val restored = AndroidCookieJar(context)
                restored.remove(target.newBuilder().encodedPath("/").build())
                assertFalse(restored.get(target).any { it.name == "fixture_session" })
                assertEquals("other-value", restored.get(other).first { it.name == "fixture_session" }.value)
            } finally { first.remove(target); first.remove(other) }
        }
    }
}
