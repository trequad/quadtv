from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import NotificationHistoryModel
from app.schemas import NotificationHistory

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
