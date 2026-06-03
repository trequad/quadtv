package net.trequad.quadtv.vod

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.URLEncoder
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.provider.ProviderFeedRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

class VodRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val providerFeedRepository: ProviderFeedRepository? = null
) {
    suspend fun loadCategories(): List<VodCategory> {
        val context = loadXtreamContext() ?: return getLegacyList("categories", VodCategory::class.java)
        val url = context.playerApiUrl("get_vod_categories")
        return executeList(url, XtreamVodCategoryDto::class.java)
            .map { VodCategory(id = it.categoryId, name = it.categoryName) }
    }

    suspend fun loadRecentlyAdded(): List<VodItem> {
        return loadXtreamItems(categoryId = null)
            .sortedByDescending { it.addedTimestamp ?: 0L }
            .take(100)
            .map { it.toVodItem() }
    }

    suspend fun loadItems(categoryId: String): List<VodItem> {
        val context = loadXtreamContext() ?: return getLegacyList("categories/$categoryId/items", VodItem::class.java)
        return loadXtreamItems(context, categoryId).map { it.toVodItem() }
    }

    suspend fun searchMovies(query: String): List<VodItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()
        val context = loadXtreamContext()
        if (context != null) {
            return loadXtreamItems(context, categoryId = null)
                .map { it.toVodItem() }
                .filter { item -> item.title.lowercase().contains(normalized) }
                .distinctBy { it.id }
                .sortedBy { it.title }
                .take(100)
        }
        val fromRecentlyAdded = loadRecentlyAdded()
        val fromCategories = loadCategories().flatMap { category -> loadItems(category.id) }
        return (fromRecentlyAdded + fromCategories)
            .distinctBy { it.id }
            .filter { item -> item.title.lowercase().contains(normalized) }
            .sortedBy { it.title }
    }

    suspend fun loadSeries(): List<VodItem> {
        val context = loadXtreamContext() ?: return emptyList()
        return executeList(context.playerApiUrl("get_series"), XtreamSeriesDto::class.java)
            .sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }
            .take(100)
            .map { it.toVodItem() }
    }

    suspend fun loadEpisodes(seriesId: String): List<VodEpisode> {
        return getLegacyList("series/$seriesId/episodes", VodEpisode::class.java)
    }

    private suspend fun loadXtreamItems(categoryId: String?): List<XtreamVodStreamDto> {
        val context = loadXtreamContext() ?: return emptyList()
        return loadXtreamItems(context, categoryId)
    }

    private fun loadXtreamItems(context: XtreamVodContext, categoryId: String?): List<XtreamVodStreamDto> {
        val extraParams = categoryId?.let { mapOf("category_id" to it) }.orEmpty()
        return executeList(context.playerApiUrl("get_vod_streams", extraParams), XtreamVodStreamDto::class.java)
            .map { dto -> dto.copy(playbackUrl = context.moviePlaybackUrl(dto.streamId, dto.containerExtension)) }
    }

    private suspend fun loadXtreamContext(): XtreamVodContext? {
        val providerFeed = providerFeedRepository?.loadOrRefreshLiveTvFeed() ?: return null
        val credentials = parseCredentials(providerFeed.liveTvPlaylistUrl) ?: return null
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val baseUrl = launchConfig.vodProviderBaseUrl.trimEnd('/')
        return XtreamVodContext(baseUrl, credentials.username, credentials.password)
    }

    private fun parseCredentials(url: String): XtreamCredentials? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val username = httpUrl.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
        val password = httpUrl.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
        return XtreamCredentials(username = username, password = password)
    }

    private fun <T> executeList(url: String, itemType: Class<T>): List<T> {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return emptyList()
            val type = Types.newParameterizedType(List::class.java, itemType)
            val adapter = moshi.adapter<List<T>>(type)
            return adapter.fromJson(it.body?.string().orEmpty()).orEmpty()
        }
    }

    private suspend fun <T> getLegacyList(path: String, itemType: Class<T>): List<T> {
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val baseUrl = launchConfig.vodProviderBaseUrl.trimEnd('/')
        return executeList("$baseUrl/$path", itemType)
    }
}

private data class XtreamCredentials(
    val username: String,
    val password: String
)

private data class XtreamVodContext(
    val baseUrl: String,
    val username: String,
    val password: String
) {
    fun playerApiUrl(action: String, extraParams: Map<String, String> = emptyMap()): String {
        val base = "$baseUrl/player_api.php?username=${username.encodeQuery()}&password=${password.encodeQuery()}&action=${action.encodeQuery()}"
        return extraParams.entries.fold(base) { url, entry ->
            "$url&${entry.key.encodeQuery()}=${entry.value.encodeQuery()}"
        }
    }

    fun moviePlaybackUrl(streamId: Int, extension: String?): String {
        val safeExtension = extension?.takeIf { it.isNotBlank() } ?: "mp4"
        return "$baseUrl/movie/${username.encodePath()}/${password.encodePath()}/$streamId.$safeExtension"
    }
}

private data class XtreamSeriesDto(
    @Json(name = "series_id") val seriesId: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "rating") val rating: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "last_modified") val lastModified: String? = null
) {
    fun toVodItem() = VodItem(
        id = seriesId.orEmpty(),
        title = name.orEmpty(),
        posterUrl = cover?.takeIf { it.isNotBlank() },
        description = plot,
        rating = rating,
        releaseYear = releaseDate?.take(4)?.toIntOrNull(),
        streamUrl = null,
        isSeries = true,
        isMature = false
    )
}

private data class XtreamVodCategoryDto(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String
)

private data class XtreamVodStreamDto(
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "name") val name: String,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "rating") val rating: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "added") val added: String? = null,
    @Json(name = "direct_source") val directSource: String? = null,
    val playbackUrl: String? = null
) {
    val addedTimestamp: Long?
        get() = added?.toLongOrNull()

    fun toVodItem(): VodItem {
        val year = Regex("\\((\\d{4})\\)").find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return VodItem(
            id = streamId.toString(),
            title = name,
            posterUrl = streamIcon,
            rating = rating,
            releaseYear = year,
            streamUrl = directSource?.takeIf { it.isNotBlank() } ?: playbackUrl,
            isSeries = false,
            isMature = false
        )
    }
}

private fun String.encodeQuery(): String = URLEncoder.encode(this, "UTF-8")
private fun String.encodePath(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
