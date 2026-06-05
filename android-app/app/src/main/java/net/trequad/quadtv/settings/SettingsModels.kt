package net.trequad.quadtv.settings

import android.content.SharedPreferences
import net.trequad.quadtv.parental.ProfileParentalState
import net.trequad.quadtv.player.PlayerEngine

enum class SettingsSection {
    ACCOUNT,
    REFRESH,
    PLAYBACK,
    PARENTAL,
    WATCH_HISTORY,
    ABOUT
}

enum class SettingsAction {
    REFRESH_CONTENT,
    CLEAR_WATCH_HISTORY,
    TOGGLE_PARENTAL_RATING_BLOCK,
    LOG_OUT,
    ABOUT
}

data class SettingsOption(
    val section: SettingsSection,
    val title: String,
    val description: String,
    val action: SettingsAction
)

class SettingsOptionsFactory {
    fun buildOptions(profileState: ProfileParentalState): List<SettingsOption> {
        val parentalCopy = if (profileState.parentalEnabled) {
            "On — hides R, NC-17, TV-MA, adult/XXX categories, and mature keywords where guide/library ratings are available."
        } else {
            "Off — mature ratings are visible for this profile."
        }
        val engineName = when (PlayerEngine.VLC) {
            PlayerEngine.VLC -> "Built-in QuadTV player powered by VLC"
        }
        return listOf(
            SettingsOption(
                SettingsSection.ACCOUNT,
                "Log out",
                "Sign out of this QuadTV account and return to the login screen.",
                SettingsAction.LOG_OUT
            ),
            SettingsOption(
                SettingsSection.REFRESH,
                "Refresh channels, guide & library",
                "Reload Live TV, EPG, VOD, and QuadOnDemand catalog data.",
                SettingsAction.REFRESH_CONTENT
            ),
            SettingsOption(
                SettingsSection.PLAYBACK,
                "Playback",
                "$engineName (Embedded VLC). Player engine selection is hidden until there is more than one supported in-app engine.",
                SettingsAction.ABOUT
            ),
            SettingsOption(
                SettingsSection.PARENTAL,
                "Block mature ratings: ${if (profileState.parentalEnabled) "On" else "Off"}",
                parentalCopy,
                SettingsAction.TOGGLE_PARENTAL_RATING_BLOCK
            ),
            SettingsOption(
                SettingsSection.WATCH_HISTORY,
                "Clear watch history",
                "Clear recently watched Live TV and movie rows for this profile.",
                SettingsAction.CLEAR_WATCH_HISTORY
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
