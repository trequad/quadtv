from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_supports_bundled_render_surface_attachment():
    source = read_android("player/PlayerContract.kt")

    assert "class PlayerRenderSurface" in source
    assert "val playerView: PlayerView" in source
    assert "val vlcSurfaceView: SurfaceView" in source
    assert "fun attachSurface(surface: PlayerRenderSurface)" in source


def test_exoplayer_controller_binds_media3_player_view():
    source = read_android("player/ExoPlayerController.kt")

    assert "androidx.media3.ui.PlayerView" in source
    assert "override fun attachSurface(surface: PlayerRenderSurface)" in source
    assert "surface.playerView.player = player" in source
    assert "surface.playerView.player = null" in source
    assert "Intent(" not in source


def test_vlc_controller_binds_surface_view_and_detaches_on_release():
    source = read_android("player/VlcPlayerController.kt")

    assert "android.view.SurfaceView" in source
    assert "override fun attachSurface(surface: PlayerRenderSurface)" in source
    assert "mediaPlayer.vlcVout.setVideoView(surface.vlcSurfaceView)" in source
    assert "mediaPlayer.vlcVout.attachViews()" in source
    assert "mediaPlayer.vlcVout.detachViews()" in source
    assert "Intent(" not in source


def test_player_fragment_uses_visual_surface_and_actionable_retry():
    source = read_android("player/PlayerFragment.kt")

    assert "FrameLayout" in source
    assert "PlayerRenderSurface" in source
    assert "PlayerView(requireContext())" in source
    assert "SurfaceView(requireContext())" in source
    assert "activePlayer?.attachSurface(renderSurface)" in source
    assert "retryButton" in source
    assert "setOnClickListener" in source
    assert "startBundledPlayback(playableRequest, alternate" in source
    assert "Bring up the info banner" in source
    assert "Intent(" not in source


def test_player_surface_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Visual player surface source files" in readme
    assert "Media3 PlayerView" in readme
    assert "VLC SurfaceView" in readme
    assert "### Task 9.9: Android visual player surface and retry" in plan
    assert "test_android_player_surface.py" in plan
