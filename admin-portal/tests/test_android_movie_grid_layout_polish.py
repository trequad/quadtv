from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_jellyfin_movie_grid_uses_trimmed_nav_scrollable_jump_rail_and_five_columns():
    source = read_android("jellyfin/JellyfinBrowseFragment.kt")

    assert "NAV_PANE_WIDTH_DP = 220" in source
    assert "JUMP_RAIL_WIDTH_DP = 48" in source
    assert "MEDIA_GRID_SPAN_COUNT = 5" in source
    assert "GridLayoutManager(context, MEDIA_GRID_SPAN_COUNT)" in source
    assert "ScrollView(context).apply" in source
    assert "addView(jumpRailContainer)" in source
    assert "navWidth = (NAV_PANE_WIDTH_DP * dp).toInt()" in source
    assert "val navPaneWidth = (NAV_PANE_WIDTH_DP * dp).toInt()" in source
    assert "val jumpRailWidth = (JUMP_RAIL_WIDTH_DP * dp).toInt()" in source
    assert "val cardWidth = (132 * dp).toInt()" in source


def test_vod_movie_grid_uses_trimmed_nav_scrollable_jump_rail_and_five_columns():
    source = read_android("vod/VodBrowseFragment.kt")

    assert "NAV_PANE_WIDTH_DP = 220" in source
    assert "JUMP_RAIL_WIDTH_DP = 48" in source
    assert "MEDIA_GRID_SPAN_COUNT = 5" in source
    assert "GridLayoutManager(context, MEDIA_GRID_SPAN_COUNT)" in source
    assert "ScrollView(context).apply" in source
    assert "addView(jumpRailContainer)" in source
    assert "navWidth = (NAV_PANE_WIDTH_DP * dp).toInt()" in source
    assert "val navPaneWidth = (NAV_PANE_WIDTH_DP * dp).toInt()" in source
    assert "val jumpRailWidth = (JUMP_RAIL_WIDTH_DP * dp).toInt()" in source
    assert "val cardWidth = (132 * dp).toInt()" in source


def test_seer_is_recorded_as_later_not_current_jellyfin_blocking_work():
    plan = (PROJECT_ROOT / "docs/IMPLEMENTATION_PLAN.md").read_text()

    assert "Seer" in plan
    assert "later" in plan.lower()
    assert "not a blocker for current Jellyfin app playback/browse work" in plan
