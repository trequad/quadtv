package net.trequad.quadtv.live

import net.trequad.quadtv.adminapi.AdminConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class LiveTvRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val parser: M3uParser = M3uParser()
) {
    suspend fun loadChannels(): List<LiveChannel> {
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val request = Request.Builder().url(launchConfig.liveTvEndpoint).build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            return parser.parse(it.body?.string().orEmpty())
        }
    }
}
