package net.trequad.quadtv.auth

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R

class CustomerLoginFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(96, 72, 96, 72)
            setBackgroundColor(resources.getColor(R.color.quadtv_navy, null))

            addView(TextView(context).apply {
                text = "QuadTV Login"
                textSize = 34f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "Sign in with your QuadMedia provider credentials."
                textSize = 18f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                gravity = Gravity.CENTER
            })

            addView(EditText(context).apply {
                hint = "Provider username"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
            })

            addView(EditText(context).apply {
                hint = "Provider password"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
            })

            addView(Button(context).apply {
                text = "Continue"
            })
        }
    }
}
