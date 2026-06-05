from urllib.parse import quote

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import decrypt_provider_secret, require_customer
from app.database import get_db
from app.models import AppConfigModel, ProviderAccountModel, UserModel
from app.routers.config import DEFAULT_CONFIG
from app.routers.subscriptions import _status_for_user
from app.schemas import ProviderFeedResponse

router = APIRouter(prefix="/provider-feeds", tags=["Provider Feeds"])


def _normalise_base_url(value: str) -> str:
    return value.strip().rstrip("/")


def _load_config(db: Session) -> AppConfigModel | None:
    return db.get(AppConfigModel, 1)


def _build_live_playlist_url(base_url: str, provider_username: str, provider_password: str) -> str:
    return (
        f"{base_url}/get.php?username={quote(provider_username)}"
        f"&password={quote(provider_password)}&type=m3u_plus&output=mpegts"
    )


def _build_xmltv_url(base_url: str, provider_username: str, provider_password: str) -> str:
    return f"{base_url}/xmltv.php?username={quote(provider_username)}&password={quote(provider_password)}"


@router.get("/live-tv", response_model=ProviderFeedResponse)
def get_live_tv_provider_feed(
    db: Session = Depends(get_db),
    customer_user_id: int = Depends(require_customer),
):
    user = db.get(UserModel, customer_user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid customer bearer token")

    subscription = _status_for_user(user)
    if subscription.expired:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Subscription expired")
    if not user.can_access_live_tv:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Please subscribe for access")

    account = (
        db.query(ProviderAccountModel)
        .filter(
            ProviderAccountModel.user_id == customer_user_id,
            ProviderAccountModel.provider_type == "live_tv",
        )
        .one_or_none()
    )
    if account is None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="No linked live TV provider account for this customer",
        )
    if not account.provider_password_secret:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Live TV provider account must be re-imported before feeds can be resolved",
        )

    config = _load_config(db)
    base_url = _normalise_base_url(
        config.live_tv_provider_base_url if config is not None else DEFAULT_CONFIG.live_tv_provider_base_url
    )
    refresh_hours = config.provider_feed_refresh_hours if config is not None else DEFAULT_CONFIG.provider_feed_refresh_hours
    provider_password = decrypt_provider_secret(account.provider_password_secret)
    return ProviderFeedResponse(
        live_tv_playlist_url=_build_live_playlist_url(base_url, account.provider_username, provider_password),
        xmltv_url=_build_xmltv_url(base_url, account.provider_username, provider_password),
        provider_username=account.provider_username,
        refresh_hours=refresh_hours,
    )
