package net.trequad.quadtv.player

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.core.cache.PlayerSettingsCache

class PlayerFragment : Fragment() {
    private var activePlayer: QuadTvPlayer? = null
    private val failureHandler = PlaybackFailureHandler()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val request = requirePlaybackRequest()
        val statusView = TextView(requireContext()).apply {
            text = "QuadMedia • QuadTV\nStarting ${request.title ?: "stream"}"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(8, 18, 32))
            isFocusable = true
        }
        startBundledPlayback(request, statusView)
        return statusView
    }

    private fun startBundledPlayback(request: StreamPlaybackRequest, statusView: TextView) {
        val settings = PlayerSettingsCache(
            requireContext().getSharedPreferences(PlayerSettingsCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).load()
        val selectedEngine = settings.defaultEngine
        val playableRequest = request.copy(
            bufferConfig = settings.bufferConfig,
            preferredAudioLanguage = request.preferredAudioLanguage ?: settings.preferredAudioLanguage,
            preferredSubtitleLanguage = request.preferredSubtitleLanguage ?: settings.preferredSubtitleLanguage
        )
        try {
            activePlayer = createPlayer(selectedEngine)
            activePlayer?.play(playableRequest)
            statusView.text = "QuadMedia • QuadTV\nPlaying ${playableRequest.title ?: "stream"} with ${selectedEngine.name}"
        } catch (_: Exception) {
            val alternate = failureHandler.alternateEngine(selectedEngine)
            statusView.text = "QuadMedia • QuadTV\nPlayback failed; retry with ${alternate.name}"
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
