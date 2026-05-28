package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.auth.CustomerLoginResponse

data class CachedCustomerSession(
    val accessToken: String,
    val userId: Int,
    val providerUsername: String,
    val expiresOn: String?
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
            expiresOn = sharedPreferences.getString(KEY_EXPIRES_ON, null)
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
        private const val MISSING_USER_ID = -1
    }
}
