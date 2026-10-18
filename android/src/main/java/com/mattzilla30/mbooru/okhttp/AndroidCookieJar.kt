package com.mattzilla30.mbooru.okhttp

import android.content.Context
import android.util.AtomicFile
import android.webkit.CookieManager
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** App-owned adapter to WebView's host-scoped cookie store; values never enter Room/backups. */
class AndroidCookieJar(context: Context) : CookieJar {
    private val manager
        get() = CookieManager.getInstance()

    @Serializable private data class Scope(val domain: String, val path: String, val name: String)

    private val metadata = AtomicFile(File(context.noBackupFilesDir, "cookie-scopes.json"))
    private val scopes by lazy {
        runCatching {
            Json.decodeFromString<List<Scope>>(metadata.readFully().toString(Charsets.UTF_8))
                .toMutableSet()
        }
            .getOrElse { mutableSetOf() }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        var changed = false
        cookies.forEach { cookie ->
            manager.setCookie(url.toString(), cookie.toString())
            changed = scopes.add(Scope(cookie.domain, cookie.path, cookie.name)) || changed
        }
        if (changed) saveMetadata()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = get(url)

    fun get(url: HttpUrl): List<Cookie> =
        manager.getCookie(url.toString()).orEmpty().split(';').mapNotNull {
            Cookie.parse(url, it.trim())
        }

    fun set(url: String, cookies: List<String>) {
        val parsed = url.toHttpUrlOrNull() ?: return
        saveFromResponse(parsed, cookies.mapNotNull { Cookie.parse(parsed, it) })
    }

    @Synchronized
    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = 0): Int {
        val existing =
            scopes
                .filter {
                    (url.host == it.domain || url.host.endsWith(".${it.domain}")) &&
                        (cookieNames == null || it.name in cookieNames)
                }
                .toMutableSet()
        // Legacy WebView cookies may predate scope metadata. Try matching parent domains
        // and request-path prefixes as well as the root, without touching another site.
        val domains =
            generateSequence(url.host) { current ->
                    current.substringAfter('.', "").takeIf { it.contains('.') }
                }
                .toList()
        val paths = linkedSetOf("/")
        url.encodedPath.indices
            .filter { url.encodedPath[it] == '/' }
            .forEach { index ->
                paths.add(url.encodedPath.substring(0, index + 1))
                if (index > 0) paths.add(url.encodedPath.substring(0, index))
            }
        paths.add(url.encodedPath)
        get(url)
            .filter { cookieNames == null || it.name in cookieNames }
            .forEach { cookie ->
                domains.forEach { domain ->
                    paths.forEach { path -> existing.add(Scope(domain, path, cookie.name)) }
                }
            }
        existing.forEach { cookie ->
            manager.setCookie(
                url.toString(),
                "${cookie.name}=;Max-Age=$maxAge;Path=${cookie.path};Domain=${cookie.domain}",
            )
            manager.setCookie(url.toString(), "${cookie.name}=;Max-Age=$maxAge;Path=${cookie.path}")
        }
        scopes.removeAll(existing)
        if (existing.isNotEmpty()) {
            manager.flush()
            saveMetadata()
        }
        return existing.size
    }

    private fun saveMetadata() {
        val stream = metadata.startWrite()
        try {
            stream.write(Json.encodeToString(scopes.toList()).toByteArray())
            metadata.finishWrite(stream)
        } catch (failure: Exception) {
            metadata.failWrite(stream)
            throw failure
        }
    }
}
