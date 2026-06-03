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
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.favorites.BookmarkedMediaItem
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.favorites.toJellyfinItem
import net.trequad.quadtv.favorites.toVodItem
import net.trequad.quadtv.jellyfin.JellyfinDetailsFragment
import net.trequad.quadtv.live.BookmarkedLiveChannel
import net.trequad.quadtv.live.LiveChannelBookmarkStore
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.live.currentProgrammesByChannel
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.player.StreamPlaybackRequest
import net.trequad.quadtv.provider.ProviderFeedRefreshCoordinator
import net.trequad.quadtv.provider.ProviderFeedRepository
import net.trequad.quadtv.vod.VodDetailsFragment

data class HomeAction(
    val label: String,
    val route: QuadTvRoute? = null,
    val refreshProviderFeeds: Boolean = false,
    val playbackRequest: StreamPlaybackRequest? = null,
    val mediaItem: BookmarkedMediaItem? = null
)

class HomeFragment : BrowseSupportFragment() {
    private val providerFeedRefreshCoordinator: ProviderFeedRefreshCoordinator by lazy {
        buildProviderFeedRefreshCoordinator()
    }
    private val bookmarkStore: LiveChannelBookmarkStore by lazy {
        LiveChannelBookmarkStore(requireContext().applicationContext)
    }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }
    private val liveTvRepository: LiveTvRepository by lazy { buildLiveTvRepository() }
    private val epgRepository: EpgRepository by lazy { buildEpgRepository() }

    private var onNowRowAdapter: ArrayObjectAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND}"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        searchAffordanceColor = resources.getColor(R.color.quadmedia_blue, null)
        setOnSearchClickedListener {
            (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.MOVIE_SEARCH)
        }
        setOnItemViewClickedListener { _, item, _, _ ->
            val action = item as? HomeAction ?: return@setOnItemViewClickedListener
            if (action.refreshProviderFeeds) {
                showProviderFeedRefreshDialog()
                return@setOnItemViewClickedListener
            }
            action.mediaItem?.let { openMediaDetails(it); return@setOnItemViewClickedListener }
            action.playbackRequest?.let { request ->
                (activity as? QuadTvNavigator)?.navigateToPlayer(request)
                return@setOnItemViewClickedListener
            }
            action.route?.let { route -> (activity as? QuadTvNavigator)?.navigateTo(route) }
        }
        buildAndLoadHomeRows()
    }

    override fun onResume() {
        super.onResume()
        buildAndLoadHomeRows()
    }

    private fun buildAndLoadHomeRows() {
        val onNowAdapter = ArrayObjectAdapter(HomeActionPresenter()).also {
            it.add(HomeAction("Loading what's on now…"))
            onNowRowAdapter = it
        }
        adapter = buildHomeRows(onNowAdapter)
        loadOnNowRow(onNowAdapter)
    }

    private fun buildHomeRows(onNowAdapter: ArrayObjectAdapter): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            // Live TV section — shows On Now channels in the right pane; click header to open full Live TV
            add(ListRow(HeaderItem(0, "Live TV"), onNowAdapter))

            // On-Demand section — browse categories, favorites, recently viewed
            add(ListRow(HeaderItem(1, "On-Demand"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Browse by Category", QuadTvRoute.VOD))
                add(HomeAction("Favorites", QuadTvRoute.FAVORITES))
                add(HomeAction("Recently Viewed", QuadTvRoute.RECENTLY_VIEWED))
                add(HomeAction("Movie Search", QuadTvRoute.MOVIE_SEARCH))
            }))

            // Jellyfin section — open library browser
            add(ListRow(HeaderItem(2, "Jellyfin"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Jellyfin", QuadTvRoute.JELLYFIN))
            }))

            // Continue Watching (only shown when there is history)
            addContinueWatchingRow(rowId = 3)

            // Favorite and recently-viewed live channel shortcuts
            addLiveShortcutRow(rowId = 4, title = "Favorite Live Channels", channels = bookmarkStore.favoriteChannels())
            addLiveShortcutRow(rowId = 5, title = "Recently Viewed Live Channels", channels = bookmarkStore.recentChannels())

            // Settings & utilities
            add(ListRow(HeaderItem(6, "More"), ArrayObjectAdapter(HomeActionPresenter()).apply {
                add(HomeAction("Guide", QuadTvRoute.EPG))
                add(HomeAction("Refresh Playlist & Guide", refreshProviderFeeds = true))
                add(HomeAction("Settings", QuadTvRoute.SETTINGS))
            }))
        }
    }

    private fun loadOnNowRow(onNowAdapter: ArrayObjectAdapter) {
        val liveRepo = liveTvRepository   // initialise on main thread
        val epgRepo = epgRepository
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val channels = liveRepo.loadChannels()
                    val programmes = try { epgRepo.loadProgrammes() } catch (_: Throwable) { emptyList() }
                    channels to programmes
                }
            } catch (_: Throwable) {
                onNowAdapter.clear()
                onNowAdapter.add(HomeAction("Can't reach Live TV — check Wi-Fi", QuadTvRoute.LIVE_TV))
                return@launch
            }
            try {
                val (channels, programmes) = result
                val nowMap = withContext(Dispatchers.Default) { programmes.currentProgrammesByChannel(channels) }
                val onNowItems = channels.filter { nowMap.containsKey(it.id) }.take(8)
                onNowAdapter.clear()
                if (onNowItems.isEmpty()) {
                    onNowAdapter.add(HomeAction("No guide data — open Live TV to browse", QuadTvRoute.LIVE_TV))
                } else {
                    onNowItems.forEach { channel ->
                        val prog = nowMap[channel.id]
                        val label = if (prog?.title != null) "${channel.name}  •  ${prog.title}" else channel.name
                        onNowAdapter.add(HomeAction(label, playbackRequest = StreamPlaybackRequest(
                            url = channel.streamUrl,
                            channelId = channel.id,
                            title = channel.name,
                            groupTitle = channel.groupTitle,
                            contentTitle = prog?.title ?: "Live TV",
                            subtitle = "Live TV",
                            isLive = true
                        )))
                    }
                }
            } catch (_: Throwable) {
                onNowAdapter.clear()
                onNowAdapter.add(HomeAction("Can't load On Now — open Live TV to browse", QuadTvRoute.LIVE_TV))
            }
        }
    }

    private fun ArrayObjectAdapter.addContinueWatchingRow(rowId: Long) {
        val recentMedia = mediaStore.recentItems().take(6)
        val recentLive = bookmarkStore.recentChannels().take(3)
        if (recentMedia.isEmpty() && recentLive.isEmpty()) return
        val rowAdapter = ArrayObjectAdapter(HomeActionPresenter())
        recentMedia.forEach { item ->
            rowAdapter.add(HomeAction(item.title, mediaItem = item))
        }
        recentLive.forEach { channel ->
            rowAdapter.add(HomeAction(channel.name, playbackRequest = channel.toPlaybackRequest()))
        }
        add(ListRow(HeaderItem(rowId, "Continue Watching"), rowAdapter))
    }

    private fun ArrayObjectAdapter.addLiveShortcutRow(rowId: Long, title: String, channels: List<BookmarkedLiveChannel>) {
        val rowAdapter = ArrayObjectAdapter(HomeActionPresenter())
        if (channels.isEmpty()) {
            rowAdapter.add(HomeAction("No ${title.lowercase()} yet", QuadTvRoute.LIVE_TV))
        } else {
            channels.forEach { channel ->
                rowAdapter.add(HomeAction(channel.name, playbackRequest = channel.toPlaybackRequest()))
            }
        }
        add(ListRow(HeaderItem(rowId, title), rowAdapter))
    }

    private fun BookmarkedLiveChannel.toPlaybackRequest(): StreamPlaybackRequest {
        return StreamPlaybackRequest(
            url = streamUrl,
            channelId = id,
            title = name,
            groupTitle = groupTitle,
            contentTitle = contentTitle ?: "Live TV",
            subtitle = "Live TV",
            isLive = true
        )
    }

    private fun openMediaDetails(item: BookmarkedMediaItem) {
        val fragment = when (item.source) {
            BookmarkedMediaSource.VOD -> VodDetailsFragment.newInstance(item.toVodItem())
            BookmarkedMediaSource.JELLYFIN -> JellyfinDetailsFragment.newInstance(item.toJellyfinItem())
        }
        parentFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack("DETAILS")
            .commit()
    }

    private fun showProviderFeedRefreshDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Refreshing Playlist & Guide")
            .setMessage("Fetching the latest channels and programme guide for you…")
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
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
                    "All done! ${result.playlistChannelCount} channels and " +
                        "${result.guideProgrammeCount} guide entries are ready."
                )
            } catch (_: Exception) {
                dialog.setMessage("Something went wrong. Check your Wi-Fi and try again.")
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
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return ProviderFeedRefreshCoordinator(
            liveTvRepository = LiveTvRepository(providerFeedRepository, okHttpClient),
            epgRepository = EpgRepository(providerFeedRepository, okHttpClient)
        )
    }

    private fun buildLiveTvRepository(): LiveTvRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return LiveTvRepository(providerFeedRepository, okHttpClient)
    }

    private fun buildEpgRepository(): EpgRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return EpgRepository(providerFeedRepository, okHttpClient)
    }
}

class HomeActionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.rgb(44, 95, 124))
        }
        // Use captured `view` reference — Leanback wraps the presenter view in
        // ShadowOverlayContainer, so the `v` parameter in the listener is NOT the TextView.
        view.setOnFocusChangeListener { _, hasFocus ->
            view.setBackgroundColor(if (hasFocus) Color.rgb(66, 165, 245) else Color.rgb(44, 95, 124))
            view.textSize = if (hasFocus) 24f else 22f
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? HomeAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            action.label
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
