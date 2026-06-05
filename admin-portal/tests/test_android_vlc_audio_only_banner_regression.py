from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_vlc_uses_android_display_rgb_software_path_for_vout_black_screen():
    source = read_android("player/VlcPlayerController.kt")

    assert "media.setHWDecoderEnabled(false, false)" in source
    assert '":no-mediacodec-dr"' in source
    assert '":no-omxil-dr"' in source
    assert '":vout=android-display"' not in source
    assert '":android-display-chroma=RV32"' not in source


def test_player_startup_status_banner_auto_hides_after_playback_starts():
    source = read_android("player/PlayerFragment.kt")

    assert "private val startupStatusHideHandler = Handler(Looper.getMainLooper())" in source
    assert "hideStartupStatusSoon()" in source
    assert "STARTUP_STATUS_AUTO_HIDE_MS" in source
    assert "statusView.visibility = View.GONE" in source
    assert "startupStatusHideHandler.removeCallbacksAndMessages(null)" in source


def test_player_screen_enters_fullscreen_immersive_mode():
    source = read_android("player/PlayerFragment.kt")

    assert "enterImmersivePlaybackMode()" in source
    assert "SYSTEM_UI_FLAG_FULLSCREEN" in source
    assert "SYSTEM_UI_FLAG_HIDE_NAVIGATION" in source
    assert "SYSTEM_UI_FLAG_IMMERSIVE_STICKY" in source


def test_player_screen_keeps_display_awake_during_playback():
    source = read_android("player/PlayerFragment.kt")

    assert "import android.view.WindowManager" in source
    assert "FLAG_KEEP_SCREEN_ON" in source
    assert "addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)" in source
    assert "clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)" in source
    assert "root.keepScreenOn = true" in source
