package com.mattzilla30.mbooru.okhttp

import com.mattzilla30.mbooru.app.Keys
import com.mattzilla30.mbooru.app.Values
import okhttp3.Interceptor
import okhttp3.Response

class RequestHeaderInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val scheme = url.scheme
        val host = url.host
        var referer = "$scheme://$host/post"
        if (host == "rule34.xxx" || host.endsWith(".rule34.xxx")) {
            referer = "https://rule34.xxx/"
        }
        val ua =
            if (host.contains("sankaku", ignoreCase = true)) {
                referer = Values.SANKAKU_REFERER
                Values.PC_USER_AGENT
            } else Values.MOBILE_USER_AGENT
        val builder =
            chain.request().newBuilder().apply {
                removeHeader(Keys.HEADER_USER_AGENT)
                addHeader(Keys.HEADER_USER_AGENT, ua)
                header(Keys.HEADER_REFERER, referer)
            }
        return chain.proceed(builder.build())
    }
}
