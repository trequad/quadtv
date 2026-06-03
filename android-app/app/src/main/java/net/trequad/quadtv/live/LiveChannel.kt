package net.trequad.quadtv.live

data class LiveChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val channelNumber: Int? = null,
    val contentRating: String? = null,
    val isMature: Boolean = false
)
