from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_route_is_part_of_navigation_contract_and_main_activity():
    nav = read_android("navigation/QuadTvNavigator.kt")
    main = read_android("MainActivity.kt")

    assert "PLAYER" in nav
    assert "fun navigateToPlayer(request: StreamPlaybackRequest)" in nav
    assert "import net.trequad.quadtv.player.StreamPlaybackRequest" in nav
    assert "PlayerFragment.newInstance(request)" in main
    assert "QuadTvRoute.PLAYER" in main
    assert "PlayerFragment" in main


def test_live_tv_channel_click_navigates_to_in_app_player_screen():
    source = read_android("live/LiveTvFragment.kt")

    assert "playbackCoordinator.buildRequest(action.channel)" in source
    assert "navigateToPlayer(request)" in source
    assert "Prepared ${request.title} for bundled live playback" not in source
    assert "Toast.makeText" not in source
    assert "Intent(" not in source


def test_player_fragment_loads_request_uses_selected_engine_and_no_external_intents():
    source = read_android("player/PlayerFragment.kt")

    assert "class PlayerFragment" in source
    assert "newInstance(request: StreamPlaybackRequest)" in source
    assert "ARG_URL" in source
    assert "ARG_TITLE" in source
    assert "ARG_IS_LIVE" in source
    assert "PlayerSettingsCache" in source
    assert "settings.defaultEngine" in source
    assert "ExoPlayerController" in source
    assert "VlcPlayerController" in source
    assert "PlaybackFailureHandler" in source
    assert "retry with ${alternate.name}" in source
    assert "QuadMedia" in source
    assert "Intent(" not in source


def test_player_route_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "In-app player route source files" in readme
    assert "PlayerFragment.kt" in readme
    assert "Live TV channel selection now navigates to the bundled player route" in readme
    assert "### Task 9.8: Android in-app player route" in plan
    assert "test_android_player_route.py" in plan
