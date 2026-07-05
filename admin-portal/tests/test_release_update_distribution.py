import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

ADMIN_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ADMIN_ROOT.parent
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"
SCRIPT = PROJECT_ROOT / "scripts" / "build_and_publish_beta.py"

if str(ADMIN_ROOT) not in sys.path:
    sys.path.insert(0, str(ADMIN_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app), db_path


def admin_token(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_release_metadata_defaults_empty_and_admin_publish_persists(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)

    empty_response = client.get("/api/v1/releases/current")
    assert empty_response.status_code == 200
    assert empty_response.json() == {
        "update_available": False,
        "forced_update_required": False,
        "release": None,
    }

    publish_payload = {
        "version_name": "0.2.0",
        "version_code": 20,
        "changelog": "Adds private QuadTV update prompts.",
        "apk_url": "/releases/quadtv-0.2.0.apk",
        "minimum_supported_version_code": 10,
        "forced": False,
        "published": True,
    }
    unauthorized = client.post("/api/v1/releases", json=publish_payload)
    assert unauthorized.status_code in {401, 403}

    token = admin_token(client)
    publish_response = client.post(
        "/api/v1/releases",
        json=publish_payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert publish_response.status_code == 200
    body = publish_response.json()
    assert body["version_name"] == "0.2.0"
    assert body["version_code"] == 20
    assert body["apk_url"] == "/releases/quadtv-0.2.0.apk"
    assert body["forced"] is False
    assert body["published"] is True
    assert body["release_date"] is not None

    current_response = client.get("/api/v1/releases/current")
    assert current_response.status_code == 200
    current = current_response.json()
    assert current["update_available"] is True
    assert current["release"]["version_name"] == "0.2.0"
    assert current["release"]["minimum_supported_version_code"] == 0


def test_admin_can_upload_apk_for_release_publishing(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)
    token = admin_token(client)

    unauthorized = client.post(
        "/api/v1/releases/upload",
        files={"apk": ("QuadTV-beta.apk", b"PK\x03\x04fake-apk", "application/vnd.android.package-archive")},
    )
    assert unauthorized.status_code in {401, 403}

    response = client.post(
        "/api/v1/releases/upload",
        files={"apk": ("QuadTV beta 2.apk", b"PK\x03\x04fake-apk", "application/vnd.android.package-archive")},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    payload = response.json()
    assert payload["apk_url"].startswith("/downloads/")
    assert payload["apk_url"].endswith(".apk")
    assert payload["filename"] in payload["apk_url"]
    assert payload["size_bytes"] == len(b"PK\x03\x04fake-apk")
    stored = ADMIN_ROOT / "web" / "downloads" / payload["filename"]
    assert stored.read_bytes().startswith(b"PK\x03\x04")

    bad = client.post(
        "/api/v1/releases/upload",
        files={"apk": ("notes.txt", b"not an apk", "text/plain")},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert bad.status_code == 400


def test_download_route_returns_404_for_missing_apk_and_apk_mime_for_zip_magic(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)

    missing = client.get("/downloads/missing-beta.apk")
    assert missing.status_code == 404

    downloads = ADMIN_ROOT / "web" / "downloads"
    downloads.mkdir(parents=True, exist_ok=True)
    extensionless = downloads / "app-beta-test"
    extensionless.write_bytes(b"PK\x03\x04fake-apk")
    try:
        response = client.get("/downloads/app-beta-test")
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("application/vnd.android.package-archive")
        assert response.content.startswith(b"PK\x03\x04")
    finally:
        extensionless.unlink(missing_ok=True)


def test_release_dashboard_has_apk_upload_controls():
    html = (ADMIN_ROOT / "web" / "index.html").read_text()
    js = (ADMIN_ROOT / "web" / "app.js").read_text()

    assert 'id="release-apk-file"' in html
    assert 'type="file"' in html
    assert 'accept=".apk,application/vnd.android.package-archive"' in html
    assert 'id="upload-release-apk-button"' in html
    assert "Upload APK" in html
    assert "auto-filled after upload" in html
    assert 'id="release-apk-url"' in html and "readonly" in html
    assert "/releases/upload" in js
    assert "FormData" in js
    assert "release-apk-url" in js
    assert "autoFillReleaseFieldsFromUpload" in js
    assert "inferVersionNameFromFilename" in js
    assert "nextReleaseCode" in js
    assert "release-forced" not in html
    assert "release-forced" not in js
    assert "Force update" not in html
    assert "forced update" not in js.lower()


def test_release_status_never_requires_forced_update_even_for_stale_clients(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)
    token = admin_token(client)
    payload = {
        "version_name": "1.0.0",
        "version_code": 100,
        "changelog": "Optional signed APK update.",
        "apk_url": "/releases/quadtv-1.0.0.apk",
        "minimum_supported_version_code": 90,
        "forced": True,
        "published": True,
    }
    response = client.post(
        "/api/v1/releases",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200

    stale = client.get("/api/v1/releases/current?current_version_code=50").json()
    current_enough = client.get("/api/v1/releases/current?current_version_code=100").json()

    assert stale["update_available"] is True
    assert stale["forced_update_required"] is False
    assert stale["release"]["apk_url"] == "/releases/quadtv-1.0.0.apk"
    assert current_enough["update_available"] is False
    assert current_enough["forced_update_required"] is False


def test_beta_publish_script_creates_optional_releases_only():
    content = SCRIPT.read_text()

    assert "forced=True" not in content
    assert "forced=False" in content
    assert "minimum_supported_version_code=0" in content


def test_android_app_has_no_update_prompt_or_release_status_gate():
    api = read_android("adminapi/AdminApiService.kt")
    main = read_android("MainActivity.kt")

    assert "api/v1/releases/current" not in api
    assert "getCurrentReleaseStatus" not in api
    assert "AppUpdateRepository" not in main
    assert "UpdatePromptFragment" not in main
    assert "checkForRequiredUpdateThenLaunch" not in main
    assert "showUpdatePrompt" not in main
    assert "launchLoginOrProfiles()" in main


def test_main_activity_launches_directly_without_private_apk_update_status_check():
    main = read_android("MainActivity.kt")

    assert "private lateinit var appUpdateRepository" not in main
    assert "buildAppUpdateRepository" not in main
    assert "lifecycleScope.launch" not in main
    assert "appUpdateRepository.loadUpdateStatus()" not in main
    assert "forcedUpdateRequired" not in main
    assert "showUpdatePrompt" not in main
    assert "navigateTo(QuadTvRoute.LOGIN)" in main
    assert "launchLoginOrProfiles()" in main


def test_update_prompt_source_is_removed_from_android_app():
    main = read_android("MainActivity.kt")
    updates_dir = ANDROID_SRC / "updates"

    assert not updates_dir.exists()
    assert "showUpdatePrompt" not in main
    assert "status.updateAvailable" not in main


def test_docs_record_private_signed_apk_update_slice():
    readme = README.read_text()
    plan = PLAN.read_text()

    assert "self-hosted APK release metadata" in readme
    assert "admin-only release publishing" in readme
    assert "signed APK" in readme
    assert "forced update" in readme
    assert "end users never enter arbitrary APK URLs" in readme
    assert "### Task 9.14: Sideloaded APK release and update distribution" in plan
    assert "`admin-portal/tests/test_release_update_distribution.py`" in plan
