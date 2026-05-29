package net.trequad.quadtv.live

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
import net.trequad.quadtv.R

data class LiveTvAction(
    val label: String,
    val description: String
)

class LiveTvFragment : BrowseSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Live TV"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Live TV"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction("All Channels", "Browse channel groups and start in-app bundled player playback."))
                add(LiveTvAction("Open Guide", "Jump to the cable-style EPG grid while live preview remains available."))
                add(LiveTvAction("Info banner", "Channel, current programme, next programme, time, and progress overlay scaffold."))
            }))
        }
    }
}

private class LiveTvActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(44, 95, 124))
        })
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? LiveTvAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
