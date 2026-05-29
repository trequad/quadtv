package net.trequad.quadtv

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import net.trequad.quadtv.auth.CustomerLoginFragment
import net.trequad.quadtv.epg.EpgGridFragment
import net.trequad.quadtv.home.HomeFragment
import net.trequad.quadtv.jellyfin.JellyfinBrowseFragment
import net.trequad.quadtv.live.LiveTvFragment
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.profiles.ProfilePickerFragment
import net.trequad.quadtv.settings.SettingsFragment
import net.trequad.quadtv.vod.VodBrowseFragment

class MainActivity : FragmentActivity(), QuadTvNavigator {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            navigateTo(QuadTvRoute.LOGIN)
        }
    }

    override fun navigateTo(route: QuadTvRoute) {
        val fragment: Fragment = when (route) {
            QuadTvRoute.LOGIN -> CustomerLoginFragment()
            QuadTvRoute.HOME -> HomeFragment()
            QuadTvRoute.PROFILES -> ProfilePickerFragment()
            QuadTvRoute.LIVE_TV -> LiveTvFragment()
            QuadTvRoute.EPG -> EpgGridFragment()
            QuadTvRoute.VOD -> VodBrowseFragment()
            QuadTvRoute.JELLYFIN -> JellyfinBrowseFragment()
            QuadTvRoute.SETTINGS -> SettingsFragment()
        }

        val transaction = supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)

        if (route != QuadTvRoute.LOGIN) {
            transaction.addToBackStack(route.name)
        }

        transaction.commit()
    }

    override fun goBack() {
        supportFragmentManager.popBackStack()
    }
}
