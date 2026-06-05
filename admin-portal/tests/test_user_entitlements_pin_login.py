import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_entitlements_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_USERNAME", "admin")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")
    monkeypatch.setenv("QUADTV_SECRET_KEY", "test-secret")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app)


def admin_headers(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_admin_can_create_live_tv_only_user_with_pin_and_login_returns_entitlements(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)

    create = client.post(
        "/api/v1/users",
        json={
            "display_name": "Live Only Customer",
            "email": "live-only@example.test",
            "app_username": "liveonly001",
            "app_pin": "4829",
            "access_package": "live_tv_only",
        },
        headers=headers,
    )

    assert create.status_code == 201
    user = create.json()
    assert user["access_package"] == "live_tv_only"
    assert user["can_access_live_tv"] is True
    assert user["can_access_vod"] is False
    assert user["can_access_quaddemand"] is False
    assert user["can_access_seerr"] is False
    assert "pin" not in str(user).lower()

    login = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "liveonly001", "password": "4829"},
    )

    assert login.status_code == 200
    body = login.json()
    assert body["access_token"]
    assert body["access_package"] == "live_tv_only"
    assert body["can_access_live_tv"] is True
    assert body["can_access_vod"] is False
    assert body["can_access_quaddemand"] is False
    assert body["can_access_seerr"] is False


def test_customer_login_rejects_wrong_pin_for_pin_user(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    response = client.post(
        "/api/v1/users",
        json={"display_name": "Pin Customer", "app_username": "pin001", "app_pin": "1234"},
        headers=headers,
    )
    assert response.status_code == 201

    login = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "pin001", "password": "9999"},
    )

    assert login.status_code == 401


def test_admin_can_manually_link_live_tv_and_vod_provider_accounts_separately(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = client.post(
        "/api/v1/users",
        json={"display_name": "Manual Link Customer", "app_username": "manual001", "app_pin": "2468"},
        headers=headers,
    ).json()

    live_link = client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_type": "live_tv", "provider_username": "live001", "provider_password": "111111"},
        headers=headers,
    )

    assert live_link.status_code == 201
    live_body = live_link.json()
    assert live_body["provider_username"] == "live001"
    assert live_body["results"] == [
        {"provider_type": "live_tv", "provider_username": "live001", "sync_status": "manual_imported", "last_error": None},
        {"provider_type": "vod", "provider_username": "", "sync_status": "not_synced", "last_error": None},
    ]

    vod_link = client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_type": "vod", "provider_username": "vod001", "provider_password": "222222"},
        headers=headers,
    )

    assert vod_link.status_code == 201
    status = client.get(f"/api/v1/provider-sync/users/{user['id']}", headers=headers)
    assert status.status_code == 200
    assert status.json()["results"] == [
        {"provider_type": "live_tv", "provider_username": "live001", "sync_status": "manual_imported", "last_error": None},
        {"provider_type": "vod", "provider_username": "vod001", "sync_status": "manual_imported", "last_error": None},
    ]
