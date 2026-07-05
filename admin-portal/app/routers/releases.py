from datetime import datetime, timezone
from pathlib import Path
import re

from fastapi import APIRouter, Depends, File, HTTPException, Query, UploadFile
from sqlalchemy import desc
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import AppReleaseModel
from app.schemas import AppRelease, AppReleaseCreate, UpdateStatus

router = APIRouter(prefix="/releases", tags=["App Releases"])
DOWNLOADS_DIR = Path(__file__).resolve().parents[2] / "web" / "downloads"


def _safe_apk_filename(filename: str) -> str:
    name = Path(filename or "quadtv-update.apk").name
    if not name.lower().endswith(".apk"):
        raise HTTPException(status_code=400, detail="Only .apk files can be uploaded")
    safe = re.sub(r"[^A-Za-z0-9._-]+", "-", name).strip(".-")
    return safe if safe.lower().endswith(".apk") else f"{safe}.apk"


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
    forced_update_required = False
    return UpdateStatus(
        update_available=update_available,
        forced_update_required=forced_update_required,
        release=_model_to_schema(release) if update_available else None,
    )


@router.post("/upload")
async def upload_release_apk(
    apk: UploadFile = File(...),
    _admin: str = Depends(require_admin),
):
    filename = _safe_apk_filename(apk.filename or "quadtv-update.apk")
    content = await apk.read()
    if not content.startswith(b"PK\x03\x04"):
        raise HTTPException(status_code=400, detail="Uploaded file is not a valid APK/ZIP package")
    DOWNLOADS_DIR.mkdir(parents=True, exist_ok=True)
    destination = DOWNLOADS_DIR / filename
    destination.write_bytes(content)
    return {
        "filename": filename,
        "apk_url": f"/downloads/{filename}",
        "size_bytes": len(content),
    }


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
        minimum_supported_version_code=0,
        forced=False,
        published=release.published,
        release_date=release.release_date or datetime.now(timezone.utc),
    )
    db.add(model)
    db.commit()
    db.refresh(model)
    return _model_to_schema(model)
