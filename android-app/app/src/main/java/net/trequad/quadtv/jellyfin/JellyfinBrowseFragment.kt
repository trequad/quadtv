package net.trequad.quadtv.jellyfin

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore

class JellyfinBrowseFragment : Fragment() {
    private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    private lateinit var sectionContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var contentHeader: TextView

    private val cachedItems = mutableMapOf<String, List<JellyfinItem>>()
    private var selectedSection = SECTION_MOVIES

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.rgb(7, 24, 39))
        val dp = context.resources.displayMetrics.density
        val navWidth = (280 * dp).toInt()

        // Left pane — section navigation
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 30, 50))
            layoutParams = LinearLayout.LayoutParams(navWidth, LinearLayout.LayoutParams.MATCH_PARENT)

            // Left pane header
            addView(TextView(context).apply {
                text = "Jellyfin"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(66, 165, 245))
                setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                setBackgroundColor(Color.rgb(7, 18, 32))
            })

            // Divider below header
            addView(android.view.View(context).apply {
                setBackgroundColor(Color.rgb(44, 95, 124))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                )
            })

            sectionContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(ScrollView(context).apply {
                addView(sectionContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            })
        })

        // Vertical divider between panes
        addView(android.view.View(context).apply {
            setBackgroundColor(Color.rgb(44, 95, 124))
            layoutParams = LinearLayout.LayoutParams((2 * dp).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
        })

        // Right pane — content
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

            // Right pane header row with title + search
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
                setBackgroundColor(Color.rgb(7, 18, 32))
                contentHeader = TextView(context).apply {
                    text = "Movies"
                    textSize = 22f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(contentHeader)
                addView(TextView(context).apply {
                    text = "🔍 Search"
                    textSize = 16f
                    setTextColor(Color.rgb(66, 165, 245))
                    setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener { showSearchDialog() }
                })
            })

            // Divider below right pane header
            addView(android.view.View(context).apply {
                setBackgroundColor(Color.rgb(44, 95, 124))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, (2 * dp).toInt()
                )
            })

            contentContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(ScrollView(context).apply {
                addView(contentContainer)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
                )
            })
        })
    }.also {
        showContentMessage("Loading Jellyfin library…")
        loadNav()
    }

    private fun loadNav() {
        val repo = jellyfinRepository
        val store = mediaStore
        lifecycleScope.launch {
            val (movies, series) = try {
                withContext(Dispatchers.IO) { repo.loadMovies() to repo.loadSeries() }
            } catch (_: Throwable) {
                showContentMessage("Can't load Jellyfin right now — check Wi-Fi and try again.")
                return@launch
            }
            cachedItems[SECTION_MOVIES] = movies
            cachedItems[SECTION_SHOWS] = series

            sectionContainer.removeAllViews()
            sectionContainer.addView(sectionLabel("Jellyfin"))
            addSectionButton("Movies  (${movies.size})", SECTION_MOVIES, selected = true)
            addSectionButton("TV Shows  (${series.size})", SECTION_SHOWS)

            showItems(movies, "Movies", store)
        }
    }

    private fun addSectionButton(label: String, sectionId: String, selected: Boolean = false) {
        val dp = requireContext().resources.displayMetrics.density
        val btn = TextView(requireContext()).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setBackgroundColor(if (selected) Color.rgb(44, 95, 124) else Color.rgb(12, 30, 50))
            val view = this
            setOnFocusChangeListener { _, hasFocus ->
                view.setBackgroundColor(
                    if (hasFocus || sectionId == selectedSection) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38)
                )
            }
            setOnClickListener {
                selectedSection = sectionId
                refreshSectionHighlights()
                val name = if (sectionId == SECTION_MOVIES) "Movies" else "TV Shows"
                contentHeader.text = name
                val store = mediaStore
                val items = cachedItems[sectionId] ?: emptyList()
                showItems(items, name, store)
            }
        }
        sectionContainer.addView(btn)
    }

    private fun showItems(items: List<JellyfinItem>, sectionName: String, store: MediaBookmarkStore) {
        contentContainer.removeAllViews()
        if (items.isEmpty()) {
            contentContainer.addView(infoText("No $sectionName found. Check your Jellyfin URL and API key in the admin portal."))
            return
        }
        val dp = requireContext().resources.displayMetrics.density
        val cardWidth = (150 * dp).toInt()
        val cardMargin = (6 * dp).toInt()
        val navPaneWidth = (280 * dp).toInt()
        val displayWidth = requireContext().resources.displayMetrics.widthPixels
        val columns = maxOf(2, (displayWidth - navPaneWidth) / (cardWidth + cardMargin * 2))

        items.sortedBy { it.title }.chunked(columns).forEach { rowItems ->
            contentContainer.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                rowItems.forEach { item ->
                    val fav = runCatching {
                        store.isFavorite(item.id, BookmarkedMediaSource.JELLYFIN)
                    }.getOrDefault(false)
                    addView(itemRow(item, fav, cardWidth, cardMargin))
                }
            })
        }
    }

    private fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Movie or show title"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Search Jellyfin")
            .setView(input)
            .setPositiveButton("Search") { _, _ -> searchContent(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchContent(query: String) {
        if (query.isBlank()) return
        val repo = jellyfinRepository
        showContentMessage("Searching for \"$query\"…")
        contentHeader.text = "Results: $query"
        lifecycleScope.launch {
            val results = try {
                withContext(Dispatchers.IO) { repo.searchMovies(query) }
            } catch (_: Throwable) {
                showContentMessage("Search failed — check Wi-Fi and try again.")
                return@launch
            }
            val store = mediaStore
            contentContainer.removeAllViews()
            if (results.isEmpty()) {
                contentContainer.addView(infoText("No results for \"$query\"."))
            } else {
                val dp = requireContext().resources.displayMetrics.density
                val cardWidth = (150 * dp).toInt()
                val cardMargin = (6 * dp).toInt()
                val navPaneWidth = (280 * dp).toInt()
                val displayWidth = requireContext().resources.displayMetrics.widthPixels
                val columns = maxOf(2, (displayWidth - navPaneWidth) / (cardWidth + cardMargin * 2))
                results.sortedBy { it.title }.chunked(columns).forEach { rowItems ->
                    contentContainer.addView(LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        rowItems.forEach { item ->
                            addView(itemRow(item, false, cardWidth, cardMargin))
                        }
                    })
                }
            }
        }
    }

    private fun refreshSectionHighlights() {
        for (i in 0 until sectionContainer.childCount) {
            val child = sectionContainer.getChildAt(i) as? TextView ?: continue
            val sectionId = child.tag as? String ?: continue
            child.setBackgroundColor(
                if (sectionId == selectedSection) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38)
            )
        }
    }

    private fun itemRow(item: JellyfinItem, isFavorited: Boolean, cardWidth: Int, margin: Int): LinearLayout {
        val dp = requireContext().resources.displayMetrics.density
        val posterHeight = (cardWidth * 1.5f).toInt()
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(margin, margin, margin, margin) }
            setBackgroundColor(Color.rgb(12, 30, 48))
            val card = this
            setOnFocusChangeListener { _, hasFocus ->
                card.setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(12, 30, 48))
            }
            setOnClickListener {
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, JellyfinDetailsFragment.newInstance(item))
                    .addToBackStack("JELLYFIN_DETAILS")
                    .commit()
            }

            // Portrait poster
            val posterView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(20, 40, 65))
                layoutParams = LinearLayout.LayoutParams(cardWidth, posterHeight)
            }
            addView(posterView)
            if (!item.posterUrl.isNullOrBlank()) {
                Glide.with(this@JellyfinBrowseFragment).load(item.posterUrl).centerCrop().into(posterView)
            }

            // Title below poster
            val favPrefix = if (isFavorited) "★ " else ""
            val typeTag = if (item.isFolder) " [S]" else ""
            addView(TextView(context).apply {
                text = "$favPrefix${item.title}$typeTag"
                textSize = 13f
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            })
            val meta = listOfNotNull(item.productionYear?.toString(), item.contentRating).joinToString(" • ")
            if (meta.isNotBlank()) {
                addView(TextView(context).apply {
                    text = meta
                    textSize = 11f
                    setTextColor(Color.LTGRAY)
                    setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), (6 * dp).toInt())
                })
            }
        }
    }

    private fun sectionLabel(title: String) = TextView(requireContext()).apply {
        text = title
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.rgb(66, 165, 245))
        setPadding(20, 20, 20, 8)
    }

    private fun infoText(msg: String) = TextView(requireContext()).apply {
        text = msg
        textSize = 16f
        setTextColor(Color.LTGRAY)
        setPadding(28, 24, 28, 24)
    }

    private fun showContentMessage(msg: String) {
        contentContainer.removeAllViews()
        contentContainer.addView(infoText(msg))
    }

    private fun buildJellyfinRepository(): JellyfinRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val prefs = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(prefs))
        return JellyfinRepository(configRepository, okHttpClient, moshi)
    }

    companion object {
        private const val SECTION_MOVIES = "movies"
        private const val SECTION_SHOWS = "shows"
    }
}
