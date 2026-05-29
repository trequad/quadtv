package net.trequad.quadtv.provider

data class LiveTvProviderFeed(
    val liveTvPlaylistUrl: String,
    val xmltvUrl: String,
    val fetchedAtMillis: Long
)

object ProviderFeedPolicy {
    const val PROVIDER_FEED_REFRESH_HOURS = 24
    const val REFRESH_INTERVAL_MILLIS = PROVIDER_FEED_REFRESH_HOURS * 60L * 60L * 1000L
}
