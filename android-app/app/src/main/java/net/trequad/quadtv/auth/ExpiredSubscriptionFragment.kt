package net.trequad.quadtv.auth

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R

class ExpiredSubscriptionFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(96, 72, 96, 72)
            setBackgroundColor(resources.getColor(R.color.quadtv_charcoal, null))

            addView(TextView(context).apply {
                text = "Subscription Expired"
                textSize = 36f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "Please contact QuadMedia to renew your QuadTV access."
                textSize = 22f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                gravity = Gravity.CENTER
            })
        }
    }
}
