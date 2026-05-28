package net.trequad.quadtv.home

import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import net.trequad.quadtv.core.config.QuadTvConfig

class HomeFragment : BrowseSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND}"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(net.trequad.quadtv.R.color.quadmedia_blue, null)
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Architecture scaffold"), ArrayObjectAdapter(PlaceholderPresenter()).apply {
                add("Profile picker, Home, Live TV, EPG, VOD, Jellyfin, Settings")
            }))
        }
    }
}

private class PlaceholderPresenter : Presenter() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup): ViewHolder {
        val view = android.widget.TextView(parent.context).apply {
            textSize = 22f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(android.graphics.Color.rgb(44, 95, 124))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (viewHolder.view as android.widget.TextView).text = item?.toString().orEmpty()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
