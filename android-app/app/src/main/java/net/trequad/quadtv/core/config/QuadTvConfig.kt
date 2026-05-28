package net.trequad.quadtv.core.config

object QuadTvConfig {
    const val APP_NAME = "QuadTV"
    const val PARENT_BRAND = "QuadMedia"

    // Developer-side defaults. Production values should be injected by the admin portal config API.
    const val ADMIN_PORTAL_BASE_URL = "https://example.invalid/"
    const val LIVE_TV_DNS_ENDPOINT = "https://live.example.invalid/playlist.m3u"
    const val LIVE_TV_XMLTV_ENDPOINT = "https://live.example.invalid/xmltv.xml"
    const val VOD_DNS_ENDPOINT = "https://vod.example.invalid/"

    const val DEFAULT_MAX_PROFILES_PER_DEVICE = 5
    const val DEFAULT_LIVE_STREAM_LIMIT_PER_USER = 3
    const val DEFAULT_VOD_STREAM_LIMIT_PER_USER = 1
    const val DEFAULT_JELLYFIN_STREAM_LIMIT_PER_USER = 2
    const val DEFAULT_PLAYER = "EXOPLAYER"
}
