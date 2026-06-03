from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
PROVIDER_FEEDS = PROJECT_ROOT / "admin-portal/app/routers/provider_feeds.py"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_admin_provider_feed_requests_mpegts_playlist_not_hls():
    source = PROVIDER_FEEDS.read_text()

    assert "output=mpegts" in source
    assert "output=m3u8" not in source


def test_android_live_repository_normalizes_existing_hls_playlist_url_to_mpegts():
    source = read_android("live/LiveTvRepository.kt")

    assert "normaliseLivePlaylistOutput" in source
    assert "output=mpegts" in source
    assert "[?&]output=" in source
    assert "replace(Regex(\"([?&])output=[^&]*\"), \"\\$1output=mpegts\")" in source
    assert "fetchChannels(normaliseLivePlaylistOutput(feed.liveTvPlaylistUrl))" in source


def test_live_playback_copy_no_longer_mentions_alternate_player():
    source = read_android("live/LiveTvPlaybackCoordinator.kt")

    assert "alternate bundled player" not in source
    assert "embedded VLC" in source
