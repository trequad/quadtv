package net.trequad.quadtv.parental

import android.content.SharedPreferences
import com.squareup.moshi.Json
import net.trequad.quadtv.epg.EpgProgramme
import net.trequad.quadtv.jellyfin.JellyfinItem
import net.trequad.quadtv.live.LiveChannel
import net.trequad.quadtv.vod.VodItem

data class GlobalParentalBlocklist(
    val channelIds: Set<String> = emptySet(),
    val categoryNames: Set<String> = emptySet(),
    val contentRatings: Set<String> = setOf("R", "NC-17", "TV-MA"),
    val keywords: Set<String> = setOf("adult", "xxx", "porn")
) {
    companion object {
        fun defaults() = GlobalParentalBlocklist()
    }
}

data class ParentalBlocklistDto(
    @Json(name = "channel_ids") val channelIds: List<String> = emptyList(),
    @Json(name = "category_names") val categoryNames: List<String> = emptyList(),
    @Json(name = "content_ratings") val contentRatings: List<String> = listOf("R", "NC-17", "TV-MA"),
    val keywords: List<String> = listOf("adult", "xxx", "porn")
) {
    fun toDomain() = GlobalParentalBlocklist(
        channelIds = channelIds.toSet(),
        categoryNames = categoryNames.toSet(),
        contentRatings = contentRatings.toSet(),
        keywords = keywords.toSet()
    )

    companion object {
        fun fromDomain(blocklist: GlobalParentalBlocklist) = ParentalBlocklistDto(
            channelIds = blocklist.channelIds.toList(),
            categoryNames = blocklist.categoryNames.toList(),
            contentRatings = blocklist.contentRatings.toList(),
            keywords = blocklist.keywords.toList()
        )
    }
}

data class ProfileParentalState(
    val profileId: Int,
    val parentalEnabled: Boolean
)

class ParentalSettingsCache(
    private val sharedPreferences: SharedPreferences
) {
    fun isEnabledForProfile(profileId: Int): Boolean {
        return sharedPreferences.getBoolean(key(profileId), false)
    }

    fun setEnabledForProfile(profileId: Int, enabled: Boolean) {
        sharedPreferences.edit().putBoolean(key(profileId), enabled).apply()
    }

    fun toggleForProfile(profileId: Int): Boolean {
        val enabled = !isEnabledForProfile(profileId)
        setEnabledForProfile(profileId, enabled)
        return enabled
    }

    private fun key(profileId: Int) = "profile_${profileId}_parental_rating_block"

    companion object {
        const val PREFERENCES_NAME = "quadtv_parental_settings"
    }
}

class ParentalFilter(
    private val blocklist: GlobalParentalBlocklist
) {
    fun filterLiveChannels(
        profileState: ProfileParentalState,
        channels: List<LiveChannel>
    ): List<LiveChannel> {
        if (!profileState.parentalEnabled) return channels
        return channels.filterNot { channel ->
            channel.isMature ||
                blocklist.channelIds.contains(channel.id) ||
                matches(blocklist.categoryNames, channel.groupTitle) ||
                matches(blocklist.contentRatings, channel.contentRating) ||
                matchesAnyKeyword(channel.name)
        }
    }

    fun filterEpgProgrammes(
        profileState: ProfileParentalState,
        programmes: List<EpgProgramme>
    ): List<EpgProgramme> {
        if (!profileState.parentalEnabled) return programmes
        return programmes.filterNot { programme ->
            programme.isMature ||
                matches(blocklist.contentRatings, programme.rating) ||
                matchesAnyKeyword(programme.title) ||
                matchesAnyKeyword(programme.description)
        }
    }

    fun filterVodItems(
        profileState: ProfileParentalState,
        items: List<VodItem>
    ): List<VodItem> {
        if (!profileState.parentalEnabled) return items
        return items.filterNot { item ->
            item.isMature ||
                matches(blocklist.contentRatings, item.rating) ||
                matchesAnyKeyword(item.title) ||
                matchesAnyKeyword(item.description)
        }
    }

    fun filterJellyfinItems(
        profileState: ProfileParentalState,
        items: List<JellyfinItem>
    ): List<JellyfinItem> {
        if (!profileState.parentalEnabled) return items
        return items.filterNot { item ->
            item.isMature ||
                matches(blocklist.contentRatings, item.contentRating) ||
                matchesAnyKeyword(item.title) ||
                matchesAnyKeyword(item.overview)
        }
    }

    private fun matches(values: Set<String>, candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return values.any { value -> value.equals(candidate, ignoreCase = true) }
    }

    private fun matchesAnyKeyword(candidate: String?): Boolean {
        if (candidate.isNullOrBlank()) return false
        return blocklist.keywords.any { keyword -> candidate.contains(keyword, ignoreCase = true) }
    }
}
