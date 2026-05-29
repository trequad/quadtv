package net.trequad.quadtv.navigation

import net.trequad.quadtv.player.StreamPlaybackRequest


enum class QuadTvRoute {
    LOGIN,
    HOME,
    PROFILES,
    LIVE_TV,
    EPG,
    VOD,
    JELLYFIN,
    SETTINGS,
    PLAYER
}

interface QuadTvNavigator {
    fun navigateTo(route: QuadTvRoute)
    fun navigateToPlayer(request: StreamPlaybackRequest)
    fun goBack()
}
