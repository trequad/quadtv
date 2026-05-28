package net.trequad.quadtv.jellyfin

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

class JellyfinBrowseFragment : BrowseSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Jellyfin"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Jellyfin Libraries"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                add(JellyfinLibrary(id = "placeholder", name = "Browse configured Jellyfin libraries"))
            }))
        }
    }
}

class JellyfinCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TextView(parent.context).apply {
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(44, 95, 124))
        })
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val label = when (item) {
            is JellyfinLibrary -> item.name
            is JellyfinItem -> item.title
            else -> item?.toString().orEmpty()
        }
        (viewHolder.view as TextView).text = label
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
