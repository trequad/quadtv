from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import AnnouncementModel, NotificationHistoryModel
from app.schemas import Announcement, AnnouncementCreate, AnnouncementUpdate

router = APIRouter(prefix="/announcements", tags=["Announcements"])


def _normalize_for_compare(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


def _announcement_to_schema(model: AnnouncementModel) -> Announcement:
    return Announcement(
        id=model.id,
        title=model.title,
        body=model.body,
        image_url=model.image_url,
        publish_at=model.publish_at,
        expires_at=model.expires_at,
        active=model.active,
    )


def _get_announcement_or_404(announcement_id: int, db: Session) -> AnnouncementModel:
    announcement = db.get(AnnouncementModel, announcement_id)
    if announcement is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Announcement not found")
    return announcement


def _is_active(model: AnnouncementModel) -> bool:
    now = datetime.now(timezone.utc)
    publish_at = _normalize_for_compare(model.publish_at)
    expires_at = _normalize_for_compare(model.expires_at)
    return model.active and (publish_at is None or publish_at <= now) and (expires_at is None or expires_at > now)


@router.get("")
def list_announcements(db: Session = Depends(get_db), _admin: str = Depends(require_admin)):
    announcements = db.query(AnnouncementModel).order_by(AnnouncementModel.created_at.desc()).all()
    return {"items": [_announcement_to_schema(item).model_dump() for item in announcements]}


@router.get("/active")
def list_active_announcements(db: Session = Depends(get_db)):
    announcements = db.query(AnnouncementModel).order_by(AnnouncementModel.created_at.desc()).all()
    return {"items": [_announcement_to_schema(item).model_dump() for item in announcements if _is_active(item)]}


@router.post("", response_model=Announcement, status_code=status.HTTP_201_CREATED)
def create_announcement(
    request: AnnouncementCreate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    announcement = AnnouncementModel(
        title=request.title,
        body=request.body,
        image_url=request.image_url,
        publish_at=request.publish_at,
        expires_at=request.expires_at,
        active=request.active,
    )
    db.add(announcement)
    db.commit()
    db.refresh(announcement)

    if request.push_notification:
        db.add(
            NotificationHistoryModel(
                notification_type="announcement",
                title=request.title,
                body=request.body,
                target_type="all",
                target_id=None,
                delivery_status="queued",
            )
        )
        db.commit()

    return _announcement_to_schema(announcement)


@router.patch("/{announcement_id}", response_model=Announcement)
def update_announcement(
    announcement_id: int,
    request: AnnouncementUpdate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    announcement = _get_announcement_or_404(announcement_id, db)
    for key, value in request.model_dump(exclude_unset=True).items():
        setattr(announcement, key, value)
    db.add(announcement)
    db.commit()
    db.refresh(announcement)
    return _announcement_to_schema(announcement)


@router.delete("/{announcement_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_announcement(
    announcement_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    announcement = _get_announcement_or_404(announcement_id, db)
    db.delete(announcement)
    db.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
