package net.trequad.quadtv.epg

import net.trequad.quadtv.adminapi.AdminConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class EpgRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val parser: XmlTvParser = XmlTvParser()
) {
    suspend fun loadProgrammes(): List<EpgProgramme> {
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val request = Request.Builder().url(launchConfig.xmltvEndpoint).build()
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
