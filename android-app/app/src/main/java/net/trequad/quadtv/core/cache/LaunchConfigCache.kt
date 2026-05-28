package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.adminapi.LaunchConfig

class LaunchConfigCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(config: LaunchConfig) {
        sharedPreferences.edit()
            .putString(KEY_LIVE_TV_ENDPOINT, config.liveTvEndpoint)
            .putString(KEY_XMLTV_ENDPOINT, config.xmltvEndpoint)
            .putString(KEY_VOD_ENDPOINT, config.vodEndpoint)
            .putString(KEY_JELLYFIN_BASE_URL, config.jellyfinBaseUrl)
            .putString(KEY_JELLYFIN_API_KEY, config.jellyfinApiKey)
            .putInt(KEY_MAX_PROFILES, config.maxProfilesPerDevice)
            .putString(KEY_WARNING_THRESHOLDS, config.warningThresholdDays.joinToString(","))
            .putInt(KEY_LIVE_STREAM_LIMIT, config.liveStreamLimitPerUser)
            .putInt(KEY_VOD_STREAM_LIMIT, config.vodStreamLimitPerUser)
            .putInt(KEY_JELLYFIN_STREAM_LIMIT, config.jellyfinStreamLimitPerUser)
            .apply()
    }

    fun load(): LaunchConfig? {
        val liveTvEndpoint = sharedPreferences.getString(KEY_LIVE_TV_ENDPOINT, null) ?: return null
        val xmltvEndpoint = sharedPreferences.getString(KEY_XMLTV_ENDPOINT, null) ?: return null
        val vodEndpoint = sharedPreferences.getString(KEY_VOD_ENDPOINT, null) ?: return null
        val defaults = LaunchConfig.defaults()
        return LaunchConfig(
            liveTvEndpoint = liveTvEndpoint,
            xmltvEndpoint = xmltvEndpoint,
            vodEndpoint = vodEndpoint,
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
        private const val KEY_LIVE_TV_ENDPOINT = "live_tv_endpoint"
        private const val KEY_XMLTV_ENDPOINT = "xmltv_endpoint"
        private const val KEY_VOD_ENDPOINT = "vod_endpoint"
        private const val KEY_JELLYFIN_BASE_URL = "jellyfin_base_url"
        private const val KEY_JELLYFIN_API_KEY = "jellyfin_api_key"
        private const val KEY_MAX_PROFILES = "max_profiles_per_device"
        private const val KEY_WARNING_THRESHOLDS = "warning_threshold_days"
        private const val KEY_LIVE_STREAM_LIMIT = "live_stream_limit_per_user"
        private const val KEY_VOD_STREAM_LIMIT = "vod_stream_limit_per_user"
        private const val KEY_JELLYFIN_STREAM_LIMIT = "jellyfin_stream_limit_per_user"
    }
}
