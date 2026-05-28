package net.trequad.quadtv.adminapi

import retrofit2.http.GET

interface AdminApiService {
    @GET("api/v1/app/config")
    suspend fun getLaunchConfig(): LaunchConfigDto
}

data class LaunchConfigDto(
    val liveTvEndpoint: String,
    val xmltvEndpoint: String,
    val vodEndpoint: String,
    val jellyfinBaseUrl: String?,
    val maxProfilesPerDevice: Int,
    val warningThresholdDays: List<Int>
)
