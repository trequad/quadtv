package net.trequad.quadtv.seerr

import net.trequad.quadtv.core.ui.QuadTvTheme
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.core.AppServices
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.navigation.QuadTvNavigator

class SeerrFragment : Fragment() {
    private lateinit var webView: WebView
    private lateinit var statusView: TextView
    private val sessionRepository: SeerrSessionRepository by lazy { buildSeerrSessionRepository() }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val dp = context.resources.displayMetrics.density

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(QuadTvTheme.BACKGROUND)
                setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())

                addView(ImageView(context).apply {
                    setImageResource(R.drawable.quadtv_logo_horizontal)
                    contentDescription = "QuadTV by QuadMedia"
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = LinearLayout.LayoutParams(0, (64 * dp).toInt(), 1f)
                })

                addView(TextView(context).apply {
                    text = "Requests"
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })

                addView(TextView(context).apply {
                    text = "Back"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(QuadTvTheme.ACCENT)
                    gravity = Gravity.CENTER
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setPadding((18 * dp).toInt(), (10 * dp).toInt(), (18 * dp).toInt(), (10 * dp).toInt())
                    setBackgroundColor(QuadTvTheme.SURFACE)
                    setOnFocusChangeListener { view, hasFocus ->
                        view.setBackgroundColor(if (hasFocus) QuadTvTheme.FOCUS else QuadTvTheme.SURFACE)
                    }
                    setOnClickListener { handleBack() }
                })
            })

            statusView = TextView(context).apply {
                text = "Loading Requests — approved requests appear in QuadOnDemand…"
                textSize = 17f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            }
            addView(statusView)

            webView = WebView(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                setBackgroundColor(QuadTvTheme.BACKGROUND)
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
            // The portal issues the Requests session; no credentials live in the app.
            val session = withContext(Dispatchers.IO) { sessionRepository.createSession() }
            if (!isAdded || !::webView.isInitialized) return@launch
            if (session == null) {
                statusView.text =
                    "Requests are temporarily unavailable. Please try again later or contact QuadTV support."
                return@launch
            }
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setCookie(session.baseUrl, session.sessionCookie)
            cookieManager.flush()
            statusView.visibility = View.GONE
            webView.loadUrl(session.baseUrl)
            webView.requestFocus()
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

    private fun buildSeerrSessionRepository(): SeerrSessionRepository =
        AppServices.seerrSessionRepository(requireContext())
}
