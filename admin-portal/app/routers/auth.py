import httpx

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.auth import LoginRequest, TokenResponse, authenticate_admin, create_admin_token, create_customer_token, hash_provider_password, verify_provider_password
from app.database import get_db
from app.jellyfin_client import authenticate_jellyfin_user
from app.models import AppConfigModel, UserModel
from app.routers.subscriptions import _status_for_user
from app.schemas import CustomerLoginRequest, CustomerLoginResponse, CustomerRegisterRequest

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest):
    if not authenticate_admin(request.username, request.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    return TokenResponse(access_token=create_admin_token())


@router.post("/customer-login", response_model=CustomerLoginResponse)
def customer_login(request: CustomerLoginRequest, db: Session = Depends(get_db)):
    username = request.username.strip()
    credential = request.password.strip()
    user = (
        db.query(UserModel)
        .filter(func.lower(UserModel.app_username) == username.lower())
        .one_or_none()
    )
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    password_ok = bool(user.app_password_hash and verify_provider_password(credential, user.app_password_hash))
    pin_ok = bool(user.app_pin_hash and verify_provider_password(credential, user.app_pin_hash))
    if not (password_ok or pin_ok):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

    subscription = _status_for_user(user)
    if subscription.expired:
        return CustomerLoginResponse(
            access_token=None,
            user_id=user.id,
            provider_username=user.app_username,
            expired=True,
            expires_on=subscription.expires_on,
            days_remaining=subscription.days_remaining,
            access_package=user.access_package,
            can_access_live_tv=user.can_access_live_tv,
            can_access_vod=user.can_access_vod,
            can_access_quaddemand=user.can_access_quaddemand,
            can_access_seerr=user.can_access_seerr,
            message="Subscription expired. Please contact QuadMedia.",
        )

    jellyfin_base_url = None
    jellyfin_user_id = None
    jellyfin_access_token = None
    if user.can_access_quaddemand:
        config = db.get(AppConfigModel, 1)
        if config is not None and config.jellyfin_base_url and config.jellyfin_api_key:
            # Per-user Jellyfin session: the app gets the customer's own token,
            # never the shared admin API key. Failure never blocks login.
            result = authenticate_jellyfin_user(
                config.jellyfin_base_url,
                user.jellyfin_username or user.app_username or username,
                credential,
            )
            if result is not None:
                jellyfin_access_token, jellyfin_user_id = result
                jellyfin_base_url = config.jellyfin_base_url.rstrip("/")
                if user.jellyfin_user_id != jellyfin_user_id:
                    user.jellyfin_user_id = jellyfin_user_id
                    if not user.jellyfin_username:
                        user.jellyfin_username = user.app_username
                    db.add(user)
                    db.commit()

    return CustomerLoginResponse(
        access_token=create_customer_token(user.id, user.app_username),
        user_id=user.id,
        provider_username=user.app_username,
        expired=False,
        expires_on=subscription.expires_on,
        days_remaining=subscription.days_remaining,
        access_package=user.access_package,
        can_access_live_tv=user.can_access_live_tv,
        can_access_vod=user.can_access_vod,
        can_access_quaddemand=user.can_access_quaddemand,
        can_access_seerr=user.can_access_seerr,
        jellyfin_base_url=jellyfin_base_url,
        jellyfin_user_id=jellyfin_user_id,
        jellyfin_access_token=jellyfin_access_token,
    )


@router.post("/register", response_model=CustomerLoginResponse, status_code=status.HTTP_201_CREATED)
def register(request: CustomerRegisterRequest, db: Session = Depends(get_db)):
    username = request.username.strip()
    if not username or not request.password or not request.display_name.strip():
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="All fields are required")
    if db.query(UserModel).filter(UserModel.app_username == username).one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken")

    jellyfin_user_id, jellyfin_username = _create_jellyfin_account(db, username, request.password)

    user = UserModel(
        display_name=request.display_name.strip(),
        app_username=username,
        app_password_hash=hash_provider_password(request.password),
        jellyfin_user_id=jellyfin_user_id,
        jellyfin_username=jellyfin_username,
        active=True,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    return CustomerLoginResponse(
        access_token=create_customer_token(user.id, user.app_username),
        user_id=user.id,
        provider_username=user.app_username,
        expired=False,
    )


def _create_jellyfin_account(db: Session, username: str, password: str) -> tuple[str | None, str | None]:
    config = db.query(AppConfigModel).first()
    if not config or not config.jellyfin_base_url or not config.jellyfin_api_key:
        return None, None
    base_url = config.jellyfin_base_url.rstrip("/")
    headers = {"X-Emby-Token": config.jellyfin_api_key, "Content-Type": "application/json"}
    try:
        resp = httpx.post(
            f"{base_url}/Users/New",
            json={"Name": username, "Password": password},
            headers=headers,
            timeout=10,
        )
        if not resp.is_success:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Jellyfin account creation failed: {resp.status_code}",
            )
        data = resp.json()
        return data.get("Id"), data.get("Name")
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Could not reach Jellyfin: {exc}",
        )
