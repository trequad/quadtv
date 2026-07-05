import httpx

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.auth import decrypt_provider_secret, require_customer
from app.database import get_db
from app.models import AppConfigModel, UserModel
from app.routers.subscriptions import _status_for_user

router = APIRouter(prefix="/seerr", tags=["Seerr"])


class SeerrSessionResponse(BaseModel):
    base_url: str
    session_cookie: str


def fetch_seerr_session_cookie(base_url: str, email: str, password: str) -> str | None:
    """Log in to Seerr server-side and return the session cookie, or None."""
    try:
        response = httpx.post(
            f"{base_url.rstrip('/')}/api/v1/auth/local",
            json={"email": email, "password": password},
            timeout=10,
        )
    except Exception:
        return None
    if not response.is_success:
        return None
    for cookie_header in response.headers.get_list("set-cookie"):
        if cookie_header.startswith("connect.sid="):
            return cookie_header.split(";", 1)[0]
    return None


@router.post("/session", response_model=SeerrSessionResponse)
def create_seerr_session(
    db: Session = Depends(get_db),
    customer_user_id: int = Depends(require_customer),
):
    """Portal-mediated Seerr session so credentials never ship in the app APK.

    Replaces the hardcoded admin login in the Android client (PRODUCT_AUDIT.md S1).
    """
    user = db.get(UserModel, customer_user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid customer bearer token")
    if _status_for_user(user).expired:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Subscription expired")
    if not user.can_access_seerr:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This feature is not included in your current package",
        )

    config = db.get(AppConfigModel, 1)
    if (
        config is None
        or not config.seerr_base_url
        or not config.seerr_email
        or not config.seerr_password_secret
    ):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Requests are not configured yet",
        )

    cookie = fetch_seerr_session_cookie(
        config.seerr_base_url,
        config.seerr_email,
        decrypt_provider_secret(config.seerr_password_secret),
    )
    if not cookie:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Requests are temporarily unavailable. Please try again later.",
        )

    return SeerrSessionResponse(base_url=config.seerr_base_url.rstrip("/"), session_cookie=cookie)
