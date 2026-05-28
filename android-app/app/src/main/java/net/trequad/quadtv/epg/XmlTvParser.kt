package net.trequad.quadtv.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

class XmlTvParser {
    fun parse(xml: String): List<EpgProgramme> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val programmes = mutableListOf<EpgProgramme>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "programme") {
                readProgramme(parser)?.let(programmes::add)
            }
            eventType = parser.next()
        }
        return programmes
    }

    private fun readProgramme(parser: XmlPullParser): EpgProgramme? {
        val channelId = parser.getAttributeValue(null, "channel") ?: return null
        val start = parseXmlTvTime(parser.getAttributeValue(null, "start"))
        val stop = parseXmlTvTime(parser.getAttributeValue(null, "stop"))
        var title = "Untitled"
        var desc: String? = null
        var category: String? = null
        var rating: String? = null
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "programme")) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "title" -> title = parser.nextText()
                    "desc" -> desc = parser.nextText()
                    "category" -> category = parser.nextText()
                    "rating" -> rating = readRating(parser)
                }
            }
            parser.next()
        }
        return EpgProgramme(
            channelId = channelId,
            title = title,
            description = desc,
            startTimeMillis = start,
            endTimeMillis = stop,
            category = category,
            rating = rating,
            isMature = isMatureProgramme(category, rating, title)
        )
    }

    private fun readRating(parser: XmlPullParser): String? {
        var value: String? = null
        while (!(parser.eventType == XmlPullParser.END_TAG && parser.name == "rating")) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "value") {
                value = parser.nextText()
            }
            parser.next()
        }
        return value
    }

    private fun parseXmlTvTime(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val normalized = raw.replace(Regex(" ([+-]\\d{4})$"), "\$1")
        return runCatching {
            SimpleDateFormat("yyyyMMddHHmmssZ", Locale.US).parse(normalized)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun isMatureProgramme(category: String?, rating: String?, title: String): Boolean {
        val ratingUpper = rating?.uppercase()
        if (ratingUpper in MATURE_RATINGS) return true
        val searchable = listOfNotNull(category, rating, title).joinToString(" ").lowercase()
        return MATURE_KEYWORDS.any { searchable.contains(it) }
    }

    companion object {
        private val MATURE_RATINGS = setOf("TV-MA", "R", "NC-17", "18+")
        private val MATURE_KEYWORDS = setOf("adult", "xxx", "mature", "18+")
    }
}
