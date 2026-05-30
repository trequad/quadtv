import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

ADMIN_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ADMIN_ROOT.parent
ANDROID_SRC = PROJECT_ROOT / "android-app/app/src/main/java/net/trequad/quadtv"
README = PROJECT_ROOT / "README.md"
PLAN = PROJECT_ROOT / "docs" / "IMPLEMENTATION_PLAN.md"

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
    assert current["release"]["minimum_supported_version_code"] == 10


def test_forced_update_status_depends_on_client_version(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)
    token = admin_token(client)
    payload = {
        "version_name": "1.0.0",
        "version_code": 100,
        "changelog": "Required signed APK update.",
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
    assert stale["forced_update_required"] is True
    assert stale["release"]["apk_url"] == "/releases/quadtv-1.0.0.apk"
    assert current_enough["update_available"] is False
    assert current_enough["forced_update_required"] is False


def test_android_update_models_repository_and_prompt_are_present():
    api = read_android("adminapi/AdminApiService.kt")
    models = read_android("updates/UpdateModels.kt")
    repository = read_android("updates/AppUpdateRepository.kt")
    prompt = read_android("updates/UpdatePromptFragment.kt")
    main = read_android("MainActivity.kt")

    assert "@GET(\"api/v1/releases/current\")" in api
    assert "current_version_code" in api
    assert "data class AppRelease" in models
    assert "versionName: String" in models
    assert "versionCode: Int" in models
    assert "minimumSupportedVersionCode: Int" in models
    assert "forced: Boolean" in models
    assert "apkUrl: String" in models
    assert "data class UpdateStatus" in models
    assert "forcedUpdateRequired: Boolean" in models
    assert "BuildConfig.VERSION_CODE" in repository
    assert "loadUpdateStatus" in repository
    assert "QuadTV Update Available" in prompt
    assert "Update Required" in prompt
    assert "sideload" in prompt.lower()
    assert "arbitrary APK URL" not in prompt
    assert "UpdatePromptFragment" in main


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
