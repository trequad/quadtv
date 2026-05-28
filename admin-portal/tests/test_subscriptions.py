import importlib
import sys
from datetime import date, timedelta
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_subscriptions_test.db"
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


def register_device(client, identifier="subscription-device"):
    response = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": identifier, "device_name": "Bedroom TV", "app_version": "0.1.0"},
    )
    assert response.status_code == 201
    return response.json()


def create_user(client, headers, display_name="Tre", email="tre@example.test"):
    response = client.post(
        "/api/v1/users",
        json={"display_name": display_name, "email": email},
        headers=headers,
    )
    assert response.status_code == 201
    return response.json()


def test_admin_sets_subscription_on_user_and_linked_device_reports_status(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    device = register_device(client)
    headers = admin_headers(client)
    user = create_user(client, headers)
    expires_on = (date.today() + timedelta(days=7)).isoformat()

    assign = client.post(
        f"/api/v1/users/{user['id']}/devices/{device['id']}",
        headers=headers,
    )
    assert assign.status_code == 200
    assert assign.json()["user_id"] == user["id"]

    response = client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": expires_on, "active": True},
        headers=headers,
    )

    assert response.status_code == 200
    assert response.json()["user_id"] == user["id"]
    assert response.json()["expires_on"] == expires_on
    assert response.json()["expired"] is False
    assert response.json()["days_remaining"] == 7

    launch = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": "subscription-device", "device_name": "Bedroom TV", "app_version": "0.1.0"},
    )
    assert launch.status_code == 200
    assert launch.json()["user_id"] == user["id"]
    assert launch.json()["expires_on"] == expires_on
    assert launch.json()["expired"] is False


def test_expired_user_flags_all_linked_devices_for_branded_expired_screen(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    bedroom = register_device(client, "expired-user-bedroom")
    living_room = register_device(client, "expired-user-living-room")
    headers = admin_headers(client)
    user = create_user(client, headers, display_name="Expired User", email="expired@example.test")
    yesterday = (date.today() - timedelta(days=1)).isoformat()

    for device in (bedroom, living_room):
        response = client.post(f"/api/v1/users/{user['id']}/devices/{device['id']}", headers=headers)
        assert response.status_code == 200

    response = client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": yesterday, "active": True},
        headers=headers,
    )

    assert response.status_code == 200
    assert response.json()["expired"] is True
    assert response.json()["days_remaining"] == -1

    for identifier in ("expired-user-bedroom", "expired-user-living-room"):
        launch = client.post(
            "/api/v1/devices/register",
            json={"device_identifier": identifier, "device_name": "TV", "app_version": "0.1.0"},
        )
        assert launch.json()["expired"] is True
        assert launch.json()["user_id"] == user["id"]


def test_user_subscription_update_requires_admin(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    user = create_user(client, headers, display_name="Protected User", email="protected@example.test")

    response = client.put(
        f"/api/v1/subscriptions/users/{user['id']}",
        json={"expires_on": None, "active": False},
    )

    assert response.status_code == 401
