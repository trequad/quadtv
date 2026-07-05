"""Security slice: no secrets on public endpoints; authenticated Jellyfin/Seerr access.

Covers PRODUCT_AUDIT.md findings S1 (Seerr admin credentials must come from the
portal, not the APK) and S2 (Jellyfin API key must not be served unauthenticated).
"""

import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_security_test.db"
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


BASE_CONFIG = {
    "live_tv_provider_base_url": "http://provider.example:83",
    "vod_provider_base_url": "https://vod.example",
    "jellyfin_base_url": "https://jellyfin.example.test",
    "jellyfin_api_key": "jellyfin-admin-key",
    "seerr_base_url": "https://requests.example.test",
    "seerr_email": "operator@example.test",
    "seerr_password": "seerr-operator-pass",
    "max_profiles_per_device": 5,
    "warning_threshold_days": [14, 7, 3, 0],
    "live_stream_limit_per_user": 3,
    "vod_stream_limit_per_user": 1,
    "jellyfin_stream_limit_per_user": 2,
    "provider_feed_refresh_hours": 24,
}


def configure(client, headers, **overrides):
    payload = {**BASE_CONFIG, **overrides}
    response = client.put("/api/v1/app/config", json=payload, headers=headers)
    assert response.status_code == 200
    return response


def create_customer(client, headers, access_package="full_access", username="cust001"):
    response = client.post(
        "/api/v1/users",
        json={
            "display_name": "Customer",
            "app_username": username,
            "app_pin": "9090",
            "access_package": access_package,
        },
        headers=headers,
    )
    assert response.status_code == 201
    login = client.post("/api/v1/auth/customer-login", json={"username": username, "password": "9090"})
    assert login.status_code == 200
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def test_public_config_never_exposes_jellyfin_or_seerr_secrets(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    configure(client, admin_headers(client))

    response = client.get("/api/v1/app/config")

    assert response.status_code == 200
    body = response.json()
    assert body["jellyfin_api_key"] is None
    assert body["jellyfin_api_key_set"] is True
    assert body["seerr_password"] is None
    assert body["seerr_password_set"] is True
    raw = response.text
    assert "jellyfin-admin-key" not in raw
    assert "seerr-operator-pass" not in raw


def test_put_config_with_blank_secrets_keeps_existing_values(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    configure(client, headers)

    # Admin saves the form again without re-typing secrets.
    second = configure(client, headers, jellyfin_api_key=None, seerr_password=None)
    assert second.json()["jellyfin_api_key_set"] is True
    assert second.json()["seerr_password_set"] is True

    # Jellyfin access still works with the retained key.
    customer = create_customer(client, headers)
    access = client.get("/api/v1/jellyfin/access", headers=customer)
    assert access.status_code == 200
    assert access.json()["api_key"] == "jellyfin-admin-key"


def test_jellyfin_access_requires_customer_token_and_entitlement(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    configure(client, headers)

    anonymous = client.get("/api/v1/jellyfin/access")
    assert anonymous.status_code == 401

    live_only = create_customer(client, headers, access_package="live_tv_only", username="liveonly1")
    denied = client.get("/api/v1/jellyfin/access", headers=live_only)
    assert denied.status_code == 403

    entitled = create_customer(client, headers, username="fullcust1")
    allowed = client.get("/api/v1/jellyfin/access", headers=entitled)
    assert allowed.status_code == 200
    body = allowed.json()
    assert body["base_url"] == "https://jellyfin.example.test"
    assert body["api_key"] == "jellyfin-admin-key"


def test_seerr_session_is_portal_mediated_and_entitlement_gated(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    configure(client, headers)

    seerr = importlib.import_module("app.routers.seerr")
    calls = []

    def fake_fetch(base_url, email, password):
        calls.append({"base_url": base_url, "email": email, "password": password})
        return "connect.sid=session-cookie-value"

    monkeypatch.setattr(seerr, "fetch_seerr_session_cookie", fake_fetch)

    anonymous = client.post("/api/v1/seerr/session")
    assert anonymous.status_code == 401

    live_only = create_customer(client, headers, access_package="live_tv_only", username="liveonly2")
    denied = client.post("/api/v1/seerr/session", headers=live_only)
    assert denied.status_code == 403
    assert calls == []

    entitled = create_customer(client, headers, username="fullcust2")
    session = client.post("/api/v1/seerr/session", headers=entitled)
    assert session.status_code == 200
    body = session.json()
    assert body["base_url"] == "https://requests.example.test"
    assert body["session_cookie"] == "connect.sid=session-cookie-value"
    assert calls == [
        {
            "base_url": "https://requests.example.test",
            "email": "operator@example.test",
            "password": "seerr-operator-pass",
        }
    ]


def test_seerr_session_reports_not_configured_cleanly(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    configure(client, headers, seerr_base_url=None, seerr_email=None, seerr_password=None)

    entitled = create_customer(client, headers, username="fullcust3")
    response = client.post("/api/v1/seerr/session", headers=entitled)

    assert response.status_code == 409


def test_android_no_longer_ships_seerr_admin_credentials():
    config_source = (
        PROJECT_ROOT.parent
        / "android-app/app/src/main/java/net/trequad/quadtv/core/config/QuadTvConfig.kt"
    ).read_text()
    seerr_source = (
        PROJECT_ROOT.parent
        / "android-app/app/src/main/java/net/trequad/quadtv/seerr/SeerrFragment.kt"
    ).read_text()

    assert "SEERR_ADMIN_EMAIL" not in config_source
    assert "SEERR_ADMIN_PASSWORD" not in config_source
    assert "REDACTED_PASSWORD" not in config_source
    # The app asks the portal for a session instead of logging in as admin itself.
    assert "seerr/session" in seerr_source or "SeerrSessionRepository" in seerr_source
    assert "auth/local" not in seerr_source
