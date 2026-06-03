from datetime import datetime, timedelta, timezone
import base64
import hashlib
import hmac
import json
import secrets

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel
from cryptography.fernet import Fernet, InvalidToken

from app.settings import settings

TOKEN_TTL_HOURS = 12
bearer_scheme = HTTPBearer(auto_error=False)


class LoginRequest(BaseModel):
    username: str
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"


def _b64encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")


def _b64decode(value: str) -> bytes:
    padding = "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(value + padding)


def _sign(payload: str) -> str:
    digest = hmac.new(settings.secret_key.encode("utf-8"), payload.encode("utf-8"), hashlib.sha256).digest()
    return _b64encode(digest)


def create_admin_token() -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": settings.admin_username,
        "scope": "admin",
        "iat": int(now.timestamp()),
        "exp": int((now + timedelta(hours=TOKEN_TTL_HOURS)).timestamp()),
    }
    encoded_payload = _b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    return f"{encoded_payload}.{_sign(encoded_payload)}"


def create_customer_token(user_id: int, provider_username: str) -> str:
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "provider_username": provider_username,
        "scope": "customer",
        "iat": int(now.timestamp()),
    }
    encoded_payload = _b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    return f"{encoded_payload}.{_sign(encoded_payload)}"


def _decode_signed_token(token: str, invalid_detail: str) -> dict:
    try:
        encoded_payload, signature = token.split(".", 1)
        expected_signature = _sign(encoded_payload)
        if not secrets.compare_digest(signature, expected_signature):
            raise ValueError("bad signature")
        return json.loads(_b64decode(encoded_payload))
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=invalid_detail) from exc


def require_customer(credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme)) -> int:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing customer bearer token")
    payload = _decode_signed_token(credentials.credentials, "Invalid customer bearer token")
    if payload.get("scope") != "customer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid customer bearer token")
    try:
        return int(payload["sub"])
    except (KeyError, TypeError, ValueError) as exc:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid customer bearer token") from exc


def _provider_secret_cipher() -> Fernet:
    key = base64.urlsafe_b64encode(hashlib.sha256(settings.secret_key.encode("utf-8")).digest())
    return Fernet(key)


def encrypt_provider_secret(secret: str) -> str:
    return _provider_secret_cipher().encrypt(secret.encode("utf-8")).decode("ascii")


def decrypt_provider_secret(secret: str) -> str:
    try:
        return _provider_secret_cipher().decrypt(secret.encode("ascii")).decode("utf-8")
    except InvalidToken as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Provider credential secret cannot be decoded") from exc


def hash_provider_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 120_000)
    return f"pbkdf2_sha256${_b64encode(salt)}${_b64encode(digest)}"


def verify_provider_password(password: str, stored_hash: str) -> bool:
    try:
        algorithm, salt_value, digest_value = stored_hash.split("$", 2)
        if algorithm != "pbkdf2_sha256":
            return False
        salt = _b64decode(salt_value)
        expected_digest = _b64decode(digest_value)
        actual_digest = hashlib.pbkdf2_hmac("sha256", password.encode("utf-8"), salt, 120_000)
    except Exception:
        return False
    return secrets.compare_digest(actual_digest, expected_digest)


def authenticate_admin(username: str, password: str) -> bool:
    return secrets.compare_digest(username, settings.admin_username) and secrets.compare_digest(
        password, settings.admin_password
    )


def _decode_admin_token(token: str) -> dict:
    payload = _decode_signed_token(token, "Invalid admin bearer token")

    now = int(datetime.now(timezone.utc).timestamp())
    if payload.get("exp", 0) < now:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Expired admin bearer token")
    return payload


def require_admin(credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme)) -> str:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing admin bearer token")
    payload = _decode_admin_token(credentials.credentials)
    if payload.get("sub") != settings.admin_username or payload.get("scope") != "admin":
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid admin bearer token")
    return settings.admin_username
