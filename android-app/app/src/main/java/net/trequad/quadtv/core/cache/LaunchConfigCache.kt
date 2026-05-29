package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.adminapi.LaunchConfig

class LaunchConfigCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(config: LaunchConfig) {
        sharedPreferences.edit()
            .putString(KEY_LIVE_TV_PROVIDER_BASE_URL, config.liveTvProviderBaseUrl)
            .putString(KEY_VOD_PROVIDER_BASE_URL, config.vodProviderBaseUrl)
            .putString(KEY_JELLYFIN_BASE_URL, config.jellyfinBaseUrl)
            .putString(KEY_JELLYFIN_API_KEY, config.jellyfinApiKey)
            .putInt(KEY_MAX_PROFILES, config.maxProfilesPerDevice)
            .putString(KEY_WARNING_THRESHOLDS, config.warningThresholdDays.joinToString(","))
            .putInt(KEY_LIVE_STREAM_LIMIT, config.liveStreamLimitPerUser)
            .putInt(KEY_VOD_STREAM_LIMIT, config.vodStreamLimitPerUser)
            .putInt(KEY_JELLYFIN_STREAM_LIMIT, config.jellyfinStreamLimitPerUser)
            .putInt(KEY_PROVIDER_FEED_REFRESH_HOURS, config.providerFeedRefreshHours)
            .apply()
    }

    fun load(): LaunchConfig? {
        val liveTvProviderBaseUrl = sharedPreferences.getString(KEY_LIVE_TV_PROVIDER_BASE_URL, null) ?: return null
        val vodProviderBaseUrl = sharedPreferences.getString(KEY_VOD_PROVIDER_BASE_URL, null) ?: return null
        val defaults = LaunchConfig.defaults()
        return LaunchConfig(
            liveTvProviderBaseUrl = liveTvProviderBaseUrl,
            vodProviderBaseUrl = vodProviderBaseUrl,
            jellyfinBaseUrl = sharedPreferences.getString(KEY_JELLYFIN_BASE_URL, null),
            jellyfinApiKey = sharedPreferences.getString(KEY_JELLYFIN_API_KEY, null),
            maxProfilesPerDevice = sharedPreferences.getInt(KEY_MAX_PROFILES, defaults.maxProfilesPerDevice),
            warningThresholdDays = parseThresholds(
                sharedPreferences.getString(KEY_WARNING_THRESHOLDS, null),
                defaults.warningThresholdDays
            ),
            liveStreamLimitPerUser = sharedPreferences.getInt(KEY_LIVE_STREAM_LIMIT, defaults.liveStreamLimitPerUser),
            vodStreamLimitPerUser = sharedPreferences.getInt(KEY_VOD_STREAM_LIMIT, defaults.vodStreamLimitPerUser),
            jellyfinStreamLimitPerUser = sharedPreferences.getInt(
                KEY_JELLYFIN_STREAM_LIMIT,
                defaults.jellyfinStreamLimitPerUser
            ),
            providerFeedRefreshHours = sharedPreferences.getInt(
                KEY_PROVIDER_FEED_REFRESH_HOURS,
                defaults.providerFeedRefreshHours
            )
        )
    }

    private fun parseThresholds(raw: String?, fallback: List<Int>): List<Int> {
        if (raw.isNullOrBlank()) return fallback
        val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return parsed.ifEmpty { fallback }
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_launch_config"
        private const val KEY_LIVE_TV_PROVIDER_BASE_URL = "live_tv_provider_base_url"
        private const val KEY_VOD_PROVIDER_BASE_URL = "vod_provider_base_url"
        private const val KEY_JELLYFIN_BASE_URL = "jellyfin_base_url"
        private const val KEY_JELLYFIN_API_KEY = "jellyfin_api_key"
        private const val KEY_MAX_PROFILES = "max_profiles_per_device"
        private const val KEY_WARNING_THRESHOLDS = "warning_threshold_days"
        private const val KEY_LIVE_STREAM_LIMIT = "live_stream_limit_per_user"
        private const val KEY_VOD_STREAM_LIMIT = "vod_stream_limit_per_user"
        private const val KEY_JELLYFIN_STREAM_LIMIT = "jellyfin_stream_limit_per_user"
        private const val KEY_PROVIDER_FEED_REFRESH_HOURS = "provider_feed_refresh_hours"
    }
}
