package net.trequad.quadtv.onboarding

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import net.trequad.quadtv.R
import net.trequad.quadtv.core.cache.OnboardingCache
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute

private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String,
    val tip: String
)

private val PAGES = listOf(
    OnboardingPage(
        emoji = "📺",
        title = "Watch Live TV",
        body = "Hundreds of live channels, organized by category. Press OK on any channel to start watching immediately.",
        tip = "Tip: Long-press a channel to add it to Favorites."
    ),
    OnboardingPage(
        emoji = "🎬",
        title = "Movies & Shows On Demand",
        body = "Browse thousands of titles from VOD and QuadOnDemand. Pick a movie, press Play — it's that simple.",
        tip = "Tip: Tap \"Add to Favorites\" on any details page to save it."
    ),
    OnboardingPage(
        emoji = "📝",
        title = "Request with Seerr",
        body = "Use Seerr to request movies and shows that are not in QuadOnDemand yet. Once approved, approved requests appear in QuadOnDemand.",
        tip = "Tip: Seerr is for requests; QuadOnDemand is where approved movies and shows are watched."
    ),
    OnboardingPage(
        emoji = "⭐",
        title = "Your Favorites & History",
        body = "Everything you watch and favorite is saved to your profile. Find it all on the Home screen under Favorites and Recently Viewed.",
        tip = "Tip: Use the Guide button for the full TV schedule."
    )
)

class OnboardingFragment : Fragment() {
    private var currentPage = 0
    private lateinit var emojiView: TextView
    private lateinit var titleView: TextView
    private lateinit var bodyView: TextView
    private lateinit var tipView: TextView
    private lateinit var dotsView: TextView
    private lateinit var nextButton: Button
    private lateinit var skipButton: Button

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)
            setPadding(96, 64, 96, 64)

            emojiView = TextView(context).apply {
                textSize = 72f
                gravity = Gravity.CENTER
            }
            addView(emojiView)

            titleView = TextView(context).apply {
                textSize = 48f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 0)
            }
            addView(titleView)

            bodyView = TextView(context).apply {
                textSize = 26f
                setTextColor(Color.rgb(200, 220, 240))
                gravity = Gravity.CENTER
                setLineSpacing(6f, 1f)
                setPadding(0, 32, 0, 0)
            }
            addView(bodyView)

            tipView = TextView(context).apply {
                textSize = 22f
                setTextColor(Color.rgb(100, 160, 220))
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 0)
            }
            addView(tipView)

            dotsView = TextView(context).apply {
                textSize = 28f
                setTextColor(Color.rgb(66, 165, 245))
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 0)
            }
            addView(dotsView)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 0)

                skipButton = Button(context).apply {
                    text = "Skip"
                    textSize = 22f
                    isFocusable = true
                    setPadding(40, 20, 40, 20)
                    setOnClickListener { complete() }
                }
                addView(skipButton)

                addView(TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(48, 1)
                })

                nextButton = Button(context).apply {
                    textSize = 24f
                    isFocusable = true
                    setPadding(60, 20, 60, 20)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(resources.getColor(R.color.quadmedia_blue, null))
                    setOnClickListener { advancePage() }
                }
                addView(nextButton)
            })
        }.also { showPage(0) }
    }

    private fun showPage(page: Int) {
        currentPage = page
        val p = PAGES[page]
        emojiView.text = p.emoji
        titleView.text = p.title
        bodyView.text = p.body
        tipView.text = p.tip
        dotsView.text = PAGES.indices.joinToString("  ") { if (it == page) "●" else "○" }
        val isLast = page == PAGES.lastIndex
        nextButton.text = if (isLast) "Get Started" else "Next  →"
        skipButton.visibility = if (isLast) View.GONE else View.VISIBLE
    }

    private fun advancePage() {
        if (currentPage < PAGES.lastIndex) {
            showPage(currentPage + 1)
        } else {
            complete()
        }
    }

    private fun complete() {
        OnboardingCache(requireContext()).markCompleted()
        (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.LOGIN)
    }
}
