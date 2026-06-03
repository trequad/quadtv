from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vlc_controller_logs_surface_and_playback_events_for_black_screen_debugging():
    source = read_android("player/VlcPlayerController.kt")

    assert "import android.util.Log" in source
    assert "private const val TAG = \"QuadTV/VLC\"" in source
    assert "--verbose=2" in source
    assert "Log.i(TAG, \"vlc video layout" in source
    assert "Log.i(TAG, \"play url=" in source
    assert "eventName(event.type)" in source
    assert "notifyStatus" in source
    assert "url.redactPlaybackUrl()" in source
    assert "NOISY_PROGRESS_EVENTS" in source
    assert "MediaPlayer.Event.TimeChanged" in source
    assert "event.type !in NOISY_PROGRESS_EVENTS" in source


def test_player_fragment_surfaces_vlc_diagnostics_on_screen_temporarily():
    source = read_android("player/PlayerFragment.kt")

    assert "setPlaybackStatusListener" in source
    assert "showPlaybackDiagnostic" in source
    assert "diagnosticStatusHideHandler" in source
    assert "VLC:" in source
    assert "DIAGNOSTIC_STATUS_AUTO_HIDE_MS" in source


def test_player_contract_has_optional_status_listener_without_external_players():
    source = read_android("player/PlayerContract.kt")

    assert "setPlaybackStatusListener" in source
    assert "MX_PLAYER" not in source
