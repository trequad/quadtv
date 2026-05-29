package net.trequad.quadtv.provider

import java.net.URLEncoder
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.CustomerSessionCache

class ProviderFeedRepository(
    private val adminConfigRepository: AdminConfigRepository,
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
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val providerBaseUrl = launchConfig.liveTvProviderBaseUrl.trimEnd('/')
        val feed = LiveTvProviderFeed(
            liveTvPlaylistUrl = deriveLiveTvPlaylistUrl(providerBaseUrl, session.providerUsername, session.accessToken),
            xmltvUrl = deriveXmltvUrl(providerBaseUrl, session.providerUsername, session.accessToken),
            fetchedAtMillis = currentTimeMillis
        )
        this.cached = feed
        return feed
    }

    fun deriveLiveTvPlaylistUrl(
        providerBaseUrl: String,
        providerUsername: String,
        sessionToken: String
    ): String {
        return "$providerBaseUrl/get.php?username=${providerUsername.urlEncode()}&token=${sessionToken.urlEncode()}&type=m3u_plus&output=m3u8"
    }

    fun deriveXmltvUrl(
        providerBaseUrl: String,
        providerUsername: String,
        sessionToken: String
    ): String {
        return "$providerBaseUrl/xmltv.php?username=${providerUsername.urlEncode()}&token=${sessionToken.urlEncode()}"
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
}
