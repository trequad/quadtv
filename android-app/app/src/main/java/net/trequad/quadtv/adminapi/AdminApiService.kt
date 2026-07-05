package net.trequad.quadtv.adminapi

import com.squareup.moshi.Json
import net.trequad.quadtv.auth.CustomerLoginRequest
import net.trequad.quadtv.auth.CustomerLoginResponse
import net.trequad.quadtv.notifications.FcmTokenRegistrationRequest
import net.trequad.quadtv.notifications.FcmTokenRegistrationResponse
import net.trequad.quadtv.parental.ParentalBlocklistDto
import net.trequad.quadtv.profiles.ProfileListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import net.trequad.quadtv.profiles.ProfileCreateRequest
import net.trequad.quadtv.profiles.QuadTvProfile

interface AdminApiService {
    @GET("api/v1/app/config")
    suspend fun getLaunchConfig(): LaunchConfigDto

    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: DeviceRegistrationRequest): DeviceRegistrationResponse

    @POST("api/v1/auth/customer-login")
    suspend fun customerLogin(@Body request: CustomerLoginRequest): CustomerLoginResponse

    @GET("api/v1/provider-feeds/live-tv")
    suspend fun getLiveTvProviderFeed(@Header("Authorization") authorization: String): ProviderFeedDto

    @GET("api/v1/devices/{deviceId}/profiles")
    suspend fun getDeviceProfiles(@Path("deviceId") deviceId: Int): ProfileListResponse

    @POST("api/v1/devices/{deviceId}/profiles")
    suspend fun createProfile(
        @Path("deviceId") deviceId: Int,
        @Body request: ProfileCreateRequest
    ): QuadTvProfile

    @GET("api/v1/parental/blocklist")
    suspend fun getParentalBlocklist(): ParentalBlocklistDto

    @POST("api/v1/devices/{deviceId}/fcm-token")
    suspend fun registerFcmToken(
        @Path("deviceId") deviceId: Int,
        @Body request: FcmTokenRegistrationRequest
    ): FcmTokenRegistrationResponse

    @POST("api/v1/seerr/session")
    suspend fun createSeerrSession(@Header("Authorization") authorization: String): SeerrSessionDto

    @GET("api/v1/jellyfin/access")
    suspend fun getJellyfinAccess(@Header("Authorization") authorization: String): JellyfinAccessDto
}

data class SeerrSessionDto(
    @Json(name = "base_url") val baseUrl: String,
    @Json(name = "session_cookie") val sessionCookie: String
)

data class JellyfinAccessDto(
    @Json(name = "base_url") val baseUrl: String,
    @Json(name = "api_key") val apiKey: String,
    @Json(name = "jellyfin_username") val jellyfinUsername: String?,
    @Json(name = "jellyfin_user_id") val jellyfinUserId: String?
)

data class ProviderFeedDto(
    @Json(name = "live_tv_playlist_url") val liveTvPlaylistUrl: String,
    @Json(name = "xmltv_url") val xmltvUrl: String,
    @Json(name = "provider_username") val providerUsername: String,
    @Json(name = "refresh_hours") val refreshHours: Int
)

data class LaunchConfigDto(
    @Json(name = "live_tv_provider_base_url") val liveTvProviderBaseUrl: String,
    @Json(name = "vod_provider_base_url") val vodProviderBaseUrl: String,
    @Json(name = "jellyfin_base_url") val jellyfinBaseUrl: String?,
    @Json(name = "jellyfin_api_key") val jellyfinApiKey: String?,
    @Json(name = "max_profiles_per_device") val maxProfilesPerDevice: Int,
    @Json(name = "warning_threshold_days") val warningThresholdDays: List<Int>,
    @Json(name = "live_stream_limit_per_user") val liveStreamLimitPerUser: Int,
    @Json(name = "vod_stream_limit_per_user") val vodStreamLimitPerUser: Int,
    @Json(name = "jellyfin_stream_limit_per_user") val jellyfinStreamLimitPerUser: Int,
    @Json(name = "provider_feed_refresh_hours") val providerFeedRefreshHours: Int
) {
    fun toDomain() = LaunchConfig(
        liveTvProviderBaseUrl = liveTvProviderBaseUrl,
        vodProviderBaseUrl = vodProviderBaseUrl,
        jellyfinBaseUrl = jellyfinBaseUrl,
        jellyfinApiKey = jellyfinApiKey,
        maxProfilesPerDevice = maxProfilesPerDevice,
        warningThresholdDays = warningThresholdDays,
        liveStreamLimitPerUser = liveStreamLimitPerUser,
        vodStreamLimitPerUser = vodStreamLimitPerUser,
        jellyfinStreamLimitPerUser = jellyfinStreamLimitPerUser,
        providerFeedRefreshHours = providerFeedRefreshHours
    )
}
