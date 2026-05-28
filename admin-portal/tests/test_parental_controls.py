import importlib
import sys
from pathlib import Path

from fastapi.testclient import TestClient

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

ANDROID_SRC = PROJECT_ROOT.parent / "android-app/app/src/main/java/net/trequad/quadtv"


def build_client(tmp_path, monkeypatch):
    db_path = tmp_path / "quadtv_parental_test.db"
    monkeypatch.setenv("QUADTV_DATABASE_URL", f"sqlite:///{db_path}")
    monkeypatch.setenv("QUADTV_ADMIN_PASSWORD", "test-password")

    for module_name in list(sys.modules):
        if module_name == "app" or module_name.startswith("app."):
            sys.modules.pop(module_name)

    main = importlib.import_module("app.main")
    return TestClient(main.app)


def admin_token(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "admin", "password": "test-password"},
    )
    assert response.status_code == 200
    return response.json()["access_token"]


def read_android(relative_path):
    return (ANDROID_SRC / relative_path).read_text()


def test_parental_blocklist_defaults_and_admin_update_persist(tmp_path, monkeypatch):
    client = build_client(tmp_path, monkeypatch)

    default_response = client.get("/api/v1/parental/blocklist")
    assert default_response.status_code == 200
    assert default_response.json() == {
        "channel_ids": [],
        "category_names": [],
        "content_ratings": ["R", "NC-17", "TV-MA"],
        "keywords": ["adult", "xxx", "porn"],
    }

    token = admin_token(client)
    payload = {
        "channel_ids": ["adult-channel"],
        "category_names": ["Adults Only"],
        "content_ratings": ["R", "TV-MA"],
        "keywords": ["after dark"],
    }
    update_response = client.put(
        "/api/v1/parental/blocklist",
        json=payload,
        headers={"Authorization": f"Bearer {token}"},
    )

    assert update_response.status_code == 200
    assert update_response.json() == payload

    restarted_client = build_client(tmp_path, monkeypatch)
    persisted_response = restarted_client.get("/api/v1/parental/blocklist")
    assert persisted_response.status_code == 200
    assert persisted_response.json() == payload


def test_android_parental_filter_models_profile_state_global_blocklist_and_filter_methods():
    source = read_android("parental/ParentalControls.kt")

    assert "data class GlobalParentalBlocklist" in source
    assert "channelIds: Set<String>" in source
    assert "categoryNames: Set<String>" in source
    assert "contentRatings: Set<String>" in source
    assert "keywords: Set<String>" in source
    assert "data class ProfileParentalState" in source
    assert "profileId: Int" in source
    assert "parentalEnabled: Boolean" in source
    assert "class ParentalFilter" in source
    assert "fun filterLiveChannels" in source
    assert "fun filterEpgProgrammes" in source
    assert "fun filterVodItems" in source
    assert "fun filterJellyfinItems" in source
    assert "isMature" in source
    assert "contentRating" in source


def test_android_parental_repository_fetches_and_caches_portal_blocklist():
    source = read_android("parental/ParentalBlocklistRepository.kt")
    cache = read_android("core/cache/ParentalBlocklistCache.kt")

    assert "class ParentalBlocklistRepository" in source
    assert "AdminApiService" in source
    assert "suspend fun loadBlocklist(): GlobalParentalBlocklist" in source
    assert "apiService.getParentalBlocklist()" in source
    assert "cache.save" in source
    assert "cache.load()" in source
    assert "GlobalParentalBlocklist.defaults()" in source
    assert '@GET("api/v1/parental/blocklist")' in read_android("adminapi/AdminApiService.kt")

    assert "class ParentalBlocklistCache" in cache
    assert "SharedPreferences" in cache
    assert "channel_ids" in cache
    assert "content_ratings" in cache
