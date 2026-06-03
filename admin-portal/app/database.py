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


def get_db() -> Generator[Session, None, None]:
    init_db()
    db = get_session_factory()()
    try:
        yield db
    finally:
        db.close()
