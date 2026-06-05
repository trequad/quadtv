from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = ROOT / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"
ANDROID_RES = ROOT / "android-app" / "app" / "src" / "main" / "res"


def read_android(relative_path: str) -> str:
    return (ANDROID_SRC / relative_path).read_text()


def test_android_has_header_logo_asset_for_text_only_brand_replacement():
    assert (ANDROID_RES / "drawable-nodpi" / "quadtv_logo_horizontal.png").exists()


def test_home_left_rail_uses_logo_image_not_text_only_brand_stack():
    source = read_android("home/HomeFragment.kt")

    assert "R.drawable.quadtv_logo_horizontal" in source
    assert "ImageView(context).apply" in source
    assert "contentDescription = \"QuadTV by QuadMedia\"" in source
    assert "QuadTvConfig.APP_NAME + \"\\n\" + QuadTvConfig.PARENT_BRAND" not in source


def test_gate_and_request_headers_use_logo_asset_before_copy():
    for relative_path in [
        "auth/CustomerLoginFragment.kt",
        "auth/ExpiredSubscriptionFragment.kt",
        "auth/SubscriptionRequiredFragment.kt",
        "seerr/SeerrFragment.kt",
    ]:
        source = read_android(relative_path)
        assert "R.drawable.quadtv_logo_horizontal" in source, relative_path
        assert "contentDescription = \"QuadTV by QuadMedia\"" in source, relative_path

    assert 'text = "QuadTV Login"' not in read_android("auth/CustomerLoginFragment.kt")
    assert 'text = "QuadMedia Request"' not in read_android("seerr/SeerrFragment.kt")


def test_page_titles_do_not_duplicate_text_only_branding():
    title_sources = {
        "profiles/ProfilePickerFragment.kt": 'title = "Profiles"',
        "favorites/FavoritesFragment.kt": 'title = "Favorites"',
        "favorites/RecentlyViewedFragment.kt": 'title = "Recently Viewed"',
        "settings/SettingsFragment.kt": 'title = "Settings"',
        "epg/EpgGridFragment.kt": 'text = "Guide"',
        "vod/VodDetailsFragment.kt": 'text = "Details"',
    }

    for relative_path, expected in title_sources.items():
        source = read_android(relative_path)
        assert expected in source, relative_path

    forbidden_title_copy = [
        'title = "QuadTV Profiles"',
        'title = "QuadTV Favorites"',
        'title = "QuadTV Recently Viewed"',
        'title = "QuadTV Settings • QuadMedia"',
        'text = "QuadTV Guide"',
        'text = "QuadTV Details"',
    ]
    all_sources = "\n".join(read_android(path) for path in title_sources)
    for forbidden in forbidden_title_copy:
        assert forbidden not in all_sources
