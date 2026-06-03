package net.trequad.quadtv.core.cache

import android.content.Context

class OnboardingCache(context: Context) {
    private val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_onboarding"
        private const val KEY_COMPLETED = "onboarding_completed"
    }
}
