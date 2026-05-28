from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import LoginRequest, TokenResponse, authenticate_admin, create_admin_token, create_customer_token, verify_provider_password
from app.database import get_db
from app.models import ProviderAccountModel, UserModel
from app.routers.subscriptions import _status_for_user
from app.schemas import CustomerLoginRequest, CustomerLoginResponse

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest):
    if not authenticate_admin(request.username, request.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    return TokenResponse(access_token=create_admin_token())


@router.post("/customer-login", response_model=CustomerLoginResponse)
def customer_login(request: CustomerLoginRequest, db: Session = Depends(get_db)):
    provider_username = request.username.strip()
    account = (
        db.query(ProviderAccountModel)
        .filter(
            ProviderAccountModel.provider_type == "live_tv",
            ProviderAccountModel.provider_username == provider_username,
        )
        .one_or_none()
    )
    if account is None or not verify_provider_password(request.password, account.password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid provider username or password")

    user = db.get(UserModel, account.user_id)
    if user is None:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Provider account is not linked to an active QuadTV user")

    subscription = _status_for_user(user)
    if subscription.expired:
        return CustomerLoginResponse(
            access_token=None,
            user_id=user.id,
            provider_username=provider_username,
            expired=True,
            expires_on=subscription.expires_on,
            days_remaining=subscription.days_remaining,
            message="Subscription expired. Please contact QuadMedia.",
        )

    return CustomerLoginResponse(
        access_token=create_customer_token(user.id, provider_username),
        user_id=user.id,
        provider_username=provider_username,
        expired=False,
        expires_on=subscription.expires_on,
        days_remaining=subscription.days_remaining,
    )
