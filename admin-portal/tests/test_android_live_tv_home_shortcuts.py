from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_channel_bookmark_store_tracks_recent_and_favorite_channels():
    source = read_android("live/LiveChannelBookmarkStore.kt")

    assert "class LiveChannelBookmarkStore" in source
    assert "KEY_RECENT = \"recent_live_channels\"" in source
    assert "KEY_FAVORITES = \"favorite_live_channels\"" in source
    assert "fun recordRecent(channel: LiveChannel" in source
    assert "fun recordRecent(channel: BookmarkedLiveChannel)" in source
    assert "fun toggleFavorite(channel: LiveChannel" in source
    assert "fun toggleFavorite(channel: BookmarkedLiveChannel)" in source
    assert "MAX_RECENT = 12" in source


def test_stream_playback_request_carries_live_channel_identity_for_shortcuts():
    contract = read_android("player/PlayerContract.kt")
    coordinator = read_android("live/LiveTvPlaybackCoordinator.kt")
    player = read_android("player/PlayerFragment.kt")

    assert "val channelId: String? = null" in contract
    assert "val liveChannelIds: List<String> = emptyList()" in contract
    assert "channelId = channel.id" in coordinator
    assert "liveChannelIds = playableLineup.map { it.id }" in coordinator
    assert "ARG_CHANNEL_ID" in player
    assert "ARG_LIVE_CHANNEL_IDS" in player


def test_home_screen_renders_favorites_and_recent_live_channel_rows():
    source = read_android("home/HomeFragment.kt")

    assert "LiveChannelBookmarkStore" in source
    assert "Recently Watched Channels" in source
    assert "Recently Watched Movies" in source
    assert "recentChannels()" in source
    assert "bookmarkStore.recentChannels()" in source
    assert "bookmarkStore.recentChannels()" in source
    assert "playbackRequest = channel.toPlaybackRequest()" in source
    assert "navigateToPlayer(tile.playbackRequest)" in source


def test_live_tv_and_player_can_bookmark_and_record_recent_channels():
    live = read_android("live/LiveTvFragment.kt")
    player = read_android("player/PlayerFragment.kt")

    assert "bookmarkStore.recordRecent(channel, programme?.title)" in live
    assert "setOnLongClickListener" in live
    assert "bookmarkStore.toggleFavorite(channel, programme?.title)" in live
    # Favorite is an icon button since the 2026-07-04 icon-controls pass.
    assert "btn_star_big_off" in player
    assert "toggleCurrentFavorite()" in player
    assert "toggleCurrentFavorite" in player
    assert "recordRecentLiveChannel(playableRequest)" in player
    assert "LiveChannelBookmarkStore(requireContext().applicationContext).recordRecent" in player
