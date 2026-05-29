package net.trequad.quadtv.player

import android.view.SurfaceView
import androidx.media3.ui.PlayerView

enum class PlayerEngine {
    EXOPLAYER,
    VLC
}

enum class BufferStrategy {
    ADAPTIVE,
    AGGRESSIVE_PREBUFFER
}

data class BufferConfig(
    val sizeSeconds: Int = 30,
    val strategy: BufferStrategy = BufferStrategy.ADAPTIVE
)

data class StreamPlaybackRequest(
    val url: String,
    val title: String? = null,
    val subtitle: String? = null,
    val nextTitle: String? = null,
    val isLive: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val bufferConfig: BufferConfig = BufferConfig()
)

data class PlayerRenderSurface(
    val playerView: PlayerView,
    val vlcSurfaceView: SurfaceView
)

interface QuadTvPlayer {
    fun attachSurface(surface: PlayerRenderSurface)
    fun play(request: StreamPlaybackRequest)
    fun stop()
    fun release()
}
