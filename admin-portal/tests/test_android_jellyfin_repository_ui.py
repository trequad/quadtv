from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_jellyfin_browse_loads_libraries_and_items_from_repository():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "private val jellyfinRepository: JellyfinRepository by lazy { buildJellyfinRepository() }" in source
    assert "adapter = buildLoadingRows()" in source
    assert "configureClickHandling()" in source
    assert "loadJellyfinFromRepository()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "jellyfinRepository.loadLibraries()" in source
    assert "jellyfinRepository.loadItems(library.id)" in source
    assert "buildJellyfinRows(libraryRows)" in source
    assert "buildErrorRows(\"Unable to load Jellyfin\"" in source
    assert "Browse configured Jellyfin libraries" not in source
    assert "id = \"placeholder\"" not in source


def test_jellyfin_browse_has_typed_actions_loading_empty_error_and_player_handoff():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "sealed class JellyfinAction" in source
    assert "data class PlayItem(" in source
    assert "data class Library(" in source
    assert "data class Message(" in source
    assert "data class JellyfinLibraryRow(" in source
    assert "buildLoadingRows()" in source
    assert "buildEmptyRows()" in source
    assert "buildErrorRows(label: String, description: String)" in source
    assert "jellyfinRepository.buildHlsStream(action.item.id)" in source
    assert "JellyfinDetailsFragment().buildPlaybackRequest(stream)" in source
    assert "navigateToPlayer(request)" in source
    assert "Jellyfin Libraries" in source
    assert "Recently Added from Jellyfin" in source
    assert "Toast.makeText" not in source
    assert "Intent(" not in source


def test_jellyfin_repository_backed_ui_docs_and_install_plan_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Jellyfin browse now loads libraries and items from JellyfinRepository" in readme
    assert "### Task 9.16: Jellyfin repository-backed browse rows" in plan
    assert "test_android_jellyfin_repository_ui.py" in plan
    assert "### Task 9.17: Jellyfin server install and arr-stack integration plan" in plan
    assert "Frigg" in plan
    assert "Radarr/Sonarr" in plan
    assert "no infrastructure changes have been executed" in plan
