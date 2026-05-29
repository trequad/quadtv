package net.trequad.quadtv.home

import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

data class HomeAction(
    val label: String,
    val description: String,
    val route: QuadTvRoute
)

class HomeFragment : BrowseSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND}"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(net.trequad.quadtv.R.color.quadmedia_blue, null)
        adapter = buildHomeRows()
        setOnItemViewClickedListener { _, item, _, _ ->
            val action = item as? HomeAction ?: return@setOnItemViewClickedListener
            (activity as? QuadTvNavigator)?.navigateTo(action.route)
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
                add(HomeAction("On-Demand", "Browse VOD categories and details.", QuadTvRoute.VOD))
                add(HomeAction("Jellyfin", "Browse configured Jellyfin libraries.", QuadTvRoute.JELLYFIN))
                add(HomeAction("Settings", "Player, buffering, language, parental, and about settings.", QuadTvRoute.SETTINGS))
            }))
        }
    }
}

private class HomeActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val view = android.widget.TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(android.graphics.Color.rgb(44, 95, 124))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? HomeAction
        (viewHolder.view as android.widget.TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
