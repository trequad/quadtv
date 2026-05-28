package net.trequad.quadtv.vod

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import net.trequad.quadtv.adminapi.AdminConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class VodRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {
    suspend fun loadCategories(): List<VodCategory> {
        return getList("categories", VodCategory::class.java)
    }

    suspend fun loadRecentlyAdded(): List<VodItem> {
        return getList("recently-added", VodItem::class.java)
    }

    suspend fun loadItems(categoryId: String): List<VodItem> {
        return getList("categories/$categoryId/items", VodItem::class.java)
    }

    suspend fun loadEpisodes(seriesId: String): List<VodEpisode> {
        return getList("series/$seriesId/episodes", VodEpisode::class.java)
    }

    private suspend fun <T> getList(path: String, itemType: Class<T>): List<T> {
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val baseUrl = launchConfig.vodEndpoint.trimEnd('/')
        val request = Request.Builder().url("$baseUrl/$path").build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            val type = Types.newParameterizedType(List::class.java, itemType)
            val adapter = moshi.adapter<List<T>>(type)
            return adapter.fromJson(it.body?.string().orEmpty()).orEmpty()
        }
    }
}
