package net.trequad.quadtv.updates

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

class UpdatePromptFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val status = requireArguments().getString(ARG_STATUS, STATUS_OPTIONAL)
        val title = if (status == STATUS_FORCED) "Update Required" else "QuadTV Update Available"
        val body = if (status == STATUS_FORCED) {
            "A signed APK update is required before QuadTV can continue. Follow the private sideload update path from your QuadTV operator."
        } else {
            "A signed QuadTV APK is available. You may continue watching now or follow the private sideload update path when convenient."
        }

        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            addView(TextView(context).apply {
                text = title
                textSize = 30f
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = body
                textSize = 20f
                gravity = Gravity.CENTER
            })
        }
    }

    companion object {
        private const val ARG_STATUS = "update_status"
        private const val STATUS_OPTIONAL = "optional"
        private const val STATUS_FORCED = "forced"

        fun optional() = UpdatePromptFragment().apply {
            arguments = Bundle().apply { putString(ARG_STATUS, STATUS_OPTIONAL) }
        }

        fun forced() = UpdatePromptFragment().apply {
            arguments = Bundle().apply { putString(ARG_STATUS, STATUS_FORCED) }
        }
    }
}
