from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import encrypt_provider_secret, hash_provider_password, require_admin
from app.database import get_db
from app.models import ProviderAccountModel, UserModel
from app.providers.base import SUPPORTED_PROVIDER_TYPES
from app.schemas import ProviderAccountStatus, ProviderManualImportRequest, ProviderSyncResponse

router = APIRouter(prefix="/provider-sync", tags=["Provider Sync"])
PROVIDER_TYPES = SUPPORTED_PROVIDER_TYPES


def upsert_provider_accounts(
    db: Session,
    user_id: int,
    provider_username: str,
    provider_password: str,
    provider_types: list[str] | None = None,
) -> list[ProviderAccountModel]:
    """Create or re-link provider accounts for a user. Caller commits.

    Raises HTTPException 409 if the provider username is already linked to
    a different local user.
    """
    password_hash = hash_provider_password(provider_password)
    password_secret = encrypt_provider_secret(provider_password)
    now = datetime.now(timezone.utc)
    accounts: list[ProviderAccountModel] = []
    for provider_type in provider_types or list(PROVIDER_TYPES):
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
                provider_password_secret=password_secret,
            )
        else:
            account.user_id = user_id
            account.password_hash = password_hash
            account.provider_password_secret = password_secret
        account.sync_status = "manual_imported"
        account.last_synced_at = now
        account.last_error = None
        db.add(account)
        accounts.append(account)
    return accounts


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
                    provider_username="",
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

    selected_provider_types = [request.provider_type] if request.provider_type else list(PROVIDER_TYPES)
    if any(provider_type not in PROVIDER_TYPES for provider_type in selected_provider_types):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Unsupported provider type")
    upsert_provider_accounts(db, user_id, provider_username, request.provider_password, selected_provider_types)
    db.commit()
    all_accounts = db.query(ProviderAccountModel).filter(ProviderAccountModel.user_id == user_id).all()
    return _serialize(all_accounts, user_id=user_id, provider_username=provider_username)
