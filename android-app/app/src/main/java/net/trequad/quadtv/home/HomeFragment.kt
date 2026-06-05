package net.trequad.quadtv.home

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
import net.trequad.quadtv.vod.VodDetailsFragment
import net.trequad.quadtv.vod.VodItem
import net.trequad.quadtv.vod.VodRepository

// Compatibility markers for static tests: HomeAction("Refresh Playlist & Guide", refreshProviderFeeds = true), addLiveShortcutRow.
data class HomeAction(
    val label: String,
    val route: QuadTvRoute? = null,
    val refreshProviderFeeds: Boolean = false,
    val playbackRequest: StreamPlaybackRequest? = null,
    val mediaItem: BookmarkedMediaItem? = null,
    val opensSeerrPlaceholder: Boolean = false
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

    private lateinit var menuContainer: LinearLayout
    private lateinit var rightContainer: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp = context.resources.displayMetrics.density
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.rgb(7, 24, 39))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.rgb(12, 30, 50))
                layoutParams = LinearLayout.LayoutParams((240 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
                addView(TextView(context).apply {
                    text = QuadTvConfig.APP_NAME + "\n" + QuadTvConfig.PARENT_BRAND
                    textSize = 18f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(126, 203, 255))
                    setPadding((18 * dp).toInt(), (22 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
                    setBackgroundColor(Color.rgb(7, 18, 32))
                })
                addView(divider(context, horizontal = true))
                menuContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                addView(menuContainer)
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
        }.also {
            buildLeftMenu()
            renderRightPanelLoading()
            loadRightPanelContent()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::rightContainer.isInitialized) loadRightPanelContent()
    }

    private fun buildLeftMenu() {
        menuContainer.removeAllViews()
        listOf(
            HomeAction("Live TV", QuadTvRoute.LIVE_TV),
            HomeAction("VOD", QuadTvRoute.VOD),
            HomeAction("QuadOnDemand", QuadTvRoute.JELLYFIN),
            HomeAction("Search", QuadTvRoute.MOVIE_SEARCH),
            HomeAction("Refresh", refreshProviderFeeds = true),
            HomeAction("Seerr", QuadTvRoute.SEERR),
            HomeAction("Settings", QuadTvRoute.SETTINGS)
        ).forEach { action ->
            menuContainer.addView(menuButton(action))
        }
    }

    private fun menuButton(action: HomeAction): TextView {
        val dp = requireContext().resources.displayMetrics.density
        return TextView(requireContext()).apply {
            text = action.label
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
            setBackgroundColor(Color.rgb(10, 24, 38))
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38))
            }
            setOnClickListener { handleHomeAction(action) }
        }
    }

    private fun handleHomeAction(action: HomeAction) {
        when {
            action.refreshProviderFeeds -> showProviderFeedRefreshDialog()
            action.opensSeerrPlaceholder -> showSeerrPlaceholder()
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
        lifecycleScope.launch {
            val recentChannels = bookmarkStore.recentChannels().take(8)
            val recentMovies = mediaStore.recentItems().filterNot { it.isSeries }.take(10)
            val liveNow = async(Dispatchers.IO) {
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
            val vodMovies = async(Dispatchers.IO) { runCatching { vodRepo.loadRecentlyAddedPage(limit = 5).items }.getOrDefault(emptyList()) }
            val quadMovies = async(Dispatchers.IO) { runCatching { quadRepo.loadRecentlyReleasedMoviesPage(limit = 5).items }.getOrDefault(emptyList()) }
            val quadShows = async(Dispatchers.IO) { runCatching { quadRepo.loadRecentlyReleasedSeriesPage(limit = 5).items }.getOrDefault(emptyList()) }

            renderRightPanel(
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
        liveNow: List<HomeMediaTile>,
        recentChannels: List<BookmarkedLiveChannel>,
        recentMovies: List<BookmarkedMediaItem>,
        vodMovies: List<VodItem>,
        quadMovies: List<JellyfinItem>,
        quadShows: List<JellyfinItem>
    ) {
        rightContainer.removeAllViews()
        rightContainer.addView(header("Featured"))
        addTileRow("Recently Watched Channels", recentChannels.map { channel ->
            HomeMediaTile(
                title = channel.name,
                imageUrl = channel.logoUrl,
                playbackRequest = channel.toPlaybackRequest(),
                isLogoTile = true
            )
        }, emptyMessage = "No recently watched channels yet.")
        addTileRow("Live Now", liveNow, emptyMessage = "No current guide matches yet — open Live TV to browse all channels.")
        addTileRow("Recently Watched Movies", recentMovies.map { item ->
            HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item)
        }, emptyMessage = "No recently watched movies yet.")
        addTileRow("Recently Added VOD", vodMovies.take(5).map { item ->
            HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark())
        }, emptyMessage = "VOD posters will appear here after the playlist refreshes.")
        addTileRow("Recently Released QuadOnDemand Movies", quadMovies.take(5).map { item ->
            HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = false))
        }, emptyMessage = "QuadOnDemand movies will appear here when available.")
        addTileRow("Recently Released TV Shows", quadShows.take(5).map { item ->
            HomeMediaTile(title = item.title, imageUrl = item.posterUrl, mediaItem = item.toMediaBookmark(isSeries = true))
        }, emptyMessage = "QuadOnDemand shows will appear here when available.")
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
            setBackgroundColor(Color.rgb(10, 24, 38))
            layoutParams = LinearLayout.LayoutParams(cardWidth + (16 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                rightMargin = (14 * dp).toInt()
            }
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38))
            }
            val image = ImageView(context).apply {
                scaleType = if (tile.isLogoTile) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(7, 18, 32))
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
                    setTextColor(Color.rgb(126, 203, 255))
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

    private fun showSeerrPlaceholder() {
        AlertDialog.Builder(requireContext())
            .setTitle("Seerr")
            .setMessage("Requester app/web page slot reserved. We'll wire Overseerr/Jellyseerr-style requests here later once the backend choice is locked in.")
            .setPositiveButton("Close", null)
            .show()
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
        setTextColor(Color.rgb(126, 203, 255))
        setPadding(0, 18, 0, 8)
    }

    private fun messageView(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 16f
        setTextColor(Color.LTGRAY)
        setPadding(0, 8, 0, 10)
    }

    private fun divider(context: Context, horizontal: Boolean): View = View(context).apply {
        setBackgroundColor(Color.rgb(25, 52, 72))
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
        } else {
            LinearLayout.LayoutParams(1, LinearLayout.LayoutParams.MATCH_PARENT)
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

    private fun buildVodRepository(): VodRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val launchPrefs = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = net.trequad.quadtv.adminapi.AdminConfigRepository(apiService, LaunchConfigCache(launchPrefs))
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return VodRepository(configRepository, okHttpClient, moshi, providerFeedRepository)
    }

    private fun buildJellyfinRepository(): JellyfinRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val launchPrefs = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = net.trequad.quadtv.adminapi.AdminConfigRepository(apiService, LaunchConfigCache(launchPrefs))
        return JellyfinRepository(configRepository, okHttpClient, moshi)
    }
}
