from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import encrypt_provider_secret, require_admin
from app.database import get_db
from app.models import AppConfigModel
from app.schemas import AppConfig

router = APIRouter(prefix="/app", tags=["App Config"])

DEFAULT_CONFIG = AppConfig(
    live_tv_provider_base_url="http://ahhshitherewegoagain.sytes.net/",
    vod_provider_base_url="https://livinitup.online",
    jellyfin_base_url=None,
    jellyfin_api_key=None,
    seerr_base_url=None,
    seerr_email=None,
    seerr_password=None,
    max_profiles_per_device=5,
    warning_threshold_days=[14, 7, 3, 0],
    live_stream_limit_per_user=3,
    vod_stream_limit_per_user=1,
    jellyfin_stream_limit_per_user=2,
    provider_feed_refresh_hours=24,
)

# AppConfig fields that map 1:1 onto AppConfigModel columns.
_PLAIN_FIELDS = (
    "live_tv_provider_base_url",
    "vod_provider_base_url",
    "jellyfin_base_url",
    "seerr_base_url",
    "seerr_email",
    "max_profiles_per_device",
    "warning_threshold_days",
    "live_stream_limit_per_user",
    "vod_stream_limit_per_user",
    "jellyfin_stream_limit_per_user",
    "provider_feed_refresh_hours",
)


def _model_to_schema(model: AppConfigModel) -> AppConfig:
    # Secrets are never echoed back; only their presence is reported.
    return AppConfig(
        **{field: getattr(model, field) for field in _PLAIN_FIELDS},
        jellyfin_api_key=None,
        jellyfin_api_key_set=bool(model.jellyfin_api_key),
        seerr_password=None,
        seerr_password_set=bool(model.seerr_password_secret),
    )


def _get_or_create_config(db: Session) -> AppConfigModel:
    existing = db.get(AppConfigModel, 1)
    if existing is not None:
        return existing

    model = AppConfigModel(
        id=1,
        **{field: getattr(DEFAULT_CONFIG, field) for field in _PLAIN_FIELDS},
        jellyfin_api_key=None,
        seerr_password_secret=None,
    )
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
    for field in _PLAIN_FIELDS:
        setattr(model, field, getattr(config, field))
    # Blank secret fields mean "keep the stored value" so the admin form can be
    # saved without re-typing keys; a non-blank value replaces it.
    if config.jellyfin_api_key:
        model.jellyfin_api_key = config.jellyfin_api_key
    if config.seerr_password:
        model.seerr_password_secret = encrypt_provider_secret(config.seerr_password)
    db.add(model)
    db.commit()
    db.refresh(model)
    return _model_to_schema(model)
