from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_provider_feed_refresh_coordinator_forces_live_tv_and_epg_refresh():
    coordinator = read_android("provider/ProviderFeedRefreshCoordinator.kt")
    live_repo = read_android("live/LiveTvRepository.kt")
    epg_repo = read_android("epg/EpgRepository.kt")
    feed_repo = read_android("provider/ProviderFeedRepository.kt")

    assert "class ProviderFeedRefreshCoordinator" in coordinator
    assert "data class ProviderFeedRefreshResult" in coordinator
    assert "refreshPlaylistAndGuide" in coordinator
    assert "liveTvRepository.loadChannels(forceRefresh = true)" in coordinator
    assert "epgRepository.loadProgrammes(forceRefresh = true)" in coordinator
    assert "playlistChannelCount" in coordinator
    assert "guideProgrammeCount" in coordinator
    assert "ProviderFeedPolicy.REFRESH_INTERVAL_MILLIS" in coordinator

    assert "suspend fun loadChannels(): List<LiveChannel>" in live_repo
    assert "suspend fun loadChannels(forceRefresh: Boolean)" in live_repo
    assert "loadOrRefreshLiveTvFeed(forceRefresh = forceRefresh)" in live_repo
    assert "suspend fun loadProgrammes(): List<EpgProgramme>" in epg_repo
    assert "suspend fun loadProgrammes(forceRefresh: Boolean)" in epg_repo
    assert "loadOrRefreshLiveTvFeed(forceRefresh = forceRefresh)" in epg_repo
    assert "forceRefresh" in feed_repo


def test_home_manual_refresh_runs_real_coordinator_with_progress_success_and_failure_copy():
    home = read_android("home/HomeFragment.kt")

    assert "ProviderFeedRefreshCoordinator" in home
    assert "buildProviderFeedRefreshCoordinator" in home
    assert "lifecycleScope.launch" in home
    assert "withContext(Dispatchers.IO)" in home
    assert "refreshPlaylistAndGuide()" in home
    assert "Refreshing channels, EPG, and VOD content" in home
    assert "guide entries" not in home
    assert "totalCount" in home
    assert "VOD Movies" in home
    assert "VOD TV Shows" in home
    assert "QuadOnDemand Movies" in home
    assert "QuadOnDemand TV Shows" in home
    assert "Something went wrong" in home
    assert "Close" in home
    assert "raw feed" not in home.lower()
    assert "password" not in home.lower()
    assert "token" not in home.lower()


def test_docs_record_real_manual_playlist_guide_refresh_slice():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "real manual Refresh Playlist & Guide action" in readme
    assert "ProviderFeedRefreshCoordinator" in readme
    assert "loading, success, and failure dialog states" in readme
    assert "raw provider credentials or feed URLs" in readme

    assert "### Task 9.20: Real manual playlist and guide refresh" in plan
    assert "ProviderFeedRefreshCoordinator" in plan
    assert "focused provider-feed refresh tests" in plan
