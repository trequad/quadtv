package net.trequad.quadtv.profiles

import com.squareup.moshi.Json

data class QuadTvProfile(
    val id: Int,
    @Json(name = "device_id") val deviceId: Int,
    @Json(name = "display_name") val displayName: String,
    val avatar: String,
    @Json(name = "parental_enabled") val parentalEnabled: Boolean
)

data class ProfileListResponse(
    val items: List<QuadTvProfile>
)
