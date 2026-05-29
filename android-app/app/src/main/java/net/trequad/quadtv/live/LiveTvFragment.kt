package net.trequad.quadtv.live

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import net.trequad.quadtv.R
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

sealed class LiveTvAction(
    open val label: String,
    open val description: String,
    open val route: QuadTvRoute? = null
) {
    data object AllChannels : LiveTvAction(
        "All Channels",
        "Browse channel groups and start in-app bundled player playback."
    )

    data object OpenGuide : LiveTvAction(
        "Open Guide",
        "Jump to the cable-style EPG grid while live preview remains available.",
        QuadTvRoute.EPG
    )

    data object InfoBanner : LiveTvAction(
        "Info banner",
        "Channel, current programme, next programme, time, and progress overlay scaffold."
    )

    data class Channel(
        val channel: LiveChannel,
        override val label: String = channel.name,
        override val description: String = "Prepare HLS playback handoff through the selected bundled player."
    ) : LiveTvAction(label, description)
}

class LiveTvFragment : BrowseSupportFragment() {
    private val playbackCoordinator = LiveTvPlaybackCoordinator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Live TV"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildLiveRows()
        setOnItemViewClickedListener { _, item, _, _ ->
            val action = item as? LiveTvAction ?: return@setOnItemViewClickedListener
            action.route?.let { route ->
                (activity as? QuadTvNavigator)?.navigateTo(route)
                return@setOnItemViewClickedListener
            }
            when (action) {
                is LiveTvAction.Channel -> {
                    val request = playbackCoordinator.buildRequest(action.channel)
                    Toast.makeText(
                        requireContext(),
                        "Prepared ${request.title} for bundled live playback",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> Unit
            }
        }
    }

    private fun buildLiveRows(): ArrayObjectAdapter {
        val previewChannel = LiveChannel(
            id = "preview-channel",
            name = "Preview Channel",
            streamUrl = "https://example.invalid/quadtv/live/preview.m3u8",
            groupTitle = "QuadTV Preview"
        )

        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Live TV"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction.AllChannels)
                add(LiveTvAction.OpenGuide)
                add(LiveTvAction.InfoBanner)
            }))
            add(ListRow(HeaderItem(1, "Playback Handoff"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction.Channel(previewChannel))
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
