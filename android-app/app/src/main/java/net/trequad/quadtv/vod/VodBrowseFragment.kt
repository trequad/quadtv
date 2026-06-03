package net.trequad.quadtv.vod

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.provider.ProviderFeedRepository

class VodBrowseFragment : Fragment() {
    private val vodRepository: VodRepository by lazy { buildVodRepository() }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    private lateinit var categoryContainer: LinearLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var contentHeader: TextView

    private var categories: List<VodCategory> = emptyList()
    private val cachedItems = mutableMapOf<String, List<VodItem>>()
    private var selectedSectionId = SECTION_RECENT

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(Color.rgb(7, 24, 39))
        val dp = context.resources.displayMetrics.density
        val navWidth = (280 * dp).toInt()

        // Left pane — category navigation
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(12, 30, 50))
            layoutParams = LinearLayout.LayoutParams(navWidth, LinearLayout.LayoutParams.MATCH_PARENT)

            // Left pane header
            addView(TextView(context).apply {
                text = "On-Demand"
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

            categoryContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(ScrollView(context).apply {
                addView(categoryContainer)
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

            // Right pane header
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
                setBackgroundColor(Color.rgb(7, 18, 32))
                contentHeader = TextView(context).apply {
                    text = "Recently Added"
                    textSize = 22f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                addView(contentHeader)
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
        showContentMessage("Loading…")
        loadNav()
    }

    private fun loadNav() {
        val repo = vodRepository
        val store = mediaStore
        lifecycleScope.launch {
            val (cats, recent, series) = try {
                withContext(Dispatchers.IO) {
                    Triple(repo.loadCategories(), repo.loadRecentlyAdded(), repo.loadSeries())
                }
            } catch (_: Throwable) {
                showContentMessage("Can't load On-Demand right now — check your Wi-Fi and try again.")
                return@launch
            }
            categories = cats
            cachedItems[SECTION_RECENT] = recent
            cachedItems[SECTION_SERIES] = series

            categoryContainer.removeAllViews()
            categoryContainer.addView(sectionLabel("On-Demand"))
            addCategoryButton("Recently Added", SECTION_RECENT, selected = true)
            cats.forEach { addCategoryButton(it.name.take(26), it.id) }
            if (series.isNotEmpty()) addCategoryButton("TV Series  (${series.size})", SECTION_SERIES)

            showItems(recent, "Recently Added")
        }
    }

    private fun addCategoryButton(label: String, sectionId: String, selected: Boolean = false) {
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
                    if (hasFocus || sectionId == selectedSectionId) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38)
                )
            }
            setOnClickListener { selectSection(sectionId, label.trimEnd()) }
        }
        categoryContainer.addView(btn)
    }

    private fun selectSection(sectionId: String, displayName: String) {
        selectedSectionId = sectionId
        refreshCategoryHighlights()
        contentHeader.text = displayName

        val cached = cachedItems[sectionId]
        if (cached != null) { showItems(cached, displayName); return }

        showContentMessage("Loading $displayName…")
        val repo = vodRepository
        val store = mediaStore
        lifecycleScope.launch {
            val items = try {
                withContext(Dispatchers.IO) { repo.loadItems(sectionId) }
            } catch (_: Throwable) {
                showContentMessage("Can't load $displayName — check Wi-Fi and try again.")
                return@launch
            }
            cachedItems[sectionId] = items
            if (selectedSectionId == sectionId) showItems(items, displayName)
        }
    }

    private fun showItems(items: List<VodItem>, sectionName: String) {
        contentContainer.removeAllViews()
        if (items.isEmpty()) {
            contentContainer.addView(infoText("No items in $sectionName."))
            return
        }
        val store = mediaStore
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
                        store.isFavorite(item.id, BookmarkedMediaSource.VOD)
                    }.getOrDefault(false)
                    addView(itemRow(item, fav, cardWidth, cardMargin))
                }
            })
        }
    }

    private fun refreshCategoryHighlights() {
        for (i in 0 until categoryContainer.childCount) {
            val child = categoryContainer.getChildAt(i) as? TextView ?: continue
            val tag = child.tag as? String ?: continue
            child.setBackgroundColor(
                if (tag == selectedSectionId) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38)
            )
        }
    }

    private fun addCategoryButtonTagged(label: String, sectionId: String, selected: Boolean = false) {
        val btn = TextView(requireContext()).apply {
            text = label
            tag = sectionId
            textSize = 16f
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(20, 14, 20, 14)
            setBackgroundColor(if (selected) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38))
            val view = this
            setOnFocusChangeListener { _, hasFocus ->
                view.setBackgroundColor(
                    if (hasFocus || sectionId == selectedSectionId) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38)
                )
            }
            setOnClickListener { selectSection(sectionId, label.trimEnd()) }
        }
        categoryContainer.addView(btn)
    }

    private fun itemRow(item: VodItem, isFavorited: Boolean, cardWidth: Int, margin: Int): LinearLayout {
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
                    .replace(android.R.id.content, VodDetailsFragment.newInstance(item))
                    .addToBackStack("VOD_DETAILS")
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
                Glide.with(this@VodBrowseFragment).load(item.posterUrl).centerCrop().into(posterView)
            }

            // Title below poster
            val favPrefix = if (isFavorited) "★ " else ""
            val typeTag = if (item.isSeries) " [S]" else ""
            addView(TextView(context).apply {
                text = "$favPrefix${item.title}$typeTag"
                textSize = 13f
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            })
            val meta = listOfNotNull(item.releaseYear?.toString(), item.rating).joinToString(" • ")
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

    private fun buildVodRepository(): VodRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val prefs = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sessionPrefs = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(prefs))
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPrefs))
        return VodRepository(configRepository, okHttpClient, moshi, providerFeedRepository)
    }

    companion object {
        private const val SECTION_RECENT = "__recent__"
        private const val SECTION_SERIES = "__series__"
    }
}
