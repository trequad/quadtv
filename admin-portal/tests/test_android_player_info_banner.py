from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_stream_playback_request_carries_info_banner_metadata():
    contract = read_android("player/PlayerContract.kt")
    live = read_android("live/LiveTvPlaybackCoordinator.kt")

    assert "val subtitle: String? = null" in contract
    assert "val groupTitle: String? = null" in contract
    assert "val contentTitle: String? = null" in contract
    assert "val nextTitle: String? = null" in contract
    assert "title = channel.name" in live
    assert "groupTitle = channel.groupTitle" in live
    assert "contentTitle = currentContentTitle ?: \"Live TV\"" in live
    assert "subtitle = \"Live TV\"" in live
    assert "nextTitle = \"Guide data pending\"" in live


def test_player_fragment_builds_cable_style_info_banner_overlay():
    source = read_android("player/PlayerFragment.kt")

    assert "private lateinit var infoBannerContainer: LinearLayout" in source
    assert "private lateinit var infoBannerTitleView: TextView" in source
    assert "private lateinit var infoBannerGroupView: TextView" in source
    assert "private lateinit var infoBannerContentView: TextView" in source
    assert "private lateinit var infoBannerNextView: TextView" in source
    assert "private lateinit var infoBannerTimeView: TextView" in source
    assert "private lateinit var infoBannerProgressBar: ProgressBar" in source
    assert "buildInfoBanner" in source
    assert "updateInfoBanner(playableRequest" in source
    assert "SimpleDateFormat(\"h:mm a\"" in source
    assert "progress = if (request.isLive) 50 else 0" in source
    assert "Channel:" in source
    assert "Group:" in source
    assert "Now playing:" in source
    assert "Next" in source
    assert "QuadTV" in source


def test_player_fragment_dpad_ok_back_toggle_info_banner():
    source = read_android("player/PlayerFragment.kt")

    assert "setOnKeyListener" in source
    assert "KeyEvent.KEYCODE_DPAD_CENTER" in source
    assert "KeyEvent.KEYCODE_ENTER" in source
    assert "KeyEvent.KEYCODE_BACK" in source
    assert "toggleInfoBanner()" in source
    assert "showInfoBanner()" in source
    assert "hideInfoBanner()" in source
    assert "infoBannerHideHandler.postDelayed" in source
    assert "INFO_BANNER_AUTO_HIDE_MS" in source
    assert "setOnClickListener { showInfoBanner() }" in source


def test_player_info_banner_has_physical_control_buttons():
    source = read_android("player/PlayerFragment.kt")

    assert "private lateinit var playbackControlRow: LinearLayout" in source
    assert "buildPlaybackControlRow" in source
    assert "controlButton(\"Back\")" in source
    assert "controlButton(\"Channel −\")" in source
    assert "controlButton(\"Channel +\")" in source
    assert "onBackPressedDispatcher.onBackPressed()" in source


def test_player_info_banner_docs_are_recorded():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "Cable-style playback info banner source files" in readme
    assert "D-pad center/enter toggles the banner" in readme
    assert "Back hides the banner before leaving playback" in readme
    assert "### Task 9.10: Android playback info banner behavior" in plan
    assert "test_android_player_info_banner.py" in plan
