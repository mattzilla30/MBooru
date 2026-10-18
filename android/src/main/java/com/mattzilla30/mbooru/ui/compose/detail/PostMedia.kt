package com.mattzilla30.mbooru.ui.compose.detail

import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mattzilla30.mbooru.data.model.common.Booru
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.player.PlayerHolder
import com.mattzilla30.mbooru.widget.DismissFrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

@Composable
fun PostMedia(post: Post, booru: Booru, size: String, active: Boolean,
    dismiss: DismissFrameLayout.OnDismissListener, tap: () -> Unit,
    hold: () -> Unit, zoom: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    key(post.id, booru.uid, size, post.sample, post.medium, post.origin) {
        PostMediaContent(post, booru, size, active, dismiss, tap, hold, zoom, modifier)
    }
}

@Composable
private fun PostMediaContent(post: Post, booru: Booru, size: String, active: Boolean,
    dismiss: DismissFrameLayout.OnDismissListener, tap: () -> Unit,
    hold: () -> Unit, zoom: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val currentTap by rememberUpdatedState(tap)
    val currentHold by rememberUpdatedState(hold)
    val currentZoom by rememberUpdatedState(zoom)
    var renderer by remember { mutableStateOf<PostMediaRenderer?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = rememberPlayerHolder(booru)
    AndroidView(factory = { context ->
        PostMediaRenderer(context, post, size, Dispatchers.IO.asExecutor(), dismiss, { currentTap() }, { currentHold() }, { currentZoom(it) })
            .also { renderer = it }.view
    }, modifier = modifier, onRelease = { renderer?.close(); holder.release() })
    val playbackScope = rememberCoroutineScope()
    DisposableEffect(renderer, active, lifecycle) {
        var startJob: kotlinx.coroutines.Job? = null
        fun start() {
            if (!active || startJob?.isActive == true || !holder.playerIsNull()) return
            startJob = playbackScope.launch {
                holder.resources.prepare()
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                renderer?.playerView?.takeIf { active }?.let {
                    holder.create(it.context.applicationContext)
                    holder.start(it.context.applicationContext, renderer!!.url.toUri(), it)
                }
            }
        }
        fun stop() { startJob?.cancel(); startJob = null; renderer?.playerView?.onPause(); renderer?.playerView?.player = null; holder.release() }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) start()
            if (event == Lifecycle.Event.ON_PAUSE) stop()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { lifecycle.removeObserver(observer); stop() }
    }
}
