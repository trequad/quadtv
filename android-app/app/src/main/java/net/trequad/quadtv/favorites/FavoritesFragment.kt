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
import net.trequad.quadtv.jellyfin.JellyfinItem
import net.trequad.quadtv.live.BookmarkedLiveChannel
import net.trequad.quadtv.live.LiveChannelBookmarkStore
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.player.StreamPlaybackRequest
import net.trequad.quadtv.vod.VodDetailsFragment
import net.trequad.quadtv.vod.VodItem

sealed class FavoritesAction(open val label: String) {
    data class PlayLive(val channel: BookmarkedLiveChannel) : FavoritesAction(channel.name)
    data class OpenMediaDetails(val item: BookmarkedMediaItem) : FavoritesAction(item.title)
    data class Message(override val label: String) : FavoritesAction(label)
}

class FavoritesFragment : BrowseSupportFragment() {
    private val liveStore: LiveChannelBookmarkStore by lazy {
        LiveChannelBookmarkStore(requireContext().applicationContext)
    }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Favorites"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildFavoritesRows()
        setOnItemViewClickedListener { _, item, _, _ ->
            when (val action = item as? FavoritesAction) {
                is FavoritesAction.PlayLive ->
                    (activity as? QuadTvNavigator)?.navigateToPlayer(action.channel.toPlaybackRequest())
                is FavoritesAction.OpenMediaDetails ->
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
        adapter = buildFavoritesRows()
    }

    private fun buildFavoritesRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            addFavoriteLiveRow(rowId = 0)
            addFavoriteMediaRow(rowId = 1, title = "Favorite VOD", source = BookmarkedMediaSource.VOD)
            addFavoriteMediaRow(rowId = 2, title = "Favorite QuadOnDemand", source = BookmarkedMediaSource.JELLYFIN)
        }
    }

    private fun ArrayObjectAdapter.addFavoriteLiveRow(rowId: Long) {
        val channels = liveStore.favoriteChannels()
        val rowAdapter = ArrayObjectAdapter(FavoritesActionPresenter())
        if (channels.isEmpty()) {
            rowAdapter.add(FavoritesAction.Message("No favorite live channels yet"))
        } else {
            channels.forEach { rowAdapter.add(FavoritesAction.PlayLive(it)) }
        }
        add(ListRow(HeaderItem(rowId, "Favorite Live Channels"), rowAdapter))
    }

    private fun ArrayObjectAdapter.addFavoriteMediaRow(rowId: Long, title: String, source: BookmarkedMediaSource) {
        val items = mediaStore.favoriteItems().filter { it.source == source }
        val rowAdapter = ArrayObjectAdapter(FavoritesActionPresenter())
        if (items.isEmpty()) {
            rowAdapter.add(FavoritesAction.Message("No ${title.lowercase()} yet"))
        } else {
            items.forEach { rowAdapter.add(FavoritesAction.OpenMediaDetails(it)) }
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

fun BookmarkedMediaItem.toVodItem(): VodItem = VodItem(
    id = id, title = title, posterUrl = posterUrl, description = description,
    rating = rating, releaseYear = releaseYear, streamUrl = streamUrl,
    isSeries = isSeries, isMature = isMature
)

fun BookmarkedMediaItem.toJellyfinItem(): JellyfinItem = JellyfinItem(
    id = id, title = title, posterUrl = posterUrl, overview = description,
    contentRating = rating, productionYear = releaseYear, isFolder = false, isMature = isMature
)

private class FavoritesActionPresenter : Presenter() {
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
        (viewHolder.view as TextView).text = (item as? FavoritesAction)?.label ?: item?.toString().orEmpty()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
