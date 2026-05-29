package net.trequad.quadtv.live

import net.trequad.quadtv.player.StreamPlaybackRequest

class LiveTvPlaybackCoordinator {
    fun buildRequest(channel: LiveChannel): StreamPlaybackRequest {
        return StreamPlaybackRequest(
            url = channel.streamUrl,
            title = channel.name,
            isLive = true
        )
    }

    fun describeFallback(channel: LiveChannel): String {
        return "If ${channel.name} fails in the selected engine, retry with the alternate bundled player."
    }
}
