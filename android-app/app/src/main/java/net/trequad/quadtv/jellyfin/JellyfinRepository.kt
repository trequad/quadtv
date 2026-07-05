package net.trequad.quadtv.jellyfin

import com.squareup.moshi.Moshi
import java.net.URLEncoder
import net.trequad.quadtv.adminapi.AdminConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class JellyfinRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val accessProvider: JellyfinAccessProvider? = null
) {
    suspend fun loadLibraries(): List<JellyfinLibrary> {
        val context = loadContext() ?: return emptyList()
        val request = authorizedRequest("${context.baseUrl}/Library/MediaFolders", context.apiKey)
        return executeItemsRequest(request)?.items.orEmpty().map { it.toLibrary() }
    }

    suspend fun loadItems(libraryId: String): List<JellyfinItem> {
        val context = loadContext() ?: return emptyList()
        val request = authorizedRequest(
            "${context.baseUrl}/Items?ParentId=$libraryId&Recursive=false&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio",
            context.apiKey
        )
        return executeItemsRequest(request)?.items.orEmpty().map { it.toItem(context.baseUrl) }
    }

    suspend fun loadMovies(): List<JellyfinItem> = loadMoviesPage().items

    suspend fun loadMoviesPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Movie&SortBy=SortName&SortOrder=Ascending&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun countMoviesBeforeLetter(letter: Char): Int {
        val context = loadContext() ?: return 0
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Movie&SortBy=SortName&SortOrder=Ascending&NameLessThan=$letter&Limit=0",
            context.apiKey
        )
        return executeItemsRequest(request)?.totalRecordCount ?: 0
    }

    suspend fun countSeriesBeforeLetter(letter: Char): Int {
        val context = loadContext() ?: return 0
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Series&SortBy=SortName&SortOrder=Ascending&NameLessThan=$letter&Limit=0",
            context.apiKey
        )
        return executeItemsRequest(request)?.totalRecordCount ?: 0
    }

    /** Per-user Continue Watching (resume points). Needs a provisioned Jellyfin user. */
    suspend fun loadContinueWatching(limit: Int = 12): List<JellyfinItem> {
        val access = accessProvider?.loadAccess() ?: return emptyList()
        val userId = access.jellyfinUserId ?: return emptyList()
        val baseUrl = access.baseUrl.trimEnd('/')
        val request = authorizedRequest(
            "$baseUrl/Users/$userId/Items/Resume?Limit=$limit&Recursive=true&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio&MediaTypes=Video",
            access.apiKey
        )
        return executeItemsRequest(request)?.items.orEmpty().map { it.toItem(baseUrl) }
    }

    /** Newest additions to the library (movies and shows mixed), for the Home hub. */
    suspend fun loadRecentlyAddedPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun loadRecentlyReleasedMoviesPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Movie&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio,PremiereDate&SortBy=PremiereDate&SortOrder=Descending&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun loadSeries(): List<JellyfinItem> = loadSeriesPage().items

    suspend fun loadSeriesPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Series&SortBy=SortName&SortOrder=Ascending&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun loadRecentlyReleasedSeriesPage(startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Series&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio,PremiereDate&SortBy=PremiereDate&SortOrder=Descending&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun searchMovies(query: String): List<JellyfinItem> = searchMoviesPage(query).items

    suspend fun searchMoviesPage(query: String, startIndex: Int = 0, limit: Int = DEFAULT_PAGE_SIZE): JellyfinPage {
        val context = loadContext() ?: return emptyPage(startIndex, limit)
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        if (encodedQuery.isBlank()) return emptyPage(startIndex, limit)
        val request = authorizedRequest(
            "${context.baseUrl}/Items?Recursive=true&IncludeItemTypes=Movie&SearchTerm=$encodedQuery&Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio&StartIndex=$startIndex&Limit=$limit",
            context.apiKey
        )
        return executeItemsPage(request, context.baseUrl, startIndex, limit)
    }

    suspend fun loadSeasons(seriesId: String): List<JellyfinSeason> {
        val context = loadContext() ?: return emptyList()
        val request = authorizedRequest(
            "${context.baseUrl}/Shows/$seriesId/Seasons?Fields=Overview,IndexNumber",
            context.apiKey
        )
        return executeItemsRequest(request)?.items.orEmpty()
            .map { it.toSeason() }
            .sortedBy { it.seasonNumber }
    }

    suspend fun loadEpisodes(seriesId: String, season: JellyfinSeason): List<JellyfinEpisode> {
        val context = loadContext() ?: return emptyList()
        val request = authorizedRequest(
            "${context.baseUrl}/Items?ParentId=${season.id}&Fields=Overview,OfficialRating,IndexNumber,ParentIndexNumber,SeriesId,SeasonId",
            context.apiKey
        )
        return executeItemsRequest(request)?.items.orEmpty()
            .map { it.toEpisode(seriesId, season.id) }
            .sortedBy { it.episodeNumber }
    }

    suspend fun loadEpisodes(seriesId: String): List<JellyfinEpisode> {
        return loadSeasons(seriesId).flatMap { season -> loadEpisodes(seriesId, season) }
    }

    suspend fun buildEpisodeStream(episode: JellyfinEpisode): JellyfinStream? {
        return buildHlsStream(episode.id, episode.title)
    }

    suspend fun buildHlsStream(itemId: String, title: String): JellyfinStream? {
        val context = loadContext() ?: return null
        // MPEG-TS progressive stream: VLC handles TS containers best for HTTP streaming.
        // Direct-copy H.264 video, transcode audio to AAC for universal compatibility.
        // MaxWidth/Height tells Jellyfin the device can handle up to 720p — prevents the
        // default 416px mobile profile from kicking in for unknown clients.
        val streamUrl = "${context.baseUrl}/Videos/$itemId/stream.ts" +
            "?DeviceId=quadtv-app" +
            "&VideoCodec=h264" +
            "&AudioCodec=aac" +
            "&AllowVideoStreamCopy=true" +
            "&AllowAudioStreamCopy=false" +
            "&MaxWidth=1280" +
            "&MaxHeight=720" +
            "&api_key=${context.apiKey}"
        return JellyfinStream(itemId = itemId, title = title, hlsUrl = streamUrl)
    }

    private suspend fun loadContext(): JellyfinContext? {
        // Preferred: authenticated per-customer access from the portal. The
        // unauthenticated launch config no longer carries the API key.
        accessProvider?.loadAccess()?.let { access ->
            return JellyfinContext(baseUrl = access.baseUrl.trimEnd('/'), apiKey = access.apiKey)
        }
        val launchConfig = adminConfigRepository.loadLaunchConfig()
        val baseUrl = launchConfig.jellyfinBaseUrl?.trimEnd('/') ?: return null
        val apiKey = launchConfig.jellyfinApiKey ?: return null
        return JellyfinContext(baseUrl = baseUrl, apiKey = apiKey)
    }

    private fun authorizedRequest(url: String, apiKey: String): Request {
        return Request.Builder()
            .url(url)
            .header("X-Emby-Token", apiKey)
            .build()
    }

    private fun executeItemsRequest(request: Request): JellyfinItemsResponse? {
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            return moshi.adapter(JellyfinItemsResponse::class.java).fromJson(it.body?.string().orEmpty())
        }
    }

    private fun executeItemsPage(
        request: Request,
        baseUrl: String,
        startIndex: Int,
        limit: Int
    ): JellyfinPage {
        val response = executeItemsRequest(request) ?: return emptyPage(startIndex, limit)
        val items = response.items.map { it.toItem(baseUrl) }
        return JellyfinPage(
            items = items,
            totalCount = response.totalRecordCount ?: (startIndex + items.size),
            startIndex = response.startIndex ?: startIndex,
            limit = limit
        )
    }

    private fun emptyPage(startIndex: Int, limit: Int): JellyfinPage {
        return JellyfinPage(items = emptyList(), totalCount = 0, startIndex = startIndex, limit = limit)
    }

    private fun executeItemRequest(request: Request): JellyfinApiItem? {
        val response = okHttpClient.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) return null
            return moshi.adapter(JellyfinApiItem::class.java).fromJson(it.body?.string().orEmpty())
        }
    }

    private data class JellyfinContext(
        val baseUrl: String,
        val apiKey: String
    )

    companion object {
        const val DEFAULT_PAGE_SIZE = 60
    }
}
