from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_epg_grid_loads_programmes_from_repository_on_launch():
    source = read_android("epg/EpgGridFragment.kt")

    assert "BrowseSupportFragment" in source
    assert "private val epgRepository: EpgRepository by lazy { buildEpgRepository() }" in source
    assert "adapter = buildLoadingRows()" in source
    assert "loadProgrammesFromRepository()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "epgRepository.loadProgrammes()" in source
    assert "buildGuideRows(programmes)" in source
    assert "buildEmptyRows()" in source
    assert "buildErrorRows(\"Unable to load Guide\"" in source


def test_epg_grid_renders_channel_rows_typed_programme_actions_and_states():
    source = read_android("epg/EpgGridFragment.kt")

    assert "sealed class EpgAction" in source
    assert "data class Programme(" in source
    assert "data class Message(" in source
    assert "programmes.groupBy { it.channelId }" in source
    assert "epgRepository.programmesForChannel" in source
    assert "HeaderItem(channelId.hashCode().toLong()" in source
    assert "EpgAction.Programme(programme)" in source
    assert "Loading Guide" in source
    assert "No guide data available" in source
    assert "current / next programme details" in source
    assert "time axis" in source.lower()
    assert "preview panel" in source.lower()


def test_epg_repository_backed_ui_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "EPG guide now loads XMLTV programmes from EpgRepository" in readme
    assert "repository-backed guide rows" in readme
    assert "### Task 9.15: Android EPG repository-backed guide rows" in plan
    assert "test_android_epg_repository_ui.py" in plan
