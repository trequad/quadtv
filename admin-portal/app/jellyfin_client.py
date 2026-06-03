import json
import urllib.error
import urllib.request


class JellyfinProvisioningError(RuntimeError):
    pass


def _request(base_url: str, api_key: str, method: str, path: str, payload: dict | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "Accept": "application/json",
            "X-Emby-Token": api_key,
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="ignore")[:300]
        raise JellyfinProvisioningError(f"Jellyfin {method} {path} failed: HTTP {exc.code} {detail}") from exc
    except urllib.error.URLError as exc:
        raise JellyfinProvisioningError(f"Jellyfin {method} {path} failed: {exc.reason}") from exc


def _find_user(base_url: str, api_key: str, username: str) -> dict | None:
    users = _request(base_url, api_key, "GET", "/Users") or []
    for user in users:
        if user.get("Name", "").casefold() == username.casefold():
            return user
    return None


def create_or_update_jellyfin_user(base_url: str, api_key: str, username: str, password: str) -> bool:
    """Ensure a portal user can log in to Jellyfin directly with the same app credentials."""
    existing = _find_user(base_url, api_key, username)
    if existing is None:
        _request(base_url, api_key, "POST", "/Users/New", {"Name": username, "Password": password})
        return True

    user_id = existing.get("Id")
    if not user_id:
        raise JellyfinProvisioningError("Jellyfin returned a user without Id")
    _request(base_url, api_key, "POST", f"/Users/{user_id}/Password", {"CurrentPw": "", "NewPw": password})
    return True
