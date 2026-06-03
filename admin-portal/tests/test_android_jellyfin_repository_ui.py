from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_jellyfin_browse_loads_movies_and_series_from_repository():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }" in source
    assert "loadNav()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "repo.loadMovies()" in source
    assert "repo.loadSeries()" in source
    assert "JellyfinDetailsFragment.newInstance(item)" in source
    assert "JELLYFIN_DETAILS" in source
    assert "Movies" in source
    assert "TV Shows" in source


def test_jellyfin_browse_is_two_pane_fragment_with_section_nav():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "class JellyfinBrowseFragment : Fragment()" in source
    assert "sectionContainer" in source
    assert "contentContainer" in source
    assert "ScrollView" in source
    assert "addSectionButton" in source
    assert "setOnFocusChangeListener" in source
    assert "BrowseSupportFragment" not in source


def test_jellyfin_browse_shows_error_message_not_crash():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "Can't load Jellyfin right now" in source
    assert "showContentMessage" in source


def test_jellyfin_repository_backed_ui_docs_and_install_plan_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Jellyfin browse now loads libraries and items from JellyfinRepository" in readme
    assert "### Task 9.16: Jellyfin repository-backed browse rows" in plan
    assert "test_android_jellyfin_repository_ui.py" in plan
    assert "### Task 9.17: Jellyfin server install and arr-stack integration plan" in plan
    assert "Frigg" in plan
    assert "Radarr/Sonarr" in plan
    assert "Jellyfin for QuadTV is running on Frigg" in plan
    assert "Android APK install/smoke test on the TV still requires Tre approval" in plan
