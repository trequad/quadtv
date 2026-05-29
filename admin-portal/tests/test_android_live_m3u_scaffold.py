from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_channel_model_contains_epg_logo_group_rating_and_mature_flags():
    source = read_android("live/LiveChannel.kt")

    assert 'data class LiveChannel' in source
    assert 'id: String' in source
    assert 'name: String' in source
    assert 'streamUrl: String' in source
    assert 'logoUrl: String?' in source
    assert 'groupTitle: String?' in source
    assert 'tvgId: String?' in source
    assert 'tvgName: String?' in source
    assert 'contentRating: String?' in source
    assert 'isMature: Boolean' in source


def test_m3u_parser_extracts_extinf_attributes_and_following_stream_url():
    source = read_android("live/M3uParser.kt")

    assert 'class M3uParser' in source
    assert 'fun parse(playlist: String): List<LiveChannel>' in source
    assert '#EXTINF' in source
    assert 'parseAttributes' in source
    assert 'tvg-id' in source
    assert 'tvg-name' in source
    assert 'tvg-logo' in source
    assert 'group-title' in source
    assert 'content-rating' in source
    assert 'streamUrl = streamUrl' in source
    assert 'displayName' in source


def test_m3u_parser_marks_mature_channels_from_rating_group_or_name_keywords():
    source = read_android("live/M3uParser.kt")

    assert 'private fun isMatureChannel' in source
    assert 'contentRating' in source
    assert 'groupTitle' in source
    assert 'name' in source
    assert 'MATURE_KEYWORDS' in source
    assert 'adult' in source.lower()
    assert 'xxx' in source.lower()
    assert '18+' in source
    assert 'MATURE_RATINGS' in source
    assert 'TV-MA' in source
    assert 'R' in source
    assert 'NC-17' in source


def test_live_repository_fetches_m3u_from_launch_config_endpoint_with_okhttp():
    source = read_android("live/LiveTvRepository.kt")

    assert 'class LiveTvRepository' in source
    assert 'OkHttpClient' in source
    assert 'ProviderFeedRepository' in source
    assert 'M3uParser' in source
    assert 'suspend fun loadChannels(): List<LiveChannel>' in source
    assert 'loadOrRefreshLiveTvFeed()' in source
    assert 'feed.liveTvPlaylistUrl' in source
    assert 'Request.Builder().url' in source
    assert 'parser.parse' in source
