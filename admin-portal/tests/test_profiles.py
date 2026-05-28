import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_profiles_test.db"
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


def register_device(client, identifier="profile-device"):
    response = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": identifier, "device_name": "Living Room", "app_version": "0.1.0"},
    )
    assert response.status_code == 201
    return response.json()


def test_profile_crud_for_registered_device(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    device = register_device(client)
    headers = admin_headers(client)

    created = client.post(
        f"/api/v1/devices/{device['id']}/profiles",
        json={"display_name": "Tre", "avatar": "odin", "parental_pin": "1234"},
        headers=headers,
    )

    assert created.status_code == 201
    profile = created.json()
    assert profile["device_id"] == device["id"]
    assert profile["display_name"] == "Tre"
    assert profile["avatar"] == "odin"
    assert profile["parental_enabled"] is False
    assert "parental_pin" not in profile

    listed = client.get(f"/api/v1/devices/{device['id']}/profiles")
    assert listed.status_code == 200
    assert listed.json()["items"] == [profile]

    updated = client.patch(
        f"/api/v1/profiles/{profile['id']}",
        json={"display_name": "Kiddo", "avatar": "bifrost", "parental_enabled": True},
        headers=headers,
    )
    assert updated.status_code == 200
    assert updated.json()["display_name"] == "Kiddo"
    assert updated.json()["parental_enabled"] is True

    deleted = client.delete(f"/api/v1/profiles/{profile['id']}", headers=headers)
    assert deleted.status_code == 204
    assert client.get(f"/api/v1/devices/{device['id']}/profiles").json()["items"] == []


def test_profile_limit_is_enforced_from_app_config(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    device = register_device(client, "limit-device")
    headers = admin_headers(client)
    config = client.get("/api/v1/app/config").json()
    config["max_profiles_per_device"] = 1
    assert client.put("/api/v1/app/config", json=config, headers=headers).status_code == 200

    first = client.post(
        f"/api/v1/devices/{device['id']}/profiles",
        json={"display_name": "One", "avatar": "odin"},
        headers=headers,
    )
    second = client.post(
        f"/api/v1/devices/{device['id']}/profiles",
        json={"display_name": "Two", "avatar": "loki"},
        headers=headers,
    )

    assert first.status_code == 201
    assert second.status_code == 409
