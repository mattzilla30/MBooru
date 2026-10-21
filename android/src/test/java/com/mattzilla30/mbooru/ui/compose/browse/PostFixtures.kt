package com.mattzilla30.mbooru.ui.compose.browse

import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.data.model.common.User

fun testPost(id: Int, width: Int = 200, height: Int = 200) =
    Post(
        id = id,
        uid = id.toLong(),
        booruUid = 1,
        index = id - 1,
        query = "test",
        width = width,
        height = height,
        size = 100,
        score = 0,
        rating = "s",
        time = 0,
        tags = emptyList(),
        preview = "",
        sample = "unsupported.bin",
        medium = "unsupported.bin",
        origin = "unsupported.bin",
        source = null,
        uploader = User(name = "test"),
    )
