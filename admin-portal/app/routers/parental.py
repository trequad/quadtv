from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.auth import require_admin
from app.database import get_db
from app.models import ParentalBlocklistModel
from app.schemas import ParentalBlocklist

router = APIRouter(prefix="/parental", tags=["Parental"])

DEFAULT_BLOCKLIST = ParentalBlocklist()


def _model_to_schema(model: ParentalBlocklistModel) -> ParentalBlocklist:
    return ParentalBlocklist(
        channel_ids=model.channel_ids,
        category_names=model.category_names,
        content_ratings=model.content_ratings,
        keywords=model.keywords,
    )


def _get_or_create_blocklist(db: Session) -> ParentalBlocklistModel:
    existing = db.get(ParentalBlocklistModel, 1)
    if existing is not None:
        return existing

    model = ParentalBlocklistModel(id=1, **DEFAULT_BLOCKLIST.model_dump())
    db.add(model)
    db.commit()
    db.refresh(model)
    return model


@router.get("")
def list_parental():
    return {"items": [], "status": "scaffold"}


@router.get("/blocklist", response_model=ParentalBlocklist)
def get_parental_blocklist(db: Session = Depends(get_db)):
    return _model_to_schema(_get_or_create_blocklist(db))


@router.put("/blocklist", response_model=ParentalBlocklist)
def update_parental_blocklist(
    blocklist: ParentalBlocklist,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    model = _get_or_create_blocklist(db)
    for key, value in blocklist.model_dump().items():
        setattr(model, key, value)
    db.add(model)
    db.commit()
    db.refresh(model)
    return _model_to_schema(model)
