package net.trequad.quadtv.auth

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

class CustomerLoginFragment : Fragment() {
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var continueButton: Button
    private lateinit var authRepository: CustomerAuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepository = buildAuthRepository()
    }

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
                text = "Login"
                textSize = 28f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(context).apply {
                text = "Sign in with your QuadTV username and PIN."
                textSize = 18f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                gravity = Gravity.CENTER
            })

            usernameInput = EditText(context).apply {
                hint = "QuadTV username"
                inputType = InputType.TYPE_CLASS_TEXT
                setSingleLine(true)
            }
            addView(usernameInput)

            passwordInput = EditText(context).apply {
                hint = "QuadTV PIN"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setSingleLine(true)
            }
            addView(passwordInput)

            statusText = TextView(context).apply {
                text = "Enter your username and PIN."
                textSize = 16f
                setTextColor(resources.getColor(R.color.quadtv_white, null))
                gravity = Gravity.CENTER
            }
            addView(statusText)

            continueButton = Button(context).apply {
                text = "Continue"
                setOnClickListener { submitLogin() }
            }
            addView(continueButton)
        }
    }

    private fun submitLogin() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()

        if (username.isBlank() || password.isBlank()) {
            statusText.text = "Enter your username and PIN."
            return
        }

        continueButton.isEnabled = false
        statusText.text = "Signing in…"

        lifecycleScope.launch {
            val result = try {
                Result.success(withContext(Dispatchers.IO) {
                    authRepository.login(username, password)
                })
            } catch (exception: Exception) {
                Result.failure(exception)
            }

            continueButton.isEnabled = true

            result.onSuccess { response ->
                val navigator = requireActivity() as QuadTvNavigator
                if (response.expired) {
                    navigator.navigateTo(QuadTvRoute.EXPIRED)
                } else {
                    navigator.navigateTo(QuadTvRoute.PROFILES)
                }
            }.onFailure {
                statusText.text = "Login failed. Check your credentials and try again."
            }
        }
    }

    private fun buildAuthRepository(): CustomerAuthRepository {
        val appContext = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val preferences = appContext.getSharedPreferences(
            CustomerSessionCache.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        return CustomerAuthRepository(apiService, CustomerSessionCache(preferences))
    }
}
