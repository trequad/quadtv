package net.trequad.quadtv.core.config

object QuadTvConfig {
    const val APP_NAME = "QuadTV"
    const val PARENT_BRAND = "QuadMedia"

    // Operator-controlled provider defaults. The portal may override these at launch.
    const val ADMIN_PORTAL_BASE_URL = "https://example.invalid/"
    const val SEERR_BASE_URL = "http://10.34.1.194:5055"
    const val SEERR_ADMIN_EMAIL = "jpitmon34@gmail.com"
    const val SEERR_ADMIN_PASSWORD = "REDACTED_PASSWORD"
    const val OPERATOR_LIVE_TV_PROVIDER_BASE_URL = "http://by.questreams.com:83"
    const val OPERATOR_VOD_PROVIDER_BASE_URL = "https://livinitup.online"
    const val PROVIDER_FEED_REFRESH_HOURS = 24

    @Deprecated("Use OPERATOR_LIVE_TV_PROVIDER_BASE_URL; playlists are per-user provider feeds.")
    const val LIVE_TV_DNS_ENDPOINT = OPERATOR_LIVE_TV_PROVIDER_BASE_URL
    @Deprecated("Use OPERATOR_VOD_PROVIDER_BASE_URL.")
    const val VOD_DNS_ENDPOINT = OPERATOR_VOD_PROVIDER_BASE_URL

    const val DEFAULT_MAX_PROFILES_PER_DEVICE = 5
    const val DEFAULT_LIVE_STREAM_LIMIT_PER_USER = 3
    const val DEFAULT_VOD_STREAM_LIMIT_PER_USER = 1
    const val DEFAULT_JELLYFIN_STREAM_LIMIT_PER_USER = 2
    const val DEFAULT_PLAYER = "VLC"
}
