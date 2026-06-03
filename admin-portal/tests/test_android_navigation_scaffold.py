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
    for route in ["LOGIN", "HOME", "PROFILES", "LIVE_TV", "EPG", "MOVIE_SEARCH", "VOD", "JELLYFIN", "SETTINGS"]:
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
    assert "QuadTvRoute.MOVIE_SEARCH -> MovieSearchFragment()" in source
    assert "QuadTvRoute.VOD -> VodBrowseFragment()" in source
    assert "QuadTvRoute.JELLYFIN -> JellyfinBrowseFragment()" in source
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
    for label in ["Live TV", "Guide", "Movie Search", "Refresh Playlist & Guide", "Settings"]:
        assert label in source
    for route in ["QuadTvRoute.LIVE_TV", "QuadTvRoute.EPG", "QuadTvRoute.MOVIE_SEARCH", "QuadTvRoute.SETTINGS"]:
        assert route in source
    assert "Recently Added VOD" not in source


def test_live_tv_fragment_exists_as_navigation_target():
    source = read_android("live/LiveTvFragment.kt")

    assert "class LiveTvFragment : Fragment()" in source
    assert "groupContainer" in source
    assert "channelContainer" in source
    assert "Open Guide" in source
    assert "No information" in source
    assert "currentProgramme?.title" in source


def test_readme_and_plan_document_navigation_scaffold():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Navigation foundation source files" in readme
    assert "QuadTvNavigator.kt" in readme
    assert "Home action cards route to Live TV, Guide, On-Demand, Jellyfin, and Settings" in readme
    assert "### Task 9.4: Android home navigation scaffold" in plan
    assert "test_android_navigation_scaffold.py" in plan
    assert "Android Gradle verification passes" in plan
