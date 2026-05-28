import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_devices_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_USERNAME", "admin")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")
    monkeypatch.setenv("QUADTV_SECRET_KEY", "test-secret")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app)


def test_device_self_registration_creates_device_and_returns_launch_limits(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    response = client.post(
        "/api/v1/devices/register",
        json={
            "device_identifier": "android-tv-serial-001",
            "device_name": "Living Room Shield",
            "app_version": "0.1.0",
        },
    )

    assert response.status_code == 201
    body = response.json()
    assert body["device_identifier"] == "android-tv-serial-001"
    assert body["device_name"] == "Living Room Shield"
    assert body["active"] is True
    assert body["expired"] is False
    assert body["max_profiles_per_device"] == 5
    assert body["live_stream_limit_per_user"] == 3
    assert body["vod_stream_limit_per_user"] == 1
    assert body["jellyfin_stream_limit_per_user"] == 2


def test_device_self_registration_is_idempotent_for_same_identifier(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    first = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": "same-device", "device_name": "Original", "app_version": "0.1.0"},
    )
    second = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": "same-device", "device_name": "Renamed", "app_version": "0.1.1"},
    )

    assert first.status_code == 201
    assert second.status_code == 200
    assert second.json()["id"] == first.json()["id"]
    assert second.json()["device_name"] == "Renamed"
    assert second.json()["app_version"] == "0.1.1"
