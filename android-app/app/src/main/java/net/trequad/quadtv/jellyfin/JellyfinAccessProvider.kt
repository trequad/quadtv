package net.trequad.quadtv.jellyfin

import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.JellyfinAccessDto
import net.trequad.quadtv.core.cache.CustomerSessionCache

/**
 * Fetches QuadOnDemand (Jellyfin) access for the signed-in customer from the
 * portal. The server URL/API key are no longer part of the unauthenticated
 * launch config (PRODUCT_AUDIT.md S2); only entitled, active customers get them.
 */
class JellyfinAccessProvider(
    private val apiService: AdminApiService,
    private val sessionCache: CustomerSessionCache
) {
    @Volatile
    private var cached: JellyfinAccessDto? = null

    suspend fun loadAccess(): JellyfinAccessDto? {
        cached?.let { return it }
        val session = sessionCache.load() ?: return null
        // Preferred: the per-user Jellyfin token issued at login. The customer's
        // own token scopes watch state and keeps the admin key off the device.
        if (!session.jellyfinBaseUrl.isNullOrBlank() && !session.jellyfinAccessToken.isNullOrBlank()) {
            return JellyfinAccessDto(
                baseUrl = session.jellyfinBaseUrl,
                apiKey = session.jellyfinAccessToken,
                jellyfinUsername = session.providerUsername,
                jellyfinUserId = session.jellyfinUserId
            ).also { cached = it }
        }
        return try {
            apiService.getJellyfinAccess("Bearer ${session.accessToken}").also { cached = it }
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        cached = null
    }
}
