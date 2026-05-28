from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.models import DeviceModel, UserModel
from app.routers.config import _get_or_create_config
from app.schemas import FcmTokenRegistrationRequest, FcmTokenRegistrationResponse, DeviceRegistrationRequest, DeviceRegistrationResponse

router = APIRouter(prefix="/devices", tags=["Devices"])


def _is_expired(expires_on: date | None) -> bool:
    return expires_on is not None and expires_on < date.today()


def _registration_response(device: DeviceModel, config, db: Session) -> DeviceRegistrationResponse:
    user = db.get(UserModel, device.user_id) if device.user_id is not None else None
    expires_on = user.expires_on if user is not None else None
    active = device.active and (user.active if user is not None else True)
    return DeviceRegistrationResponse(
        id=device.id,
        user_id=device.user_id,
        device_identifier=device.device_identifier,
        device_name=device.device_name,
        app_version=device.app_version,
        active=active,
        expired=(not active) or _is_expired(expires_on),
        expires_on=expires_on,
        max_profiles_per_device=config.max_profiles_per_device,
        live_stream_limit_per_user=config.live_stream_limit_per_user,
        vod_stream_limit_per_user=config.vod_stream_limit_per_user,
        jellyfin_stream_limit_per_user=config.jellyfin_stream_limit_per_user,
    )


@router.post("/register", response_model=DeviceRegistrationResponse, status_code=status.HTTP_201_CREATED)
def register_device(request: DeviceRegistrationRequest, response: Response, db: Session = Depends(get_db)):
    config = _get_or_create_config(db)
    device = (
        db.query(DeviceModel)
        .filter(DeviceModel.device_identifier == request.device_identifier)
        .one_or_none()
    )
    if device is None:
        device = DeviceModel(
            device_identifier=request.device_identifier,
            device_name=request.device_name,
            app_version=request.app_version,
            active=True,
        )
        db.add(device)
        db.commit()
        db.refresh(device)
        response.status_code = status.HTTP_201_CREATED
    else:
        device.device_name = request.device_name
        device.app_version = request.app_version
        db.add(device)
        db.commit()
        db.refresh(device)
        response.status_code = status.HTTP_200_OK

    return _registration_response(device, config, db)


@router.get("")
def list_devices(db: Session = Depends(get_db)):
    devices = db.query(DeviceModel).order_by(DeviceModel.created_at.desc()).all()
    return {
        "items": [
            {
                "id": device.id,
                "device_identifier": device.device_identifier,
                "device_name": device.device_name,
                "app_version": device.app_version,
                "user_id": device.user_id,
                "active": device.active,
                "fcm_token_present": bool(device.fcm_token),
                "fcm_platform": device.fcm_platform,
            }
            for device in devices
        ]
    }


@router.post("/{device_id}/fcm-token", response_model=FcmTokenRegistrationResponse)
def register_fcm_token(device_id: int, request: FcmTokenRegistrationRequest, db: Session = Depends(get_db)):
    device = db.get(DeviceModel, device_id)
    if device is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Device not found")

    device.fcm_token = request.token
    device.fcm_platform = request.platform
    db.add(device)
    db.commit()

    return FcmTokenRegistrationResponse(
        device_id=device.id,
        token_registered=True,
        platform=request.platform,
    )
