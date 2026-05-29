package net.trequad.quadtv.live

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
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.provider.ProviderFeedRepository

sealed class LiveTvAction(
    open val label: String,
    open val description: String,
    open val route: QuadTvRoute? = null
) {
    data object AllChannels : LiveTvAction(
        "All Channels",
        "Browse channel groups and start in-app bundled player playback."
    )

    data object OpenGuide : LiveTvAction(
        "Open Guide",
        "Jump to the cable-style EPG grid while live preview remains available.",
        QuadTvRoute.EPG
    )

    data object InfoBanner : LiveTvAction(
        "Info banner",
        "Channel, current programme, next programme, time, and progress overlay scaffold."
    )

    data class Channel(
        val channel: LiveChannel,
        override val label: String = channel.name,
        override val description: String = "Prepare HLS playback handoff through the selected bundled player."
    ) : LiveTvAction(label, description)

    data class Message(
        override val label: String,
        override val description: String
    ) : LiveTvAction(label, description)
}

class LiveTvFragment : BrowseSupportFragment() {
    private val playbackCoordinator = LiveTvPlaybackCoordinator()
    private val liveTvRepository: LiveTvRepository by lazy { buildLiveTvRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Live TV"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        adapter = buildLoadingRows()
        configureClickHandling()
        loadChannelsFromRepository()
    }

    private fun configureClickHandling() {
        setOnItemViewClickedListener { _, item, _, _ ->
            val action = item as? LiveTvAction ?: return@setOnItemViewClickedListener
            action.route?.let { route ->
                (activity as? QuadTvNavigator)?.navigateTo(route)
                return@setOnItemViewClickedListener
            }
            when (action) {
                is LiveTvAction.Channel -> {
                    val request = playbackCoordinator.buildRequest(action.channel)
                    (activity as? QuadTvNavigator)?.navigateToPlayer(request)
                }
                else -> Unit
            }
        }
    }

    private fun loadChannelsFromRepository() {
        lifecycleScope.launch {
            val channels = try {
                withContext(Dispatchers.IO) { liveTvRepository.loadChannels() }
            } catch (_: Exception) {
                adapter = buildErrorRows("Unable to load Live TV channels", "Check the portal endpoint config and network connection.")
                return@launch
            }

            adapter = if (channels.isEmpty()) {
                buildErrorRows("No channels available", "The live TV playlist returned no playable channels.")
            } else {
                buildLiveRows(channels)
            }
        }
    }

    private fun buildLiveRows(channels: List<LiveChannel>): ArrayObjectAdapter {
        val groups = channels.groupBy { it.groupTitle ?: "Other Channels" }
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Live TV"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction.AllChannels)
                add(LiveTvAction.OpenGuide)
                add(LiveTvAction.InfoBanner)
            }))
            groups.toSortedMap().forEach { (groupTitle, groupChannels) ->
                add(ListRow(HeaderItem(groupTitle), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                    groupChannels.sortedBy { it.name }.forEach { channel ->
                        add(LiveTvAction.Channel(channel))
                    }
                }))
            }
        }
    }

    private fun buildLoadingRows(): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Live TV"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction.Message("Loading channels", "Fetching the portal-configured M3U playlist for this profile."))
                add(LiveTvAction.OpenGuide)
            }))
        }
    }

    private fun buildErrorRows(label: String, description: String): ArrayObjectAdapter {
        return ArrayObjectAdapter(ListRowPresenter()).apply {
            add(ListRow(HeaderItem(0, "Live TV"), ArrayObjectAdapter(LiveTvActionPresenter()).apply {
                add(LiveTvAction.Message(label, description))
                add(LiveTvAction.OpenGuide)
            }))
        }
    }

    private fun buildLiveTvRepository(): LiveTvRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val launchPreferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(launchPreferences))
        val providerFeedRepository = ProviderFeedRepository(configRepository, CustomerSessionCache(sessionPreferences))
        return LiveTvRepository(providerFeedRepository, okHttpClient)
    }
}

private class LiveTvActionPresenter : Presenter() {
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
        val action = item as? LiveTvAction
        (viewHolder.view as TextView).text = if (action == null) {
            item?.toString().orEmpty()
        } else {
            "${action.label}\n${action.description}"
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
