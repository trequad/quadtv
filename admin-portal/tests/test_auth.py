import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_auth_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_USERNAME", "admin")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")
    monkeypatch.setenv("QUADTV_SECRET_KEY", "test-secret")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app)


def test_admin_login_returns_bearer_token_for_valid_credentials(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert isinstance(body["access_token"], str)
    assert len(body["access_token"]) > 20


def test_admin_login_rejects_invalid_credentials(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "wrong"},
    )

    assert response.status_code == 401


def test_config_update_requires_admin_token_but_config_read_stays_public(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    public_get = client.get("/api/v1/app/config")
    assert public_get.status_code == 200

    payload = public_get.json()
    payload["live_tv_endpoint"] = "https://protected-change.trequad.test/playlist.m3u"

    unauthorized = client.put("/api/v1/app/config", json=payload)
    assert unauthorized.status_code == 401

    login = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    token = login.json()["access_token"]

    authorized = client.put(
        "/api/v1/app/config",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )
    assert authorized.status_code == 200
    assert authorized.json()["live_tv_endpoint"] == payload["live_tv_endpoint"]
