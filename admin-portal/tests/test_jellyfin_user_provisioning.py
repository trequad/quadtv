import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_jellyfin_user_test.db"
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


def configure_jellyfin(client, headers):
    response = client.put(
        "/api/v1/app/config",
        json={
            "live_tv_provider_base_url": "https://live.example.test",
            "vod_provider_base_url": "https://vod.example.test",
            "jellyfin_base_url": "https://jellyfin.example.test",
            "jellyfin_api_key": "jellyfin-admin-key",
            "max_profiles_per_device": 5,
            "warning_threshold_days": [14, 7, 3, 0],
            "live_stream_limit_per_user": 3,
            "vod_stream_limit_per_user": 1,
            "jellyfin_stream_limit_per_user": 2,
            "provider_feed_refresh_hours": 24,
        },
        headers=headers,
    )
    assert response.status_code == 200


def test_creating_portal_user_with_app_credentials_creates_jellyfin_login(tmp_path, monkeypatch):
    calls = []

    def fake_create_or_update(base_url, api_key, username, password):
        calls.append({
            "base_url": base_url,
            "api_key": api_key,
            "username": username,
            "password": password,
        })
        return True

    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    monkeypatch.setattr(users, "create_or_update_jellyfin_user", fake_create_or_update)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)

    response = client.post(
        "/api/v1/users",
        json={
            "display_name": "Portal Customer",
            "email": "customer@example.test",
            "app_username": "customer001",
            "app_password": "local-pass",
        },
        headers=headers,
    )

    assert response.status_code == 201
    assert response.json()["app_username"] == "customer001"
    assert calls == [
        {
            "base_url": "https://jellyfin.example.test",
            "api_key": "jellyfin-admin-key",
            "username": "customer001",
            "password": "local-pass",
        }
    ]
    assert "local-pass" not in str(response.json())


def test_user_create_skips_jellyfin_when_credentials_or_config_missing(tmp_path, monkeypatch):
    calls = []

    def fake_create_or_update(*args, **kwargs):
        calls.append((args, kwargs))
        return True

    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    monkeypatch.setattr(users, "create_or_update_jellyfin_user", fake_create_or_update)
    headers = admin_headers(client)

    response = client.post(
        "/api/v1/users",
        json={"display_name": "No Jellyfin", "email": "no-jellyfin@example.test"},
        headers=headers,
    )

    assert response.status_code == 201
    assert calls == []
