package net.trequad.quadtv.adminapi

import com.squareup.moshi.Json
import retrofit2.http.GET

interface AdminApiService {
    @GET("api/v1/app/config")
    suspend fun getLaunchConfig(): LaunchConfigDto
}

data class LaunchConfigDto(
    @Json(name = "live_tv_endpoint") val liveTvEndpoint: String,
    @Json(name = "xmltv_endpoint") val xmltvEndpoint: String,
    @Json(name = "vod_endpoint") val vodEndpoint: String,
    @Json(name = "jellyfin_base_url") val jellyfinBaseUrl: String?,
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
        maxProfilesPerDevice = maxProfilesPerDevice,
        warningThresholdDays = warningThresholdDays,
        liveStreamLimitPerUser = liveStreamLimitPerUser,
        vodStreamLimitPerUser = vodStreamLimitPerUser,
        jellyfinStreamLimitPerUser = jellyfinStreamLimitPerUser
    )
}
