package com.mattzilla30.mbooru.data.api

import android.content.Context
import com.mattzilla30.mbooru.app.SettingsRepository
import com.mattzilla30.mbooru.okhttp.*
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/** One owned pool, timeout, cookie and DNS policy; protocol headers remain engine-specific. */
class HttpTransport(
    context: Context,
    private val settings: SettingsRepository,
    val cookies: AndroidCookieJar,
    private val fixtureInterceptors: List<okhttp3.Interceptor> = emptyList(),
) : AutoCloseable {
    private val context = context.applicationContext
    private val pool = ConnectionPool()
    private val dispatcher = Dispatcher()
    private val dns = SettingsDns(settings)

    private fun base(): OkHttpClient =
        OkHttpClient.Builder()
            .connectionPool(pool)
            .dispatcher(dispatcher)
            .cookieJar(cookies)
            .dns(dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(
                false
            ) // Account/favorite writes must not be replayed after an ambiguous failure.
            .apply {
                if (settings.isBypassWAF) addInterceptor(CloudflareInterceptor(context, cookies))
            }
            .build()

    fun apiClient(sankaku: Boolean): OkHttpClient =
        base()
            .newBuilder()
            .addInterceptor(if (sankaku) ApiSankakuInterceptor() else ApiInterceptor())
            .addInterceptor(BoundedApiResponseInterceptor())
            .apply { fixtureInterceptors.forEach(::addInterceptor) }
            .build()

    override fun close() {
        dispatcher.cancelAll()
        dispatcher.executorService.shutdown()
        pool.evictAll()
    }

    fun mediaClient(): OkHttpClient =
        base()
            .newBuilder()
            // Image/video GETs can safely recover from a stale pooled connection or an
            // unreachable first DNS address. Account writes retain the base no-replay policy.
            .retryOnConnectionFailure(true)
            .addNetworkInterceptor(RequestHeaderInterceptor())
            .addInterceptor(ProgressInterceptor())
            .apply { fixtureInterceptors.forEach(::addInterceptor) }
            .build()
}
