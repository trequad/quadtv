package net.trequad.quadtv.seerr

import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.SeerrSessionDto
import net.trequad.quadtv.core.cache.CustomerSessionCache

/**
 * Portal-mediated Requests (Seerr) session. The portal signs in server-side with
 * operator credentials and returns only a scoped session cookie, so no Seerr
 * URL or login ever ships inside the app.
 */
class SeerrSessionRepository(
    private val apiService: AdminApiService,
    private val sessionCache: CustomerSessionCache
) {
    suspend fun createSession(): SeerrSessionDto? {
        val session = sessionCache.load() ?: return null
        return try {
            apiService.createSeerrSession("Bearer ${session.accessToken}")
        } catch (_: Exception) {
            null
        }
    }
}
