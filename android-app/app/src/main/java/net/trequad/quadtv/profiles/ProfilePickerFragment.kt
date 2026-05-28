package net.trequad.quadtv.profiles

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

class ProfilePickerFragment : BrowseSupportFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Profiles"
        headersState = HEADERS_DISABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            add(
                ListRow(
                    HeaderItem(0, "Choose who is watching"),
                    ArrayObjectAdapter(ProfileCardPresenter()).apply {
                        add(QuadTvProfile(0, 0, "Choose who is watching", "quadmedia", false))
                    }
                )
            )
        }
    }
}

class ProfileCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.rgb(44, 95, 124))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val profile = item as? QuadTvProfile
        (viewHolder.view as TextView).text = profile?.displayName ?: item?.toString().orEmpty()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
