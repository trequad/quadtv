package net.trequad.quadtv.live

class M3uParser {
    fun parse(playlist: String): List<LiveChannel> {
        val lines = playlist.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        val channels = mutableListOf<LiveChannel>()
        var index = 0
        while (index < lines.size) {
            val extinf = lines[index]
            if (extinf.startsWith("#EXTINF")) {
                val streamUrl = lines.drop(index + 1).firstOrNull { !it.startsWith("#") }
                if (streamUrl != null) {
                    val attributes = parseAttributes(extinf)
                    val displayName = extinf.substringAfterLast(',', attributes["tvg-name"].orEmpty()).trim()
                    val tvgId = attributes["tvg-id"]
                    val tvgName = attributes["tvg-name"]
                    val logoUrl = attributes["tvg-logo"]
                    val groupTitle = attributes["group-title"]
                    val contentRating = attributes["content-rating"]
                    channels.add(
                        LiveChannel(
                            id = tvgId ?: displayName.ifBlank { streamUrl },
                            name = displayName.ifBlank { tvgName ?: "Unknown Channel" },
                            streamUrl = streamUrl,
                            logoUrl = logoUrl,
                            groupTitle = groupTitle,
                            tvgId = tvgId,
                            tvgName = tvgName,
                            contentRating = contentRating,
                            isMature = isMatureChannel(contentRating, groupTitle, displayName)
                        )
                    )
                }
            }
            index += 1
        }
        return channels
    }

    private fun parseAttributes(extinf: String): Map<String, String> {
        return ATTRIBUTE_REGEX.findAll(extinf).associate { match ->
            match.groupValues[1] to match.groupValues[2]
        }
    }

    private fun isMatureChannel(contentRating: String?, groupTitle: String?, name: String): Boolean {
        val rating = contentRating?.uppercase()
        if (rating in MATURE_RATINGS) return true
        val searchable = listOfNotNull(groupTitle, name, contentRating).joinToString(" ").lowercase()
        return MATURE_KEYWORDS.any { keyword -> searchable.contains(keyword) }
    }

    companion object {
        private val ATTRIBUTE_REGEX = Regex("""([A-Za-z0-9_-]+)=\"([^\"]*)\"""")
        private val MATURE_RATINGS = setOf("TV-MA", "R", "NC-17", "18+")
        private val MATURE_KEYWORDS = setOf("adult", "xxx", "18+", "mature", "porn")
    }
}
