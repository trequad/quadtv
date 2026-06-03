from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_tv_playback_request_carries_channel_zap_lineup():
    contract = read_android("player/PlayerContract.kt")
    coordinator = read_android("live/LiveTvPlaybackCoordinator.kt")
    fragment = read_android("live/LiveTvFragment.kt")

    assert "val liveChannelUrls: List<String> = emptyList()" in contract
    assert "val liveChannelTitles: List<String> = emptyList()" in contract
    assert "val liveChannelGroupTitles: List<String> = emptyList()" in contract
    assert "val liveChannelContentTitles: List<String> = emptyList()" in contract
    assert "val liveChannelIndex: Int = -1" in contract
    assert "fun buildRequest(channel: LiveChannel, lineup: List<LiveChannel>)" in coordinator
    assert "Math.floorMod(currentIndex + offset, lineup.size)" in coordinator
    assert "liveChannelUrls = playableLineup.map { it.streamUrl }" in coordinator
    assert "liveChannelTitles = playableLineup.map { it.name }" in coordinator
    assert "liveChannelGroupTitles = playableLineup.map { it.groupTitle.orEmpty() }" in coordinator
    assert "liveChannelContentTitles = playableLineup.map" in coordinator
    assert "playbackCoordinator.buildRequest(channel, selectedLineup, programme?.title)" in fragment


def test_player_fragment_maps_channel_up_down_keys_to_in_player_switching():
    source = read_android("player/PlayerFragment.kt")

    assert "KeyEvent.KEYCODE_CHANNEL_UP" in source
    assert "KeyEvent.KEYCODE_CHANNEL_DOWN" in source
    assert "KeyEvent.KEYCODE_DPAD_UP" in source
    assert "KeyEvent.KEYCODE_DPAD_DOWN" in source
    assert "private fun switchLiveChannel(offset: Int): Boolean" in source
    assert "private fun liveChannelRequestAt(request: StreamPlaybackRequest, targetIndex: Int)" in source
    assert "startBundledPlayback(switchedRequest.copy(bufferConfig = request.bufferConfig))" in source
    assert "CH+/Up:" in source
    assert "CH-/Down:" in source


def test_player_fragment_persists_live_lineup_through_arguments():
    source = read_android("player/PlayerFragment.kt")

    assert "ARG_LIVE_CHANNEL_URLS" in source
    assert "ARG_LIVE_CHANNEL_TITLES" in source
    assert "ARG_LIVE_CHANNEL_INDEX" in source
    assert "args.getStringArrayList(ARG_LIVE_CHANNEL_URLS).orEmpty()" in source
    assert "args.getStringArrayList(ARG_LIVE_CHANNEL_TITLES).orEmpty()" in source
    assert "args.getStringArrayList(ARG_LIVE_CHANNEL_GROUP_TITLES).orEmpty()" in source
    assert "args.getStringArrayList(ARG_LIVE_CHANNEL_CONTENT_TITLES).orEmpty()" in source
    assert "putStringArrayList(ARG_LIVE_CHANNEL_URLS, ArrayList(request.liveChannelUrls))" in source
    assert "putStringArrayList(ARG_LIVE_CHANNEL_TITLES, ArrayList(request.liveChannelTitles))" in source
    assert "putStringArrayList(ARG_LIVE_CHANNEL_GROUP_TITLES, ArrayList(request.liveChannelGroupTitles))" in source
    assert "putStringArrayList(ARG_LIVE_CHANNEL_CONTENT_TITLES, ArrayList(request.liveChannelContentTitles))" in source
