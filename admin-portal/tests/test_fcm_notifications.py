import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

ANDROID_SRC = PROJECT_ROOT.parent / "android-app/app/src/main/java/net/trequad/quadtv"
ANDROID_MANIFEST = PROJECT_ROOT.parent / "android-app/app/src/main/AndroidManifest.xml"


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_fcm_notifications_test.db"
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


def register_device(client):
    response = client.post(
        "/api/v1/devices/register",
        json={
            "device_identifier": "shield-living-room",
            "device_name": "Living Room Shield",
            "app_version": "0.1.0",
        },
    )
    assert response.status_code == 201
    return response.json()["id"]


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_device_can_register_fcm_token_and_admin_device_list_hides_raw_token(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    device_id = register_device(client)

    token_response = client.post(
        f"/api/v1/devices/{device_id}/fcm-token",
        json={"token": "firebase-token-abc", "platform": "android-tv"},
    )

    assert token_response.status_code == 200
    assert token_response.json() == {
        "device_id": device_id,
        "token_registered": True,
        "platform": "android-tv",
    }

    devices = client.get("/api/v1/devices")
    assert devices.status_code == 200
    listed = devices.json()["items"][0]
    assert listed["fcm_token_present"] is True
    assert "firebase-token-abc" not in str(listed)


def test_admin_can_queue_fcm_notification_for_all_device_or_profile_targets(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)
    headers = admin_headers(client)
    device_id = register_device(client)
    client.post(
        f"/api/v1/devices/{device_id}/fcm-token",
        json={"token": "firebase-token-abc", "platform": "android-tv"},
    )

    queued = client.post(
        "/api/v1/notifications/send",
        headers=headers,
        json={
            "notification_type": "service_alert",
            "title": "Maintenance Window",
            "body": "QuadTV maintenance starts at midnight.",
            "target_type": "device",
            "target_id": str(device_id),
            "data": {"screen": "announcements"},
        },
    )

    assert queued.status_code == 201
    payload = queued.json()
    assert payload["notification_type"] == "service_alert"
    assert payload["target_type"] == "device"
    assert payload["target_id"] == str(device_id)
    assert payload["delivery_status"] in {"queued", "sent", "firebase_not_configured"}

    history = client.get("/api/v1/notifications/history", headers=headers)
    assert history.status_code == 200
    assert history.json()["items"][0]["title"] == "Maintenance Window"

    unauthorized = client.post(
        "/api/v1/notifications/send",
        json={"title": "Nope", "body": "Missing token"},
    )
    assert unauthorized.status_code == 401


def test_android_fcm_service_token_repository_and_manifest_are_scaffolded():
    manifest = ANDROID_MANIFEST.read_text()
    service = read_android("notifications/QuadTvFirebaseMessagingService.kt")
    repository = read_android("notifications/FcmTokenRepository.kt")
    models = read_android("notifications/NotificationModels.kt")
    api = read_android("adminapi/AdminApiService.kt")

    assert "QuadTvFirebaseMessagingService" in manifest
    assert "com.google.firebase.MESSAGING_EVENT" in manifest
    assert "android:exported=\"false\"" in manifest

    assert "FirebaseMessagingService" in service
    assert "override fun onNewToken" in service
    assert "override fun onMessageReceived" in service
    assert "NotificationCompat.Builder" in service
    assert "QuadTV" in service
    assert "QuadMedia" in service

    assert "class FcmTokenRepository" in repository
    assert "suspend fun registerToken" in repository
    assert "apiService.registerFcmToken" in repository
    assert "data class FcmTokenRegistrationRequest" in models
    assert '@POST("api/v1/devices/{deviceId}/fcm-token")' in api
