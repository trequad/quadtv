package net.trequad.quadtv.vod

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
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.navigation.QuadTvNavigator

sealed class VodAction(
    open val label: String,
    open val description: String
) {
    data class PlayItem(
        val item: VodItem,
        override val label: String = item.title,
        override val description: String = item.description ?: item.rating ?: "Play through the bundled QuadTV player."
    ) : VodAction(label, description)

    data class Category(
        val category: VodCategory,
        override val label: String = category.name,
        override val description: String = "Browse on-demand titles in this category."
    ) : VodAction(label, description)

    data class Message(
        override val label: String,
        override val description: String
    ) : VodAction(label, description)
}

class VodBrowseFragment : BrowseSupportFragment() {
    private val vodRepository: VodRepository by lazy { buildVodRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV On-Demand"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildLoadingRows()
        configureClickHandling()
        loadVodFromRepository()
    }

    private fun configureClickHandling() {
        setOnItemViewClickedListener { _, item, _, _ ->
            when (val action = item as? VodAction) {
                is VodAction.PlayItem -> {
                    val request = VodDetailsFragment().buildPlaybackRequest(action.item) ?: return@setOnItemViewClickedListener
                    (activity as? QuadTvNavigator)?.navigateToPlayer(request)
                }
                else -> Unit
            }
        }
    }

    private fun loadVodFromRepository() {
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val recentlyAdded = vodRepository.loadRecentlyAdded()
                    val categories = vodRepository.loadCategories()
                    recentlyAdded to categories
                }
            } catch (_: Exception) {
                adapter = buildErrorRows("Unable to load On-Demand", "Check the portal VOD endpoint config and network connection.")
                return@launch
            }

            val (recentlyAdded, categories) = result
            adapter = if (recentlyAdded.isEmpty() && categories.isEmpty()) {
                buildEmptyRows()
            } else {
                buildVodRows(recentlyAdded, categories)
            }
        }
    }

    private fun buildVodRows(
        recentlyAdded: List<VodItem>,
        categories: List<VodCategory>
    ): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            if (recentlyAdded.isNotEmpty()) {
                add(ListRow(HeaderItem(0, "Recently Added VOD"), ArrayObjectAdapter(VodCardPresenter()).apply {
                    recentlyAdded.sortedBy { it.title }.forEach { item ->
                        add(VodAction.PlayItem(item))
                    }
                }))
            }
            if (categories.isNotEmpty()) {
                add(ListRow(HeaderItem(1, "On-Demand Categories"), ArrayObjectAdapter(VodCardPresenter()).apply {
                    categories.sortedBy { it.name }.forEach { category ->
                        add(VodAction.Category(category))
                    }
                }))
            }
        }
    }

    private fun buildLoadingRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Recently Added VOD"), ArrayObjectAdapter(VodCardPresenter()).apply {
                add(VodAction.Message("Loading On-Demand", "Fetching recently added titles and categories from the configured VOD endpoint."))
            }))
        }
    }

    private fun buildEmptyRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Recently Added VOD"), ArrayObjectAdapter(VodCardPresenter()).apply {
                add(VodAction.Message("No VOD titles available", "The configured VOD endpoint returned no recently added titles or categories."))
            }))
        }
    }

    private fun buildErrorRows(label: String, description: String): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Recently Added VOD"), ArrayObjectAdapter(VodCardPresenter()).apply {
                add(VodAction.Message(label, description))
            }))
        }
    }

    private fun buildVodRepository(): VodRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val preferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(preferences))
        return VodRepository(configRepository, okHttpClient, moshi)
    }
}

class VodCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(44, 95, 124))
        })
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? VodAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
