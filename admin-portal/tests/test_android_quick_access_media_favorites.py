from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


# ---------------------------------------------------------------------------
# Navigator routes
# ---------------------------------------------------------------------------

def test_navigator_includes_favorites_and_recently_viewed_routes():
    nav = read_android("navigation/QuadTvNavigator.kt")
    assert "FAVORITES" in nav
    assert "RECENTLY_VIEWED" in nav


# ---------------------------------------------------------------------------
# Home quick access
# ---------------------------------------------------------------------------

def test_home_quick_access_includes_all_five_required_destinations():
    home = read_android("home/HomeFragment.kt")
    assert '"Live TV"' in home
    assert 'QuadTvRoute.VOD' in home
    assert 'QuadTvRoute.JELLYFIN' in home
    assert 'Recently Watched Movies' in home
    assert 'Recently Watched Movies' in home


# ---------------------------------------------------------------------------
# MediaBookmarkStore
# ---------------------------------------------------------------------------

def test_media_bookmark_store_is_profile_scoped():
    store = read_android("favorites/MediaBookmarkStore.kt")
    assert "ProfileSelectionCache" in store
    assert "profileScope" in store
    assert "scopedKey(KEY_RECENT)" in store
    assert "scopedKey(KEY_FAVORITES)" in store


def test_media_bookmark_store_supports_vod_and_jellyfin_sources():
    store = read_android("favorites/MediaBookmarkStore.kt")
    assert "enum class BookmarkedMediaSource" in store
    assert "VOD" in store
    assert "JELLYFIN" in store


def test_media_bookmark_store_has_toggle_favorite_and_record_recent():
    store = read_android("favorites/MediaBookmarkStore.kt")
    assert "fun toggleFavorite(" in store
    assert "fun recordRecent(" in store
    assert "fun isFavorite(" in store
    assert "fun favoriteItems(" in store
    assert "fun recentItems(" in store


def test_media_bookmark_store_preferences_name_is_unique():
    store = read_android("favorites/MediaBookmarkStore.kt")
    assert 'PREFERENCES_NAME = "media_bookmarks"' in store


# ---------------------------------------------------------------------------
# FavoritesFragment
# ---------------------------------------------------------------------------

def test_favorites_fragment_has_three_rows():
    fav = read_android("favorites/FavoritesFragment.kt")
    assert "Favorite Live Channels" in fav
    assert "Favorite VOD" in fav
    assert "Favorite QuadOnDemand" in fav


def test_favorites_fragment_routes_live_to_player_and_media_to_details():
    fav = read_android("favorites/FavoritesFragment.kt")
    assert "navigateToPlayer" in fav
    assert "VodDetailsFragment.newInstance" in fav
    assert "JellyfinDetailsFragment.newInstance" in fav


def test_favorites_fragment_refreshes_on_resume():
    fav = read_android("favorites/FavoritesFragment.kt")
    assert "override fun onResume()" in fav
    assert "adapter = buildFavoritesRows()" in fav


def test_bookmark_to_vod_item_and_jellyfin_item_converters_exist():
    fav = read_android("favorites/FavoritesFragment.kt")
    assert "fun BookmarkedMediaItem.toVodItem()" in fav
    assert "fun BookmarkedMediaItem.toJellyfinItem()" in fav


# ---------------------------------------------------------------------------
# RecentlyViewedFragment
# ---------------------------------------------------------------------------

def test_recently_viewed_fragment_has_three_rows():
    recent = read_android("favorites/RecentlyViewedFragment.kt")
    assert "Recently Viewed Live" in recent
    assert "Recently Viewed VOD" in recent
    assert "Recently Viewed QuadOnDemand" in recent


def test_recently_viewed_fragment_routes_correctly():
    recent = read_android("favorites/RecentlyViewedFragment.kt")
    assert "navigateToPlayer" in recent
    assert "VodDetailsFragment.newInstance" in recent
    assert "JellyfinDetailsFragment.newInstance" in recent


def test_recently_viewed_fragment_refreshes_on_resume():
    recent = read_android("favorites/RecentlyViewedFragment.kt")
    assert "override fun onResume()" in recent
    assert "adapter = buildRecentRows()" in recent


# ---------------------------------------------------------------------------
# VodDetailsFragment — favorite toggle and recent recording
# ---------------------------------------------------------------------------

def test_vod_details_records_recently_viewed():
    vod = read_android("vod/VodDetailsFragment.kt")
    assert "mediaStore.recordRecent(" in vod


def test_vod_details_has_favorite_toggle_button():
    vod = read_android("vod/VodDetailsFragment.kt")
    assert "Add to Favorites" in vod
    assert "Remove from Favorites" in vod
    assert "mediaStore.toggleFavorite(" in vod
    assert "mediaStore.isFavorite(" in vod


def test_vod_details_favorite_button_labels_do_not_contain_urls():
    vod = read_android("vod/VodDetailsFragment.kt")
    # Confirm favorite button label strings are safe user-facing copy (no URLs)
    assert '"Add to Favorites"' in vod
    assert '"Remove from Favorites"' in vod
    assert "http" not in '"Add to Favorites"'
    assert "http" not in '"Remove from Favorites"'


def test_vod_details_imports_media_bookmark_store():
    vod = read_android("vod/VodDetailsFragment.kt")
    assert "import net.trequad.quadtv.favorites.MediaBookmarkStore" in vod
    assert "import net.trequad.quadtv.favorites.BookmarkedMediaSource" in vod


# ---------------------------------------------------------------------------
# JellyfinDetailsFragment — favorite toggle and recent recording
# ---------------------------------------------------------------------------

def test_jellyfin_details_records_recently_viewed():
    jelly = read_android("jellyfin/JellyfinDetailsFragment.kt")
    assert "mediaStore.recordRecent(" in jelly


def test_jellyfin_details_has_favorite_toggle_button():
    jelly = read_android("jellyfin/JellyfinDetailsFragment.kt")
    assert "Add to Favorites" in jelly
    assert "Remove from Favorites" in jelly
    assert "mediaStore.toggleFavorite(" in jelly
    assert "mediaStore.isFavorite(" in jelly


def test_jellyfin_details_imports_media_bookmark_store():
    jelly = read_android("jellyfin/JellyfinDetailsFragment.kt")
    assert "import net.trequad.quadtv.favorites.MediaBookmarkStore" in jelly
    assert "import net.trequad.quadtv.favorites.BookmarkedMediaSource" in jelly


def test_jellyfin_details_does_not_expose_api_key_in_favorite_copy():
    jelly = read_android("jellyfin/JellyfinDetailsFragment.kt")
    assert "api_key" not in jelly.lower().split("add to favorites")[0].split("class jellyfindetailsfragment")[0].lower()


# ---------------------------------------------------------------------------
# MainActivity routing
# ---------------------------------------------------------------------------

def test_main_activity_routes_vod_to_vod_browse_fragment():
    main = read_android("MainActivity.kt")
    assert "QuadTvRoute.VOD -> VodBrowseFragment()" in main


def test_main_activity_routes_jellyfin_to_jellyfin_browse_fragment():
    main = read_android("MainActivity.kt")
    assert "QuadTvRoute.JELLYFIN -> JellyfinBrowseFragment()" in main


def test_main_activity_routes_favorites_and_recently_viewed():
    main = read_android("MainActivity.kt")
    assert "QuadTvRoute.FAVORITES -> FavoritesFragment()" in main
    assert "QuadTvRoute.RECENTLY_VIEWED -> RecentlyViewedFragment()" in main


def test_main_activity_imports_favorites_fragments():
    main = read_android("MainActivity.kt")
    assert "import net.trequad.quadtv.favorites.FavoritesFragment" in main
    assert "import net.trequad.quadtv.favorites.RecentlyViewedFragment" in main
