package com.mattzilla30.mbooru.ui.compose.detail

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.player.MediaResources
import com.mattzilla30.mbooru.player.PlayerHolder
import org.koin.android.ext.android.get

/** Android media integration resolves its owned application resources at this boundary. */
@Composable
internal fun rememberPlayerHolder(booru: Booru?, mediaKey: Any? = null): PlayerHolder {
    val application = LocalContext.current.applicationContext as Application
    val resources = remember(application) { application.get<MediaResources>() }
    return remember(booru?.uid, resources, mediaKey) { PlayerHolder(resources, booru) }
}
