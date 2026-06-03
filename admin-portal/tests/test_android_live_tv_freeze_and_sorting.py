from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_tv_groups_are_alphabetical_with_adult_xxx_groups_last():
    source = read_android("live/LiveTvFragment.kt")

    assert "sortedGroupNames" in source
    assert "isAdultGroupTitle" in source
    assert "compareBy<String> { isAdultGroupTitle(it) }" in source
    assert "String.CASE_INSENSITIVE_ORDER" in source
    assert "xxx" in source.lower()
    assert "adult" in source.lower()
    assert "channelsByGroup = sortedGroupNames(grouped.keys).associateWith" in source
    assert ".toSortedMap()" not in source


def test_live_tv_does_not_reset_selected_group_when_epg_finishes_loading():
    source = read_android("live/LiveTvFragment.kt")

    assert "private var selectedGroup: String? = null" in source
    assert "selectedGroup = selectedGroupName" in source
    assert "renderGroupsAndChannels(selectedGroup ?: channelsByGroup.keys.first())" in source
    assert "channelsByGroup.keys.firstOrNull()?.let { renderGroupsAndChannels(it) }" not in source


def test_live_tv_expensive_epg_matching_runs_off_main_thread_and_uses_index():
    source = read_android("live/LiveTvFragment.kt")
    epg_utils = read_android("live/EpgUtils.kt")

    assert "withContext(Dispatchers.Default) { programmes.currentProgrammesByChannel(channels) }" in source
    assert "val currentByIdentifier" in epg_utils
    assert "firstNotNullOfOrNull" in epg_utils
    assert "firstOrNull { programme ->" not in source


def test_m3u_parser_scans_playlist_linearly_without_drop_per_channel():
    source = read_android("live/M3uParser.kt")

    assert "pendingExtinf" in source
    assert "lineSequence()" in source
    assert ".drop(index + 1)" not in source
    assert "while (index < lines.size)" not in source
