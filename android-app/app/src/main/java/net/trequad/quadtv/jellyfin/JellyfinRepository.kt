package net.trequad.quadtv.jellyfin

import com.squareup.moshi.Moshi
import net.trequad.quadtv.adminapi.AdminConfigRepository
import okhttp3.OkHttpClient
import okhttp3.Request

class JellyfinRepository(
    private val adminConfigRepository: AdminConfigRepository,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
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

    suspend fun buildHlsStream(itemId: String): JellyfinStream? {
        val context = loadContext() ?: return null
        val request = authorizedRequest("${context.baseUrl}/Items/$itemId", context.apiKey)
        val item = executeItemRequest(request) ?: return null
        val hlsUrl = "${context.baseUrl}/Videos/$itemId/master.m3u8?api_key=${context.apiKey}"
        return JellyfinStream(itemId = itemId, title = item.name, hlsUrl = hlsUrl)
    }

    private suspend fun loadContext(): JellyfinContext? {
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
}
