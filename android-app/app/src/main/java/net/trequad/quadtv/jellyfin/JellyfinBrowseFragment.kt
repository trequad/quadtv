package net.trequad.quadtv.jellyfin

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

sealed class JellyfinAction(
    open val label: String,
    open val description: String
) {
    data class Library(
        val library: JellyfinLibrary,
        override val label: String = library.name,
        override val description: String = library.collectionType?.let { "Jellyfin $it library" }
            ?: "Browse this Jellyfin library."
    ) : JellyfinAction(label, description)

    data class PlayItem(
        val item: JellyfinItem,
        override val label: String = item.title,
        override val description: String = listOfNotNull(
            item.contentRating,
            item.productionYear?.toString(),
            item.overview
        ).firstOrNull() ?: "Play from the bundled QuadTV player."
    ) : JellyfinAction(label, description)

    data class Message(
        override val label: String,
        override val description: String
    ) : JellyfinAction(label, description)
}

data class JellyfinLibraryRow(
    val library: JellyfinLibrary,
    val items: List<JellyfinItem>
)

class JellyfinBrowseFragment : BrowseSupportFragment() {
    private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Jellyfin"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildLoadingRows()
        configureClickHandling()
        loadJellyfinFromRepository()
    }

    private fun configureClickHandling() {
        setOnItemViewClickedListener { _, item, _, _ ->
            when (val action = item as? JellyfinAction) {
                is JellyfinAction.PlayItem -> {
                    lifecycleScope.launch {
                        val stream = withContext(Dispatchers.IO) {
                            jellyfinRepository.buildHlsStream(action.item.id)
                        } ?: return@launch
                        val request = JellyfinDetailsFragment().buildPlaybackRequest(stream)
                        (activity as? QuadTvNavigator)?.navigateToPlayer(request)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun loadJellyfinFromRepository() {
        lifecycleScope.launch {
            val libraryRows = try {
                withContext(Dispatchers.IO) {
                    val libraries = jellyfinRepository.loadLibraries()
                    libraries.map { library ->
                        JellyfinLibraryRow(
                            library = library,
                            items = jellyfinRepository.loadItems(library.id)
                        )
                    }
                }
            } catch (_: Exception) {
                adapter = buildErrorRows("Unable to load Jellyfin", "Check the portal Jellyfin URL/API key and network connection.")
                return@launch
            }

            adapter = if (libraryRows.isEmpty()) {
                buildEmptyRows()
            } else {
                buildJellyfinRows(libraryRows)
            }
        }
    }

    private fun buildJellyfinRows(libraryRows: List<JellyfinLibraryRow>): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Jellyfin Libraries"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                libraryRows.sortedBy { it.library.name }.forEach { row ->
                    add(JellyfinAction.Library(row.library))
                }
            }))

            val recentlyAdded = libraryRows.flatMap { it.items }
                .filterNot { it.isFolder }
                .sortedBy { it.title }
                .take(24)
            if (recentlyAdded.isNotEmpty()) {
                add(ListRow(HeaderItem(1, "Recently Added from Jellyfin"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                    recentlyAdded.forEach { item ->
                        add(JellyfinAction.PlayItem(item))
                    }
                }))
            }

            libraryRows.sortedBy { it.library.name }.forEachIndexed { index, row ->
                val playableItems = row.items.filterNot { it.isFolder }
                if (playableItems.isNotEmpty()) {
                    add(ListRow(HeaderItem((index + 2).toLong(), row.library.name), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                        playableItems.sortedBy { it.title }.forEach { item ->
                            add(JellyfinAction.PlayItem(item))
                        }
                    }))
                }
            }
        }
    }

    private fun buildLoadingRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Jellyfin Libraries"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                add(JellyfinAction.Message("Loading Jellyfin", "Fetching configured Jellyfin libraries and media rows from QuadTV Admin."))
            }))
        }
    }

    private fun buildEmptyRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Jellyfin Libraries"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                add(JellyfinAction.Message("No Jellyfin libraries available", "Configure Jellyfin in QuadTV Admin, then refresh this screen."))
            }))
        }
    }

    private fun buildErrorRows(label: String, description: String): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Jellyfin Libraries"), ArrayObjectAdapter(JellyfinCardPresenter()).apply {
                add(JellyfinAction.Message(label, description))
            }))
        }
    }

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
}

class JellyfinCardPresenter : Presenter() {
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
        val action = item as? JellyfinAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
