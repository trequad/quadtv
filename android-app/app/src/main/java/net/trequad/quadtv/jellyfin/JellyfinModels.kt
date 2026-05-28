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

data class JellyfinStream(
    val itemId: String,
    val title: String,
    val hlsUrl: String
)

data class JellyfinItemsResponse(
    @Json(name = "Items") val items: List<JellyfinApiItem> = emptyList()
)

data class JellyfinApiItem(
    @Json(name = "Id") val id: String,
    @Json(name = "Name") val name: String,
    @Json(name = "CollectionType") val collectionType: String? = null,
    @Json(name = "Overview") val overview: String? = null,
    @Json(name = "OfficialRating") val officialRating: String? = null,
    @Json(name = "ProductionYear") val productionYear: Int? = null,
    @Json(name = "IsFolder") val isFolder: Boolean = false,
    @Json(name = "Type") val type: String? = null
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
        isFolder = isFolder,
        isMature = officialRating?.contains("R", ignoreCase = true) == true ||
            officialRating?.contains("TV-MA", ignoreCase = true) == true
    )
}
