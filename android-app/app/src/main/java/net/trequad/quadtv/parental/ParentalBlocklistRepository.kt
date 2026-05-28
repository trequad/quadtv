package net.trequad.quadtv.parental

import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.ParentalBlocklistCache

class ParentalBlocklistRepository(
    private val apiService: AdminApiService,
    private val cache: ParentalBlocklistCache
) {
    suspend fun loadBlocklist(): GlobalParentalBlocklist {
        return try {
            val blocklist = apiService.getParentalBlocklist().toDomain()
            cache.save(blocklist)
            blocklist
        } catch (_: Exception) {
            cache.load() ?: GlobalParentalBlocklist.defaults()
        }
    }
}
