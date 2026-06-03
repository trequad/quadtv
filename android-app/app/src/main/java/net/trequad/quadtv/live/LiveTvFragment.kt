package net.trequad.quadtv.live

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgProgramme
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.provider.ProviderFeedRepository

sealed class LiveTvAction(
    open val label: String,
    open val description: String,
    open val route: QuadTvRoute? = null
) {
    data object OpenGuide : LiveTvAction("Open Guide", "Cable guide", QuadTvRoute.EPG)
    data class Channel(
        val channel: LiveChannel,
        val currentProgramme: EpgProgramme? = null,
        override val label: String = channel.name,
        override val description: String = currentProgramme?.title ?: "No information"
    ) : LiveTvAction(label, description)
    data class Message(override val label: String, override val description: String) : LiveTvAction(label, description)
}

class LiveTvFragment : Fragment() {
    private val playbackCoordinator = LiveTvPlaybackCoordinator()
    private val bookmarkStore: LiveChannelBookmarkStore by lazy { LiveChannelBookmarkStore(requireContext().applicationContext) }
    private val liveTvRepository: LiveTvRepository by lazy { buildLiveTvRepository() }
    private val epgRepository: EpgRepository by lazy { buildEpgRepository() }
    private lateinit var groupContainer: LinearLayout
    private lateinit var channelContainer: LinearLayout
    private var channelsByGroup: Map<String, List<LiveChannel>> = emptyMap()
    private var currentProgrammes: Map<String, EpgProgramme> = emptyMap()
    private var selectedGroup: String? = null

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(resources.getColor(R.color.quadtv_charcoal, null))
            setPadding(48, 40, 48, 40)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply {
                    text = "Live TV"
                    textSize = 34f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                addView(Button(context).apply {
                    text = "Open Guide"
                    textSize = 20f
                    isFocusable = true
                    setOnClickListener { (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.EPG) }
                })
            })

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 28, 0, 0)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

                groupContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, 28, 0)
                }
                addView(ScrollView(context).apply {
                    addView(groupContainer)
                    layoutParams = LinearLayout.LayoutParams(360, ViewGroup.LayoutParams.MATCH_PARENT)
                })

                channelContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                }
                addView(ScrollView(context).apply {
                    addView(channelContainer)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                })
            })
        }.also {
            showLoadingState()
            loadChannelsFromRepository()
        }
    }

    private fun loadChannelsFromRepository() {
        val liveRepo = liveTvRepository   // initialise on main thread
        val epgRepo = epgRepository
        lifecycleScope.launch {
            val channels = try {
                withContext(Dispatchers.IO) { liveRepo.loadChannels() }
            } catch (_: Throwable) {
                showErrorState()
                return@launch
            }
            if (channels.isEmpty()) {
                showErrorState()
                return@launch
            }
            try {
                val grouped = channels.groupBy { it.groupTitle?.takeIf { g -> g.isNotBlank() } ?: "Other Channels" }
                channelsByGroup = sortedGroupNames(grouped.keys).associateWith { group -> grouped[group].orEmpty() }
                selectedGroup = channelsByGroup.keys.first()
                renderGroupsAndChannels(selectedGroup ?: channelsByGroup.keys.first())
                loadCurrentProgrammesIntoRows(channels, epgRepo)
            } catch (_: Throwable) {
                showErrorState()
            }
        }
    }

    private fun loadCurrentProgrammesIntoRows(channels: List<LiveChannel>, epgRepo: EpgRepository) {
        lifecycleScope.launch {
            currentProgrammes = try {
                val programmes = withContext(Dispatchers.IO) { epgRepo.loadProgrammes() }
                withContext(Dispatchers.Default) { programmes.currentProgrammesByChannel(channels) }
            } catch (_: Throwable) {
                emptyMap()
            }
            renderGroupsAndChannels(selectedGroup ?: channelsByGroup.keys.first())
        }
    }

    private fun renderGroupsAndChannels(selectedGroupName: String) {
        selectedGroup = selectedGroupName
        groupContainer.removeAllViews()
        channelContainer.removeAllViews()
        channelsByGroup.keys.forEach { group ->
            groupContainer.addView(sideButton(group, selected = group == selectedGroupName) {
                renderGroupsAndChannels(group)
            })
        }
        val selectedLineup = channelsByGroup[selectedGroupName].orEmpty().sortedBy { it.name.lowercase() }
        selectedLineup.forEach { channel ->
            channelContainer.addView(channelButton(channel, selectedLineup))
        }
    }

    private fun showLoadingState() {
        groupContainer.removeAllViews()
        channelContainer.removeAllViews()
        groupContainer.addView(messageView("Groups"))
        channelContainer.addView(messageView("Loading your channels…"))
    }

    private fun showErrorState() {
        groupContainer.removeAllViews()
        channelContainer.removeAllViews()
        groupContainer.addView(sideButton("Open Guide", selected = false) {
            (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.EPG)
        })
        channelContainer.addView(messageView("Can't load channels right now.\nCheck your Wi-Fi and try again, or pull down to refresh."))
    }

    private fun sideButton(label: String, selected: Boolean, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            textSize = 19f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isFocusable = true
            setTextColor(Color.WHITE)
            setBackgroundColor(if (selected) Color.rgb(44, 95, 124) else Color.rgb(24, 52, 76))
            setPadding(20, 14, 20, 14)
            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(
                    if (hasFocus || selected) Color.rgb(66, 165, 245) else Color.rgb(24, 52, 76)
                )
                textSize = if (hasFocus) 21f else 19f
            }
            setOnClickListener { onClick() }
        }
    }

    private fun channelButton(channel: LiveChannel, selectedLineup: List<LiveChannel>): View {
        val programme = currentProgrammes[channel.id]
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(20, 14, 20, 14)
            setBackgroundColor(Color.rgb(18, 38, 56))

            setOnFocusChangeListener { _, hasFocus ->
                setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(18, 38, 56))
                (getChildAt(1) as? LinearLayout)
                    ?.let { (it.getChildAt(0) as? TextView)?.textSize = if (hasFocus) 22f else 20f }
            }

            val logoPlaceholder = TextView(context).apply {
                text = channel.name.firstOrNull()?.uppercase() ?: "?"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.rgb(10, 28, 46))
                layoutParams = LinearLayout.LayoutParams(72, 56).apply { rightMargin = 18 }
            }
            addView(logoPlaceholder)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val isFav = bookmarkStore.isFavorite(channel.id)
                val channelLabel = listOfNotNull(
                    channel.channelNumber?.let { "Ch. $it" },
                    if (isFav) "★ ${channel.name}" else channel.name
                ).joinToString("  ")

                addView(TextView(context).apply {
                    text = channelLabel
                    textSize = 20f
                    setTextColor(Color.WHITE)
                    maxLines = 1
                })
                addView(TextView(context).apply {
                    text = programme?.title ?: "No programme info right now"
                    textSize = 16f
                    setTextColor(Color.LTGRAY)
                    maxLines = 1
                    setPadding(0, 4, 0, 0)
                })
            })

            setOnClickListener {
                bookmarkStore.recordRecent(channel, programme?.title)
                val request = playbackCoordinator.buildRequest(channel, selectedLineup, programme?.title)
                (activity as? QuadTvNavigator)?.navigateToPlayer(request)
            }

            setOnLongClickListener {
                val isFavorite = bookmarkStore.toggleFavorite(channel, programme?.title)
                val nameView = (getChildAt(1) as? LinearLayout)?.getChildAt(0) as? TextView
                val label = listOfNotNull(
                    channel.channelNumber?.let { "Ch. $it" },
                    if (isFavorite) "★ ${channel.name}" else channel.name
                ).joinToString("  ")
                nameView?.text = label
                true
            }
        }
    }

    private fun messageView(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 22f
        setTextColor(Color.LTGRAY)
        setPadding(24, 18, 24, 18)
    }

    private fun buildLiveTvRepository(): LiveTvRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return LiveTvRepository(providerFeedRepository, okHttpClient)
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

private fun sortedGroupNames(groups: Set<String>): List<String> {
    return groups.sortedWith(compareBy<String> { isAdultGroupTitle(it) }.then(String.CASE_INSENSITIVE_ORDER))
}

private fun isAdultGroupTitle(group: String): Boolean {
    val lower = group.lowercase()
    return listOf("xxx", "adult", "18+", "mature", "porn").any { lower.contains(it) }
}
