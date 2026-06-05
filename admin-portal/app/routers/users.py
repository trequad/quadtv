from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import hash_provider_password, require_admin
from app.database import get_db
from app.jellyfin_client import JellyfinProvisioningError, create_or_update_jellyfin_user
from app.models import AppConfigModel, DeviceModel, UserModel
from app.schemas import User, UserCreate, UserDeviceAssignment, UserUpdate

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
