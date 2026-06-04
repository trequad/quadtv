package net.trequad.quadtv.vod

data class VodCategory(
    val id: String,
    val name: String
)

data class VodItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val releaseYear: Int? = null,
    val streamUrl: String? = null,
    val isSeries: Boolean = false,
    val isMature: Boolean = false
)

data class VodPage(
    val items: List<VodItem>,
    val totalCount: Int,
    val startIndex: Int,
    val limit: Int
) {
    val hasMore: Boolean
        get() = startIndex + items.size < totalCount
}

data class VodSeason(
    val seasonNumber: Int,
    val episodes: List<VodEpisode>
)

data class VodEpisode(
    val id: String,
    val seriesId: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val streamUrl: String? = null,
    val description: String? = null,
    val rating: String? = null,
    val isMature: Boolean = false
)
