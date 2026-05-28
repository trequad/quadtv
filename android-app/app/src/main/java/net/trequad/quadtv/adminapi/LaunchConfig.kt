package net.trequad.quadtv.adminapi

import net.trequad.quadtv.core.config.QuadTvConfig

/**
 * Domain launch configuration used by the Android TV app after combining
 * portal-provided values, local cache, and baked developer defaults.
 */
data class LaunchConfig(
    val liveTvEndpoint: String,
    val xmltvEndpoint: String,
    val vodEndpoint: String,
    val jellyfinBaseUrl: String?,
    val maxProfilesPerDevice: Int,
    val warningThresholdDays: List<Int>,
    val liveStreamLimitPerUser: Int,
    val vodStreamLimitPerUser: Int,
    val jellyfinStreamLimitPerUser: Int
) {
    companion object {
        fun defaults() = LaunchConfig(
            liveTvEndpoint = QuadTvConfig.LIVE_TV_DNS_ENDPOINT,
            xmltvEndpoint = QuadTvConfig.LIVE_TV_XMLTV_ENDPOINT,
            vodEndpoint = QuadTvConfig.VOD_DNS_ENDPOINT,
            jellyfinBaseUrl = null,
            maxProfilesPerDevice = QuadTvConfig.DEFAULT_MAX_PROFILES_PER_DEVICE,
            warningThresholdDays = listOf(14, 7, 3, 0),
            liveStreamLimitPerUser = QuadTvConfig.DEFAULT_LIVE_STREAM_LIMIT_PER_USER,
            vodStreamLimitPerUser = QuadTvConfig.DEFAULT_VOD_STREAM_LIMIT_PER_USER,
            jellyfinStreamLimitPerUser = QuadTvConfig.DEFAULT_JELLYFIN_STREAM_LIMIT_PER_USER
        )
    }
}
