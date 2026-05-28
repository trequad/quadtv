from datetime import date, datetime, timezone

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, JSON, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class AppConfigModel(Base):
    __tablename__ = "app_config"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, default=1)
    live_tv_endpoint: Mapped[str] = mapped_column(String(2048), nullable=False)
    xmltv_endpoint: Mapped[str] = mapped_column(String(2048), nullable=False)
    vod_endpoint: Mapped[str] = mapped_column(String(2048), nullable=False)
    jellyfin_base_url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    jellyfin_api_key: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    max_profiles_per_device: Mapped[int] = mapped_column(Integer, nullable=False, default=5)
    warning_threshold_days: Mapped[list[int]] = mapped_column(JSON, nullable=False, default=lambda: [14, 7, 3, 0])
    live_stream_limit_per_user: Mapped[int] = mapped_column(Integer, nullable=False, default=3)
    vod_stream_limit_per_user: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    jellyfin_stream_limit_per_user: Mapped[int] = mapped_column(Integer, nullable=False, default=2)


class ParentalBlocklistModel(Base):
    __tablename__ = "parental_blocklist"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, default=1)
    channel_ids: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    category_names: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=list)
    content_ratings: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=lambda: ["R", "NC-17", "TV-MA"])
    keywords: Mapped[list[str]] = mapped_column(JSON, nullable=False, default=lambda: ["adult", "xxx", "porn"])


class UserModel(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    display_name: Mapped[str] = mapped_column(String(120), nullable=False)
    email: Mapped[str | None] = mapped_column(String(255), unique=True, index=True, nullable=True)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    expires_on: Mapped[date | None] = mapped_column(Date, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class ProviderAccountModel(Base):
    __tablename__ = "provider_accounts"
    __table_args__ = (
        UniqueConstraint("provider_type", "provider_username", name="uq_provider_type_username"),
    )

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True, nullable=False)
    provider_type: Mapped[str] = mapped_column(String(40), nullable=False)
    provider_username: Mapped[str] = mapped_column(String(120), index=True, nullable=False)
    provider_account_id: Mapped[str | None] = mapped_column(String(120), nullable=True)
    password_hash: Mapped[str] = mapped_column(String(255), nullable=False)
    sync_status: Mapped[str] = mapped_column(String(80), nullable=False, default="manual_imported")
    last_synced_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_error: Mapped[str | None] = mapped_column(Text, nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class DeviceModel(Base):
    __tablename__ = "devices"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    user_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), index=True, nullable=True)
    device_identifier: Mapped[str] = mapped_column(String(255), unique=True, index=True, nullable=False)
    device_name: Mapped[str] = mapped_column(String(255), nullable=False)
    app_version: Mapped[str | None] = mapped_column(String(64), nullable=True)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class ProfileModel(Base):
    __tablename__ = "profiles"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("devices.id"), index=True, nullable=False)
    display_name: Mapped[str] = mapped_column(String(80), nullable=False)
    avatar: Mapped[str] = mapped_column(String(80), nullable=False)
    parental_pin_hash: Mapped[str | None] = mapped_column(String(128), nullable=True)
    parental_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class AnnouncementModel(Base):
    __tablename__ = "announcements"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    title: Mapped[str] = mapped_column(String(160), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    image_url: Mapped[str | None] = mapped_column(String(2048), nullable=True)
    publish_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    active: Mapped[bool] = mapped_column(Boolean, nullable=False, default=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        default=lambda: datetime.now(timezone.utc),
        onupdate=lambda: datetime.now(timezone.utc),
    )


class NotificationHistoryModel(Base):
    __tablename__ = "notification_history"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    notification_type: Mapped[str] = mapped_column(String(80), nullable=False)
    title: Mapped[str] = mapped_column(String(160), nullable=False)
    body: Mapped[str] = mapped_column(Text, nullable=False)
    target_type: Mapped[str] = mapped_column(String(80), nullable=False, default="all")
    target_id: Mapped[str | None] = mapped_column(String(255), nullable=True)
    delivery_status: Mapped[str] = mapped_column(String(80), nullable=False, default="queued")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(timezone.utc)
    )
