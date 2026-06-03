package net.trequad.quadtv.live

import android.content.Context
import net.trequad.quadtv.core.cache.ProfileSelectionCache
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkedLiveChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val groupTitle: String? = null,
    val contentTitle: String? = null
)

class LiveChannelBookmarkStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val profileScope = ProfileSelectionCache(
        context.getSharedPreferences(ProfileSelectionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
    ).scopeKey()

    fun recentChannels(): List<BookmarkedLiveChannel> = readChannels(scopedKey(KEY_RECENT))

    fun favoriteChannels(): List<BookmarkedLiveChannel> = readChannels(scopedKey(KEY_FAVORITES))

    fun recordRecent(channel: LiveChannel, contentTitle: String? = null) {
        recordRecent(channel.toBookmark(contentTitle))
    }

    fun recordRecent(channel: BookmarkedLiveChannel) {
        val key = scopedKey(KEY_RECENT)
        writeChannels(key, listOf(channel) + readChannels(key).filterNot { it.id == channel.id }.take(MAX_RECENT - 1))
    }

    fun toggleFavorite(channel: LiveChannel, contentTitle: String? = null): Boolean {
        return toggleFavorite(channel.toBookmark(contentTitle))
    }

    fun toggleFavorite(channel: BookmarkedLiveChannel): Boolean {
        val key = scopedKey(KEY_FAVORITES)
        val favorites = readChannels(key)
        val existing = favorites.any { it.id == channel.id }
        val updated = if (existing) {
            favorites.filterNot { it.id == channel.id }
        } else {
            listOf(channel) + favorites
        }
        writeChannels(key, updated)
        return !existing
    }

    fun isFavorite(channelId: String): Boolean = readChannels(scopedKey(KEY_FAVORITES)).any { it.id == channelId }

    private fun scopedKey(key: String): String = "${profileScope}_$key"

    private fun readChannels(key: String): List<BookmarkedLiveChannel> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                BookmarkedLiveChannel(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    streamUrl = item.optString("streamUrl"),
                    groupTitle = item.optString("groupTitle").takeIf { it.isNotBlank() },
                    contentTitle = item.optString("contentTitle").takeIf { it.isNotBlank() }
                )
            }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.streamUrl.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun writeChannels(key: String, channels: List<BookmarkedLiveChannel>) {
        val array = JSONArray()
        channels.forEach { channel ->
            array.put(JSONObject().apply {
                put("id", channel.id)
                put("name", channel.name)
                put("streamUrl", channel.streamUrl)
                put("groupTitle", channel.groupTitle.orEmpty())
                put("contentTitle", channel.contentTitle.orEmpty())
            })
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "live_channel_bookmarks"
        private const val KEY_RECENT = "recent_live_channels"
        private const val KEY_FAVORITES = "favorite_live_channels"
        private const val MAX_RECENT = 12
    }
}

fun LiveChannel.toBookmark(contentTitle: String? = null): BookmarkedLiveChannel {
    return BookmarkedLiveChannel(
        id = id,
        name = name,
        streamUrl = streamUrl,
        groupTitle = groupTitle,
        contentTitle = contentTitle
    )
}
