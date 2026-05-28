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
    val message: String?
)
