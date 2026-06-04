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

    suspend fun loadRecentlyAdded(): List<VodItem> = loadRecentlyAddedPage().items

    suspend fun loadRecentlyAddedPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): VodPage {
        return pageFromItems(
            items = loadXtreamItems(categoryId = null)
                .sortedByDescending { it.addedTimestamp ?: 0L }
                .map { it.toVodItem() },
            startIndex = startIndex,
            limit = limit
        )
    }

    suspend fun loadItems(categoryId: String): List<VodItem> = loadItemsPage(categoryId).items

    suspend fun loadItemsPage(categoryId: String, startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): VodPage {
        val context = loadXtreamContext()
        val items = if (context != null) {
            loadXtreamItems(context, categoryId).map { it.toVodItem() }
        } else {
            getLegacyList("categories/$categoryId/items", VodItem::class.java)
        }
        return pageFromItems(items, startIndex, limit)
    }

    suspend fun searchMovies(query: String): List<VodItem> = searchMoviesPage(query).items

    suspend fun searchMoviesPage(query: String, startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): VodPage {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyPage(startIndex, limit)
        val context = loadXtreamContext()
        val items = if (context != null) {
            loadXtreamItems(context, categoryId = null)
                .map { it.toVodItem() }
        } else {
            val fromRecentlyAdded = loadRecentlyAddedPage(limit = Int.MAX_VALUE).items
            val fromCategories = loadCategories().flatMap { category -> loadItemsPage(category.id, limit = Int.MAX_VALUE).items }
            fromRecentlyAdded + fromCategories
        }
        return pageFromItems(
            items = items
                .distinctBy { it.id }
                .filter { item -> item.title.lowercase().contains(normalized) }
                .sortedBy { it.title },
            startIndex = startIndex,
            limit = limit
        )
    }

    suspend fun loadSeries(): List<VodItem> = loadSeriesPage().items

    suspend fun loadSeriesPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): VodPage {
        val context = loadXtreamContext() ?: return emptyPage(startIndex, limit)
        val items = executeList(context.playerApiUrl("get_series"), XtreamSeriesDto::class.java)
            .sortedByDescending { it.lastModified?.toLongOrNull() ?: 0L }
            .map { it.toVodItem() }
        return pageFromItems(items, startIndex, limit)
    }

    suspend fun loadEpisodes(seriesId: String): List<VodEpisode> {
        val context = loadXtreamContext()
        if (context != null) {
            val request = Request.Builder()
                .url(context.playerApiUrl("get_series_info", mapOf("series_id" to seriesId)))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val info = moshi.adapter(XtreamSeriesInfoResponse::class.java).fromJson(response.body?.string().orEmpty())
                    ?: return emptyList()
                return info.episodesBySeason
                    .flatMap { entry ->
                        val seasonNumber = entry.key.toIntOrNull() ?: 0
                        entry.value.map { dto -> dto.toVodEpisode(seriesId, seasonNumber, context) }
                    }
                    .sortedWith(compareBy<VodEpisode> { it.seasonNumber }.thenBy { it.episodeNumber })
            }
        }
        return getLegacyList("series/$seriesId/episodes", VodEpisode::class.java)
    }

    suspend fun loadSeasons(seriesId: String): List<VodSeason> {
        return loadEpisodes(seriesId)
            .groupBy { it.seasonNumber }
            .toSortedMap()
            .map { (seasonNumber, episodes) -> VodSeason(seasonNumber = seasonNumber, episodes = episodes.sortedBy { it.episodeNumber }) }
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

    private fun pageFromItems(items: List<VodItem>, startIndex: Int, limit: Int): VodPage {
        val safeStart = startIndex.coerceAtLeast(0)
        val safeLimit = limit.coerceAtLeast(1)
        return VodPage(
            items = items.drop(safeStart).take(safeLimit),
            totalCount = items.size,
            startIndex = safeStart,
            limit = safeLimit
        )
    }

    private fun emptyPage(startIndex: Int, limit: Int): VodPage {
        return VodPage(items = emptyList(), totalCount = 0, startIndex = startIndex, limit = limit)
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

    companion object {
        const val DEFAULT_PAGE_SIZE = 100
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

    fun seriesPlaybackUrl(episodeId: String, extension: String?): String {
        val safeExtension = extension?.takeIf { it.isNotBlank() } ?: "mp4"
        return "$baseUrl/series/${username.encodePath()}/${password.encodePath()}/$episodeId.$safeExtension"
    }
}

private data class XtreamSeriesInfoResponse(
    @Json(name = "episodes") val episodesBySeason: Map<String, List<XtreamEpisodeDto>> = emptyMap()
)

private data class XtreamEpisodeDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "episode_num") val episodeNum: Int? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "info") val info: XtreamEpisodeInfoDto? = null
) {
    fun toVodEpisode(seriesId: String, seasonNumber: Int, context: XtreamVodContext): VodEpisode {
        val episodeId = id.orEmpty()
        return VodEpisode(
            id = episodeId,
            seriesId = seriesId,
            title = title?.takeIf { it.isNotBlank() } ?: "Episode ${episodeNum ?: 0}",
            seasonNumber = seasonNumber,
            episodeNumber = episodeNum ?: 0,
            streamUrl = context.seriesPlaybackUrl(episodeId, containerExtension),
            description = info?.plot,
            rating = info?.rating,
            isMature = false
        )
    }
}

private data class XtreamEpisodeInfoDto(
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "rating") val rating: String? = null
)

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
