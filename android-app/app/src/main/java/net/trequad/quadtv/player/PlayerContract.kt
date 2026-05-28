package net.trequad.quadtv.player

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
    val isLive: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
    val bufferConfig: BufferConfig = BufferConfig()
)

interface QuadTvPlayer {
    fun play(request: StreamPlaybackRequest)
    fun stop()
    fun release()
}
