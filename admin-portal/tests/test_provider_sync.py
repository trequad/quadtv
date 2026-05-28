import importlib
import sys
from datetime import date, timedelta
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_provider_sync_test.db"
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
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def create_user(client, headers, display_name="Provider Customer", email="provider@example.test"):
    response = client.post(
        "/api/v1/users",
        json={"display_name": display_name, "email": email},
        headers=headers,
    )
    assert response.status_code == 201
    return response.json()


def test_admin_manual_import_links_same_provider_credential_without_returning_password(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers)

    response = client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "customer001", "provider_password": "123456"},
        headers=headers,
    )

    assert response.status_code == 201
    body = response.json()
    assert body["user_id"] == user["id"]
    assert body["provider_username"] == "customer001"
    assert "password" not in str(body).lower()
    assert body["results"] == [
        {"provider_type": "live_tv", "provider_username": "customer001", "sync_status": "manual_imported", "last_error": None},
        {"provider_type": "vod", "provider_username": "customer001", "sync_status": "manual_imported", "last_error": None},
    ]

    status_response = client.get(f"/api/v1/provider-sync/users/{user['id']}", headers=headers)
    assert status_response.status_code == 200
    assert status_response.json()["results"] == body["results"]


def test_provider_sync_requires_admin_token(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers)

    response = client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "customer001", "provider_password": "123456"},
    )

    assert response.status_code == 401


def test_customer_login_accepts_imported_provider_credentials_and_returns_app_session(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers)
    expires_on = (date.today() + timedelta(days=14)).isoformat()
    client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": expires_on, "active": True},
        headers=headers,
    )
    client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "customer001", "provider_password": "123456"},
        headers=headers,
    )

    response = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "customer001", "password": "123456"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert isinstance(body["access_token"], str)
    assert len(body["access_token"]) > 20
    assert body["user_id"] == user["id"]
    assert body["provider_username"] == "customer001"
    assert body["expired"] is False
    assert body["expires_on"] == expires_on
    assert "password" not in str(body).lower()


def test_customer_login_rejects_wrong_provider_password(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers)
    client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "customer001", "provider_password": "123456"},
        headers=headers,
    )

    response = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "customer001", "password": "000000"},
    )

    assert response.status_code == 401


def test_customer_login_returns_expired_status_for_branded_expired_screen(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers, display_name="Expired Customer", email="expired-provider@example.test")
    yesterday = (date.today() - timedelta(days=1)).isoformat()
    client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": yesterday, "active": True},
        headers=headers,
    )
    client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "expired001", "provider_password": "654321"},
        headers=headers,
    )

    response = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "expired001", "password": "654321"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["access_token"] is None
    assert body["expired"] is True
    assert body["expires_on"] == yesterday
    assert body["message"] == "Subscription expired. Please contact QuadMedia."
