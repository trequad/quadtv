from pathlib import Path

ANDROID_ROOT = Path(__file__).resolve().parents[1] / ".." / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"


def read_android(path: str) -> str:
    return (ANDROID_ROOT / path).read_text()


def test_jellyfin_browse_loads_movies_and_series_and_opens_detail_screen():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "repo.loadMovies()" in source
    assert "repo.loadSeries()" in source
    assert "JellyfinDetailsFragment.newInstance(item)" in source
    assert "JELLYFIN_DETAILS" in source
    assert "navigateToPlayer(request)" not in source


def test_jellyfin_card_shows_title_year_and_type_tag():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "item.title" in source
    assert "productionYear" in source
    assert "[S]" in source
    assert "isFolder" in source


def test_jellyfin_detail_is_full_screen_with_description_and_play_button():
    source = read_android("jellyfin/JellyfinDetailsFragment.kt")

    assert "fun newInstance(item: JellyfinItem): JellyfinDetailsFragment" in source
    assert "ScrollView(requireContext())" in source
    assert "item.overview?.takeIf" in source
    assert 'text = "Play"' in source
    assert "playJellyfinItem(item)" in source
    assert "jellyfinRepository.buildHlsStream(item.id, item.title)" in source


def test_vod_browse_opens_detail_screen_and_shows_title_year():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "VodDetailsFragment.newInstance(item)" in source
    assert "navigateToPlayer(request)" not in source
    assert "item.title" in source
    assert "releaseYear" in source


def test_vod_detail_is_full_screen_with_description_and_play_button():
    source = read_android("vod/VodDetailsFragment.kt")

    assert "fun newInstance(item: VodItem): VodDetailsFragment" in source
    assert "ScrollView(requireContext())" in source
    assert "item.description?.takeIf" in source
    assert 'text = "Play"' in source
    assert "playVodItem(item)" in source
    assert "navigator?.navigateToPlayer(request)" in source
