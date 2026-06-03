from pathlib import Path

ANDROID_ROOT = Path(__file__).resolve().parents[1] / ".." / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"


def read_android(path: str) -> str:
    return (ANDROID_ROOT / path).read_text()


def test_jellyfin_playback_uses_static_stream_not_broken_master_playlist():
    source = read_android("jellyfin/JellyfinRepository.kt")

    assert "/Videos/$itemId/stream?static=true&api_key=" in source
    assert "/Videos/$itemId/master.m3u8" not in source


def test_jellyfin_repository_searches_movies_with_metadata_fields():
    source = read_android("jellyfin/JellyfinRepository.kt")

    assert "suspend fun searchMovies(query: String): List<JellyfinItem>" in source
    assert "IncludeItemTypes=Movie" in source
    assert "SearchTerm=$encodedQuery" in source
    assert "Fields=Overview,OfficialRating,ProductionYear,PrimaryImageAspectRatio" in source


def test_jellyfin_browse_exposes_search_dialog_and_result_rows():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "Search Jellyfin" in source
    assert "searchContent" in source
    assert "repo.searchMovies(query)" in source
