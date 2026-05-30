package net.trequad.quadtv.home

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.provider.ProviderFeedRefreshCoordinator
import net.trequad.quadtv.provider.ProviderFeedRepository

data class HomeAction(
    val label: String,
    val description: String,
    val route: QuadTvRoute? = null,
    val refreshProviderFeeds: Boolean = false
)

class HomeFragment : BrowseSupportFragment() {
    private val providerFeedRefreshCoordinator: ProviderFeedRefreshCoordinator by lazy {
        buildProviderFeedRefreshCoordinator()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND}"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildHomeRows()
        setOnItemViewClickedListener { _, item, _, _ ->
            val action = item as? HomeAction ?: return@setOnItemViewClickedListener
            if (action.refreshProviderFeeds) {
                showProviderFeedRefreshDialog()
                return@setOnItemViewClickedListener
            }
            action.route?.let { route ->
                (activity as? QuadTvNavigator)?.navigateTo(route)
            }
        }
    }

    private fun buildHomeRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Announcements"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Announcements", "Operator messages and service alerts appear here.", QuadTvRoute.HOME))
            }))
            add(ListRow(HeaderItem(1, "Continue Watching"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Live TV", "Resume your last watched channel.", QuadTvRoute.LIVE_TV))
                add(HomeAction("On-Demand", "Resume recently watched VOD.", QuadTvRoute.VOD))
            }))
            add(ListRow(HeaderItem(2, "Recently Added VOD"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("On-Demand", "Browse recently added movies, series, and episodes.", QuadTvRoute.VOD))
            }))
            add(ListRow(HeaderItem(3, "Quick Access"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Live TV", "Open the channel browser.", QuadTvRoute.LIVE_TV))
                add(HomeAction("Guide", "Open the cable-style EPG grid.", QuadTvRoute.EPG))
                add(HomeAction("Refresh Playlist & Guide", "Fetch the latest playlist and EPG for this QuadTV account.", refreshProviderFeeds = true))
                add(HomeAction("On-Demand", "Browse VOD categories and details.", QuadTvRoute.VOD))
                add(HomeAction("Jellyfin", "Browse configured Jellyfin libraries.", QuadTvRoute.JELLYFIN))
                add(HomeAction("Settings", "Player, buffering, language, parental, and about settings.", QuadTvRoute.SETTINGS))
            }))
        }
    }

    private fun showProviderFeedRefreshDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("QuadTV Playlist Refresh")
            .setMessage("Fetching latest playlist and guide from provider… QuadTV is fetching your playlist and guide from the configured provider DNS.")
            .setPositiveButton("Close") { activeDialog, _ -> activeDialog.dismiss() }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        }
        dialog.show()

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    providerFeedRefreshCoordinator.refreshPlaylistAndGuide()
                }
                dialog.setMessage(
                    "Playlist and guide refreshed. ${result.playlistChannelCount} channels and " +
                        "${result.guideProgrammeCount} guide programmes are ready for Live TV and Guide."
                )
            } catch (_: Exception) {
                dialog.setMessage(
                    "Unable to refresh playlist and guide. Check your connection, then try again from Home."
                )
            } finally {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            }
        }
    }

    private fun buildProviderFeedRefreshCoordinator(): ProviderFeedRefreshCoordinator {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val launchPreferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(
            AdminConfigRepository(apiService, LaunchConfigCache(launchPreferences)),
            CustomerSessionCache(sessionPreferences)
        )
        return ProviderFeedRefreshCoordinator(
            liveTvRepository = LiveTvRepository(providerFeedRepository, okHttpClient),
            epgRepository = EpgRepository(providerFeedRepository, okHttpClient)
        )
    }
}

private class HomeActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(44, 95, 124))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? HomeAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
