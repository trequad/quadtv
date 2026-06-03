package net.trequad.quadtv.core.cache

import android.content.SharedPreferences

class ProfileSelectionCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(profileId: Int) {
        sharedPreferences.edit().putInt(KEY_PROFILE_ID, profileId).apply()
    }

    fun loadProfileId(): Int? {
        val profileId = sharedPreferences.getInt(KEY_PROFILE_ID, MISSING_PROFILE_ID)
        return profileId.takeIf { it != MISSING_PROFILE_ID }
    }

    fun scopeKey(): String = loadProfileId()?.let { "profile_$it" } ?: "profile_none"

    companion object {
        const val PREFERENCES_NAME = "quadtv_profile_selection"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val MISSING_PROFILE_ID = -1
    }
}
