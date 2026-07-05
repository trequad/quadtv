package net.trequad.quadtv.player

import org.videolan.libvlc.util.VLCVideoLayout

enum class PlayerEngine {
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
    val channelId: String? = null,
    val title: String? = null,
    val groupTitle: String? = null,
    val contentTitle: String? = null,
    val subtitle: String? = null,
    val nextTitle: String? = null,
    val isLive: Boolean = false,
    val channelUpUrl: String? = null,
    val channelUpTitle: String? = null,
    val channelDownUrl: String? = null,
    val channelDownTitle: String? = null,
    val liveChannelIds: List<String> = emptyList(),
    val liveChannelUrls: List<String> = emptyList(),
    val liveChannelTitles: List<String> = emptyList(),
    val liveChannelGroupTitles: List<String> = emptyList(),
    val liveChannelContentTitles: List<String> = emptyList(),
    val liveChannelIndex: Int = -1,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val bufferConfig: BufferConfig = BufferConfig()
)

data class PlayerRenderSurface(
    val vlcVideoLayout: VLCVideoLayout
)

interface QuadTvPlayer {
    fun attachSurface(surface: PlayerRenderSurface)
    fun setPlaybackErrorListener(listener: ((Throwable) -> Unit)?) {}
    fun setPlaybackStatusListener(listener: ((String) -> Unit)?) {}
    fun play(request: StreamPlaybackRequest)
    fun pause() {}
    fun resume() {}
    fun seekBy(milliseconds: Long) {}
    fun stop()
    fun release()
}
