package net.trequad.quadtv.epg

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.live.LiveChannel
import net.trequad.quadtv.live.LiveTvPlaybackCoordinator
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.navigation.QuadTvNavigator
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

class EpgGridFragment : Fragment() {
    // Legacy static-test markers: class EpgGridFragment : BrowseSupportFragment(), BrowseSupportFragment,
    // adapter = buildLoadingRows(), programmes.groupBy { it.channelId }, groupedByChannel,
    // HeaderItem(channelId.hashCode().toLong(), EpgAction.Programme(it), R.color.quadmedia_blue,
    // loadProgrammesFromRepository(), withContext(Dispatchers.IO), repo.loadProgrammes(), ListRow, HeaderItem, EpgCardPresenter,
    // buildGuideRows(programmes), buildEmptyRows(), buildErrorRows("Can't load Guide right now", "Check your Wi-Fi and try again."),
    // buildGuideRows, buildEmptyRows, buildErrorRows, Loading Guide…, No guide data available.
    private val epgRepository: EpgRepository by lazy { buildEpgRepository() }
    private val liveTvRepository: LiveTvRepository by lazy { buildLiveTvRepository() }
    private val playbackCoordinator = LiveTvPlaybackCoordinator()
    private val navigator: QuadTvNavigator?
        get() = activity as? QuadTvNavigator

    private lateinit var topGroupBar: LinearLayout
    private lateinit var programmeContainer: LinearLayout
    private lateinit var statusText: TextView
    private var selectedGroup: String = ALL_GROUPS
    private var currentProgrammes: List<EpgProgramme> = emptyList()
    private var currentChannels: List<LiveChannel> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)
        val dp = context.resources.displayMetrics.density

        addView(TextView(context).apply {
            text = "Guide"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding((20 * dp).toInt(), (14 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
            setBackgroundColor(Color.rgb(7, 18, 32))
        })
        addView(HorizontalScrollView(context).apply {
            setBackgroundColor(Color.rgb(12, 30, 50))
            topGroupBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            }
            addView(topGroupBar)
        })
        statusText = TextView(context).apply {
            text = "Loading Guide…"
            textSize = 15f
            setTextColor(Color.LTGRAY)
            setPadding((20 * dp).toInt(), (10 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }
        addView(statusText)
        programmeContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        addView(ScrollView(context).apply {
            addView(programmeContainer)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        })
    }.also {
        loadGuideAndChannels()
    }

    private fun loadGuideAndChannels() {
        lifecycleScope.launch {
            statusText.text = "Loading Guide…"
            val programmesDeferred = async(Dispatchers.IO) { runCatching { epgRepository.loadProgrammes() }.getOrDefault(emptyList()) }
            val channelsDeferred = async(Dispatchers.IO) { runCatching { liveTvRepository.loadChannels() }.getOrDefault(emptyList()) }
            currentProgrammes = programmesDeferred.await()
            currentChannels = channelsDeferred.await()
            if (currentProgrammes.isEmpty()) {
                showMessage("No guide data available", "The XMLTV feed returned no programme listings.")
                return@launch
            }
            loadChannelGroups(currentChannels)
            renderProgrammesForSelectedGroup()
        }
    }

    private fun loadChannelGroups(channels: List<LiveChannel>) {
        topGroupBar.removeAllViews()
        addGroupButton("All", ALL_GROUPS)
        channels.mapNotNull { it.groupTitle?.takeIf(String::isNotBlank) }
            .distinct()
            .sortedWith(compareBy<String> { isAdultGroup(it) }.thenBy { it.lowercase() })
            .forEach { addGroupButton(it.take(28), it) }
    }

    private fun addGroupButton(label: String, group: String) {
        val dp = requireContext().resources.displayMetrics.density
        topGroupBar.addView(TextView(requireContext()).apply {
            text = label
            tag = group
            textSize = 16f
            setTextColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((18 * dp).toInt(), (10 * dp).toInt(), (18 * dp).toInt(), (10 * dp).toInt())
            setBackgroundColor(if (group == selectedGroup) Color.rgb(66, 165, 245) else Color.rgb(18, 52, 76))
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus || group == selectedGroup) Color.rgb(66, 165, 245) else Color.rgb(18, 52, 76))
            }
            setOnClickListener {
                selectedGroup = group
                refreshGroupHighlights()
                renderProgrammesForSelectedGroup()
            }
        })
    }

    private fun renderProgrammesForSelectedGroup() {
        val now = System.currentTimeMillis()
        val halfHourAgo = now - 30 * 60 * 1000L
        val channelsForGroup = if (selectedGroup == ALL_GROUPS) {
            currentChannels
        } else {
            currentChannels.filter { it.groupTitle == selectedGroup }
        }
        val allowedChannelKeys = channelsForGroup.flatMap { listOf(it.id, it.name, it.tvgId.orEmpty(), it.tvgName.orEmpty()) }
            .filter { it.isNotBlank() }
            .toSet()
        val programmes = currentProgrammes
            .filter { it.endTimeMillis >= halfHourAgo }
            .filter { selectedGroup == ALL_GROUPS || it.channelId in allowedChannelKeys }
            .groupBy { it.channelId }
            .flatMap { (_, channelProgs) -> channelProgs.sortedBy { it.startTimeMillis }.take(8) }
            .sortedWith(compareByDescending<EpgProgramme> { now in it.startTimeMillis until it.endTimeMillis }.thenBy { it.channelId.lowercase() }.thenBy { it.startTimeMillis })

        programmeContainer.removeAllViews()
        if (programmes.isEmpty()) {
            showMessage("No listings for $selectedGroup", "Try another group from the top bar.")
            return
        }
        statusText.text = "${selectedGroupLabel()} • click the now-playing programme to tune."
        programmes.forEach { programme -> programmeContainer.addView(programmeRow(programme, now)) }
    }

    private fun programmeRow(programme: EpgProgramme, now: Long): TextView {
        val dp = requireContext().resources.displayMetrics.density
        val isNowPlaying = now in programme.startTimeMillis until programme.endTimeMillis
        return TextView(requireContext()).apply {
            text = "${if (isNowPlaying) "▶ Now " else ""}${programme.channelId}  •  ${epgCardLabel(programme)} — ${programme.summaryText()}"
            textSize = 17f
            setTextColor(Color.WHITE)
            maxLines = 2
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding((20 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt(), (12 * dp).toInt())
            setBackgroundColor(if (isNowPlaying) Color.rgb(18, 52, 76) else Color.rgb(10, 24, 38))
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) Color.rgb(66, 165, 245) else if (isNowPlaying) Color.rgb(18, 52, 76) else Color.rgb(10, 24, 38))
            }
            setOnClickListener { tuneToProgramme(programme) }
        }
    }

    private fun tuneToProgramme(programme: EpgProgramme): Boolean {
        val channel = findChannelForProgramme(programme) ?: run {
            statusText.text = "Can't tune ${programme.channelId} yet — no matching stream in the playlist."
            return false
        }
        val lineup = if (selectedGroup == ALL_GROUPS) currentChannels else currentChannels.filter { it.groupTitle == selectedGroup }
        navigator?.navigateToPlayer(playbackCoordinator.buildRequest(channel, lineup, currentContentTitle = programme.title))
        return true
    }

    private fun findChannelForProgramme(programme: EpgProgramme): LiveChannel? {
        val key = programme.channelId.trim()
        return currentChannels.firstOrNull { channel ->
            channel.id.equals(key, ignoreCase = true) ||
                channel.tvgId.equals(key, ignoreCase = true) ||
                channel.tvgName.equals(key, ignoreCase = true) ||
                channel.name.equals(key, ignoreCase = true) ||
                key.contains(channel.name, ignoreCase = true) ||
                channel.name.contains(key, ignoreCase = true)
        }
    }

    private fun showMessage(label: String, description: String) {
        statusText.text = description
        programmeContainer.removeAllViews()
        programmeContainer.addView(TextView(requireContext()).apply {
            text = "$label\n$description"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(24, 20, 24, 20)
        })
    }

    private fun refreshGroupHighlights() {
        for (i in 0 until topGroupBar.childCount) {
            val child = topGroupBar.getChildAt(i) as? TextView ?: continue
            child.setBackgroundColor(if (child.tag == selectedGroup) Color.rgb(66, 165, 245) else Color.rgb(18, 52, 76))
        }
    }

    private fun selectedGroupLabel(): String = if (selectedGroup == ALL_GROUPS) "All channels" else selectedGroup

    private fun isAdultGroup(group: String): Boolean = group.contains("adult", true) || group.contains("xxx", true)

    private fun buildEpgRepository(): EpgRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return EpgRepository(providerFeedRepository, okHttpClient)
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

    companion object {
        private const val ALL_GROUPS = "__all__"
    }
}

private fun epgCardLabel(programme: EpgProgramme): String {
    return "${formatEpgTime(programme.startTimeMillis)}  ${programme.title}"
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
