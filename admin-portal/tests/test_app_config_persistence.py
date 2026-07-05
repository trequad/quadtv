import importlib
import os
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app), db_path


def test_app_config_update_persists_across_app_restart(tmp_path, monkeypatch):
    client, db_path = build_client(tmp_path, monkeypatch)

    payload = {
        "live_tv_provider_base_url": "https://live.trequad.test",
        "vod_provider_base_url": "https://vod.trequad.test",
        "jellyfin_base_url": "https://jellyfin.trequad.test/",
        "jellyfin_api_key": "jellyfin-secret",
        "max_profiles_per_device": 6,
        "warning_threshold_days": [14, 7, 3, 0],
        "live_stream_limit_per_user": 3,
        "vod_stream_limit_per_user": 1,
        "jellyfin_stream_limit_per_user": 2,
        "provider_feed_refresh_hours": 24,
    }

    login_response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    token = login_response.json()["access_token"]
    put_response = client.put(
        "/api/v1/app/config",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )

    assert put_response.status_code == 200
    assert db_path.exists()

    restarted_client, _ = build_client(tmp_path, monkeypatch)
    get_response = restarted_client.get("/api/v1/app/config")

    assert get_response.status_code == 200
    body = get_response.json()
    # Secrets are write-only: never echoed back, only reported as set.
    assert body["jellyfin_api_key"] is None
    assert body["jellyfin_api_key_set"] is True
    assert "jellyfin-secret" not in get_response.text
    expected = {key: value for key, value in payload.items() if key != "jellyfin_api_key"}
    for key, value in expected.items():
        assert body[key] == value


def test_default_config_contains_confirmed_stream_entitlements(tmp_path, monkeypatch):
    client, _ = build_client(tmp_path, monkeypatch)

    response = client.get("/api/v1/app/config")

    assert response.status_code == 200
    body = response.json()
    assert body["live_tv_provider_base_url"] == "http://ahhshitherewegoagain.sytes.net/"
    assert body["vod_provider_base_url"] == "https://livinitup.online"
    assert body["provider_feed_refresh_hours"] == 24
    assert body["live_stream_limit_per_user"] == 3
    assert body["vod_stream_limit_per_user"] == 1
    assert body["jellyfin_stream_limit_per_user"] == 2
