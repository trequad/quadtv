from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import desc
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import AppReleaseModel
from app.schemas import AppRelease, AppReleaseCreate, UpdateStatus

router = APIRouter(prefix="/releases", tags=["App Releases"])


def _model_to_schema(model: AppReleaseModel) -> AppRelease:
    return AppRelease(
        id=model.id,
        version_name=model.version_name,
        version_code=model.version_code,
        changelog=model.changelog,
        apk_url=model.apk_url,
        minimum_supported_version_code=model.minimum_supported_version_code,
        forced=model.forced,
        published=model.published,
        release_date=model.release_date,
    )


def _latest_published_release(db: Session) -> AppReleaseModel | None:
    return (
        db.query(AppReleaseModel)
        .filter(AppReleaseModel.published.is_(True))
        .order_by(desc(AppReleaseModel.version_code), desc(AppReleaseModel.release_date))
        .first()
    )


@router.get("/current", response_model=UpdateStatus)
def get_current_release(
    current_version_code: int | None = Query(default=None, ge=0),
    db: Session = Depends(get_db),
):
    release = _latest_published_release(db)
    if release is None:
        return UpdateStatus(update_available=False, forced_update_required=False, release=None)

    update_available = current_version_code is None or current_version_code < release.version_code
    forced_update_required = bool(
        update_available
        and release.forced
        and current_version_code is not None
        and current_version_code < release.minimum_supported_version_code
    )
    return UpdateStatus(
        update_available=update_available,
        forced_update_required=forced_update_required,
        release=_model_to_schema(release) if update_available else None,
    )


@router.post("", response_model=AppRelease)
def publish_release(
    release: AppReleaseCreate,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    model = AppReleaseModel(
        version_name=release.version_name,
        version_code=release.version_code,
        changelog=release.changelog,
        apk_url=release.apk_url,
        minimum_supported_version_code=release.minimum_supported_version_code,
        forced=release.forced,
        published=release.published,
        release_date=release.release_date or datetime.now(timezone.utc),
    )
    db.add(model)
    db.commit()
    db.refresh(model)
    return _model_to_schema(model)
