from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_player_contract_exposes_on_demand_transport_methods():
    contract = read_android("player/PlayerContract.kt")

    assert "fun pause()" in contract
    assert "fun resume()" in contract
    assert "fun seekBy(milliseconds: Long)" in contract


def test_vlc_controller_implements_pause_resume_and_seek():
    source = read_android("player/VlcPlayerController.kt")

    assert "override fun pause()" in source
    assert "mediaPlayer.pause()" in source
    assert "override fun resume()" in source
    assert "mediaPlayer.play()" in source
    assert "override fun seekBy(milliseconds: Long)" in source
    assert "mediaPlayer.setTime" in source
    assert "mediaPlayer.getLength" in source


def test_player_fragment_shows_on_demand_transport_icons_and_remote_keys():
    source = read_android("player/PlayerFragment.kt")

    # Transport controls are icon buttons (Tre 2026-07-04), not word buttons.
    assert "iconControlButton(android.R.drawable.ic_media_rew" in source
    assert "iconControlButton(android.R.drawable.ic_media_pause" in source
    assert "iconControlButton(android.R.drawable.ic_media_ff" in source
    assert "ic_media_previous" in source
    assert "ic_media_next" in source
    assert "ImageButton" in source
    assert "contentDescription" in source
    assert 'controlButton("Pause")' not in source
    assert 'controlButton("Rewind 30s")' not in source
    # Pause button swaps between pause and play icons.
    assert "setImageResource(android.R.drawable.ic_media_play)" in source
    assert "setImageResource(android.R.drawable.ic_media_pause)" in source
    assert "togglePauseResume()" in source
    assert "seekOnDemand(offsetMs = -SEEK_STEP_MS)" in source
    assert "seekOnDemand(offsetMs = SEEK_STEP_MS)" in source
    assert "KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE" in source
    assert "KeyEvent.KEYCODE_MEDIA_REWIND" in source
    assert "KeyEvent.KEYCODE_MEDIA_FAST_FORWARD" in source
    assert "if (!request.isLive)" in source


def test_docs_record_on_demand_transport_controls():
    readme = (PROJECT_ROOT / "README.md").read_text()
    plan = (PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md").read_text()

    assert "On-demand playback transport controls" in readme
    assert "Pause/Resume, Rewind 30s, and Forward 30s" in readme
    assert "### Task 9.22: On-demand playback transport controls" in plan
    assert "test_android_on_demand_transport_controls.py" in plan
