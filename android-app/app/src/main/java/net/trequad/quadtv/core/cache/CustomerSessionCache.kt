package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.auth.CustomerLoginResponse

data class CachedCustomerSession(
    val accessToken: String,
    val userId: Int,
    val providerUsername: String,
    val expiresOn: String?,
    val accessPackage: String,
    val canAccessLiveTv: Boolean,
    val canAccessVod: Boolean,
    val canAccessQuaddemand: Boolean,
    val canAccessSeerr: Boolean
)

class CustomerSessionCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(response: CustomerLoginResponse) {
        sharedPreferences.edit()
            .putString(KEY_ACCESS_TOKEN, response.accessToken)
            .putInt(KEY_USER_ID, response.userId)
            .putString(KEY_PROVIDER_USERNAME, response.providerUsername)
            .putString(KEY_EXPIRES_ON, response.expiresOn)
            .putString(KEY_ACCESS_PACKAGE, response.accessPackage)
            .putBoolean(KEY_CAN_ACCESS_LIVE_TV, response.canAccessLiveTv)
            .putBoolean(KEY_CAN_ACCESS_VOD, response.canAccessVod)
            .putBoolean(KEY_CAN_ACCESS_QUADDEMAND, response.canAccessQuaddemand)
            .putBoolean(KEY_CAN_ACCESS_SEERR, response.canAccessSeerr)
            .apply()
    }

    fun load(): CachedCustomerSession? {
        val accessToken = sharedPreferences.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val providerUsername = sharedPreferences.getString(KEY_PROVIDER_USERNAME, null) ?: return null
        val userId = sharedPreferences.getInt(KEY_USER_ID, MISSING_USER_ID)
        if (userId == MISSING_USER_ID) return null
        return CachedCustomerSession(
            accessToken = accessToken,
            userId = userId,
            providerUsername = providerUsername,
            expiresOn = sharedPreferences.getString(KEY_EXPIRES_ON, null),
            accessPackage = sharedPreferences.getString(KEY_ACCESS_PACKAGE, "full_access") ?: "full_access",
            canAccessLiveTv = sharedPreferences.getBoolean(KEY_CAN_ACCESS_LIVE_TV, true),
            canAccessVod = sharedPreferences.getBoolean(KEY_CAN_ACCESS_VOD, true),
            canAccessQuaddemand = sharedPreferences.getBoolean(KEY_CAN_ACCESS_QUADDEMAND, true),
            canAccessSeerr = sharedPreferences.getBoolean(KEY_CAN_ACCESS_SEERR, true)
        )
    }

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_customer_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PROVIDER_USERNAME = "provider_username"
        private const val KEY_EXPIRES_ON = "expires_on"
        private const val KEY_ACCESS_PACKAGE = "access_package"
        private const val KEY_CAN_ACCESS_LIVE_TV = "can_access_live_tv"
        private const val KEY_CAN_ACCESS_VOD = "can_access_vod"
        private const val KEY_CAN_ACCESS_QUADDEMAND = "can_access_quaddemand"
        private const val KEY_CAN_ACCESS_SEERR = "can_access_seerr"
        private const val MISSING_USER_ID = -1
    }
}
