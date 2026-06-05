package net.trequad.quadtv.auth

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R
import net.trequad.quadtv.navigation.QuadTvNavigator

class SubscriptionRequiredFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(96, 72, 96, 72)
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)

            addView(ImageView(context).apply {
                setImageResource(R.drawable.quadtv_logo_horizontal)
                contentDescription = "QuadTV by QuadMedia"
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    132
                )
            })

            addView(TextView(context).apply {
                text = "Please subscribe for access"
                textSize = 32f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "This feature is not included with your current QuadTV package. Contact QuadMedia to upgrade."
                textSize = 18f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                gravity = Gravity.CENTER
            })

            addView(Button(context).apply {
                text = "Back"
                setOnClickListener { (requireActivity() as QuadTvNavigator).goBack() }
            })
        }
    }
}
