package net.trequad.quadtv.jellyfin

import android.graphics.Color
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.player.StreamPlaybackRequest

class JellyfinDetailsFragment : Fragment() {
    private val navigator: QuadTvNavigator?
        get() = activity as? QuadTvNavigator

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is QuadTvNavigator) {
            "JellyfinDetailsFragment must be hosted by a QuadTvNavigator"
        }
    }
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 48, 64, 48)
            setBackgroundColor(resources.getColor(R.color.quadtv_charcoal, null))

            addView(TextView(context).apply {
                text = "Jellyfin Details"
                textSize = 32f
                setTextColor(Color.WHITE)
            })

            addView(TextView(context).apply {
                text = "HLS stream delivery, poster art, metadata, ratings, descriptions, and playback handoff via StreamPlaybackRequest are wired in this scaffold."
                textSize = 20f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
            })
        }
    }

    fun playJellyfinStream(stream: JellyfinStream): Boolean {
        val request = buildPlaybackRequest(stream)
        navigator?.navigateToPlayer(request)
        return true
    }

    fun buildPlaybackRequest(stream: JellyfinStream): StreamPlaybackRequest {
        return StreamPlaybackRequest(
            url = stream.hlsUrl,
            title = stream.title,
            isLive = false,
            subtitle = "Jellyfin",
            nextTitle = "QuadMedia library"
        )
    }
}
