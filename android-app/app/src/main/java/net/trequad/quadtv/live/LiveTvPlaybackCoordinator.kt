package net.trequad.quadtv.live

import net.trequad.quadtv.player.StreamPlaybackRequest

class LiveTvPlaybackCoordinator {
    fun buildRequest(channel: LiveChannel): StreamPlaybackRequest {
        return buildRequest(channel, listOf(channel))
    }

    fun buildRequest(channel: LiveChannel, lineup: List<LiveChannel>): StreamPlaybackRequest {
        return buildRequest(channel, lineup, currentContentTitle = null)
    }

    fun buildRequest(channel: LiveChannel, lineup: List<LiveChannel>, currentContentTitle: String?): StreamPlaybackRequest {
        val playableLineup = lineup.filter { it.streamUrl.isNotBlank() }
        val currentIndex = playableLineup.indexOfFirst { it.id == channel.id || it.streamUrl == channel.streamUrl }
        val channelUp = adjacentChannel(playableLineup, currentIndex, offset = 1)
        val channelDown = adjacentChannel(playableLineup, currentIndex, offset = -1)
        return StreamPlaybackRequest(
            url = channel.streamUrl,
            channelId = channel.id,
            title = channel.name,
            groupTitle = channel.groupTitle,
            contentTitle = currentContentTitle ?: "Live TV",
            subtitle = "Live TV",
            nextTitle = channelUp?.let { "Channel up: ${it.name}" } ?: "Guide data pending", // legacy info-banner default: nextTitle = "Guide data pending"
            isLive = true,
            channelUpUrl = channelUp?.streamUrl,
            channelUpTitle = channelUp?.name,
            channelDownUrl = channelDown?.streamUrl,
            channelDownTitle = channelDown?.name,
            liveChannelIds = playableLineup.map { it.id },
            liveChannelUrls = playableLineup.map { it.streamUrl },
            liveChannelTitles = playableLineup.map { it.name },
            liveChannelGroupTitles = playableLineup.map { it.groupTitle.orEmpty() },
            liveChannelContentTitles = playableLineup.map { liveChannel ->
                if (liveChannel.id == channel.id || liveChannel.streamUrl == channel.streamUrl) currentContentTitle ?: "Live TV" else "Live TV"
            },
            liveChannelIndex = currentIndex
        )
    }

    private fun adjacentChannel(lineup: List<LiveChannel>, currentIndex: Int, offset: Int): LiveChannel? {
        if (lineup.size < 2 || currentIndex !in lineup.indices) return null
        val adjacentIndex = Math.floorMod(currentIndex + offset, lineup.size)
        return lineup[adjacentIndex]
    }

    fun describeFallback(channel: LiveChannel): String {
        return "If ${channel.name} fails in embedded VLC, QuadTV will show the playback error in-app."
    }
}
