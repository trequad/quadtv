from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_jellyfin_repository_uses_server_pagination_instead_of_fixed_100_item_cap():
    source = read_android("jellyfin/JellyfinRepository.kt")

    assert "DEFAULT_PAGE_SIZE" in source
    assert "StartIndex=$startIndex" in source
    assert "Limit=$limit" in source
    assert "JellyfinPage(" in source
    assert "TotalRecordCount" not in source  # parsed by model, not ignored in URL construction
    assert "&Limit=100" not in source
    assert "&Limit=50" not in source


def test_jellyfin_browse_loads_more_pages_and_shows_total_counts():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "loadMoreCurrentSection" in source
    assert "JellyfinPage" in source
    assert "hasMore" in source
    assert "of ${page.totalCount}" in source
    assert "Load more" in source


def test_vod_repository_exposes_paged_results_without_user_visible_100_item_caps():
    source = read_android("vod/VodRepository.kt")

    assert "DEFAULT_PAGE_SIZE" in source
    assert "VodPage(" in source
    assert "loadRecentlyAddedPage" in source
    assert "loadSeriesPage" in source
    assert "searchMoviesPage" in source
    assert ".take(100)" not in source


def test_vod_browse_uses_paged_load_more_for_large_provider_libraries():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "loadMoreCurrentSection" in source
    assert "VodPage" in source
    assert "hasMore" in source
    assert "Load more" in source
    assert "of ${page.totalCount}" in source
