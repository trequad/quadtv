from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_supports_vlc_render_surface_attachment_without_media3_view():
    source = read_android("player/PlayerContract.kt")

    assert "class PlayerRenderSurface" in source
    assert "val vlcVideoLayout: VLCVideoLayout" in source
    assert "PlayerView" not in source
    assert "fun attachSurface(surface: PlayerRenderSurface)" in source


def test_vlc_controller_waits_for_surface_lifecycle_and_detaches_on_release():
    source = read_android("player/VlcPlayerController.kt")

    assert "org.videolan.libvlc.util.VLCVideoLayout" in source
    assert "android.view.View" in source
    assert "override fun attachSurface(surface: PlayerRenderSurface)" in source
    assert "View.OnAttachStateChangeListener" in source
    assert "onViewAttachedToWindow" in source
    assert "attachViewsIfReady()" in source
    assert "mediaPlayer.attachViews(attachedVideoLayout, null, false, false)" in source
    assert "mediaPlayer.detachViews()" in source
    assert "attachedVideoLayout?.removeOnAttachStateChangeListener" in source
    assert "Intent(" not in source


def test_player_fragment_uses_vlc_surface_without_external_player_retry():
    source = read_android("player/PlayerFragment.kt")

    assert "FrameLayout" in source
    assert "PlayerRenderSurface" in source
    assert "VLCVideoLayout(requireContext())" in source
    assert "PlayerView(requireContext())" not in source
    assert "activePlayer?.attachSurface(renderSurface)" in source
    assert "retryButton" in source
    assert "Playback failed with embedded VLC" in source
    assert "Playback failed with embedded VLC" in source
    assert "Bring up the info banner" in source
    assert "launchMxPlayer(playableRequest)" not in source
    assert "Intent(Intent.ACTION_VIEW)" not in source
    assert "MX_PLAYER" not in source


def test_player_surface_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Visual player surface source files" in readme
    assert "VLC Surface" in readme
    assert "### Task 9.9: Android visual player surface and retry" in plan
    assert "test_android_player_surface.py" in plan
