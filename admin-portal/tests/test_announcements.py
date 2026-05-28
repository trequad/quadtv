import importlib
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_announcements_test.db"
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


def test_admin_can_create_publish_update_and_delete_announcement(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    publish_at = datetime.now(timezone.utc).isoformat()
    expires_at = (datetime.now(timezone.utc) + timedelta(days=2)).isoformat()

    created = client.post(
        "/api/v1/announcements",
        json={
            "title": "Service Window",
            "body": "Maintenance tonight at midnight.",
            "image_url": "https://cdn.trequad.test/banner.png",
            "publish_at": publish_at,
            "expires_at": expires_at,
            "push_notification": True,
        },
        headers=headers,
    )

    assert created.status_code == 201
    announcement = created.json()
    assert announcement["title"] == "Service Window"
    assert announcement["active"] is True

    public = client.get("/api/v1/announcements/active")
    assert public.status_code == 200
    assert public.json()["items"][0]["id"] == announcement["id"]

    history = client.get("/api/v1/notifications/history", headers=headers)
    assert history.status_code == 200
    assert history.json()["items"][0]["notification_type"] == "announcement"
    assert history.json()["items"][0]["title"] == "Service Window"

    updated = client.patch(
        f"/api/v1/announcements/{announcement['id']}",
        json={"title": "Updated Window", "active": False},
        headers=headers,
    )
    assert updated.status_code == 200
    assert updated.json()["title"] == "Updated Window"
    assert updated.json()["active"] is False
    assert client.get("/api/v1/announcements/active").json()["items"] == []

    deleted = client.delete(f"/api/v1/announcements/{announcement['id']}", headers=headers)
    assert deleted.status_code == 204


def test_announcement_mutations_require_admin_but_active_list_is_public(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    public = client.get("/api/v1/announcements/active")
    assert public.status_code == 200

    unauthorized = client.post(
        "/api/v1/announcements",
        json={"title": "Nope", "body": "Missing token"},
    )
    assert unauthorized.status_code == 401
