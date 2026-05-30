package net.trequad.quadtv.updates

import net.trequad.quadtv.BuildConfig
import net.trequad.quadtv.adminapi.AdminApiService

class AppUpdateRepository(
    private val apiService: AdminApiService
) {
    suspend fun loadUpdateStatus(): UpdateStatus {
        return try {
            apiService.getCurrentReleaseStatus(BuildConfig.VERSION_CODE).toDomain()
        } catch (_: Exception) {
            UpdateStatus(
                updateAvailable = false,
                forcedUpdateRequired = false,
                release = null
            )
        }
    }
}
