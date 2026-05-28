package net.trequad.quadtv.adminapi

import com.squareup.moshi.Json
import net.trequad.quadtv.auth.CustomerLoginRequest
import net.trequad.quadtv.auth.CustomerLoginResponse
import net.trequad.quadtv.parental.ParentalBlocklistDto
import net.trequad.quadtv.profiles.ProfileListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AdminApiService {
    @GET("api/v1/app/config")
    suspend fun getLaunchConfig(): LaunchConfigDto

    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): DeviceRegistrationResponse

    @POST("api/v1/auth/customer-login")
    suspend fun customerLogin(@Body request: CustomerLoginRequest): CustomerLoginResponse

    @GET("api/v1/devices/{deviceId}/profiles")
    suspend fun getDeviceProfiles(@Path("deviceId") deviceId: Int): ProfileListResponse

    @GET("api/v1/parental/blocklist")
    suspend fun getParentalBlocklist(): ParentalBlocklistDto
}

data class LaunchConfigDto(
    @Json(name = "live_tv_endpoint") val liveTvEndpoint: String,
    @Json(name = "xmltv_endpoint") val xmltvEndpoint: String,
    @Json(name = "vod_endpoint") val vodEndpoint: String,
    @Json(name = "jellyfin_base_url") val jellyfinBaseUrl: String?,
    @Json(name = "jellyfin_api_key") val jellyfinApiKey: String?,
    @Json(name = "max_profiles_per_device") val maxProfilesPerDevice: Int,
    @Json(name = "warning_threshold_days") val warningThresholdDays: List<Int>,
    @Json(name = "live_stream_limit_per_user") val liveStreamLimitPerUser: Int,
    @Json(name = "vod_stream_limit_per_user") val vodStreamLimitPerUser: Int,
    @Json(name = "jellyfin_stream_limit_per_user") val jellyfinStreamLimitPerUser: Int
) {
    fun toDomain() = LaunchConfig(
        liveTvEndpoint = liveTvEndpoint,
        xmltvEndpoint = xmltvEndpoint,
        vodEndpoint = vodEndpoint,
        jellyfinBaseUrl = jellyfinBaseUrl,
        jellyfinApiKey = jellyfinApiKey,
        maxProfilesPerDevice = maxProfilesPerDevice,
        warningThresholdDays = warningThresholdDays,
        liveStreamLimitPerUser = liveStreamLimitPerUser,
        vodStreamLimitPerUser = vodStreamLimitPerUser,
        jellyfinStreamLimitPerUser = jellyfinStreamLimitPerUser
    )
}
