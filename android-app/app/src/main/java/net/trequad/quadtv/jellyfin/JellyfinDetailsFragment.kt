package net.trequad.quadtv.jellyfin

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R
import net.trequad.quadtv.player.StreamPlaybackRequest

class JellyfinDetailsFragment : Fragment() {
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

    fun buildPlaybackRequest(stream: JellyfinStream): StreamPlaybackRequest {
        return StreamPlaybackRequest(url = stream.hlsUrl, title = stream.title, isLive = false)
    }
}
