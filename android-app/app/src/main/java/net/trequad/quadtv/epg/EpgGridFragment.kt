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
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.provider.ProviderFeedRepository
import java.util.Calendar

sealed class EpgAction(
    open val label: String,
    open val description: String
) {
    data class Programme(
        val programme: EpgProgramme,
        override val label: String = epgCardLabel(programme),
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
        val repo = epgRepository
        lifecycleScope.launch {
            val programmes = try {
                withContext(Dispatchers.IO) { repo.loadProgrammes() }
            } catch (_: Throwable) {
                adapter = buildErrorRows("Can't load Guide right now", "Check your Wi-Fi and try again.")
                return@launch
            }
            try {
                adapter = if (programmes.isEmpty()) buildEmptyRows() else buildGuideRows(programmes)
            } catch (_: Throwable) {
                adapter = buildErrorRows("Can't load Guide right now", "Check your Wi-Fi and try again.")
            }
        }
    }

    private fun buildGuideRows(programmes: List<EpgProgramme>): ArrayObjectAdapter {
        val now = System.currentTimeMillis()
        val halfHourAgo = now - 30 * 60 * 1000L
        val groupedByChannel = programmes.groupBy { it.channelId }

        // Channels with a currently-airing show first, then alphabetical
        val sortedChannelIds = groupedByChannel.keys.sortedWith(
            compareByDescending<String> { channelId ->
                groupedByChannel[channelId]?.any { now in it.startTimeMillis until it.endTimeMillis } == true
            }.thenBy { it.lowercase() }
        )

        return ArrayObjectAdapter(ListRowPresenter()).apply {
            sortedChannelIds.forEach { channelId ->
                val channelProgs = groupedByChannel[channelId].orEmpty()
                    .filter { it.endTimeMillis >= halfHourAgo }
                    .sortedBy { it.startTimeMillis }
                    .take(8)
                if (channelProgs.isEmpty()) return@forEach

                val headerLabel = channelId
                    .replace(Regex("(?i)channel\\s+\\d+\\s+"), "")  // strip "Channel 22 " prefix
                    .trimEnd()
                    .take(22)
                    .let { if (channelId.length > it.length + 22) "$it…" else it }
                    .ifBlank { channelId.take(22) }

                add(ListRow(
                    HeaderItem(channelId.hashCode().toLong(), headerLabel),
                    ArrayObjectAdapter(EpgCardPresenter()).apply {
                        channelProgs.forEach { add(EpgAction.Programme(it)) }
                    }
                ))
            }
        }
    }

    private fun buildLoadingRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "QuadTV Guide"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message("Loading Guide…", "Fetching programme listings."))
            }))
        }
    }

    private fun buildEmptyRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "QuadTV Guide"), ArrayObjectAdapter(EpgCardPresenter()).apply {
                add(EpgAction.Message("No guide data available", "The XMLTV feed returned no programme listings."))
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
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return EpgRepository(providerFeedRepository, okHttpClient)
    }
}

private fun epgCardLabel(programme: EpgProgramme): String {
    val now = System.currentTimeMillis()
    val isLive = now in programme.startTimeMillis until programme.endTimeMillis
    val prefix = if (isLive) "▶ " else ""
    return "$prefix${formatEpgTime(programme.startTimeMillis)}  ${programme.title}"
}

private fun EpgProgramme.summaryText(): String {
    val time = "${formatEpgTime(startTimeMillis)} – ${formatEpgTime(endTimeMillis)}"
    val desc = description?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty()
    val cat = category?.let { "  [$it]" }.orEmpty()
    val rate = rating?.let { "  $it" }.orEmpty()
    return "$time$desc$cat$rate"
}

private fun formatEpgTime(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val h = cal.get(Calendar.HOUR_OF_DAY)
    val m = cal.get(Calendar.MINUTE)
    val ampm = if (h < 12) "AM" else "PM"
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "$h12:${m.toString().padStart(2, '0')} $ampm"
}

private class EpgCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(28, 20, 28, 20)
            setBackgroundColor(Color.rgb(18, 52, 76))
            maxLines = 2
        }
        view.setOnFocusChangeListener { _, hasFocus ->
            view.setBackgroundColor(if (hasFocus) Color.rgb(66, 165, 245) else Color.rgb(18, 52, 76))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val action = item as? EpgAction
        (viewHolder.view as TextView).text = action?.label ?: item?.toString().orEmpty()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
