package net.trequad.quadtv.settings

import android.content.SharedPreferences
import net.trequad.quadtv.core.cache.PlayerSettings
import net.trequad.quadtv.core.cache.PlayerSettingsCache
import net.trequad.quadtv.parental.ProfileParentalState
import net.trequad.quadtv.player.BufferStrategy
import net.trequad.quadtv.player.PlayerEngine

enum class SettingsSection {
    PLAYER,
    BUFFERING,
    LANGUAGES,
    PARENTAL,
    WATCH_HISTORY,
    ABOUT
}

data class SettingsOption(
    val section: SettingsSection,
    val title: String,
    val description: String
)

class SettingsOptionsFactory(
    private val playerSettingsCache: PlayerSettingsCache
) {
    fun buildOptions(profileState: ProfileParentalState): List<SettingsOption> {
        val settings: PlayerSettings = playerSettingsCache.load()
        val engineName = when (settings.defaultEngine) {
            PlayerEngine.EXOPLAYER -> "ExoPlayer"
            PlayerEngine.VLC -> "VLC"
        }
        val strategyName = when (settings.bufferConfig.strategy) {
            BufferStrategy.ADAPTIVE -> "adaptive"
            BufferStrategy.AGGRESSIVE_PREBUFFER -> "aggressive pre-buffer"
        }
        return listOf(
            SettingsOption(
                SettingsSection.PLAYER,
                "Player",
                "Choose ExoPlayer or VLC as the bundled default playback engine. Current: $engineName."
            ),
            SettingsOption(
                SettingsSection.BUFFERING,
                "Buffering",
                "Adjust small / medium / large / custom buffer sizes and $strategyName strategy. Current: ${settings.bufferConfig.sizeSeconds}s."
            ),
            SettingsOption(
                SettingsSection.LANGUAGES,
                "Subtitle language / Audio track",
                "Set preferred subtitle language and audio track for this profile."
            ),
            SettingsOption(
                SettingsSection.PARENTAL,
                "Parental controls",
                "Toggle PIN-protected mature-content filtering for profile ${profileState.profileId}. Current: ${if (profileState.parentalEnabled) "enabled" else "disabled"}."
            ),
            SettingsOption(
                SettingsSection.WATCH_HISTORY,
                "Clear watch history",
                "Clear last watched channel and recently watched VOD for this profile."
            )
        )
    }
}

class ProfilePreferencesClearAction(
    private val sharedPreferences: SharedPreferences
) {
    fun clearWatchHistory(profileId: Int) {
        sharedPreferences.edit()
            .remove("profile_${profileId}_last_watched_channel")
            .remove("profile_${profileId}_recently_watched_vod")
            .remove("last_watched_channel_$profileId")
            .remove("recently_watched_vod_$profileId")
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_profile_preferences"
    }
}
