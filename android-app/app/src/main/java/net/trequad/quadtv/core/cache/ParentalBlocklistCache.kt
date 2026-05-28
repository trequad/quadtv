package net.trequad.quadtv.core.cache

import android.content.SharedPreferences
import net.trequad.quadtv.parental.GlobalParentalBlocklist

class ParentalBlocklistCache(
    private val sharedPreferences: SharedPreferences
) {
    fun save(blocklist: GlobalParentalBlocklist) {
        sharedPreferences.edit()
            .putStringSet(KEY_CHANNEL_IDS, blocklist.channelIds)
            .putStringSet(KEY_CATEGORY_NAMES, blocklist.categoryNames)
            .putStringSet(KEY_CONTENT_RATINGS, blocklist.contentRatings)
            .putStringSet(KEY_KEYWORDS, blocklist.keywords)
            .apply()
    }

    fun load(): GlobalParentalBlocklist? {
        if (!sharedPreferences.contains(KEY_CONTENT_RATINGS)) return null
        val defaults = GlobalParentalBlocklist.defaults()
        return GlobalParentalBlocklist(
            channelIds = sharedPreferences.getStringSet(KEY_CHANNEL_IDS, defaults.channelIds).orEmpty(),
            categoryNames = sharedPreferences.getStringSet(KEY_CATEGORY_NAMES, defaults.categoryNames).orEmpty(),
            contentRatings = sharedPreferences.getStringSet(KEY_CONTENT_RATINGS, defaults.contentRatings).orEmpty(),
            keywords = sharedPreferences.getStringSet(KEY_KEYWORDS, defaults.keywords).orEmpty()
        )
    }

    companion object {
        const val PREFERENCES_NAME = "quadtv_parental_blocklist"
        private const val KEY_CHANNEL_IDS = "channel_ids"
        private const val KEY_CATEGORY_NAMES = "category_names"
        private const val KEY_CONTENT_RATINGS = "content_ratings"
        private const val KEY_KEYWORDS = "keywords"
    }
}
