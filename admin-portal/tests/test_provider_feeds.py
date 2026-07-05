import importlib
import sys
from datetime import date, timedelta
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_provider_feeds_test.db"
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


def create_active_local_user_with_provider_account(client, headers):
    user_response = client.post(
        "/api/v1/users",
        json={
            "display_name": "Local Login Customer",
            "email": "local-login@example.test",
            "app_username": "quad-local-001",
            "app_password": "local-pass",
        },
        headers=headers,
    )
    assert user_response.status_code == 201
    user = user_response.json()
    expires_on = (date.today() + timedelta(days=14)).isoformat()
    subscription_response = client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": expires_on, "active": True},
        headers=headers,
    )
    assert subscription_response.status_code == 200
    import_response = client.post(
        f"/api/v1/provider-sync/users/{user['id']}/manual-import",
        json={"provider_username": "iptv001", "provider_password": "123456"},
        headers=headers,
    )
    assert import_response.status_code == 201
    return user


def customer_headers(client):
    response = client.post(
        "/api/v1/auth/customer-login",
        json={"username": "quad-local-001", "password": "local-pass"},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["provider_username"] == "quad-local-001"
    assert body["access_token"]
    return {"Authorization": f"Bearer {body['access_token']}"}


def test_customer_provider_feed_endpoint_resolves_provider_urls_server_side(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    create_active_local_user_with_provider_account(client, headers)

    response = client.get("/api/v1/provider-feeds/live-tv", headers=customer_headers(client))

    assert response.status_code == 200
    body = response.json()
    assert body["live_tv_playlist_url"].startswith(
        "http://ahhshitherewegoagain.sytes.net/get.php?username=iptv001&password="
    )
    assert "type=m3u_plus" in body["live_tv_playlist_url"]
    assert "output=mpegts" in body["live_tv_playlist_url"]
    assert "output=m3u8" not in body["live_tv_playlist_url"]
    assert body["xmltv_url"].startswith(
        "http://ahhshitherewegoagain.sytes.net/xmltv.php?username=iptv001&password="
    )
    assert body["provider_username"] == "iptv001"
    assert body["refresh_hours"] == 24
    assert "quad-local-001" not in body["live_tv_playlist_url"]
    assert "quad-local-001" not in body["xmltv_url"]


def test_provider_feed_endpoint_requires_customer_token(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    response = client.get("/api/v1/provider-feeds/live-tv")

    assert response.status_code == 401


def test_provider_feed_endpoint_reports_unlinked_provider_account(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user_response = client.post(
        "/api/v1/users",
        json={
            "display_name": "No Provider Customer",
            "email": "no-provider@example.test",
            "app_username": "quad-local-001",
            "app_password": "local-pass",
        },
        headers=headers,
    )
    assert user_response.status_code == 201

    response = client.get("/api/v1/provider-feeds/live-tv", headers=customer_headers(client))

    assert response.status_code == 409
    assert response.json()["detail"] == "No linked live TV provider account for this customer"
