package net.trequad.quadtv.jellyfin

import com.squareup.moshi.Json

data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String? = null
)

data class JellyfinItem(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val overview: String? = null,
    val contentRating: String? = null,
    val productionYear: Int? = null,
    val isFolder: Boolean = false,
    val isMature: Boolean = false
)

data class JellyfinPage(
    val items: List<JellyfinItem>,
    val totalCount: Int,
    val startIndex: Int,
    val limit: Int
) {
    val hasMore: Boolean
        get() = startIndex + items.size < totalCount
}

data class JellyfinStream(
    val itemId: String,
    val title: String,
    val hlsUrl: String
)

data class JellyfinSeason(
    val id: String,
    val title: String,
    val seasonNumber: Int
)

data class JellyfinEpisode(
    val id: String,
    val title: String,
    val seriesId: String,
    val seasonId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val overview: String? = null,
    val contentRating: String? = null
)

data class JellyfinItemsResponse(
    @Json(name = "Items") val items: List<JellyfinApiItem> = emptyList(),
    @Json(name = "TotalRecordCount") val totalRecordCount: Int? = null,
    @Json(name = "StartIndex") val startIndex: Int? = null
)

data class JellyfinApiItem(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String,
    @Json(name = "CollectionType") val collectionType: String? = null,
    @Json(name = "Overview") val overview: String? = null,
    @Json(name = "OfficialRating") val officialRating: String? = null,
    @Json(name = "ProductionYear") val productionYear: Int? = null,
    @Json(name = "IsFolder") val isFolder: Boolean = false,
    @Json(name = "Type") val type: String? = null,
    @Json(name = "IndexNumber") val indexNumber: Int? = null,
    @Json(name = "ParentIndexNumber") val parentIndexNumber: Int? = null,
    @Json(name = "SeriesId") val seriesId: String? = null,
    @Json(name = "SeasonId") val seasonId: String? = null
) {
    fun toLibrary() = JellyfinLibrary(
        id = id,
        name = name,
        collectionType = collectionType
    )

    fun toItem(baseUrl: String) = JellyfinItem(
        id = id,
        title = name,
        posterUrl = "$baseUrl/Items/$id/Images/Primary",
        overview = overview,
        contentRating = officialRating,
        productionYear = productionYear,
        isFolder = isFolder || type == "Series",
        isMature = officialRating?.contains("R", ignoreCase = true) == true ||
            officialRating?.contains("TV-MA", ignoreCase = true) == true
    )

    fun toSeason() = JellyfinSeason(
        id = id,
        title = name,
        seasonNumber = indexNumber ?: 0
    )

    fun toEpisode(seriesIdFallback: String, seasonIdFallback: String) = JellyfinEpisode(
        id = id,
        title = name,
        seriesId = seriesId ?: seriesIdFallback,
        seasonId = seasonId ?: seasonIdFallback,
        seasonNumber = parentIndexNumber ?: 0,
        episodeNumber = indexNumber ?: 0,
        overview = overview,
        contentRating = officialRating
    )
}
