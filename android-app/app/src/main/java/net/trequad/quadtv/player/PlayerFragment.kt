package net.trequad.quadtv.player

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.media3.ui.PlayerView
import net.trequad.quadtv.core.cache.PlayerSettingsCache

class PlayerFragment : Fragment() {
    private var activePlayer: QuadTvPlayer? = null
    private val failureHandler = PlaybackFailureHandler()
    private lateinit var renderSurface: PlayerRenderSurface
    private lateinit var statusView: TextView
    private lateinit var retryButton: Button
    private var currentRequest: StreamPlaybackRequest? = null

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
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
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

        root.addView(renderSurface.playerView)
        root.addView(renderSurface.vlcSurfaceView)
        root.addView(buildOverlay(statusView, retryButton))
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
            gravity = Gravity.START or Gravity.BOTTOM
            setPadding(48, 48, 48, 48)
            addView(statusView)
            addView(retryButton)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
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
        startBundledPlayback(playableRequest, selectedEngine)
    }

    private fun startBundledPlayback(playableRequest: StreamPlaybackRequest, selectedEngine: PlayerEngine) {
        activePlayer?.release()
        activePlayer = null
        retryButton.visibility = View.GONE
        renderSurface.playerView.visibility = if (selectedEngine == PlayerEngine.EXOPLAYER) View.VISIBLE else View.GONE
        renderSurface.vlcSurfaceView.visibility = if (selectedEngine == PlayerEngine.VLC) View.VISIBLE else View.GONE
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
        activePlayer?.release()
        activePlayer = null
        currentRequest = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_TITLE = "title"
        private const val ARG_IS_LIVE = "is_live"
        private const val ARG_AUDIO_LANGUAGE = "audio_language"
        private const val ARG_SUBTITLE_LANGUAGE = "subtitle_language"
        private const val ARG_BUFFER_SECONDS = "buffer_seconds"
        private const val ARG_BUFFER_STRATEGY = "buffer_strategy"

        fun newInstance(request: StreamPlaybackRequest): PlayerFragment {
            return PlayerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, request.url)
                    putString(ARG_TITLE, request.title)
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
