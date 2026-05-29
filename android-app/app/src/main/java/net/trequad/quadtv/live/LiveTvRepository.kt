package net.trequad.quadtv.live

import net.trequad.quadtv.provider.ProviderFeedRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class LiveTvRepository(
    private val providerFeedRepository: ProviderFeedRepository,
    private val okHttpClient: OkHttpClient,
    private val parser: M3uParser = M3uParser()
) {
    suspend fun loadChannels(): List<LiveChannel> {
        val feed = providerFeedRepository.loadOrRefreshLiveTvFeed() ?: return emptyList()
        val request = Request.Builder().url(feed.liveTvPlaylistUrl).build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            return parser.parse(it.body?.string().orEmpty())
        }
    }
}
