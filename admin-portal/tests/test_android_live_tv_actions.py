from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_live_tv_actions_carry_guide_route_and_channel_payloads():
    source = read_android("live/LiveTvFragment.kt")

    assert "QuadTvNavigator" in source
    assert "QuadTvRoute.EPG" in source
    assert "sideButton(\"Open Guide\"" in source
    assert "navigateTo(QuadTvRoute.EPG)" in source
    assert "sealed class LiveTvAction" in source
    assert "data object OpenGuide" in source
    assert "data class Channel" in source
    assert "LiveChannel" in source
    assert "channelButton(channel," in source


def test_live_tv_playback_coordinator_builds_live_stream_requests():
    source = read_android("live/LiveTvPlaybackCoordinator.kt")

    assert "class LiveTvPlaybackCoordinator" in source
    assert "fun buildRequest(channel: LiveChannel): StreamPlaybackRequest" in source
    assert "StreamPlaybackRequest(" in source
    assert "url = channel.streamUrl" in source
    assert "title = channel.name" in source
    assert "isLive = true" in source
    assert "fun describeFallback(channel: LiveChannel)" in source
    assert "embedded VLC" in source


def test_live_tv_action_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Live TV action handoff source files" in readme
    assert "LiveTvPlaybackCoordinator.kt" in readme
    assert "Open Guide action routes to the EPG" in readme
    assert "### Task 9.5: Android Live TV action handoff" in plan
    assert "test_android_live_tv_actions.py" in plan
