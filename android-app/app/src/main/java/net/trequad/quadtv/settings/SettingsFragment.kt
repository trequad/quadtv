package net.trequad.quadtv.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.trequad.quadtv.R
import net.trequad.quadtv.adminapi.AdminApiService
import net.trequad.quadtv.core.cache.CustomerSessionCache
import net.trequad.quadtv.core.cache.ProfileSelectionCache
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.core.network.NetworkModule
import net.trequad.quadtv.epg.EpgRepository
import net.trequad.quadtv.live.LiveTvRepository
import net.trequad.quadtv.navigation.QuadTvNavigator
import net.trequad.quadtv.navigation.QuadTvRoute
import net.trequad.quadtv.parental.ParentalSettingsCache
import net.trequad.quadtv.parental.ProfileParentalState
import net.trequad.quadtv.provider.ProviderFeedRefreshCoordinator
import net.trequad.quadtv.provider.ProviderFeedRepository

class SettingsFragment : BrowseSupportFragment() {
    // User-facing settings include real Account logout, Refresh, local parental rating block,
    // Clear watch history, and About/version/playback info. They intentionally omit operator-only endpoints and credentials.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Settings"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)
        buildAdapter()
        setOnItemViewClickedListener { _, item, _, _ ->
            (item as? SettingsOption)?.let { handleSettingsOption(it) }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.drawable.quadtv_neon_waves_background)
    }

    private fun buildAdapter() {
        val options = SettingsOptionsFactory().buildOptions(currentProfileParentalState()) + SettingsOption(
            SettingsSection.ABOUT,
            "About QuadTV",
            "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND} version ${appVersionName()} • Built-in QuadTV player powered by VLC.",
            SettingsAction.ABOUT
        )

        adapter = ArrayObjectAdapter(ListRowPresenter()).apply {
            SettingsSection.values().forEachIndexed { index, section ->
                val sectionOptions = options.filter { it.section == section }
                if (sectionOptions.isNotEmpty()) {
                    add(
                        ListRow(
                            HeaderItem(index.toLong(), section.displayName()),
                            ArrayObjectAdapter(SettingsOptionPresenter()).apply {
                                sectionOptions.forEach { add(it) }
                            }
                        )
                    )
                }
            }
        }
    }

    private fun handleSettingsOption(option: SettingsOption) {
        when (option.action) {
            SettingsAction.REFRESH_CONTENT -> showRefreshDialog()
            SettingsAction.CLEAR_WATCH_HISTORY -> clearCurrentProfileHistory()
            SettingsAction.TOGGLE_PARENTAL_RATING_BLOCK -> toggleParentalControls()
            SettingsAction.LOG_OUT -> logoutToLogin()
            SettingsAction.ABOUT -> showAboutDialog()
        }
    }

    private fun showRefreshDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Refresh QuadTV")
            .setMessage("Refresh channels, guide, and catalog data now?")
            .setPositiveButton("Refresh") { _, _ -> refreshContent() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshContent() {
        Toast.makeText(requireContext(), "Refreshing channels, EPG, and VOD content…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { buildProviderFeedRefreshCoordinator().refreshPlaylistAndGuide() }
            }
            val message = result.fold(
                onSuccess = { "Refresh complete: ${it.playlistChannelCount} channels, ${it.guideProgrammeCount} guide entries." },
                onFailure = { "Refresh failed. Check Wi-Fi and try again." }
            )
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun clearCurrentProfileHistory() {
        val profileId = currentProfileId()
        val scope = "profile_$profileId"
        ProfilePreferencesClearAction(
            requireContext().getSharedPreferences(ProfilePreferencesClearAction.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).clearWatchHistory(profileId)
        requireContext().getSharedPreferences("media_bookmarks", Context.MODE_PRIVATE).edit()
            .remove("${scope}_recent_media_items")
            .apply()
        requireContext().getSharedPreferences("live_channel_bookmarks", Context.MODE_PRIVATE).edit()
            .remove("${scope}_recent_live_channels")
            .apply()
        Toast.makeText(requireContext(), "Watch history cleared for this profile.", Toast.LENGTH_LONG).show()
    }

    private fun toggleParentalControls() {
        val profileId = currentProfileId()
        val enabled = ParentalSettingsCache(
            requireContext().getSharedPreferences(ParentalSettingsCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).toggleForProfile(profileId)
        Toast.makeText(
            requireContext(),
            if (enabled) "Mature ratings blocked for this profile." else "Mature rating block turned off.",
            Toast.LENGTH_LONG
        ).show()
        buildAdapter()
    }

    private fun logoutToLogin() {
        AlertDialog.Builder(requireContext())
            .setTitle("Log out")
            .setMessage("Sign out of QuadTV on this device?")
            .setPositiveButton("Log out") { _, _ ->
                requireContext().getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
                    .edit().clear().apply()
                (activity as? QuadTvNavigator)?.navigateTo(QuadTvRoute.LOGIN)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("About QuadTV")
            .setMessage("${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND}\nVersion ${appVersionName()}\n\nPlayback: Built-in QuadTV player powered by VLC.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun currentProfileId(): Int {
        return ProfileSelectionCache(
            requireContext().getSharedPreferences(ProfileSelectionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).loadProfileId() ?: 0
    }

    private fun currentProfileParentalState(): ProfileParentalState {
        val profileId = currentProfileId()
        val enabled = ParentalSettingsCache(
            requireContext().getSharedPreferences(ParentalSettingsCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        ).isEnabledForProfile(profileId)
        return ProfileParentalState(profileId = profileId, parentalEnabled = enabled)
    }

    private fun buildProviderFeedRefreshCoordinator(): ProviderFeedRefreshCoordinator {
        val context = requireContext().applicationContext
        val okHttpClient = NetworkModule.provideOkHttpClient()
        val retrofit = NetworkModule.provideRetrofit(okHttpClient, NetworkModule.provideMoshi())
        val apiService = retrofit.create(AdminApiService::class.java)
        val sessionPreferences = context.getSharedPreferences(CustomerSessionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val providerFeedRepository = ProviderFeedRepository(apiService, CustomerSessionCache(sessionPreferences))
        return ProviderFeedRefreshCoordinator(
            liveTvRepository = LiveTvRepository(providerFeedRepository, okHttpClient),
            epgRepository = EpgRepository(providerFeedRepository, okHttpClient)
        )
    }

    private fun appVersionName(): String {
        return runCatching {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "0.1.0"
        }.getOrDefault("0.1.0")
    }
}

private fun SettingsSection.displayName(): String = when (this) {
    SettingsSection.ACCOUNT -> "Account"
    SettingsSection.REFRESH -> "Refresh"
    SettingsSection.PLAYBACK -> "Playback"
    SettingsSection.PARENTAL -> "Parental controls"
    SettingsSection.WATCH_HISTORY -> "Watch history"
    SettingsSection.ABOUT -> "About QuadTV"
}

private class SettingsOptionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(36, 26, 36, 26)
            setBackgroundColor(Color.rgb(16, 34, 52))
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundColor(if (hasFocus) Color.rgb(44, 95, 124) else Color.rgb(16, 34, 52))
            }
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val option = item as SettingsOption
        (viewHolder.view as TextView).apply {
            text = option.title
            setTypeface(null, Typeface.BOLD)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
