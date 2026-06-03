package net.trequad.quadtv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlayerController(
    context: Context
) : QuadTvPlayer {
    private val libVLC = LibVLC(
        context,
        arrayListOf(
            "--network-caching=3000",
            "--live-caching=3000",
            "--file-caching=3000",
            "--vout=android-display",
            "--android-display-chroma=RV32",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--verbose=2"
        )
    )
    private val mediaPlayer = MediaPlayer(libVLC)
    private var attachedVideoLayout: VLCVideoLayout? = null
    private var attachStateListener: View.OnAttachStateChangeListener? = null
    private var viewsAttached = false
    private var pendingPlaybackRequest: StreamPlaybackRequest? = null
    private var playbackErrorListener: ((Throwable) -> Unit)? = null
    private var playbackStatusListener: ((String) -> Unit)? = null

    init {
        mediaPlayer.setEventListener { event ->
            val eventName = eventName(event.type)
            Log.i(TAG, "vlc event=$eventName type=${event.type}")
            if (event.type !in NOISY_PROGRESS_EVENTS) {
                notifyStatus(eventName)
            }
            if (event.type == MediaPlayer.Event.EncounteredError) {
                playbackErrorListener?.invoke(IllegalStateException("VLC encountered a playback error: $eventName"))
            }
        }
    }

    override fun attachSurface(surface: PlayerRenderSurface) {
        detachSurfaceViews()
        attachedVideoLayout = surface.vlcVideoLayout
        attachStateListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                Log.i(TAG, "vlc video layout attached")
                notifyStatus("video layout attached")
                attachViewsIfReady()
            }

            override fun onViewDetachedFromWindow(view: View) {
                Log.i(TAG, "vlc video layout detached")
                notifyStatus("video layout detached")
                pendingPlaybackRequest = null
                detachSurfaceViews()
            }
        }
        attachedVideoLayout?.addOnAttachStateChangeListener(attachStateListener)
        attachViewsIfReady()
    }

    private fun attachViewsIfReady() {
        val attachedVideoLayout = attachedVideoLayout ?: return
        if (viewsAttached || !attachedVideoLayout.isAttachedToWindow) return
        val width = attachedVideoLayout.width.takeIf { it > 0 } ?: 1920
        val height = attachedVideoLayout.height.takeIf { it > 0 } ?: 1080
        Log.i(TAG, "vlc video layout attach views ${width}x$height")
        notifyStatus("attach video layout ${width}x$height")
        mediaPlayer.attachViews(attachedVideoLayout, null, false, false)
        mediaPlayer.vlcVout.setWindowSize(width, height)
        viewsAttached = true
        pendingPlaybackRequest?.let { request ->
            pendingPlaybackRequest = null
            startPlaybackWhenSurfaceReady(request)
        }
    }

    private fun detachSurfaceViews() {
        if (viewsAttached) {
            Log.i(TAG, "surface detach views")
            notifyStatus("detach views")
            mediaPlayer.detachViews()
            viewsAttached = false
        }
        attachStateListener?.let { listener ->
            attachedVideoLayout?.removeOnAttachStateChangeListener(listener)
        }
        attachStateListener = null
        attachedVideoLayout = null
    }

    override fun setPlaybackErrorListener(listener: ((Throwable) -> Unit)?) {
        playbackErrorListener = listener
    }

    override fun setPlaybackStatusListener(listener: ((String) -> Unit)?) {
        playbackStatusListener = listener
    }

    override fun play(request: StreamPlaybackRequest) {
        attachViewsIfReady()
        startPlaybackWhenSurfaceReady(request)
    }

    private fun startPlaybackWhenSurfaceReady(request: StreamPlaybackRequest) {
        if (!viewsAttached) {
            Log.i(TAG, "play deferred waiting for surface")
            notifyStatus("waiting for video surface")
            pendingPlaybackRequest = request
            return
        }
        val networkCachingMs = (request.bufferConfig.sizeSeconds.coerceIn(3, 60) * 1000).toString()
        val url = request.url.trim()
        Log.i(TAG, "play url=${url.redactPlaybackUrl()} cacheMs=$networkCachingMs live=${request.isLive}")
        notifyStatus("play ${url.describePlaybackOutput()} cache=${networkCachingMs}ms")
        val media = Media(libVLC, Uri.parse(url))
        media.setHWDecoderEnabled(false, false)
        media.addOption(":vout=android-display")
        media.addOption(":android-display-chroma=RV32")
        media.addOption(":no-mediacodec-dr")
        media.addOption(":no-omxil-dr")
        media.addOption(":network-caching=$networkCachingMs")
        media.addOption(":live-caching=$networkCachingMs")
        media.addOption(":file-caching=$networkCachingMs")
        media.addOption(":clock-jitter=0")
        media.addOption(":clock-synchro=0")
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
    }

    override fun stop() {
        pendingPlaybackRequest = null
        mediaPlayer.stop()
    }

    override fun release() {
        pendingPlaybackRequest = null
        playbackErrorListener = null
        playbackStatusListener = null
        mediaPlayer.setEventListener(null)
        detachSurfaceViews()
        mediaPlayer.release()
        libVLC.release()
    }

    private fun notifyStatus(status: String) {
        playbackStatusListener?.invoke(status)
    }

    private fun eventName(type: Int): String = when (type) {
        MediaPlayer.Event.Opening -> "Opening"
        MediaPlayer.Event.Buffering -> "Buffering"
        MediaPlayer.Event.Playing -> "Playing"
        MediaPlayer.Event.Paused -> "Paused"
        MediaPlayer.Event.Stopped -> "Stopped"
        MediaPlayer.Event.EndReached -> "EndReached"
        MediaPlayer.Event.EncounteredError -> "EncounteredError"
        MediaPlayer.Event.TimeChanged -> "TimeChanged"
        MediaPlayer.Event.PositionChanged -> "PositionChanged"
        MediaPlayer.Event.Vout -> "Vout"
        MediaPlayer.Event.ESAdded -> "ESAdded"
        MediaPlayer.Event.ESDeleted -> "ESDeleted"
        else -> "Event($type)"
    }

    private fun String.describePlaybackOutput(): String {
        return when {
            contains("output=mpegts", ignoreCase = true) -> "mpegts"
            contains("output=m3u8", ignoreCase = true) -> "hls"
            endsWith(".ts", ignoreCase = true) -> "ts"
            endsWith(".m3u8", ignoreCase = true) -> "m3u8"
            else -> "stream"
        }
    }

    private fun String.redactPlaybackUrl(): String {
        return replace(Regex("([?&](?:password|token|api_key|key)=)[^&]+", RegexOption.IGNORE_CASE), "\$1[REDACTED]")
            .replace(Regex("(/(?:live|movie|series)/[^/]+/)[^/]+(/)", RegexOption.IGNORE_CASE), "\$1[REDACTED]\$2")
    }

    private companion object {
        private const val TAG = "QuadTV/VLC"
        private val NOISY_PROGRESS_EVENTS = setOf(
            MediaPlayer.Event.TimeChanged,
            MediaPlayer.Event.PositionChanged
        )
    }
}
