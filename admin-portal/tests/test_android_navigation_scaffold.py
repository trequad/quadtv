from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_navigation_contract_lists_major_tv_destinations():
    source = read_android("navigation/QuadTvNavigator.kt")

    assert "enum class QuadTvRoute" in source
    for route in ["LOGIN", "HOME", "PROFILES", "LIVE_TV", "EPG", "VOD", "JELLYFIN", "SETTINGS"]:
        assert route in source
    assert "interface QuadTvNavigator" in source
    assert "fun navigateTo(route: QuadTvRoute)" in source
    assert "fun goBack()" in source


def test_main_activity_maps_routes_to_feature_fragments():
    source = read_android("MainActivity.kt")

    assert "QuadTvNavigator" in source
    assert "override fun navigateTo(route: QuadTvRoute)" in source
    assert "CustomerLoginFragment()" in source
    assert "HomeFragment()" in source
    assert "ProfilePickerFragment()" in source
    assert "LiveTvFragment()" in source
    assert "EpgGridFragment()" in source
    assert "VodBrowseFragment()" in source
    assert "JellyfinBrowseFragment()" in source
    assert "SettingsFragment()" in source
    assert "addToBackStack(route.name)" in source
    assert "supportFragmentManager.popBackStack()" in source


def test_home_fragment_renders_action_cards_and_invokes_navigator():
    source = read_android("home/HomeFragment.kt")

    assert "data class HomeAction" in source
    assert "QuadTvRoute" in source
    assert "QuadTvNavigator" in source
    assert "setOnItemViewClickedListener" in source
    assert "navigateTo(route)" in source
    for label in ["Live TV", "Guide", "On-Demand", "Jellyfin", "Settings"]:
        assert label in source
    for route in ["QuadTvRoute.LIVE_TV", "QuadTvRoute.EPG", "QuadTvRoute.VOD", "QuadTvRoute.JELLYFIN", "QuadTvRoute.SETTINGS"]:
        assert route in source
    assert "Continue Watching" in source
    assert "Recently Added VOD" in source
    assert "Announcements" in source


def test_live_tv_fragment_exists_as_navigation_target():
    source = read_android("live/LiveTvFragment.kt")

    assert "class LiveTvFragment" in source
    assert "BrowseSupportFragment" in source
    assert "QuadTV Live TV" in source
    assert "channel groups" in source
    assert "Open Guide" in source
    assert "Info banner" in source
    assert "bundled player" in source


def test_readme_and_plan_document_navigation_scaffold():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Navigation foundation source files" in readme
    assert "QuadTvNavigator.kt" in readme
    assert "Home action cards route to Live TV, Guide, On-Demand, Jellyfin, and Settings" in readme
    assert "### Task 9.4: Android home navigation scaffold" in plan
    assert "test_android_navigation_scaffold.py" in plan
    assert "Android Gradle verification passes" in plan
