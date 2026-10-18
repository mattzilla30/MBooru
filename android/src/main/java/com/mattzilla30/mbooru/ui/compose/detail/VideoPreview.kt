package com.mattzilla30.mbooru.ui.compose.detail

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import com.mattzilla30.mbooru.data.model.common.Booru
import kotlinx.coroutines.launch

/** Retain Media3 controls and the application's authenticated, cached playback stack. */
@Composable
fun VideoPreview(
    url: String,
    modifier: Modifier = Modifier,
    booru: Booru? = null,
    active: Boolean = true,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val holder = rememberPlayerHolder(booru, url)
    var view by remember { mutableStateOf<PlayerView?>(null) }
    AndroidView(
        factory = { PlayerView(it).also { player -> view = player } },
        modifier = modifier,
        onRelease = {
            it.onPause()
            it.player = null
            holder.release()
        },
    )
    val playbackScope = rememberCoroutineScope()
    DisposableEffect(owner, view, active, url) {
        var startJob: kotlinx.coroutines.Job? = null
        fun start() {
            if (!active || startJob?.isActive == true || !holder.playerIsNull()) return
            startJob = playbackScope.launch {
                holder.resources.prepare()
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                view
                    ?.takeIf { active }
                    ?.let { player ->
                        holder.create(context.applicationContext)
                        holder.start(context.applicationContext, url.toUri(), player)
                    }
            }
        }
        fun stop() {
            startJob?.cancel()
            startJob = null
            view?.onPause()
            view?.player = null
            holder.release()
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            owner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}
