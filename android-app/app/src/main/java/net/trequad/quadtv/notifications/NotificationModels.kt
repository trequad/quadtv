package net.trequad.quadtv.notifications

import com.squareup.moshi.Json

data class FcmTokenRegistrationRequest(
    val token: String,
    val platform: String = "android-tv"
)

data class FcmTokenRegistrationResponse(
    @Json(name = "device_id") val deviceId: Int,
    @Json(name = "token_registered") val tokenRegistered: Boolean,
    val platform: String
)

data class QuadTvNotificationPayload(
    val type: String = "service_alert",
    val title: String = "QuadTV",
    val body: String = "QuadMedia notification",
    val targetScreen: String? = null
)
