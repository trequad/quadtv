from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[1]
README = PROJECT_ROOT.parent / "README.md"
PLAN = PROJECT_ROOT.parent / "docs" / "IMPLEMENTATION_PLAN.md"


def test_readme_contains_fresh_operator_release_runbook():
    content = README.read_text()

    required_headings = [
        "## Release operator runbook",
        "### 1. Prerequisites",
        "### 2. Configure portal environment",
        "### 3. Deploy QuadTV Admin with Docker",
        "### 4. First login and endpoint configuration",
        "### 5. User, provider credential, device, and profile workflow",
        "### 6. Subscription expiration workflow",
        "### 7. Announcements and push notifications",
        "### 8. Firebase Cloud Messaging setup",
        "### 9. Build the Android TV app",
        "### 10. Update baked app constants between builds",
        "### 11. Smoke-test checklist",
    ]
    for heading in required_headings:
        assert heading in content

    required_commands_and_paths = [
        "cp .env.example .env",
        "docker compose up -d --build",
        "docker compose logs -f quadtv-admin",
        "POST /api/v1/auth/login",
        "PUT /api/v1/app/config",
        "POST /api/v1/provider-sync/users/{user_id}/manual-import",
        "POST /api/v1/devices/register",
        "PUT /api/v1/subscriptions/users/{user_id}",
        "POST /api/v1/announcements",
        "POST /api/v1/notifications/send",
        "QUADTV_FIREBASE_CREDENTIALS_PATH",
        "android-app/app/google-services.json",
        "./gradlew --no-daemon :app:assembleDebug",
        "android-app/app/src/main/java/net/trequad/quadtv/core/config/QuadTvConfig.kt",
        "docs/ASSET_INVENTORY.md",
    ]
    for term in required_commands_and_paths:
        assert term in content


def test_release_docs_call_out_operator_safe_boundaries_and_verification():
    content = README.read_text()

    required_terms = [
        "Provider CAPTCHA remains manual/API-only",
        "raw provider passwords are never returned",
        "raw FCM tokens are hidden",
        "Expired users see the branded QuadTV subscription-expired screen",
        "End-user settings must not expose DNS endpoints, backend URLs, Jellyfin API keys, or provider credentials",
        "Final app/design assets are deferred",
        "Admin portal tests",
        "JavaScript syntax check",
        "Python compile check",
        "Android debug build",
    ]
    for term in required_terms:
        assert term in content


def test_implementation_plan_marks_release_documentation_complete():
    plan = PLAN.read_text()

    assert "### Task 9.2: Release documentation" in plan
    assert "**Status:** Complete" in plan
    assert "fresh operator release runbook" in plan
    assert "Admin portal tests" in plan
    assert "Android debug build" in plan
