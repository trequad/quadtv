"""Jellyfin-first home, entitlement gating, and responsive foundation (Phase 3+4).

Locks the contracts from docs/DESIGN_SYSTEM.md §2–3:
- QuadOnDemand (Jellyfin) leads navigation and Home rows.
- Rail items are hidden when the customer's package does not include them.
- Continue Watching uses per-user Jellyfin resume points.
- Phone portrait gets a bottom navigation bar; orientation is unlocked.
- Jellyfin access comes from the authenticated portal endpoint, not launch config.
"""

from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read(path: str) -> str:
    return (ANDROID_SRC / path).read_text()


def test_home_rail_leads_with_quadondemand_and_gates_by_entitlement():
    home = read("home/HomeFragment.kt")

    quad = home.index('HomeAction("QuadOnDemand", QuadTvRoute.JELLYFIN)')
    live = home.index('HomeAction("Live TV", QuadTvRoute.LIVE_TV)')
    vod = home.index('HomeAction("VOD", QuadTvRoute.VOD)')
    assert quad < live < vod

    for flag in ("canAccessQuaddemand", "canAccessLiveTv", "canAccessVod", "canAccessSeerr"):
        assert flag in home

    # Gated adds: non-entitled items are hidden, not disabled.
    assert 'if (canQuad) add(HomeAction("QuadOnDemand"' in home
    assert 'if (canLive) add(HomeAction("Live TV"' in home
    assert 'if (canVod) add(HomeAction("VOD"' in home
    assert 'if (canSeerr) add(HomeAction("Requests"' in home


def test_home_rows_are_jellyfin_first_with_continue_watching():
    home = read("home/HomeFragment.kt")

    continue_watching = home.index('"Continue Watching"')
    recently_added = home.index('"Recently Added on QuadOnDemand"')
    live_now = home.index('"Live Now"')
    assert continue_watching < recently_added < live_now

    assert "loadContinueWatching" in home
    assert "Request movies & shows" in home
    assert "Approved requests appear in QuadOnDemand." in home


def test_jellyfin_repository_supports_resume_and_recently_added():
    repo = read("jellyfin/JellyfinRepository.kt")

    assert "Items/Resume" in repo
    assert "SortBy=DateCreated&SortOrder=Descending" in repo
    assert "accessProvider" in repo
    # Authenticated portal access is preferred over unauthenticated launch config.
    assert repo.index("accessProvider?.loadAccess()") < repo.index("launchConfig.jellyfinApiKey")


def test_jellyfin_access_and_seerr_sessions_come_from_portal():
    provider = read("jellyfin/JellyfinAccessProvider.kt")
    services = read("core/AppServices.kt")
    api = read("adminapi/AdminApiService.kt")

    assert "getJellyfinAccess" in provider
    assert "CustomerSessionCache" in provider
    assert 'GET("api/v1/jellyfin/access")' in api
    assert 'POST("api/v1/seerr/session")' in api
    assert "fun jellyfinRepository" in services
    assert "jellyfinAccessProvider(context)" in services


def test_phone_portrait_bottom_navigation_and_unlocked_orientation():
    home = read("home/HomeFragment.kt")
    manifest = (PROJECT_ROOT / "android-app/app/src/main/AndroidManifest.xml").read_text()

    assert "isPhonePortrait" in home
    assert "createPhonePortraitLayout" in home
    assert "smallestScreenWidthDp < 600" in home
    assert 'android:screenOrientation="fullUser"' in manifest
    assert 'android:screenOrientation="landscape"' not in manifest


def test_design_tokens_exist():
    theme = read("core/ui/QuadTvTheme.kt")

    assert "object QuadTvTheme" in theme
    assert "FOCUS" in theme
    assert "ACCENT_WARM" in theme
    assert "TYPE_BODY" in theme
    assert "MIN_FOCUSABLE_SIZE" in theme
