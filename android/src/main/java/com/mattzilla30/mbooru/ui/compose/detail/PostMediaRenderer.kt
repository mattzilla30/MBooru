package com.mattzilla30.mbooru.ui.compose.detail

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.disk.DiskCache
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.chrisbanes.photoview.PhotoView
import com.mattzilla30.mbooru.R
import com.mattzilla30.mbooru.app.Settings
import com.mattzilla30.mbooru.data.model.common.Post
import com.mattzilla30.mbooru.decoder.CustomDecoder
import com.mattzilla30.mbooru.decoder.CustomRegionDecoder
import com.mattzilla30.mbooru.extension.*
import com.mattzilla30.mbooru.widget.DismissFrameLayout
import java.util.concurrent.Executor

/** Specialized media views remain behind one disposable Compose interoperability boundary. */
class PostMediaRenderer(
    context: Context, post: Post, size: String, executor: Executor,
    dismiss: DismissFrameLayout.OnDismissListener, tap: () -> Unit, hold: () -> Unit,
    zoom: (Boolean) -> Unit,
) : AutoCloseable {
    val view = DismissFrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        setDismissListener(dismiss)
    }
    var playerView: PlayerView? = null
        private set
    private var request: Disposable? = null
    private var retryAction: (() -> Unit)? = null
    private var ownedLoader: ImageLoader? = null
    private var snapshot: DiskCache.Snapshot? = null
    private var tiled: SubsamplingScaleImageView? = null
    private var photo: PhotoView? = null
    val url = when (size) {
        Settings.POST_SIZE_SAMPLE -> post.sample
        Settings.POST_SIZE_LARGER -> post.medium
        else -> post.origin
    }

    init {
        fun shared(target: View) { target.transitionName = "post_${post.id}" }
        fun touchZoom(value: Boolean) { view.isDismissEnabled = !value; zoom(value) }
        val progress = ProgressBar(context).apply { layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER) }
        val error = android.widget.Button(context).apply {
            text = context.getString(R.string.image_load_retry)
            minHeight = (48 * context.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.CENTER)
        }
        fun showError() {
            view.removeView(progress)
            if (error.parent == null) view.addView(error)
        }
        error.setOnClickListener {
            view.removeView(error)
            if (progress.parent == null) view.addView(progress)
            retryAction?.invoke() ?: showError()
        }
        when {
            url.isVideo() -> {
                playerView = (LayoutInflater.from(context).inflate(R.layout.item_exoplayer, view, false) as PlayerView).apply {
                    shared(this); setOnClickListener { tap() }
                    setOnLongClickListener { hold(); true }
                }
                view.addView(playerView)
            }
            url.isImage() && size == Settings.POST_SIZE_ORIGIN && !url.isGifImage() -> {
                val image = SubsamplingScaleImageView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setExecutor(executor)
                    setBitmapDecoderFactory { CustomDecoder() }
                    setRegionDecoderFactory { CustomRegionDecoder() }
                    shared(this)
                    setOnClickListener { tap() }
                    setOnLongClickListener { hold(); true }
                    setOnStateChangedListener(object : SubsamplingScaleImageView.OnStateChangedListener {
                        override fun onScaleChanged(scale: Float, origin: Int) { touchZoom(scale > minScale + 0.01f) }
                        override fun onCenterChanged(center: android.graphics.PointF?, origin: Int) {}
                        override fun onStateChanged(scale: Float, center: android.graphics.PointF?, origin: Int) { touchZoom(scale > minScale + 0.01f) }
                    })
                    setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        override fun onImageLoaded() { view.removeView(progress) }
                        override fun onImageLoadError(e: Exception?) { showError() }
                    })
                }
                tiled = image
                view.addView(image); view.addView(progress)
                // Decode a preview while fetching the encoded original into Coil's existing disk cache.
                // The full original is tiled from the retained cache file, rather than expanded into RAM.
                if (url.toUri().scheme == "file") {
                    retryAction = { image.setImage(ImageSource.uri(url.toUri())) }
                    retryAction?.invoke()
                } else load(context.imageLoader, ImageRequest.Builder(context).data(url).diskCacheKey(url)
                    .size(1024).allowHardware(false).memoryCachePolicy(CachePolicy.DISABLED)
                    .listener(onSuccess = { _, result ->
                        val cached = context.imageLoader.diskCache?.openSnapshot(url)
                        snapshot = cached
                        if (cached != null) image.setImage(ImageSource.uri(cached.data.toFile().toUri()))
                        else image.setImage(ImageSource.bitmap(result.drawable.toBitmap()))
                    }, onError = { _, _ -> showError() }).build())
            }
            url.isImage() -> {
                val image = PhotoView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    shared(this)
                    setOnViewTapListener { _, _, _ -> tap() }
                    setOnLongClickListener { hold(); true }
                    setOnMatrixChangeListener { touchZoom(scale > minimumScale + 0.01f) }
                }
                photo = image
                view.addView(image); view.addView(progress)
                val loader = if (url.isGifImage()) context.imageLoader.newBuilder().components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
                }.build().also { ownedLoader = it } else context.imageLoader
                // A target crossfade replaces the drawable at completion, which resets PhotoView's
                // supplemental matrix and can discard a pinch started while the image appears.
                load(loader, ImageRequest.Builder(context).data(url).crossfade(false).target(image)
                    .listener(onSuccess = { _, _ -> view.removeView(progress) }, onError = { _, _ -> showError() }).build())
            }
            else -> view.addView(TextView(context).apply {
                text = context.getString(R.string.browse_unsupported_format, url.fileExt())
                setTextColor(android.graphics.Color.WHITE); gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(-1, -1); shared(this)
            })
        }
    }
    private fun load(loader: ImageLoader, imageRequest: ImageRequest) {
        retryAction = {
            request?.dispose()
            tiled?.recycle()
            snapshot?.close()
            snapshot = null
            request = loader.enqueue(imageRequest)
        }
        retryAction?.invoke()
    }

    override fun close() {
        retryAction = null
        request?.dispose()
        (photo?.drawable as? android.graphics.drawable.Animatable)?.stop()
        tiled?.recycle()
        snapshot?.close()
        snapshot = null
        ownedLoader?.shutdown()
        playerView?.onPause()
        playerView?.player = null
        view.setDismissListener(null)
    }
}
