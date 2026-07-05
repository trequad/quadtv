import json
import urllib.error
import urllib.request


class JellyfinProvisioningError(RuntimeError):
    pass


def _request(base_url: str, api_key: str | None, method: str, path: str, payload: dict | None = None, extra_headers: dict | None = None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
    }
    if api_key:
        headers["X-Emby-Token"] = api_key
    if extra_headers:
        headers.update(extra_headers)
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}{path}",
        data=data,
        method=method,
        headers=headers,
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


def create_or_update_jellyfin_user(base_url: str, api_key: str, username: str, password: str) -> str | None:
    """Ensure a portal user can log in to Jellyfin directly with the same app credentials.

    Returns the Jellyfin user id when known so callers can store it for
    per-user features (Continue Watching, watch state).
    """
    existing = _find_user(base_url, api_key, username)
    if existing is None:
        created = _request(base_url, api_key, "POST", "/Users/New", {"Name": username, "Password": password})
        return created.get("Id") if isinstance(created, dict) else None

    user_id = existing.get("Id")
    if not user_id:
        raise JellyfinProvisioningError("Jellyfin returned a user without Id")
    _request(base_url, api_key, "POST", f"/Users/{user_id}/Password", {"CurrentPw": "", "NewPw": password})
    return user_id


def authenticate_jellyfin_user(base_url: str, username: str, password: str) -> tuple[str, str] | None:
    """Log a customer in to Jellyfin with their own credentials.

    Returns (access_token, jellyfin_user_id) or None. Used at customer-login so
    the app receives a per-user token instead of the shared admin API key.
    """
    auth_header = (
        'MediaBrowser Client="QuadTV", Device="QuadTV Portal", '
        'DeviceId="quadtv-portal", Version="1.0"'
    )
    try:
        response = _request(
            base_url,
            None,
            "POST",
            "/Users/AuthenticateByName",
            {"Username": username, "Pw": password},
            extra_headers={"X-Emby-Authorization": auth_header, "Authorization": auth_header},
        )
    except JellyfinProvisioningError:
        return None
    if not isinstance(response, dict):
        return None
    token = response.get("AccessToken")
    user_id = (response.get("User") or {}).get("Id")
    if not token or not user_id:
        return None
    return token, user_id
