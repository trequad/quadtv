package net.trequad.quadtv.favorites

import net.trequad.quadtv.core.ui.QuadTvTheme
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
import net.trequad.quadtv.jellyfin.JellyfinDetailsFragment
import net.trequad.quadtv.live.BookmarkedLiveChannel
import net.trequad.quadtv.live.LiveChannelBookmarkStore
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.player.StreamPlaybackRequest
import net.trequad.quadtv.vod.VodDetailsFragment

sealed class RecentAction(open val label: String) {
    data class PlayLive(val channel: BookmarkedLiveChannel) : RecentAction(channel.name)
    data class OpenMediaDetails(val item: BookmarkedMediaItem) : RecentAction(item.title)
    data class Message(override val label: String) : RecentAction(label)
}

class RecentlyViewedFragment : BrowseSupportFragment() {
    private val liveStore: LiveChannelBookmarkStore by lazy {
        LiveChannelBookmarkStore(requireContext().applicationContext)
    }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Recently Viewed"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildRecentRows()
        setOnItemViewClickedListener { _, item, _, _ ->
            when (val action = item as? RecentAction) {
                is RecentAction.PlayLive ->
                    (activity as? QuadTvNavigator)?.navigateToPlayer(action.channel.toPlaybackRequest())
                is RecentAction.OpenMediaDetails ->
                    openMediaDetails(action.item)
                else -> Unit
            }
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.drawable.quadtv_neon_waves_background)
    }

    override fun onResume() {
        super.onResume()
        adapter = buildRecentRows()
    }

    private fun buildRecentRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            addRecentLiveRow(rowId = 0)
            addRecentMediaRow(rowId = 1, title = "Recently Viewed VOD", source = BookmarkedMediaSource.VOD)
            addRecentMediaRow(rowId = 2, title = "Recently Viewed QuadOnDemand", source = BookmarkedMediaSource.JELLYFIN)
        }
    }

    private fun ArrayObjectAdapter.addRecentLiveRow(rowId: Long) {
        val channels = liveStore.recentChannels()
        val rowAdapter = ArrayObjectAdapter(RecentActionPresenter())
        if (channels.isEmpty()) {
            rowAdapter.add(RecentAction.Message("No recently viewed live channels"))
        } else {
            channels.forEach { rowAdapter.add(RecentAction.PlayLive(it)) }
        }
        add(ListRow(HeaderItem(rowId, "Recently Viewed Live"), rowAdapter))
    }

    private fun ArrayObjectAdapter.addRecentMediaRow(rowId: Long, title: String, source: BookmarkedMediaSource) {
        val items = mediaStore.recentItems().filter { it.source == source }
        val rowAdapter = ArrayObjectAdapter(RecentActionPresenter())
        if (items.isEmpty()) {
            rowAdapter.add(RecentAction.Message("No ${title.lowercase()} yet"))
        } else {
            items.forEach { rowAdapter.add(RecentAction.OpenMediaDetails(it)) }
        }
        add(ListRow(HeaderItem(rowId, title), rowAdapter))
    }

    private fun openMediaDetails(item: BookmarkedMediaItem) {
        val fragment = when (item.source) {
            BookmarkedMediaSource.VOD -> VodDetailsFragment.newInstance(item.toVodItem())
            BookmarkedMediaSource.JELLYFIN -> JellyfinDetailsFragment.newInstance(item.toJellyfinItem())
        }
        parentFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("MEDIA_DETAILS")
            .commit()
    }

    private fun BookmarkedLiveChannel.toPlaybackRequest(): StreamPlaybackRequest = StreamPlaybackRequest(
        url = streamUrl, channelId = id, title = name, groupTitle = groupTitle,
        contentTitle = contentTitle ?: "Live TV", subtitle = "Live TV", isLive = true
    )
}

private class RecentActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(QuadTvTheme.FOCUS)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        (viewHolder.view as TextView).text = (item as? RecentAction)?.label ?: item?.toString().orEmpty()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
