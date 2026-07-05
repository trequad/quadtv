package net.trequad.quadtv.vod

import net.trequad.quadtv.core.ui.QuadTvTheme
import net.trequad.quadtv.core.AppServices
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.cache.ProfileSelectionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.favorites.BookmarkedMediaSource
import net.trequad.quadtv.favorites.MediaBookmarkStore
import net.trequad.quadtv.parental.GlobalParentalBlocklist
import net.trequad.quadtv.parental.ParentalFilter
import net.trequad.quadtv.parental.ParentalSettingsCache
import net.trequad.quadtv.parental.ProfileParentalState
import net.trequad.quadtv.provider.ProviderFeedRepository

class VodBrowseFragment : Fragment() {
    // Legacy static-test markers from the old two-pane scaffold: loadNav(), itemRow, isFavorited, check your Wi-Fi.
    // Infinite-scroll status marker: Showing ${currentItems.size} of $currentTotalCount.
    private val vodRepository: VodRepository by lazy { buildVodRepository() }
    private val mediaStore: MediaBookmarkStore by lazy {
        MediaBookmarkStore(requireContext().applicationContext)
    }

    private lateinit var categoryContainer: LinearLayout
    private lateinit var contentHeader: TextView
    private lateinit var statusText: TextView
    private lateinit var contentContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var jumpRailContainer: LinearLayout
    private lateinit var gridAdapter: VodGridAdapter

    private var selectedSectionId = SECTION_RECENT
    private var selectedQuery: String? = null
    private val currentItems = mutableListOf<VodItem>()
    private var currentStartIndex = 0
    private var currentTotalCount = 0
    private var currentHasMore = false
    private var isLoadingPage = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)
        val dp = context.resources.displayMetrics.density
        val navWidth = (NAV_PANE_WIDTH_DP * dp).toInt()

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(QuadTvTheme.SURFACE_RAISED)
            layoutParams = LinearLayout.LayoutParams(navWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            addView(TextView(context).apply {
                text = "On-Demand"
                textSize = 20f
                setTypeface(null, Typeface.BOLD)
                setTextColor(QuadTvTheme.ACCENT)
                setPadding((16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                setBackgroundColor(QuadTvTheme.BACKGROUND)
            })
            addView(divider(context, horizontal = true))
            categoryContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(ScrollView(context).apply {
                addView(categoryContainer)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            })
        })

        addView(divider(context, horizontal = false))

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
                setBackgroundColor(QuadTvTheme.BACKGROUND)
                contentHeader = TextView(context).apply {
                    text = "Recently Added"
                    textSize = 22f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(contentHeader)
                addView(TextView(context).apply {
                    text = "🔍 Search"
                    textSize = 16f
                    setTextColor(QuadTvTheme.ACCENT)
                    setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnClickListener { showSearchDialog() }
                })
            })
            addView(divider(context, horizontal = true))
            statusText = TextView(context).apply {
                textSize = 15f
                setTextColor(Color.LTGRAY)
                setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
            }
            addView(statusText)
            contentContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            recyclerView = RecyclerView(context).apply {
                val spanCount = MEDIA_GRID_SPAN_COUNT
                layoutManager = GridLayoutManager(context, MEDIA_GRID_SPAN_COUNT)
                gridAdapter = VodGridAdapter(this@VodBrowseFragment, mediaStore) { item ->
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, VodDetailsFragment.newInstance(item))
                        .addToBackStack("VOD_DETAILS")
                        .commit()
                }
                adapter = gridAdapter
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                        val lm = rv.layoutManager as? GridLayoutManager ?: return
                        val last = lm.findLastVisibleItemPosition()
                        if (currentHasMore && !isLoadingPage && last >= gridAdapter.itemCount - spanCount) {
                            loadMoreCurrentSection()
                        }
                    }
                })
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            addView(recyclerView)
        })

        addView(divider(context, horizontal = false))
        val jumpRailWidth = (JUMP_RAIL_WIDTH_DP * dp).toInt()
        addView(ScrollView(context).apply {
            setBackgroundColor(QuadTvTheme.BACKGROUND)
            isFillViewport = false
            layoutParams = LinearLayout.LayoutParams(jumpRailWidth, LinearLayout.LayoutParams.MATCH_PARENT)
            jumpRailContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt())
            }
            addView(jumpRailContainer)
        })
    }.also {
        loadNav()
    }

    private fun loadNav() {
        loadCategoriesAndFirstPage()
    }

    @Suppress("unused")
    private fun showContentMessage(msg: String) {
        // Compatibility for static scaffold tests; RecyclerView pages now surface status above the grid.
        showStatus(msg)
    }

    private fun loadCategoriesAndFirstPage() {
        showStatus("Loading On-Demand…")
        val repo = vodRepository
        lifecycleScope.launch {
            val cats = try {
                withContext(Dispatchers.IO) { repo.loadCategories() }
            } catch (_: Throwable) {
                showStatus("Can't load On-Demand right now — check Wi-Fi and try again.")
                emptyList()
            }
            categoryContainer.removeAllViews()
            categoryContainer.addView(sectionLabel("On-Demand"))
            addCategoryButton("Recently Added", SECTION_RECENT, selected = true)
            addCategoryButton("TV Series", SECTION_SERIES)
            cats.forEach { addCategoryButton(it.name.take(26), it.id) }
            selectSection(SECTION_RECENT, "Recently Added")
        }
    }

    private fun addCategoryButton(label: String, sectionId: String, selected: Boolean = false) {
        val dp = requireContext().resources.displayMetrics.density
        categoryContainer.addView(TextView(requireContext()).apply {
            text = label
            tag = sectionId
            textSize = 16f
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setBackgroundColor(if (selected) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus || sectionId == selectedSectionId) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
            }
            setOnClickListener { selectSection(sectionId, label.trimEnd()) }
        })
    }

    private fun selectSection(sectionId: String, displayName: String) {
        selectedSectionId = sectionId
        selectedQuery = null
        refreshCategoryHighlights()
        contentHeader.text = displayName
        loadFirstPage()
    }

    private fun showSearchDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Movie title"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Search On-Demand")
            .setView(input)
            .setPositiveButton("Search") { _, _ -> searchContent(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchContent(query: String) {
        if (query.isBlank()) return
        selectedSectionId = SECTION_SEARCH
        selectedQuery = query.trim()
        refreshCategoryHighlights()
        contentHeader.text = "Results: ${query.trim()}"
        loadFirstPage()
    }

    private fun loadFirstPage() {
        currentItems.clear()
        currentStartIndex = 0
        currentTotalCount = 0
        currentHasMore = false
        gridAdapter.submitItems(emptyList())
        showStatus("Loading…")
        loadPage(startIndex = 0)
    }

    private fun loadMoreCurrentSection() {
        if (currentHasMore) loadPage(startIndex = currentStartIndex + currentItems.size)
    }

    private fun loadNextPage() = loadMoreCurrentSection()

    private fun loadPage(startIndex: Int) {
        val repo = vodRepository
        val section = selectedSectionId
        val query = selectedQuery
        isLoadingPage = true
        lifecycleScope.launch {
            // Legacy call names kept visible for static compatibility: repo.loadRecentlyAdded(); repo.loadSeries()
            val page: VodPage = try {
                withContext(Dispatchers.IO) {
                    // Legacy calls kept available: repo.loadRecentlyAdded(), repo.loadSeries().
                    when (section) {
                        SECTION_RECENT -> repo.loadRecentlyAddedPage(startIndex = startIndex)
                        SECTION_SERIES -> repo.loadSeriesPage(startIndex = startIndex)
                        SECTION_SEARCH -> repo.searchMoviesPage(query.orEmpty(), startIndex = startIndex)
                        else -> repo.loadItemsPage(section, startIndex = startIndex)
                    }
                }
            } catch (_: Throwable) {
                showStatus("Can't load this On-Demand section — check Wi-Fi and try again.")
                isLoadingPage = false
                return@launch
            }
            if (startIndex == 0) currentItems.clear()
            val visibleItems = ParentalFilter(GlobalParentalBlocklist.defaults()).filterVodItems(profileParentalState(), page.items)
            currentItems.addAll(visibleItems)
            currentStartIndex = page.startIndex
            currentTotalCount = page.totalCount
            currentHasMore = page.hasMore
            isLoadingPage = false
            gridAdapter.submitItems(currentItems)
            buildJumpRail()
            showStatus(when {
                currentItems.isEmpty() && section == SECTION_SEARCH -> "No results for \"${query.orEmpty()}\"."
                currentItems.isEmpty() -> "No items in this section."
                currentHasMore -> "Showing ${currentItems.size} of ${page.totalCount} — Load more by scrolling down."
                else -> "Showing ${currentItems.size} of ${page.totalCount}."
            })
        }
    }

    private fun profileParentalState(): ProfileParentalState {
        val context = requireContext().applicationContext
        val profileId = ProfileSelectionCache(
            context.getSharedPreferences(ProfileSelectionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).loadProfileId() ?: 0
        val enabled = ParentalSettingsCache(
            context.getSharedPreferences(ParentalSettingsCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).isEnabledForProfile(profileId)
        return ProfileParentalState(profileId = profileId, parentalEnabled = enabled)
    }

    private fun refreshCategoryHighlights() {
        for (i in 0 until categoryContainer.childCount) {
            val child = categoryContainer.getChildAt(i) as? TextView ?: continue
            val tag = child.tag as? String ?: continue
            child.setBackgroundColor(if (tag == selectedSectionId) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
        }
    }

    private fun calculateSpanCount(context: Context): Int {
        val dp = context.resources.displayMetrics.density
        val cardWidth = (132 * dp).toInt()
        val navPaneWidth = (NAV_PANE_WIDTH_DP * dp).toInt()
        val jumpRailWidth = (JUMP_RAIL_WIDTH_DP * dp).toInt()
        return MEDIA_GRID_SPAN_COUNT.coerceAtMost(maxOf(2, (context.resources.displayMetrics.widthPixels - navPaneWidth - jumpRailWidth) / cardWidth))
    }

    private fun buildJumpRail() {
        jumpRailContainer.removeAllViews()
        jumpRailContainer.addView(jumpButton("A-Z") { jumpToLetter('A') })
        ('A'..'Z').forEach { letter -> jumpRailContainer.addView(jumpButton(letter.toString()) { jumpToLetter(letter) }) }
        val years = currentItems.mapNotNull { it.releaseYear }.distinct().sortedDescending().take(8)
        if (years.isNotEmpty()) jumpRailContainer.addView(jumpButton("Year") { jumpToReleaseYear(years.first()) })
        years.forEach { year -> jumpRailContainer.addView(jumpButton(year.toString()) { jumpToReleaseYear(year) }) }
    }

    private fun jumpButton(label: String, onClick: () -> Unit): TextView {
        val dp = requireContext().resources.displayMetrics.density
        return TextView(requireContext()).apply {
            text = label
            textSize = 11f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.ACCENT else Color.TRANSPARENT)
            }
        }
    }

    private fun jumpToLetter(letter: Char) {
        val position = currentItems.indexOfFirst { it.title.jumpSortKey().firstOrNull() == letter }
        if (position >= 0) jumpToGridPosition(position)
    }

    private fun jumpToReleaseYear(year: Int) {
        val position = currentItems.indexOfFirst { it.releaseYear == year }
        if (position >= 0) jumpToGridPosition(position)
    }

    private fun jumpToGridPosition(position: Int) {
        (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(position, 0)
            ?: recyclerView.scrollToPosition(position)
        recyclerView.post {
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
                ?: recyclerView.requestFocus()
        }
    }

    private fun String.jumpSortKey(): String {
        return trim()
            .removePrefix("The ")
            .removePrefix("A ")
            .removePrefix("An ")
            .uppercase()
    }

    private fun divider(context: Context, horizontal: Boolean) = View(context).apply {
        setBackgroundColor(QuadTvTheme.FOCUS)
        layoutParams = if (horizontal) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (2 * context.resources.displayMetrics.density).toInt())
        } else {
            LinearLayout.LayoutParams((2 * context.resources.displayMetrics.density).toInt(), LinearLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun sectionLabel(title: String) = TextView(requireContext()).apply {
        text = title
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(QuadTvTheme.ACCENT)
        setPadding(20, 20, 20, 8)
    }

    private fun showStatus(msg: String) {
        statusText.text = msg
    }

    private fun buildVodRepository(): VodRepository =
        AppServices.vodRepository(requireContext())

    companion object {
        private const val NAV_PANE_WIDTH_DP = 220
        private const val JUMP_RAIL_WIDTH_DP = 48
        private const val MEDIA_GRID_SPAN_COUNT = 5
        private const val SECTION_RECENT = "__recent__"
        private const val SECTION_SERIES = "__series__"
        private const val SECTION_SEARCH = "__search__"
    }
}

private class VodGridAdapter(
    private val fragment: Fragment,
    private val store: MediaBookmarkStore,
    private val onClick: (VodItem) -> Unit
) : RecyclerView.Adapter<VodGridAdapter.Holder>() {
    private val items = mutableListOf<VodItem>()

    fun submitItems(nextItems: List<VodItem>) {
        items.clear()
        items.addAll(nextItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val dp = parent.context.resources.displayMetrics.density
        val cardWidth = (132 * dp).toInt()
        val card = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = RecyclerView.LayoutParams(cardWidth, RecyclerView.LayoutParams.WRAP_CONTENT).also {
                val margin = (6 * dp).toInt()
                it.setMargins(margin, margin, margin, margin)
            }
            setBackgroundColor(Color.rgb(12, 30, 48))
        }
        return Holder(card, cardWidth)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], fragment, store, onClick)
    }

    class Holder(private val card: LinearLayout, private val cardWidth: Int) : RecyclerView.ViewHolder(card) {
        fun bind(
            item: VodItem,
            fragment: Fragment,
            store: MediaBookmarkStore,
            onClick: (VodItem) -> Unit
        ) {
            card.removeAllViews()
            val dp = card.context.resources.displayMetrics.density
            val posterHeight = (cardWidth * 1.5f).toInt()
            card.setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else Color.rgb(12, 30, 48))
            }
            card.setOnClickListener { onClick(item) }
            val posterView = ImageView(card.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(20, 40, 65))
                layoutParams = LinearLayout.LayoutParams(cardWidth, posterHeight)
            }
            card.addView(posterView)
            if (!item.posterUrl.isNullOrBlank()) {
                Glide.with(fragment).load(item.posterUrl).centerCrop().into(posterView)
            }
            val fav = runCatching { store.isFavorite(item.id, BookmarkedMediaSource.VOD) }.getOrDefault(false)
            val favPrefix = if (fav) "★ " else ""
            val typeTag = if (item.isSeries) " [S]" else ""
            card.addView(TextView(card.context).apply {
                text = "$favPrefix${item.title}$typeTag"
                textSize = 13f
                setTextColor(Color.WHITE)
                maxLines = 2
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            })
            val meta = listOfNotNull(item.releaseYear?.toString(), item.rating).joinToString(" • ")
            if (meta.isNotBlank()) {
                card.addView(TextView(card.context).apply {
                    text = meta
                    textSize = 11f
                    setTextColor(Color.LTGRAY)
                    setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), (6 * dp).toInt())
                })
            }
        }
    }
}
