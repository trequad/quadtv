package net.trequad.quadtv.vod

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
import java.net.URL
import net.trequad.quadtv.R
import net.trequad.quadtv.favorites.BookmarkedMediaItem
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.player.StreamPlaybackRequest

class VodDetailsFragment : Fragment() {
    private val navigator: QuadTvNavigator?
        get() = activity as? QuadTvNavigator
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is QuadTvNavigator) {
            "VodDetailsFragment must be hosted by a QuadTvNavigator"
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
                    contentDescription = "Poster art"
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
                        text = "QuadTV Details"
                        textSize = 18f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 0, 0, 10)
                    })
                    addView(TextView(context).apply {
                        text = item.title
                        textSize = 40f
                        setTextColor(Color.WHITE)
                        maxLines = 2
                    })
                    addView(TextView(context).apply {
                        text = "Metadata"
                        textSize = 16f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 8, 0, 0)
                    })
                    addView(TextView(context).apply {
                        text = listOfNotNull(item.releaseYear?.toString(), item.rating).joinToString(" • ")
                        textSize = 22f
                        setTextColor(Color.LTGRAY)
                        setPadding(0, 12, 0, 24)
                    })
                    addView(TextView(context).apply {
                        text = item.description?.takeIf { it.isNotBlank() } ?: "No description available."
                        textSize = 21f
                        setTextColor(resources.getColor(R.color.quadtv_white, null))
                        setLineSpacing(4f, 1.05f)
                        setPadding(0, 0, 0, 32)
                    })
                    addView(Button(context).apply {
                        text = "Play"
                        textSize = 24f
                        isFocusable = true
                        isEnabled = item.streamUrl != null
                        setOnClickListener { playVodItem(item) }
                    })
                    var isFav = mediaStore.isFavorite(item.id, BookmarkedMediaSource.VOD)
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

    fun playVodItem(item: VodItem): Boolean {
        // Playback handoff: details screen builds a StreamPlaybackRequest and lets MainActivity open PlayerFragment.
        val request = buildPlaybackRequest(item) ?: return false
        navigator?.navigateToPlayer(request)
        return true
    }

    fun buildPlaybackRequest(item: VodItem): StreamPlaybackRequest? {
        val url = item.streamUrl ?: return null
        return StreamPlaybackRequest(
            url = url,
            title = item.title,
            isLive = false,
            subtitle = "On-Demand",
            nextTitle = item.rating ?: item.releaseYear?.toString() ?: "QuadTV VOD"
        )
    }

    private fun requireItem(): VodItem {
        val args = requireArguments()
        return VodItem(
            id = requireNotNull(args.getString(ARG_ID)),
            title = requireNotNull(args.getString(ARG_TITLE)),
            posterUrl = args.getString(ARG_POSTER_URL),
            description = args.getString(ARG_DESCRIPTION),
            rating = args.getString(ARG_RATING),
            releaseYear = if (args.containsKey(ARG_YEAR)) args.getInt(ARG_YEAR) else null,
            streamUrl = args.getString(ARG_STREAM_URL),
            isSeries = args.getBoolean(ARG_IS_SERIES, false),
            isMature = args.getBoolean(ARG_IS_MATURE, false)
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

    fun VodItem.toMediaBookmark(): BookmarkedMediaItem = BookmarkedMediaItem(
        id = id, title = title, source = BookmarkedMediaSource.VOD,
        posterUrl = posterUrl, description = description, rating = rating,
        releaseYear = releaseYear, streamUrl = streamUrl,
        isSeries = isSeries, isMature = isMature
    )

    companion object {
        private const val ARG_ID = "vod_id"
        private const val ARG_TITLE = "vod_title"
        private const val ARG_POSTER_URL = "vod_poster_url"
        private const val ARG_DESCRIPTION = "vod_description"
        private const val ARG_RATING = "vod_rating"
        private const val ARG_YEAR = "vod_year"
        private const val ARG_STREAM_URL = "vod_stream_url"
        private const val ARG_IS_SERIES = "vod_is_series"
        private const val ARG_IS_MATURE = "vod_is_mature"

        fun newInstance(item: VodItem): VodDetailsFragment {
            return VodDetailsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ID, item.id)
                    putString(ARG_TITLE, item.title)
                    putString(ARG_POSTER_URL, item.posterUrl)
                    putString(ARG_DESCRIPTION, item.description)
                    putString(ARG_RATING, item.rating)
                    item.releaseYear?.let { putInt(ARG_YEAR, it) }
                    putString(ARG_STREAM_URL, item.streamUrl)
                    putBoolean(ARG_IS_SERIES, item.isSeries)
                    putBoolean(ARG_IS_MATURE, item.isMature)
                }
            }
        }
    }
}
