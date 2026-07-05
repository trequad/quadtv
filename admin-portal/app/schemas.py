from datetime import datetime, date
from pydantic import BaseModel, Field

class AppConfig(BaseModel):
    live_tv_provider_base_url: str
    vod_provider_base_url: str
    jellyfin_base_url: str | None = None
    # Secrets are write-only: accepted on PUT, always returned as None with a
    # matching *_set flag so the public config endpoint never leaks them.
    jellyfin_api_key: str | None = None
    jellyfin_api_key_set: bool = False
    seerr_base_url: str | None = None
    seerr_email: str | None = None
    seerr_password: str | None = None
    seerr_password_set: bool = False
    max_profiles_per_device: int = 5
    warning_threshold_days: list[int] = [14, 7, 3, 0]
    live_stream_limit_per_user: int = 3
    vod_stream_limit_per_user: int = 1
    jellyfin_stream_limit_per_user: int = 2
    provider_feed_refresh_hours: int = 24

class ParentalBlocklist(BaseModel):
    channel_ids: list[str] = []
    category_names: list[str] = []
    content_ratings: list[str] = ["R", "NC-17", "TV-MA"]
    keywords: list[str] = ["adult", "xxx", "porn"]

class Announcement(BaseModel):
    id: int
    title: str
    body: str
    image_url: str | None = None
    publish_at: datetime | None = None
    expires_at: datetime | None = None
    active: bool = True

class AnnouncementCreate(BaseModel):
    title: str
    body: str
    image_url: str | None = None
    publish_at: datetime | None = None
    expires_at: datetime | None = None
    active: bool = True
    push_notification: bool = False

class AnnouncementUpdate(BaseModel):
    title: str | None = None
    body: str | None = None
    image_url: str | None = None
    publish_at: datetime | None = None
    expires_at: datetime | None = None
    active: bool | None = None

class User(BaseModel):
    id: int
    display_name: str
    email: str | None = None
    app_username: str | None = None
    active: bool = True
    expires_on: date | None = None
    access_package: str = "full_access"
    can_access_live_tv: bool = True
    can_access_vod: bool = True
    can_access_quaddemand: bool = True
    can_access_seerr: bool = True

class UserCreate(BaseModel):
    display_name: str
    email: str | None = None
    app_username: str | None = None
    app_password: str | None = None
    app_pin: str | None = None
    access_package: str = "full_access"
    expires_on: date | None = None
    can_access_live_tv: bool | None = None
    can_access_vod: bool | None = None
    can_access_quaddemand: bool | None = None
    can_access_seerr: bool | None = None

class UserUpdate(BaseModel):
    display_name: str | None = None
    email: str | None = None
    active: bool | None = None
    expires_on: date | None = None
    app_username: str | None = None
    app_password: str | None = None
    app_pin: str | None = None
    access_package: str | None = None
    can_access_live_tv: bool | None = None
    can_access_vod: bool | None = None
    can_access_quaddemand: bool | None = None
    can_access_seerr: bool | None = None

class UserDeviceAssignment(BaseModel):
    device_id: int
    user_id: int

class Device(BaseModel):
    id: str
    name: str
    user_id: int | None = None
    active: bool = True

class DeviceRegistrationRequest(BaseModel):
    device_identifier: str
    device_name: str
    app_version: str | None = None

class DeviceRegistrationResponse(BaseModel):
    id: int
    user_id: int | None = None
    device_identifier: str
    device_name: str
    app_version: str | None = None
    active: bool
    expired: bool
    expires_on: date | None = None
    max_profiles_per_device: int
    live_stream_limit_per_user: int
    vod_stream_limit_per_user: int
    jellyfin_stream_limit_per_user: int

class FcmTokenRegistrationRequest(BaseModel):
    token: str = Field(min_length=1)
    platform: str = "android-tv"

class FcmTokenRegistrationResponse(BaseModel):
    device_id: int
    token_registered: bool
    platform: str

class Profile(BaseModel):
    id: int
    device_id: int
    user_id: int | None = None
    display_name: str
    avatar: str
    parental_enabled: bool = False

class ProfileCreate(BaseModel):
    display_name: str
    avatar: str
    parental_pin: str | None = None

class ProfileUpdate(BaseModel):
    display_name: str | None = None
    avatar: str | None = None
    parental_enabled: bool | None = None
    parental_pin: str | None = None

class SubscriptionUpdate(BaseModel):
    expires_on: date | None = None
    active: bool = True

class SubscriptionStatus(BaseModel):
    user_id: int
    expires_on: date | None = None
    active: bool
    expired: bool
    days_remaining: int | None = None

class NotificationRequest(BaseModel):
    notification_type: str = "service_alert"
    title: str
    body: str
    target_type: str = "all"
    target_id: str | None = None
    data: dict[str, str] = {}

class NotificationHistory(BaseModel):
    id: int
    notification_type: str
    title: str
    body: str
    target_type: str
    target_id: str | None = None
    delivery_status: str
    created_at: datetime

class ProviderManualImportRequest(BaseModel):
    provider_username: str
    provider_password: str
    provider_type: str | None = None

class ProviderAccountStatus(BaseModel):
    provider_type: str
    provider_username: str
    sync_status: str
    last_error: str | None = None

class ProviderSyncResponse(BaseModel):
    user_id: int
    provider_username: str
    results: list[ProviderAccountStatus]

class ProviderFeedResponse(BaseModel):
    live_tv_playlist_url: str
    xmltv_url: str
    provider_username: str
    refresh_hours: int

class CustomerLoginRequest(BaseModel):
    username: str
    password: str

class CustomerRegisterRequest(BaseModel):
    display_name: str
    username: str
    password: str

class CustomerLoginResponse(BaseModel):
    access_token: str | None = None
    token_type: str = "bearer"
    user_id: int
    provider_username: str | None = None
    expired: bool
    expires_on: date | None = None
    days_remaining: int | None = None
    access_package: str = "full_access"
    can_access_live_tv: bool = True
    can_access_vod: bool = True
    can_access_quaddemand: bool = True
    can_access_seerr: bool = True
    # Per-user QuadOnDemand session (set when Jellyfin auth succeeds at login).
    jellyfin_base_url: str | None = None
    jellyfin_user_id: str | None = None
    jellyfin_access_token: str | None = None
    message: str | None = None


class AppReleaseCreate(BaseModel):
    version_name: str = Field(min_length=1)
    version_code: int = Field(ge=1)
    changelog: str = Field(min_length=1)
    apk_url: str = Field(min_length=1)
    minimum_supported_version_code: int = Field(ge=0)
    forced: bool = False
    published: bool = True
    release_date: datetime | None = None


class AppRelease(BaseModel):
    id: int
    version_name: str
    version_code: int
    changelog: str
    apk_url: str
    minimum_supported_version_code: int
    forced: bool
    published: bool
    release_date: datetime


class UpdateStatus(BaseModel):
    update_available: bool
    forced_update_required: bool = False
    release: AppRelease | None = None


class UserFullSetupRequest(UserCreate):
    """One-flow admin user setup: local account + entitlements + integrations."""

    provider_username: str | None = None
    provider_password: str | None = None
    provision_jellyfin: bool = True


class UserSetupSummary(BaseModel):
    """What the one-flow setup created, linked, and still needs manual action."""

    user: User
    jellyfin_status: str
    jellyfin_detail: str | None = None
    provider_live_tv: str
    provider_vod: str
    warnings: list[str] = []
    next_steps: list[str] = []


class UserOverviewDevice(BaseModel):
    id: int
    device_name: str
    app_version: str | None = None
    active: bool


class UserOverviewJellyfin(BaseModel):
    provisioned: bool
    username: str | None = None
    jellyfin_user_id: str | None = None


class UserOverviewProviderAccount(BaseModel):
    provider_type: str
    provider_username: str
    sync_status: str


class UserOverview(BaseModel):
    user: User
    subscription: SubscriptionStatus
    jellyfin: UserOverviewJellyfin
    provider_accounts: list[UserOverviewProviderAccount]
    devices: list[UserOverviewDevice]
