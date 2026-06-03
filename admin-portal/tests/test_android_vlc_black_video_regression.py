from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vlc_defers_media_play_until_video_surface_is_attached():
    source = read_android("player/VlcPlayerController.kt")

    assert "private var pendingPlaybackRequest: StreamPlaybackRequest? = null" in source
    assert "private fun startPlaybackWhenSurfaceReady(request: StreamPlaybackRequest)" in source
    assert "if (!viewsAttached)" in source
    assert "pendingPlaybackRequest = request" in source
    assert "pendingPlaybackRequest?.let { request ->" in source
    assert "pendingPlaybackRequest = null" in source
    assert "startPlaybackWhenSurfaceReady(request)" in source
    assert "mediaPlayer.play()" in source


def test_vlc_sets_window_size_and_video_output_before_playback():
    source = read_android("player/VlcPlayerController.kt")

    assert "attachedVideoLayout.width" in source
    assert "attachedVideoLayout.height" in source
    assert "attachedVideoLayout.isAttachedToWindow" in source
    assert "mediaPlayer.attachViews(attachedVideoLayout, null, false, false)" in source
    assert "mediaPlayer.vlcVout.setWindowSize(width, height)" in source


def test_vlc_clears_pending_playback_on_stop_release_and_surface_destroyed():
    source = read_android("player/VlcPlayerController.kt")

    assert "pendingPlaybackRequest = null" in source
    assert "override fun stop()" in source
    assert "override fun release()" in source
    assert "onViewDetachedFromWindow(view: View)" in source
