package com.mattzilla30.mbooru.player

import android.content.Context
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.mattzilla30.mbooru.data.api.HttpTransport
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@android.annotation.SuppressLint("UnsafeOptInUsageError")
class MediaResources(context: Context, val transport: HttpTransport) : AutoCloseable {
    private val database = DefaultDatabaseProvider(context.applicationContext)
    private val cached = lazy {
        SimpleCache(
            File(context.applicationContext.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
            database,
        )
    }
    val cache: SimpleCache
        get() = cached.value

    suspend fun prepare() {
        withContext(Dispatchers.IO) { cache }
    }

    override fun close() {
        if (cached.isInitialized()) cached.value.release()
        database.close()
    }
}
