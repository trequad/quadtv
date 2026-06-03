from pathlib import Path

ANDROID_ROOT = Path(__file__).resolve().parents[1] / ".." / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"


def test_jellyfin_rows_show_title_year_and_type_with_description_on_detail():
    browse = (ANDROID_ROOT / "jellyfin" / "JellyfinBrowseFragment.kt").read_text()
    details = (ANDROID_ROOT / "jellyfin" / "JellyfinDetailsFragment.kt").read_text()

    assert "item.title" in browse
    assert "productionYear" in browse
    assert "contentRating" in browse
    assert "item.overview?.takeIf" in details


def test_vod_rows_show_title_year_and_type_with_description_on_detail():
    browse = (ANDROID_ROOT / "vod" / "VodBrowseFragment.kt").read_text()
    details = (ANDROID_ROOT / "vod" / "VodDetailsFragment.kt").read_text()

    assert "item.title" in browse
    assert "releaseYear" in browse
    assert "item.rating" in browse
    assert "item.description?.takeIf" in details
