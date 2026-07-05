package net.trequad.quadtv.auth

import com.squareup.moshi.Json

data class CustomerLoginRequest(
    val username: String,
    val password: String
)

data class CustomerLoginResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type") val tokenType: String = "bearer",
    @Json(name = "user_id") val userId: Int,
    @Json(name = "provider_username") val providerUsername: String,
    val expired: Boolean,
    @Json(name = "expires_on") val expiresOn: String?,
    @Json(name = "days_remaining") val daysRemaining: Int?,
    @Json(name = "access_package") val accessPackage: String = "full_access",
    @Json(name = "can_access_live_tv") val canAccessLiveTv: Boolean = true,
    @Json(name = "can_access_vod") val canAccessVod: Boolean = true,
    @Json(name = "can_access_quaddemand") val canAccessQuaddemand: Boolean = true,
    @Json(name = "can_access_seerr") val canAccessSeerr: Boolean = true,
    // Per-user QuadOnDemand session issued by the portal at login.
    @Json(name = "jellyfin_base_url") val jellyfinBaseUrl: String? = null,
    @Json(name = "jellyfin_user_id") val jellyfinUserId: String? = null,
    @Json(name = "jellyfin_access_token") val jellyfinAccessToken: String? = null,
    val message: String?
)
