package net.trequad.quadtv.provider

import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.live.LiveTvRepository

data class ProviderFeedRefreshResult(
    val playlistChannelCount: Int,
    val guideProgrammeCount: Int,
    val refreshedAtMillis: Long
)

class ProviderFeedRefreshCoordinator(
    private val liveTvRepository: LiveTvRepository,
    private val epgRepository: EpgRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun refreshPlaylistAndGuide(): ProviderFeedRefreshResult {
        val channels = liveTvRepository.loadChannels(forceRefresh = true)
        val programmes = epgRepository.loadProgrammes(forceRefresh = true)
        return ProviderFeedRefreshResult(
            playlistChannelCount = channels.size,
            guideProgrammeCount = programmes.size,
            refreshedAtMillis = clock()
        )
    }

    fun shouldAutoRefresh(lastRefreshAtMillis: Long?, nowMillis: Long = clock()): Boolean {
        return lastRefreshAtMillis == null ||
            nowMillis - lastRefreshAtMillis >= ProviderFeedPolicy.REFRESH_INTERVAL_MILLIS
    }
}
