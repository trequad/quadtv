from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vod_details_navigates_playable_items_to_bundled_player_route():
    source = read_android("vod/VodDetailsFragment.kt")

    assert "import net.trequad.quadtv.navigation.QuadTvNavigator" in source
    assert "private val navigator: QuadTvNavigator?" in source
    assert "fun playVodItem(item: VodItem): Boolean" in source
    assert "val request = buildPlaybackRequest(item) ?: return false" in source
    assert "navigator?.navigateToPlayer(request)" in source
    assert "subtitle = \"On-Demand\"" in source
    assert "nextTitle = item.rating ?: item.releaseYear?.toString() ?: \"QuadTV VOD\"" in source
    assert "Intent(" not in source


def test_jellyfin_details_navigates_hls_streams_to_bundled_player_route():
    source = read_android("jellyfin/JellyfinDetailsFragment.kt")

    assert "import net.trequad.quadtv.navigation.QuadTvNavigator" in source
    assert "private val navigator: QuadTvNavigator?" in source
    assert "fun playJellyfinStream(stream: JellyfinStream): Boolean" in source
    assert "val request = buildPlaybackRequest(stream)" in source
    assert "navigator?.navigateToPlayer(request)" in source
    assert "subtitle = \"QuadOnDemand\"" in source
    assert "nextTitle = \"QuadMedia library\"" in source
    assert "Intent(" not in source


def test_vod_jellyfin_playback_route_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "VOD and Jellyfin details now hand playable streams to the bundled player route" in readme
    assert "### Task 9.11: VOD and Jellyfin player handoff" in plan
    assert "test_android_vod_jellyfin_playback_route.py" in plan
