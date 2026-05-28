from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import hash_provider_password, require_admin
from app.database import get_db
from app.models import ProviderAccountModel, UserModel
from app.providers.base import SUPPORTED_PROVIDER_TYPES
from app.schemas import ProviderAccountStatus, ProviderManualImportRequest, ProviderSyncResponse

router = APIRouter(prefix="/provider-sync", tags=["Provider Sync"])
PROVIDER_TYPES = SUPPORTED_PROVIDER_TYPES


def _get_user_or_404(user_id: int, db: Session) -> UserModel:
    user = db.get(UserModel, user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return user


def _serialize(accounts: list[ProviderAccountModel], user_id: int, provider_username: str) -> ProviderSyncResponse:
    status_by_type = {account.provider_type: account for account in accounts}
    results = []
    for provider_type in PROVIDER_TYPES:
        account = status_by_type.get(provider_type)
        if account is None:
            results.append(
                ProviderAccountStatus(
                    provider_type=provider_type,
                    provider_username=provider_username,
                    sync_status="not_synced",
                    last_error=None,
                )
            )
        else:
            results.append(
                ProviderAccountStatus(
                    provider_type=account.provider_type,
                    provider_username=account.provider_username,
                    sync_status=account.sync_status,
                    last_error=account.last_error,
                )
            )
    return ProviderSyncResponse(user_id=user_id, provider_username=provider_username, results=results)


@router.get("/users/{user_id}", response_model=ProviderSyncResponse)
def get_provider_sync_status(
    user_id: int,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    _get_user_or_404(user_id, db)
    accounts = db.query(ProviderAccountModel).filter(ProviderAccountModel.user_id == user_id).all()
    provider_username = accounts[0].provider_username if accounts else ""
    return _serialize(accounts, user_id=user_id, provider_username=provider_username)


@router.post("/users/{user_id}/manual-import", response_model=ProviderSyncResponse, status_code=status.HTTP_201_CREATED)
def manual_import_provider_credentials(
    user_id: int,
    request: ProviderManualImportRequest,
    db: Session = Depends(get_db),
    _admin: str = Depends(require_admin),
):
    _get_user_or_404(user_id, db)
    provider_username = request.provider_username.strip()
    if not provider_username:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Provider username is required")
    if not request.provider_password:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Provider password is required")

    password_hash = hash_provider_password(request.provider_password)
    now = datetime.now(timezone.utc)
    accounts = []
    for provider_type in PROVIDER_TYPES:
        account = (
            db.query(ProviderAccountModel)
            .filter(
                ProviderAccountModel.provider_type == provider_type,
                ProviderAccountModel.provider_username == provider_username,
            )
            .one_or_none()
        )
        if account is not None and account.user_id != user_id:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"{provider_type} provider username already linked to another user",
            )
        if account is None:
            account = ProviderAccountModel(
                user_id=user_id,
                provider_type=provider_type,
                provider_username=provider_username,
                password_hash=password_hash,
            )
        else:
            account.user_id = user_id
            account.password_hash = password_hash
        account.sync_status = "manual_imported"
        account.last_synced_at = now
        account.last_error = None
        db.add(account)
        accounts.append(account)

    db.commit()
    for account in accounts:
        db.refresh(account)
    return _serialize(accounts, user_id=user_id, provider_username=provider_username)
