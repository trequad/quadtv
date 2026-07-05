from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.auth import require_customer
from app.database import get_db
from app.models import AppConfigModel, UserModel
from app.routers.subscriptions import _status_for_user

router = APIRouter(prefix="/jellyfin", tags=["Jellyfin"])


class JellyfinAccessResponse(BaseModel):
    base_url: str
    api_key: str
    jellyfin_username: str | None = None
    jellyfin_user_id: str | None = None


@router.get("/access", response_model=JellyfinAccessResponse)
def get_jellyfin_access(
    db: Session = Depends(get_db),
    customer_user_id: int = Depends(require_customer),
):
    """QuadOnDemand access for signed-in, entitled customers only.

    This replaces the old behaviour of shipping the Jellyfin API key on the
    unauthenticated public config endpoint (PRODUCT_AUDIT.md S2).
    """
    user = db.get(UserModel, customer_user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid customer bearer token")
    if _status_for_user(user).expired:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Subscription expired")
    if not user.can_access_quaddemand:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="This feature is not included in your current package",
        )

    config = db.get(AppConfigModel, 1)
    if config is None or not config.jellyfin_base_url or not config.jellyfin_api_key:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="QuadOnDemand is not configured yet",
        )

    return JellyfinAccessResponse(
        base_url=config.jellyfin_base_url.rstrip("/"),
        api_key=config.jellyfin_api_key,
        jellyfin_username=user.jellyfin_username,
        jellyfin_user_id=user.jellyfin_user_id,
    )
