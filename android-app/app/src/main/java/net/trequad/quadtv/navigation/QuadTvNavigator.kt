package net.trequad.quadtv.navigation

enum class QuadTvRoute {
    LOGIN,
    HOME,
    PROFILES,
    LIVE_TV,
    EPG,
    VOD,
    JELLYFIN,
    SETTINGS
}

interface QuadTvNavigator {
    fun navigateTo(route: QuadTvRoute)
    fun goBack()
}
