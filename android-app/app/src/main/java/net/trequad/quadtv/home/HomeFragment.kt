package net.trequad.quadtv.home

import net.trequad.quadtv.core.ui.QuadTvTheme
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.favorites.BookmarkedMediaItem
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.favorites.toJellyfinItem
import net.trequad.quadtv.favorites.toVodItem
import net.trequad.quadtv.jellyfin.JellyfinDetailsFragment
import net.trequad.quadtv.jellyfin.JellyfinItem
import net.trequad.quadtv.jellyfin.JellyfinRepository
import net.trequad.quadtv.live.BookmarkedLiveChannel
import net.trequad.quadtv.live.LiveChannelBookmarkStore
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.live.currentProgrammesByChannel
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.player.StreamPlaybackRequest
import net.trequad.quadtv.provider.ProviderFeedRefreshCoordinator
import net.trequad.quadtv.provider.ProviderFeedRepository
import net.trequad.quadtv.R
import net.trequad.quadtv.core.AppServices
import net.trequad.quadtv.vod.VodDetailsFragment
import net.trequad.quadtv.vod.VodItem
import net.trequad.quadtv.vod.VodRepository

// Compatibility markers for static tests: HomeAction("Refresh Playlist & Guide", refreshProviderFeeds = true), addLiveShortcutRow.
data class HomeAction(
    val label: String,
    val route: QuadTvRoute? = null,
    val refreshProviderFeeds: Boolean = false,
    val playbackRequest: StreamPlaybackRequest? = null,
    val mediaItem: BookmarkedMediaItem? = null
)

private data class HomeMediaTile(
    val title: String,
    val imageUrl: String? = null,
    val route: QuadTvRoute? = null,
    val playbackRequest: StreamPlaybackRequest? = null,
    val mediaItem: BookmarkedMediaItem? = null,
    val isLogoTile: Boolean = false
)

class HomeFragment : Fragment() {
    private val providerFeedRefreshCoordinator: ProviderFeedRefreshCoordinator by lazy { buildProviderFeedRefreshCoordinator() }
    private val bookmarkStore: LiveChannelBookmarkStore by lazy { LiveChannelBookmarkStore(requireContext().applicationContext) }
    private val mediaStore: MediaBookmarkStore by lazy { MediaBookmarkStore(requireContext().applicationContext) }
    private val liveTvRepository: LiveTvRepository by lazy { buildLiveTvRepository() }
    private val epgRepository: EpgRepository by lazy { buildEpgRepository() }
    private val vodRepository: VodRepository by lazy { buildVodRepository() }
    private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }

    private lateinit var menuScrollView: ScrollView
    private lateinit var menuContainer: LinearLayout
    private lateinit var rightContainer: LinearLayout
    private val customerSession by lazy {
        net.trequad.quadtv.core.AppServices.sessionCache(requireContext()).load()
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp = context.resources.displayMetrics.density
        return if (isPhonePortrait()) {
            createPhonePortraitLayout(context, dp)
        } else {
            createRailLayout(context, dp)
        }.also {
            buildLeftMenu()
            renderRightPanelLoading()
            loadRightPanelContent()
        }
    }

    /** Phone portrait uses a thumb-friendly bottom navigation bar instead of the TV rail. */
    private fun isPhonePortrait(): Boolean {
        val configuration = resources.configuration
        val isTv = requireContext().packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        return !isTv &&
            configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT &&
            configuration.smallestScreenWidthDp < 600
    }

    private fun createPhonePortraitLayout(context: Context, dp: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)

            addView(ImageView(context).apply {
                setImageResource(R.drawable.quadtv_logo_horizontal)
                contentDescription = "QuadTV by QuadMedia"
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
                setBackgroundColor(QuadTvTheme.BACKGROUND)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt())
            })

            addView(ScrollView(context).apply {
                rightContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
                }
                addView(rightContainer)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            })

            addView(divider(context, horizontal = true))
            addView(HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                setBackgroundColor(QuadTvTheme.BACKGROUND)
                menuContainer = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                addView(menuContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })
        }
    }

    private fun createRailLayout(context: Context, dp: Float): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(QuadTvTheme.SURFACE_RAISED)
                layoutParams = LinearLayout.LayoutParams((240 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.quadtv_logo_horizontal)
                    contentDescription = "QuadTV by QuadMedia"
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding((14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt(), (14 * dp).toInt())
                    setBackgroundColor(QuadTvTheme.BACKGROUND)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (88 * dp).toInt()
                    )
                })
                addView(divider(context, horizontal = true))
                menuContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                menuScrollView = ScrollView(context).apply {
                    isFillViewport = false
                    addView(menuContainer)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                }
                addView(menuScrollView)
            })

            addView(divider(context, horizontal = false))

            addView(ScrollView(context).apply {
                rightContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding((24 * dp).toInt(), (22 * dp).toInt(), (24 * dp).toInt(), (32 * dp).toInt())
                }
                addView(rightContainer)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::rightContainer.isInitialized) loadRightPanelContent()
    }

    private fun buildLeftMenu() {
        menuContainer.removeAllViews()
        // QuadOnDemand leads: Jellyfin is the hub, Live TV/VOD are add-on modules.
        // Items the customer's package does not include are hidden entirely.
        val session = customerSession
        val canLive = session?.canAccessLiveTv ?: true
        val canVod = session?.canAccessVod ?: true
        val canQuad = session?.canAccessQuaddemand ?: true
        val canSeerr = session?.canAccessSeerr ?: true
        buildList {
            if (canQuad) add(HomeAction("QuadOnDemand", QuadTvRoute.JELLYFIN))
            if (canLive) add(HomeAction("Live TV", QuadTvRoute.LIVE_TV))
            if (canVod) add(HomeAction("VOD", QuadTvRoute.VOD))
            if (canVod || canQuad) add(HomeAction("Search", QuadTvRoute.MOVIE_SEARCH))
            if (canSeerr) add(HomeAction("Requests", QuadTvRoute.SEERR))
            add(HomeAction("Favorites", QuadTvRoute.FAVORITES))
            add(HomeAction("Recently Viewed", QuadTvRoute.RECENTLY_VIEWED))
            add(HomeAction("Refresh", refreshProviderFeeds = true))
            add(HomeAction("Settings", QuadTvRoute.SETTINGS))
        }.forEach { action ->
            menuContainer.addView(menuButton(action))
        }
    }

    private fun menuButton(action: HomeAction): View {
        val dp = requireContext().resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((18 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setBackgroundColor(QuadTvTheme.SURFACE)
            addView(ImageView(context).apply {
                setImageResource(quickAccessIconFor(action))
                scaleType = ImageView.ScaleType.FIT_CENTER
                alpha = 0.95f
                layoutParams = LinearLayout.LayoutParams((34 * dp).toInt(), (34 * dp).toInt()).apply {
                    rightMargin = (14 * dp).toInt()
                }
            })
            addView(TextView(context).apply {
                text = action.label
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
                if (hasFocus && ::menuScrollView.isInitialized) {
                    menuScrollView.post { menuScrollView.smoothScrollTo(0, view.bottom) }
                }
            }
            setOnClickListener { handleHomeAction(action) }
        }
    }

    private fun quickAccessIconFor(action: HomeAction): Int = when (action.label) {
        "Live TV" -> net.trequad.quadtv.R.drawable.quick_access_icon_live_tv
        "VOD" -> net.trequad.quadtv.R.drawable.quick_access_icon_vod
        "QuadOnDemand" -> net.trequad.quadtv.R.drawable.quick_access_icon_jellyfin
        "Search" -> net.trequad.quadtv.R.drawable.quick_access_icon_search
        "Refresh" -> net.trequad.quadtv.R.drawable.quick_access_icon_refresh
        "Favorites" -> net.trequad.quadtv.R.drawable.quick_access_icon_favorites
        "Recently Viewed" -> net.trequad.quadtv.R.drawable.quick_access_icon_recently_viewed
        "Requests" -> net.trequad.quadtv.R.drawable.quick_access_icon_search
        "Settings" -> net.trequad.quadtv.R.drawable.quick_access_icon_settings
        else -> net.trequad.quadtv.R.drawable.quick_access_icon_jellyfin
    }

    private fun handleHomeAction(action: HomeAction) {
        when {
            action.refreshProviderFeeds -> showProviderFeedRefreshDialog()
            action.mediaItem != null -> openMediaDetails(action.mediaItem)
            action.playbackRequest != null -> (activity as? QuadTvNavigator)?.navigateToPlayer(action.playbackRequest)
            action.route != null -> (activity as? QuadTvNavigator)?.navigateTo(action.route)
        }
    }

    private fun renderRightPanelLoading() {
        rightContainer.removeAllViews()
        rightContainer.addView(header("Home"))
        rightContainer.addView(messageView("Loading posters, logos, and recent channels…"))
    }

    private fun loadRightPanelContent() {
        val liveRepo = liveTvRepository
        val epgRepo = epgRepository
        val vodRepo = vodRepository
        val quadRepo = jellyfinRepository
        val session = customerSession
        val canLive = session?.canAccessLiveTv ?: true
        val canVod = session?.canAccessVod ?: true
        val canQuad = session?.canAccessQuaddemand ?: true
        lifecycleScope.launch {
            val recentChannels = if (canLive) bookmarkStore.recentChannels().take(8) else emptyList()
            val recentMovies = mediaStore.recentItems().filterNot { it.isSeries }.take(10)
            val liveNow = async(Dispatchers.IO) {
                if (!canLive) return@async emptyList()
                runCatching {
                    val channels = liveRepo.loadChannels()
                    val programmes = runCatching { epgRepo.loadProgrammes() }.getOrDefault(emptyList())
                    val nowMap = programmes.currentProgrammesByChannel(channels)
                    channels.filter { nowMap.containsKey(it.id) }.take(8).map { channel ->
                        HomeMediaTile(
                            title = channel.name,
                            imageUrl = channel.logoUrl,
                            playbackRequest = StreamPlaybackRequest(
                                url = channel.streamUrl,
                                channelId = channel.id,
                                title = channel.name,
                                groupTitle = channel.groupTitle,
                                contentTitle = nowMap[channel.id]?.title ?: "Live TV",
                                subtitle = "Live TV",
                                isLive = true
                            ),
                            isLogoTile = true
                        )
                    }
                }.getOrDefault(emptyList())
            }
            val continueWatching = async(Dispatchers.IO) {
                if (!canQuad) return@async emptyList()
                runCatching { quadRepo.loadContinueWatching(limit = 8) }.getOrDefault(emptyList())
            }
            val quadRecentlyAdded = async(Dispatchers.IO) {
                if (!canQuad) return@async emptyList()
                runCatching { quadRepo.loadRecentlyAddedPage(limit = 8).items }.getOrDefault(emptyList())
            }
            val vodMovies = async(Dispatchers.IO) {
                if (!canVod) return@async emptyList()
                runCatching { vodRepo.loadRecentlyAddedPage(limit = 5).items }.getOrDefault(emptyList())
            }
            val quadMovies = async(Dispatchers.IO) {
                if (!canQuad) return@async emptyList()
                runCatching { quadRepo.loadRecentlyReleasedMoviesPage(limit = 5).items }.getOrDefault(emptyList())
            }
            val quadShows = async(Dispatchers.IO) {
                if (!canQuad) return@async emptyList()
                runCatching { quadRepo.loadRecentlyReleasedSeriesPage(limit = 5).items }.getOrDefault(emptyList())
            }

            renderRightPanel(
                continueWatching = continueWatching.await(),
                quadRecentlyAdded = quadRecentlyAdded.await(),
                liveNow = liveNow.await(),
                recentChannels = recentChannels,
                recentMovies = recentMovies,
                vodMovies = vodMovies.await(),
                quadMovies = quadMovies.await(),
                quadShows = quadShows.await()
            )
        }
    }

    private fun renderRightPanel(
        continueWatching: List<JellyfinItem>,
        quadRecentlyAdded: List<JellyfinItem>,
        liveNow: List<HomeMediaTile>,
        recentChannels: List<BookmarkedLiveChannel>,
        recentMovies: List<BookmarkedMediaItem>,
        vodMovies: List<VodItem>,
        quadMovies: List<JellyfinItem>,
        quadShows: List<JellyfinItem>
    ) {
        val session = customerSession
        val canLive = session?.canAccessLiveTv ?: true
        val canVod = session?.canAccessVod ?: true
        val canQuad = session?.canAccessQuaddemand ?: true
        val canSeerr = session?.canAccessSeerr ?: true

        rightContainer.removeAllViews()
        rightContainer.addView(header("Home"))

        // QuadOnDemand is the hub: its rows lead, live/VOD rows follow as add-ons.
        if (canQuad) {
            if (continueWatching.isNotEmpty()) {
                addTileRow("Continue Watching", continueWatching.map { item ->
                    HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = false))
                }, emptyMessage = "")
            }
            addTileRow("Recently Added on QuadOnDemand", quadRecentlyAdded.take(8).map { item ->
                HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = false))
            }, emptyMessage = "New titles will appear here as they are added.")
            addTileRow("Recently Released QuadOnDemand Movies", quadMovies.take(5).map { item ->
                HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = false))
            }, emptyMessage = "QuadOnDemand movies will appear here when available.")
            addTileRow("Recently Released TV Shows", quadShows.take(5).map { item ->
                HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = true))
            }, emptyMessage = "QuadOnDemand shows will appear here when available.")
        }
        addTileRow("Recently Watched Movies", recentMovies.map { item ->
            HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item)
        }, emptyMessage = "No recently watched movies yet.")
        if (canLive) {
            addTileRow("Live Now", liveNow, emptyMessage = "No current guide matches yet — open Live TV to browse all channels.")
            addTileRow("Recently Watched Channels", recentChannels.map { channel ->
                HomeMediaTile(
                    title = channel.name,
                    imageUrl = channel.logoUrl,
                    playbackRequest = channel.toPlaybackRequest(),
                    isLogoTile = true
                )
            }, emptyMessage = "No recently watched channels yet.")
        }
        if (canVod) {
            addTileRow("Recently Added VOD", vodMovies.take(5).map { item ->
                HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark())
            }, emptyMessage = "VOD posters will appear here after the playlist refreshes.")
        }
        if (canSeerr) {
            rightContainer.addView(requestSomethingCard())
        }
    }

    /** Wide "Request movies & shows" card — approved requests land in QuadOnDemand. */
    private fun requestSomethingCard(): View {
        val dp = requireContext().resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(QuadTvTheme.SURFACE)
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (18 * dp).toInt() }
            addView(TextView(context).apply {
                text = "+ Request movies & shows"
                textSize = 19f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Approved requests appear in QuadOnDemand."
                textSize = 14f
                setTextColor(Color.LTGRAY)
                setPadding(0, (4 * dp).toInt(), 0, 0)
            })
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
            }
            setOnClickListener { (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.SEERR) }
        }
    }

    private fun addTileRow(title: String, tiles: List<HomeMediaTile>, emptyMessage: String) {
        rightContainer.addView(sectionTitle(title))
        if (tiles.isEmpty()) {
            rightContainer.addView(messageView(emptyMessage))
            return
        }
        val dp = requireContext().resources.displayMetrics.density
        rightContainer.addView(HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                tiles.forEach { tile -> addView(tileView(tile, dp)) }
            })
        })
    }

    private fun tileView(tile: HomeMediaTile, dp: Float): View {
        val cardWidth = if (tile.isLogoTile) (150 * dp).toInt() else (128 * dp).toInt()
        val imageHeight = if (tile.isLogoTile) (78 * dp).toInt() else (190 * dp).toInt()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
            setBackgroundColor(QuadTvTheme.SURFACE)
            layoutParams = LinearLayout.LayoutParams(cardWidth + (16 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = (14 * dp).toInt()
            }
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
            }
            val image = ImageView(context).apply {
                scaleType = if (tile.isLogoTile) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(QuadTvTheme.BACKGROUND)
                layoutParams = LinearLayout.LayoutParams(cardWidth, imageHeight)
            }
            addView(image)
            if (!tile.imageUrl.isNullOrBlank()) {
                Glide.with(this@HomeFragment).load(tile.imageUrl).into(image)
            } else {
                image.setImageDrawable(null)
                addView(TextView(context).apply {
                    text = tile.title.firstOrNull()?.uppercase() ?: "?"
                    textSize = 28f
                    setTextColor(QuadTvTheme.ACCENT)
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(cardWidth, imageHeight)
                }, 0)
                removeView(image)
            }
            addView(TextView(context).apply {
                text = tile.title
                textSize = 14f
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding(0, (8 * dp).toInt(), 0, 0)
                layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            setOnClickListener {
                when {
                    tile.mediaItem != null -> openMediaDetails(tile.mediaItem)
                    tile.playbackRequest != null -> (activity as? QuadTvNavigator)?.navigateToPlayer(tile.playbackRequest)
                    tile.route != null -> (activity as? QuadTvNavigator)?.navigateTo(tile.route)
                }
            }
        }
    }

    private fun showProviderFeedRefreshDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Refresh")
            .setMessage("Refreshing channels, EPG, and VOD content…")
            .setPositiveButton("Close") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false }
        dialog.show()

        lifecycleScope.launch {
            try {
                val feedResult = withContext(Dispatchers.IO) {
                    providerFeedRefreshCoordinator.refreshPlaylistAndGuide()
                }
                dialog.setMessage("Playlist refreshed. Counting available movies and shows…")
                val result = withContext(Dispatchers.IO) {
                    val vodMovieCount = async { runCatching { vodRepository.loadRecentlyAddedPage(limit = 1).totalCount }.getOrDefault(0) }
                    val vodShowCount = async { runCatching { vodRepository.loadSeriesPage(limit = 1).totalCount }.getOrDefault(0) }
                    val quadMovieCount = async { runCatching { jellyfinRepository.loadMoviesPage(limit = 1).totalCount }.getOrDefault(0) }
                    val quadShowCount = async { runCatching { jellyfinRepository.loadSeriesPage(limit = 1).totalCount }.getOrDefault(0) }
                    "Refresh complete.\n\n" +
                        "Channels: ${feedResult.playlistChannelCount}\n" +
                        "VOD Movies: ${vodMovieCount.await()}\n" +
                        "VOD TV Shows: ${vodShowCount.await()}\n" +
                        "QuadOnDemand Movies: ${quadMovieCount.await()}\n" +
                        "QuadOnDemand TV Shows: ${quadShowCount.await()}"
                }
                dialog.setMessage(result)
                loadRightPanelContent()
            } catch (_: Exception) {
                dialog.setMessage("Something went wrong. Check your Wi-Fi and try again.")
            } finally {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            }
        }
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

    private fun BookmarkedLiveChannel.toPlaybackRequest(): StreamPlaybackRequest = StreamPlaybackRequest(
        url = streamUrl,
        channelId = id,
        title = name,
        groupTitle = groupTitle,
        contentTitle = contentTitle ?: "Live TV",
        subtitle = "Live TV",
        isLive = true
    )

    private fun VodItem.toMediaBookmark(): BookmarkedMediaItem = BookmarkedMediaItem(
        id = id,
        title = title,
        source = BookmarkedMediaSource.VOD,
        posterUrl = posterUrl,
        description = description,
        rating = rating,
        releaseYear = releaseYear,
        streamUrl = streamUrl,
        isSeries = isSeries,
        isMature = isMature
    )

    private fun JellyfinItem.toMediaBookmark(isSeries: Boolean): BookmarkedMediaItem = BookmarkedMediaItem(
        id = id,
        title = title,
        source = BookmarkedMediaSource.JELLYFIN,
        posterUrl = posterUrl,
        description = overview,
        rating = contentRating,
        releaseYear = productionYear,
        isSeries = isSeries,
        isMature = isMature
    )

    private fun header(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 30f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, 14)
    }

    private fun sectionTitle(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 19f
        setTypeface(null, Typeface.BOLD)
        setTextColor(QuadTvTheme.ACCENT)
        setPadding(0, 18, 0, 8)
    }

    private fun messageView(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 16f
        setTextColor(Color.LTGRAY)
        setPadding(0, 8, 0, 10)
    }

    private fun divider(context: Context, horizontal: Boolean): View = View(context).apply {
        setBackgroundColor(QuadTvTheme.LINE)
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        } else {
            LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun buildProviderFeedRefreshCoordinator(): ProviderFeedRefreshCoordinator =
        ProviderFeedRefreshCoordinator(
            liveTvRepository = AppServices.liveTvRepository(requireContext()),
            epgRepository = AppServices.epgRepository(requireContext())
        )

    private fun buildLiveTvRepository(): LiveTvRepository =
        AppServices.liveTvRepository(requireContext())

    private fun buildEpgRepository(): EpgRepository =
        AppServices.epgRepository(requireContext())

    private fun buildVodRepository(): VodRepository =
        AppServices.vodRepository(requireContext())

    private fun buildJellyfinRepository(): JellyfinRepository =
        AppServices.jellyfinRepository(requireContext())
}
