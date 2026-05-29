from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_launch_config_dto_maps_fastapi_snake_case_fields():
    source = read_android("adminapi/AdminApiService.kt")

    assert '@Json(name = "live_tv_provider_base_url")' in source
    assert '@Json(name = "vod_provider_base_url")' in source
    assert '@Json(name = "provider_feed_refresh_hours")' in source
    assert '@Json(name = "jellyfin_base_url")' in source
    assert '@Json(name = "max_profiles_per_device")' in source
    assert '@Json(name = "warning_threshold_days")' in source
    assert '@Json(name = "live_stream_limit_per_user")' in source
    assert '@Json(name = "vod_stream_limit_per_user")' in source
    assert '@Json(name = "jellyfin_stream_limit_per_user")' in source


def test_launch_config_repository_fetches_portal_config_and_falls_back_to_cache_then_defaults():
    repository = read_android("adminapi/AdminConfigRepository.kt")
    cache = read_android("core/cache/LaunchConfigCache.kt")

    assert "class AdminConfigRepository" in repository
    assert "suspend fun loadLaunchConfig" in repository
    assert "apiService.getLaunchConfig()" in repository
    assert "cache.save" in repository
    assert "cache.load()" in repository
    assert "LaunchConfig.defaults()" in repository
    assert "class LaunchConfigCache" in cache
    assert "SharedPreferences" in cache
    assert "save(config: LaunchConfig)" in cache
    assert "load(): LaunchConfig?" in cache


def test_launch_config_domain_model_contains_stream_limits_and_baked_defaults():
    source = read_android("adminapi/LaunchConfig.kt")

    assert "data class LaunchConfig" in source
    assert "liveTvProviderBaseUrl: String" in source
    assert "vodProviderBaseUrl: String" in source
    assert "providerFeedRefreshHours: Int" in source
    assert "jellyfinBaseUrl: String?" in source
    assert "maxProfilesPerDevice: Int" in source
    assert "warningThresholdDays: List<Int>" in source
    assert "liveStreamLimitPerUser: Int" in source
    assert "vodStreamLimitPerUser: Int" in source
    assert "jellyfinStreamLimitPerUser: Int" in source
    assert "fun defaults()" in source
    assert "QuadTvConfig.OPERATOR_LIVE_TV_PROVIDER_BASE_URL" in source
    assert "QuadTvConfig.OPERATOR_VOD_PROVIDER_BASE_URL" in source
    assert "QuadTvConfig.PROVIDER_FEED_REFRESH_HOURS" in source
