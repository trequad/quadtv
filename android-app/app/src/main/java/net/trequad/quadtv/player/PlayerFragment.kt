package net.trequad.quadtv.player

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.ui.PlayerView
import net.trequad.quadtv.core.cache.PlayerSettingsCache
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
    private lateinit var infoBannerNextView: TextView
    private lateinit var infoBannerTimeView: TextView
    private lateinit var infoBannerProgressBar: ProgressBar
    private val infoBannerHideHandler = Handler(Looper.getMainLooper())
    private var currentRequest: StreamPlaybackRequest? = null
    private var currentEngine: PlayerEngine = PlayerEngine.EXOPLAYER

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val request = requirePlaybackRequest()
        currentRequest = request
        val root = FrameLayout(requireContext()).apply {
            setBackgroundColor(Color.rgb(8, 18, 32))
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setOnKeyListener { _, keyCode, event -> handlePlaybackKey(keyCode, event) }
        }
        renderSurface = PlayerRenderSurface(
            playerView = PlayerView(requireContext()).apply {
                useController = true
                setBackgroundColor(Color.BLACK)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            },
            vlcSurfaceView = SurfaceView(requireContext()).apply {
                setBackgroundColor(Color.BLACK)
                visibility = View.GONE
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        )
        statusView = buildStatusView(request)
        retryButton = buildRetryButton()
        infoBannerContainer = buildInfoBanner(request)

        root.addView(renderSurface.playerView)
        root.addView(renderSurface.vlcSurfaceView)
        root.addView(buildOverlay(statusView, retryButton))
        root.addView(infoBannerContainer)
        root.post { root.requestFocus() }
        startBundledPlayback(request)
        return root
    }

    private fun buildStatusView(request: StreamPlaybackRequest): TextView {
        return TextView(requireContext()).apply {
            text = "QuadMedia • QuadTV\nStarting ${request.title ?: "stream"}\nBring up the info banner for channel details."
            textSize = 22f
            gravity = Gravity.START
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(170, 8, 18, 32))
            setPadding(32, 20, 32, 20)
        }
    }

    private fun buildRetryButton(): Button {
        return Button(requireContext()).apply {
            text = "Retry with alternate player"
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
        infoBannerNextView = TextView(requireContext()).apply {
            textSize = 18f
            setTextColor(Color.rgb(244, 248, 251))
        }
        infoBannerTimeView = TextView(requireContext()).apply {
            textSize = 18f
            gravity = Gravity.END
            setTextColor(Color.rgb(66, 165, 245))
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
            addView(infoBannerNextView)
            addView(infoBannerTimeView)
            addView(infoBannerProgressBar)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
    }

    private fun updateInfoBanner(request: StreamPlaybackRequest, engine: PlayerEngine) {
        val currentTime = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        infoBannerTitleView.text = "QuadTV • Current: ${request.title ?: "Stream"}"
        infoBannerNextView.text = "${request.subtitle ?: "Playback"} • Next: ${request.nextTitle ?: "No upcoming programme data"} • Player: ${engine.name}"
        infoBannerTimeView.text = currentTime
        infoBannerProgressBar.progress = if (request.isLive) 50 else 0
    }

    private fun handlePlaybackKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                toggleInfoBanner()
                true
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
        if (infoBannerContainer.visibility == View.VISIBLE) {
            hideInfoBanner()
        } else {
            showInfoBanner()
        }
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
        startBundledPlayback(playableRequest, selectedEngine)
    }

    private fun startBundledPlayback(playableRequest: StreamPlaybackRequest, selectedEngine: PlayerEngine) {
        activePlayer?.release()
        activePlayer = null
        currentEngine = selectedEngine
        retryButton.visibility = View.GONE
        renderSurface.playerView.visibility = if (selectedEngine == PlayerEngine.EXOPLAYER) View.VISIBLE else View.GONE
        renderSurface.vlcSurfaceView.visibility = if (selectedEngine == PlayerEngine.VLC) View.VISIBLE else View.GONE
        updateInfoBanner(playableRequest, selectedEngine)
        try {
            activePlayer = createPlayer(selectedEngine)
            activePlayer?.attachSurface(renderSurface)
            activePlayer?.play(playableRequest)
            statusView.text = "QuadMedia • QuadTV\nPlaying ${playableRequest.title ?: "stream"} with ${selectedEngine.name}\nBring up the info banner for channel details."
        } catch (_: Exception) {
            val alternate = failureHandler.alternateEngine(selectedEngine)
            statusView.text = "QuadMedia • QuadTV\nPlayback failed; retry with ${alternate.name}"
            retryButton.text = "Retry with ${alternate.name}"
            retryButton.visibility = View.VISIBLE
            retryButton.setOnClickListener {
                startBundledPlayback(playableRequest, alternate)
            }
        }
    }

    private fun createPlayer(engine: PlayerEngine): QuadTvPlayer {
        return when (engine) {
            PlayerEngine.EXOPLAYER -> ExoPlayerController(requireContext())
            PlayerEngine.VLC -> VlcPlayerController(requireContext())
        }
    }

    private fun requirePlaybackRequest(): StreamPlaybackRequest {
        val args = requireArguments()
        return StreamPlaybackRequest(
            url = requireNotNull(args.getString(ARG_URL)),
            title = args.getString(ARG_TITLE),
            subtitle = args.getString(ARG_SUBTITLE),
            nextTitle = args.getString(ARG_NEXT_TITLE),
            isLive = args.getBoolean(ARG_IS_LIVE),
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
        activePlayer?.release()
        activePlayer = null
        currentRequest = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"
        private const val ARG_SUBTITLE = "subtitle"
        private const val ARG_NEXT_TITLE = "next_title"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_AUDIO_LANGUAGE = "audio_language"
        private const val ARG_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val ARG_BUFFER_SECONDS = "buffer_seconds"
        private const val ARG_BUFFER_STRATEGY = "buffer_strategy"
        private const val INFO_BANNER_AUTO_HIDE_MS = 5_000L

        fun newInstance(request: StreamPlaybackRequest): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, request.url)
                    putString(ARG_TITLE, request.title)
                    putString(ARG_SUBTITLE, request.subtitle)
                    putString(ARG_NEXT_TITLE, request.nextTitle)
                    putBoolean(ARG_IS_LIVE, request.isLive)
                    putString(ARG_AUDIO_LANGUAGE, request.preferredAudioLanguage)
                    putString(ARG_SUBTITLE_LANGUAGE, request.preferredSubtitleLanguage)
                    putInt(ARG_BUFFER_SECONDS, request.bufferConfig.sizeSeconds)
                    putString(ARG_BUFFER_STRATEGY, request.bufferConfig.strategy.name)
                }
            }
        }
    }
}
