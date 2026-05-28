from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Any, Protocol


SUPPORTED_PROVIDER_TYPES = ("live_tv", "vod")


@dataclass(frozen=True)
class ProviderCredentials:
    username: str
    password: str
    display_name: str
    expires_on: date | None
    active: bool = True


@dataclass(frozen=True)
class ProviderOperationResult:
    provider_type: str
    success: bool
    sync_status: str
    provider_account_id: str | None = None
    last_error: str | None = None


class ProviderNotConfiguredError(RuntimeError):
    """Raised when a provider panel has no safe API automation configured."""


class ProviderClient(Protocol):
    provider_type: str

    def create_user(self, credentials: ProviderCredentials) -> dict[str, Any] | ProviderOperationResult:
        """Create a provider-panel user and return status metadata."""

    def fetch_user(self, provider_username: str) -> dict[str, Any] | None:
        """Fetch a provider-panel user by username, or None when not found/unsupported."""

    def update_user(self, provider_username: str, updates: dict[str, Any]) -> dict[str, Any] | ProviderOperationResult:
        """Update a provider-panel user and return status metadata."""


def _normalize_success_result(provider_type: str, raw_result: dict[str, Any] | ProviderOperationResult) -> ProviderOperationResult:
    if isinstance(raw_result, ProviderOperationResult):
        return raw_result
    return ProviderOperationResult(
        provider_type=str(raw_result.get("provider_type") or provider_type),
        success=bool(raw_result.get("success", True)),
        sync_status=str(raw_result.get("sync_status") or "created"),
        provider_account_id=raw_result.get("provider_account_id"),
        last_error=raw_result.get("last_error"),
    )


def sync_matching_provider_accounts(
    clients: list[ProviderClient], credentials: ProviderCredentials
) -> list[ProviderOperationResult]:
    """Create matching live/VOD provider accounts and preserve partial failure details.

    The provider panels may fail independently. A VOD CAPTCHA, API outage, or bad
    credential should not hide a successful live-TV creation. The caller receives
    one result per client in input order.
    """
    results: list[ProviderOperationResult] = []
    for client in clients:
        try:
            raw_result = client.create_user(credentials)
        except Exception as exc:  # noqa: BLE001 - preserve provider-specific failure text for operator triage.
            results.append(
                ProviderOperationResult(
                    provider_type=client.provider_type,
                    success=False,
                    sync_status="failed",
                    last_error=str(exc),
                )
            )
            continue
        results.append(_normalize_success_result(client.provider_type, raw_result))
    return results
