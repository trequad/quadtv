import importlib
import subprocess
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_admin_dashboard_test.db"
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


def test_admin_dashboard_serves_operator_ui_shell(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    response = client.get("/")

    assert response.status_code == 200
    html = response.text
    assert "QuadTV Admin" in html
    assert "id=\"login-form\"" in html
    assert "id=\"users-panel\"" in html
    assert "id=\"devices-panel\"" in html
    assert "id=\"endpoint-config-panel\"" in html
    assert "id=\"announcements-panel\"" in html
    assert "id=\"provider-sync-panel\"" in html
    assert "id=\"provider-sync-form\"" in html
    assert "app.js" in html


def test_admin_dashboard_css_preserves_hidden_state():
    css = (PROJECT_ROOT / "web" / "styles.css").read_text()

    assert "[hidden]" in css
    assert "display: none" in css
    assert "!important" in css


def test_admin_dashboard_javascript_has_valid_syntax():
    script_path = PROJECT_ROOT / "web" / "app.js"

    result = subprocess.run(["node", "--check", str(script_path)], capture_output=True, text=True)

    assert result.returncode == 0, result.stderr


def test_admin_device_list_returns_ids_and_user_links_for_assignment_ui(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    device = client.post(
        "/api/v1/devices/register",
        json={"device_identifier": "ui-device", "device_name": "Living Room", "app_version": "0.1.0"},
    ).json()
    headers = admin_headers(client)
    user = client.post(
        "/api/v1/users",
        json={"display_name": "Tre", "email": "tre@example.test"},
        headers=headers,
    ).json()
    assign = client.post(f"/api/v1/users/{user['id']}/devices/{device['id']}", headers=headers)
    assert assign.status_code == 200

    response = client.get("/api/v1/devices", headers=headers)

    assert response.status_code == 200
    assert response.json()["items"] == [
        {
            "id": device["id"],
            "device_identifier": "ui-device",
            "device_name": "Living Room",
            "app_version": "0.1.0",
            "user_id": user["id"],
            "active": True,
            "fcm_token_present": False,
            "fcm_platform": None,
        }
    ]
