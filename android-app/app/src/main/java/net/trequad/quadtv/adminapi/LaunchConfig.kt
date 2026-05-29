package net.trequad.quadtv.adminapi

import net.trequad.quadtv.core.config.QuadTvConfig

/**
 * Domain launch configuration used by the Android TV app after combining
 * portal-provided values, local cache, and baked developer defaults.
 *
 * Provider base URLs are operator-editable DNS roots. Live TV playlist and
 * XMLTV/EPG feed URLs are user-specific and resolved after customer login.
 */
data class LaunchConfig(
    val liveTvProviderBaseUrl: String,
    val vodProviderBaseUrl: String,
    val jellyfinBaseUrl: String?,
    val jellyfinApiKey: String?,
    val maxProfilesPerDevice: Int,
    val warningThresholdDays: List<Int>,
    val liveStreamLimitPerUser: Int,
    val vodStreamLimitPerUser: Int,
    val jellyfinStreamLimitPerUser: Int,
    val providerFeedRefreshHours: Int
) {
    companion object {
        fun defaults() = LaunchConfig(
            liveTvProviderBaseUrl = QuadTvConfig.OPERATOR_LIVE_TV_PROVIDER_BASE_URL,
            vodProviderBaseUrl = QuadTvConfig.OPERATOR_VOD_PROVIDER_BASE_URL,
            jellyfinBaseUrl = null,
            jellyfinApiKey = null,
            maxProfilesPerDevice = QuadTvConfig.DEFAULT_MAX_PROFILES_PER_DEVICE,
            warningThresholdDays = listOf(14, 7, 3, 0),
            liveStreamLimitPerUser = QuadTvConfig.DEFAULT_LIVE_STREAM_LIMIT_PER_USER,
            vodStreamLimitPerUser = QuadTvConfig.DEFAULT_VOD_STREAM_LIMIT_PER_USER,
            jellyfinStreamLimitPerUser = QuadTvConfig.DEFAULT_JELLYFIN_STREAM_LIMIT_PER_USER,
            providerFeedRefreshHours = QuadTvConfig.PROVIDER_FEED_REFRESH_HOURS
        )
    }
}
