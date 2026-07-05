"""Per-user Jellyfin sessions at customer login (next step after S2 hardening).

The portal authenticates the customer to Jellyfin with their own credentials
during customer-login and returns a per-user token + user id, so the app never
needs the shared admin API key and Continue Watching is user-scoped.
"""

import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_jf_user_auth_test.db"
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
            "jellyfin_base_url": "https://jellyfin.example.test/",
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


def create_user(client, headers, access_package="full_access", username="jfuser1"):
    response = client.post(
        "/api/v1/users",
        json={
            "display_name": "JF Customer",
            "app_username": username,
            "app_pin": "5150",
            "access_package": access_package,
        },
        headers=headers,
    )
    assert response.status_code == 201
    return response.json()


def test_login_returns_per_user_jellyfin_session_and_persists_user_id(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    auth = importlib.import_module("app.routers.auth")
    calls = []

    def fake_authenticate(base_url, username, password):
        calls.append({"base_url": base_url, "username": username, "password": password})
        return ("user-scoped-token", "jf-uid-42")

    monkeypatch.setattr(auth, "authenticate_jellyfin_user", fake_authenticate)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)
    created = create_user(client, headers)

    login = client.post("/api/v1/auth/customer-login", json={"username": "jfuser1", "password": "5150"})

    assert login.status_code == 200
    body = login.json()
    assert body["jellyfin_base_url"] == "https://jellyfin.example.test"
    assert body["jellyfin_user_id"] == "jf-uid-42"
    assert body["jellyfin_access_token"] == "user-scoped-token"
    assert calls == [
        {"base_url": "https://jellyfin.example.test/", "username": "jfuser1", "password": "5150"}
    ]

    # The Jellyfin user id is backfilled on the local account for admin views.
    overview = client.get(f"/api/v1/users/{created['id']}/overview", headers=headers)
    assert overview.status_code == 200
    assert overview.json()["jellyfin"]["jellyfin_user_id"] == "jf-uid-42"


def test_login_still_succeeds_when_jellyfin_auth_fails(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    auth = importlib.import_module("app.routers.auth")
    monkeypatch.setattr(auth, "authenticate_jellyfin_user", lambda *args: None)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)
    create_user(client, headers, username="jfuser2")

    login = client.post("/api/v1/auth/customer-login", json={"username": "jfuser2", "password": "5150"})

    assert login.status_code == 200
    body = login.json()
    assert body["access_token"]
    assert body["jellyfin_access_token"] is None
    assert body["jellyfin_user_id"] is None


def test_login_skips_jellyfin_auth_without_quadondemand_entitlement(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    auth = importlib.import_module("app.routers.auth")
    calls = []
    monkeypatch.setattr(auth, "authenticate_jellyfin_user", lambda *args: calls.append(args) or None)
    headers = admin_headers(client)
    configure_jellyfin(client, headers)
    create_user(client, headers, access_package="live_tv_only", username="liveonly9")

    login = client.post("/api/v1/auth/customer-login", json={"username": "liveonly9", "password": "5150"})

    assert login.status_code == 200
    assert calls == []


def test_full_setup_stores_jellyfin_user_id_from_provisioning(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    users = importlib.import_module("app.routers.users")
    monkeypatch.setattr(users, "create_or_update_jellyfin_user", lambda *args: "jf-uid-77")
    headers = admin_headers(client)
    configure_jellyfin(client, headers)

    created = client.post(
        "/api/v1/users/full-setup",
        json={"display_name": "Setup User", "app_username": "setup77", "app_pin": "7777"},
        headers=headers,
    )

    assert created.status_code == 201
    assert created.json()["jellyfin_status"] == "provisioned"
    user_id = created.json()["user"]["id"]
    overview = client.get(f"/api/v1/users/{user_id}/overview", headers=headers)
    assert overview.json()["jellyfin"]["jellyfin_user_id"] == "jf-uid-77"


def test_android_prefers_per_user_jellyfin_session():
    android = PROJECT_ROOT.parent / "android-app/app/src/main/java/net/trequad/quadtv"
    models = (android / "auth/CustomerLoginModels.kt").read_text()
    cache = (android / "core/cache/CustomerSessionCache.kt").read_text()
    provider = (android / "jellyfin/JellyfinAccessProvider.kt").read_text()

    assert '"jellyfin_base_url"' in models or "jellyfin_base_url" in models
    assert "jellyfin_access_token" in models
    assert "jellyfinAccessToken" in cache
    assert "jellyfinUserId" in cache
    # The per-user session from login wins over the shared portal access call.
    assert provider.index("jellyfinAccessToken") < provider.index("getJellyfinAccess")
