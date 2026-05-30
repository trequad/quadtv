package net.trequad.quadtv.epg

import net.trequad.quadtv.provider.ProviderFeedRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class EpgRepository(
    private val providerFeedRepository: ProviderFeedRepository,
    private val okHttpClient: OkHttpClient,
    private val parser: XmlTvParser = XmlTvParser()
) {
    suspend fun loadProgrammes(): List<EpgProgramme> {
        val feed = providerFeedRepository.loadOrRefreshLiveTvFeed() ?: return emptyList()
        return fetchProgrammes(feed.xmltvUrl)
    }

    suspend fun loadProgrammes(forceRefresh: Boolean): List<EpgProgramme> {
        val feed = providerFeedRepository.loadOrRefreshLiveTvFeed(forceRefresh = forceRefresh) ?: return emptyList()
        return fetchProgrammes(feed.xmltvUrl)
    }

    private fun fetchProgrammes(xmltvUrl: String): List<EpgProgramme> {
        val request = Request.Builder().url(xmltvUrl).build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            return parser.parse(it.body?.string().orEmpty())
        }
    }

    fun programmesForChannel(programmes: List<EpgProgramme>, channelId: String): List<EpgProgramme> {
        return programmes
            .filter { it.channelId == channelId }
            .sortedBy { it.startTimeMillis }
    }
}
