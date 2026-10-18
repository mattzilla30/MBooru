package com.mattzilla30.mbooru.ui.compose.detail

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.ui.PlayerView
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.player.PlayerHolder

/** Retain Media3 controls and the application's authenticated, cached playback stack. */
@Composable
fun VideoPreview(url: String, modifier: Modifier = Modifier, booru: Booru? = null, active: Boolean = true) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val holder = remember(url, booru?.uid) { PlayerHolder(booru) }
    var view by remember { mutableStateOf<PlayerView?>(null) }
    AndroidView(factory = { PlayerView(it).also { player -> view = player } }, modifier = modifier,
        onRelease = { it.onPause(); it.player = null; holder.release() })
    DisposableEffect(owner, view, active, url) {
        fun start() {
            if (active) view?.let { player -> holder.create(context.applicationContext); holder.start(context.applicationContext, url.toUri(), player) }
        }
        fun stop() { view?.onPause(); view?.player = null; holder.release() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { owner.lifecycle.removeObserver(observer); stop() }
    }
}
