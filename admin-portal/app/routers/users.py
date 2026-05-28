from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import DeviceModel, UserModel
from app.schemas import User, UserCreate, UserDeviceAssignment, UserUpdate

router = APIRouter(prefix="/users", tags=["Users"])


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
    user = UserModel(display_name=request.display_name, email=request.email, active=True)
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
    for key, value in updates.items():
        setattr(user, key, value)
    db.add(user)
    db.commit()
    db.refresh(user)
    return User.model_validate(user, from_attributes=True)


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
