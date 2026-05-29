import importlib
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


def admin_headers(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_app_config_exposes_editable_provider_dns_base_urls(tmp_path, monkeypatch):
    client, db_path = build_client(tmp_path, monkeypatch)

    default_response = client.get("/api/v1/app/config")
    assert default_response.status_code == 200
    body = default_response.json()
    assert body["live_tv_provider_base_url"] == "http://by.questreams.com:83"
    assert body["vod_provider_base_url"] == "https://livinitup.online"
    assert body["provider_feed_refresh_hours"] == 24
    assert "live_tv_endpoint" not in body
    assert "xmltv_endpoint" not in body

    payload = {
        "live_tv_provider_base_url": "https://backup-live.trequad.test:8443",
        "vod_provider_base_url": "https://backup-vod.trequad.test",
        "jellyfin_base_url": "https://jellyfin.trequad.test/",
        "jellyfin_api_key": "jellyfin-secret",
        "max_profiles_per_device": 6,
        "warning_threshold_days": [14, 7, 3, 0],
        "live_stream_limit_per_user": 3,
        "vod_stream_limit_per_user": 1,
        "jellyfin_stream_limit_per_user": 2,
        "provider_feed_refresh_hours": 24,
    }
    put_response = client.put(
        "/api/v1/app/config",
        json=payload,
        headers=admin_headers(client),
    )

    assert put_response.status_code == 200
    assert db_path.exists()

    restarted_client, _ = build_client(tmp_path, monkeypatch)
    get_response = restarted_client.get("/api/v1/app/config")

    assert get_response.status_code == 200
    assert get_response.json() == payload
