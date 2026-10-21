package com.mattzilla30.mbooru.okhttp

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

class ApiResponseTooLargeException : IOException("The API response exceeds the supported size")

/** API documents are bounded; media/download clients continue streaming large files. */
class BoundedApiResponseInterceptor(private val maximumBytes: Long = 8L * 1024 * 1024) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        if (body.contentLength() > maximumBytes) {
            response.close()
            throw ApiResponseTooLargeException()
        }
        val source =
            object : ForwardingSource(body.source()) {
                    private var consumed = 0L

                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val count = super.read(sink, byteCount)
                        if (count > 0) {
                            consumed += count
                            if (consumed > maximumBytes) throw ApiResponseTooLargeException()
                        }
                        return count
                    }
                }
                .buffer()
        return response
            .newBuilder()
            .body(
                object : ResponseBody() {
                    override fun contentType() = body.contentType()

                    override fun contentLength() = body.contentLength()

                    override fun source() = source
                }
            )
            .build()
    }
}
