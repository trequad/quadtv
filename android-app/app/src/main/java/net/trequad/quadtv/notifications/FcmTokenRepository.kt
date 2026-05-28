package net.trequad.quadtv.notifications

import net.trequad.quadtv.adminapi.AdminApiService

class FcmTokenRepository(
    private val apiService: AdminApiService
) {
    suspend fun registerToken(deviceId: Int, token: String): FcmTokenRegistrationResponse {
        return apiService.registerFcmToken(
            deviceId = deviceId,
            request = FcmTokenRegistrationRequest(token = token)
        )
    }
}
