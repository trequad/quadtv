package net.trequad.quadtv.adminapi

import net.trequad.quadtv.core.device.AppVersionProvider
import net.trequad.quadtv.core.device.DeviceIdentifierProvider

class DeviceRegistrationRepository(
    private val apiService: AdminApiService,
    private val deviceIdentifierProvider: DeviceIdentifierProvider,
    private val appVersionProvider: AppVersionProvider
) {
    suspend fun registerThisDevice(deviceName: String): DeviceRegistrationResponse {
        return apiService.registerDevice(
            DeviceRegistrationRequest(
                deviceIdentifier = deviceIdentifierProvider.getOrCreateDeviceIdentifier(),
                deviceName = deviceName,
                appVersion = appVersionProvider.versionName
            )
        )
    }
}
