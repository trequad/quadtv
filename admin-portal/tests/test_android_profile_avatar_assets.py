import struct
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_APP = PROJECT_ROOT / "android-app/app"
ANDROID_SRC = ANDROID_APP / "src/main/java/net/trequad/quadtv"
DRAWABLE_NODPI = ANDROID_APP / "src/main/res/drawable-nodpi"


def png_dimensions(path):
    data = path.read_bytes()
    assert data.startswith(b"\x89PNG\r\n\x1a\n")
    return struct.unpack(">II", data[16:24])


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_profile_avatar_assets_are_sanitized_android_resources():
    expected = [
        "profile_avatar_bear.png",
        "profile_avatar_dragon.png",
        "profile_avatar_fox.png",
        "profile_avatar_lightning.png",
        "profile_avatar_raven.png",
        "profile_avatar_robot.png",
        "profile_avatar_star.png",
        "profile_avatar_wolf.png",
    ]

    for filename in expected:
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def test_brand_background_assets_are_sanitized_android_resources():
    expected = [
        "quadtv_account_profiles_background.png",
        "quadtv_neon_waves_background.png",
        "quadtv_system_settings_background.png",
    ]

    for filename in expected:
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def test_profile_picker_renders_avatar_images_and_creates_known_avatar_values():
    picker = read_android("profiles/ProfilePickerFragment.kt")

    assert "ImageView" in picker
    assert "resolveAvatarResource" in picker
    assert "profile_avatar_raven" in picker
    assert "profile_avatar_bear" in picker
    assert "profile_avatar_wolf" in picker
    assert "R.drawable.profile_avatar_raven" in picker
    assert "setImageResource(resolveAvatarResource(profile.avatar))" in picker
    assert "nextAvatarForNewProfile" in picker
    assert "ProfileCreateRequest(displayName = name, avatar = avatar)" in picker


def test_rating_badge_assets_are_sanitized_android_resources():
    expected = [
        "rating_badge_g.png",
        "rating_badge_nc_17.png",
        "rating_badge_pg.png",
        "rating_badge_pg_13.png",
        "rating_badge_r.png",
        "rating_badge_tv_14.png",
        "rating_badge_tv_g.png",
        "rating_badge_tv_ma.png",
        "rating_badge_tv_pg.png",
        "rating_badge_tv_y.png",
        "rating_badge_tv_y7.png",
    ]

    for filename in expected:
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def test_genre_icon_assets_are_sanitized_android_resources():
    expected = [
        "genre_icon_action.png",
        "genre_icon_comedy.png",
        "genre_icon_documentary.png",
        "genre_icon_drama.png",
        "genre_icon_horror.png",
        "genre_icon_kids.png",
        "genre_icon_music.png",
        "genre_icon_news.png",
        "genre_icon_scifi.png",
        "genre_icon_sports.png",
    ]

    for filename in expected:
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def test_quick_access_icon_assets_are_sanitized_android_resources():
    expected = [
        "media_favorite_badge.png",
        "quick_access_icon_favorites.png",
        "quick_access_icon_jellyfin.png",
        "quick_access_icon_live_tv.png",
        "quick_access_icon_recently_viewed.png",
        "quick_access_icon_refresh.png",
        "quick_access_icon_search.png",
        "quick_access_icon_settings.png",
        "quick_access_icon_vod.png",
        "recently_viewed_badge.png",
    ]

    for filename in expected:
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n")


def test_announcement_banner_and_media_placeholder_assets_are_sanitized_android_resources():
    expected_sizes = {
        "announcement_banner_maintenance.png": (1280, 720),
        "announcement_banner_service_alert.png": (1280, 720),
        "announcement_banner_subscription_notice.png": (1280, 720),
        "channel_logo_placeholder.png": (512, 288),
        "vod_poster_placeholder.png": (500, 750),
    }

    for filename, dimensions in expected_sizes.items():
        path = DRAWABLE_NODPI / filename
        assert path.exists(), filename
        assert ".png.png" not in filename
        assert "-" not in filename
        assert png_dimensions(path) == dimensions


def test_neon_background_is_wired_to_all_primary_android_tv_pages():
    pages = [
        "auth/CustomerLoginFragment.kt",
        "auth/ExpiredSubscriptionFragment.kt",
        "auth/SubscriptionRequiredFragment.kt",
        "epg/EpgGridFragment.kt",
        "favorites/FavoritesFragment.kt",
        "favorites/RecentlyViewedFragment.kt",
        "home/HomeFragment.kt",
        "jellyfin/JellyfinBrowseFragment.kt",
        "jellyfin/JellyfinDetailsFragment.kt",
        "live/LiveTvFragment.kt",
        "onboarding/OnboardingFragment.kt",
        "profiles/ProfilePickerFragment.kt",
        "search/MovieSearchFragment.kt",
        "seerr/SeerrFragment.kt",
        "settings/SettingsFragment.kt",
        "vod/VodBrowseFragment.kt",
        "vod/VodDetailsFragment.kt",
    ]

    for page in pages:
        source = read_android(page)
        assert "quadtv_neon_waves_background" in source, page
