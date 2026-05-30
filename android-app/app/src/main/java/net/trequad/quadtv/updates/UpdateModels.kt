package net.trequad.quadtv.updates

import com.squareup.moshi.Json

/** Private sideload release metadata published by the QuadTV admin portal. */
data class AppRelease(
    val versionName: String,
    val versionCode: Int,
    val changelog: String,
    val apkUrl: String,
    val minimumSupportedVersionCode: Int,
    val forced: Boolean,
    val releaseDate: String?
)

data class UpdateStatus(
    val updateAvailable: Boolean,
    val forcedUpdateRequired: Boolean,
    val release: AppRelease?
)

data class AppReleaseDto(
    @Json(name = "version_name") val versionName: String,
    @Json(name = "version_code") val versionCode: Int,
    @Json(name = "changelog") val changelog: String,
    @Json(name = "apk_url") val apkUrl: String,
    @Json(name = "minimum_supported_version_code") val minimumSupportedVersionCode: Int,
    @Json(name = "forced") val forced: Boolean,
    @Json(name = "release_date") val releaseDate: String?
) {
    fun toDomain() = AppRelease(
        versionName = versionName,
        versionCode = versionCode,
        changelog = changelog,
        apkUrl = apkUrl,
        minimumSupportedVersionCode = minimumSupportedVersionCode,
        forced = forced,
        releaseDate = releaseDate
    )
}

data class UpdateStatusDto(
    @Json(name = "update_available") val updateAvailable: Boolean,
    @Json(name = "forced_update_required") val forcedUpdateRequired: Boolean,
    @Json(name = "release") val release: AppReleaseDto?
) {
    fun toDomain() = UpdateStatus(
        updateAvailable = updateAvailable,
        forcedUpdateRequired = forcedUpdateRequired,
        release = release?.toDomain()
    )
}
