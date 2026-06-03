from pathlib import Path

ANDROID_SRC = Path(__file__).resolve().parents[2] / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"

BUTTON_FILES = [
    "home/HomeFragment.kt",
    "live/LiveTvFragment.kt",
    "epg/EpgGridFragment.kt",
    "vod/VodBrowseFragment.kt",
    "jellyfin/JellyfinBrowseFragment.kt",
    "settings/SettingsFragment.kt",
]


def test_tv_button_cards_show_clean_single_line_titles_only():
    for relative_path in BUTTON_FILES:
        source = (ANDROID_SRC / relative_path).read_text()
        assert "\\n${" not in source, f"{relative_path} still renders a second description line on cards"
        assert "description}" not in source, f"{relative_path} still binds description text directly to a card"


def test_home_quick_access_keeps_refresh_action_without_wordy_card_description():
    source = (ANDROID_SRC / "home/HomeFragment.kt").read_text()
    assert 'add(HomeAction("Refresh Playlist & Guide", refreshProviderFeeds = true))' in source
    assert '"Fetch the latest playlist and EPG for this QuadTV account."' not in source
