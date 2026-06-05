package net.trequad.quadtv.navigation

import net.trequad.quadtv.player.StreamPlaybackRequest


enum class QuadTvRoute {
    LOGIN,
    REGISTER,
    EXPIRED,
    SUBSCRIPTION_REQUIRED,
    HOME,
    PROFILES,
    LIVE_TV,
    EPG,
    MOVIE_SEARCH,
    VOD,
    JELLYFIN,
    SEERR,
    FAVORITES,
    RECENTLY_VIEWED,
    ONBOARDING,
    SETTINGS,
    PLAYER
}

interface QuadTvNavigator {
    fun navigateTo(route: QuadTvRoute)
    fun navigateToPlayer(request: StreamPlaybackRequest)
    fun goBack()
}
