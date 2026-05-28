package net.trequad.quadtv.epg

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R

class EpgGridFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(resources.getColor(R.color.quadtv_navy, null))

            addView(TextView(context).apply {
                text = "QuadTV Guide"
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(resources.getColor(R.color.quadmedia_blue, null))
                setPadding(24, 16, 24, 16)
            })

            addView(TextView(context).apply {
                text = "Cable-style EPG scaffold: time axis across the top, channel rows down the left, program blocks in the grid, preview panel on the side, D-pad focus states pending."
                textSize = 20f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                setPadding(24, 24, 24, 24)
            })
        }
    }
}
