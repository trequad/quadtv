from dataclasses import dataclass
from datetime import date
from pathlib import Path
import sys

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


@dataclass
class RecordingProviderClient:
    provider_type: str
    should_fail: bool = False

    def __post_init__(self):
        self.created_users = []
        self.fetched_usernames = []
        self.updated_users = []

    def create_user(self, credentials):
        self.created_users.append(credentials)
        if self.should_fail:
            raise RuntimeError(f"{self.provider_type} captcha required")
        return {
            "provider_type": self.provider_type,
            "provider_account_id": f"{self.provider_type}-account-1",
            "sync_status": "created",
        }

    def fetch_user(self, provider_username):
        self.fetched_usernames.append(provider_username)
        return None

    def update_user(self, provider_username, updates):
        self.updated_users.append((provider_username, updates))
        return {"provider_type": self.provider_type, "sync_status": "updated"}


def test_sync_matching_provider_accounts_creates_live_and_vod_with_same_credentials():
    from app.providers.base import ProviderCredentials, sync_matching_provider_accounts

    live_client = RecordingProviderClient("live_tv")
    vod_client = RecordingProviderClient("vod")
    credentials = ProviderCredentials(
        username="customer001",
        password="123456",
        display_name="Customer One",
        expires_on=date(2026, 6, 1),
        active=True,
    )

    results = sync_matching_provider_accounts([live_client, vod_client], credentials)

    assert [result.provider_type for result in results] == ["live_tv", "vod"]
    assert [result.sync_status for result in results] == ["created", "created"]
    assert [result.success for result in results] == [True, True]
    assert [result.provider_account_id for result in results] == ["live_tv-account-1", "vod-account-1"]
    assert live_client.created_users == [credentials]
    assert vod_client.created_users == [credentials]


def test_sync_matching_provider_accounts_reports_partial_failures_without_hiding_successes():
    from app.providers.base import ProviderCredentials, sync_matching_provider_accounts

    live_client = RecordingProviderClient("live_tv")
    vod_client = RecordingProviderClient("vod", should_fail=True)
    credentials = ProviderCredentials(
        username="customer002",
        password="654321",
        display_name="Customer Two",
        expires_on=None,
        active=True,
    )

    results = sync_matching_provider_accounts([live_client, vod_client], credentials)

    assert len(results) == 2
    assert results[0].provider_type == "live_tv"
    assert results[0].success is True
    assert results[0].sync_status == "created"
    assert results[0].last_error is None
    assert results[1].provider_type == "vod"
    assert results[1].success is False
    assert results[1].sync_status == "failed"
    assert "vod captcha required" in results[1].last_error
    assert live_client.created_users == [credentials]
    assert vod_client.created_users == [credentials]


def test_manual_provider_clients_are_explicitly_manual_until_api_credentials_are_configured():
    from app.providers.base import ProviderCredentials, ProviderNotConfiguredError
    from app.providers.live_tv import LiveTvProviderClient
    from app.providers.vod import VodProviderClient

    credentials = ProviderCredentials(
        username="customer003",
        password="111222",
        display_name="Customer Three",
        expires_on=None,
        active=True,
    )

    for client in (LiveTvProviderClient(), VodProviderClient()):
        assert client.provider_type in {"live_tv", "vod"}
        assert client.fetch_user(credentials.username) is None
        try:
            client.create_user(credentials)
        except ProviderNotConfiguredError as exc:
            assert client.provider_type in str(exc)
            assert "manual import" in str(exc).lower()
        else:
            raise AssertionError("unconfigured provider client should require manual import")
