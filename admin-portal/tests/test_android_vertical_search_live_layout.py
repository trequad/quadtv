from pathlib import Path

ANDROID_ROOT = Path(__file__).resolve().parents[1] / ".." / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"


def read_android(path: str) -> str:
    return (ANDROID_ROOT / path).read_text()


def test_vod_and_jellyfin_routes_use_dedicated_browse_screens():
    main = read_android("MainActivity.kt")
    search = read_android("search/MovieSearchFragment.kt")

    assert "QuadTvRoute.MOVIE_SEARCH -> MovieSearchFragment()" in main
    assert "QuadTvRoute.VOD -> VodBrowseFragment()" in main
    assert "QuadTvRoute.JELLYFIN -> JellyfinBrowseFragment()" in main
    assert "class MovieSearchFragment : Fragment()" in search
    assert "ScrollView(context)" in search
    assert "vodRepository.searchMovies(query)" in search
    assert "jellyfinRepository.searchMovies(query)" in search
    assert "Available on VOD" in search
    assert "Available on Jellyfin" in search
    assert "VodDetailsFragment.newInstance(item)" in search
    assert "JellyfinDetailsFragment.newInstance(item)" in search


def test_vod_repository_has_title_search_across_available_vod_items():
    source = read_android("vod/VodRepository.kt")

    assert "suspend fun searchMovies(query: String): List<VodItem>" in source
    assert "loadRecentlyAdded()" in source
    assert "loadCategories().flatMap" in source
    assert "item.title.lowercase().contains(normalized)" in source


def test_live_tv_uses_left_groups_and_right_vertical_channels_layout():
    source = read_android("live/LiveTvFragment.kt")

    assert "class LiveTvFragment : Fragment()" in source
    assert "groupContainer" in source
    assert "channelContainer" in source
    assert "channels.groupBy" in source
    assert "renderGroupsAndChannels(selectedGroup" in source
    assert "sideButton(group" in source
    assert "channelButton(channel," in source
    assert "navigateToPlayer(request)" in source
