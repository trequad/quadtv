from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vod_browse_loads_categories_and_recently_added_from_repository():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "private val vodRepository: VodRepository by lazy { buildVodRepository() }" in source
    assert "adapter = buildLoadingRows()" in source
    assert "configureClickHandling()" in source
    assert "loadVodFromRepository()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "vodRepository.loadRecentlyAdded()" in source
    assert "vodRepository.loadCategories()" in source
    assert "buildVodRows(recentlyAdded, categories)" in source
    assert "buildErrorRows(\"Unable to load On-Demand\"" in source
    assert "Browse categories" not in source


def test_vod_browse_has_typed_actions_loading_empty_error_and_player_handoff():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "sealed class VodAction" in source
    assert "data class PlayItem(" in source
    assert "data class Category(" in source
    assert "data class Message(" in source
    assert "buildLoadingRows()" in source
    assert "buildEmptyRows()" in source
    assert "buildErrorRows(label: String, description: String)" in source
    assert "VodDetailsFragment().buildPlaybackRequest(action.item)" in source
    assert "navigateToPlayer(request)" in source
    assert "On-Demand Categories" in source
    assert "Recently Added VOD" in source
    assert "Toast.makeText" not in source
    assert "Intent(" not in source


def test_vod_repository_backed_ui_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "VOD browse now loads recently added titles and categories from VodRepository" in readme
    assert "### Task 9.12: VOD repository-backed browse rows" in plan
    assert "test_android_vod_repository_ui.py" in plan
