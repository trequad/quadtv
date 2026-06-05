import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_account_profiles_test.db"
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


def create_user(client, headers, name="Family"):
    response = client.post(
        "/api/v1/users",
        json={"display_name": name, "app_username": name.lower(), "app_pin": "1234"},
        headers=headers,
    )
    assert response.status_code == 201
    return response.json()


def register_device(client, identifier):
    response = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": identifier, "device_name": identifier, "app_version": "0.1.0"},
    )
    assert response.status_code == 201
    return response.json()


def assign_device(client, headers, user_id, device_id):
    response = client.post(f"/api/v1/users/{user_id}/devices/{device_id}", headers=headers)
    assert response.status_code == 200


def test_profiles_follow_account_across_assigned_devices_with_parental_settings(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers)
    living_room = register_device(client, "living-room")
    bedroom = register_device(client, "bedroom")
    assign_device(client, headers, user["id"], living_room["id"])
    assign_device(client, headers, user["id"], bedroom["id"])

    created = client.post(
        f"/api/v1/devices/{living_room['id']}/profiles",
        json={"display_name": "Kids", "avatar": "loki", "parental_pin": "4321"},
        headers=headers,
    )
    assert created.status_code == 201
    profile = created.json()
    assert profile["user_id"] == user["id"]
    assert profile["device_id"] == living_room["id"]

    updated = client.patch(
        f"/api/v1/profiles/{profile['id']}",
        json={"parental_enabled": True},
        headers=headers,
    )
    assert updated.status_code == 200
    assert updated.json()["parental_enabled"] is True

    bedroom_profiles = client.get(f"/api/v1/devices/{bedroom['id']}/profiles")
    assert bedroom_profiles.status_code == 200
    assert bedroom_profiles.json()["items"] == [updated.json()]


def test_profile_limit_is_per_account_not_per_device_for_assigned_devices(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers, "LimitFam")
    first = register_device(client, "limit-one")
    second = register_device(client, "limit-two")
    assign_device(client, headers, user["id"], first["id"])
    assign_device(client, headers, user["id"], second["id"])

    config = client.get("/api/v1/app/config").json()
    config["max_profiles_per_device"] = 1
    assert client.put("/api/v1/app/config", json=config, headers=headers).status_code == 200

    assert client.post(f"/api/v1/devices/{first['id']}/profiles", json={"display_name": "One", "avatar": "odin"}, headers=headers).status_code == 201
    assert client.post(f"/api/v1/devices/{second['id']}/profiles", json={"display_name": "Two", "avatar": "loki"}, headers=headers).status_code == 409
