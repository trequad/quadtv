from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
ANDROID_ROOT = PROJECT_ROOT.parent / "android-app" / "app" / "src" / "main" / "java" / "net" / "trequad" / "quadtv"


def read_android(relative_path: str) -> str:
    return (ANDROID_ROOT / relative_path).read_text()


def test_customer_login_screen_uses_pin_copy_instead_of_provider_password_copy():
    fragment = read_android("auth/CustomerLoginFragment.kt")
    assert "QuadTV PIN" in fragment
    assert "username and PIN" in fragment
    assert "Provider password" not in fragment
    assert "provider credentials" not in fragment


def test_android_session_models_cache_entitlements_from_login_response():
    models = read_android("auth/CustomerLoginModels.kt")
    cache = read_android("core/cache/CustomerSessionCache.kt")
    assert "accessPackage" in models
    assert "canAccessLiveTv" in models
    assert "canAccessVod" in models
    assert "canAccessQuaddemand" in models
    assert "canAccessSeerr" in models
    assert "canAccessVod" in cache
    assert "canAccessQuaddemand" in cache
    assert "canAccessSeerr" in cache


def test_main_activity_blocks_unentitled_paid_routes_with_subscribe_screen():
    main = read_android("MainActivity.kt")
    assert "SubscriptionRequiredFragment" in main
    assert "canAccessVod" in main
    assert "canAccessQuaddemand" in main
    assert "canAccessSeerr" in main
    assert "Please subscribe for access" in read_android("auth/SubscriptionRequiredFragment.kt")
    assert "SUBSCRIPTION_REQUIRED" in read_android("navigation/QuadTvNavigator.kt")
