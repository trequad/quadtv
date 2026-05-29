from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_tv_fragment_loads_channels_from_repository_on_launch():
    source = read_android("live/LiveTvFragment.kt")

    assert "LiveTvRepository" in source
    assert "loadChannelsFromRepository()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "liveTvRepository.loadChannels()" in source
    assert "buildLoadingRows()" in source
    assert "buildErrorRows" in source
    assert "No channels available" in source


def test_live_tv_fragment_groups_real_channels_without_preview_placeholder():
    source = read_android("live/LiveTvFragment.kt")

    assert "buildLiveRows(channels: List<LiveChannel>)" in source
    assert "channels.groupBy" in source
    assert "groupTitle ?: \"Other Channels\"" in source
    assert "groups.toSortedMap()" in source
    assert "LiveTvAction.Channel(channel)" in source
    assert "Preview Channel" not in source
    assert "example.invalid" not in source


def test_live_tv_repository_ui_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Live TV repository-backed UI source files" in readme
    assert "LiveTvRepository.loadChannels()" in readme
    assert "grouped channel rows" in readme
    assert "### Task 9.6: Android Live TV repository-backed rows" in plan
    assert "test_android_live_tv_repository_ui.py" in plan
