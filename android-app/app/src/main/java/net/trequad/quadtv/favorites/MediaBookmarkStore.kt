package net.trequad.quadtv.favorites

import android.content.Context
import net.trequad.quadtv.core.cache.ProfileSelectionCache
import org.json.JSONArray
import org.json.JSONObject

enum class BookmarkedMediaSource { VOD, JELLYFIN }

data class BookmarkedMediaItem(
    val id: String,
    val title: String,
    val source: BookmarkedMediaSource,
    val posterUrl: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val releaseYear: Int? = null,
    val streamUrl: String? = null,
    val isSeries: Boolean = false,
    val isMature: Boolean = false
)

class MediaBookmarkStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val profileScope = ProfileSelectionCache(
        context.getSharedPreferences(ProfileSelectionCache.PREFERENCES_NAME, Context.MODE_PRIVATE)
    ).scopeKey()

    fun recentItems(): List<BookmarkedMediaItem> = readItems(scopedKey(KEY_RECENT))

    fun favoriteItems(): List<BookmarkedMediaItem> = readItems(scopedKey(KEY_FAVORITES))

    fun recordRecent(item: BookmarkedMediaItem) {
        val key = scopedKey(KEY_RECENT)
        writeItems(key, listOf(item) + readItems(key)
            .filterNot { it.id == item.id && it.source == item.source }
            .take(MAX_RECENT - 1))
    }

    fun toggleFavorite(item: BookmarkedMediaItem): Boolean {
        val key = scopedKey(KEY_FAVORITES)
        val favorites = readItems(key)
        val existing = favorites.any { it.id == item.id && it.source == item.source }
        writeItems(key, if (existing) {
            favorites.filterNot { it.id == item.id && it.source == item.source }
        } else {
            listOf(item) + favorites
        })
        return !existing
    }

    fun isFavorite(id: String, source: BookmarkedMediaSource): Boolean =
        readItems(scopedKey(KEY_FAVORITES)).any { it.id == id && it.source == source }

    private fun scopedKey(key: String): String = "${profileScope}_$key"

    private fun readItems(key: String): List<BookmarkedMediaItem> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val source = runCatching {
                    BookmarkedMediaSource.valueOf(obj.optString("source"))
                }.getOrNull() ?: return@mapNotNull null
                BookmarkedMediaItem(
                    id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    title = obj.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                    source = source,
                    posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() },
                    description = obj.optString("description").takeIf { it.isNotBlank() },
                    rating = obj.optString("rating").takeIf { it.isNotBlank() },
                    releaseYear = obj.optInt("releaseYear", -1).takeIf { it != -1 },
                    streamUrl = obj.optString("streamUrl").takeIf { it.isNotBlank() },
                    isSeries = obj.optBoolean("isSeries", false),
                    isMature = obj.optBoolean("isMature", false)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeItems(key: String, items: List<BookmarkedMediaItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("source", item.source.name)
                put("posterUrl", item.posterUrl.orEmpty())
                put("description", item.description.orEmpty())
                put("rating", item.rating.orEmpty())
                item.releaseYear?.let { put("releaseYear", it) }
                put("streamUrl", item.streamUrl.orEmpty())
                put("isSeries", item.isSeries)
                put("isMature", item.isMature)
            })
        }
        preferences.edit().putString(key, array.toString()).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "media_bookmarks"
        private const val KEY_RECENT = "recent_media_items"
        private const val KEY_FAVORITES = "favorite_media_items"
        private const val MAX_RECENT = 24
    }
}
