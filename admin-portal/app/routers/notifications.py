from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import DeviceModel, NotificationHistoryModel, ProfileModel
from app.schemas import NotificationHistory, NotificationRequest
from app.settings import settings

router = APIRouter(prefix="/notifications", tags=["Notifications"])


def _history_to_schema(model: NotificationHistoryModel) -> NotificationHistory:
    return NotificationHistory(
        id=model.id,
        notification_type=model.notification_type,
        title=model.title,
        body=model.body,
        target_type=model.target_type,
        target_id=model.target_id,
        delivery_status=model.delivery_status,
        created_at=model.created_at,
    )


@router.get("/history")
def notification_history(db: Session = Depends(get_db), _admin: str = Depends(require_admin)):
    items = db.query(NotificationHistoryModel).order_by(NotificationHistoryModel.created_at.desc()).all()
    return {"items": [_history_to_schema(item).model_dump() for item in items]}


@router.get("")
def list_notifications(db: Session = Depends(get_db), _admin: str = Depends(require_admin)):
    return notification_history(db=db, _admin=_admin)


def _target_tokens(request: NotificationRequest, db: Session) -> list[str]:
    query = db.query(DeviceModel).filter(DeviceModel.fcm_token.isnot(None))
    if request.target_type == "device" and request.target_id:
        query = query.filter(DeviceModel.id == int(request.target_id))
    elif request.target_type == "profile" and request.target_id:
        profile = db.get(ProfileModel, int(request.target_id))
        if profile is None:
            return []
        query = query.filter(DeviceModel.id == profile.device_id)
    return [device.fcm_token for device in query.all() if device.fcm_token]


def _delivery_status_for_tokens(tokens: list[str]) -> str:
    if not tokens:
        return "no_registered_tokens"
    if not settings.firebase_credentials_path:
        return "firebase_not_configured"
    return "queued"


@router.post("/send", response_model=NotificationHistory, status_code=status.HTTP_201_CREATED)
def send_notification(
    request: NotificationRequest,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    tokens = _target_tokens(request, db)
    history = NotificationHistoryModel(
        notification_type=request.notification_type,
        title=request.title,
        body=request.body,
        target_type=request.target_type,
        target_id=request.target_id,
        delivery_status=_delivery_status_for_tokens(tokens),
    )
    db.add(history)
    db.commit()
    db.refresh(history)
    return _history_to_schema(history)
