from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_favorites_and_recent_are_profile_scoped():
    store = read_android("live/LiveChannelBookmarkStore.kt")
    profile_cache = read_android("core/cache/ProfileSelectionCache.kt")
    picker = read_android("profiles/ProfilePickerFragment.kt")

    assert "class ProfileSelectionCache" in profile_cache
    assert "PREFERENCES_NAME = \"quadtv_profile_selection\"" in profile_cache
    assert "scopeKey()" in profile_cache
    assert "ProfileSelectionCache" in store
    assert "private val profileScope" in store
    assert "scopedKey(KEY_RECENT)" in store
    assert "scopedKey(KEY_FAVORITES)" in store
    assert "ProfileSelectionCache" in picker
    assert ".save(profile.id)" in picker


def test_home_refreshes_shortcuts_and_includes_vod_jellyfin_options():
    home = read_android("home/HomeFragment.kt")

    assert "override fun onResume()" in home
    assert "buildAndLoadHomeRows()" in home
    assert "QuadTvRoute.VOD" in home
    assert "HomeAction(\"Jellyfin\", QuadTvRoute.JELLYFIN)" in home
    assert "Favorite Live Channels" in home
    assert "Recently Viewed Live Channels" in home


def test_player_uses_friendly_tuning_copy_instead_of_debug_vlc_copy():
    player = read_android("player/PlayerFragment.kt")

    assert "Tuning to channel" in player
    assert "If the screen stays black, send this line back to Mimir" not in player
    assert "VLC: $status" not in player
    assert "friendlyStatus" in player


def test_vod_and_jellyfin_browse_show_content_with_metadata():
    vod = read_android("vod/VodBrowseFragment.kt")
    jellyfin = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "releaseYear" in vod
    assert "Recently Added" in vod
    assert "TV Series" in vod
    assert "productionYear" in jellyfin
    assert "Movies" in jellyfin
    assert "TV Shows" in jellyfin
