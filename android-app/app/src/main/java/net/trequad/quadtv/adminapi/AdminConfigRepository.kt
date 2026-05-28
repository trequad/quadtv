package net.trequad.quadtv.adminapi

import net.trequad.quadtv.core.cache.LaunchConfigCache

class AdminConfigRepository(
    private val apiService: AdminApiService,
    private val cache: LaunchConfigCache
) {
    suspend fun loadLaunchConfig(): LaunchConfig {
        return try {
            val config = apiService.getLaunchConfig().toDomain()
            cache.save(config)
            config
        } catch (_: Exception) {
            cache.load() ?: LaunchConfig.defaults()
        }
    }
}
