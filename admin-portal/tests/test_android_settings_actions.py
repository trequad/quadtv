from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_home_left_quick_menu_scrolls_to_settings_on_small_screens():
    source = read_android("home/HomeFragment.kt")

    assert "private lateinit var menuScrollView: ScrollView" in source
    assert "menuScrollView = ScrollView(context).apply" in source
    assert "menuScrollView.smoothScrollTo(0, view.bottom)" in source
    assert 'HomeAction("Settings", QuadTvRoute.SETTINGS)' in source


def test_settings_buttons_are_real_actions_not_placeholder_rows():
    fragment = read_android("settings/SettingsFragment.kt")
    models = read_android("settings/SettingsModels.kt")

    assert "setOnItemViewClickedListener" in fragment
    assert "handleSettingsOption" in fragment
    assert "showRefreshDialog" in fragment
    assert "clearCurrentProfileHistory" in fragment
    assert "toggleParentalControls" in fragment
    assert "logoutToLogin" in fragment
    assert "Built-in QuadTV player powered by VLC" in fragment
    assert "SettingsAction" in models
    assert "REFRESH_CONTENT" in models
    assert "CLEAR_WATCH_HISTORY" in models
    assert "TOGGLE_PARENTAL_RATING_BLOCK" in models
    assert "LOG_OUT" in models
    assert "BUFFERING" not in models


def test_parental_rating_block_is_locally_toggleable_and_used_by_browse_screens():
    parental = read_android("parental/ParentalControls.kt")
    settings = read_android("settings/SettingsFragment.kt")
    vod = read_android("vod/VodBrowseFragment.kt")
    jellyfin = read_android("jellyfin/JellyfinBrowseFragment.kt")
    live = read_android("live/LiveTvFragment.kt")

    assert "class ParentalSettingsCache" in parental
    assert "fun toggleForProfile(profileId: Int): Boolean" in parental
    assert "contentRatings: Set<String> = setOf(\"R\", \"NC-17\", \"TV-MA\")" in parental
    assert "ParentalSettingsCache" in settings
    assert "filterVodItems(profileParentalState(), page.items)" in vod
    assert "filterJellyfinItems(profileParentalState(), page.items)" in jellyfin
    assert "filterLiveChannels(profileParentalState(), channels)" in live
