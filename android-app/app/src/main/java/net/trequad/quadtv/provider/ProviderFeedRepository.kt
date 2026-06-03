package net.trequad.quadtv.provider

import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache

class ProviderFeedRepository(
    private val adminApiService: AdminApiService,
    private val customerSessionCache: CustomerSessionCache,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var cached: LiveTvProviderFeed? = null

    suspend fun loadOrRefreshLiveTvFeed(forceRefresh: Boolean = false): LiveTvProviderFeed? {
        val currentTimeMillis = clock()
        val cached = cached
        if (!forceRefresh && cached != null &&
            currentTimeMillis - cached.fetchedAtMillis < ProviderFeedPolicy.REFRESH_INTERVAL_MILLIS
        ) {
            return cached
        }

        val session = customerSessionCache.load() ?: return null
        val response = adminApiService.getLiveTvProviderFeed("Bearer ${session.accessToken}")
        val feed = LiveTvProviderFeed(
            liveTvPlaylistUrl = response.liveTvPlaylistUrl,
            xmltvUrl = response.xmltvUrl,
            fetchedAtMillis = currentTimeMillis
        )
        this.cached = feed
        return feed
    }
}
