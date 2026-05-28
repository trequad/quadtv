package net.trequad.quadtv.epg

data class EpgProgramme(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val category: String? = null,
    val rating: String? = null,
    val isMature: Boolean = false
) {
    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)
}
