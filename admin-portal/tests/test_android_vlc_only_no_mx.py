from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
MANIFEST = PROJECT_ROOT / "android-app/app/src/main/AndroidManifest.xml"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_engine_is_vlc_only_with_no_external_mx_engine():
    contract = read_android("player/PlayerContract.kt")
    handler = read_android("player/PlaybackFailureHandler.kt")

    assert "enum class PlayerEngine" in contract
    assert "VLC" in contract
    assert "MX_PLAYER" not in contract
    assert "EXOPLAYER" not in contract
    assert "fun alternateEngine(failedEngine: PlayerEngine): PlayerEngine? = null" in handler
    assert "shouldOfferRetryWithAlternate" not in handler


def test_player_fragment_never_launches_external_video_apps():
    source = read_android("player/PlayerFragment.kt")

    assert "VlcPlayerController(requireContext())" in source
    assert "PlayerEngine.MX_PLAYER" not in source
    assert "launchMxPlayer" not in source
    assert "Intent(Intent.ACTION_VIEW)" not in source
    assert "Intent.createChooser" not in source
    assert "ActivityNotFoundException" not in source
    assert "Open in MX Player" not in source
    assert "Retry with alternate player" not in source


def test_manifest_removes_mx_player_package_visibility():
    manifest = MANIFEST.read_text()

    assert "com.mxtech.videoplayer" not in manifest
    assert "android.intent.action.VIEW" not in manifest
    assert "video/*" not in manifest
    assert "<queries>" not in manifest


def test_settings_copy_is_vlc_only():
    source = read_android("settings/SettingsModels.kt")

    assert "VLC" in source
    assert "MX Player" not in source
    assert "fallback" not in source.lower()
    assert "Embedded VLC" in source
