from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
DOC = PROJECT_ROOT.parent / "docs" / "ASSET_INVENTORY.md"
PLAN = PROJECT_ROOT.parent / "docs" / "IMPLEMENTATION_PLAN.md"
README = PROJECT_ROOT.parent / "README.md"


def test_asset_inventory_documents_required_final_art_and_allows_placeholders():
    content = DOC.read_text()

    assert "# QuadTV / QuadMedia Asset Inventory" in content
    assert "Final app/design assets are intentionally deferred" in content
    assert "Placeholder/simple generated assets are acceptable for current implementation" in content

    required_assets = [
        "quadtv_icon_foreground",
        "quadtv_icon_background",
        "quadtv_banner",
        "quadmedia_logo_primary",
        "quadtv_logo_horizontal",
        "quadtv_splash_logo",
        "quadtv_notification_small_icon",
        "profile_avatar_",
        "announcement_banner_",
        "vod_poster_placeholder",
        "channel_logo_placeholder",
        "rating_badge_",
        "genre_icon_",
        "portal_logo",
        "portal_favicon",
    ]
    for asset_name in required_assets:
        assert asset_name in content

    required_terms = [
        "Target sizes / densities",
        "Format",
        "Purpose / where used",
        "Android TV launcher",
        "Splash screen",
        "EPG",
        "Info banner",
        "Admin portal",
        "mdpi",
        "hdpi",
        "xhdpi",
        "xxhdpi",
        "xxxhdpi",
        "SVG",
        "PNG",
        "WebP",
    ]
    for term in required_terms:
        assert term in content


def test_readme_and_plan_link_asset_inventory_instead_of_blocking_on_final_art():
    plan = PLAN.read_text()
    readme = README.read_text()

    assert "docs/ASSET_INVENTORY.md" in plan
    assert "Final art/assets deferred" in plan
    assert "Placeholder/simple generated assets remain acceptable" in plan
    assert "docs/ASSET_INVENTORY.md" in readme
    assert "final app/design assets are deferred" in readme.lower()
