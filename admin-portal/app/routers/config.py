from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import AppConfigModel
from app.schemas import AppConfig

router = APIRouter(prefix="/app", tags=["App Config"])

DEFAULT_CONFIG = AppConfig(
    live_tv_endpoint="https://live.example.invalid/playlist.m3u",
    xmltv_endpoint="https://live.example.invalid/xmltv.xml",
    vod_endpoint="https://vod.example.invalid/",
    jellyfin_base_url=None,
    jellyfin_api_key=None,
    max_profiles_per_device=5,
    warning_threshold_days=[14, 7, 3, 0],
    live_stream_limit_per_user=3,
    vod_stream_limit_per_user=1,
    jellyfin_stream_limit_per_user=2,
)


def _model_to_schema(model: AppConfigModel) -> AppConfig:
    return AppConfig(
        live_tv_endpoint=model.live_tv_endpoint,
        xmltv_endpoint=model.xmltv_endpoint,
        vod_endpoint=model.vod_endpoint,
        jellyfin_base_url=model.jellyfin_base_url,
        jellyfin_api_key=model.jellyfin_api_key,
        max_profiles_per_device=model.max_profiles_per_device,
        warning_threshold_days=model.warning_threshold_days,
        live_stream_limit_per_user=model.live_stream_limit_per_user,
        vod_stream_limit_per_user=model.vod_stream_limit_per_user,
        jellyfin_stream_limit_per_user=model.jellyfin_stream_limit_per_user,
    )


def _get_or_create_config(db: Session) -> AppConfigModel:
    existing = db.get(AppConfigModel, 1)
    if existing is not None:
        return existing

    model = AppConfigModel(id=1, **DEFAULT_CONFIG.model_dump())
    db.add(model)
    db.commit()
    db.refresh(model)
    return model


@router.get("/config", response_model=AppConfig)
def get_app_config(db: Session = Depends(get_db)):
    return _model_to_schema(_get_or_create_config(db))


@router.put("/config", response_model=AppConfig)
def update_app_config(
    config: AppConfig,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    model = _get_or_create_config(db)
    for key, value in config.model_dump().items():
        setattr(model, key, value)
    db.add(model)
    db.commit()
    db.refresh(model)
    return _model_to_schema(model)
