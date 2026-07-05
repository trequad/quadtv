package net.trequad.quadtv

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import net.trequad.quadtv.auth.CustomerLoginFragment
import net.trequad.quadtv.auth.ExpiredSubscriptionFragment
import net.trequad.quadtv.auth.SubscriptionRequiredFragment
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.epg.EpgGridFragment
import net.trequad.quadtv.core.cache.OnboardingCache
import net.trequad.quadtv.favorites.FavoritesFragment
import net.trequad.quadtv.favorites.RecentlyViewedFragment
import net.trequad.quadtv.onboarding.OnboardingFragment
import net.trequad.quadtv.home.HomeFragment
import net.trequad.quadtv.jellyfin.JellyfinBrowseFragment
import net.trequad.quadtv.live.LiveTvFragment
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.player.PlayerFragment
import net.trequad.quadtv.player.StreamPlaybackRequest
import net.trequad.quadtv.profiles.ProfilePickerFragment
import net.trequad.quadtv.search.MovieSearchFragment
import net.trequad.quadtv.seerr.SeerrFragment
import net.trequad.quadtv.settings.SettingsFragment
import net.trequad.quadtv.vod.VodBrowseFragment

class MainActivity : FragmentActivity(), QuadTvNavigator {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            launchLoginOrProfiles()
        }
    }

    private fun launchLoginOrProfiles() {
        if (!OnboardingCache(this).isCompleted()) {
            navigateTo(QuadTvRoute.ONBOARDING)
            return
        }
        val prefs = getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, MODE_PRIVATE)
        val session = CustomerSessionCache(prefs).load()
        if (session != null) {
            navigateTo(QuadTvRoute.PROFILES)
        } else {
            navigateTo(QuadTvRoute.LOGIN)
        }
    }

    private fun guardSubscriptionRoute(route: QuadTvRoute): QuadTvRoute {
        val prefs = getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, MODE_PRIVATE)
        val session = CustomerSessionCache(prefs).load()
        if (session == null) return route
        return when (route) {
            QuadTvRoute.LIVE_TV, QuadTvRoute.EPG -> if (session.canAccessLiveTv) route else QuadTvRoute.SUBSCRIPTION_REQUIRED
            QuadTvRoute.VOD, QuadTvRoute.MOVIE_SEARCH -> if (session.canAccessVod) route else QuadTvRoute.SUBSCRIPTION_REQUIRED
            QuadTvRoute.JELLYFIN -> if (session.canAccessQuaddemand) route else QuadTvRoute.SUBSCRIPTION_REQUIRED
            QuadTvRoute.SEERR -> if (session.canAccessSeerr) route else QuadTvRoute.SUBSCRIPTION_REQUIRED
            else -> route
        }
    }

    override fun navigateTo(route: QuadTvRoute) {
        val guardedRoute = guardSubscriptionRoute(route)
        val fragment: Fragment = when (guardedRoute) {
            QuadTvRoute.LOGIN, QuadTvRoute.REGISTER -> CustomerLoginFragment()
            QuadTvRoute.EXPIRED -> ExpiredSubscriptionFragment()
            QuadTvRoute.SUBSCRIPTION_REQUIRED -> SubscriptionRequiredFragment()
            QuadTvRoute.HOME -> HomeFragment()
            QuadTvRoute.PROFILES -> ProfilePickerFragment()
            QuadTvRoute.LIVE_TV -> LiveTvFragment()
            QuadTvRoute.EPG -> EpgGridFragment()
            QuadTvRoute.MOVIE_SEARCH -> MovieSearchFragment()
            QuadTvRoute.VOD -> VodBrowseFragment()
            QuadTvRoute.JELLYFIN -> JellyfinBrowseFragment()
            QuadTvRoute.SEERR -> SeerrFragment()
            QuadTvRoute.FAVORITES -> FavoritesFragment()
            QuadTvRoute.RECENTLY_VIEWED -> RecentlyViewedFragment()
            QuadTvRoute.ONBOARDING -> OnboardingFragment()
            QuadTvRoute.SETTINGS -> SettingsFragment()
            QuadTvRoute.PLAYER -> PlayerFragment()
        }

        val transaction = supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)

        if (guardedRoute != QuadTvRoute.LOGIN) {
            transaction.addToBackStack(guardedRoute.name)
        }

        transaction.commit()
    }

    override fun navigateToPlayer(request: StreamPlaybackRequest) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, PlayerFragment.newInstance(request))
            .addToBackStack(QuadTvRoute.PLAYER.name)
            .commit()
    }

    override fun goBack() {
        supportFragmentManager.popBackStack()
    }

}
