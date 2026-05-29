package net.trequad.quadtv.settings

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.Presenter
import net.trequad.quadtv.R
import net.trequad.quadtv.core.cache.PlayerSettingsCache
import net.trequad.quadtv.core.config.QuadTvConfig
import net.trequad.quadtv.parental.ProfileParentalState

class SettingsFragment : BrowseSupportFragment() {
    // User-facing settings include Player selection for ExoPlayer or VLC, Buffering
    // with small / medium / large / custom sizes, Subtitle language, Audio track,
    // Parental controls, Clear watch history, and About/version.
    // They intentionally omit operator-only endpoints and credentials.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "QuadTV Settings • QuadMedia"
        headersState = HEADERS_ENABLED
        brandColor = resources.getColor(R.color.quadmedia_blue, null)

        val preferences = requireContext().getSharedPreferences(
            PlayerSettingsCache.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val options = SettingsOptionsFactory(PlayerSettingsCache(preferences)).buildOptions(
            ProfileParentalState(profileId = 0, parentalEnabled = false)
        ) + SettingsOption(
            SettingsSection.ABOUT,
            "About QuadTV",
            "${QuadTvConfig.APP_NAME} by ${QuadTvConfig.PARENT_BRAND} version ${appVersionName()}"
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

    private fun appVersionName(): String {
        return runCatching {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName ?: "0.1.0"
        }.getOrDefault("0.1.0")
    }
}

private fun SettingsSection.displayName(): String = when (this) {
    SettingsSection.PLAYER -> "Player"
    SettingsSection.BUFFERING -> "Buffering"
    SettingsSection.LANGUAGES -> "Subtitle language & Audio track"
    SettingsSection.PARENTAL -> "Parental controls"
    SettingsSection.WATCH_HISTORY -> "Clear watch history"
    SettingsSection.ABOUT -> "About QuadTV"
}

private class SettingsOptionPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.rgb(16, 34, 52))
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val option = item as SettingsOption
        (viewHolder.view as TextView).text = "${option.title}\n${option.description}"
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) = Unit
}
