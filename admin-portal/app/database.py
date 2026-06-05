from collections.abc import Generator
from functools import lru_cache

from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker

from app.settings import settings


class Base(DeclarativeBase):
    pass


@lru_cache(maxsize=1)
def get_engine():
    connect_args = {"check_same_thread": False} if settings.database_url.startswith("sqlite") else {}
    return create_engine(settings.database_url, connect_args=connect_args)


@lru_cache(maxsize=1)
def get_session_factory():
    return sessionmaker(autocommit=False, autoflush=False, bind=get_engine())


def init_db() -> None:
    # Import models here so SQLAlchemy metadata is populated before create_all.
    import app.models  # noqa: F401

    engine = get_engine()
    Base.metadata.create_all(bind=engine)
    _ensure_sqlite_schema(engine)


def _ensure_sqlite_schema(engine) -> None:
    if engine.dialect.name != "sqlite":
        return
    inspector = inspect(engine)
    if "provider_accounts" not in inspector.get_table_names():
        return
    provider_account_columns = {column["name"] for column in inspector.get_columns("provider_accounts")}
    if "provider_password_secret" not in provider_account_columns:
        with engine.begin() as connection:
            connection.execute(text("ALTER TABLE provider_accounts ADD COLUMN provider_password_secret VARCHAR(4096)"))

    if "users" in inspector.get_table_names():
        user_columns = {column["name"] for column in inspector.get_columns("users")}
        with engine.begin() as connection:
            if "jellyfin_user_id" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN jellyfin_user_id VARCHAR(120)"))
            if "jellyfin_username" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN jellyfin_username VARCHAR(120)"))
            if "app_pin_hash" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN app_pin_hash VARCHAR(255)"))
            if "access_package" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN access_package VARCHAR(40) NOT NULL DEFAULT 'full_access'"))
            if "can_access_live_tv" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN can_access_live_tv BOOLEAN NOT NULL DEFAULT 1"))
            if "can_access_vod" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN can_access_vod BOOLEAN NOT NULL DEFAULT 1"))
            if "can_access_quaddemand" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN can_access_quaddemand BOOLEAN NOT NULL DEFAULT 1"))
            if "can_access_seerr" not in user_columns:
                connection.execute(text("ALTER TABLE users ADD COLUMN can_access_seerr BOOLEAN NOT NULL DEFAULT 1"))

    if "profiles" in inspector.get_table_names():
        profile_columns = {column["name"] for column in inspector.get_columns("profiles")}
        if "user_id" not in profile_columns:
            with engine.begin() as connection:
                connection.execute(text("ALTER TABLE profiles ADD COLUMN user_id INTEGER"))
                connection.execute(text("UPDATE profiles SET user_id = (SELECT user_id FROM devices WHERE devices.id = profiles.device_id) WHERE user_id IS NULL"))


def get_db() -> Generator[Session, None, None]:
    init_db()
    db = get_session_factory()()
    try:
        yield db
    finally:
        db.close()
