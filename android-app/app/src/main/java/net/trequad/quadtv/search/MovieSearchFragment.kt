package net.trequad.quadtv.search

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.adminapi.AdminConfigRepository
import net.trequad.quadtv.core.cache.LaunchConfigCache
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.jellyfin.JellyfinDetailsFragment
import net.trequad.quadtv.jellyfin.JellyfinItem
import net.trequad.quadtv.jellyfin.JellyfinRepository
import net.trequad.quadtv.vod.VodDetailsFragment
import net.trequad.quadtv.provider.ProviderFeedRepository
import net.trequad.quadtv.vod.VodItem
import net.trequad.quadtv.vod.VodRepository

class MovieSearchFragment : Fragment() {
    private val vodRepository: VodRepository by lazy { buildVodRepository() }
    private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }
    private lateinit var resultsContainer: LinearLayout
    private lateinit var searchInput: EditText

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 48)
            setBackgroundResource(net.trequad.quadtv.R.drawable.quadtv_neon_waves_background)

            addView(TextView(context).apply {
                text = "Movie Search"
                textSize = 36f
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Search once and QuadTV will show matching movies from VOD and QuadOnDemand when either source has them."
                textSize = 19f
                setTextColor(Color.LTGRAY)
                setPadding(0, 8, 0, 24)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                searchInput = EditText(context).apply {
                    hint = "Search movie title"
                    inputType = InputType.TYPE_CLASS_TEXT
                    setSingleLine(true)
                    textSize = 22f
                    setTextColor(Color.WHITE)
                    setHintTextColor(Color.LTGRAY)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(searchInput)
                addView(Button(context).apply {
                    text = "Search"
                    textSize = 22f
                    isFocusable = true
                    setOnClickListener { runMovieSearch(searchInput.text.toString()) }
                })
            })
            resultsContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 32, 0, 0)
            }
            addView(ScrollView(context).apply {
                addView(resultsContainer)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            })
            post { searchInput.requestFocus() }
        }
    }

    private fun runMovieSearch(query: String) {
        if (query.isBlank()) return
        resultsContainer.removeAllViews()
        resultsContainer.addView(messageView("Searching VOD and QuadOnDemand…"))
        lifecycleScope.launch {
            val result = try {
                val vod = async(Dispatchers.IO) { vodRepository.searchMovies(query) }
                val jellyfin = async(Dispatchers.IO) { jellyfinRepository.searchMovies(query) }
                vod.await() to jellyfin.await()
            } catch (_: Exception) {
                resultsContainer.removeAllViews()
                resultsContainer.addView(messageView("Search failed. Check the configured VOD/QuadOnDemand endpoints and try again."))
                return@launch
            }
            showResults(result.first, result.second)
        }
    }

    private fun showResults(vodResults: List<VodItem>, jellyfinResults: List<JellyfinItem>) {
        resultsContainer.removeAllViews()
        if (vodResults.isEmpty() && jellyfinResults.isEmpty()) {
            resultsContainer.addView(messageView("No movies found in VOD or QuadOnDemand."))
            return
        }
        if (vodResults.isNotEmpty()) {
            resultsContainer.addView(sectionHeader("Available on VOD"))
            vodResults.forEach { item ->
                resultsContainer.addView(resultButton(item.title, item.releaseYear?.toString()) {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, VodDetailsFragment.newInstance(item))
                        .addToBackStack("VOD_DETAILS")
                        .commit()
                })
            }
        }
        if (jellyfinResults.isNotEmpty()) {
            resultsContainer.addView(sectionHeader("Available on QuadOnDemand"))
            jellyfinResults.forEach { item ->
                resultsContainer.addView(resultButton(item.title, item.productionYear?.toString()) {
                    parentFragmentManager.beginTransaction()
                        .replace(android.R.id.content, JellyfinDetailsFragment.newInstance(item))
                        .addToBackStack("JELLYFIN_DETAILS")
                        .commit()
                })
            }
        }
    }

    private fun sectionHeader(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 26f
        setTextColor(Color.WHITE)
        setPadding(0, 28, 0, 10)
    }

    private fun messageView(label: String): TextView = TextView(requireContext()).apply {
        text = label
        textSize = 22f
        setTextColor(Color.LTGRAY)
        setPadding(0, 20, 0, 20)
    }

    private fun resultButton(title: String, year: String?, onClick: () -> Unit): Button {
        return Button(requireContext()).apply {
            text = listOfNotNull(title, year).joinToString("  •  ")
            textSize = 22f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isFocusable = true
            setPadding(28, 18, 28, 18)
            setOnClickListener { onClick() }
        }
    }

    private fun buildVodRepository(): VodRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val preferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val configRepository = AdminConfigRepository(apiService, LaunchConfigCache(preferences))
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return VodRepository(configRepository, okHttpClient, moshi, providerFeedRepository)
    }

    private fun buildJellyfinRepository(): JellyfinRepository {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val moshi = NetworkModule.provideMoshi()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, moshi)
        val apiService = retrofit.create(AdminApiService::class.java)
        val preferences = context.getSharedPreferences(LaunchConfigCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        return JellyfinRepository(AdminConfigRepository(apiService, LaunchConfigCache(preferences)), okHttpClient, moshi)
    }
}
