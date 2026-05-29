from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_settings_fragment_exposes_only_user_safe_preferences():
    source = read_android("settings/SettingsFragment.kt")

    assert "class SettingsFragment" in source
    assert "BrowseSupportFragment" in source
    assert "QuadTV Settings" in source
    assert "QuadMedia" in source
    assert "Player" in source
    assert "ExoPlayer" in source
    assert "VLC" in source
    assert "Buffering" in source
    assert "small / medium / large / custom" in source
    assert "Subtitle language" in source
    assert "Audio track" in source
    assert "Parental controls" in source
    assert "Clear watch history" in source
    assert "About QuadTV" in source
    assert "version" in source.lower()

    forbidden_user_facing_terms = [
        "LIVE_TV_DNS_ENDPOINT",
        "VOD_DNS_ENDPOINT",
        "LIVE_TV_XMLTV_ENDPOINT",
        "ADMIN_PORTAL_BASE_URL",
        "Jellyfin API key",
        "provider password",
    ]
    for term in forbidden_user_facing_terms:
        assert term not in source


def test_settings_models_connect_to_player_and_profile_preferences():
    source = read_android("settings/SettingsModels.kt")

    assert "enum class SettingsSection" in source
    assert "PLAYER" in source
    assert "BUFFERING" in source
    assert "LANGUAGES" in source
    assert "PARENTAL" in source
    assert "WATCH_HISTORY" in source
    assert "ABOUT" in source
    assert "data class SettingsOption" in source
    assert "PlayerSettingsCache" in source
    assert "PlayerEngine" in source
    assert "BufferStrategy" in source
    assert "ProfileParentalState" in source
    assert "class ProfilePreferencesClearAction" in source
    assert "fun clearWatchHistory(profileId: Int)" in source
    assert "last_watched_channel" in source
    assert "recently_watched_vod" in source


def test_readme_and_plan_document_settings_scaffold():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Settings foundation source files" in readme
    assert "SettingsFragment.kt" in readme
    assert "SettingsModels.kt" in readme
    assert "end-user settings do not expose backend URLs" in readme
    assert "### Task 9.3: Android user settings scaffold" in plan
    assert "**Status:** Initial Android source implementation complete" in plan
    assert "test_android_settings_scaffold.py" in plan
