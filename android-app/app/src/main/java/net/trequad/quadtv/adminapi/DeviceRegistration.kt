package net.trequad.quadtv.adminapi

import com.squareup.moshi.Json

data class DeviceRegistrationRequest(
    @Json(name = "device_identifier") val deviceIdentifier: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "app_version") val appVersion: String?
)

data class DeviceRegistrationResponse(
    val id: Int,
    @Json(name = "user_id") val userId: Int?,
    @Json(name = "device_identifier") val deviceIdentifier: String,
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "app_version") val appVersion: String?,
    val active: Boolean,
    val expired: Boolean,
    @Json(name = "expires_on") val expiresOn: String?,
    @Json(name = "max_profiles_per_device") val maxProfilesPerDevice: Int,
    @Json(name = "live_stream_limit_per_user") val liveStreamLimitPerUser: Int,
    @Json(name = "vod_stream_limit_per_user") val vodStreamLimitPerUser: Int,
    @Json(name = "jellyfin_stream_limit_per_user") val jellyfinStreamLimitPerUser: Int
)
