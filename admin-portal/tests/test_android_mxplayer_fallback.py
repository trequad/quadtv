from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
MANIFEST = PROJECT_ROOT / "android-app/app/src/main/AndroidManifest.xml"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_and_failure_handler_do_not_include_mx_player_external_fallback():
    contract = read_android("player/PlayerContract.kt")
    handler = read_android("player/PlaybackFailureHandler.kt")

    assert "VLC" in contract
    assert "MX_PLAYER" not in contract
    assert "EXOPLAYER" not in contract
    assert "fun alternateEngine(failedEngine: PlayerEngine): PlayerEngine? = null" in handler
    assert "MX_PLAYER" not in handler


def test_player_fragment_does_not_launch_external_video_apps():
    source = read_android("player/PlayerFragment.kt")

    assert "VlcPlayerController(requireContext())" in source
    assert "launchMxPlayer" not in source
    assert "Intent(Intent.ACTION_VIEW)" not in source
    assert "setDataAndType" not in source
    assert "com.mxtech.videoplayer" not in source
    assert "Open in MX Player" not in source
    assert "Intent.createChooser" not in source


def test_manifest_does_not_declare_mx_player_package_visibility():
    manifest = MANIFEST.read_text()

    assert '<queries>' not in manifest
    assert 'android:name="com.mxtech.videoplayer.ad"' not in manifest
    assert 'android:name="com.mxtech.videoplayer.pro"' not in manifest
    assert 'android:mimeType="video/*"' not in manifest
