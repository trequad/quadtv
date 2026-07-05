from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import func
from sqlalchemy.orm import Session

from app.auth import hash_provider_password, require_admin
from app.database import get_db
from app.jellyfin_client import JellyfinProvisioningError, create_or_update_jellyfin_user
from app.models import AppConfigModel, DeviceModel, ProviderAccountModel, UserModel
from app.routers.provider_sync import upsert_provider_accounts
from app.routers.subscriptions import _status_for_user
from app.schemas import (
    User,
    UserCreate,
    UserDeviceAssignment,
    UserFullSetupRequest,
    UserOverview,
    UserOverviewDevice,
    UserOverviewJellyfin,
    UserOverviewProviderAccount,
    UserSetupSummary,
    UserUpdate,
)

router = APIRouter(prefix="/users", tags=["Users"])

ACCESS_PACKAGES = {
    "live_tv_only": {
        "can_access_live_tv": True,
        "can_access_vod": False,
        "can_access_quaddemand": False,
        "can_access_seerr": False,
    },
    "live_tv_vod": {
        "can_access_live_tv": True,
        "can_access_vod": True,
        "can_access_quaddemand": False,
        "can_access_seerr": False,
    },
    "live_tv_quaddemand": {
        "can_access_live_tv": True,
        "can_access_vod": False,
        "can_access_quaddemand": True,
        "can_access_seerr": False,
    },
    "full_access": {
        "can_access_live_tv": True,
        "can_access_vod": True,
        "can_access_quaddemand": True,
        "can_access_seerr": True,
    },
}


def _entitlements_for_request(request: UserCreate | UserUpdate, current: UserModel | None = None) -> dict[str, bool | str]:
    access_package = getattr(request, "access_package", None) or (current.access_package if current else "full_access")
    if access_package not in ACCESS_PACKAGES:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Unknown access package")
    result: dict[str, bool | str] = {"access_package": access_package}
    for key, default_value in ACCESS_PACKAGES[access_package].items():
        value = getattr(request, key, None)
        result[key] = default_value if value is None else value
    return result


def _get_user_or_404(user_id: int, db: Session) -> UserModel:
    user = db.get(UserModel, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


def _get_device_or_404(device_id: int, db: Session) -> DeviceModel:
    device = db.get(DeviceModel, device_id)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    return device


def _provision_jellyfin_login_if_configured(request: UserCreate | UserUpdate, db: Session) -> None:
    username = getattr(request, "app_username", None)
    password = getattr(request, "app_password", None)
    if not username or not password:
        return
    config = db.get(AppConfigModel, 1)
    if config is None or not config.jellyfin_base_url or not config.jellyfin_api_key:
        return
    try:
        create_or_update_jellyfin_user(
            config.jellyfin_base_url,
            config.jellyfin_api_key,
            username,
            password,
        )
    except JellyfinProvisioningError as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Jellyfin user provisioning failed",
        ) from exc


@router.post("/full-setup", response_model=UserSetupSummary, status_code=status.HTTP_201_CREATED)
def full_setup_user(
    request: UserFullSetupRequest,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    """One-flow user setup: local account, entitlements, Jellyfin login, provider link.

    The local account is always created; integration failures are reported as
    warnings in the summary instead of aborting the whole workflow.
    """
    username = (request.app_username or "").strip() or None
    if username is not None:
        existing = (
            db.query(UserModel)
            .filter(func.lower(UserModel.app_username) == username.lower())
            .one_or_none()
        )
        if existing is not None:
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="App username already taken")

    warnings: list[str] = []
    next_steps: list[str] = []

    entitlements = _entitlements_for_request(request)
    login_secret = request.app_password or request.app_pin
    user = UserModel(
        display_name=request.display_name,
        email=request.email,
        active=True,
        expires_on=request.expires_on,
        app_username=username,
        app_password_hash=hash_provider_password(request.app_password) if request.app_password else None,
        app_pin_hash=hash_provider_password(request.app_pin) if request.app_pin else None,
        **entitlements,
    )
    db.add(user)
    db.flush()  # assign user.id so integrations can reference it

    # QuadOnDemand (Jellyfin) login provisioning — never blocks local creation.
    jellyfin_detail = None
    config = db.get(AppConfigModel, 1)
    jellyfin_configured = bool(config and config.jellyfin_base_url and config.jellyfin_api_key)
    if not request.provision_jellyfin:
        jellyfin_status = "disabled"
    elif not jellyfin_configured:
        jellyfin_status = "not_configured"
        if username and login_secret:
            next_steps.append("Configure the QuadOnDemand (Jellyfin) server in App Config to enable automatic logins.")
    elif not username or not login_secret:
        jellyfin_status = "skipped_no_credentials"
        next_steps.append("Add an app username and PIN/password to provision a QuadOnDemand login.")
    else:
        try:
            provisioned_id = create_or_update_jellyfin_user(
                config.jellyfin_base_url, config.jellyfin_api_key, username, login_secret
            )
            jellyfin_status = "provisioned"
            user.jellyfin_username = username
            if isinstance(provisioned_id, str) and provisioned_id:
                user.jellyfin_user_id = provisioned_id
        except JellyfinProvisioningError as exc:
            jellyfin_status = "failed"
            jellyfin_detail = "Could not reach the QuadOnDemand server."
            warnings.append(
                "QuadOnDemand (Jellyfin) login could not be provisioned — re-run provisioning from the user panel."
            )
            del exc  # never echo upstream error text (may contain URLs)

    # Provider account linking (live TV + VOD share one credential set).
    provider_username = (request.provider_username or "").strip()
    if provider_username and request.provider_password:
        try:
            upsert_provider_accounts(db, user.id, provider_username, request.provider_password)
            provider_live_tv = provider_vod = "linked"
        except HTTPException:
            provider_live_tv = provider_vod = "conflict"
            warnings.append("Provider username is already linked to another customer — provider link skipped.")
    else:
        provider_live_tv = provider_vod = "skipped"
        next_steps.append("Link Live TV/VOD provider credentials when they are available.")

    db.commit()
    db.refresh(user)

    next_steps.append("Ask the customer to sign in on their device, then link the device to this account.")

    return UserSetupSummary(
        user=User.model_validate(user, from_attributes=True),
        jellyfin_status=jellyfin_status,
        jellyfin_detail=jellyfin_detail,
        provider_live_tv=provider_live_tv,
        provider_vod=provider_vod,
        warnings=warnings,
        next_steps=next_steps,
    )


@router.get("/{user_id}/overview", response_model=UserOverview)
def user_overview(
    user_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    user = _get_user_or_404(user_id, db)
    accounts = db.query(ProviderAccountModel).filter(ProviderAccountModel.user_id == user_id).all()
    devices = db.query(DeviceModel).filter(DeviceModel.user_id == user_id).order_by(DeviceModel.created_at).all()
    return UserOverview(
        user=User.model_validate(user, from_attributes=True),
        subscription=_status_for_user(user),
        jellyfin=UserOverviewJellyfin(
            provisioned=bool(user.jellyfin_username or user.jellyfin_user_id),
            username=user.jellyfin_username,
            jellyfin_user_id=user.jellyfin_user_id,
        ),
        provider_accounts=[
            UserOverviewProviderAccount(
                provider_type=account.provider_type,
                provider_username=account.provider_username,
                sync_status=account.sync_status,
            )
            for account in accounts
        ],
        devices=[
            UserOverviewDevice(
                id=device.id,
                device_name=device.device_name,
                app_version=device.app_version,
                active=device.active,
            )
            for device in devices
        ],
    )


@router.get("")
def list_users(
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    users = db.query(UserModel).order_by(UserModel.created_at.desc()).all()
    return {"items": [User.model_validate(user, from_attributes=True).model_dump() for user in users]}


@router.post("", response_model=User, status_code=status.HTTP_201_CREATED)
def create_user(
    request: UserCreate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    _provision_jellyfin_login_if_configured(request, db)
    entitlements = _entitlements_for_request(request)
    user = UserModel(
        display_name=request.display_name,
        email=request.email,
        active=True,
        expires_on=request.expires_on,
        app_username=request.app_username or None,
        app_password_hash=hash_provider_password(request.app_password) if request.app_password else None,
        app_pin_hash=hash_provider_password(request.app_pin) if request.app_pin else None,
        **entitlements,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return User.model_validate(user, from_attributes=True)


@router.patch("/{user_id}", response_model=User)
def update_user(
    user_id: int,
    request: UserUpdate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    user = _get_user_or_404(user_id, db)
    updates = request.model_dump(exclude_unset=True)
    app_password = updates.pop("app_password", None)
    app_pin = updates.pop("app_pin", None)
    if app_password is not None:
        user.app_password_hash = hash_provider_password(app_password)
    if app_pin is not None:
        user.app_pin_hash = hash_provider_password(app_pin)
    entitlement_keys = {"access_package", "can_access_live_tv", "can_access_vod", "can_access_quaddemand", "can_access_seerr"}
    if entitlement_keys.intersection(updates):
        for key in entitlement_keys:
            updates.pop(key, None)
        for key, value in _entitlements_for_request(request, current=user).items():
            setattr(user, key, value)
    for key, value in updates.items():
        setattr(user, key, value)
    db.add(user)
    db.commit()
    db.refresh(user)
    return User.model_validate(user, from_attributes=True)


@router.delete("/{user_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_user(
    user_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    user = _get_user_or_404(user_id, db)
    db.delete(user)
    db.commit()


@router.post("/{user_id}/devices/{device_id}", response_model=UserDeviceAssignment)
def assign_device_to_user(
    user_id: int,
    device_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    _get_user_or_404(user_id, db)
    device = _get_device_or_404(device_id, db)
    device.user_id = user_id
    db.add(device)
    db.commit()
    db.refresh(device)
    return UserDeviceAssignment(device_id=device.id, user_id=user_id)
