package com.mattzilla30.mbooru

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import com.mattzilla30.mbooru.data.api.ShimmieApi
import com.mattzilla30.mbooru.data.api.createApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Config.NONE

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = NONE)
class ShimmieApiTest {

    private lateinit var url: HttpUrl
    private lateinit var shimmieApi: ShimmieApi

    @Before
    fun setup() {
        url = HttpUrl.Builder()
            .scheme("https")
            .host("rule34.paheal.net")
            .addPathSegments("api/danbooru/find_posts")
            .addQueryParameter("limit", "2")
            .build()
        shimmieApi = createApi()
    }

    @Test
    @Throws(Exception::class)
    fun run() {
        GlobalScope.launch(Dispatchers.IO) {
            val response = shimmieApi.getPosts(url)
            println(response.body())
            assert(response.isSuccessful)
        }
    }
}