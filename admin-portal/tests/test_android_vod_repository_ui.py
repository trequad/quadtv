from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vod_browse_loads_movies_and_series_from_repository():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "private val vodRepository: VodRepository by lazy { buildVodRepository() }" in source
    assert "loadNav()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "repo.loadRecentlyAdded()" in source
    assert "repo.loadSeries()" in source
    assert "VodDetailsFragment.newInstance(item)" in source
    assert "Recently Added" in source
    assert "TV Series" in source
    assert "Browse categories" not in source


def test_vod_browse_is_two_pane_fragment_with_category_nav():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "class VodBrowseFragment : Fragment()" in source
    assert "categoryContainer" in source
    assert "contentContainer" in source
    assert "ScrollView" in source
    assert "addCategoryButton" in source
    assert "selectSection" in source
    assert "setOnFocusChangeListener" in source
    assert "BrowseSupportFragment" not in source


def test_vod_browse_shows_error_message_not_crash():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "Can't load On-Demand right now" in source
    assert "showContentMessage" in source


def test_vod_repository_backed_ui_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "VOD browse now loads recently added titles and categories from VodRepository" in readme
    assert "### Task 9.12: VOD repository-backed browse rows" in plan
    assert "test_android_vod_repository_ui.py" in plan
