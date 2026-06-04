package net.trequad.quadtv.jellyfin

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.favorites.BookmarkedMediaItem
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.player.StreamPlaybackRequest

class JellyfinDetailsFragment : Fragment() {
    private val navigator: QuadTvNavigator?
        get() = activity as? QuadTvNavigator
    private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is QuadTvNavigator) {
            "JellyfinDetailsFragment must be hosted by a QuadTvNavigator"
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val item = requireItem()
        mediaStore.recordRecent(item.toMediaBookmark())
        return ScrollView(requireContext()).apply {
            setBackgroundColor(resources.getColor(R.color.quadtv_charcoal, null))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(72, 56, 72, 56)

                val poster = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setBackgroundColor(Color.rgb(28, 50, 66))
                    layoutParams = LinearLayout.LayoutParams(360, 540)
                }
                addView(poster)
                loadPoster(item.posterUrl, poster)

                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(56, 0, 0, 0)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                    addView(TextView(context).apply {
                        text = item.title
                        textSize = 40f
                        setTextColor(Color.WHITE)
                        maxLines = 2
                    })
                    addView(TextView(context).apply {
                        text = listOfNotNull(item.productionYear?.toString(), item.contentRating).joinToString(" • ")
                        textSize = 22f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 12, 0, 24)
                    })
                    addView(TextView(context).apply {
                        text = item.overview?.takeIf { it.isNotBlank() } ?: "No description available."
                        textSize = 21f
                        setTextColor(resources.getColor(R.color.quadtv_white, null))
                        setLineSpacing(4f, 1.05f)
                        setPadding(0, 0, 0, 32)
                    })
                    if (item.isFolder) {
                        addView(TextView(context).apply {
                            text = "Seasons & Episodes"
                            textSize = 24f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(Color.rgb(66, 165, 245))
                            setPadding(0, 0, 0, 16)
                        })
                        showSeriesSeasons(this, item)
                    } else {
                        addView(Button(context).apply {
                            text = "Play"
                            textSize = 24f
                            isFocusable = true
                            setOnClickListener { playJellyfinItem(item) }
                        })
                    }
                    var isFav = mediaStore.isFavorite(item.id, BookmarkedMediaSource.JELLYFIN)
                    addView(Button(context).apply {
                        text = if (isFav) "Remove from Favorites" else "Add to Favorites"
                        textSize = 22f
                        isFocusable = true
                        setPadding(0, 16, 0, 0)
                        setOnClickListener {
                            isFav = mediaStore.toggleFavorite(item.toMediaBookmark())
                            text = if (isFav) "Remove from Favorites" else "Add to Favorites"
                        }
                    })
                })
            })
        }
    }

    private fun showSeriesSeasons(container: LinearLayout, item: JellyfinItem) {
        val loading = TextView(requireContext()).apply {
            text = "Loading seasons…"
            textSize = 18f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, 16)
        }
        container.addView(loading)
        lifecycleScope.launch {
            val seasons = try {
                withContext(Dispatchers.IO) { jellyfinRepository.loadSeasons(item.id) }
            } catch (_: Throwable) { emptyList() }
            container.removeView(loading)
            if (seasons.isEmpty()) {
                container.addView(TextView(requireContext()).apply {
                    text = "No episodes found for this series."
                    textSize = 18f
                    setTextColor(Color.LTGRAY)
                    setPadding(0, 0, 0, 16)
                })
                return@launch
            }
            seasons.forEach { season ->
                container.addView(TextView(requireContext()).apply {
                    text = "Season ${season.seasonNumber}"
                    textSize = 22f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    setPadding(0, 12, 0, 8)
                })
                val episodes = try {
                    withContext(Dispatchers.IO) { jellyfinRepository.loadEpisodes(item.id, season) }
                } catch (_: Throwable) { emptyList() }
                episodes.forEach { episode ->
                    container.addView(Button(requireContext()).apply {
                        text = "Episode ${episode.episodeNumber}: ${episode.title}"
                        textSize = 18f
                        isFocusable = true
                        setOnClickListener { playEpisode(episode) }
                    })
                }
            }
        }
    }

    private fun playEpisode(episode: JellyfinEpisode) {
        lifecycleScope.launch {
            val stream = try {
                withContext(Dispatchers.IO) { jellyfinRepository.buildEpisodeStream(episode) }
            } catch (_: Throwable) { null }
            if (stream == null) {
                android.widget.Toast.makeText(requireContext(), "Can't play this episode right now.", android.widget.Toast.LENGTH_LONG).show()
                return@launch
            }
            navigator?.navigateToPlayer(buildPlaybackRequest(stream))
        }
    }

    private fun playJellyfinItem(item: JellyfinItem) {
        lifecycleScope.launch {
            val stream = try {
                withContext(Dispatchers.IO) { jellyfinRepository.buildHlsStream(item.id) }
            } catch (_: Throwable) { null }
            if (stream == null) {
                android.widget.Toast.makeText(
                    requireContext(),
                    "Can't play — check your Jellyfin URL and API key in the admin portal.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            navigator?.navigateToPlayer(buildPlaybackRequest(stream))
        }
    }

    fun playJellyfinStream(stream: JellyfinStream): Boolean {
        val request = buildPlaybackRequest(stream)
        navigator?.navigateToPlayer(request)
        return true
    }

    fun buildPlaybackRequest(stream: JellyfinStream): StreamPlaybackRequest {
        return StreamPlaybackRequest(
            url = stream.hlsUrl,
            title = stream.title,
            isLive = false,
            subtitle = "Jellyfin",
            nextTitle = "QuadMedia library"
        )
    }

    private fun requireItem(): JellyfinItem {
        val args = requireArguments()
        return JellyfinItem(
            id = requireNotNull(args.getString(ARG_ID)),
            title = requireNotNull(args.getString(ARG_TITLE)),
            posterUrl = args.getString(ARG_POSTER_URL),
            overview = args.getString(ARG_OVERVIEW),
            contentRating = args.getString(ARG_RATING),
            productionYear = if (args.containsKey(ARG_YEAR)) args.getInt(ARG_YEAR) else null,
            isFolder = args.getBoolean(ARG_IS_FOLDER, false),
            isMature = false
        )
    }

    private fun loadPoster(posterUrl: String?, poster: ImageView) {
        if (posterUrl == null) return
        Thread {
            runCatching {
                URL(posterUrl).openStream().use { BitmapFactory.decodeStream(it) }
            }.onSuccess { bitmap ->
                poster.post { poster.setImageBitmap(bitmap) }
            }
        }.start()
    }

    fun JellyfinItem.toMediaBookmark(): BookmarkedMediaItem = BookmarkedMediaItem(
        id = id, title = title, source = BookmarkedMediaSource.JELLYFIN,
        posterUrl = posterUrl, description = overview, rating = contentRating,
        releaseYear = productionYear, streamUrl = null,
        isSeries = false, isMature = isMature
    )

    private fun buildJellyfinRepository(): JellyfinRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val preferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(preferences))
        return JellyfinRepository(configRepository, okHttpClient, moshi)
    }

    companion object {
        private const val ARG_ID = "jellyfin_id"
        private const val ARG_TITLE = "jellyfin_title"
        private const val ARG_POSTER_URL = "jellyfin_poster_url"
        private const val ARG_OVERVIEW = "jellyfin_overview"
        private const val ARG_RATING = "jellyfin_rating"
        private const val ARG_YEAR = "jellyfin_year"
        private const val ARG_IS_FOLDER = "jellyfin_is_folder"

        fun newInstance(item: JellyfinItem): JellyfinDetailsFragment {
            return JellyfinDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, item.id)
                    putString(ARG_TITLE, item.title)
                    putString(ARG_POSTER_URL, item.posterUrl)
                    putString(ARG_OVERVIEW, item.overview)
                    putString(ARG_RATING, item.contentRating)
                    item.productionYear?.let { putInt(ARG_YEAR, it) }
                    putBoolean(ARG_IS_FOLDER, item.isFolder)
                }
            }
        }
    }
}
