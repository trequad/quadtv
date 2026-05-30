package net.trequad.quadtv.epg

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
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.provider.ProviderFeedRepository

sealed class EpgAction(
    open val label: String,
    open val description: String
) {
    data class Programme(
        val programme: EpgProgramme,
        override val label: String = programme.title,
        override val description: String = programme.summaryText()
    ) : EpgAction(label, description)

    data class Message(
        override val label: String,
        override val description: String
    ) : EpgAction(label, description)
}

class EpgGridFragment : BrowseSupportFragment() {
    private val epgRepository: EpgRepository by lazy { buildEpgRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Guide"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildLoadingRows()
        loadProgrammesFromRepository()
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(resources.getColor(R.color.quadtv_navy, null))
    }

    private fun loadProgrammesFromRepository() {
        lifecycleScope.launch {
            val programmes = try {
                withContext(Dispatchers.IO) { epgRepository.loadProgrammes() }
            } catch (_: Exception) {
                adapter = buildErrorRows("Unable to load Guide", "Check the provider XMLTV feed and network connection.")
                return@launch
            }

            adapter = if (programmes.isEmpty()) {
                buildEmptyRows()
            } else {
                buildGuideRows(programmes)
            }
        }
    }

    private fun buildGuideRows(programmes: List<EpgProgramme>): ArrayObjectAdapter {
        val groupedByChannel = programmes.groupBy { it.channelId }
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Guide Layout"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message(
                    "Time axis and preview panel",
                    "Cable-style time axis across the top, channel rows down the left, program blocks in each row, D-pad focus, and current / next programme details in the preview panel."
                ))
            }))
            groupedByChannel.toSortedMap().forEach { (channelId, channelProgrammes) ->
                add(ListRow(HeaderItem(channelId.hashCode().toLong(), "Channel $channelId"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                    epgRepository.programmesForChannel(channelProgrammes, channelId).forEach { programme ->
                        add(EpgAction.Programme(programme))
                    }
                }))
            }
        }
    }

    private fun buildLoadingRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "QuadTV Guide"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message("Loading Guide", "Fetching XMLTV programmes for the active user from the configured provider DNS."))
            }))
        }
    }

    private fun buildEmptyRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "QuadTV Guide"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message("No guide data available", "The XMLTV feed returned no programme blocks for the current playlist."))
            }))
        }
    }

    private fun buildErrorRows(label: String, description: String): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "QuadTV Guide"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message(label, description))
            }))
        }
    }

    private fun buildEpgRepository(): EpgRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val launchPreferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(launchPreferences))
        val providerFeedRepository = ProviderFeedRepository(configRepository, CustomerSessionCache(sessionPreferences))
        return EpgRepository(providerFeedRepository, okHttpClient)
    }
}

private fun EpgProgramme.summaryText(): String {
    val ratingText = rating?.let { " • $it" }.orEmpty()
    val categoryText = category?.let { " • $it" }.orEmpty()
    val matureText = if (isMature) " • Mature" else ""
    return "Program blocks show current / next programme details$categoryText$ratingText$matureText"
}

private class EpgCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        return ViewHolder(TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 21f
            setTextColor(Color.WHITE)
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(44, 95, 124))
        })
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? EpgAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
