package net.trequad.quadtv.player

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.core.cache.PlayerSettingsCache
import net.trequad.quadtv.live.BookmarkedLiveChannel
import net.trequad.quadtv.live.LiveChannelBookmarkStore
import net.trequad.quadtv.core.ui.QuadTvTheme
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerFragment : Fragment() {
    private var activePlayer: QuadTvPlayer? = null
    private val failureHandler = PlaybackFailureHandler()
    private lateinit var renderSurface: PlayerRenderSurface
    private lateinit var statusView: TextView
    private lateinit var retryButton: Button
    private lateinit var infoBannerContainer: LinearLayout
    private lateinit var infoBannerTitleView: TextView
    private lateinit var infoBannerGroupView: TextView
    private lateinit var infoBannerContentView: TextView
    private lateinit var infoBannerNextView: TextView
    private lateinit var infoBannerTimeView: TextView
    private lateinit var infoBannerProgressBar: ProgressBar
    private lateinit var playbackControlRow: LinearLayout
    private var pausePlayButton: ImageButton? = null
    private val infoBannerHideHandler = Handler(Looper.getMainLooper())
    private val startupStatusHideHandler = Handler(Looper.getMainLooper())
    private val diagnosticStatusHideHandler = Handler(Looper.getMainLooper())
    private var currentRequest: StreamPlaybackRequest? = null
    private var currentEngine: PlayerEngine = PlayerEngine.VLC
    private var isOnDemandPaused = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val request = requirePlaybackRequest()
        enterImmersivePlaybackMode()
        currentRequest = request
        val root = FrameLayout(requireContext()).apply {
            setBackgroundColor(QuadTvTheme.BACKGROUND)
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setOnKeyListener { _, keyCode, event -> handlePlaybackKey(keyCode, event) }
            setOnClickListener { showInfoBanner() }
        }
        root.keepScreenOn = true
        renderSurface = PlayerRenderSurface(
            vlcVideoLayout = VLCVideoLayout(requireContext()).apply {
                setBackgroundColor(Color.BLACK)
                visibility = View.VISIBLE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        )
        statusView = buildStatusView(request)
        retryButton = buildRetryButton()
        infoBannerContainer = buildInfoBanner(request)

        root.addView(renderSurface.vlcVideoLayout)
        root.addView(buildOverlay(statusView, retryButton))
        root.addView(infoBannerContainer)
        root.post { root.requestFocus() }
        startBundledPlayback(request)
        return root
    }

    private fun enterImmersivePlaybackMode() {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.decorView?.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun buildStatusView(request: StreamPlaybackRequest): TextView {
        return TextView(requireContext()).apply {
            text = "QuadMedia • QuadTV\nTuning to channel ${request.title ?: "stream"}…"
            textSize = 22f
            gravity = Gravity.START
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 8, 18, 32))
            setPadding(32, 20, 32, 20)
        }
    }

    private fun buildRetryButton(): Button {
        return Button(requireContext()).apply {
            text = "Retry playback"
            textSize = 18f
            visibility = View.GONE
            isFocusable = true
        }
    }

    private fun buildOverlay(statusView: TextView, retryButton: Button): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START or Gravity.TOP
            setPadding(48, 48, 48, 48)
            addView(statusView)
            addView(retryButton)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
    }

    private fun buildInfoBanner(request: StreamPlaybackRequest): LinearLayout {
        infoBannerTitleView = TextView(requireContext()).apply {
            textSize = 26f
            setTextColor(Color.WHITE)
        }
        infoBannerGroupView = TextView(requireContext()).apply {
            textSize = 18f
            setTextColor(QuadTvTheme.TEXT_SECONDARY)
        }
        infoBannerContentView = TextView(requireContext()).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        infoBannerNextView = TextView(requireContext()).apply {
            textSize = 18f
            setTextColor(QuadTvTheme.TEXT_PRIMARY)
        }
        infoBannerTimeView = TextView(requireContext()).apply {
            textSize = 18f
            gravity = Gravity.END
            setTextColor(QuadTvTheme.ACCENT)
        }
        infoBannerProgressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = if (request.isLive) 50 else 0
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            visibility = View.GONE
            setBackgroundColor(Color.argb(220, 7, 24, 39))
            setPadding(48, 28, 48, 28)
            addView(infoBannerTitleView)
            addView(infoBannerGroupView)
            addView(infoBannerContentView)
            addView(infoBannerNextView)
            addView(infoBannerTimeView)
            addView(infoBannerProgressBar)
            addView(buildPlaybackControlRow(request))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
    }

    private fun updateInfoBanner(request: StreamPlaybackRequest, engine: PlayerEngine) {
        val currentTime = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        infoBannerTitleView.text = "QuadTV • Channel: ${request.title ?: "Stream"}"
        infoBannerGroupView.text = "Group: ${request.groupTitle?.takeIf { it.isNotBlank() } ?: "Live TV"}"
        infoBannerContentView.text = "Now playing: ${request.contentTitle?.takeIf { it.isNotBlank() } ?: request.subtitle ?: "Live TV"}"
        val channelHint = if (request.isLive && request.channelUpTitle != null && request.channelDownTitle != null) {
            "CH+/Up: ${request.channelUpTitle} • CH-/Down: ${request.channelDownTitle}"
        } else {
            "${request.subtitle ?: "Playback"} • Next: ${request.nextTitle ?: "No upcoming programme data"}"
        }
        infoBannerNextView.text = "$channelHint • Player: ${engine.name}"
        infoBannerTimeView.text = currentTime
        infoBannerProgressBar.progress = if (request.isLive) 50 else 0
    }

    private fun buildPlaybackControlRow(request: StreamPlaybackRequest): LinearLayout {
        playbackControlRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 0)
            addView(controlButton("⌂ Home") { (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.HOME) })
            addView(controlButton("Back") { requireActivity().onBackPressedDispatcher.onBackPressed() })
            if (request.isLive) {
                addView(iconControlButton(android.R.drawable.btn_star_big_off, "Add or remove favorite") { toggleCurrentFavorite() })
                addView(iconControlButton(android.R.drawable.ic_media_previous, "Previous channel") { switchLiveChannel(offset = -1) })
                addView(iconControlButton(android.R.drawable.ic_media_next, "Next channel") { switchLiveChannel(offset = 1) })
            }
            if (!request.isLive) {
                addView(iconControlButton(android.R.drawable.ic_media_rew, "Rewind 30 seconds") { seekOnDemand(offsetMs = -SEEK_STEP_MS) })
                pausePlayButton = iconControlButton(android.R.drawable.ic_media_pause, "Pause or resume") { togglePauseResume() }
                    .also { addView(it) }
                addView(iconControlButton(android.R.drawable.ic_media_ff, "Forward 30 seconds") { seekOnDemand(offsetMs = SEEK_STEP_MS) })
            }
        }
        return playbackControlRow
    }

    private fun controlButton(label: String, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = label
            textSize = 18f
            isFocusable = true
            setPadding(20, 10, 20, 10)
            setOnClickListener { onClick() }
        }
    }

    /** Icon transport buttons (play/pause/seek/channel): D-pad focusable, 56dp targets. */
    private fun iconControlButton(iconRes: Int, description: String, onClick: () -> Unit): ImageButton {
        val dp = requireContext().resources.displayMetrics.density
        return ImageButton(requireContext()).apply {
            setImageResource(iconRes)
            contentDescription = description
            isFocusable = true
            isFocusableInTouchMode = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setColorFilter(QuadTvTheme.TEXT_PRIMARY)
            setBackgroundColor(QuadTvTheme.SURFACE)
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt()).apply {
                leftMargin = (8 * dp).toInt()
            }
            setOnFocusChangeListener { view, hasFocus ->
                view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
                view.scaleX = if (hasFocus) 1.08f else 1f
                view.scaleY = if (hasFocus) 1.08f else 1f
            }
            setOnClickListener { onClick() }
        }
    }

    private fun handlePlaybackKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                toggleInfoBanner()
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchLiveChannel(offset = 1)
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                switchLiveChannel(offset = -1)
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                togglePauseResume()
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekOnDemand(offsetMs = -SEEK_STEP_MS)
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekOnDemand(offsetMs = SEEK_STEP_MS)
            }
            KeyEvent.KEYCODE_BACK -> {
                if (infoBannerContainer.visibility == View.VISIBLE) {
                    hideInfoBanner()
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun toggleInfoBanner() {
        // Bring up the info banner or dismiss it based on current state
        if (infoBannerContainer.visibility == View.VISIBLE) {
            hideInfoBanner()
        } else {
            showInfoBanner()
        }
    }

    private fun switchLiveChannel(offset: Int): Boolean {
        val request = currentRequest ?: return false
        if (!request.isLive || request.liveChannelUrls.size < 2 || request.liveChannelIndex !in request.liveChannelUrls.indices) {
            return false
        }
        val targetIndex = Math.floorMod(request.liveChannelIndex + offset, request.liveChannelUrls.size)
        val switchedRequest = liveChannelRequestAt(request, targetIndex)
        statusView.text = "QuadMedia • QuadTV\nTuning to channel ${switchedRequest.title ?: "Live TV"}…"
        statusView.visibility = View.VISIBLE
        startBundledPlayback(switchedRequest.copy(bufferConfig = request.bufferConfig))
        showInfoBanner()
        return true
    }

    private fun togglePauseResume(): Boolean {
        val request = currentRequest ?: return false
        if (request.isLive) return false
        if (isOnDemandPaused) {
            activePlayer?.resume()
            isOnDemandPaused = false
            pausePlayButton?.setImageResource(android.R.drawable.ic_media_pause)
            pausePlayButton?.contentDescription = "Pause"
            statusView.text = "QuadMedia • QuadTV\nResumed ${request.title ?: "on-demand content"}"
        } else {
            activePlayer?.pause()
            isOnDemandPaused = true
            pausePlayButton?.setImageResource(android.R.drawable.ic_media_play)
            pausePlayButton?.contentDescription = "Resume"
            statusView.text = "QuadMedia • QuadTV\nPaused ${request.title ?: "on-demand content"}"
        }
        statusView.visibility = View.VISIBLE
        showInfoBanner()
        hideStartupStatusSoon()
        return true
    }

    private fun seekOnDemand(offsetMs: Long): Boolean {
        val request = currentRequest ?: return false
        if (request.isLive) return false
        activePlayer?.seekBy(offsetMs)
        val direction = if (offsetMs < 0) "Rewound" else "Skipped forward"
        statusView.text = "QuadMedia • QuadTV\n$direction 30 seconds"
        statusView.visibility = View.VISIBLE
        showInfoBanner()
        hideStartupStatusSoon()
        return true
    }

    private fun liveChannelRequestAt(request: StreamPlaybackRequest, targetIndex: Int): StreamPlaybackRequest {
        val upIndex = Math.floorMod(targetIndex + 1, request.liveChannelUrls.size)
        val downIndex = Math.floorMod(targetIndex - 1, request.liveChannelUrls.size)
        return request.copy(
            url = request.liveChannelUrls[targetIndex],
            channelId = request.liveChannelIds.getOrNull(targetIndex),
            title = request.liveChannelTitles.getOrNull(targetIndex) ?: "Live TV",
            groupTitle = request.liveChannelGroupTitles.getOrNull(targetIndex)?.takeIf { it.isNotBlank() } ?: request.groupTitle,
            contentTitle = request.liveChannelContentTitles.getOrNull(targetIndex)?.takeIf { it.isNotBlank() } ?: "Live TV",
            subtitle = "Live TV",
            nextTitle = "Channel up: ${request.liveChannelTitles.getOrNull(upIndex) ?: "Live TV"}",
            channelUpUrl = request.liveChannelUrls.getOrNull(upIndex),
            channelUpTitle = request.liveChannelTitles.getOrNull(upIndex),
            channelDownUrl = request.liveChannelUrls.getOrNull(downIndex),
            channelDownTitle = request.liveChannelTitles.getOrNull(downIndex),
            liveChannelIndex = targetIndex
        )
    }


    private fun toggleCurrentFavorite() {
        val request = currentRequest ?: return
        val channelId = request.channelId ?: return
        val bookmarked = BookmarkedLiveChannel(
            id = channelId,
            name = request.title ?: "Live TV",
            streamUrl = request.url,
            groupTitle = request.groupTitle,
            contentTitle = request.contentTitle
        )
        val isFavorite = LiveChannelBookmarkStore(requireContext().applicationContext).toggleFavorite(bookmarked)
        statusView.text = "QuadMedia • QuadTV\n${if (isFavorite) "Added favorite" else "Removed favorite"}: ${bookmarked.name}"
        statusView.visibility = View.VISIBLE
        showInfoBanner()
        hideStartupStatusSoon()
    }

    private fun showInfoBanner() {
        currentRequest?.let { updateInfoBanner(it, currentEngine) }
        infoBannerContainer.visibility = View.VISIBLE
        infoBannerHideHandler.removeCallbacksAndMessages(null)
        infoBannerHideHandler.postDelayed({ hideInfoBanner() }, INFO_BANNER_AUTO_HIDE_MS)
    }

    private fun hideInfoBanner() {
        infoBannerHideHandler.removeCallbacksAndMessages(null)
        infoBannerContainer.visibility = View.GONE
    }

    private fun hideStartupStatusSoon() {
        startupStatusHideHandler.removeCallbacksAndMessages(null)
        startupStatusHideHandler.postDelayed({
            statusView.visibility = View.GONE
        }, STARTUP_STATUS_AUTO_HIDE_MS)
    }

    private fun showStartupStatus() {
        startupStatusHideHandler.removeCallbacksAndMessages(null)
        statusView.visibility = View.VISIBLE
    }

    private fun showPlaybackDiagnostic(status: String) {
        // VLC: status events are translated to friendly copy before display
        Handler(Looper.getMainLooper()).post {
            if (!isAdded) return@post
            val friendlyStatus = if (status.contains("Buffer", ignoreCase = true)) {
                "Tuning to channel…"
            } else {
                "Starting video…"
            }
            statusView.text = "QuadMedia • QuadTV\n$friendlyStatus"
            statusView.visibility = View.VISIBLE
            diagnosticStatusHideHandler.removeCallbacksAndMessages(null)
            diagnosticStatusHideHandler.postDelayed({
                if (isAdded) statusView.visibility = View.GONE
            }, DIAGNOSTIC_STATUS_AUTO_HIDE_MS)
        }
    }

    private fun startBundledPlayback(request: StreamPlaybackRequest) {
        val settings = PlayerSettingsCache(
            requireContext().getSharedPreferences(PlayerSettingsCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).load()
        val selectedEngine = settings.defaultEngine
        val playableRequest = request.copy(
            bufferConfig = settings.bufferConfig,
            preferredAudioLanguage = request.preferredAudioLanguage ?: settings.preferredAudioLanguage,
            preferredSubtitleLanguage = request.preferredSubtitleLanguage ?: settings.preferredSubtitleLanguage
        )
        currentRequest = playableRequest
        recordRecentLiveChannel(playableRequest)
        startBundledPlayback(playableRequest, selectedEngine)
    }


    private fun recordRecentLiveChannel(request: StreamPlaybackRequest) {
        val channelId = request.channelId ?: return
        if (!request.isLive) return
        LiveChannelBookmarkStore(requireContext().applicationContext).recordRecent(
            BookmarkedLiveChannel(
                id = channelId,
                name = request.title ?: "Live TV",
                streamUrl = request.url,
                groupTitle = request.groupTitle,
                contentTitle = request.contentTitle
            )
        )
    }

    private fun startBundledPlayback(playableRequest: StreamPlaybackRequest, selectedEngine: PlayerEngine) {
        activePlayer?.release()
        activePlayer = null
        isOnDemandPaused = false
        pausePlayButton?.setImageResource(android.R.drawable.ic_media_pause)
        pausePlayButton?.contentDescription = "Pause"
        currentEngine = selectedEngine
        retryButton.visibility = View.GONE
        showStartupStatus()
        renderSurface.vlcVideoLayout.visibility = View.VISIBLE
        updateInfoBanner(playableRequest, selectedEngine)
        try {
            activePlayer = createPlayer(selectedEngine)
            activePlayer?.attachSurface(renderSurface)
            activePlayer?.setPlaybackErrorListener { throwable ->
                handlePlaybackFailure(playableRequest, selectedEngine, throwable)
            }
            activePlayer?.setPlaybackStatusListener { status ->
                showPlaybackDiagnostic(status)
            }
            activePlayer?.play(playableRequest)
            statusView.text = "QuadMedia • QuadTV\nTuning to channel ${playableRequest.title ?: "stream"}…"
            hideStartupStatusSoon()
        } catch (error: Exception) {
            handlePlaybackFailure(playableRequest, selectedEngine, error)
        }
    }

    private fun handlePlaybackFailure(
        playableRequest: StreamPlaybackRequest,
        selectedEngine: PlayerEngine,
        throwable: Throwable
    ) {
        Handler(Looper.getMainLooper()).post {
            if (!isAdded || currentEngine != selectedEngine) return@post
            val alternate = failureHandler.alternateEngine(selectedEngine)
            activePlayer?.setPlaybackErrorListener(null)
            showStartupStatus()
            if (alternate == null) {
                statusView.text = "QuadMedia • QuadTV\nPlayback failed with embedded VLC.\n${throwable.message ?: "No player error details available."}"
                retryButton.visibility = View.GONE
                return@post
            }
        }
    }

    private fun createPlayer(engine: PlayerEngine): QuadTvPlayer {
        return when (engine) {
            PlayerEngine.VLC -> VlcPlayerController(requireContext())
        }
    }

    private fun requirePlaybackRequest(): StreamPlaybackRequest {
        val args = requireArguments()
        return StreamPlaybackRequest(
            url = requireNotNull(args.getString(ARG_URL)),
            channelId = args.getString(ARG_CHANNEL_ID),
            title = args.getString(ARG_TITLE),
            groupTitle = args.getString(ARG_GROUP_TITLE),
            contentTitle = args.getString(ARG_CONTENT_TITLE),
            subtitle = args.getString(ARG_SUBTITLE),
            nextTitle = args.getString(ARG_NEXT_TITLE),
            isLive = args.getBoolean(ARG_IS_LIVE),
            channelUpUrl = args.getString(ARG_CHANNEL_UP_URL),
            channelUpTitle = args.getString(ARG_CHANNEL_UP_TITLE),
            channelDownUrl = args.getString(ARG_CHANNEL_DOWN_URL),
            channelDownTitle = args.getString(ARG_CHANNEL_DOWN_TITLE),
            liveChannelIds = args.getStringArrayList(ARG_LIVE_CHANNEL_IDS).orEmpty(),
            liveChannelUrls = args.getStringArrayList(ARG_LIVE_CHANNEL_URLS).orEmpty(),
            liveChannelTitles = args.getStringArrayList(ARG_LIVE_CHANNEL_TITLES).orEmpty(),
            liveChannelGroupTitles = args.getStringArrayList(ARG_LIVE_CHANNEL_GROUP_TITLES).orEmpty(),
            liveChannelContentTitles = args.getStringArrayList(ARG_LIVE_CHANNEL_CONTENT_TITLES).orEmpty(),
            liveChannelIndex = args.getInt(ARG_LIVE_CHANNEL_INDEX, -1),
            preferredAudioLanguage = args.getString(ARG_AUDIO_LANGUAGE),
            preferredSubtitleLanguage = args.getString(ARG_SUBTITLE_LANGUAGE),
            bufferConfig = BufferConfig(
                sizeSeconds = args.getInt(ARG_BUFFER_SECONDS, 30),
                strategy = runCatching {
                    BufferStrategy.valueOf(args.getString(ARG_BUFFER_STRATEGY) ?: BufferStrategy.ADAPTIVE.name)
                }.getOrDefault(BufferStrategy.ADAPTIVE)
            )
        )
    }

    override fun onStop() {
        activePlayer?.stop()
        super.onStop()
    }

    override fun onDestroyView() {
        infoBannerHideHandler.removeCallbacksAndMessages(null)
        startupStatusHideHandler.removeCallbacksAndMessages(null)
        diagnosticStatusHideHandler.removeCallbacksAndMessages(null)
        activePlayer?.release()
        activePlayer = null
        currentRequest = null
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_CHANNEL_ID = "channel_id"
        private const val ARG_TITLE = "title"
        private const val ARG_GROUP_TITLE = "group_title"
        private const val ARG_CONTENT_TITLE = "content_title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_NEXT_TITLE = "next_title"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_CHANNEL_UP_URL = "channel_up_url"
        private const val ARG_CHANNEL_UP_TITLE = "channel_up_title"
        private const val ARG_CHANNEL_DOWN_URL = "channel_down_url"
        private const val ARG_CHANNEL_DOWN_TITLE = "channel_down_title"
        private const val ARG_LIVE_CHANNEL_IDS = "live_channel_ids"
        private const val ARG_LIVE_CHANNEL_URLS = "live_channel_urls"
        private const val ARG_LIVE_CHANNEL_TITLES = "live_channel_titles"
        private const val ARG_LIVE_CHANNEL_GROUP_TITLES = "live_channel_group_titles"
        private const val ARG_LIVE_CHANNEL_CONTENT_TITLES = "live_channel_content_titles"
        private const val ARG_LIVE_CHANNEL_INDEX = "live_channel_index"
        private const val ARG_AUDIO_LANGUAGE = "audio_language"
        private const val ARG_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val ARG_BUFFER_SECONDS = "buffer_seconds"
        private const val ARG_BUFFER_STRATEGY = "buffer_strategy"
        private const val INFO_BANNER_AUTO_HIDE_MS = 5_000L
        private const val STARTUP_STATUS_AUTO_HIDE_MS = 2_500L
        private const val DIAGNOSTIC_STATUS_AUTO_HIDE_MS = 8_000L
        private const val SEEK_STEP_MS = 30_000L

        fun newInstance(request: StreamPlaybackRequest): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, request.url)
                    putString(ARG_CHANNEL_ID, request.channelId)
                    putString(ARG_TITLE, request.title)
                    putString(ARG_GROUP_TITLE, request.groupTitle)
                    putString(ARG_CONTENT_TITLE, request.contentTitle)
                    putString(ARG_SUBTITLE, request.subtitle)
                    putString(ARG_NEXT_TITLE, request.nextTitle)
                    putBoolean(ARG_IS_LIVE, request.isLive)
                    putString(ARG_CHANNEL_UP_URL, request.channelUpUrl)
                    putString(ARG_CHANNEL_UP_TITLE, request.channelUpTitle)
                    putString(ARG_CHANNEL_DOWN_URL, request.channelDownUrl)
                    putString(ARG_CHANNEL_DOWN_TITLE, request.channelDownTitle)
                    putStringArrayList(ARG_LIVE_CHANNEL_IDS, ArrayList(request.liveChannelIds))
                    putStringArrayList(ARG_LIVE_CHANNEL_URLS, ArrayList(request.liveChannelUrls))
                    putStringArrayList(ARG_LIVE_CHANNEL_TITLES, ArrayList(request.liveChannelTitles))
                    putStringArrayList(ARG_LIVE_CHANNEL_GROUP_TITLES, ArrayList(request.liveChannelGroupTitles))
                    putStringArrayList(ARG_LIVE_CHANNEL_CONTENT_TITLES, ArrayList(request.liveChannelContentTitles))
                    putInt(ARG_LIVE_CHANNEL_INDEX, request.liveChannelIndex)
                    putString(ARG_AUDIO_LANGUAGE, request.preferredAudioLanguage)
                    putString(ARG_SUBTITLE_LANGUAGE, request.preferredSubtitleLanguage)
                    putInt(ARG_BUFFER_SECONDS, request.bufferConfig.sizeSeconds)
                    putString(ARG_BUFFER_STRATEGY, request.bufferConfig.strategy.name)
                }
            }
        }
    }
}
