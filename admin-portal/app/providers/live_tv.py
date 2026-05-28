from __future__ import annotations

from typing import Any

from app.providers.base import ProviderCredentials, ProviderNotConfiguredError


class LiveTvProviderClient:
    provider_type = "live_tv"

    def __init__(self, api_base_url: str | None = None, api_token: str | None = None):
        self.api_base_url = api_base_url
        self.api_token = api_token

    def create_user(self, credentials: ProviderCredentials) -> dict[str, Any]:
        raise ProviderNotConfiguredError(
            "live_tv provider API is not configured; use manual import until a safe API/export path is available."
        )

    def fetch_user(self, provider_username: str) -> dict[str, Any] | None:
        return None

    def update_user(self, provider_username: str, updates: dict[str, Any]) -> dict[str, Any]:
        raise ProviderNotConfiguredError(
            "live_tv provider API is not configured; use manual import until a safe API/export path is available."
        )
