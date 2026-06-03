package net.trequad.quadtv.live

import net.trequad.quadtv.epg.EpgProgramme

fun List<EpgProgramme>.currentProgrammesByChannel(channels: List<LiveChannel>): Map<String, EpgProgramme> {
    val now = System.currentTimeMillis()
    val currentByIdentifier = asSequence()
        .filter { now >= it.startTimeMillis && now < it.endTimeMillis }
        .groupBy { it.channelId.trim().lowercase() }
        .mapValues { (_, progs) -> progs.first() }
    return channels.mapNotNull { channel ->
        val identifiers = listOfNotNull(channel.tvgId, channel.tvgName, channel.name, channel.id)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        val current = identifiers.firstNotNullOfOrNull { currentByIdentifier[it] }
        current?.let { channel.id to it }
    }.toMap()
}
