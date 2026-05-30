from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.auth import LoginRequest, TokenResponse, authenticate_admin, create_admin_token, create_customer_token, hash_provider_password, verify_provider_password
from app.database import get_db
from app.models import UserModel
from app.routers.subscriptions import _status_for_user
from app.schemas import CustomerLoginRequest, CustomerLoginResponse, CustomerRegisterRequest

router = APIRouter(prefix="/auth", tags=["Auth"])


@router.post("/login", response_model=TokenResponse)
def login(request: LoginRequest):
    if not authenticate_admin(request.username, request.password):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")
    return TokenResponse(access_token=create_admin_token())


@router.post("/customer-login", response_model=CustomerLoginResponse)
def customer_login(request: CustomerLoginRequest, db: Session = Depends(get_db)):
    username = request.username.strip()
    user = (
        db.query(UserModel)
        .filter(UserModel.app_username == username)
        .one_or_none()
    )
    if user is None or not user.app_password_hash or not verify_provider_password(request.password, user.app_password_hash):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid username or password")

    subscription = _status_for_user(user)
    if subscription.expired:
        return CustomerLoginResponse(
            access_token=None,
            user_id=user.id,
            provider_username=user.app_username,
            expired=True,
            expires_on=subscription.expires_on,
            days_remaining=subscription.days_remaining,
            message="Subscription expired. Please contact QuadMedia.",
        )

    return CustomerLoginResponse(
        access_token=create_customer_token(user.id, user.app_username),
        user_id=user.id,
        provider_username=user.app_username,
        expired=False,
        expires_on=subscription.expires_on,
        days_remaining=subscription.days_remaining,
    )


@router.post("/register", response_model=CustomerLoginResponse, status_code=status.HTTP_201_CREATED)
def register(request: CustomerRegisterRequest, db: Session = Depends(get_db)):
    username = request.username.strip()
    if not username or not request.password or not request.display_name.strip():
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="All fields are required")
    if db.query(UserModel).filter(UserModel.app_username == username).one_or_none():
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Username already taken")

    user = UserModel(
        display_name=request.display_name.strip(),
        app_username=username,
        app_password_hash=hash_provider_password(request.password),
        active=True,
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    return CustomerLoginResponse(
        access_token=create_customer_token(user.id, user.app_username),
        user_id=user.id,
        provider_username=user.app_username,
        expired=False,
    )
