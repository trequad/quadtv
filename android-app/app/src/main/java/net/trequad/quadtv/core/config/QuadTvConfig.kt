package net.trequad.quadtv.core.config

object QuadTvConfig {
    const val APP_NAME = "QuadTV"
    const val PARENT_BRAND = "QuadMedia"

    // Operator-controlled provider defaults. The portal may override these at launch.
    const val ADMIN_PORTAL_BASE_URL = "https://example.invalid/"
    const val LIVE_TV_DNS_ENDPOINT = "https://live.example.invalid/playlist.m3u"
    const val LIVE_TV_XMLTV_ENDPOINT = "https://live.example.invalid/xmltv.xml"
    const val OPERATOR_VOD_PROVIDER_BASE_URL = "https://livinitup.online"
    const val VOD_DNS_ENDPOINT = OPERATOR_VOD_PROVIDER_BASE_URL

    const val DEFAULT_MAX_PROFILES_PER_DEVICE = 5
    const val DEFAULT_LIVE_STREAM_LIMIT_PER_USER = 3
    const val DEFAULT_VOD_STREAM_LIMIT_PER_USER = 1
    const val DEFAULT_JELLYFIN_STREAM_LIMIT_PER_USER = 2
    const val DEFAULT_PLAYER = "EXOPLAYER"
}
