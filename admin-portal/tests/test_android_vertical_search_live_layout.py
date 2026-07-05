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
    assert "Available on QuadOnDemand" in search
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
    assert "ALL_CHANNELS_GROUP" in source
    assert "contentHeader.text = selectedGroupName" in source
    assert "Live TV" in source
    assert "Categories" in source
    assert "FAVORITES_GROUP" in source
    assert "bookmarkStore.favoriteChannels()" in source
    assert "FAVORITES_GROUP to favoriteChannels" in source
    assert "channels.groupBy" in source
    assert "sortedGroupNames(grouped.keys)" in source
    assert "renderGroupsAndChannels(selectedGroup" in source
    assert "sideButton(group" in source
    assert "channelButton(channel," in source
    assert "navigateToPlayer(request)" in source


def test_home_screen_has_left_menu_and_right_feature_rows():
    source = read_android("home/HomeFragment.kt")

    assert "menuContainer" in source
    assert "rightContainer" in source
    assert "HomeAction(\"Live TV\", QuadTvRoute.LIVE_TV)" in source
    assert "HomeAction(\"VOD\", QuadTvRoute.VOD)" in source
    assert "HomeAction(\"QuadOnDemand\", QuadTvRoute.JELLYFIN)" in source
    assert "HomeAction(\"Search\", QuadTvRoute.MOVIE_SEARCH)" in source
    assert "HomeAction(\"Refresh\", refreshProviderFeeds = true)" in source
    # Customer-facing label is "Requests"; Seerr is an internal name.
    assert "HomeAction(\"Requests\", QuadTvRoute.SEERR)" in source
    assert "Recently Watched Channels" in source
    assert "Recently Added VOD" in source
    assert "Recently Released QuadOnDemand Movies" in source
    assert "Recently Released TV Shows" in source


def test_jellyfin_home_rows_request_recently_released_movies_and_shows():
    home = read_android("home/HomeFragment.kt")
    repo = read_android("jellyfin/JellyfinRepository.kt")

    assert "quadRepo.loadRecentlyReleasedMoviesPage(limit = 5)" in home
    assert "quadRepo.loadRecentlyReleasedSeriesPage(limit = 5)" in home
    assert "suspend fun loadRecentlyReleasedMoviesPage" in repo
    assert "suspend fun loadRecentlyReleasedSeriesPage" in repo
    assert "IncludeItemTypes=Movie" in repo
    assert "IncludeItemTypes=Series" in repo
    assert "SortBy=PremiereDate" in repo
    assert "SortOrder=Descending" in repo
