package net.trequad.quadtv

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.auth.CustomerLoginFragment
import net.trequad.quadtv.auth.ExpiredSubscriptionFragment
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
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
import net.trequad.quadtv.updates.AppUpdateRepository
import net.trequad.quadtv.updates.UpdatePromptFragment
import net.trequad.quadtv.vod.VodBrowseFragment

class MainActivity : FragmentActivity(), QuadTvNavigator {
    private lateinit var appUpdateRepository: AppUpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateRepository = buildAppUpdateRepository()
        if (savedInstanceState == null) {
            checkForRequiredUpdateThenLaunch()
        }
    }

    private fun checkForRequiredUpdateThenLaunch() {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                appUpdateRepository.loadUpdateStatus()
            }

            if (status.forcedUpdateRequired) {
                showUpdatePrompt(forced = true)
            } else if (status.updateAvailable) {
                showUpdatePrompt(forced = false)
            } else {
                launchLoginOrProfiles()
            }
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

    override fun navigateTo(route: QuadTvRoute) {
        val fragment: Fragment = when (route) {
            QuadTvRoute.LOGIN, QuadTvRoute.REGISTER -> CustomerLoginFragment()
            QuadTvRoute.EXPIRED -> ExpiredSubscriptionFragment()
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

        if (route != QuadTvRoute.LOGIN) {
            transaction.addToBackStack(route.name)
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

    private fun showUpdatePrompt(forced: Boolean) {
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, buildUpdatePrompt(forced))
            .commit()
    }

    private fun buildUpdatePrompt(forced: Boolean): Fragment {
        return if (forced) UpdatePromptFragment.forced() else UpdatePromptFragment.optional()
    }

    private fun buildAppUpdateRepository(): AppUpdateRepository {
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        return AppUpdateRepository(apiService)
    }
}
