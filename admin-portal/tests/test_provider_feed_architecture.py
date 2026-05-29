from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_launch_config_models_provider_base_urls_not_global_playlist_urls():
    launch = read_android("adminapi/LaunchConfig.kt")
    api = read_android("adminapi/AdminApiService.kt")
    cache = read_android("core/cache/LaunchConfigCache.kt")
    constants = read_android("core/config/QuadTvConfig.kt")

    assert "liveTvProviderBaseUrl: String" in launch
    assert "vodProviderBaseUrl: String" in launch
    assert "providerFeedRefreshHours: Int" in launch
    assert "@Json(name = \"live_tv_provider_base_url\") val liveTvProviderBaseUrl" in api
    assert "@Json(name = \"vod_provider_base_url\") val vodProviderBaseUrl" in api
    assert "KEY_LIVE_TV_PROVIDER_BASE_URL" in cache
    assert "KEY_VOD_PROVIDER_BASE_URL" in cache
    assert "OPERATOR_LIVE_TV_PROVIDER_BASE_URL = \"http://by.questreams.com:83\"" in constants
    assert "OPERATOR_VOD_PROVIDER_BASE_URL = \"https://livinitup.online\"" in constants
    assert "liveTvEndpoint" not in launch
    assert "xmltvEndpoint" not in launch


def test_live_tv_and_epg_repositories_use_per_user_provider_feed_resolver():
    live_repo = read_android("live/LiveTvRepository.kt")
    epg_repo = read_android("epg/EpgRepository.kt")
    feed_models = read_android("provider/ProviderFeedModels.kt")
    feed_repo = read_android("provider/ProviderFeedRepository.kt")

    assert "ProviderFeedRepository" in live_repo
    assert "loadOrRefreshLiveTvFeed()" in live_repo
    assert "feed.liveTvPlaylistUrl" in live_repo
    assert "launchConfig.liveTvProviderBaseUrl" not in live_repo
    assert "liveTvEndpoint" not in live_repo

    assert "ProviderFeedRepository" in epg_repo
    assert "loadOrRefreshLiveTvFeed()" in epg_repo
    assert "feed.xmltvUrl" in epg_repo
    assert "xmltvEndpoint" not in epg_repo

    assert "data class LiveTvProviderFeed" in feed_models
    assert "liveTvPlaylistUrl: String" in feed_models
    assert "xmltvUrl: String" in feed_models
    assert "fetchedAtMillis: Long" in feed_models
    assert "PROVIDER_FEED_REFRESH_HOURS = 24" in feed_models
    assert "currentTimeMillis - cached.fetchedAtMillis" in feed_repo
    assert "deriveLiveTvPlaylistUrl" in feed_repo
    assert "deriveXmltvUrl" in feed_repo
    assert "providerPassword" not in feed_repo
    assert "password" not in feed_models.lower()


def test_home_manual_refresh_and_release_update_architecture_are_planned():
    home = read_android("home/HomeFragment.kt")
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Refresh Playlist & Guide" in home
    assert "QuadTV is fetching your playlist and guide from the configured provider DNS" in home
    assert "raw feed URLs" not in home
    assert "provider password" not in home.lower()

    assert "per-user provider feed" in readme.lower()
    assert "manual Refresh Playlist & Guide action" in readme
    assert "self-hosted APK release metadata" in readme
    assert "### Task 9.13: Per-user provider feed modeling correction" in plan
    assert "### Future Phase: Sideloaded APK release and update distribution" in plan
    assert "versionName/versionCode" in plan
    assert "forced update" in plan
