from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_tv_fragment_loads_channels_and_current_programmes_from_repositories_on_launch():
    source = read_android("live/LiveTvFragment.kt")

    assert "LiveTvRepository" in source
    assert "EpgRepository" in source
    assert "loadChannelsFromRepository()" in source
    assert "lifecycleScope.launch" in source
    assert "withContext(Dispatchers.IO)" in source
    assert "liveRepo.loadChannels()" in source
    assert "epgRepo.loadProgrammes()" in source
    assert "renderGroupsAndChannels" in source
    assert "loadCurrentProgrammesIntoRows(channels," in source
    assert "showLoadingState()" in source
    assert "showErrorState" in source
    assert "Can't load channels right now" in source


def test_live_tv_fragment_uses_left_group_list_and_right_vertical_channels_without_scaffold_copy():
    source = read_android("live/LiveTvFragment.kt")

    assert "class LiveTvFragment : Fragment()" in source
    assert "groupContainer = LinearLayout" in source
    assert "channelContainer = LinearLayout" in source
    assert "channels.groupBy" in source
    assert "sideButton(group" in source
    assert "channelButton(channel," in source
    assert "currentProgrammes[channel.id]" in source
    assert "currentProgramme?.title ?: \"No information\"" in source
    assert "Prepare HLS playback" not in source
    assert "Info banner" not in source
    assert "Browse channel groups" not in source
    assert "Preview Channel" not in source
    assert "example.invalid" not in source


def test_live_tv_repository_ui_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Live TV repository-backed UI source files" in readme
    assert "LiveTvRepository.loadChannels()" in readme
    assert "horizontal channel row" in readme
    assert "### Task 9.6: Android Live TV repository-backed rows" in plan
    assert "test_android_live_tv_repository_ui.py" in plan
