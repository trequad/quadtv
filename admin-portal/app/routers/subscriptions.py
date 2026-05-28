from datetime import date

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import UserModel
from app.schemas import SubscriptionStatus, SubscriptionUpdate

router = APIRouter(prefix="/subscriptions", tags=["Subscriptions"])


def _status_for_user(user: UserModel) -> SubscriptionStatus:
    days_remaining = None
    expired = False
    if user.expires_on is not None:
        days_remaining = (user.expires_on - date.today()).days
        expired = days_remaining < 0
    if not user.active:
        expired = True
    return SubscriptionStatus(
        user_id=user.id,
        expires_on=user.expires_on,
        active=user.active,
        expired=expired,
        days_remaining=days_remaining,
    )


def _get_user_or_404(user_id: int, db: Session) -> UserModel:
    user = db.get(UserModel, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


@router.get("")
def list_subscriptions(db: Session = Depends(get_db)):
    users = db.query(UserModel).order_by(UserModel.created_at.desc()).all()
    return {"items": [_status_for_user(user).model_dump() for user in users]}


@router.get("/users/{user_id}", response_model=SubscriptionStatus)
def get_user_subscription(user_id: int, db: Session = Depends(get_db)):
    return _status_for_user(_get_user_or_404(user_id, db))


@router.put("/users/{user_id}", response_model=SubscriptionStatus)
def update_user_subscription(
    user_id: int,
    request: SubscriptionUpdate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    user = _get_user_or_404(user_id, db)
    user.expires_on = request.expires_on
    user.active = request.active
    db.add(user)
    db.commit()
    db.refresh(user)
    return _status_for_user(user)
