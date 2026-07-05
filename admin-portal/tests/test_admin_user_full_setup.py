"""One-flow admin user setup: create + entitlements + Jellyfin + provider link + summary.

Covers docs/DESIGN_SYSTEM.md §4 (one-flow user creation) and PRODUCT_AUDIT.md S4
(local user creation must never fail because Jellyfin is down).
"""

import importlib
import subprocess
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_full_setup_test.db"
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
            "live_tv_provider_base_url": "http://provider.example:83",
            "vod_provider_base_url": "https://vod.example",
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


FULL_SETUP_PAYLOAD = {
    "display_name": "Jane Doe",
    "email": "jane@example.test",
    "app_username": "jane001",
    "app_pin": "4321",
    "access_package": "full_access",
    "expires_on": "2027-01-31",
    "provider_username": "iptv042",
    "provider_password": "9876543210",
    "provision_jellyfin": True,
}


def test_full_setup_creates_user_provisions_jellyfin_and_links_provider(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    calls = []

    def fake_create_or_update(base_url, api_key, username, password):
        calls.append({"base_url": base_url, "username": username})
        return True

    monkeypatch.setattr(users, "create_or_update_jellyfin_user", fake_create_or_update)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)

    response = client.post("/api/v1/users/full-setup", json=FULL_SETUP_PAYLOAD, headers=headers)

    assert response.status_code == 201
    body = response.json()
    assert body["user"]["display_name"] == "Jane Doe"
    assert body["user"]["access_package"] == "full_access"
    assert body["user"]["expires_on"] == "2027-01-31"
    assert body["jellyfin_status"] == "provisioned"
    assert body["provider_live_tv"] == "linked"
    assert body["provider_vod"] == "linked"
    assert body["warnings"] == []
    assert isinstance(body["next_steps"], list) and body["next_steps"]
    assert calls == [{"base_url": "https://jellyfin.example.test", "username": "jane001"}]

    # PIN provisions the Jellyfin login when no separate password is supplied,
    # and the customer can immediately sign in to the app with it.
    login = client.post("/api/v1/auth/customer-login", json={"username": "jane001", "password": "4321"})
    assert login.status_code == 200
    assert login.json()["access_token"]

    # Provider accounts were linked for both panels.
    sync = client.get(f"/api/v1/provider-sync/users/{body['user']['id']}", headers=headers)
    assert sync.status_code == 200
    statuses = {result["provider_type"]: result["sync_status"] for result in sync.json()["results"]}
    assert statuses == {"live_tv": "manual_imported", "vod": "manual_imported"}

    # No raw secrets in the summary.
    raw = response.text.lower()
    assert "4321" not in raw
    assert "9876543210" not in raw
    assert "jellyfin-admin-key" not in raw


def test_full_setup_still_creates_user_when_jellyfin_is_down(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    jellyfin_client = importlib.import_module("app.jellyfin_client")

    def fake_create_or_update(base_url, api_key, username, password):
        raise jellyfin_client.JellyfinProvisioningError("Jellyfin unreachable")

    monkeypatch.setattr(users, "create_or_update_jellyfin_user", fake_create_or_update)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)

    response = client.post("/api/v1/users/full-setup", json=FULL_SETUP_PAYLOAD, headers=headers)

    assert response.status_code == 201
    body = response.json()
    assert body["jellyfin_status"] == "failed"
    assert any("quadondemand" in warning.lower() or "jellyfin" in warning.lower() for warning in body["warnings"])

    listed = client.get("/api/v1/users", headers=headers)
    assert any(user["app_username"] == "jane001" for user in listed.json()["items"])


def test_full_setup_reports_skipped_states_and_next_steps(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    # No Jellyfin config, no provider credentials supplied.
    payload = {
        "display_name": "Live Only",
        "app_username": "liveonly007",
        "app_pin": "1111",
        "access_package": "live_tv_only",
    }

    response = client.post("/api/v1/users/full-setup", json=payload, headers=headers)

    assert response.status_code == 201
    body = response.json()
    assert body["jellyfin_status"] == "not_configured"
    assert body["provider_live_tv"] == "skipped"
    assert body["provider_vod"] == "skipped"
    assert any("provider" in step.lower() for step in body["next_steps"])
    assert body["user"]["can_access_live_tv"] is True
    assert body["user"]["can_access_quaddemand"] is False


def test_full_setup_rejects_duplicate_app_username_without_creating_user(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)

    first = client.post(
        "/api/v1/users/full-setup",
        json={"display_name": "First", "app_username": "dupe01", "app_pin": "2222"},
        headers=headers,
    )
    assert first.status_code == 201

    second = client.post(
        "/api/v1/users/full-setup",
        json={"display_name": "Second", "app_username": "DUPE01", "app_pin": "3333"},
        headers=headers,
    )
    assert second.status_code == 409

    listed = client.get("/api/v1/users", headers=headers)
    matching = [user for user in listed.json()["items"] if (user["app_username"] or "").lower() == "dupe01"]
    assert len(matching) == 1


def test_full_setup_requires_admin_token(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    response = client.post("/api/v1/users/full-setup", json=FULL_SETUP_PAYLOAD)
    assert response.status_code == 401


def test_user_overview_reports_devices_provider_and_jellyfin_without_secrets(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    monkeypatch.setattr(users, "create_or_update_jellyfin_user", lambda *args: True)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)

    created = client.post("/api/v1/users/full-setup", json=FULL_SETUP_PAYLOAD, headers=headers)
    user_id = created.json()["user"]["id"]

    device = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": "tv-abc-123", "device_name": "Living Room TV", "app_version": "1.0.0"},
    )
    assert device.status_code in (200, 201)
    device_id = device.json()["id"]
    assign = client.post(f"/api/v1/users/{user_id}/devices/{device_id}", headers=headers)
    assert assign.status_code == 200

    overview = client.get(f"/api/v1/users/{user_id}/overview", headers=headers)

    assert overview.status_code == 200
    body = overview.json()
    assert body["user"]["id"] == user_id
    assert body["subscription"]["expired"] is False
    assert body["jellyfin"]["provisioned"] is True
    assert body["jellyfin"]["username"] == "jane001"
    provider_types = {account["provider_type"]: account for account in body["provider_accounts"]}
    assert provider_types["live_tv"]["provider_username"] == "iptv042"
    assert provider_types["live_tv"]["sync_status"] == "manual_imported"
    assert [d["device_name"] for d in body["devices"]] == ["Living Room TV"]

    raw = overview.text.lower()
    assert "hash" not in raw
    assert "secret" not in raw
    assert "9876543210" not in raw
    assert "4321" not in raw


def test_dashboard_serves_one_flow_user_form_and_summary(tmp_path, monkeypatch):
    html = (PROJECT_ROOT / "web" / "index.html").read_text()
    js = (PROJECT_ROOT / "web" / "app.js").read_text()

    # Users panel is the operator's primary workspace: it must appear before releases.
    assert html.index('id="users-panel"') < html.index('id="releases-panel"')

    # One-flow form fields.
    assert 'id="user-provider-username"' in html
    assert 'id="user-provider-password"' in html
    assert 'id="user-provision-jellyfin"' in html

    # JS posts to the one-flow endpoint and renders a provisioning summary.
    assert "/users/full-setup" in js
    assert "renderUserSetupSummary" in js
    assert "jellyfin_status" in js

    # Detail panel loads the aggregate overview.
    assert "/overview" in js

    # Static syntax check.
    subprocess.run(["node", "--check", str(PROJECT_ROOT / "web" / "app.js")], check=True)
