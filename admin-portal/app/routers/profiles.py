import hashlib
import hmac

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import DeviceModel, ProfileModel
from app.routers.config import _get_or_create_config
from app.schemas import Profile, ProfileCreate, ProfileUpdate
from app.settings import settings

router = APIRouter(tags=["Profiles"])


def _hash_pin(pin: str | None) -> str | None:
    if not pin:
        return None
    return hmac.new(settings.secret_key.encode("utf-8"), pin.encode("utf-8"), hashlib.sha256).hexdigest()


def _profile_to_schema(profile: ProfileModel) -> Profile:
    return Profile(
        id=profile.id,
        device_id=profile.device_id,
        display_name=profile.display_name,
        avatar=profile.avatar,
        parental_enabled=profile.parental_enabled,
    )


def _get_profile_or_404(profile_id: int, db: Session) -> ProfileModel:
    profile = db.get(ProfileModel, profile_id)
    if profile is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Profile not found")
    return profile


def _get_device_or_404(device_id: int, db: Session) -> DeviceModel:
    device = db.get(DeviceModel, device_id)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")
    return device


@router.get("/devices/{device_id}/profiles")
def list_device_profiles(device_id: int, db: Session = Depends(get_db)):
    _get_device_or_404(device_id, db)
    profiles = (
        db.query(ProfileModel)
        .filter(ProfileModel.device_id == device_id)
        .order_by(ProfileModel.created_at.asc())
        .all()
    )
    return {"items": [_profile_to_schema(profile).model_dump() for profile in profiles]}


@router.post("/devices/{device_id}/profiles", response_model=Profile, status_code=status.HTTP_201_CREATED)
def create_device_profile(
    device_id: int,
    request: ProfileCreate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    _get_device_or_404(device_id, db)
    config = _get_or_create_config(db)
    profile_count = db.query(ProfileModel).filter(ProfileModel.device_id == device_id).count()
    if profile_count >= config.max_profiles_per_device:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Profile limit reached for device")

    profile = ProfileModel(
        device_id=device_id,
        display_name=request.display_name,
        avatar=request.avatar,
        parental_pin_hash=_hash_pin(request.parental_pin),
        parental_enabled=False,
    )
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return _profile_to_schema(profile)


@router.patch("/profiles/{profile_id}", response_model=Profile)
def update_profile(
    profile_id: int,
    request: ProfileUpdate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    profile = _get_profile_or_404(profile_id, db)
    update_data = request.model_dump(exclude_unset=True)
    if "display_name" in update_data and update_data["display_name"] is not None:
        profile.display_name = update_data["display_name"]
    if "avatar" in update_data and update_data["avatar"] is not None:
        profile.avatar = update_data["avatar"]
    if "parental_enabled" in update_data and update_data["parental_enabled"] is not None:
        profile.parental_enabled = update_data["parental_enabled"]
    if "parental_pin" in update_data:
        profile.parental_pin_hash = _hash_pin(update_data["parental_pin"])
    db.add(profile)
    db.commit()
    db.refresh(profile)
    return _profile_to_schema(profile)


@router.delete("/profiles/{profile_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_profile(
    profile_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    profile = _get_profile_or_404(profile_id, db)
    db.delete(profile)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
