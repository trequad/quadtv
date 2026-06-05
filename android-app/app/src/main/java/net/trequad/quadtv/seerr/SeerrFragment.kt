package net.trequad.quadtv.seerr

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.navigation.QuadTvNavigator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SeerrFragment : Fragment() {
    private lateinit var webView: WebView

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(7, 24, 39))

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.rgb(7, 18, 32))
                setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())

                addView(TextView(context).apply {
                    text = "QuadMedia Request"
                    textSize = 22f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                addView(TextView(context).apply {
                    text = "Back"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(126, 203, 255))
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setPadding((18 * dp).toInt(), (10 * dp).toInt(), (18 * dp).toInt(), (10 * dp).toInt())
                    setBackgroundColor(Color.rgb(10, 24, 38))
                    setOnFocusChangeListener { view, hasFocus ->
                        view.setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(10, 24, 38))
                    }
                    setOnClickListener { handleBack() }
                })
            })

            webView = WebView(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundColor(Color.rgb(7, 24, 39))
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }
            addView(webView)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handleBack()
            }
        )
        authenticateAndLoad()
    }

    private fun authenticateAndLoad() {
        lifecycleScope.launch {
            val cookie = withContext(Dispatchers.IO) { fetchSeerrSessionCookie() }
            if (cookie != null) {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setCookie(QuadTvConfig.SEERR_BASE_URL, cookie)
                cookieManager.flush()
            }
            if (::webView.isInitialized) {
                webView.loadUrl(QuadTvConfig.SEERR_BASE_URL)
                webView.requestFocus()
            }
        }
    }

    private fun fetchSeerrSessionCookie(): String? {
        return try {
            val client = OkHttpClient.Builder()
                .followRedirects(false)
                .build()
            val body = """{"email":"${QuadTvConfig.SEERR_ADMIN_EMAIL}","password":"${QuadTvConfig.SEERR_ADMIN_PASSWORD}"}"""
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${QuadTvConfig.SEERR_BASE_URL}/api/v1/auth/local")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                response.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("connect.sid=") }
                    ?.substringBefore(";")
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onDestroyView() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroyView()
    }

    private fun handleBack() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            (activity as? QuadTvNavigator)?.goBack()
        }
    }
}
